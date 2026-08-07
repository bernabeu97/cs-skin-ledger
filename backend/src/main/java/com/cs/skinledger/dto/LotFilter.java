package com.cs.skinledger.dto;

import com.cs.skinledger.domain.LotStatus;

import java.time.LocalDateTime;

public record LotFilter(
        String q,
        LotStatus status,
        String platform,
        LocalDateTime from,
        LocalDateTime to) {
}