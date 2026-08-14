package com.cs.skinledger.dto;

import java.math.BigDecimal;

/** 账本统计：已实现 ROI、胜率等（总 ROI 需结合行情估值在前端计算）。 */
public record LotStats(
        BigDecimal realizedRoi,
        double winRate,
        int soldCount,
        int winningSoldCount,
        int lotCount,
        int holdingCount) {
}
