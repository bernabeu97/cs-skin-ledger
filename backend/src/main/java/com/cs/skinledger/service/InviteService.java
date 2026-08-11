package com.cs.skinledger.service;

import com.cs.skinledger.domain.InviteCode;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.InviteCodeRepository;
import com.cs.skinledger.util.SecurityTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InviteService {
    private final InviteCodeRepository invites;

    @Transactional
    public CreatedInvite create(User admin, int expiresInDays) {
        int days = Math.max(1, Math.min(30, expiresInDays));
        String raw = SecurityTokens.randomCode(6) + "-" + SecurityTokens.randomCode(6)
                + "-" + SecurityTokens.randomCode(6);
        InviteCode invite = new InviteCode();
        invite.setCodeHash(SecurityTokens.sha256(normalize(raw)));
        invite.setCreatedBy(admin);
        invite.setExpiresAt(LocalDateTime.now().plusDays(days));
        InviteCode saved = invites.save(invite);
        return new CreatedInvite(saved.getId(), raw, saved.getExpiresAt());
    }

    public InviteCode requireValid(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) throw new IllegalArgumentException("请输入邀请码");
        InviteCode invite = invites.findForUse(SecurityTokens.sha256(normalize(rawCode)))
                .orElseThrow(() -> new IllegalArgumentException("邀请码无效"));
        if (invite.getUsedAt() != null) throw new IllegalArgumentException("邀请码已使用");
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("邀请码已过期");
        return invite;
    }

    public void markUsed(InviteCode invite, User user) {
        invite.setUsedBy(user);
        invite.setUsedAt(LocalDateTime.now());
        invites.save(invite);
    }

    private String normalize(String value) {
        return value.replace("-", "").trim().toUpperCase();
    }

    public record CreatedInvite(Long id, String code, LocalDateTime expiresAt) {}
}
