package com.cs.skinledger.service;

import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.domain.TradeStatus;
import com.cs.skinledger.dto.ImportResult;
import com.cs.skinledger.dto.TradeCreateRequest;
import com.cs.skinledger.dto.TradeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportExportService {

    private static final String[] HEADER = {
            "itemName", "platform", "direction", "quantity", "unitPrice",
            "fee", "feeRate", "currency", "tradedAt", "externalTradeId", "status", "note"
    };

    private final TradeService tradeService;
    private final ObjectMapper objectMapper;

    public ImportResult importCsv(MultipartFile file) throws IOException {
        ImportResult result = new ImportResult(0, 0, new ArrayList<>());
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            int line = 1;
            for (CSVRecord record : parser) {
                line++;
                try {
                    tradeService.create(toRequest(record));
                    result = result.withCreated(result.created() + 1);
                } catch (Exception e) {
                    result = result.withFailed(result.failed() + 1);
                    result.errors().add("第 " + line + " 行: " + e.getMessage());
                }
            }
        }
        return result;
    }

    public ImportResult importJson(List<TradeCreateRequest> requests) {
        ImportResult result = new ImportResult(0, 0, new ArrayList<>());
        for (int i = 0; i < requests.size(); i++) {
            try {
                tradeService.create(requests.get(i));
                result = result.withCreated(result.created() + 1);
            } catch (Exception e) {
                result = result.withFailed(result.failed() + 1);
                result.errors().add("第 " + (i + 1) + " 条: " + e.getMessage());
            }
        }
        return result;
    }

    public byte[] export(String format) throws IOException {
        return switch (format) {
            case "json" -> exportJson();
            case "xlsx" -> exportXlsx();
            default -> exportCsv();
        };
    }

    private byte[] exportCsv() throws IOException {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            for (TradeResponse t : tradeService.list(null)) {
                printer.printRecord(t.itemName(), t.platform(), t.direction(), t.quantity(),
                        t.unitPrice(), t.fee(), t.feeRate(), t.currency(), t.tradedAt(),
                        t.externalTradeId(), t.status(), t.note());
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportJson() throws IOException {
        return objectMapper.writeValueAsBytes(tradeService.list(null));
    }

    private byte[] exportXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("trades");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            int r = 1;
            for (TradeResponse t : tradeService.list(null)) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(t.itemName());
                row.createCell(1).setCellValue(t.platform());
                row.createCell(2).setCellValue(t.direction().name());
                row.createCell(3).setCellValue(t.quantity().doubleValue());
                row.createCell(4).setCellValue(t.unitPrice().doubleValue());
                row.createCell(5).setCellValue(t.fee().doubleValue());
                row.createCell(6).setCellValue(t.feeRate() == null ? null : t.feeRate().doubleValue());
                row.createCell(7).setCellValue(t.currency());
                row.createCell(8).setCellValue(t.tradedAt().toString());
                row.createCell(9).setCellValue(t.externalTradeId());
                row.createCell(10).setCellValue(t.status().name());
                row.createCell(11).setCellValue(t.note());
            }
            for (int i = 0; i < HEADER.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private TradeCreateRequest toRequest(CSVRecord record) {
        return new TradeCreateRequest(
                null,
                record.get("itemName"),
                record.get("platform"),
                TradeDirection.valueOf(record.get("direction").toUpperCase()),
                new BigDecimal(record.get("quantity")),
                new BigDecimal(record.get("unitPrice")),
                blankToNull(record.get("fee")) == null ? BigDecimal.ZERO : new BigDecimal(blankToNull(record.get("fee"))),
                blankToNull(record.get("feeRate")) == null ? null : new BigDecimal(blankToNull(record.get("feeRate"))),
                blankToNull(record.get("currency")),
                LocalDateTime.parse(record.get("tradedAt")),
                blankToNull(record.get("externalTradeId")),
                blankToNull(record.get("status")) == null ? null
                        : TradeStatus.valueOf(blankToNull(record.get("status")).toUpperCase()),
                blankToNull(record.get("note")), null, null);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}