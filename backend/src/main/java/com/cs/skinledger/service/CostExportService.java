package com.cs.skinledger.service;

import com.cs.skinledger.dto.CostResponse;
import com.cs.skinledger.util.SpreadsheetCells;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 其他收支导出 */
@Service
@RequiredArgsConstructor
public class CostExportService {

    private static final Map<String, String> CATEGORY_ZH = new LinkedHashMap<>();
    static {
        CATEGORY_ZH.put("membership", "会员费");
        CATEGORY_ZH.put("platform_fee", "平台服务费");
        CATEGORY_ZH.put("compensation_expense", "赔偿支出");
        CATEGORY_ZH.put("compensation_income", "赔偿收入");
        CATEGORY_ZH.put("refund", "退款");
        CATEGORY_ZH.put("other", "其他");
    }

    private static final String[] HEADER = {"分类", "方向", "金额", "时间", "平台", "关联饰品", "备注"};

    private final CostService costService;
    private final ObjectMapper objectMapper;

    public byte[] export(String format) throws IOException {
        return switch (format) {
            case "json" -> objectMapper.writeValueAsBytes(costService.list(null, null, null, null));
            case "xlsx" -> exportXlsx();
            default -> exportCsv();
        };
    }

    private byte[] exportCsv() throws IOException {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            for (CostResponse c : costService.list(null, null, null, null)) {
                printer.printRecord(
                        CATEGORY_ZH.getOrDefault(c.category(), c.category()),
                        "income".equals(c.direction()) ? "收入" : "支出",
                        c.amount(), c.occurredAt(), SpreadsheetCells.csv(c.platform()),
                        SpreadsheetCells.csv(c.itemNameZh() == null ? c.itemName() : c.itemNameZh()),
                        SpreadsheetCells.csv(c.note()));
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("costs");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            int r = 1;
            for (CostResponse c : costService.list(null, null, null, null)) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(CATEGORY_ZH.getOrDefault(c.category(), c.category()));
                row.createCell(1).setCellValue("income".equals(c.direction()) ? "收入" : "支出");
                row.createCell(2).setCellValue(c.amount().doubleValue());
                row.createCell(3).setCellValue(c.occurredAt().toString());
                row.createCell(4).setCellValue(nz(c.platform()));
                row.createCell(5).setCellValue(c.itemNameZh() == null ? nz(c.itemName()) : c.itemNameZh());
                row.createCell(6).setCellValue(nz(c.note()));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
