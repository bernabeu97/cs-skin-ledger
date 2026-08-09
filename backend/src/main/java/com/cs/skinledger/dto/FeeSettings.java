package com.cs.skinledger.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 平台费率配置（手续费比例，0.005 = 0.5%） */
public record FeeSettings(
        @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal steam,
        @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal uu,
        @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal buff) {

    public static FeeSettings defaults() {
        return new FeeSettings(new BigDecimal("0.15"), new BigDecimal("0.005"), new BigDecimal("0.025"));
    }
}