package com.loantrans.egs.clearpar;

public record UploadTradeMessage(String tradeId, String dealId, double notional)
        implements ClearParOutboundMessage {}
