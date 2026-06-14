package com.loantrans.egs.clearpar;

public record SettlementCoordinationMessage(String tradeId, long requestedSettlementEpochMs)
        implements ClearParOutboundMessage {}
