package com.cs.skinledger.web;

import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;

    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;

    @Autowired
    private com.cs.skinledger.repository.TradeRepository tradeRepository;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private String buyBody(String item, String price, String time) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "itemName", item,
                "buyPrice", price,
                "buyTime", time,
                "buyPlatform", "uu"));
    }

    @Test
    void createBuyThenUpdateSellMatchesExcel() throws Exception {
        String created = mockMvc.perform(post("/api/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Test Agent | Loudmouth", "668.88", "2026-03-25T19:11:01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HOLDING"))
                .andExpect(jsonPath("$.buyPlatform").value("uu"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        String sellBody = objectMapper.writeValueAsString(Map.of(
                "sellPrice", "558",
                "sellTime", "2026-04-09T19:54:03",
                "sellPlatform", "uu",
                "fee", "5.58"));
        mockMvc.perform(post("/api/lots/" + id + "/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.actualIncome").value(552.42))
                .andExpect(jsonPath("$.profit").value(-116.46));
    }

    @Test
    void sellOnMissingLotReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/lots/9999/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sellPrice", "100"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeBuyPriceReturns400() throws Exception {
        mockMvc.perform(post("/api/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Knife X", "-1", "2026-01-01T00:00:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsHoldingsAndProfit() throws Exception {
        String a = mockMvc.perform(post("/api/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Knife A", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(a).get("id").asLong();
        mockMvc.perform(post("/api/lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Knife B", "200", "2026-02-01T10:00:00")));
        mockMvc.perform(post("/api/lots/" + idA + "/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sellPrice", "120", "fee", "0"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lots/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotCount").value(2))
                .andExpect(jsonPath("$.holdingCount").value(1))
                .andExpect(jsonPath("$.soldCount").value(1))
                .andExpect(jsonPath("$.totalBuyCost").value(300.0))
                .andExpect(jsonPath("$.holdingCost").value(200.0))
                .andExpect(jsonPath("$.realizedProfit").value(20.0));
    }

    @Test
    void updateBuyWithSellFieldsMarksSold() throws Exception {
        String created = mockMvc.perform(post("/api/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Knife C", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("itemName", "Knife C");
        payload.put("buyPrice", "100");
        payload.put("buyTime", "2026-01-01T10:00:00");
        payload.put("buyPlatform", "uu");
        payload.put("sellPrice", "120");
        payload.put("sellTime", "2026-02-01T10:00:00");
        payload.put("sellPlatform", "steam");
        payload.put("fee", "2");

        mockMvc.perform(put("/api/lots/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.actualIncome").value(118.0))
                .andExpect(jsonPath("$.profit").value(18.0));
    }
}