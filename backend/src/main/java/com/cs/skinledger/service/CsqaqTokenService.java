package com.cs.skinledger.service;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.domain.Setting;
import com.cs.skinledger.repository.SettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CsqaqTokenService {

    private static final String KEY = "csqaq_token";

    private final SettingRepository settings;
    private final CurrentUser currentUser;
    private final SecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final AppPriceProperties properties;

    @Transactional(readOnly = true)
    public Optional<String> currentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Optional<Setting> setting = settings.findByUserIdAndKey(currentUser.id(), KEY);
            if (setting.isPresent()) {
                return setting.map(this::decrypt);
            }
        }
        String fallback = properties.getCsqaq().getApiToken();
        return fallback == null || fallback.isBlank() ? Optional.empty() : Optional.of(fallback.trim());
    }

    @Transactional(readOnly = true)
    public TokenView status() {
        Optional<Setting> own = settings.findByUserIdAndKey(currentUser.id(), KEY);
        if (own.isPresent()) {
            return new TokenView(true, mask(decrypt(own.get())), "account");
        }
        String fallback = properties.getCsqaq().getApiToken();
        boolean configured = fallback != null && !fallback.isBlank();
        return new TokenView(configured, configured ? mask(fallback.trim()) : null,
                configured ? "server" : null);
    }

    @Transactional
    public TokenView save(String rawToken) {
        String token = rawToken.trim();
        if (!token.matches("^[A-Za-z0-9_-]{8,128}$")) {
            throw new IllegalArgumentException("Token 格式不正确");
        }
        Setting setting = settings.findByUserIdAndKey(currentUser.id(), KEY).orElseGet(() -> {
            Setting created = new Setting();
            created.setUser(currentUser.get());
            created.setKey(KEY);
            return created;
        });
        try {
            setting.setValue(objectMapper.writeValueAsString(cipher.encrypt(token)));
        } catch (Exception e) {
            throw new IllegalStateException("Token 保存失败", e);
        }
        settings.save(setting);
        return new TokenView(true, mask(token), "account");
    }

    @Transactional
    public TokenView delete() {
        settings.findByUserIdAndKey(currentUser.id(), KEY).ifPresent(settings::delete);
        return status();
    }

    private String decrypt(Setting setting) {
        try {
            return cipher.decrypt(objectMapper.readValue(setting.getValue(), String.class));
        } catch (Exception e) {
            throw new IllegalStateException("Token 读取失败，请重新绑定", e);
        }
    }

    private String mask(String token) {
        int visible = Math.min(4, token.length());
        return "••••" + token.substring(token.length() - visible);
    }

    public record TokenView(boolean configured, String maskedToken, String source) {
    }
}
