package com.cs.skinledger.web;

import com.cs.skinledger.dto.CostRequest;
import com.cs.skinledger.dto.CostResponse;
import com.cs.skinledger.dto.CostSummary;
import com.cs.skinledger.service.CostService;
import com.cs.skinledger.service.CostExportService;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/costs")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;
    private final CostExportService costExportService;

    @GetMapping
    public List<CostResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return costService.list(category, direction, from, to);
    }

    @GetMapping("/summary")
    public CostSummary summary() {
        return costService.summary();
    }

    @PostMapping
    public CostResponse create(@Valid @RequestBody CostRequest req) {
        return costService.create(req);
    }

    @PutMapping("/{id}")
    public CostResponse update(@PathVariable Long id, @Valid @RequestBody CostRequest req) {
        return costService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        costService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) throws IOException {
        byte[] body = costExportService.export(format);
        String ext = "xlsx".equals(format) ? "xlsx" : format;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=costs." + ext)
                .contentType("xlsx".equals(format)
                        ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : "json".equals(format) ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}