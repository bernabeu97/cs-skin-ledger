package com.cs.skinledger.service;

import com.cs.skinledger.dto.LotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import com.cs.skinledger.util.SpreadsheetCells;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class LotExportService {

    private static final String[] HEADER = {
            "饰品", "磨损", "磨损值", "数量", "买入价", "买入时间", "买入平台",
            "出售价", "实际收入", "手续费", "出售时间", "出售平台", "盈亏", "状态", "备注"
    };

    private final LotService lotService;
    private final ObjectMapper objectMapper;

    public byte[] export(String format) throws IOException {
        return export(format, Set.of());
    }

    public byte[] export(String format, Set<Long> ids) throws IOException {
        Predicate<LotResponse> filter = ids.isEmpty()
                ? lot -> true
                : lot -> ids.contains(lot.id());
        return switch (format) {
            case "json" -> objectMapper.writeValueAsBytes(
                    lotService.list(null).stream().filter(filter).toList());
            case "xlsx" -> exportXlsx(filter);
            default -> exportCsv(filter);
        };
    }

    private byte[] exportCsv(Predicate<LotResponse> filter) throws IOException {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            for (LotResponse l : lotService.list(null).stream().filter(filter).toList()) {
                printer.printRecord(
                        SpreadsheetCells.csv(l.itemNameZh() == null ? l.itemName() : l.itemNameZh()),
                        SpreadsheetCells.csv(l.exterior()), nz(l.floatValue()), l.quantity(),
                        l.buyPrice(), l.buyTime(), l.buyPlatform(),
                        nz(l.sellPrice()), nz(l.actualIncome()), l.fee(),
                        nz(l.sellTime()), SpreadsheetCells.csv(l.sellPlatform()), nz(l.profit()),
                        l.status().name(), SpreadsheetCells.csv(l.note()));
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportXlsx(Predicate<LotResponse> filter) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("lots");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            int r = 1;
            for (LotResponse l : lotService.list(null).stream().filter(filter).toList()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(l.itemNameZh() == null ? l.itemName() : l.itemNameZh());
                row.createCell(1).setCellValue(nz(l.exterior()));
                row.createCell(2).setCellValue(l.floatValue() == null ? null : l.floatValue().doubleValue());
                row.createCell(3).setCellValue(l.quantity().doubleValue());
                row.createCell(4).setCellValue(l.buyPrice().doubleValue());
                row.createCell(5).setCellValue(l.buyTime().toString());
                row.createCell(6).setCellValue(l.buyPlatform());
                row.createCell(7).setCellValue(l.sellPrice() == null ? null : l.sellPrice().doubleValue());
                row.createCell(8).setCellValue(l.actualIncome() == null ? null : l.actualIncome().doubleValue());
                row.createCell(9).setCellValue(l.fee().doubleValue());
                row.createCell(10).setCellValue(l.sellTime() == null ? null : l.sellTime().toString());
                row.createCell(11).setCellValue(nz(l.sellPlatform()));
                row.createCell(12).setCellValue(l.profit() == null ? null : l.profit().doubleValue());
                row.createCell(13).setCellValue(l.status().name());
                row.createCell(14).setCellValue(nz(l.note()));
            }
            for (int i = 0; i < HEADER.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
