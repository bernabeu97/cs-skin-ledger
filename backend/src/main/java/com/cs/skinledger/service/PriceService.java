package com.cs.skinledger.service;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.PriceSnapshot;
import com.cs.skinledger.dto.HoldingValuation;
import com.cs.skinledger.dto.PortfolioValuation;
import com.cs.skinledger.dto.PriceConfigView;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceRefreshResult;
import com.cs.skinledger.dto.PriceTarget;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.service.price.PriceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行情服务：刷新平台价格快照、计算持仓估值（浮动盈亏）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    /** 估值统一以 UU（悠悠有品）价格为准；无 UU 价的批次不估值，不回退 Steam/BUFF */
    private static final List<String> PRICE_PRIORITY = List.of("uu");

    private final LotRepository lotRepository;
    private final ItemRepository itemRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final AlertService alertService;
    private final AppPriceProperties props;
    private final CurrentUser currentUser;
    private final List<PriceProvider> providers;

    /** 手动/定时触发刷新；platforms 为空时使用配置默认值 */
    @Transactional
    public PriceRefreshResult refresh(List<String> platforms) {
        LocalDateTime start = LocalDateTime.now();
        List<PriceTarget> targets = buildTargets();
        List<String> wanted = platforms == null || platforms.isEmpty()
                ? parseSources(props.getDefaultSources())
                : platforms.stream().map(String::trim).filter(s -> !s.isBlank()).toList();

        List<PriceQuote> quotes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> byPlatform = new LinkedHashMap<>();
        for (String p : wanted) {
            byPlatform.put(p, 0);
        }
        int requested = targets.size();

        if (!targets.isEmpty()) {
            List<PriceProvider> usable = selectProviders(wanted);
            if (usable.isEmpty()) {
                errors.add("没有可用的行情数据源：请配置 CSQAQ ApiToken（推荐）或启用 Steam/UU 直连");
            } else {
                for (PriceProvider provider : usable) {
                    try {
                        List<PriceQuote> qs = provider.fetch(targets);
                        quotes.addAll(qs);
                    } catch (Exception e) {
                        errors.add(provider.name() + ": " + e.getMessage());
                    }
                }
            }
        }

        int saved = saveQuotes(quotes);
        List<com.cs.skinledger.dto.AlertResponse> triggered = alertService.check(quotes);
        if (!triggered.isEmpty()) {
            log.info("行情刷新后触发价格提醒 {} 条", triggered.size());
        }
        for (PriceQuote q : quotes) {
            byPlatform.merge(q.platform(), 1, Integer::sum);
        }
        return new PriceRefreshResult(start, LocalDateTime.now(), requested, saved, errors.size(), errors, byPlatform);
    }

    /** 持仓估值：每个持有批次按平台优先级取最新价，计算市值与浮动盈亏 */
    @Transactional(readOnly = true)
    public PortfolioValuation valuation() {
        List<Lot> lots = lotRepository.findByUserIdOrderByBuyTimeAsc(currentUser.id()).stream()
                .filter(l -> l.getStatus() == LotStatus.HOLDING)
                .toList();
        if (lots.isEmpty()) {
            return new PortfolioValuation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of());
        }
        List<Long> itemIds = lots.stream().map(l -> l.getItem().getId()).distinct().toList();
        Map<String, Map<String, PriceSnapshot>> latest = snapshotRepository.findLatestByItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(ps -> priceKey(ps.getItem().getId(), ps.getExterior()),
                        Collectors.toMap(PriceSnapshot::getPlatform, Function.identity(), (a, b) -> a)));

        BigDecimal holdingCost = BigDecimal.ZERO;
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        LocalDateTime priceAsOf = null;
        List<HoldingValuation> rows = new ArrayList<>();
        for (Lot lot : lots) {
            BigDecimal qty = lot.getQuantity();
            BigDecimal cost = qty.multiply(lot.getBuyPrice());
            holdingCost = holdingCost.add(cost);
            Map<String, PriceSnapshot> byPlatform = latest.get(priceKey(lot.getItem().getId(), lot.getExterior()));
            PriceSnapshot used = pickSnapshot(byPlatform);
            if (used == null) {
                rows.add(HoldingValuation.withoutPrice(lot.getId(), lot.getItem().getId(),
                        lot.getItem().getMarketHashName(), lot.getItem().getNameZh(),
                        lot.getExterior(), qty, lot.getBuyPrice()));
                continue;
            }
            BigDecimal currentPrice = used.getPrice();
            BigDecimal value = qty.multiply(currentPrice);
            BigDecimal pnl = value.subtract(cost);
            marketValue = marketValue.add(value);
            unrealized = unrealized.add(pnl);
            if (priceAsOf == null || used.getFetchedAt().isAfter(priceAsOf)) {
                priceAsOf = used.getFetchedAt();
            }
            java.util.Map<String, BigDecimal> latestPrices = byPlatform.values().stream()
                    .filter(ps -> ps.getPrice() != null && ps.getPrice().signum() > 0)
                    .collect(Collectors.toMap(PriceSnapshot::getPlatform, PriceSnapshot::getPrice, (a, b) -> a));
            rows.add(new HoldingValuation(lot.getId(), lot.getItem().getId(),
                    lot.getItem().getMarketHashName(), lot.getItem().getNameZh(), lot.getExterior(),
                    qty, lot.getBuyPrice(), currentPrice, used.getPlatform(), used.getFetchedAt(), value, pnl,
                    latestPrices));
        }
        return new PortfolioValuation(holdingCost, marketValue, unrealized, priceAsOf, rows);
    }

    /** 是否存在至少一个可用数据源 */
    public boolean hasAvailableSource() {
        return providers.stream().anyMatch(PriceProvider::available);
    }

    public PriceConfigView config() {
        boolean csqaq = providers.stream().anyMatch(p -> "csqaq".equals(p.name()) && p.available());
        boolean steam = providers.stream().anyMatch(p -> "steam".equals(p.name()) && p.available());
        boolean uu = providers.stream().anyMatch(p -> "uu".equals(p.name()) && p.available());
        Map<String, String> messages = new LinkedHashMap<>();
        if (!csqaq) {
            messages.put("csqaq", "未配置 CSQAQ ApiToken（免费注册 https://csqaq.com 获取，或设置环境变量 CSQAQ_TOKEN）。配置后一次即可获取 UU/Steam/BUFF 三平台价格。");
        }
        if (!uu) {
            messages.put("uu", "UU 直连需登录 token 文件（app.price.youpin.token-file），且可能被风控拦截。");
        }
        return new PriceConfigView(csqaq, steam, uu, messages);
    }

    private PriceSnapshot pickSnapshot(Map<String, PriceSnapshot> byPlatform) {
        if (byPlatform == null || byPlatform.isEmpty()) {
            return null;
        }
        for (String p : PRICE_PRIORITY) {
            PriceSnapshot ps = byPlatform.get(p);
            if (ps != null && ps.getPrice() != null && ps.getPrice().signum() > 0) {
                return ps;
            }
        }
        return null;
    }

    /** 持有批次 -> 采集目标（按完整市场名去重） */
    private List<PriceTarget> buildTargets() {
        Map<String, PriceTarget> byName = new LinkedHashMap<>();
        lotRepository.findByUserIdOrderByBuyTimeAsc(currentUser.id()).stream()
                .filter(l -> l.getStatus() == LotStatus.HOLDING)
                .forEach(l -> {
                    Item item = l.getItem();
                    PriceTarget t = new PriceTarget(item.getId(), item.getMarketHashName(), l.getExterior());
                    byName.putIfAbsent(t.fullMarketHashName(), t);
                });
        return new ArrayList<>(byName.values());
    }

    /** 按请求平台选择数据源：CSQAQ 可用时优先（一次覆盖三平台），否则回退直连 */
    private List<PriceProvider> selectProviders(List<String> wanted) {
        PriceProvider csqaq = providers.stream().filter(p -> "csqaq".equals(p.name()) && p.available()).findFirst().orElse(null);
        if (csqaq != null) {
            return List.of(csqaq);
        }
        List<PriceProvider> direct = new ArrayList<>();
        for (String platform : wanted) {
            providers.stream()
                    .filter(p -> platform.equals(p.name()) && p.available())
                    .findFirst()
                    .ifPresent(direct::add);
        }
        return direct;
    }

    private int saveQuotes(List<PriceQuote> quotes) {
        if (quotes.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PriceSnapshot> snapshots = quotes.stream().map(q -> {
            PriceSnapshot ps = new PriceSnapshot();
            ps.setItem(itemRepository.getReferenceById(q.itemId()));
            ps.setExterior(q.exterior());
            ps.setPlatform(q.platform());
            ps.setPrice(q.price());
            ps.setBuyPrice(q.buyPrice());
            
            ps.setVolume(q.volume());
            ps.setCurrency(q.currency() == null ? "CNY" : q.currency());
            ps.setFetchedAt(q.capturedAt() == null ? now : q.capturedAt());
            return ps;
        }).toList();
        snapshotRepository.saveAll(snapshots);
        return snapshots.size();
    }

    private List<String> parseSources(String s) {
        if (s == null || s.isBlank()) {
            return List.of("uu", "steam", "buff");
        }
        return java.util.Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    private String priceKey(Long itemId, String exterior) {
        return itemId + "|" + (exterior == null ? "" : exterior.trim());
    }
}
