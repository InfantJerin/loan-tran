package com.loantrans.model;

public record LedgerAck(String ledgerId, String dealId, long writtenAtEpochMs) {}
