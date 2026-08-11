package com.cs.skinledger.config;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccountGuardFilter extends OncePerRequestFilter {
    public static final String SESSION_VERSION = "skinLedgerSessionVersion";
    private final UserRepository users;
    private final ObjectMapper mapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }
        User user = users.findByUsername(auth.getName()).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getDisabled())) {
            reject(request, response, 401, "账号已失效，请重新登录");
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object version = session.getAttribute(SESSION_VERSION);
            if (version != null && !version.equals(user.getSessionVersion())) {
                reject(request, response, 401, "登录状态已失效，请重新登录");
                return;
            }
        }
        if (!request.getRequestURI().startsWith("/api/auth/")) {
            if (Boolean.TRUE.equals(user.getMustChangePassword())) {
                reject(request, response, 403, "请先修改临时密码");
                return;
            }
            if ("ADMIN".equals(user.getRole()) && !Boolean.TRUE.equals(user.getTotpEnabled())) {
                reject(request, response, 403, "管理员必须先启用双重验证");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (status == 401 && session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
