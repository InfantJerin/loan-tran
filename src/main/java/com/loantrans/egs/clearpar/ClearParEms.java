package com.loantrans.egs.clearpar;

public interface ClearParEms {
    void publishOutbound(ClearParOutboundMessage message);

    ClearParOutboundMessage takeOutbound() throws InterruptedException;

    void publishInbound(ClearParInboundMessage message);

    ClearParInboundMessage takeInbound() throws InterruptedException;
}
