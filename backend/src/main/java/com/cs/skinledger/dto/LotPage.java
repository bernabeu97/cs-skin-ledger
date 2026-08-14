package com.cs.skinledger.dto;

import java.util.List;

/** 账本分页结果。 */
public record LotPage(
        List<LotResponse> items,
        long total,
        int page,
        int size,
        int totalPages) {
}
