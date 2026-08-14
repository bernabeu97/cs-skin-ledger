package com.cs.skinledger.dto;

import java.math.BigDecimal;

/** 对账差异:同源记录金额不一致(仅提示,需逐条确认后修正)。 */
public record AmountMismatch(
        Long id,
        String sourceRef,
        String itemName,
        String exterior,
        String field,
        BigDecimal systemValue,
        BigDecimal platformValue) {
}
