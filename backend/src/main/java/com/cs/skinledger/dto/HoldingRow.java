package com.cs.skinledger.dto;

import java.math.BigDecimal;

public record HoldingRow(String itemName, BigDecimal quantity,
                         BigDecimal avgCost, BigDecimal realizedPnl,
                         BigDecimal unrealizedPnl) {
}