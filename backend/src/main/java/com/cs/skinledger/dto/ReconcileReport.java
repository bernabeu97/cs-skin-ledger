package com.cs.skinledger.dto;

import java.util.List;

/** 悠悠双向对账报告。 */
public record ReconcileReport(
        int totalRecords,
        int platformOnlyHoldings,
        int platformOnlySales,
        int systemOnlyCount,
        List<AmountMismatch> amountMismatches,
        List<String> warnings) {
}
