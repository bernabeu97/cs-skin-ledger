package com.cs.skinledger.dto;

/** 账本数据健康指标。 */
public record LotHealth(
        int holdingCount,
        int pricedHoldingCount,
        int unpricedHoldingCount,
        double coverageRate,
        int pendingBuyPriceCount) {
}
