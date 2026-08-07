package com.cs.skinledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LotCreateRequest(
        Long itemId,
        @NotBlank String itemName,
        @DecimalMin(value = "0.0001", message = "数量必须大于 0") BigDecimal quantity,
        @Size(max = 16, message = "磨损等级过长") String exterior,
        @DecimalMin(value = "0", message = "磨损值不能小于 0")
        @DecimalMax(value = "1", message = "磨损值不能大于 1") BigDecimal floatValue,
        @NotNull @DecimalMin(value = "0", message = "买入价不能为负") BigDecimal buyPrice,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime buyTime,
        @NotBlank @Pattern(regexp = "steam|uu|buff", message = "buyPlatform 仅支持 steam/uu/buff") String buyPlatform,
        @Size(max = 500) String note) {
}