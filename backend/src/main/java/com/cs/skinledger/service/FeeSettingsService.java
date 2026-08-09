package com.cs.skinledger.service;

import com.cs.skinledger.domain.Setting;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.FeeSettings;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台费率配置（settings 表 key=fees），用于卖出表单自动带出建议手续费。
 */
@Service
@RequiredArgsConstructor
public class FeeSettingsService {

    private static final String KEY = "fees";

    private final SettingRepository settingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeeSettings get() {
        return settingRepository.findByUserIdAndKey(localUserId(), KEY)
                .map(s -> {
                    try {
                        return objectMapper.readValue(s.getValue(), FeeSettings.class);
                    } catch (Exception e) {
                        return FeeSettings.defaults();
                    }
                })
                .orElseGet(FeeSettings::defaults);
    }

    @Transactional
    public FeeSettings save(FeeSettings fees) {
        Setting setting = settingRepository.findByUserIdAndKey(localUserId(), KEY).orElseGet(() -> {
            Setting s = new Setting();
            s.setUser(localUser());
            s.setKey(KEY);
            return s;
        });
        try {
            setting.setValue(objectMapper.writeValueAsString(fees));
        } catch (Exception e) {
            throw new IllegalStateException("费率配置序列化失败", e);
        }
        settingRepository.save(setting);
        return fees;
    }

    /** 按平台返回费率（未知平台返回 0） */
    public BigDecimal rateFor(String platform) {
        FeeSettings fees = get();
        if ("steam".equals(platform)) return fees.steam();
        if ("uu".equals(platform)) return fees.uu();
        if ("buff".equals(platform)) return fees.buff();
        return java.math.BigDecimal.ZERO;
    }

    private Long localUserId() {
        return localUser().getId();
    }

    private User localUser() {
        return userRepository.findByUsername("local")
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername("local");
                    return userRepository.save(user);
                });
    }
}