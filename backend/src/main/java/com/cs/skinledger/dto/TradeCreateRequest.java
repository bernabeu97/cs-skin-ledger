package com.cs.skinledger.dto;

import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.domain.TradeStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeCreateRequest(
        @NotBlank String itemName,
        @NotBlank @Pattern(regexp = "steam|uu|buff", message = "platform 仅支持 steam/uu/buff") String platform,
        @NotNull TradeDirection direction,
        @NotNull @DecimalMin(value = "0.0001", message = "数量必须大于 0") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0", message = "单价不能为负") BigDecimal unitPrice,
        @DecimalMin(value = "0", message = "手续费不能为负") BigDecimal fee,
        @DecimalMin(value = "0", message = "费率不能为负") BigDecimal feeRate,
        String currency,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime tradedAt,
        String externalTradeId,
        TradeStatus status,
        String note) {
}