package com.cs.skinledger.web;

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
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    
    @Autowired
    private com.cs.skinledger.repository.LotRepository lotRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;

    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private String body(String item, String direction, String qty, String price) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "itemName", item,
                "platform", "steam",
                "direction", direction,
                "quantity", qty,
                "unitPrice", price,
                "fee", "0",
                "tradedAt", "2026-01-05T10:00:00"));
    }

    @Test
    void realizedPnlGroupedByItem() throws Exception {
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Stats Knife", "BUY", "1", "100")));
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Stats Knife", "BUY", "1", "100")));
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Stats Knife", "SELL", "1", "150")));
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Stats Knife", "SELL", "1", "150")));

        mockMvc.perform(get("/api/analytics/pnl").param("group_by", "item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("Stats Knife"))
                .andExpect(jsonPath("$[0].realizedPnl").value(100.0))
                .andExpect(jsonPath("$[0].tradeCount").value(2));
    }

    @Test
    void portfolioReturnsHoldingsWithRealizedPnl() throws Exception {
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Hold Knife", "BUY", "2", "100")));
        mockMvc.perform(post("/api/trades").contentType(MediaType.APPLICATION_JSON)
                .content(body("Hold Knife", "SELL", "1", "120")));

        mockMvc.perform(get("/api/analytics/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].itemName").value("Hold Knife"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(1.0))
                .andExpect(jsonPath("$.holdings[0].realizedPnl").value(20.0))
                .andExpect(jsonPath("$.totalRealizedPnl").value(20.0));
    }
}
