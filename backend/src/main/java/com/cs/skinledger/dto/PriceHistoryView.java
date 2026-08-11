package com.cs.skinledger.dto;

import java.util.List;

public record PriceHistoryView(
        Long itemId,
        String itemName,
        String itemNameZh,
        String exterior,
        String platform,
        String period,
        List<PricePoint> points) {
}
