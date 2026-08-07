package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Trade;
import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.domain.TradeStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.HoldingRow;
import com.cs.skinledger.dto.PnlGroupBy;
import com.cs.skinledger.dto.PnlRow;
import com.cs.skinledger.dto.PortfolioView;
import com.cs.skinledger.dto.TradeCreateRequest;
import com.cs.skinledger.dto.TradeFilter;
import com.cs.skinledger.dto.TradeResponse;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.TradeRepository;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.service.PnlEngine.Position;
import com.cs.skinledger.service.PnlEngine.TradeInput;
import com.cs.skinledger.web.TradeNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TradeService {

    private static final long LOCAL_USER_ID = 1L;
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("steam", "uu", "buff");

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public TradeResponse create(TradeCreateRequest req) {
        validate(req);
        validateSellHolding(req, null);
        User user = localUser();
        Item item = findOrCreateItem(req.itemName());
        Trade trade = new Trade();
        applyFields(trade, req, item, user);
        return TradeResponse.from(tradeRepository.save(trade));
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> list(TradeFilter filter) {
        Specification<Trade> spec = filter == null
                ? (root, query, cb) -> cb.equal(root.get("user").get("id"), LOCAL_USER_ID)
                : buildSpec(filter);
        return tradeRepository.findAll(spec, Sort.by("tradedAt").descending()).stream()
                .map(TradeResponse::from)
                .toList();
    }

    @Transactional
    public TradeResponse update(Long id, TradeCreateRequest req) {
        Trade trade = tradeRepository.findById(id).orElseThrow(() -> new TradeNotFoundException(id));
        validate(req);
        validateSellHolding(req, trade.getId());
        Item item = findOrCreateItem(req.itemName());
        applyFields(trade, req, item, trade.getUser());
        return TradeResponse.from(tradeRepository.save(trade));
    }

    @Transactional
    public void delete(Long id) {
        Trade trade = tradeRepository.findById(id).orElseThrow(() -> new TradeNotFoundException(id));
        tradeRepository.delete(trade);
    }

    @Transactional(readOnly = true)
    public PortfolioView portfolio() {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID);
        Map<Long, Position> positions = new LinkedHashMap<>();
        Map<Long, Item> items = new LinkedHashMap<>();
        for (Trade t : trades) {
            if (t.getStatus() != TradeStatus.COMPLETED) {
                continue;
            }
            Position prev = positions.getOrDefault(t.getItem().getId(), Position.empty());
            positions.put(t.getItem().getId(), PnlEngine.apply(prev, toInput(t)));
            items.put(t.getItem().getId(), t.getItem());
        }
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;
        List<HoldingRow> holdings = new ArrayList<>();
        for (Map.Entry<Long, Position> e : positions.entrySet()) {
            Item item = items.get(e.getKey());
            Position p = e.getValue();
            totalCost = totalCost.add(p.remainingCost());
            totalRealized = totalRealized.add(p.realizedPnl());
            holdings.add(new HoldingRow(item.getMarketHashName(), p.remainingQty(), p.avgCost(), p.realizedPnl(), null));
        }
        return new PortfolioView(totalCost, totalRealized, holdings);
    }

    @Transactional(readOnly = true)
    public List<PnlRow> realizedPnl(PnlGroupBy groupBy) {
        List<Trade> trades = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID);
        Map<Long, Position> positions = new HashMap<>();
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Trade t : trades) {
            if (t.getStatus() != TradeStatus.COMPLETED) {
                continue;
            }
            Position prev = positions.getOrDefault(t.getItem().getId(), Position.empty());
            Position next = PnlEngine.apply(prev, toInput(t));
            positions.put(t.getItem().getId(), next);
            if (t.getDirection() == TradeDirection.SELL) {
                BigDecimal delta = next.realizedPnl().subtract(prev.realizedPnl());
                String key = groupKey(t, groupBy);
                sums.merge(key, delta, BigDecimal::add);
                counts.merge(key, 1, Integer::sum);
            }
        }
        return sums.entrySet().stream()
                .map(e -> new PnlRow(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0)))
                .toList();
    }

    private String groupKey(Trade t, PnlGroupBy groupBy) {
        return switch (groupBy) {
            case day -> t.getTradedAt().toLocalDate().toString();
            case week -> {
                java.time.LocalDate d = t.getTradedAt().toLocalDate();
                int y = d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
                int w = d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield y + "-W" + String.format("%02d", w);
            }
            case month -> YearMonth.from(t.getTradedAt()).toString();
            case year -> String.valueOf(t.getTradedAt().getYear());
            case platform -> t.getPlatform();
            case category -> {
                String c = t.getItem().getCategory();
                yield (c == null || c.isBlank()) ? "未分类" : c;
            }
            case item -> t.getItem().getMarketHashName();
        };
    }

    private Specification<Trade> buildSpec(TradeFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("user").get("id"), LOCAL_USER_ID));
            if (f.platform() != null && !f.platform().isBlank()) {
                ps.add(cb.equal(root.get("platform"), f.platform()));
            }
            if (f.direction() != null) {
                ps.add(cb.equal(root.get("direction"), f.direction()));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("tradedAt"), f.from()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("tradedAt"), f.to()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                ps.add(cb.like(root.get("item").get("marketHashName"), "%" + f.q() + "%"));
            }
            if (f.category() != null && !f.category().isBlank()) {
                ps.add(cb.equal(root.get("item").get("category"), f.category()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private void validate(TradeCreateRequest req) {
        if (!SUPPORTED_PLATFORMS.contains(req.platform())) {
            throw new IllegalArgumentException("platform 仅支持 steam/uu/buff");
        }
    }

    private void validateSellHolding(TradeCreateRequest req, Long excludeId) {
        if (req.direction() != TradeDirection.SELL) {
            return;
        }
        Item item = itemRepository.findByMarketHashName(req.itemName()).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("卖出数量超过当前持仓：该饰品尚无任何持仓");
        }
        List<TradeInput> inputs = tradeRepository.findByUserIdOrderByTradedAtAsc(LOCAL_USER_ID).stream()
                .filter(t -> t.getStatus() == TradeStatus.COMPLETED)
                .filter(t -> t.getItem().getId().equals(item.getId()))
                .filter(t -> excludeId == null || !t.getId().equals(excludeId))
                .map(this::toInput)
                .toList();
        Position pos = PnlEngine.replay(inputs);
        if (req.quantity().compareTo(pos.remainingQty()) > 0) {
            throw new IllegalArgumentException("卖出数量超过当前持仓（当前可卖 " + pos.remainingQty() + "）");
        }
    }

    private TradeInput toInput(Trade t) {
        return new TradeInput(t.getDirection(), t.getQuantity(), t.getUnitPrice(), t.getFee());
    }

    private void applyFields(Trade trade, TradeCreateRequest req, Item item, User user) {
        trade.setUser(user);
        trade.setItem(item);
        trade.setPlatform(req.platform());
        trade.setDirection(req.direction());
        trade.setQuantity(req.quantity());
        trade.setUnitPrice(req.unitPrice());
        trade.setTotalAmount(req.quantity().multiply(req.unitPrice()));
        trade.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
        trade.setFeeRate(req.feeRate());
        trade.setCurrency(req.currency() == null || req.currency().isBlank() ? "CNY" : req.currency());
        trade.setTradedAt(req.tradedAt());
        trade.setExternalTradeId(req.externalTradeId());
        trade.setStatus(req.status() == null ? TradeStatus.COMPLETED : req.status());
        trade.setNote(req.note());
    }

    private Item findOrCreateItem(String marketHashName) {
        return itemRepository.findByMarketHashName(marketHashName)
                .orElseGet(() -> {
                    Item item = new Item();
                    item.setMarketHashName(marketHashName);
                    item.setSource("manual");
                    return itemRepository.save(item);
                });
    }

    private User localUser() {
        return userRepository.findByUsername("local")
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername("local");
                    return userRepository.save(user);
                });
    }
}