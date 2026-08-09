package com.cs.skinledger.dto;

import java.util.List;

/** UU 导入结果 */
public record UuImportResult(
        int holdingsRequested,
        int holdingsImported,
        int holdingsSkippedDuplicates,
        int salesRequested,
        int salesImported,
        int salesSkippedDuplicates,
        List<String> errors) {
}