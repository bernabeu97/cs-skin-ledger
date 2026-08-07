package com.cs.skinledger.dto;

import java.math.BigDecimal;

public record LotSummary(
        BigDecimal totalBuyCost,
        BigDecimal holdingCost,
        BigDecimal realizedProfit,
        int lotCount,
        int holdingCount,
        int soldCount) {
}