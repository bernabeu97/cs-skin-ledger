package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioView(BigDecimal totalCost,
                            BigDecimal totalRealizedPnl,
                            List<HoldingRow> holdings) {
}