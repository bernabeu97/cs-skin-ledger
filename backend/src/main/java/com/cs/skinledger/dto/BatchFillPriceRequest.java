package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record BatchFillPriceRequest(
        @NotEmpty List<Long> ids,
        @DecimalMin(value = "0.0") BigDecimal buyPrice) {
}
