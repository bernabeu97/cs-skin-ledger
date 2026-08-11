package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.PriceSnapshot;
import com.cs.skinledger.domain.WatchlistEntry;
import com.cs.skinledger.dto.WatchlistCreateRequest;
import com.cs.skinledger.dto.WatchlistResponse;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {
    public static final int MAX_ITEMS = 50;

    private final WatchlistRepository watchlistRepository;
    private final ItemRepository itemRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<WatchlistResponse> list() {
        List<WatchlistEntry> entries = watchlistRepository.findByUserIdOrderBySortOrderAscIdAsc(currentUser.id());
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = entries.stream().map(e -> e.getItem().getId()).distinct().toList();
        Map<String, PriceSnapshot> current = uuMap(snapshotRepository.findLatestByItemIds(itemIds));
        Map<String, PriceSnapshot> dayAgo = uuMap(snapshotRepository.findLatestAtOrBeforeByItemIds(
                itemIds, LocalDateTime.now().minusHours(24)));
        return entries.stream().map(entry -> toResponse(entry, current, dayAgo)).toList();
    }

    @Transactional
    public WatchlistResponse create(WatchlistCreateRequest request) {
        Long userId = currentUser.id();
        if (watchlistRepository.countByUserId(userId) >= MAX_ITEMS) {
            throw new IllegalArgumentException("自选最多添加 " + MAX_ITEMS + " 个饰品与磨损组合");
        }
        String exterior = normalizeForStorage(request.exterior());
        if (watchlistRepository.existsByUserIdAndItemIdAndExterior(userId, request.itemId(), exterior)) {
            throw new IllegalArgumentException("该饰品与磨损已在自选中");
        }
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new IllegalArgumentException("饰品不存在：" + request.itemId()));
        WatchlistEntry entry = new WatchlistEntry();
        entry.setUser(currentUser.get());
        entry.setItem(item);
        entry.setExterior(exterior);
        entry.setSortOrder((int) watchlistRepository.countByUserId(userId));
        entry = watchlistRepository.save(entry);
        return new WatchlistResponse(entry.getId(), item.getId(), item.getMarketHashName(), item.getNameZh(),
                blankToNull(exterior), null, null, null, null, entry.getCreatedAt());
    }

    @Transactional
    public void delete(Long id) {
        WatchlistEntry entry = watchlistRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("自选记录不存在：" + id));
        watchlistRepository.delete(entry);
    }

    private WatchlistResponse toResponse(WatchlistEntry entry, Map<String, PriceSnapshot> current,
                                         Map<String, PriceSnapshot> dayAgo) {
        String key = key(entry.getItem().getId(), entry.getExterior());
        PriceSnapshot now = current.get(key);
        PriceSnapshot before = dayAgo.get(key);
        BigDecimal change = null;
        BigDecimal percent = null;
        if (now != null && before != null && before.getPrice().signum() > 0) {
            change = now.getPrice().subtract(before.getPrice());
            percent = change.multiply(BigDecimal.valueOf(100))
                    .divide(before.getPrice(), 4, RoundingMode.HALF_UP);
        }
        Item item = entry.getItem();
        return new WatchlistResponse(entry.getId(), item.getId(), item.getMarketHashName(), item.getNameZh(),
                blankToNull(entry.getExterior()), now == null ? null : now.getPrice(),
                now == null ? null : now.getFetchedAt(), change, percent, entry.getCreatedAt());
    }

    private Map<String, PriceSnapshot> uuMap(List<PriceSnapshot> snapshots) {
        return snapshots.stream()
                .filter(ps -> "uu".equals(ps.getPlatform()))
                .collect(Collectors.toMap(ps -> key(ps.getItem().getId(), ps.getExterior()),
                        Function.identity(), (a, b) -> a.getFetchedAt().isAfter(b.getFetchedAt()) ? a : b));
    }

    private String key(Long itemId, String exterior) {
        return itemId + "|" + normalizeForStorage(exterior);
    }

    private String normalizeForStorage(String exterior) {
        return exterior == null || exterior.isBlank() ? "" : exterior.trim();
    }

    private String blankToNull(String exterior) {
        return exterior == null || exterior.isBlank() ? null : exterior;
    }
}
