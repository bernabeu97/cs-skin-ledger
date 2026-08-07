package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.dto.ItemImportResult;
import com.cs.skinledger.repository.ItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 CSGO-API（ByMykel/CSGO-API）导出的本地 JSON 导入饰品数据字典。
 * 文件约定：{category}_en.json 与 {category}_zh_CN.json，按 id 对齐。
 */
@Service
@RequiredArgsConstructor
public class ItemImportService {

    /** 文件分类 -> 入库分类 */
    private static final Map<String, String> CATEGORY_BY_FILE = new LinkedHashMap<>() {{
        put("skins", "skin");
        put("crates", "crate");
        put("stickers", "sticker");
        put("keys", "key");
        put("keychains", "keychain");
        put("patches", "patch");
        put("graffiti", "graffiti");
        put("agents", "agent");
        put("music_kits", "music_kit");
        put("collectibles", "collectible");
    }};

    private final ItemRepository itemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ItemImportResult importFromDirectory(Path dir) throws IOException {
        int created = 0;
        int updated = 0;
        Map<String, Integer> byCategory = new HashMap<>();
        for (Map.Entry<String, String> entry : CATEGORY_BY_FILE.entrySet()) {
            String fileCat = entry.getKey();
            String category = entry.getValue();
            Path enPath = dir.resolve(fileCat + "_en.json");
            Path zhPath = dir.resolve(fileCat + "_zh_CN.json");
            if (!Files.exists(enPath) || !Files.exists(zhPath)) {
                continue;
            }
            JsonNode enArr = objectMapper.readTree(enPath.toFile());
            JsonNode zhArr = objectMapper.readTree(zhPath.toFile());
            Map<String, JsonNode> zhById = new HashMap<>();
            for (JsonNode node : zhArr) {
                zhById.put(node.path("id").asText(), node);
            }
            int catCreated = 0;
            int catUpdated = 0;
            for (JsonNode enNode : enArr) {
                JsonNode zhNode = zhById.get(enNode.path("id").asText());
                if (zhNode == null) {
                    continue;
                }
                String mhn = text(enNode, "market_hash_name");
                if (mhn == null || mhn.isBlank()) {
                    mhn = enNode.path("name").asText("");
                }
                if (mhn.isBlank()) {
                    continue;
                }
                Optional<Item> existing = itemRepository.findByMarketHashName(mhn);
                Item item;
                if (existing.isPresent()) {
                    item = existing.get();
                    catUpdated++;
                } else {
                    item = new Item();
                    item.setMarketHashName(mhn);
                    catCreated++;
                }
                item.setNameZh(zhNode.path("name").asText(mhn));
                item.setCategory("skin".equals(category)
                        ? enNode.path("category").path("name").asText("skin")
                        : category);
                item.setWeapon(zhNode.path("weapon").path("name").asText(null));
                item.setMinFloat(num(enNode, "min_float"));
                item.setMaxFloat(num(enNode, "max_float"));
                item.setWears(wears(zhNode));
                item.setIconUrl(truncate(enNode.path("image").asText(null), 512));
                item.setExternalId(enNode.path("id").asText(null));
                item.setSource("csgo-api");
                itemRepository.save(item);
            }
            created += catCreated;
            updated += catUpdated;
            byCategory.put(fileCat, catCreated + catUpdated);
        }
        return new ItemImportResult(created + updated, created, updated, byCategory);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static BigDecimal num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.isNumber() ? null : v.decimalValue();
    }

    private static List<String> wears(JsonNode zhNode) {
        JsonNode arr = zhNode.get("wears");
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            String name = n.path("name").asText(null);
            if (name != null && !name.isBlank()) {
                out.add(name);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}