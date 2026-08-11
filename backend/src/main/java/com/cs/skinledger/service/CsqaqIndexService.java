package com.cs.skinledger.service;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.dto.CsqaqIndexKlineView;
import com.cs.skinledger.dto.CsqaqIndexView;
import com.cs.skinledger.service.price.CsqaqRequestGate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** CSQAQ 官方指数数据代理；密钥只在后端使用。 */
@Service
public class CsqaqIndexService {

    private static final Duration INDEX_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration KLINE_CACHE_TTL = Duration.ofMinutes(2);
    private static final Set<String> PERIODS = Set.of("1hour", "4hour", "1day", "7day");

    private final AppPriceProperties properties;
    private final CsqaqTokenService tokenService;
    private final CsqaqRequestGate requestGate;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private volatile CacheEntry<List<CsqaqIndexView>> indexCache;
    private final Map<String, CacheEntry<CsqaqIndexKlineView>> klineCache = new ConcurrentHashMap<>();

    public CsqaqIndexService(AppPriceProperties properties, CsqaqTokenService tokenService,
                             CsqaqRequestGate requestGate, ObjectMapper mapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.requestGate = requestGate;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getCsqaq().getTimeoutSeconds())))
                .build();
    }

    public synchronized List<CsqaqIndexView> indices() {
        if (indexCache != null && indexCache.valid()) return indexCache.value();
        JsonNode root = get("/api/v1/current_data?type=init", null);
        List<CsqaqIndexView> result = parseIndices(root);
        indexCache = new CacheEntry<>(List.copyOf(result), Instant.now().plus(INDEX_CACHE_TTL));
        return indexCache.value();
    }

    public synchronized CsqaqIndexKlineView kline(long id, String period) {
        if (id <= 0) throw new IllegalArgumentException("指数 ID 不正确");
        if (!PERIODS.contains(period)) throw new IllegalArgumentException("K线周期仅支持 1hour/4hour/1day/7day");
        String key = id + ":" + period;
        CacheEntry<CsqaqIndexKlineView> cached = klineCache.get(key);
        if (cached != null && cached.valid()) return cached.value();
        String token = tokenService.currentToken()
                .orElseThrow(() -> new IllegalArgumentException("CSQAQ ApiToken 未绑定，请先在设置中绑定"));
        JsonNode root = get("/api/v1/sub/kline?id=" + id + "&type=" + period, token);
        CsqaqIndexKlineView result = new CsqaqIndexKlineView(id, period, parseKline(root));
        klineCache.put(key, new CacheEntry<>(result, Instant.now().plus(KLINE_CACHE_TTL)));
        return result;
    }

    private JsonNode get(String path, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getCsqaq().getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(properties.getCsqaq().getTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("User-Agent", "CS-Skin-Ledger/0.1")
                    .GET();
            if (token != null) builder.header("ApiToken", token);
            requestGate.awaitTurn();
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = mapper.readTree(response.body());
            int code = root.path("code").asInt(response.statusCode());
            if (response.statusCode() >= 400 || code != 200) {
                throw new ExternalServiceException("CSQAQ 指数接口失败: " + root.path("msg").asText("HTTP " + response.statusCode()));
            }
            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("CSQAQ 指数请求已取消", e);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("CSQAQ 指数请求失败: " + e.getMessage(), e);
        }
    }

    static List<CsqaqIndexView> parseIndices(JsonNode root) {
        List<CsqaqIndexView> result = new ArrayList<>();
        for (JsonNode node : root.path("data").path("sub_index_data")) {
            result.add(new CsqaqIndexView(
                    node.path("id").asLong(), node.path("name").asText(), node.path("name_key").asText(),
                    textOrNull(node.get("img")), decimal(node, "market_index"), decimal(node, "chg_num"),
                    decimal(node, "chg_rate"), decimal(node, "open"), decimal(node, "close"),
                    decimal(node, "high"), decimal(node, "low"), textOrNull(node.get("updated_at"))));
        }
        return result;
    }

    static List<CsqaqIndexKlineView.Candle> parseKline(JsonNode root) {
        List<CsqaqIndexKlineView.Candle> result = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            long timestamp = node.path("t").asLong();
            if (timestamp <= 0) continue;
            result.add(new CsqaqIndexKlineView.Candle(
                    Instant.ofEpochMilli(timestamp).toString(), decimal(node, "o"), decimal(node, "c"),
                    decimal(node, "h"), decimal(node, "l"), node.path("v").asLong(0)));
        }
        return result;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
        boolean valid() { return Instant.now().isBefore(expiresAt); }
    }
}
