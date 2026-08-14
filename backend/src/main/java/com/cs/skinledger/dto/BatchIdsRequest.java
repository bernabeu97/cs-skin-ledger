package com.cs.skinledger.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchIdsRequest(
        @NotEmpty List<Long> ids) {
}
