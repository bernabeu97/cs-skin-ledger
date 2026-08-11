package com.cs.skinledger.web;

import com.cs.skinledger.dto.FeeSettings;
import com.cs.skinledger.service.FeeSettingsService;
import com.cs.skinledger.service.CsqaqTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final FeeSettingsService feeSettingsService;
    private final CsqaqTokenService tokenService;

    @GetMapping("/fees")
    public FeeSettings getFees() {
        return feeSettingsService.get();
    }

    @PutMapping("/fees")
    public FeeSettings saveFees(@Valid @RequestBody FeeSettings fees) {
        return feeSettingsService.save(fees);
    }

    @GetMapping("/csqaq-token")
    public CsqaqTokenService.TokenView tokenStatus() {
        return tokenService.status();
    }

    @PutMapping("/csqaq-token")
    public CsqaqTokenService.TokenView saveToken(@Valid @RequestBody TokenRequest request) {
        return tokenService.save(request.token());
    }

    @PostMapping("/csqaq-token/bind-ip")
    public CsqaqTokenService.IpBindingView bindTokenIp() {
        return tokenService.bindCurrentServerIp();
    }

    @DeleteMapping("/csqaq-token")
    public CsqaqTokenService.TokenView deleteToken() {
        return tokenService.delete();
    }

    public record TokenRequest(@NotBlank @Size(min = 8, max = 128) String token) {
    }
}
