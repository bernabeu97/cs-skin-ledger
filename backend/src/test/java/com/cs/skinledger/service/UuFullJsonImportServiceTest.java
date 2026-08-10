package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.UuFullJsonImportResult;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.OtherCostRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "local")
class UuFullJsonImportServiceTest {

    @Autowired private UuFullJsonImportService service;
    @Autowired private LotRepository lots;
    @Autowired private ItemRepository items;
    @Autowired private UserRepository users;
    @Autowired private AlertRepository alerts;
    @Autowired private OtherCostRepository costs;
    @Autowired private SettingRepository settings;
    @Autowired private PriceSnapshotRepository prices;

    @BeforeEach
    void setUp() {
        prices.deleteAll();
        costs.deleteAll();
        settings.deleteAll();
        alerts.deleteAll();
        lots.deleteAll();
        items.deleteAll();
        users.deleteAll();

        User user = new User();
        user.setUsername("local");
        users.save(user);
        addItem("AK-47 | Test", "AK-47 | 测试");
        addItem("Glove Case", "手套武器箱");
        addItem("Unmatched Case", "无匹配武器箱");
    }

    @Test
    void importsFullExportWithFifoAndCorrectsBatchPriceScale() throws Exception {
        String json = """
                {"source":"youpin898.com","records":[
                  {"recordType":"trade","direction":"buy","status":"340","orderNo":"b1","orderDetailNo":"b101","createOrderTime":1786000000000,"commodityName":"AK-47 | 测试 (崭新出厂)","marketHashName":"AK-47 | Test (Factory New)","price":100,"wear":"0.0123456789012345678","raw":{"finishOrderTime":1786000001000,"serviceFee":0,"commodityNum":1,"productDetail":{"commodityHashName":"AK-47 | Test (Factory New)","commodityName":"AK-47 | 测试 (崭新出厂)","price":10000,"abrade":"0.0123456789012345678","exteriorHashName":"Factory New"}}},
                  {"recordType":"trade","direction":"sell","status":"340","orderNo":"s1","orderDetailNo":"s101","createOrderTime":1786000010000,"commodityName":"AK-47 | 测试 (崭新出厂)","marketHashName":"AK-47 | Test (Factory New)","price":130,"wear":"0.02","raw":{"finishOrderTime":1786000011000,"serviceFee":100,"commodityNum":1,"productDetail":{"commodityHashName":"AK-47 | Test (Factory New)","commodityName":"AK-47 | 测试 (崭新出厂)","price":13000,"exteriorHashName":"Factory New"}}},
                  {"recordType":"trade","direction":"buy","status":"340","orderNo":"b2","orderDetailNo":"","createOrderTime":1786000020000,"commodityName":"手套武器箱","marketHashName":"","price":0.83,"wear":"","raw":{"finishOrderTime":1786000021000,"serviceFee":0,"commodityNum":1,"productDetail":{"commodityHashName":"Glove Case","commodityName":"手套武器箱","price":8300}}},
                  {"recordType":"trade","direction":"sell","status":"340","orderNo":"s2","orderDetailNo":"","createOrderTime":1786000030000,"commodityName":"无匹配武器箱","marketHashName":"Unmatched Case","price":50,"wear":"","raw":{"finishOrderTime":1786000031000,"serviceFee":0,"commodityNum":1,"productDetail":{"commodityHashName":"Unmatched Case","commodityName":"无匹配武器箱","price":5000}}}
                ]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "uu.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));

        UuFullJsonImportResult result = service.importFile(file);

        assertEquals(4, result.totalRecords());
        assertEquals(2, result.buyRecords());
        assertEquals(2, result.sellRecords());
        assertEquals(1, result.matchedSales());
        assertEquals(1, result.unmatchedSales());
        assertEquals(1, result.remainingHoldings());
        assertEquals(1, result.correctedPriceRecords());
        assertEquals(1, result.holdingsImported());
        assertEquals(2, result.salesImported());

        long userId = users.findByUsername("local").orElseThrow().getId();
        List<Lot> imported = lots.findByUserIdOrderByBuyTimeAsc(userId);
        assertEquals(3, imported.size());
        Lot sold = imported.stream().filter(l -> l.getStatus() == LotStatus.SOLD
                && l.getBuyPrice().compareTo(BigDecimal.ZERO) > 0).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.0123456789012345678").compareTo(sold.getFloatValue()));
        assertEquals(0, new BigDecimal("29").compareTo(sold.getProfit()));
        Lot holding = imported.stream().filter(l -> l.getStatus() == LotStatus.HOLDING).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("83").compareTo(holding.getBuyPrice()));

        UuFullJsonImportResult repeated = service.importFile(file);
        assertEquals(1, repeated.holdingsSkippedDuplicates());
        assertEquals(2, repeated.salesSkippedDuplicates());
    }

    @Test
    @EnabledIfSystemProperty(named = "uu.full.file", matches = ".+")
    void verifiesTheProvidedFullExport() throws Exception {
        Path path = Path.of(System.getProperty("uu.full.file"));
        MockMultipartFile file = new MockMultipartFile("file", path.getFileName().toString(),
                "application/json", Files.readAllBytes(path));

        UuFullJsonImportResult result = service.importFile(file);

        assertEquals(395, result.totalRecords());
        assertEquals(291, result.buyRecords());
        assertEquals(104, result.sellRecords());
        assertEquals(72, result.matchedSales());
        assertEquals(32, result.unmatchedSales());
        assertEquals(219, result.remainingHoldings());
        assertEquals(157, result.correctedPriceRecords());
        assertEquals(219, result.holdingsImported());
        assertEquals(104, result.salesImported());
        assertEquals(0, result.errors().size());
    }

    private void addItem(String marketHashName, String nameZh) {
        Item item = new Item();
        item.setMarketHashName(marketHashName);
        item.setNameZh(nameZh);
        item.setSource("test");
        items.save(item);
    }
}
