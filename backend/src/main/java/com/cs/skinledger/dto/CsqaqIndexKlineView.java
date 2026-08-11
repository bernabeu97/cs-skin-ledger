package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.util.List;

/** CSQAQ 指数 K 线。 */
public record CsqaqIndexKlineView(long indexId, String period, List<Candle> points) {
    public record Candle(
            String at,
            BigDecimal open,
            BigDecimal close,
            BigDecimal high,
            BigDecimal low,
            long volume
    ) {
    }
}
