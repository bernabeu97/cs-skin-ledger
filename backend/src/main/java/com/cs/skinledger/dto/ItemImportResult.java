package com.cs.skinledger.dto;

import java.util.Map;

public record ItemImportResult(int total, int created, int updated, Map<String, Integer> byCategory) {
}