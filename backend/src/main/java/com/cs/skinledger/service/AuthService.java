package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String rawUsername, String password) {
        String username = rawUsername.trim();
        if (users.existsByUsername(username) && !isClaimableLocal(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user;
        User legacy = users.findByUsername("local").orElse(null);
        if (users.countByPasswordHashIsNotNull() == 0 && legacy != null && legacy.getPasswordHash() == null) {
            user = legacy;
            user.setUsername(username);
        } else {
            user = new User();
            user.setUsername(username);
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        return users.save(user);
    }

    private boolean isClaimableLocal(String username) {
        return "local".equals(username)
                && users.findByUsername("local").map(user -> user.getPasswordHash() == null).orElse(false)
                && users.countByPasswordHashIsNotNull() == 0;
    }
}
