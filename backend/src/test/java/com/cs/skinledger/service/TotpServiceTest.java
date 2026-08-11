package com.cs.skinledger.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotpServiceTest {
    @Test
    void matchesRfc6238Sha1VectorReducedToSixDigits() {
        TotpService service = new TotpService(null, null);
        // RFC 6238 的 ASCII 密钥 12345678901234567890，59 秒对应计数器 1。
        assertEquals("287082", service.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1));
    }
}
