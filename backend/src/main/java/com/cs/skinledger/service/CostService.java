package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.OtherCostEntry;
import com.cs.skinledger.dto.CostRequest;
import com.cs.skinledger.dto.CostResponse;
import com.cs.skinledger.dto.CostSummary;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.OtherCostRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 其他收支：会员费、赔偿、退款等非饰品资金项。
 * 汇总口径：net = 总收入 - 总支出；总盈亏 = 饰品已实现盈亏 + net。
 */
@Service
@RequiredArgsConstructor
public class CostService {

    private final OtherCostRepository repository;
    private final ItemRepository itemRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<CostResponse> list(String category, String direction, LocalDateTime from, LocalDateTime to) {
        Specification<OtherCostEntry> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("user").get("id"), currentUser.id()));
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(root.get("category"), category));
            }
            if (direction != null && !direction.isBlank()) {
                ps.add(cb.equal(root.get("direction"), direction));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, Sort.by("occurredAt").descending()).stream()
                .map(CostResponse::from)
                .toList();
    }

    @Transactional
    public CostResponse create(CostRequest req) {
        OtherCostEntry e = new OtherCostEntry();
        apply(e, req);
        e.setUser(currentUser.get());
        return CostResponse.from(repository.save(e));
    }

    @Transactional
    public CostResponse update(Long id, CostRequest req) {
        OtherCostEntry e = repository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        apply(e, req);
        return CostResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        OtherCostEntry e = repository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        repository.delete(e);
    }

    @Transactional(readOnly = true)
    public CostSummary summary() {
        Long userId = currentUser.id();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal[]> byCat = new LinkedHashMap<>();
        for (Object[] row : repository.sumByDirection(userId)) {
            String dir = (String) row[0];
            BigDecimal v = (BigDecimal) row[1];
            if ("income".equals(dir)) {
                totalIncome = v;
            } else {
                totalExpense = v;
            }
        }
        for (Object[] row : repository.sumByCategory(userId)) {
            String cat = (String) row[0];
            String dir = (String) row[1];
            BigDecimal v = (BigDecimal) row[2];
            BigDecimal[] arr = byCat.computeIfAbsent(cat, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("income".equals(dir)) {
                arr[0] = v;
            } else {
                arr[1] = v;
            }
        }
        List<CostSummary.CategoryCost> cats = byCat.entrySet().stream()
                .map(e -> new CostSummary.CategoryCost(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0].subtract(e.getValue()[1])))
                .toList();
        return new CostSummary(totalIncome, totalExpense, totalIncome.subtract(totalExpense), cats);
    }

    private void apply(OtherCostEntry e, CostRequest req) {
        e.setCategory(req.category());
        e.setDirection(req.direction());
        e.setAmount(req.amount());
        e.setOccurredAt(req.occurredAt());
        e.setPlatform(req.platform());
        if (req.itemId() != null) {
            Item item = itemRepository.findById(req.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + req.itemId()));
            e.setItem(item);
        } else {
            e.setItem(null);
        }
        e.setNote(req.note());
        e.setSourceRef(req.sourceRef());
    }

}
