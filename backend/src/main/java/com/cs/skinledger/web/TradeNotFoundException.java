package com.cs.skinledger.web;

public class TradeNotFoundException extends RuntimeException {
    public TradeNotFoundException(Long id) {
        super("交易不存在: " + id);
    }
}