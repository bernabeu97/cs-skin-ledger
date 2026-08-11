package com.cs.skinledger.service;

import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.MarketIndexSnapshot;
import com.cs.skinledger.domain.PriceSnapshot;
import com.cs.skinledger.dto.MarketIndexView;
import com.cs.skinledger.dto.PricePoint;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.MarketIndexSnapshotRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketIndexService {
    public static final String HOLDINGS = "holdings";
    public static final String WATCHLIST = "watchlist";
    private static final BigDecimal BASE = new BigDecimal("100.000000");

    private final LotRepository lotRepository;
    private final WatchlistRepository watchlistRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final MarketIndexSnapshotRepository indexRepository;
    private final CurrentUser currentUser;

    @Transactional
    public void captureCurrentUser() {
        capture(HOLDINGS, holdingComponents());
        capture(WATCHLIST, watchlistComponents());
        indexRepository.deleteByFetchedAtBefore(LocalDateTime.now().minusDays(100));
    }

    @Transactional(readOnly = true)
    public MarketIndexView history(String kind, String period) {
        validateKind(kind);
        LocalDateTime from = periodStart(period);
        List<MarketIndexSnapshot> rows = indexRepository
                .findByUserIdAndKindAndFetchedAtGreaterThanEqualOrderByFetchedAtAsc(currentUser.id(), kind, from);
        rows = downsample(rows, 500);
        List<PricePoint> points = rows.stream()
                .map(row -> new PricePoint(row.getFetchedAt(), row.getIndexValue()))
                .toList();
        if (rows.isEmpty()) {
            return new MarketIndexView(kind, period, null, null, null, null, points);
        }
        MarketIndexSnapshot first = rows.getFirst();
        MarketIndexSnapshot last = rows.getLast();
        BigDecimal change = first.getIndexValue().signum() == 0 ? BigDecimal.ZERO
                : last.getIndexValue().subtract(first.getIndexValue())
                .multiply(BigDecimal.valueOf(100))
                .divide(first.getIndexValue(), 4, RoundingMode.HALF_UP);
        return new MarketIndexView(kind, period, last.getIndexValue(), last.getMarketValue(),
                change, last.getFetchedAt(), points);
    }

    private void capture(String kind, List<Component> components) {
        if (components.isEmpty()) {
            return;
        }
        List<Long> itemIds = components.stream().map(Component::itemId).distinct().toList();
        Map<String, PriceSnapshot> current = uuMap(priceSnapshotRepository.findLatestByItemIds(itemIds));
        LocalDateTime asOf = components.stream().map(c -> current.get(key(c.itemId(), c.exterior())))
                .filter(java.util.Objects::nonNull)
                .map(PriceSnapshot::getFetchedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (asOf == null) {
            return;
        }
        var last = indexRepository.findTopByUserIdAndKindOrderByFetchedAtDesc(currentUser.id(), kind).orElse(null);
        if (last != null && !asOf.isAfter(last.getFetchedAt())) {
            return;
        }

        String hash = compositionHash(components);
        BigDecimal indexValue = last == null ? BASE : last.getIndexValue();
        if (last != null && last.getCompositionHash().equals(hash)) {
            Map<String, PriceSnapshot> previous = uuMap(priceSnapshotRepository
                    .findLatestAtOrBeforeByItemIds(itemIds, last.getFetchedAt()));
            BigDecimal factor = WATCHLIST.equals(kind)
                    ? equalWeightFactor(components, previous, current)
                    : valueWeightFactor(components, previous, current);
            if (factor != null && factor.signum() > 0) {
                indexValue = indexValue.multiply(factor).setScale(6, RoundingMode.HALF_UP);
            }
        }

        MarketIndexSnapshot snapshot = new MarketIndexSnapshot();
        snapshot.setUser(currentUser.get());
        snapshot.setKind(kind);
        snapshot.setIndexValue(indexValue);
        snapshot.setMarketValue(HOLDINGS.equals(kind) ? marketValue(components, current) : null);
        snapshot.setCompositionHash(hash);
        snapshot.setFetchedAt(asOf);
        indexRepository.save(snapshot);
    }

    private List<Component> holdingComponents() {
        Map<String, Component> grouped = new LinkedHashMap<>();
        lotRepository.findByUserIdOrderByBuyTimeAsc(currentUser.id()).stream()
                .filter(lot -> lot.getStatus() == LotStatus.HOLDING)
                .forEach(lot -> {
                    String key = key(lot.getItem().getId(), lot.getExterior());
                    grouped.merge(key, new Component(lot.getItem().getId(), normalize(lot.getExterior()), lot.getQuantity()),
                            (a, b) -> new Component(a.itemId(), a.exterior(), a.quantity().add(b.quantity())));
                });
        return grouped.values().stream().sorted().toList();
    }

    private List<Component> watchlistComponents() {
        return watchlistRepository.findByUserIdOrderBySortOrderAscIdAsc(currentUser.id()).stream()
                .map(entry -> new Component(entry.getItem().getId(), normalize(entry.getExterior()), BigDecimal.ONE))
                .sorted()
                .toList();
    }

    private BigDecimal valueWeightFactor(List<Component> components, Map<String, PriceSnapshot> previous,
                                         Map<String, PriceSnapshot> current) {
        BigDecimal before = BigDecimal.ZERO;
        BigDecimal after = BigDecimal.ZERO;
        for (Component c : components) {
            PriceSnapshot p0 = previous.get(key(c.itemId(), c.exterior()));
            PriceSnapshot p1 = current.get(key(c.itemId(), c.exterior()));
            if (p0 == null || p1 == null || p0.getPrice().signum() <= 0) continue;
            before = before.add(c.quantity().multiply(p0.getPrice()));
            after = after.add(c.quantity().multiply(p1.getPrice()));
        }
        return before.signum() == 0 ? null : after.divide(before, 12, RoundingMode.HALF_UP);
    }

    private BigDecimal equalWeightFactor(List<Component> components, Map<String, PriceSnapshot> previous,
                                         Map<String, PriceSnapshot> current) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Component c : components) {
            PriceSnapshot p0 = previous.get(key(c.itemId(), c.exterior()));
            PriceSnapshot p1 = current.get(key(c.itemId(), c.exterior()));
            if (p0 == null || p1 == null || p0.getPrice().signum() <= 0) continue;
            sum = sum.add(p1.getPrice().divide(p0.getPrice(), 12, RoundingMode.HALF_UP));
            count++;
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP);
    }

    private BigDecimal marketValue(List<Component> components, Map<String, PriceSnapshot> prices) {
        return components.stream().map(component -> {
            PriceSnapshot snapshot = prices.get(key(component.itemId(), component.exterior()));
            return snapshot == null ? BigDecimal.ZERO : component.quantity().multiply(snapshot.getPrice());
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, PriceSnapshot> uuMap(List<PriceSnapshot> snapshots) {
        return snapshots.stream().filter(ps -> "uu".equals(ps.getPlatform()))
                .collect(Collectors.toMap(ps -> key(ps.getItem().getId(), ps.getExterior()),
                        Function.identity(), (a, b) -> a.getFetchedAt().isAfter(b.getFetchedAt()) ? a : b));
    }

    private String compositionHash(List<Component> components) {
        String raw = components.stream()
                .map(c -> c.itemId() + "|" + normalize(c.exterior()) + "|" + c.quantity().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算指数成分摘要", e);
        }
    }

    private <T> List<T> downsample(List<T> rows, int maxPoints) {
        if (rows.size() <= maxPoints) return rows;
        int step = (int) Math.ceil(rows.size() / (double) maxPoints);
        List<T> sampled = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += step) sampled.add(rows.get(i));
        T last = rows.getLast();
        if (sampled.getLast() != last) sampled.add(last);
        return sampled;
    }

    private void validateKind(String kind) {
        if (!HOLDINGS.equals(kind) && !WATCHLIST.equals(kind)) {
            throw new IllegalArgumentException("kind 仅支持 holdings 或 watchlist");
        }
    }

    private LocalDateTime periodStart(String period) {
        return switch (period) {
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            case "90d" -> LocalDateTime.now().minusDays(90);
            default -> throw new IllegalArgumentException("period 仅支持 24h、7d、30d、90d");
        };
    }

    private String key(Long itemId, String exterior) {
        return itemId + "|" + normalize(exterior);
    }

    private String normalize(String exterior) {
        return exterior == null || exterior.isBlank() ? "" : exterior.trim();
    }

    private record Component(Long itemId, String exterior, BigDecimal quantity) implements Comparable<Component> {
        @Override
        public int compareTo(Component other) {
            int item = itemId.compareTo(other.itemId);
            return item != 0 ? item : exterior.compareTo(other.exterior);
        }
    }
}
