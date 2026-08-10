package com.cs.skinledger.web;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public AuthView register(@Valid @RequestBody Credentials request, HttpServletRequest servletRequest) {
        User user = authService.register(request.username(), request.password());
        return establishSession(user.getUsername(), request.password(), servletRequest);
    }

    @PostMapping("/login")
    public AuthView login(@Valid @RequestBody Credentials request, HttpServletRequest servletRequest) {
        return establishSession(request.username().trim(), request.password(), servletRequest);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public AuthView me(Authentication authentication) {
        boolean loggedIn = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        return new AuthView(loggedIn, loggedIn ? authentication.getName() : null);
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    private AuthView establishSession(String username, String password, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return new AuthView(true, authentication.getName());
    }

    public record Credentials(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "只能包含文字、数字、下划线和短横线")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record AuthView(boolean authenticated, String username) {
    }
}
