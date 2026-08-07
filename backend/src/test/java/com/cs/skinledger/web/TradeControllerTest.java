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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
        itemRepository.deleteAll();
    }

    private String body(String item, String platform, String direction, String qty, String price, String fee)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "itemName", item,
                "platform", platform,
                "direction", direction,
                "quantity", qty,
                "unitPrice", price,
                "fee", fee,
                "tradedAt", "2026-01-05T10:00:00"));
    }

    @Test
    void createTradePersistsAndComputesTotal() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("AK-47 | Redline (Field-Tested)", "steam", "BUY", "2", "100", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.itemName").value("AK-47 | Redline (Field-Tested)"))
                .andExpect(jsonPath("$.totalAmount").value(200.0))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void listFiltersByPlatform() throws Exception {
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Case 1", "steam", "BUY", "1", "50", "0")));
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Case 2", "uu", "BUY", "1", "50", "0")));

        mockMvc.perform(get("/api/trades").param("platform", "uu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].platform").value("uu"));
    }

    @Test
    void updateTradeChangesFields() throws Exception {
        String created = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Gloves", "steam", "BUY", "1", "100", "0")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/trades/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Gloves", "steam", "BUY", "2", "90", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2.0));
    }

    @Test
    void deleteTradeThenGetReturns404() throws Exception {
        String created = mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Sticker", "steam", "BUY", "1", "10", "0")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/trades/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/trades/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidPlatformReturns400() throws Exception {
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Knife", "csgobackpack", "BUY", "1", "100", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellExceedingHoldingsReturns400() throws Exception {
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("AK-47 | Redline (Field-Tested)", "steam", "BUY", "1", "100", "0")));
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("AK-47 | Redline (Field-Tested)", "steam", "SELL", "2", "150", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importCsvCreatesTrades() throws Exception {
        String csv = "itemName,platform,direction,quantity,unitPrice,fee,feeRate,currency,tradedAt,externalTradeId,status,note\n"
                + "Test Case,steam,BUY,1,50,0,,CNY,2026-01-05T10:00:00,,COMPLETED,\n";
        mockMvc.perform(multipart("/api/trades/import/csv")
                        .file(new MockMultipartFile("file", "trades.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void importJsonCreatesTrades() throws Exception {
        String json = "[{\"itemName\":\"JSON Knife\",\"platform\":\"steam\",\"direction\":\"BUY\","
                + "\"quantity\":1,\"unitPrice\":80,\"fee\":0,\"tradedAt\":\"2026-01-05T10:00:00\"}]";
        mockMvc.perform(post("/api/trades/import/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void exportCsvContainsHeaderAndRows() throws Exception {
        mockMvc.perform(post("/api/trades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Export Knife", "steam", "BUY", "1", "100", "0")));

        mockMvc.perform(get("/api/trades/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("trades.csv")))
                .andExpect(content().string(containsString("itemName")))
                .andExpect(content().string(containsString("Export Knife")));
    }

    @Test
    void exportXlsxReturnsNonEmptyBytes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/trades/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();
        assertTrue(result.getResponse().getContentAsByteArray().length > 0);
    }
}