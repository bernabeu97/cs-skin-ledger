package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.CostRequest;
import com.cs.skinledger.dto.CostResponse;
import com.cs.skinledger.dto.CostSummary;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.OtherCostRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.TradeRepository;
import com.cs.skinledger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "local")
class CostServiceTest {

    @Autowired
    private CostService costService;
    @Autowired
    private OtherCostRepository otherCostRepository;

    @Autowired
    private com.cs.skinledger.repository.SettingRepository settingRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        otherCostRepository.deleteAll();
        settingRepository.deleteAll();
        snapshotRepository.deleteAll();
        alertRepository.deleteAll();
        lotRepository.deleteAll();
        tradeRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("local");
        userRepository.save(user);
    }

    private CostRequest req(String category, String direction, String amount, String time) {
        return new CostRequest(category, direction, new BigDecimal(amount),
                LocalDateTime.parse(time), "uu", null, "测试", null);
    }

    @Test
    void summaryComputesIncomeExpenseAndNet() {
        costService.create(req("membership", "expense", "999", "2026-06-06T01:03:08"));
        costService.create(req("compensation_income", "income", "325", "2026-06-29T16:18:37"));

        CostSummary s = costService.summary();

        assertEquals(0, new BigDecimal("325").compareTo(s.totalIncome()));
        assertEquals(0, new BigDecimal("999").compareTo(s.totalExpense()));
        assertEquals(0, new BigDecimal("-674").compareTo(s.net()));
        assertEquals(2, s.byCategory().size());
    }

    @Test
    void updateAndDeleteWork() {
        CostResponse created = costService.create(req("refund", "income", "100", "2026-07-01T10:00:00"));

        CostResponse updated = costService.update(created.id(),
                req("refund", "income", "120", "2026-07-01T10:00:00"));
        assertEquals(0, new BigDecimal("120").compareTo(updated.amount()));

        costService.delete(created.id());
        assertThrows(IllegalArgumentException.class, () -> costService.update(created.id(),
                req("refund", "income", "1", "2026-07-01T10:00:00")));
    }

    @Test
    void filterByCategory() {
        costService.create(req("membership", "expense", "88", "2026-06-06T01:03:08"));
        costService.create(req("platform_fee", "expense", "5", "2026-06-06T01:03:08"));

        assertEquals(1, costService.list("membership", null, null, null).size());
        assertEquals(2, costService.list(null, "expense", null, null).size());
    }
}
