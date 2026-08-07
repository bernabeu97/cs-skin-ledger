package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LotResponse(
        Long id,
        Long itemId,
        String itemName,
        String itemNameZh,
        BigDecimal quantity,
        String exterior,
        BigDecimal floatValue,
        BigDecimal buyPrice,
        LocalDateTime buyTime,
        String buyPlatform,
        BigDecimal sellPrice,
        LocalDateTime sellTime,
        String sellPlatform,
        BigDecimal fee,
        BigDecimal actualIncome,
        BigDecimal profit,
        LotStatus status,
        String note) {

    public static LotResponse from(Lot lot) {
        Item item = lot.getItem();
        return new LotResponse(
                lot.getId(),
                item.getId(),
                item.getMarketHashName(),
                item.getNameZh(),
                lot.getQuantity(),
                lot.getExterior(),
                lot.getFloatValue(),
                lot.getBuyPrice(),
                lot.getBuyTime(),
                lot.getBuyPlatform(),
                lot.getSellPrice(),
                lot.getSellTime(),
                lot.getSellPlatform(),
                lot.getFee(),
                lot.getActualIncome(),
                lot.getProfit(),
                lot.getStatus(),
                lot.getNote());
    }
}