package com.cs.skinledger.service.price;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Steam 社区市场直连（备选数据源）。
 * GET /market/priceoverview/?appid=730&currency=23&market_hash_name={完整市场名}
 * 注意：Steam 对高频请求有限流，且部分地区网络不可达；失败会自动跳过。
 */
@Component
public class SteamPriceProvider implements PriceProvider {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private final AppPriceProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;
    /** 同一平台内并发请求数(受限流节奏约束) */
    private static final int CONCURRENCY = 4;
    /** 请求开始时间节奏:每个请求间隔 delayMs,池内并发可重叠但总速率受限 */
    private final AtomicLong nextRequestSlot = new AtomicLong(0);

    public SteamPriceProvider(AppPriceProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getSteam().getTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "steam";
    }

    @Override
    public boolean available() {
        return props.getSteam() != null && props.getSteam().isEnabled();
    }

    @Override
    public List<PriceQuote> fetch(List<PriceTarget> targets) throws Exception {
        if (!available()) {
            throw new IllegalStateException("Steam 直连未启用");
        }
        List<PriceQuote> quotes = Collections.synchronizedList(new ArrayList<>());
        LocalDateTime now = LocalDateTime.now();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(CONCURRENCY, Math.max(1, targets.size())));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PriceTarget t : targets) {
                futures.add(pool.submit(() -> {
                    try {
                        pace();
                        String full = t.fullMarketHashName();
                        String encoded = URLEncoder.encode(full, StandardCharsets.UTF_8);
                        String url = "https://steamcommunity.com/market/priceoverview/?appid=730&currency=23&market_hash_name=" + encoded;
                        JsonNode node = fetchOne(url, 1);
                        if (node != null && node.path("success").asBoolean(false)) {
                            BigDecimal price = parseLowestPrice(node.path("lowest_price").asText(null));
                            Integer volume = node.hasNonNull("volume") ? node.path("volume").asInt() : null;
                            if (price != null && price.signum() > 0) {
                                quotes.add(new PriceQuote(t.itemId(), full, t.exterior(), "steam", price, null, volume, "CNY", now));
                            }
                        }
                    } catch (Exception ignored) {
                        // 单条失败跳过,不中断整体刷新
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

    /** 控制请求开始间隔:池内并发执行,但起始时间按 delayMs 错开,避免触发 Steam 限流 */
    private void pace() throws InterruptedException {
        long delay = Math.max(200, props.getSteam().getDelayMs());
        long slot = nextRequestSlot.getAndAdd(delay);
        long wait = slot - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }

    private JsonNode fetchOne(String url, int attempt) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(props.getSteam().getTimeoutSeconds()))
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://steamcommunity.com/market/")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            if (attempt < 2) {
                Thread.sleep(1500);
                return fetchOne(url, attempt + 1);
            }
            return null;
        }
    }

    /** 解析 "¥ 1,234.56" 这类价格文本 */
    static BigDecimal parseLowestPrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
