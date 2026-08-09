package com.cs.skinledger.web;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeItemWearControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;

    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;

    @Autowired
    private com.cs.skinledger.repository.LotRepository lotRepository;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private Item saveItem(String name) {
        Item item = new Item();
        item.setMarketHashName(name);
        item.setNameZh("AK-47 | 红线");
        item.setSource("manual");
        return itemRepository.save(item);
    }

    @Test
    void createTradeWithItemIdAndWear() throws Exception {
        Item item = saveItem("AK-47 | Redline");
        Map<String, Object> payload = Map.of(
                "itemId", item.getId(),
                "itemName", "AK-47 | Redline",
                "platform", "steam",
                "direction", "BUY",
                "quantity", 1,
                "unitPrice", 100,
                "fee", 0,
                "tradedAt", "2026-01-05T10:00:00",
                "exterior", "崭新出厂",
                "floatValue", 0.1234);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemNameZh").value("AK-47 | 红线"))
                .andExpect(jsonPath("$.exterior").value("崭新出厂"))
                .andExpect(jsonPath("$.floatValue").value(0.1234));
    }

    @Test
    void createTradeWithInvalidFloatReturns400() throws Exception {
        Map<String, Object> payload = Map.of(
                "itemName", "AK-47 | Redline",
                "platform", "steam",
                "direction", "BUY",
                "quantity", 1,
                "unitPrice", 100,
                "fee", 0,
                "tradedAt", "2026-01-05T10:00:00",
                "floatValue", 1.5);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchTradesByChineseName() throws Exception {
        saveItem("AK-47 | Redline");
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemName", "AK-47 | Redline",
                                "platform", "steam",
                                "direction", "BUY",
                                "quantity", 1,
                                "unitPrice", 100,
                                "fee", 0,
                                "tradedAt", "2026-01-05T10:00:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trades").param("q", "红线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}