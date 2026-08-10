package com.cs.skinledger.dto;

import java.util.List;

/** 悠悠有品“全量记录”导出 JSON 的解析和入库结果。 */
public record UuFullJsonImportResult(
        int totalRecords,
        int buyRecords,
        int sellRecords,
        int matchedSales,
        int unmatchedSales,
        int remainingHoldings,
        int correctedPriceRecords,
        int ignoredRecords,
        int holdingsImported,
        int holdingsSkippedDuplicates,
        int salesImported,
        int salesSkippedDuplicates,
        List<String> warnings,
        List<String> errors) {
}
