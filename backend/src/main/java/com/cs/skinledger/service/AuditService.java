package com.cs.skinledger.service;

import com.cs.skinledger.domain.AuditLog;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository logs;

    @Transactional
    public void record(String event, String status, User user, String username,
                       String targetType, String targetId, String details, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setEventType(limit(event, 64));
        log.setStatus(limit(status, 16));
        log.setUser(user);
        log.setUsername(limit(username != null ? username : user == null ? null : user.getUsername(), 64));
        log.setTargetType(limit(targetType, 64));
        log.setTargetId(limit(targetId, 128));
        log.setDetails(limit(details, 1000));
        if (request != null) {
            String forwarded = request.getHeader("X-Real-IP");
            log.setIpAddress(limit(forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded, 64));
            log.setUserAgent(limit(request.getHeader("User-Agent"), 512));
        }
        logs.save(log);
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void removeExpired() {
        logs.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(180));
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
