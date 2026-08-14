package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 对账确认后以平台为准修正单条金额。field: buy=sell 表示修正买入价/出售价。 */
public record FixPriceRequest(
        @NotNull Long id,
        @NotBlank String field,
        @DecimalMin(value = "0.0") BigDecimal price) {
}
