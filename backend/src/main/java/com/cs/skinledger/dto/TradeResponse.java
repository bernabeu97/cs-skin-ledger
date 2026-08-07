package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Trade;
import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.domain.TradeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        Long itemId,
        String itemName,
        String itemNameZh,
        String platform,
        TradeDirection direction,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal fee,
        BigDecimal feeRate,
        String currency,
        LocalDateTime tradedAt,
        String externalTradeId,
        TradeStatus status,
        String note,
        String exterior,
        BigDecimal floatValue) {

    public static TradeResponse from(Trade t) {
        ItemBrief item = t.getItem() == null ? null : new ItemBrief(t.getItem());
        return new TradeResponse(
                t.getId(),
                item == null ? null : item.id(),
                item == null ? null : item.marketHashName(),
                item == null ? null : item.nameZh(),
                t.getPlatform(),
                t.getDirection(),
                t.getQuantity(),
                t.getUnitPrice(),
                t.getTotalAmount(),
                t.getFee(),
                t.getFeeRate(),
                t.getCurrency(),
                t.getTradedAt(),
                t.getExternalTradeId(),
                t.getStatus(),
                t.getNote(),
                t.getExterior(),
                t.getFloatValue());
    }

    private record ItemBrief(Long id, String marketHashName, String nameZh) {
        ItemBrief(com.cs.skinledger.domain.Item i) {
            this(i.getId(), i.getMarketHashName(), i.getNameZh());
        }
    }
}