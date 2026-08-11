package com.cs.skinledger.web;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.service.AdminService;
import com.cs.skinledger.service.AuditService;
import com.cs.skinledger.service.CurrentUser;
import com.cs.skinledger.service.InviteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final InviteService inviteService;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final SessionRegistry sessions;

    @GetMapping("/users")
    public List<AdminService.UserView> users() {
        return adminService.users();
    }

    @PostMapping("/users/{id}/state")
    public AdminService.UserView setState(@PathVariable Long id, @Valid @RequestBody UserState body,
                                          HttpServletRequest request) {
        User admin = currentUser.get();
        User changed = adminService.setDisabled(id, body.disabled(), admin.getId());
        expireSessions(changed.getUsername());
        audit.record("ADMIN_USER_STATE", "SUCCESS", admin, null, "USER", id.toString(),
                "disabled=" + body.disabled(), request);
        return adminService.users().stream().filter(user -> user.id().equals(id)).findFirst().orElseThrow();
    }

    @PostMapping("/users/{id}/reset-password")
    public AdminService.UserView resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPassword body,
                                               HttpServletRequest request) {
        User admin = currentUser.get();
        User changed = adminService.resetPassword(id, body.password(), admin.getId());
        expireSessions(changed.getUsername());
        audit.record("ADMIN_PASSWORD_RESET", "SUCCESS", admin, null, "USER", id.toString(), null, request);
        return adminService.users().stream().filter(user -> user.id().equals(id)).findFirst().orElseThrow();
    }

    @PostMapping("/invites")
    public InviteService.CreatedInvite createInvite(@Valid @RequestBody CreateInvite body, HttpServletRequest request) {
        User admin = currentUser.get();
        InviteService.CreatedInvite invite = inviteService.create(admin, body.expiresInDays());
        audit.record("INVITE_CREATED", "SUCCESS", admin, null, "INVITE", invite.id().toString(),
                "expiresAt=" + invite.expiresAt(), request);
        return invite;
    }

    @GetMapping("/invites")
    public List<AdminService.InviteView> invites() {
        return adminService.invites();
    }

    @GetMapping("/audits")
    public List<AdminService.AuditView> audits(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return adminService.audits(page, size);
    }

    private void expireSessions(String username) {
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails details && details.getUsername().equals(username)) {
                sessions.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }

    public record UserState(boolean disabled) {}
    public record CreateInvite(@Min(1) @Max(30) int expiresInDays) {}
    public record ResetPassword(@NotBlank @Size(min = 12, max = 128) String password) {}
}
