package com.cs.skinledger.service;

import com.cs.skinledger.domain.MarketplaceId;
import com.cs.skinledger.dto.MarketplaceIdImportResult;
import com.cs.skinledger.repository.MarketplaceIdRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * 导入平台商品 ID 映射（cs2_marketplaceids.json，来自 chinap/buff163-ids）。
 * 文件结构：{"items": {"AK-47 | Redline (Field-Tested)": {"youpin_id": 123, "buff_goods_id": 456, ...}}}
 */
@Service
@RequiredArgsConstructor
public class MarketplaceIdImportService {

    private static final int BATCH = 2000;

    private final MarketplaceIdRepository repository;
    private final ObjectMapper mapper;

    @Transactional
    public MarketplaceIdImportResult importFromFile(Path file) throws Exception {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("文件不存在: " + file);
        }
        JsonNode root = mapper.readTree(file.toFile());
        JsonNode items = root.path("items");
        if (!items.isObject()) {
            throw new IllegalArgumentException("JSON 缺少 items 对象");
        }
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new java.util.HashSet<>();
        int total = 0;
        int saved = 0;
        List<MarketplaceId> batch = new ArrayList<>(BATCH);
        Iterator<Map.Entry<String, JsonNode>> it = items.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String lowerKey = e.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!seenNames.add(lowerKey)) {
                continue;
            }
            total++;
            JsonNode v = e.getValue();
            try {
                MarketplaceId mid = new MarketplaceId();
                mid.setMarketHashName(e.getKey());
                mid.setYoupinId(longOrNull(v.get("youpin_id")));
                mid.setBuffGoodsId(longOrNull(v.get("buff163_goods_id")));
                mid.setBuffmarketGoodsId(longOrNull(v.get("buffmarket_goods_id")));
                mid.setCsmoneyNameid(longOrNull(v.get("csmoney_nameid")));
                batch.add(mid);
                saved++;
                if (batch.size() >= BATCH) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            } catch (Exception ex) {
                errors.add(e.getKey() + ": " + ex.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
        }
        return new MarketplaceIdImportResult(total, saved, errors);
    }

    private Long longOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isNumber() ? node.longValue() : null;
    }
}