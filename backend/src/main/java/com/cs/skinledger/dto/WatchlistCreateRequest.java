package com.cs.skinledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WatchlistCreateRequest(
        @NotNull @Positive Long itemId,
        @Size(max = 16) String exterior) {
}
