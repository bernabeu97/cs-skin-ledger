package com.cs.skinledger.dto;

import java.math.BigDecimal;

/** 按单品/分类聚合的盈亏行。 */
public record AggregateRow(
        String key,
        BigDecimal realizedPnl,
        BigDecimal buyCost,
        int soldCount,
        int winningSoldCount) {
}
