package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Trade;
import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.domain.TradeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        String itemName,
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
        String note) {

    public static TradeResponse from(Trade t) {
        return new TradeResponse(
                t.getId(),
                t.getItem().getMarketHashName(),
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
                t.getNote());
    }
}