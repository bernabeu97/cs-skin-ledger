package com.cs.skinledger.service;

import com.cs.skinledger.dto.LotCreateRequest;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.util.SecurityTokens;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LotWorkbookService {
    private static final String[] HEADERS = {
            "饰品", "磨损", "磨损值", "数量", "买入价", "买入时间", "买入平台",
            "出售价", "手续费", "出售时间", "出售平台", "备注"
    };
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ROWS = 10_000;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final LotService lots;
    private final LotRepository lotRepository;
    private final CurrentUser currentUser;

    public byte[] template() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("饰品账本导入");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setWrapText(false);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                header.getCell(i).setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 0 ? 34 * 256 : i == 5 || i == 9 ? 21 * 256 : 14 * 256);
            }
            Row example = sheet.createRow(1);
            String[] values = {"AK-47 | 红线 (久经沙场)", "久经沙场", "0.22", "1", "680.00",
                    "2026-08-01 20:30:00", "uu", "", "0", "", "", "示例行，导入前可删除"};
            for (int i = 0; i < values.length; i++) example.createCell(i).setCellValue(values[i]);
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public ImportResult importWorkbook(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Excel 文件不能超过 10MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) throw new IllegalArgumentException("仅支持 .xlsx 标准模板");

        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("工作簿中没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            verifyHeaders(sheet.getRow(0));
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("单次最多导入 10000 行");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || blankRow(row, formatter)) continue;
                try {
                    List<String> cells = cells(row, formatter);
                    String sourceRef = "xlsx:" + SecurityTokens.sha256(String.join("\u001f", cells));
                    if (lotRepository.existsByUserIdAndSourceRef(currentUser.id(), sourceRef)) {
                        skipped++;
                        continue;
                    }
                    lots.create(toRequest(cells), sourceRef);
                    created++;
                } catch (RuntimeException e) {
                    errors.add("第 " + (index + 1) + " 行：" + safeMessage(e));
                    if (errors.size() >= 100) {
                        errors.add("错误超过 100 条，已停止继续读取");
                        break;
                    }
                }
            }
        } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException e) {
            throw new IllegalArgumentException("文件不是有效的 .xlsx 工作簿");
        }
        return new ImportResult(created, skipped, errors.size(), errors);
    }

    private LotCreateRequest toRequest(List<String> c) {
        String itemName = required(c.get(0), "饰品");
        BigDecimal quantity = decimal(c.get(3), "数量", BigDecimal.ONE);
        if (itemName.length() > 255) throw new IllegalArgumentException("饰品名称不能超过 255 个字符");
        if (quantity.signum() <= 0) throw new IllegalArgumentException("数量必须大于 0");
        String exterior = empty(c.get(1));
        if (exterior != null && exterior.length() > 16) throw new IllegalArgumentException("磨损等级不能超过 16 个字符");
        BigDecimal floatValue = decimal(c.get(2), "磨损值", null, true);
        if (floatValue != null && floatValue.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("磨损值不能大于 1");
        }
        BigDecimal buyPrice = decimal(c.get(4), "买入价", null);
        LocalDateTime buyTime = time(c.get(5), "买入时间", false);
        String buyPlatform = platform(c.get(6), "买入平台", false);
        BigDecimal sellPrice = decimal(c.get(7), "出售价", null, true);
        BigDecimal fee = decimal(c.get(8), "手续费", BigDecimal.ZERO);
        LocalDateTime sellTime = time(c.get(9), "出售时间", sellPrice == null);
        String sellPlatform = platform(c.get(10), "出售平台", sellPrice == null);
        String note = empty(c.get(11));
        if (note != null && note.length() > 500) throw new IllegalArgumentException("备注不能超过 500 个字符");
        return new LotCreateRequest(null, itemName, quantity, exterior, floatValue,
                buyPrice, buyTime, buyPlatform, note, sellPrice, sellTime, sellPlatform, fee);
    }

    private void verifyHeaders(Row header) {
        if (header == null) throw new IllegalArgumentException("缺少表头");
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        for (int i = 0; i < HEADERS.length; i++) {
            if (!HEADERS[i].equals(formatter.formatCellValue(header.getCell(i)).trim())) {
                throw new IllegalArgumentException("模板表头不匹配，请重新下载标准模板");
            }
        }
    }

    private List<String> cells(Row row, DataFormatter formatter) {
        List<String> result = new ArrayList<>(HEADERS.length);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.getCell(i);
            result.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return result;
    }

    private boolean blankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (!formatter.formatCellValue(row.getCell(i)).isBlank()) return false;
        }
        return true;
    }

    private BigDecimal decimal(String value, String field, BigDecimal fallback) {
        return decimal(value, field, fallback, false);
    }

    private BigDecimal decimal(String value, String field, BigDecimal fallback, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional || fallback != null) return fallback;
            throw new IllegalArgumentException(field + "不能为空");
        }
        try {
            BigDecimal number = new BigDecimal(value.replace(",", ""));
            if (number.signum() < 0) throw new IllegalArgumentException(field + "不能为负数");
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + "不是有效数字");
        }
    }

    private LocalDateTime time(String value, String field, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) return null;
            throw new IllegalArgumentException(field + "不能为空");
        }
        try {
            return value.contains("T") ? LocalDateTime.parse(value) : LocalDateTime.parse(value, DISPLAY_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + "格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String platform(String value, String field, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) return null;
            throw new IllegalArgumentException(field + "不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("steam", "uu", "buff").contains(normalized)) {
            throw new IllegalArgumentException(field + "仅支持 steam/uu/buff");
        }
        return normalized;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private String empty(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "数据无效" : e.getMessage();
    }

    public record ImportResult(int created, int skipped, int failed, List<String> errors) {}
}
