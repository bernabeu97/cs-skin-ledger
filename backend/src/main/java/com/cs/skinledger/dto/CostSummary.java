package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.util.List;

/** 其他收支汇总 */
public record CostSummary(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        List<CategoryCost> byCategory) {

    public record CategoryCost(String category, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }
}