package com.cs.skinledger.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimiter {
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(15);
    private static final int MAX_REGISTRATIONS_PER_HOUR = 10;
    private final Map<String, Counter> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Counter> registrations = new ConcurrentHashMap<>();

    public void checkLogin(String key) {
        Counter counter = loginFailures.get(key);
        if (counter != null && counter.blockedUntil != null && counter.blockedUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请 15 分钟后重试");
        }
    }

    public void loginFailed(String key) {
        loginFailures.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            Counter next = current == null || current.windowStart.plus(LOGIN_LOCK).isBefore(now)
                    ? new Counter(now) : current;
            next.count++;
            if (next.count >= MAX_LOGIN_FAILURES) next.blockedUntil = now.plus(LOGIN_LOCK);
            return next;
        });
    }

    public void loginSucceeded(String key) {
        loginFailures.remove(key);
    }

    public void checkRegistration(String ip) {
        Counter counter = registrations.compute(ip, (ignored, current) -> {
            Instant now = Instant.now();
            Counter next = current == null || current.windowStart.plus(Duration.ofHours(1)).isBefore(now)
                    ? new Counter(now) : current;
            next.count++;
            return next;
        });
        if (counter.count > MAX_REGISTRATIONS_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "注册请求过于频繁，请稍后重试");
        }
    }

    // ponytail: 当前部署只有一个后端实例；扩容到多实例时替换为共享限流存储。
    private static final class Counter {
        private final Instant windowStart;
        private int count;
        private Instant blockedUntil;

        private Counter(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
