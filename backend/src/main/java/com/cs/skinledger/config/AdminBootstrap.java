package com.cs.skinledger.config;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Value("${app.security.admin-username:}")
    private String adminUsername;

    @Value("${app.security.admin-password:}")
    private String adminPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureAdmin() {
        if (users.countByRole("ADMIN") > 0) return;
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.length() < 12) {
            log.warn("尚未初始化管理员：请设置 APP_ADMIN_USERNAME 和至少 12 字符的 APP_ADMIN_PASSWORD");
            return;
        }
        String username = adminUsername.trim();
        User user = users.findByUsername(username).orElseGet(() ->
                users.findByUsername("local").filter(candidate -> candidate.getPasswordHash() == null)
                        .orElseGet(User::new));
        if (user.getId() == null && users.existsByUsername(username)) {
            throw new IllegalStateException("管理员用户名已被占用");
        }
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(adminPassword));
        user.setRole("ADMIN");
        user.setDisabled(false);
        user.setMustChangePassword(false);
        users.save(user);
        log.info("管理员账号已通过部署配置初始化：{}", username);
    }
}
