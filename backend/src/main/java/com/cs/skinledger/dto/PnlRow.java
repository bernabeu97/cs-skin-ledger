package com.cs.skinledger.dto;

import java.math.BigDecimal;

public record PnlRow(String key, BigDecimal realizedPnl, int tradeCount) {
}