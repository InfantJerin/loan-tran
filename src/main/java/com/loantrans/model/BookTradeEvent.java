package com.loantrans.model;

public record BookTradeEvent(String tradeId, String dealId, double notional) {}
