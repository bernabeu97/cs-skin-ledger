package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository users;

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("请先登录");
        }
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("登录账号不存在"));
    }

    public Long id() {
        return get().getId();
    }
}
