package com.cs.skinledger.service.price;

import org.springframework.stereotype.Component;

/**
 * CSQAQ 官方限制同一 IP 每秒最多一次请求。价格、指数共用此闸门，避免互相抢占额度。
 */
@Component
public class CsqaqRequestGate {

    private static final long INTERVAL_NANOS = 1_050_000_000L;
    private long nextAllowedNanos;

    public synchronized void awaitTurn() throws InterruptedException {
        long now = System.nanoTime();
        long waitNanos = nextAllowedNanos - now;
        if (waitNanos > 0) {
            long millis = waitNanos / 1_000_000L;
            int nanos = (int) (waitNanos % 1_000_000L);
            Thread.sleep(millis, nanos);
        }
        nextAllowedNanos = System.nanoTime() + INTERVAL_NANOS;
    }
}
