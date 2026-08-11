package com.cs.skinledger.service;

import com.cs.skinledger.domain.AuditLog;
import com.cs.skinledger.domain.InviteCode;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.AuditLogRepository;
import com.cs.skinledger.repository.InviteCodeRepository;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository users;
    private final InviteCodeRepository invites;
    private final AuditLogRepository audits;
    private final PasswordEncoder passwords;

    @Transactional(readOnly = true)
    public List<UserView> users() {
        return users.findAll().stream().map(UserView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InviteView> invites() {
        return invites.findTop100ByOrderByCreatedAtDesc().stream().map(InviteView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditView> audits(int page, int size) {
        return audits.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .stream().map(AuditView::from).toList();
    }

    @Transactional
    public User setDisabled(Long id, boolean disabled, Long actingUserId) {
        if (id.equals(actingUserId)) throw new IllegalArgumentException("不能禁用当前管理员账号");
        User user = requireUser(id);
        user.setDisabled(disabled);
        user.setSessionVersion(user.getSessionVersion() + 1);
        return users.save(user);
    }

    @Transactional
    public User resetPassword(Long id, String password, Long actingUserId) {
        if (id.equals(actingUserId)) throw new IllegalArgumentException("请在账号安全页修改自己的密码");
        User user = requireUser(id);
        user.setPasswordHash(passwords.encode(password));
        user.setMustChangePassword(true);
        user.setSessionVersion(user.getSessionVersion() + 1);
        return users.save(user);
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public record UserView(Long id, String username, String role, boolean disabled, boolean totpEnabled,
                           boolean mustChangePassword, LocalDateTime deletionRequestedAt, LocalDateTime createdAt) {
        static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getRole(), Boolean.TRUE.equals(user.getDisabled()),
                    Boolean.TRUE.equals(user.getTotpEnabled()), Boolean.TRUE.equals(user.getMustChangePassword()),
                    user.getDeletionRequestedAt(), user.getCreatedAt());
        }
    }

    public record InviteView(Long id, String createdBy, String usedBy, LocalDateTime expiresAt,
                             LocalDateTime usedAt, LocalDateTime createdAt) {
        static InviteView from(InviteCode invite) {
            return new InviteView(invite.getId(), invite.getCreatedBy().getUsername(),
                    invite.getUsedBy() == null ? null : invite.getUsedBy().getUsername(), invite.getExpiresAt(),
                    invite.getUsedAt(), invite.getCreatedAt());
        }
    }

    public record AuditView(Long id, String username, String eventType, String status, String ipAddress,
                            String userAgent, String targetType, String targetId, String details,
                            LocalDateTime createdAt) {
        static AuditView from(AuditLog log) {
            return new AuditView(log.getId(), log.getUsername(), log.getEventType(), log.getStatus(),
                    log.getIpAddress(), log.getUserAgent(), log.getTargetType(), log.getTargetId(),
                    log.getDetails(), log.getCreatedAt());
        }
    }
}
