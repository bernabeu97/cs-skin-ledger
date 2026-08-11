package com.cs.skinledger.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PricePoint(LocalDateTime at, BigDecimal value) {
}
