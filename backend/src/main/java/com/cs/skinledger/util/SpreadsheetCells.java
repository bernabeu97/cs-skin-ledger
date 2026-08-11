package com.cs.skinledger.util;

public final class SpreadsheetCells {
    private SpreadsheetCells() {}

    /** 防止用户可控文本在 CSV 被表格软件当作公式执行。 */
    public static String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) return "'" + text;
        return text;
    }
}
