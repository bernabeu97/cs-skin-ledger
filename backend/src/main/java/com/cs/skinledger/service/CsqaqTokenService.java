package com.cs.skinledger.service;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.domain.Setting;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.service.price.CsqaqRequestGate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final CsqaqRequestGate requestGate;

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

    /** 将当前账号 Token 绑定到实际发起行情请求的后端服务器出口 IP。 */
    public IpBindingView bindCurrentServerIp() {
        String token = currentToken()
                .orElseThrow(() -> new IllegalArgumentException("CSQAQ ApiToken 未绑定，请先保存 Token"));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            properties.getCsqaq().getBaseUrl() + "/api/v1/sys/bind_local_ip"))
                    .timeout(Duration.ofSeconds(properties.getCsqaq().getTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("ApiToken", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            requestGate.awaitTurn();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(response.statusCode());
            if (response.statusCode() >= 400 || code != 200) {
                throw new ExternalServiceException("CSQAQ 出口 IP 绑定失败: "
                        + root.path("msg").asText("HTTP " + response.statusCode()));
            }
            return new IpBindingView(true, root.path("data").asText("当前服务器出口 IP 已绑定"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("CSQAQ 出口 IP 绑定已取消", e);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("CSQAQ 出口 IP 绑定失败: " + e.getMessage(), e);
        }
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

    public record IpBindingView(boolean bound, String message) {
    }
}
