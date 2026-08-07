package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.LotCreateRequest;
import com.cs.skinledger.dto.LotFilter;
import com.cs.skinledger.dto.LotResponse;
import com.cs.skinledger.dto.LotSellRequest;
import com.cs.skinledger.dto.LotSummary;
import com.cs.skinledger.dto.PnlGroupBy;
import com.cs.skinledger.dto.PnlRow;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LotService {

    private static final long LOCAL_USER_ID = 1L;
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("steam", "uu", "buff");

    private final LotRepository lotRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public LotResponse create(LotCreateRequest req) {
        validatePlatform(req.buyPlatform());
        User user = localUser();
        Item item = resolveItem(req);
        Lot lot = new Lot();
        lot.setUser(user);
        lot.setItem(item);
        applyBuyFields(lot, req);
        return LotResponse.from(lotRepository.save(lot));
    }

    @Transactional
    public LotResponse update(Long id, LotCreateRequest req) {
        validatePlatform(req.buyPlatform());
        Lot lot = lotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("批次不存在: " + id));
        Item item = resolveItem(req);
        lot.setItem(item);
        applyBuyFields(lot, req);
        if (req.sellPrice() != null) {
            lot.setSellPrice(req.sellPrice());
            lot.setSellTime(req.sellTime());
            lot.setSellPlatform(req.sellPlatform());
            lot.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
            recomputeSell(lot);
            lot.setStatus(LotStatus.SOLD);
        } else if (lot.getStatus() == LotStatus.SOLD) {
            recomputeSell(lot);
        }
        return LotResponse.from(lotRepository.save(lot));
    }

    @Transactional
    public LotResponse updateSell(Long id, LotSellRequest req) {
        if (req.sellPlatform() != null) {
            validatePlatform(req.sellPlatform());
        }
        Lot lot = lotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("批次不存在: " + id));
        lot.setSellPrice(req.sellPrice());
        lot.setSellTime(req.sellTime());
        lot.setSellPlatform(req.sellPlatform());
        lot.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
        recomputeSell(lot);
        lot.setStatus(LotStatus.SOLD);
        return LotResponse.from(lotRepository.save(lot));
    }

    @Transactional
    public void delete(Long id) {
        Lot lot = lotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("批次不存在: " + id));
        lotRepository.delete(lot);
    }

    @Transactional(readOnly = true)
    public List<LotResponse> list(LotFilter filter) {
        Specification<Lot> spec = filter == null
                ? (root, query, cb) -> cb.equal(root.get("user").get("id"), LOCAL_USER_ID)
                : buildSpec(filter);
        return lotRepository.findAll(spec, Sort.by("buyTime").descending()).stream()
                .map(LotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LotSummary summary() {
        List<Lot> lots = lotRepository.findByUserIdOrderByBuyTimeAsc(LOCAL_USER_ID);
        BigDecimal totalBuyCost = BigDecimal.ZERO;
        BigDecimal holdingCost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        int holding = 0;
        int sold = 0;
        for (Lot lot : lots) {
            BigDecimal cost = lot.getQuantity().multiply(lot.getBuyPrice());
            totalBuyCost = totalBuyCost.add(cost);
            if (lot.getStatus() == LotStatus.SOLD) {
                realized = realized.add(lot.getProfit() == null ? BigDecimal.ZERO : lot.getProfit());
                sold++;
            } else {
                holdingCost = holdingCost.add(cost);
                holding++;
            }
        }
        return new LotSummary(totalBuyCost, holdingCost, realized, lots.size(), holding, sold);
    }

    @Transactional(readOnly = true)
    public List<PnlRow> realizedPnl(PnlGroupBy groupBy) {
        List<Lot> lots = lotRepository.findByUserIdOrderByBuyTimeAsc(LOCAL_USER_ID);
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Lot lot : lots) {
            if (lot.getStatus() != LotStatus.SOLD || lot.getSellTime() == null) {
                continue;
            }
            BigDecimal profit = lot.getProfit() == null ? BigDecimal.ZERO : lot.getProfit();
            String key = groupKey(lot, groupBy);
            sums.merge(key, profit, BigDecimal::add);
            counts.merge(key, 1, Integer::sum);
        }
        return sums.entrySet().stream()
                .map(e -> new PnlRow(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0)))
                .toList();
    }

    private String groupKey(Lot lot, PnlGroupBy groupBy) {
        return switch (groupBy) {
            case day -> lot.getSellTime().toLocalDate().toString();
            case week -> {
                java.time.LocalDate d = lot.getSellTime().toLocalDate();
                int y = d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
                int w = d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield y + "-W" + String.format("%02d", w);
            }
            case month -> YearMonth.from(lot.getSellTime()).toString();
            case year -> String.valueOf(lot.getSellTime().getYear());
            case platform -> lot.getSellPlatform() == null ? "未指定" : lot.getSellPlatform();
            case category -> {
                String c = lot.getItem().getCategory();
                yield (c == null || c.isBlank()) ? "未分类" : c;
            }
            case item -> lot.getItem().getMarketHashName();
        };
    }

    private Specification<Lot> buildSpec(LotFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("user").get("id"), LOCAL_USER_ID));
            if (f.q() != null && !f.q().isBlank()) {
                String pattern = "%" + f.q() + "%";
                ps.add(cb.or(
                        cb.like(root.get("item").get("marketHashName"), pattern),
                        cb.like(root.get("item").get("nameZh"), pattern)));
            }
            if (f.status() != null) {
                ps.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.platform() != null && !f.platform().isBlank()) {
                ps.add(cb.or(
                        cb.equal(root.get("buyPlatform"), f.platform()),
                        cb.equal(root.get("sellPlatform"), f.platform())));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("buyTime"), f.from()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("buyTime"), f.to()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private void applyBuyFields(Lot lot, LotCreateRequest req) {
        lot.setQuantity(req.quantity() == null ? BigDecimal.ONE : req.quantity());
        lot.setExterior(req.exterior());
        lot.setFloatValue(req.floatValue());
        lot.setBuyPrice(req.buyPrice());
        lot.setBuyTime(req.buyTime());
        lot.setBuyPlatform(req.buyPlatform());
        lot.setNote(req.note());
    }

    private void recomputeSell(Lot lot) {
        BigDecimal qty = lot.getQuantity();
        BigDecimal income = qty.multiply(lot.getSellPrice()).subtract(lot.getFee());
        lot.setActualIncome(income);
        lot.setProfit(income.subtract(qty.multiply(lot.getBuyPrice())));
    }

    private Item resolveItem(LotCreateRequest req) {
        if (req.itemId() != null) {
            return itemRepository.findById(req.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + req.itemId()));
        }
        if (req.itemName() == null || req.itemName().isBlank()) {
            throw new IllegalArgumentException("必须选择或输入饰品");
        }
        return itemRepository.findByMarketHashName(req.itemName())
                .or(() -> itemRepository.findByNameZh(req.itemName()))
                .orElseGet(() -> {
                    Item item = new Item();
                    item.setMarketHashName(req.itemName());
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

    private void validatePlatform(String platform) {
        if (!SUPPORTED_PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("platform 仅支持 steam/uu/buff");
        }
    }
}