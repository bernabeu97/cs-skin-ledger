package com.cs.skinledger.web;

import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.dto.AggregateRow;
import com.cs.skinledger.dto.BatchFillPriceRequest;
import com.cs.skinledger.dto.BatchIdsRequest;
import com.cs.skinledger.dto.LotCreateRequest;
import com.cs.skinledger.dto.LotFilter;
import com.cs.skinledger.dto.LotPage;
import com.cs.skinledger.dto.LotResponse;
import com.cs.skinledger.dto.LotSellRequest;
import com.cs.skinledger.dto.LotStats;
import com.cs.skinledger.dto.LotSummary;
import com.cs.skinledger.dto.PnlGroupBy;
import com.cs.skinledger.dto.PnlRow;
import com.cs.skinledger.service.LotExportService;
import com.cs.skinledger.service.LotService;
import com.cs.skinledger.service.LotWorkbookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final LotExportService lotExportService;
    private final LotWorkbookService lotWorkbookService;

    @GetMapping
    public List<LotResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LotStatus status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return lotService.list(new LotFilter(q, status, platform, from, to, null));
    }

    @GetMapping("/page")
    public LotPage page(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LotStatus status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Boolean noprice) {
        return lotService.page(new LotFilter(q, status, platform, from, to, noprice), page, size);
    }

    @GetMapping("/stats")
    public LotStats stats() {
        return lotService.stats();
    }

    @GetMapping("/aggregate")
    public List<AggregateRow> aggregate(@RequestParam(value = "group_by", defaultValue = "item") PnlGroupBy groupBy) {
        return lotService.aggregate(groupBy);
    }

    @GetMapping("/{id}")
    public LotResponse get(@PathVariable Long id) {
        return lotService.list(new LotFilter(null, null, null, null, null, null)).stream()
                .filter(l -> l.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + id));
    }

    @PostMapping
    public LotResponse create(@Valid @RequestBody LotCreateRequest req) {
        return lotService.create(req);
    }

    @PutMapping("/{id}")
    public LotResponse update(@PathVariable Long id, @Valid @RequestBody LotCreateRequest req) {
        return lotService.update(id, req);
    }

    @PostMapping("/{id}/sell")
    public LotResponse updateSell(@PathVariable Long id, @Valid @RequestBody LotSellRequest req) {
        return lotService.updateSell(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        lotService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch/fill-price")
    public ResponseEntity<Map<String, Integer>> batchFillPrice(@Valid @RequestBody BatchFillPriceRequest req) {
        int updated = lotService.batchFillBuyPrice(req.ids(), req.buyPrice());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/batch/delete")
    public ResponseEntity<Map<String, Integer>> batchDelete(@Valid @RequestBody BatchIdsRequest req) {
        int deleted = lotService.batchDelete(req.ids());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/trash")
    public List<LotResponse> trash() {
        return lotService.trash();
    }

    @PostMapping("/{id}/restore")
    public LotResponse restore(@PathVariable Long id) {
        return lotService.restore(id);
    }

    @DeleteMapping("/{id}/purge")
    public ResponseEntity<Void> purge(@PathVariable Long id) {
        lotService.purge(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public LotSummary summary() {
        return lotService.summary();
    }

    @GetMapping("/pnl")
    public List<PnlRow> pnl(@RequestParam(value = "group_by", defaultValue = "month") PnlGroupBy groupBy) {
        return lotService.realizedPnl(groupBy);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) String ids) throws IOException {
        Set<Long> idSet = ids == null || ids.isBlank()
                ? Collections.emptySet()
                : Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank())
                        .map(Long::valueOf).collect(Collectors.toSet());
        byte[] body = lotExportService.export(format, idSet);
        String ext = "xlsx".equals(format) ? "xlsx" : format;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=lots." + ext)
                .contentType("xlsx".equals(format)
                        ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : "json".equals(format) ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skinledger-import-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(lotWorkbookService.template());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LotWorkbookService.ImportResult importWorkbook(@RequestParam("file") MultipartFile file) throws IOException {
        return lotWorkbookService.importWorkbook(file);
    }
}
