package com.cs.skinledger.service;

import com.cs.skinledger.domain.Alert;
import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.AlertCreateRequest;
import com.cs.skinledger.dto.AlertResponse;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AlertServiceTest {

    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;

    private Item item;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("local");
        userRepository.save(user);
        item = new Item();
        item.setMarketHashName("AK-47 | Alert Test (Field-Tested)");
        item.setSource("manual");
        item = itemRepository.save(item);
    }

    private PriceQuote quote(String price) {
        return new PriceQuote(item.getId(), "AK-47 | Alert Test (Field-Tested)", "uu",
                new BigDecimal(price), null, null, "CNY", LocalDateTime.now());
    }

    @Test
    void gtAlertTriggersOnceUntilReset() {
        alertService.create(new AlertCreateRequest(item.getId(), "uu", "gt", new BigDecimal("100")));

        List<AlertResponse> first = alertService.check(List.of(quote("120")));
        assertEquals(1, first.size());
        assertNotNull(first.get(0).triggeredAt());

        List<AlertResponse> second = alertService.check(List.of(quote("130")));
        assertTrue(second.isEmpty());

        AlertResponse reset = alertService.reset(first.get(0).id());
        assertNull(reset.triggeredAt());

        List<AlertResponse> third = alertService.check(List.of(quote("140")));
        assertEquals(1, third.size());
    }

    @Test
    void ltAlertTriggersWhenBelowThreshold() {
        alertService.create(new AlertCreateRequest(item.getId(), "uu", "lt", new BigDecimal("100")));

        List<AlertResponse> hit = alertService.check(List.of(quote("80")));
        assertEquals(1, hit.size());

        Alert a = alertRepository.findById(hit.get(0).id()).orElseThrow();
        assertEquals("lt", a.getCondition());
        assertNotNull(a.getTriggeredAt());
    }
}