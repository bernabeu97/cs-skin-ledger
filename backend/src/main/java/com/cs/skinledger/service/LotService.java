package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.AggregateRow;
import com.cs.skinledger.dto.LotCreateRequest;
import com.cs.skinledger.dto.LotFilter;
import com.cs.skinledger.dto.LotPage;
import com.cs.skinledger.dto.LotResponse;
import com.cs.skinledger.dto.LotSellRequest;
import com.cs.skinledger.dto.LotStats;
import com.cs.skinledger.dto.LotSummary;
import com.cs.skinledger.dto.PnlGroupBy;
import com.cs.skinledger.dto.PnlRow;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LotService {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("steam", "uu", "buff");

    private final LotRepository lotRepository;
    private final CurrentUser currentUser;
    private final ItemRepository itemRepository;

    @Transactional
    public LotResponse create(LotCreateRequest req) {
        return create(req, null);
    }

    @Transactional
    public LotResponse create(LotCreateRequest req, String sourceRef) {
        validatePlatform(req.buyPlatform());
        User user = currentUser.get();
        Item item = resolveItem(req);
        Lot lot = new Lot();
        lot.setUser(user);
        lot.setItem(item);
        applyBuyFields(lot, req);
        lot.setSourceRef(sourceRef);
        if (req.sellPrice() != null) {
            if (req.sellTime() == null || req.sellPlatform() == null || req.sellPlatform().isBlank()) {
                throw new IllegalArgumentException("已出售记录必须填写出售时间和出售平台");
            }
            validatePlatform(req.sellPlatform());
            lot.setSellPrice(req.sellPrice());
            lot.setSellTime(req.sellTime());
            lot.setSellPlatform(req.sellPlatform());
            lot.setFee(req.fee() == null ? BigDecimal.ZERO : req.fee());
            recomputeSell(lot);
            lot.setStatus(LotStatus.SOLD);
        }
        return LotResponse.from(lotRepository.save(lot));
    }

    @Transactional
    public LotResponse update(Long id, LotCreateRequest req) {
        validatePlatform(req.buyPlatform());
        Lot lot = ownedLot(id);
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
        Lot lot = ownedLot(id);
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
        Lot lot = ownedLot(id);
        lot.setDeletedAt(LocalDateTime.now());
        lotRepository.save(lot);
    }

    @Transactional(readOnly = true)
    public List<LotResponse> trash() {
        return lotRepository.findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(currentUser.id()).stream()
                .map(LotResponse::from).toList();
    }

    @Transactional
    public LotResponse restore(Long id) {
        Lot lot = lotRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("回收站记录不存在: " + id));
        if (lot.getDeletedAt() == null) throw new IllegalArgumentException("记录不在回收站中");
        lot.setDeletedAt(null);
        return LotResponse.from(lotRepository.save(lot));
    }

    @Transactional
    public void purge(Long id) {
        Lot lot = lotRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("回收站记录不存在: " + id));
        if (lot.getDeletedAt() == null) throw new IllegalArgumentException("只能彻底删除回收站中的记录");
        lotRepository.delete(lot);
    }

    @Transactional
    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpiredTrash() {
        lotRepository.deleteByDeletedAtBefore(LocalDateTime.now().minusDays(30));
    }

    @Transactional(readOnly = true)
    public List<LotResponse> list(LotFilter filter) {
        Specification<Lot> spec = filter == null
                ? (root, query, cb) -> cb.equal(root.get("user").get("id"), currentUser.id())
                : buildSpec(filter);
        return lotRepository.findAll(spec, Sort.by("buyTime").descending()).stream()
                .map(LotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LotPage page(LotFilter filter, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Specification<Lot> spec = filter == null
                ? (root, query, cb) -> cb.equal(root.get("user").get("id"), currentUser.id())
                : buildSpec(filter);
        Page<Lot> result = lotRepository.findAll(spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by("buyTime").descending()));
        return new LotPage(
                result.getContent().stream().map(LotResponse::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize,
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LotStats stats() {
        List<Lot> lots = lotRepository.findByUserIdAndDeletedAtIsNullOrderByBuyTimeAsc(currentUser.id());
        BigDecimal totalBuyCost = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        int holding = 0;
        int sold = 0;
        int winningSold = 0;
        for (Lot lot : lots) {
            BigDecimal cost = lot.getQuantity().multiply(lot.getBuyPrice());
            totalBuyCost = totalBuyCost.add(cost);
            if (lot.getStatus() == LotStatus.SOLD) {
                BigDecimal profit = lot.getProfit() == null ? BigDecimal.ZERO : lot.getProfit();
                realized = realized.add(profit);
                sold++;
                if (profit.signum() > 0) {
                    winningSold++;
                }
            } else {
                holding++;
            }
        }
        BigDecimal realizedRoi = totalBuyCost.signum() == 0
                ? BigDecimal.ZERO
                : realized.divide(totalBuyCost, 6, RoundingMode.HALF_UP);
        double winRate = sold == 0 ? 0d : (double) winningSold / sold;
        return new LotStats(realizedRoi, winRate, sold, winningSold, lots.size(), holding);
    }

    @Transactional(readOnly = true)
    public List<AggregateRow> aggregate(PnlGroupBy groupBy) {
        if (groupBy != PnlGroupBy.item && groupBy != PnlGroupBy.category) {
            throw new IllegalArgumentException("aggregate 仅支持 item/category 分组");
        }
        List<Lot> lots = lotRepository.findByUserIdAndDeletedAtIsNullOrderByBuyTimeAsc(currentUser.id());
        Map<String, BigDecimal> realized = new LinkedHashMap<>();
        Map<String, BigDecimal> buyCost = new LinkedHashMap<>();
        Map<String, Integer> soldCount = new LinkedHashMap<>();
        Map<String, Integer> winningSold = new LinkedHashMap<>();
        for (Lot lot : lots) {
            String key = groupKey(lot, groupBy);
            buyCost.merge(key, lot.getQuantity().multiply(lot.getBuyPrice()), BigDecimal::add);
            if (lot.getStatus() == LotStatus.SOLD) {
                BigDecimal profit = lot.getProfit() == null ? BigDecimal.ZERO : lot.getProfit();
                realized.merge(key, profit, BigDecimal::add);
                soldCount.merge(key, 1, Integer::sum);
                if (profit.signum() > 0) {
                    winningSold.merge(key, 1, Integer::sum);
                }
            }
        }
        return realized.entrySet().stream()
                .map(e -> new AggregateRow(
                        e.getKey(),
                        e.getValue(),
                        buyCost.getOrDefault(e.getKey(), BigDecimal.ZERO),
                        soldCount.getOrDefault(e.getKey(), 0),
                        winningSold.getOrDefault(e.getKey(), 0)))
                .sorted((a, b) -> b.realizedPnl().compareTo(a.realizedPnl()))
                .toList();
    }

    @Transactional
    public int batchFillBuyPrice(List<Long> ids, BigDecimal buyPrice) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要补填的批次");
        }
        if (buyPrice == null || buyPrice.signum() < 0) {
            throw new IllegalArgumentException("买入价不能为负");
        }
        int updated = 0;
        for (Long id : ids) {
            Lot lot = ownedLot(id);
            lot.setBuyPrice(buyPrice);
            if (lot.getStatus() == LotStatus.SOLD) {
                recomputeSell(lot);
            }
            lotRepository.save(lot);
            updated++;
        }
        return updated;
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的记录");
        }
        int deleted = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            Lot lot = ownedLot(id);
            lot.setDeletedAt(now);
            lotRepository.save(lot);
            deleted++;
        }
        return deleted;
    }

    @Transactional(readOnly = true)
    public LotSummary summary() {
        List<Lot> lots = lotRepository.findByUserIdAndDeletedAtIsNullOrderByBuyTimeAsc(currentUser.id());
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
        List<Lot> lots = lotRepository.findByUserIdAndDeletedAtIsNullOrderByBuyTimeAsc(currentUser.id());
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
            ps.add(cb.equal(root.get("user").get("id"), currentUser.id()));
            ps.add(cb.isNull(root.get("deletedAt")));
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

    private Lot ownedLot(Long id) {
        return lotRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + id));
    }

    private void validatePlatform(String platform) {
        if (!SUPPORTED_PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("platform 仅支持 steam/uu/buff");
        }
    }
}
