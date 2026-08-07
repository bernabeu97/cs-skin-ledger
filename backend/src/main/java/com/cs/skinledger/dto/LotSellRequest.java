package com.cs.skinledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LotSellRequest(
        @NotNull @DecimalMin(value = "0", message = "出售价不能为负") BigDecimal sellPrice,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime sellTime,
        @Pattern(regexp = "steam|uu|buff", message = "sellPlatform 仅支持 steam/uu/buff") String sellPlatform,
        @DecimalMin(value = "0", message = "手续费不能为负") BigDecimal fee) {
}