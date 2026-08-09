package com.cs.skinledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * UU 库存/交易批量导入请求。
 * holdings：当前库存（转为 HOLDING 批次）；sales：已成交卖出（转为 SOLD 批次）。
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
            String note) {
    }

    public record SaleImport(
            Long itemId,
            String itemName,
            String itemNameZh,
            String wear,
            @Positive BigDecimal sellPrice,
            BigDecimal fee,
            String sellTime,
            String note) {
    }
}