package com.cs.skinledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账本批量导入请求（UU 抓取 / Excel 文件）。
 * holdings：当前持有（转为 HOLDING 批次）；sales：已成交卖出（转为 SOLD 批次）。
 * 注意：buyPrice/sellPrice 为「单件单价」，数量由 quantity 表达；盈亏=数量×卖出价−手续费−数量×买入价。
 */
public record UuImportRequest(
        @Valid List<HoldingImport> holdings,
        @Valid List<SaleImport> sales) {

    public record HoldingImport(
            Long itemId,
            String itemName,
            String itemNameZh,
            String wear,
            BigDecimal floatValue,
            @Positive BigDecimal quantity,
            BigDecimal buyPrice,
            BigDecimal marketPrice,
            String buyTime,
            String buyPlatform,
            String sourceRef,
            String note) {
    }

    public record SaleImport(
            Long itemId,
            String itemName,
            String itemNameZh,
            String wear,
            BigDecimal floatValue,
            @Positive BigDecimal quantity,
            BigDecimal buyPrice,
            String buyTime,
            String buyPlatform,
            @Positive BigDecimal sellPrice,
            BigDecimal fee,
            String sellTime,
            String sellPlatform,
            String sourceRef,
            String note) {

        /** 兼容既有调用方；旧格式没有卖出批次的具体磨损值。 */
        public SaleImport(Long itemId, String itemName, String itemNameZh, String wear,
                          BigDecimal quantity, BigDecimal buyPrice, String buyTime, String buyPlatform,
                          BigDecimal sellPrice, BigDecimal fee, String sellTime, String sellPlatform,
                          String sourceRef, String note) {
            this(itemId, itemName, itemNameZh, wear, null, quantity, buyPrice, buyTime, buyPlatform,
                    sellPrice, fee, sellTime, sellPlatform, sourceRef, note);
        }
    }
}
