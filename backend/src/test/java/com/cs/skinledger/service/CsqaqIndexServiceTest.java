package com.cs.skinledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsqaqIndexServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesOfficialIndexOverviewShape() throws Exception {
        var root = mapper.readTree("""
                {"data":{"sub_index_data":[{"id":1,"name":"饰品指数","name_key":"init",
                "market_index":1541.8,"chg_num":-13.94,"chg_rate":-0.9,"open":1555.92,
                "close":1541.8,"high":1556.69,"low":1540.58,"updated_at":"2026-08-11T22:52:06"}]}}
                """);

        var rows = CsqaqIndexService.parseIndices(root);

        assertEquals(1, rows.size());
        assertEquals("饰品指数", rows.getFirst().name());
        assertEquals("1541.8", rows.getFirst().marketIndex().toPlainString());
    }

    @Test
    void parsesOfficialKlineShape() throws Exception {
        var root = mapper.readTree("""
                {"data":[{"t":"1700150400000","o":1402.74,"c":1385.55,"h":1402.74,"l":1385.55,"v":0}]}
                """);

        var points = CsqaqIndexService.parseKline(root);

        assertEquals(1, points.size());
        assertEquals("2023-11-16T16:00:00Z", points.getFirst().at());
        assertEquals("1385.55", points.getFirst().close().toPlainString());
    }
}
