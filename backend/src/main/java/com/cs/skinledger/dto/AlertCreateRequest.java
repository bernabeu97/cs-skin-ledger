package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** 新建价格提醒 */
public record AlertCreateRequest(
        @NotNull @Positive Long itemId,
        @NotBlank String platform,
        @NotBlank @Pattern(regexp = "gt|lt", message = "condition 仅支持 gt/lt") String condition,
        @NotNull @DecimalMin(value = "0.01") BigDecimal threshold) {
}