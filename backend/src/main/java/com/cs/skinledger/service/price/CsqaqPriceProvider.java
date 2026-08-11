package com.cs.skinledger.service.price;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceTarget;
import com.cs.skinledger.service.CsqaqTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CSQAQ 数据开放 API（主数据源）。
 * 一个请求最多 50 个 marketHashName，一次返回 steam/uu/buff 三平台价格。
 * 文档：https://docs.csqaq.com
 * 需要免费注册 https://csqaq.com 获取 ApiToken 并绑定本机 IP 白名单。
 */
@Slf4j
@Component
public class CsqaqPriceProvider implements PriceProvider {

    private static final int BATCH_SIZE = 50;

    private final AppPriceProperties props;
    private final CsqaqTokenService tokenService;
    private final CsqaqRequestGate requestGate;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public CsqaqPriceProvider(AppPriceProperties props, ObjectMapper mapper, CsqaqTokenService tokenService,
                              CsqaqRequestGate requestGate) {
        this.props = props;
        this.mapper = mapper;
        this.tokenService = tokenService;
        this.requestGate = requestGate;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getCsqaq().getTimeoutSeconds())))
                .build();
    }

    @Override
    public String name() {
        return "csqaq";
    }

    @Override
    public boolean available() {
        return tokenService.currentToken().isPresent();
    }

    @Override
    public List<PriceQuote> fetch(List<PriceTarget> targets) throws Exception {
        String apiToken = tokenService.currentToken()
                .orElseThrow(() -> new IllegalStateException("CSQAQ ApiToken 未绑定，请在设置中绑定"));
        List<PriceQuote> quotes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String url = props.getCsqaq().getBaseUrl() + "/api/v1/goods/getPriceByMarketHashName";
        for (int i = 0; i < targets.size(); i += BATCH_SIZE) {
            List<PriceTarget> batch = targets.subList(i, Math.min(i + BATCH_SIZE, targets.size()));
            List<String> names = batch.stream().map(PriceTarget::fullMarketHashName).toList();
            String body = mapper.writeValueAsString(Map.of("marketHashNameList", names));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(props.getCsqaq().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("ApiToken", apiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // CSQAQ 限流较严：单个批次失败重试 2 次，批次之间间隔 1.5s，失败不中断后续批次
            boolean batchOk = false;
            for (int attempt = 1; attempt <= 3 && !batchOk; attempt++) {
                try {
                    requestGate.awaitTurn();
                    HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonNode root = mapper.readTree(resp.body());
                    int code = root.path("code").asInt(-1);
                    if (code != 200) {
                        throw new IllegalStateException("CSQAQ 返回失败: code=" + code + ", msg=" + root.path("msg").asText());
                    }
                    JsonNode success = root.path("data").path("success");
                    for (PriceTarget t : batch) {
                        JsonNode node = success.get(t.fullMarketHashName());
                        if (node == null || node.isNull() || node.isMissingNode()) {
                            continue;
                        }
                        addQuote(quotes, t, "steam", node.get("steamSellPrice"), node.get("steamSellNum"), now);
                        addQuote(quotes, t, "uu", node.get("yyypSellPrice"), node.get("yyypSellNum"), now);
                        addQuote(quotes, t, "buff", node.get("buffSellPrice"), node.get("buffSellNum"), now);
                    }
                    batchOk = true;
                } catch (Exception e) {
                    if (attempt >= 3) {
                        errors.add("CSQAQ 批次" + (i / BATCH_SIZE + 1) + " 失败: " + e.getMessage());
                    } else {
                        Thread.sleep(2000L * attempt);
                    }
                }
            }
            if (i + BATCH_SIZE < targets.size()) {
                Thread.sleep(1500);
            }
        }
        if (!errors.isEmpty()) {
            if (quotes.isEmpty()) {
                throw new IllegalStateException(String.join("；", errors));
            }
            log.warn("CSQAQ 部分批次失败（已跳过）：{}", String.join("；", errors));
        }
        return quotes;
    }

    private void addQuote(List<PriceQuote> quotes, PriceTarget t, String platform,
                          JsonNode priceNode, JsonNode volumeNode, LocalDateTime now) {
        if (priceNode == null || priceNode.isNull() || priceNode.isMissingNode()) {
            return;
        }
        BigDecimal price = priceNode.decimalValue();
        if (price.signum() <= 0) {
            return;
        }
        Integer volume = volumeNode == null || volumeNode.isNull() || volumeNode.isMissingNode()
                ? null : volumeNode.asInt();
        quotes.add(new PriceQuote(t.itemId(), t.fullMarketHashName(), t.exterior(), platform, price, null, volume, "CNY", now));
    }
}
