package com.cs.skinledger.dto;

import com.cs.skinledger.domain.TradeDirection;

import java.time.LocalDateTime;

public record TradeFilter(
        String platform,
        TradeDirection direction,
        LocalDateTime from,
        LocalDateTime to,
        String q,
        String category) {
}