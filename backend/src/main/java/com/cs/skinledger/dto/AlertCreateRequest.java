package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 新建价格提醒 */
public record AlertCreateRequest(
        @NotNull @Positive Long itemId,
        @Size(max = 16) String exterior,
        @NotBlank @Pattern(regexp = "(?i)uu", message = "行情提醒仅支持 UU 价格") String platform,
        @NotBlank @Pattern(regexp = "gt|lt", message = "condition 仅支持 gt/lt") String condition,
        @NotNull @DecimalMin(value = "0.01") BigDecimal threshold) {
}
