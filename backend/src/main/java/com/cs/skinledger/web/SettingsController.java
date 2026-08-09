package com.cs.skinledger.web;

import com.cs.skinledger.dto.FeeSettings;
import com.cs.skinledger.service.FeeSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final FeeSettingsService feeSettingsService;

    @GetMapping("/fees")
    public FeeSettings getFees() {
        return feeSettingsService.get();
    }

    @PutMapping("/fees")
    public FeeSettings saveFees(@Valid @RequestBody FeeSettings fees) {
        return feeSettingsService.save(fees);
    }
}