package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条平台价格（steam/uu/buff）。
 */
public record PriceQuote(
        Long itemId,
        String marketHashName,
        String exterior,
        String platform,
        BigDecimal price,
        BigDecimal buyPrice,
        Integer volume,
        String currency,
        LocalDateTime capturedAt) {
}
