package com.cs.skinledger.util;

import java.util.Map;

/**
 * 磨损等级中英文对照（用于把批次里的磨损等级拼进完整 marketHashName 查询价格）。
 */
public final class WearNames {

    private static final Map<String, String> ZH_TO_EN = Map.of(
            "崭新出厂", "Factory New",
            "略有磨损", "Minimal Wear",
            "久经沙场", "Field-Tested",
            "破损不堪", "Well-Worn",
            "战痕累累", "Battle-Scarred"
    );

    private WearNames() {
    }

    /** 中文磨损 -> 英文（含括号后缀用，例如 "AK-47 | Redline (Field-Tested)"）；已是英文则原样返回 */
    public static String toEnglish(String wear) {
        if (wear == null || wear.isBlank()) {
            return wear;
        }
        return ZH_TO_EN.getOrDefault(wear.trim(), wear.trim());
    }
}