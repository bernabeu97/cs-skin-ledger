package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WatchlistResponse(
        Long id,
        Long itemId,
        String itemName,
        String itemNameZh,
        String exterior,
        BigDecimal currentPrice,
        LocalDateTime priceAt,
        BigDecimal change24h,
        BigDecimal changePercent24h,
        LocalDateTime createdAt) {
}
