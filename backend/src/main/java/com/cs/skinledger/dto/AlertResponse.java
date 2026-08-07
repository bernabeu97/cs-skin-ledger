package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Alert;
import com.cs.skinledger.domain.Item;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long itemId,
        String itemName,
        String itemNameZh,
        String platform,
        String condition,
        BigDecimal threshold,
        boolean enabled,
        LocalDateTime triggeredAt) {

    public static AlertResponse from(Alert a) {
        Item item = a.getItem();
        return new AlertResponse(a.getId(), item.getId(), item.getMarketHashName(), item.getNameZh(),
                a.getPlatform(), a.getCondition(), a.getThreshold(),
                Boolean.TRUE.equals(a.getEnabled()), a.getTriggeredAt());
    }
}