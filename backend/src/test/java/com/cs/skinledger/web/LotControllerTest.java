package com.cs.skinledger.web;

import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.domain.PriceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.cs.skinledger.service.LotWorkbookService;

import java.util.Map;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "local")
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
    private PriceSnapshotRepository priceSnapshotRepository;

    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;

    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;

    @Autowired
    private com.cs.skinledger.repository.SettingRepository settingRepository;

    @Autowired
    private com.cs.skinledger.repository.TradeRepository tradeRepository;
    @Autowired
    private com.cs.skinledger.repository.UserRepository userRepository;
    @Autowired
    private LotWorkbookService workbookService;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
        priceSnapshotRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        settingRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        com.cs.skinledger.domain.User user = new com.cs.skinledger.domain.User();
        user.setUsername("local");
        userRepository.save(user);
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
        String created = mockMvc.perform(post("/api/lots").with(csrf())
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
        mockMvc.perform(post("/api/lots/" + id + "/sell").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.actualIncome").value(552.42))
                .andExpect(jsonPath("$.profit").value(-116.46));
    }

    @Test
    void sellOnMissingLotReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/lots/9999/sell").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sellPrice", "100"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeBuyPriceReturns400() throws Exception {
        mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Knife X", "-1", "2026-01-01T00:00:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsHoldingsAndProfit() throws Exception {
        String a = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Knife A", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(a).get("id").asLong();
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Knife B", "200", "2026-02-01T10:00:00")));
        mockMvc.perform(post("/api/lots/" + idA + "/sell").with(csrf())
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
        String created = mockMvc.perform(post("/api/lots").with(csrf())
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

        mockMvc.perform(put("/api/lots/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.actualIncome").value(118.0))
                .andExpect(jsonPath("$.profit").value(18.0));
    }

    @Test
    void deleteMovesLotToTrashAndRestoreReturnsItToSummary() throws Exception {
        String created = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Recycle Knife", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/lots/" + id).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/lots")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/lots/summary")).andExpect(jsonPath("$.lotCount").value(0));
        mockMvc.perform(get("/api/lots/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty());

        mockMvc.perform(post("/api/lots/" + id + "/restore").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(get("/api/lots/summary")).andExpect(jsonPath("$.lotCount").value(1));
    }

    @Test
    void standardWorkbookImportsOnceAndSkipsExactDuplicate() throws Exception {
        byte[] template = workbookService.template();
        MockMultipartFile file = new MockMultipartFile("file", "lots.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template);

        mockMvc.perform(multipart("/api/lots/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failed").value(0));
        mockMvc.perform(multipart("/api/lots/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void pageReturnsPaginatedItemsAndTotal() throws Exception {
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Page Knife A", "100", "2026-01-01T10:00:00")));
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Page Knife B", "200", "2026-02-01T10:00:00")));
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Page Knife C", "300", "2026-03-01T10:00:00")));

        mockMvc.perform(get("/api/lots/page").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/lots/page").param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void statsAndAggregateReportRoiAndWinRate() throws Exception {
        String a = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Stat Knife A", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(a).get("id").asLong();
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Stat Knife B", "200", "2026-02-01T10:00:00")));
        mockMvc.perform(post("/api/lots/" + idA + "/sell").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("sellPrice", "120", "fee", "0"))));

        mockMvc.perform(get("/api/lots/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldCount").value(1))
                .andExpect(jsonPath("$.winningSoldCount").value(1))
                .andExpect(jsonPath("$.winRate").value(1.0))
                .andExpect(jsonPath("$.realizedRoi").value(0.066667));

        mockMvc.perform(get("/api/lots/aggregate").param("group_by", "item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].realizedPnl").value(20.0))
                .andExpect(jsonPath("$[0].buyCost").value(100.0));
    }

    @Test
    void batchFillPriceAndBatchDeleteWork() throws Exception {
        String a = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Batch Knife A", "0", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(a).get("id").asLong();
        String b = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Batch Knife B", "0", "2026-02-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idB = objectMapper.readTree(b).get("id").asLong();

        mockMvc.perform(post("/api/lots/batch/fill-price").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(idA, idB), "buyPrice", "88.5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        mockMvc.perform(get("/api/lots"))
                .andExpect(jsonPath("$[?(@.id == " + idA + ")].buyPrice").value(88.5));

        mockMvc.perform(post("/api/lots/batch/delete").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(idA, idB)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));

        mockMvc.perform(get("/api/lots")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/lots/trash")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void uuFullJsonPreviewCountsNewAndDuplicateWithoutWriting() throws Exception {
        String json = """
                {"records":[
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"AK-47 | Hydroponic (Field-Tested)",
                   "price":12.34,"orderNo":"B1","createOrderTime":1700000000000,
                   "raw":{"finishOrderTime":1700000000000}},
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"AK-47 | Hydroponic (Field-Tested)",
                   "price":12.34,"orderNo":"B2","createOrderTime":1700001000000,
                   "raw":{"finishOrderTime":1700001000000}}
                ]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "records.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/api/sync/uu/preview-full-json").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyRecords").value(2))
                .andExpect(jsonPath("$.holdingsImported").value(2))
                .andExpect(jsonPath("$.holdingsSkippedDuplicates").value(0));

        mockMvc.perform(get("/api/lots")).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(multipart("/api/sync/uu/import-full-json").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingsImported").value(2));
        mockMvc.perform(multipart("/api/sync/uu/preview-full-json").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingsImported").value(0))
                .andExpect(jsonPath("$.holdingsSkippedDuplicates").value(2));
    }

    @Test
    void healthReportsCoverageAndPendingPrices() throws Exception {
        String a = mockMvc.perform(post("/api/lots").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody("Health Knife A", "100", "2026-01-01T10:00:00")))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(a).get("id").asLong();
        mockMvc.perform(post("/api/lots").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(buyBody("Health Knife B", "0", "2026-02-01T10:00:00")));

        com.cs.skinledger.domain.Item item = itemRepository.findAll().get(0);
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setItem(item);
        snapshot.setPlatform("uu");
        snapshot.setPrice(new java.math.BigDecimal("50"));
        snapshot.setFetchedAt(java.time.LocalDateTime.now());
        priceSnapshotRepository.save(snapshot);

        mockMvc.perform(get("/api/prices/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingCount").value(2))
                .andExpect(jsonPath("$.pricedHoldingCount").value(1))
                .andExpect(jsonPath("$.unpricedHoldingCount").value(1))
                .andExpect(jsonPath("$.coverageRate").value(0.5))
                .andExpect(jsonPath("$.pendingBuyPriceCount").value(1));

        mockMvc.perform(get("/api/lots/page").param("noprice", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(idA + 1));
    }

    @Test
    void reconcileReportsDiffsAndFixPriceAppliesConfirmedValue() throws Exception {
        String json = """
                {"records":[
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"Recon Knife (Field-Tested)",
                   "price":12.34,"orderNo":"B1","createOrderTime":1700000000000,
                   "raw":{"finishOrderTime":1700000000000}},
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"Recon Knife (Field-Tested)",
                   "price":12.34,"orderNo":"B2","createOrderTime":1700001000000,
                   "raw":{"finishOrderTime":1700001000000}}
                ]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "records.json", "application/json", json.getBytes());
        mockMvc.perform(multipart("/api/sync/uu/import-full-json").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingsImported").value(2));

        // 只有 B1(价格改为 99)+ 新增 B3 -> 差异:平台独有 1、系统独有 1、金额不一致 1
        String diffJson = """
                {"records":[
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"Recon Knife (Field-Tested)",
                   "price":99.00,"orderNo":"B1","createOrderTime":1700000000000,
                   "raw":{"finishOrderTime":1700000000000}},
                  {"direction":"buy","recordType":"trade","status":"340","marketHashName":"Recon Knife (Field-Tested)",
                   "price":5.00,"orderNo":"B3","createOrderTime":1700002000000,
                   "raw":{"finishOrderTime":1700002000000}}
                ]}
                """;
        MockMultipartFile diff = new MockMultipartFile("file", "diff.json", "application/json", diffJson.getBytes());
        mockMvc.perform(multipart("/api/sync/uu/reconcile").file(diff).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformOnlyHoldings").value(1))
                .andExpect(jsonPath("$.systemOnlyCount").value(1))
                .andExpect(jsonPath("$.amountMismatches.length()").value(1))
                .andExpect(jsonPath("$.amountMismatches[0].field").value("buyPrice"))
                .andExpect(jsonPath("$.amountMismatches[0].systemValue").value(12.34))
                .andExpect(jsonPath("$.amountMismatches[0].platformValue").value(99.0));

        long id = objectMapper.readTree(
                mockMvc.perform(get("/api/lots")).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();
        mockMvc.perform(post("/api/sync/uu/fix-price").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", id, "field", "buy", "price", "99"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyPrice").value(99.0));
    }
}
