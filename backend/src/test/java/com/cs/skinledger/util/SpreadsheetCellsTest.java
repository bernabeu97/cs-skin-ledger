package com.cs.skinledger.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpreadsheetCellsTest {
    @Test
    void prefixesFormulaLikeText() {
        assertEquals("'=HYPERLINK(\"https://example.invalid\")", SpreadsheetCells.csv("=HYPERLINK(\"https://example.invalid\")"));
        assertEquals("normal", SpreadsheetCells.csv("normal"));
        assertEquals("", SpreadsheetCells.csv(null));
    }
}
