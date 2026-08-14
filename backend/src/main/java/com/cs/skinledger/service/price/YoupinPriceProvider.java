package com.cs.skinledger.service.price;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.domain.MarketplaceId;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceTarget;
import com.cs.skinledger.repository.MarketplaceIdRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 悠悠有品(UU) 直连（备选，实验性）。
 * 端点来自社区项目 Steamauto / SteamTradingSiteTracker。
 * 注意：api.youpin898.com 有阿里云 WAF 风控，通常需要抓包得到的登录 token
 * （写入配置的 token 文件，格式为一行 token，如 "Bearer xxxxx" 或裸 token）。
 * 未配置 token 或请求被拦截时，本 Provider 返回空并记录原因。
 */
@Component
public class YoupinPriceProvider implements PriceProvider {

    private final AppPriceProperties props;
    private final MarketplaceIdRepository marketplaceIdRepository;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private static final int CONCURRENCY = 4;
    private final AtomicLong nextRequestSlot = new AtomicLong(0);

    public YoupinPriceProvider(AppPriceProperties props, MarketplaceIdRepository marketplaceIdRepository, ObjectMapper mapper) {
        this.props = props;
        this.marketplaceIdRepository = marketplaceIdRepository;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getYoupin().getTimeoutSeconds())))
                .build();
    }

    @Override
    public String name() {
        return "uu";
    }

    @Override
    public boolean available() {
        return props.getYoupin() != null && props.getYoupin().isEnabled() && tokenFile().map(Files::exists).orElse(false);
    }

    @Override
    public List<PriceQuote> fetch(List<PriceTarget> targets) throws Exception {
        if (!available()) {
            throw new IllegalStateException("UU 直连未启用或缺少 token 文件：" + props.getYoupin().getTokenFile());
        }
        String token = tokenFile().map(p -> readToken(p)).orElse("");
        List<PriceQuote> quotes = Collections.synchronizedList(new ArrayList<>());
        LocalDateTime now = LocalDateTime.now();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(CONCURRENCY, Math.max(1, targets.size())));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PriceTarget t : targets) {
                futures.add(pool.submit(() -> {
                    try {
                        pace();
                        MarketplaceId mid = marketplaceIdRepository.findById(t.fullMarketHashName()).orElse(null);
                        if (mid == null || mid.getYoupinId() == null) {
                            return;
                        }
                        String body = mapper.writeValueAsString(Map.of(
                                "templateId", String.valueOf(mid.getYoupinId()),
                                "pageIndex", 1,
                                "pageSize", 10));
                        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.youpin898.com/api/homepage/v2/detail/commodity/list/sell"))
                                .timeout(Duration.ofSeconds(props.getYoupin().getTimeoutSeconds()))
                                .header("Content-Type", "application/json; charset=utf-8")
                                .header("Authorization", token.startsWith("Bearer") ? token : "Bearer " + token)
                                .header("User-Agent", "okhttp/3.14.9")
                                .header("App-Version", "5.28.3")
                                .header("AppType", "4")
                                .header("deviceType", "1")
                                .header("package-type", "uuyp")
                                .header("platform", "android")
                                .header("Gameid", "730")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                        try {
                            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                            JsonNode root = mapper.readTree(resp.body());
                            if (root.path("Code").asInt(-1) != 0) {
                                return;
                            }
                            JsonNode list = root.path("Data").path("CommodityList");
                            if (list.isArray() && !list.isEmpty()) {
                                JsonNode first = list.get(0);
                                BigDecimal price = first.path("Price").decimalValue();
                                if (price.signum() > 0) {
                                    Integer volume = first.hasNonNull("CommodityNum") ? first.path("CommodityNum").asInt() : null;
                                    quotes.add(new PriceQuote(t.itemId(), t.fullMarketHashName(), t.exterior(), "uu", price, null, volume, "CNY", now));
                                }
                            }
                        } catch (Exception e) {
                            // 被 WAF 拦截或网络异常，跳过该条
                        }
                    } catch (Exception ignored) {
                        // 单条失败不中断整体
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        return quotes;
    }

    /** 请求节奏:起始时间间隔 1s,池内并发可重叠但总速率受限,规避 WAF 风控 */
    private void pace() throws InterruptedException {
        long slot = nextRequestSlot.getAndAdd(1000);
        long wait = slot - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }

    private java.util.Optional<Path> tokenFile() {
        if (props.getYoupin() == null || props.getYoupin().getTokenFile() == null || props.getYoupin().getTokenFile().isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Path.of(props.getYoupin().getTokenFile()));
    }

    private String readToken(Path p) {
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            return s.lines().findFirst().orElse("");
        } catch (Exception e) {
            return "";
        }
    }
}
