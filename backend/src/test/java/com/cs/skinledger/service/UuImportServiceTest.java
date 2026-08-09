package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.UuImportRequest;
import com.cs.skinledger.dto.UuImportResult;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class UuImportServiceTest {

    @Autowired
    private UuImportService uuImportService;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;

    private Item item;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        alertRepository.deleteAll();
        lotRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("local");
        userRepository.save(user);
        item = new Item();
        item.setMarketHashName("AK-47 | Test Import");
        item.setNameZh("AK-47 | 测试导入");
        item.setSource("manual");
        item = itemRepository.save(item);
    }

    @Test
    void importHoldingsAndSalesIsIdempotent() {
        UuImportRequest req = new UuImportRequest(
                List.of(new UuImportRequest.HoldingImport(
                        item.getId(), "AK-47 | Test Import", "AK-47 | 测试导入", "久经沙场",
                        new BigDecimal("0.25"), new BigDecimal("2"), new BigDecimal("100"),
                        new BigDecimal("120"), "2026-08-01 10:00:00", null)),
                List.of(new UuImportRequest.SaleImport(
                        item.getId(), "AK-47 | Test Import", "AK-47 | 测试导入", "久经沙场",
                        new BigDecimal("150"), new BigDecimal("3"), "2026-08-02 10:00:00", null)));

        long userId = userRepository.findByUsername("local").orElseThrow().getId();
        UuImportResult first = uuImportService.importData(req);
        assertEquals(1, first.holdingsImported());
        assertEquals(1, first.salesImported());

        Lot holding = lotRepository.findByUserIdOrderByBuyTimeAsc(userId).stream()
                .filter(l -> l.getStatus() == LotStatus.HOLDING).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(holding.getBuyPrice()));
        assertEquals("久经沙场", holding.getExterior());
        assertEquals(0, new BigDecimal("0.25").compareTo(holding.getFloatValue()));
        assertEquals(0, new BigDecimal("2").compareTo(holding.getQuantity()));

        Lot sold = lotRepository.findByUserIdOrderByBuyTimeAsc(userId).stream()
                .filter(l -> l.getStatus() == LotStatus.SOLD).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("147").compareTo(sold.getActualIncome())); // 150 - 3
        assertEquals(0, new BigDecimal("147").compareTo(sold.getProfit()));       // 买入价 0

        UuImportResult second = uuImportService.importData(req);
        assertEquals(1, second.holdingsSkippedDuplicates());
        assertEquals(1, second.salesSkippedDuplicates());
    }
}