package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 持仓估值汇总：成本、当前市值、浮动盈亏。
 */
public record PortfolioValuation(
        BigDecimal holdingCost,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        LocalDateTime priceAsOf,
        List<HoldingValuation> rows) {
}