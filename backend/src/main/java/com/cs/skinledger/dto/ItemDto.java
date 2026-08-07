package com.cs.skinledger.dto;

import com.cs.skinledger.domain.Item;

import java.math.BigDecimal;
import java.util.List;

public record ItemDto(
        Long id,
        String marketHashName,
        String nameZh,
        String weapon,
        String category,
        BigDecimal minFloat,
        BigDecimal maxFloat,
        List<String> wears) {

    public static ItemDto from(Item i) {
        return new ItemDto(i.getId(), i.getMarketHashName(), i.getNameZh(), i.getWeapon(),
                i.getCategory(), i.getMinFloat(), i.getMaxFloat(), i.getWears());
    }
}