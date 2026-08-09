package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 其他收支新增/编辑请求 */
public record CostRequest(
        @NotBlank @Pattern(regexp = "membership|platform_fee|compensation_expense|compensation_income|refund|other",
                message = "分类仅支持 6 类") String category,
        @NotBlank @Pattern(regexp = "expense|income", message = "direction 仅支持 expense/income") String direction,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        String platform,
        Long itemId,
        String note,
        String sourceRef) {
}