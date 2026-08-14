package com.cs.skinledger.web;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public Map<String, String> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        String version = getClass().getPackage() == null
                ? null
                : getClass().getPackage().getImplementationVersion();
        return Map.of("status", "ok", "version", version == null || version.isBlank() ? "0.3.0" : version);
    }
}
