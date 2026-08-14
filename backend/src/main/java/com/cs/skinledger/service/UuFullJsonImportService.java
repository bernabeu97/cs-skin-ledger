package com.cs.skinledger.service;

import com.cs.skinledger.dto.UuFullJsonImportResult;
import com.cs.skinledger.dto.UuImportRequest;
import com.cs.skinledger.dto.UuImportResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cs.skinledger.domain.User;

/** 解析浏览器导出的“悠悠有品全量记录”JSON，并复用现有账本导入能力。 */
@Service
@RequiredArgsConstructor
public class UuFullJsonImportService {

    private static final long MAX_FILE_SIZE = 64L * 1024 * 1024;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> EXTERIORS = Map.of(
            "Factory New", "崭新出厂",
            "Minimal Wear", "略有磨损",
            "Field-Tested", "久经沙场",
            "Well-Worn", "破损不堪",
            "Battle-Scarred", "战痕累累");

    private final ObjectMapper objectMapper;
    private final UuImportService uuImportService;

    public UuFullJsonImportResult importFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择悠悠有品全量记录 JSON 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("JSON 文件不能超过 64MB");
        }

        try (InputStream input = file.getInputStream()) {
            return importStream(input, null);
        }
    }

    /** 服务器本地维护任务使用，不开放为按用户名导入的 HTTP 接口。 */
    public UuFullJsonImportResult importFileForUser(Path path, User user) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("JSON 文件为空或超过 64MB");
        }
        try (InputStream input = Files.newInputStream(path)) {
            return importStream(input, user);
        }
    }

    /** 预览：解析文件并统计新增/重复/未匹配，不写入任何数据。 */
    public UuFullJsonImportResult previewFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择悠悠有品全量记录 JSON 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("JSON 文件不能超过 64MB");
        }
        try (InputStream input = file.getInputStream()) {
            return previewStream(input, null);
        }
    }

    private UuFullJsonImportResult previewStream(InputStream input, User targetUser) throws IOException {
        ParsedBundle bundle = parseStream(input);
        UuImportResult imported = targetUser == null
                ? uuImportService.preview(bundle.request())
                : uuImportService.previewForUser(bundle.request(), targetUser);
        return new UuFullJsonImportResult(
                bundle.totalRecords(), bundle.buyRecords(), bundle.sellRecords(), bundle.matchedSales(),
                bundle.unmatchedSales(), bundle.remainingHoldings(), bundle.correctedPriceRecords(),
                bundle.ignoredRecords(), imported.holdingsImported(), imported.holdingsSkippedDuplicates(),
                imported.salesImported(), imported.salesSkippedDuplicates(), bundle.warnings(), imported.errors());
    }

    private UuFullJsonImportResult importStream(InputStream input, User targetUser) throws IOException {
        ParsedBundle bundle = parseStream(input);
        UuImportResult imported = targetUser == null
                ? uuImportService.importData(bundle.request())
                : uuImportService.importDataForUser(bundle.request(), targetUser);
        return new UuFullJsonImportResult(
                bundle.totalRecords(), bundle.buyRecords(), bundle.sellRecords(), bundle.matchedSales(),
                bundle.unmatchedSales(), bundle.remainingHoldings(), bundle.correctedPriceRecords(),
                bundle.ignoredRecords(), imported.holdingsImported(), imported.holdingsSkippedDuplicates(),
                imported.salesImported(), imported.salesSkippedDuplicates(), bundle.warnings(), imported.errors());
    }

    private ParsedBundle parseStream(InputStream input) throws IOException {
        JsonNode root = objectMapper.readTree(input);
        JsonNode recordsNode = root.path("records");
        if (!root.isObject() || !recordsNode.isArray()) {
            throw new IllegalArgumentException("文件格式不正确：缺少 records 数组");
        }

        List<String> warnings = new ArrayList<>();
        List<ParsedRow> buys = new ArrayList<>();
        List<ParsedRow> sells = new ArrayList<>();
        Map<String, Integer> orderOrdinals = new HashMap<>();
        int ignored = 0;
        int correctedPrices = 0;
        int index = 0;

        for (JsonNode node : recordsNode) {
            String direction = text(node, "direction");
            String recordType = text(node, "recordType");
            String status = text(node, "status");
            if (!("buy".equals(direction) || "sell".equals(direction))
                    || (!recordType.isBlank() && !"trade".equals(recordType))
                    || (!status.isBlank() && !"340".equals(status))) {
                ignored++;
                index++;
                continue;
            }

            Parsed parsed = parseRow(node, index, direction, orderOrdinals);
            if (parsed.row() == null) {
                ignored++;
                warnings.add("第 " + (index + 1) + " 条记录缺少饰品名称、价格或时间，已忽略");
            } else {
                if (parsed.priceCorrected()) {
                    correctedPrices++;
                }
                ("buy".equals(direction) ? buys : sells).add(parsed.row());
            }
            index++;
        }

        Comparator<ParsedRow> chronological = Comparator.comparing(ParsedRow::eventTime)
                .thenComparingInt(ParsedRow::index);
        buys.sort(chronological);
        sells.sort(chronological);

        Map<String, ArrayDeque<ParsedRow>> availableBuys = new HashMap<>();
        for (ParsedRow buy : buys) {
            availableBuys.computeIfAbsent(buy.matchKey(), ignoredKey -> new ArrayDeque<>()).addLast(buy);
        }

        List<UuImportRequest.SaleImport> sales = new ArrayList<>();
        int matchedSales = 0;
        int unmatchedSales = 0;
        for (ParsedRow sale : sells) {
            ArrayDeque<ParsedRow> candidates = availableBuys.get(sale.matchKey());
            ParsedRow buy = candidates == null ? null : candidates.peekFirst();
            if (buy != null && !buy.eventTime().isAfter(sale.eventTime())) {
                candidates.removeFirst();
                matchedSales++;
                sales.add(toMatchedSale(buy, sale));
            } else {
                unmatchedSales++;
                sales.add(toUnmatchedSale(sale));
            }
        }

        List<ParsedRow> remaining = availableBuys.values().stream()
                .flatMap(ArrayDeque::stream)
                .sorted(chronological)
                .toList();
        List<UuImportRequest.HoldingImport> holdings = remaining.stream().map(this::toHolding).toList();

        if (correctedPrices > 0) {
            warnings.add("已根据 raw.productDetail.price 修正 " + correctedPrices
                    + " 条批量订单单价（原顶层 price 缩小了 100 倍）");
        }
        if (unmatchedSales > 0) {
            warnings.add(unmatchedSales + " 条卖出记录在文件内找不到更早的同名买入记录，买入价记为 0，需后续补填");
        }

        return new ParsedBundle(
                new UuImportRequest(holdings, sales),
                recordsNode.size(), buys.size(), sells.size(), matchedSales, unmatchedSales, remaining.size(),
                correctedPrices, ignored, warnings);
    }

    private Parsed parseRow(JsonNode node, int index, String direction, Map<String, Integer> orderOrdinals) {
        JsonNode raw = node.path("raw");
        JsonNode detail = raw.path("productDetail");
        String fullItemName = firstText(text(node, "marketHashName"), text(detail, "commodityHashName"));
        String fullItemNameZh = firstText(text(node, "commodityName"), text(detail, "commodityName"));
        if (fullItemName.isBlank() && fullItemNameZh.isBlank()) {
            return new Parsed(null, false);
        }

        BigDecimal topPrice = decimal(node.get("price"));
        BigDecimal detailPrice = decimal(detail.get("price"));
        BigDecimal price = detailPrice == null ? topPrice : detailPrice.movePointLeft(2);
        if (price == null || price.signum() < 0) {
            return new Parsed(null, false);
        }
        boolean corrected = topPrice != null && detailPrice != null && topPrice.compareTo(price) != 0;

        Long millis = longValue(raw.get("finishOrderTime"));
        if (millis == null || millis <= 0) {
            millis = longValue(node.get("createOrderTime"));
        }
        if (millis == null || millis <= 0) {
            return new Parsed(null, corrected);
        }
        LocalDateTime eventTime = Instant.ofEpochMilli(millis).atZone(CHINA_ZONE).toLocalDateTime();

        String orderNo = text(node, "orderNo");
        if (orderNo.isBlank()) {
            orderNo = "time-" + millis;
        }
        String ordinalKey = direction + ":" + orderNo;
        int ordinal = orderOrdinals.merge(ordinalKey, 1, Integer::sum);
        String detailNo = text(node, "orderDetailNo");
        String identity = detailNo.isBlank() ? orderNo + ":" + ordinal : detailNo;
        String sourceRef = "uu:full:" + direction + ":" + identity;

        String exterior = exterior(fullItemName, detail);
        String itemName = stripExterior(fullItemName);
        String itemNameZh = stripExteriorZh(fullItemNameZh);
        BigDecimal floatValue = firstDecimal(decimal(node.get("wear")), decimal(detail.get("abrade")));
        BigDecimal serviceFee = decimal(raw.get("serviceFee"));
        BigDecimal commodityNum = decimal(raw.get("commodityNum"));
        BigDecimal fee = BigDecimal.ZERO;
        if (serviceFee != null && serviceFee.signum() > 0) {
            fee = serviceFee.movePointLeft(2);
            if (commodityNum != null && commodityNum.signum() > 0) {
                fee = fee.divide(commodityNum, 4, java.math.RoundingMode.HALF_UP);
            }
        }

        // 配对必须包含磨损等级；字典匹配则使用去掉磨损后缀的基础饰品名。
        String matchKey = !fullItemName.isBlank() ? fullItemName.trim() : "zh:" + fullItemNameZh.trim();
        ParsedRow row = new ParsedRow(index, direction, orderNo, sourceRef, matchKey, itemName,
                itemNameZh, exterior, floatValue, price, fee, eventTime);
        return new Parsed(row, corrected);
    }

    private UuImportRequest.HoldingImport toHolding(ParsedRow buy) {
        return new UuImportRequest.HoldingImport(null, buy.itemName(), buy.itemNameZh(), buy.exterior(),
                buy.floatValue(), BigDecimal.ONE, buy.price(), null, format(buy.eventTime()), "uu",
                buy.sourceRef(), "UU全量JSON；买单 " + buy.orderNo());
    }

    private UuImportRequest.SaleImport toMatchedSale(ParsedRow buy, ParsedRow sale) {
        return new UuImportRequest.SaleImport(null, buy.itemName(), buy.itemNameZh(), buy.exterior(),
                buy.floatValue(), BigDecimal.ONE, buy.price(), format(buy.eventTime()), "uu",
                sale.price(), sale.fee(), format(sale.eventTime()), "uu", sale.sourceRef(),
                "UU全量JSON；买单 " + buy.orderNo() + "；卖单 " + sale.orderNo() + "；同名FIFO配对");
    }

    private UuImportRequest.SaleImport toUnmatchedSale(ParsedRow sale) {
        return new UuImportRequest.SaleImport(null, sale.itemName(), sale.itemNameZh(), sale.exterior(),
                sale.floatValue(), BigDecimal.ONE, null, null, "uu", sale.price(), sale.fee(),
                format(sale.eventTime()), "uu", sale.sourceRef(),
                "UU全量JSON；卖单 " + sale.orderNo() + "；文件内无更早同名买入");
    }

    private String exterior(String itemName, JsonNode detail) {
        String hashName = text(detail, "exteriorHashName");
        if (EXTERIORS.containsKey(hashName)) {
            return EXTERIORS.get(hashName);
        }
        for (Map.Entry<String, String> entry : EXTERIORS.entrySet()) {
            if (itemName.endsWith("(" + entry.getKey() + ")")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String stripExterior(String itemName) {
        String value = itemName == null ? "" : itemName.trim();
        for (String exterior : EXTERIORS.keySet()) {
            String suffix = " (" + exterior + ")";
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length()).trim();
            }
        }
        return value;
    }

    private String stripExteriorZh(String itemNameZh) {
        String value = itemNameZh == null ? "" : itemNameZh.trim();
        for (String exterior : EXTERIORS.values()) {
            String suffix = " (" + exterior + ")";
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length()).trim();
            }
        }
        return value;
    }

    private String format(LocalDateTime time) {
        return TIME_FORMAT.format(time);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longValue(JsonNode value) {
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstText(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static BigDecimal firstDecimal(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private record Parsed(ParsedRow row, boolean priceCorrected) {
    }

    private record ParsedBundle(
            UuImportRequest request,
            int totalRecords,
            int buyRecords,
            int sellRecords,
            int matchedSales,
            int unmatchedSales,
            int remainingHoldings,
            int correctedPriceRecords,
            int ignoredRecords,
            List<String> warnings) {
    }

    private record ParsedRow(
            int index,
            String direction,
            String orderNo,
            String sourceRef,
            String matchKey,
            String itemName,
            String itemNameZh,
            String exterior,
            BigDecimal floatValue,
            BigDecimal price,
            BigDecimal fee,
            LocalDateTime eventTime) {
    }
}
