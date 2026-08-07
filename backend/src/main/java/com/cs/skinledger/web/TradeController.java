package com.cs.skinledger.web;

import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.dto.ImportResult;
import com.cs.skinledger.dto.TradeCreateRequest;
import com.cs.skinledger.dto.TradeFilter;
import com.cs.skinledger.dto.TradeResponse;
import com.cs.skinledger.service.ImportExportService;
import com.cs.skinledger.service.TradeService;
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
import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;
    private final ImportExportService importExportService;

    @GetMapping
    public List<TradeResponse> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        TradeFilter filter = new TradeFilter(
                platform,
                direction == null ? null : TradeDirection.valueOf(direction),
                from, to, q, category);
        return tradeService.list(filter);
    }

    @PostMapping
    public TradeResponse create(@Valid @RequestBody TradeCreateRequest req) {
        return tradeService.create(req);
    }

    @PutMapping("/{id}")
    public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeCreateRequest req) {
        return tradeService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tradeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return importExportService.importCsv(file);
    }

    @PostMapping("/import/json")
    public ImportResult importJson(@RequestBody List<@Valid TradeCreateRequest> requests) {
        return importExportService.importJson(requests);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) throws IOException {
        byte[] body = importExportService.export(format);
        String ext = "xlsx".equals(format) ? "xlsx" : format;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trades." + ext)
                .contentType(mediaType(format))
                .body(body);
    }

    private MediaType mediaType(String format) {
        return switch (format) {
            case "json" -> MediaType.APPLICATION_JSON;
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.parseMediaType("text/csv");
        };
    }
}