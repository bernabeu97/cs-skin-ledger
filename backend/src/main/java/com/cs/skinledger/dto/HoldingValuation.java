package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条持有批次的估值（含最新市价与浮动盈亏）。
 */
public record HoldingValuation(
        Long lotId,
        Long itemId,
        String itemName,
        String itemNameZh,
        String exterior,
        BigDecimal quantity,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        String pricePlatform,
        LocalDateTime priceAt,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        java.util.Map<String, BigDecimal> latestPrices) {

    /** 未获取到行情时的空估值 */
    public static HoldingValuation withoutPrice(Long lotId, Long itemId, String itemName, String itemNameZh,
                                                String exterior, BigDecimal quantity, BigDecimal buyPrice) {
        return new HoldingValuation(lotId, itemId, itemName, itemNameZh, exterior, quantity, buyPrice,
                null, null, null, null, null, java.util.Map.of());
    }
}