package com.cs.skinledger.web;

import com.cs.skinledger.dto.UuImportRequest;
import com.cs.skinledger.dto.UuImportResult;
import com.cs.skinledger.service.UuImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UU（悠悠有品）库存/交易数据导入。
 * 数据由浏览器端从 UU 网页抓取后提交（本机流水线）。
 */
@RestController
@RequestMapping("/api/sync/uu")
@RequiredArgsConstructor
public class UuSyncController {

    private final UuImportService uuImportService;

    @PostMapping("/import")
    public UuImportResult importData(@Valid @RequestBody UuImportRequest req) {
        return uuImportService.importData(req);
    }
}