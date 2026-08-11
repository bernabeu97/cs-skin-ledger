package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.util.SecurityTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final UserRepository users;
    private final SecretCipher cipher;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Setup setup(User user) {
        String secret;
        if (user.getTotpSecret() == null) {
            byte[] bytes = new byte[20];
            random.nextBytes(bytes);
            secret = base32Encode(bytes);
            user.setTotpSecret(cipher.encrypt(secret));
            user.setTotpEnabled(false);
            users.save(user);
        } else {
            secret = cipher.decrypt(user.getTotpSecret());
        }
        String label = url("SkinLedger:" + user.getUsername());
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=SkinLedger&digits=6&period=30";
        return new Setup(secret, uri);
    }

    @Transactional
    public List<String> confirm(User user, String code) {
        if (user.getTotpSecret() == null || !verifySecret(cipher.decrypt(user.getTotpSecret()), code)) {
            throw new IllegalArgumentException("验证码不正确");
        }
        List<String> recoveryCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String value = SecurityTokens.randomCode(5) + "-" + SecurityTokens.randomCode(5);
            recoveryCodes.add(value);
            hashes.add(SecurityTokens.sha256(normalizeRecovery(value)));
        }
        user.setRecoveryCodeHashes(String.join(",", hashes));
        user.setTotpEnabled(true);
        user.setSessionVersion(user.getSessionVersion() + 1);
        users.save(user);
        return recoveryCodes;
    }

    @Transactional
    public boolean verify(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTotpEnabled()) || user.getTotpSecret() == null || code == null) return false;
        if (verifySecret(cipher.decrypt(user.getTotpSecret()), code.trim())) return true;
        String hash = SecurityTokens.sha256(normalizeRecovery(code));
        List<String> remaining = new ArrayList<>(List.of(
                user.getRecoveryCodeHashes() == null || user.getRecoveryCodeHashes().isBlank()
                        ? new String[0] : user.getRecoveryCodeHashes().split(",")));
        if (!remaining.remove(hash)) return false;
        user.setRecoveryCodeHashes(String.join(",", remaining));
        users.save(user);
        return true;
    }

    boolean verifySecret(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long step = Instant.now().getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (generate(secret, step + offset).equals(code)) return true;
        }
        return false;
    }

    String generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int value = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return "%06d".formatted(value % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("生成 TOTP 失败", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private byte[] base32Decode(String value) {
        int buffer = 0;
        int bits = 0;
        byte[] output = new byte[value.length() * 5 / 8];
        int index = 0;
        for (char c : value.toUpperCase().toCharArray()) {
            int decoded = BASE32.indexOf(c);
            if (decoded < 0) continue;
            buffer = (buffer << 5) | decoded;
            bits += 5;
            if (bits >= 8) {
                output[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return index == output.length ? output : java.util.Arrays.copyOf(output, index);
    }

    private String normalizeRecovery(String value) {
        return value == null ? "" : value.replace("-", "").trim().toUpperCase();
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record Setup(String manualKey, String provisioningUri) {}
}
