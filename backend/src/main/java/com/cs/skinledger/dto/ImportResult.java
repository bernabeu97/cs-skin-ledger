package com.cs.skinledger.dto;

import java.util.List;

public record ImportResult(int created, int failed, List<String> errors) {

    public ImportResult withCreated(int created) {
        return new ImportResult(created, failed, errors);
    }

    public ImportResult withFailed(int failed) {
        return new ImportResult(created, failed, errors);
    }
}