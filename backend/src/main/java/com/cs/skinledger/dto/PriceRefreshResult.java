package com.cs.skinledger.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次行情刷新的结果汇总。
 */
public record PriceRefreshResult(
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int requested,
        int ok,
        int failed,
        List<String> errors,
        java.util.Map<String, Integer> byPlatform,
        List<AlertResponse> triggeredAlerts) {
}
