package com.cs.skinledger.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importItemsThenSearchChineseName(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("skins_en.json"), """
                [{"id":"skin-1","name":"AK-47 | Redline","category":{"name":"Rifle"},"weapon":{"name":"AK-47"},
                  "min_float":0.1,"max_float":1.0,"wears":[{"name":"Factory New"},{"name":"Battle-Scarred"}],
                  "image":"http://x/a.png"}]
                """);
        Files.writeString(dir.resolve("skins_zh_CN.json"), """
                [{"id":"skin-1","name":"AK-47 | 红线","category":{"name":"步枪"},"weapon":{"name":"AK-47"},
                  "min_float":0.1,"max_float":1.0,"wears":[{"name":"崭新出厂"},{"name":"战痕累累"}],
                  "image":"http://x/a.png"}]
                """);
        Files.writeString(dir.resolve("crates_en.json"),
                "[{\"id\":\"crate-1\",\"name\":\"Revolution Case\",\"market_hash_name\":\"Revolution Case\"}]");
        Files.writeString(dir.resolve("crates_zh_CN.json"), "[{\"id\":\"crate-1\",\"name\":\"变革箱\"}]");

        mockMvc.perform(post("/api/items/import").param("dir", dir.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.byCategory.skins").value(1))
                .andExpect(jsonPath("$.byCategory.crates").value(1));

        mockMvc.perform(get("/api/items/search").param("q", "红线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nameZh").value("AK-47 | 红线"))
                .andExpect(jsonPath("$[0].weapon").value("AK-47"))
                .andExpect(jsonPath("$[0].wears[0]").value("崭新出厂"))
                .andExpect(jsonPath("$[0].minFloat").value(0.1));

        mockMvc.perform(get("/api/items/search").param("q", "Revolution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("crate"));
    }
}