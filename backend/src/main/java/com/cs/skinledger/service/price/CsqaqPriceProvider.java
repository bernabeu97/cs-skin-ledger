package com.cs.skinledger.service.price;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Component
public class CsqaqPriceProvider implements PriceProvider {

    private static final int BATCH_SIZE = 50;

    private final AppPriceProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public CsqaqPriceProvider(AppPriceProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
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
        return props.getCsqaq() != null
                && props.getCsqaq().getApiToken() != null
                && !props.getCsqaq().getApiToken().isBlank();
    }

    @Override
    public List<PriceQuote> fetch(List<PriceTarget> targets) throws Exception {
        if (!available()) {
            throw new IllegalStateException("CSQAQ ApiToken 未配置，请在 application.yml 或环境变量 CSQAQ_TOKEN 中配置");
        }
        List<PriceQuote> quotes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String url = props.getCsqaq().getBaseUrl() + "/api/v1/goods/getPriceByMarketHashName";
        for (int i = 0; i < targets.size(); i += BATCH_SIZE) {
            List<PriceTarget> batch = targets.subList(i, Math.min(i + BATCH_SIZE, targets.size()));
            List<String> names = batch.stream().map(PriceTarget::fullMarketHashName).toList();
            String body = mapper.writeValueAsString(Map.of("marketHashNameList", names));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(props.getCsqaq().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("ApiToken", props.getCsqaq().getApiToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
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
        quotes.add(new PriceQuote(t.itemId(), t.fullMarketHashName(), platform, price, null, volume, "CNY", now));
    }
}