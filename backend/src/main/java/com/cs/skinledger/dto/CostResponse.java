package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.OtherCostEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CostResponse(
        Long id,
        String category,
        String direction,
        BigDecimal amount,
        LocalDateTime occurredAt,
        String platform,
        Long itemId,
        String itemName,
        String itemNameZh,
        String note,
        String sourceRef) {

    public static CostResponse from(OtherCostEntry e) {
        Item item = e.getItem();
        return new CostResponse(
                e.getId(), e.getCategory(), e.getDirection(), e.getAmount(), e.getOccurredAt(),
                e.getPlatform(),
                item == null ? null : item.getId(),
                item == null ? null : item.getMarketHashName(),
                item == null ? null : item.getNameZh(),
                e.getNote(), e.getSourceRef());
    }
}