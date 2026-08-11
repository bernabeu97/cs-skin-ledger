package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MarketIndexView(
        String kind,
        String period,
        BigDecimal currentValue,
        BigDecimal marketValue,
        BigDecimal changePercent,
        LocalDateTime asOf,
        List<PricePoint> points) {
}
