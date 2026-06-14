package com.loantrans.egs.clearpar;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InMemoryClearParEms implements ClearParEms {
    private final BlockingQueue<ClearParOutboundMessage> outbound = new LinkedBlockingQueue<>();
    private final BlockingQueue<ClearParInboundMessage> inbound = new LinkedBlockingQueue<>();

    @Override public void publishOutbound(ClearParOutboundMessage m) { outbound.offer(m); }
    @Override public ClearParOutboundMessage takeOutbound() throws InterruptedException { return outbound.take(); }
    @Override public void publishInbound(ClearParInboundMessage m) { inbound.offer(m); }
    @Override public ClearParInboundMessage takeInbound() throws InterruptedException { return inbound.take(); }
}
