package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.domain.InviteCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final InviteService inviteService;

    @Transactional
    public User register(String rawUsername, String password, String inviteCode) {
        String username = rawUsername.trim();
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        InviteCode invite = inviteService.requireValid(inviteCode);
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setDisabled(false);
        User saved = users.save(user);
        inviteService.markUsed(invite, saved);
        return saved;
    }

    @Transactional
    public User changePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setSessionVersion(user.getSessionVersion() + 1);
        return users.save(user);
    }
}
