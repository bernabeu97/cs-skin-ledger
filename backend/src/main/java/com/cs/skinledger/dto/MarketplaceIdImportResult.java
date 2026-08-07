package com.cs.skinledger.dto;

import java.util.List;

/** 平台商品 ID 映射导入结果 */
public record MarketplaceIdImportResult(int total, int saved, List<String> errors) {
}