package com.cs.skinledger.dto;

import java.math.BigDecimal;

/** CSQAQ 公开指数概览。 */
public record CsqaqIndexView(
        long id,
        String name,
        String nameKey,
        String imageUrl,
        BigDecimal marketIndex,
        BigDecimal changeValue,
        BigDecimal changeRate,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        String updatedAt
) {
}
