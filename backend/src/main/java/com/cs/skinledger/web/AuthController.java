package com.cs.skinledger.web;

import com.cs.skinledger.config.AccountGuardFilter;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.service.AuditService;
import com.cs.skinledger.service.AuthRateLimiter;
import com.cs.skinledger.service.AuthService;
import com.cs.skinledger.service.CurrentUser;
import com.cs.skinledger.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final TotpService totp;
    private final AuthRateLimiter rateLimiter;
    private final AuditService audit;
    private final SessionRegistry sessions;

    @PostMapping("/register")
    public AuthView register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimiter.checkRegistration(ip);
        User user = authService.register(body.username(), body.password(), body.inviteCode());
        audit.record("ACCOUNT_REGISTERED", "SUCCESS", user, user.getUsername(), "USER",
                user.getId().toString(), null, request);
        Authentication authentication = authenticate(body.username().trim(), body.password());
        return establishSession(authentication, user, request);
    }

    @PostMapping("/login")
    public AuthView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String username = body.username().trim();
        String key = clientIp(request) + "|" + username.toLowerCase();
        rateLimiter.checkLogin(key);
        try {
            Authentication authentication = authenticate(username, body.password());
            User user = users.findByUsername(username).orElseThrow(() -> new BadCredentialsException("账号或密码错误"));
            if (Boolean.TRUE.equals(user.getTotpEnabled()) && !totp.verify(user, body.totpCode())) {
                throw new BadCredentialsException("双重验证码或恢复码错误");
            }
            rateLimiter.loginSucceeded(key);
            audit.record("LOGIN", "SUCCESS", user, username, null, null, null, request);
            return establishSession(authentication, user, request);
        } catch (AuthenticationException e) {
            rateLimiter.loginFailed(key);
            audit.record("LOGIN", "FAILURE", null, username, null, null, "认证失败", request);
            throw e;
        }
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        User user = authenticatedUserOrNull();
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessions.removeSessionInformation(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        audit.record("LOGOUT", "SUCCESS", user, null, null, null, null, request);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public AuthView me(Authentication authentication) {
        boolean loggedIn = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        if (!loggedIn) return AuthView.anonymous();
        return AuthView.from(users.findByUsername(authentication.getName()).orElseThrow());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/totp/setup")
    public TotpService.Setup setupTotp(HttpServletRequest request) {
        User user = currentUser.get();
        TotpService.Setup setup = totp.setup(user);
        audit.record("TOTP_SETUP_STARTED", "SUCCESS", user, null, "USER", user.getId().toString(), null, request);
        return setup;
    }

    @PostMapping("/totp/confirm")
    public RecoveryCodes confirmTotp(@Valid @RequestBody TotpCode body, HttpServletRequest request) {
        User user = currentUser.get();
        List<String> recoveryCodes = totp.confirm(user, body.code());
        HttpSession session = request.getSession(false);
        if (session != null) session.setAttribute(AccountGuardFilter.SESSION_VERSION, user.getSessionVersion());
        audit.record("TOTP_ENABLED", "SUCCESS", user, null, "USER", user.getId().toString(), null, request);
        return new RecoveryCodes(recoveryCodes);
    }

    @PostMapping("/password")
    public AuthView changePassword(@Valid @RequestBody PasswordChange body, HttpServletRequest request) {
        User user = currentUser.get();
        authenticate(user.getUsername(), body.currentPassword());
        User saved = authService.changePassword(user, body.newPassword());
        expireAllSessions(saved.getUsername());
        HttpSession current = request.getSession(false);
        if (current != null) current.invalidate();
        SecurityContextHolder.clearContext();
        audit.record("PASSWORD_CHANGED", "SUCCESS", saved, null, "USER", saved.getId().toString(), null, request);
        return AuthView.anonymous();
    }

    private Authentication authenticate(String username, String password) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    private AuthView establishSession(Authentication authentication, User user, HttpServletRequest request) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            sessions.removeSessionInformation(previous.getId());
            previous.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute(AccountGuardFilter.SESSION_VERSION, user.getSessionVersion());
        expireOldest(authentication.getPrincipal());
        sessions.registerNewSession(session.getId(), authentication.getPrincipal());
        return AuthView.from(user);
    }

    private void expireOldest(Object principal) {
        List<SessionInformation> active = sessions.getAllSessions(principal, false).stream()
                .sorted(Comparator.comparing(SessionInformation::getLastRequest)).toList();
        for (int i = 0; i < Math.max(0, active.size() - 4); i++) active.get(i).expireNow();
    }

    private void expireAllSessions(String username) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(username)) {
                sessions.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }

    private User authenticatedUserOrNull() {
        try {
            return currentUser.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        return real == null || real.isBlank() ? request.getRemoteAddr() : real;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32)
            @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "只能包含文字、数字、下划线和短横线")
            String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 64) String inviteCode) {}

    public record LoginRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 1, max = 128) String password,
            @Size(max = 32) String totpCode) {}

    public record TotpCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}
    public record PasswordChange(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record RecoveryCodes(List<String> recoveryCodes) {}

    public record AuthView(boolean authenticated, String username, String role, boolean totpEnabled,
                           boolean mfaSetupRequired, boolean passwordChangeRequired) {
        static AuthView anonymous() {
            return new AuthView(false, null, null, false, false, false);
        }

        static AuthView from(User user) {
            boolean adminSetup = "ADMIN".equals(user.getRole()) && !Boolean.TRUE.equals(user.getTotpEnabled());
            return new AuthView(true, user.getUsername(), user.getRole(), Boolean.TRUE.equals(user.getTotpEnabled()),
                    adminSetup, Boolean.TRUE.equals(user.getMustChangePassword()));
        }
    }
}
