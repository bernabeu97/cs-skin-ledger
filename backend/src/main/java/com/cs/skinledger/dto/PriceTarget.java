package com.cs.skinledger.dto;

import com.cs.skinledger.util.WearNames;

import java.math.BigDecimal;

/**
 * 价格采集目标：一条持有批次对应的物品。
 */
public record PriceTarget(Long itemId, String marketHashName, String exterior) {

    /**
     * 完整市场名：基础名 + 磨损后缀。
     * 例：AK-47 | Hydroponic + 久经沙场 -> AK-47 | Hydroponic (Field-Tested)
     */
    public String fullMarketHashName() {
        String enWear = WearNames.toEnglish(exterior);
        if (enWear == null || enWear.isBlank()) {
            return marketHashName;
        }
        return marketHashName + " (" + enWear + ")";
    }
}