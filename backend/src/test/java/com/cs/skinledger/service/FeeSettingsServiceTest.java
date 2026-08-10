package com.cs.skinledger.service;

import com.cs.skinledger.domain.Setting;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.FeeSettings;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "local")
class FeeSettingsServiceTest {

    @Autowired
    private FeeSettingsService feeSettingsService;
    @Autowired
    private SettingRepository settingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;
    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;
    @Autowired
    private com.cs.skinledger.repository.PriceSnapshotRepository snapshotRepository;
    @Autowired
    private com.cs.skinledger.repository.LotRepository lotRepository;
    @Autowired
    private com.cs.skinledger.repository.TradeRepository tradeRepository;
    @Autowired
    private com.cs.skinledger.repository.ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        settingRepository.deleteAll();
        otherCostRepository.deleteAll();
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

    @Test
    void defaultsAndSaveAndRateFor() {
        assertEquals(0, new BigDecimal("0.15").compareTo(feeSettingsService.get().steam()));

        FeeSettings saved = feeSettingsService.save(new FeeSettings(
                new BigDecimal("0.10"), new BigDecimal("0.005"), new BigDecimal("0.02")));
        assertEquals(0, new BigDecimal("0.10").compareTo(feeSettingsService.get().steam()));

        assertEquals(0, new BigDecimal("0.005").compareTo(feeSettingsService.rateFor("uu")));
        assertEquals(0, new BigDecimal("0.02").compareTo(feeSettingsService.rateFor("buff")));
        assertEquals(0, BigDecimal.ZERO.compareTo(feeSettingsService.rateFor("unknown")));
    }
}
