package com.cs.skinledger.dto;

import java.util.Map;

/**
 * 行情模块配置状态（前端用于提示是否已配置 CSQAQ Token 等）。
 */
public record PriceConfigView(
        boolean csqaqConfigured,
        boolean steamDirectEnabled,
        boolean youpinDirectEnabled,
        long refreshIntervalMinutes,
        Map<String, String> messages) {
}
