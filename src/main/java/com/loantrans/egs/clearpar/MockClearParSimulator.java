package com.loantrans.egs.clearpar;

import com.loantrans.model.AllocationLine;
import com.loantrans.model.Allocations;
import com.loantrans.model.TradeConfirmation;
import com.loantrans.model.TradeSettledNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MockClearParSimulator {
    private static final Logger log = LoggerFactory.getLogger(MockClearParSimulator.class);

    private final ClearParEms ems;
    private final Thread thread;

    public MockClearParSimulator(ClearParEms ems) {
        this.ems = ems;
        this.thread = new Thread(this::run, "clearpar-simulator");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
        log.info("[ClearPar mock] simulator started");
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                handle(ems.takeOutbound());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("[ClearPar mock] simulator failure", e);
            }
        }
    }

    private void handle(ClearParOutboundMessage msg) throws InterruptedException {
        log.info("[ClearPar mock] received {} for trade {}",
                msg.getClass().getSimpleName(), msg.tradeId());

        if (msg instanceof UploadTradeMessage u) {
            Thread.sleep(500);
            ems.publishInbound(new ClearParInboundMessage.Confirm(
                    new TradeConfirmation(u.tradeId(), "CP-CONF-" + u.tradeId())));
            log.info("[ClearPar mock] sent TradeConfirmation for {}", u.tradeId());

            Thread.sleep(500);
            ems.publishInbound(new ClearParInboundMessage.Alloc(
                    new Allocations(u.tradeId(), List.of(
                            new AllocationLine("CPTY-A", u.notional() * 0.6),
                            new AllocationLine("CPTY-B", u.notional() * 0.4)))));
            log.info("[ClearPar mock] sent Allocations for {}", u.tradeId());
            return;
        }
        if (msg instanceof SettlementCoordinationMessage s) {
            Thread.sleep(500);
            ems.publishInbound(new ClearParInboundMessage.Settled(
                    new TradeSettledNotice(s.tradeId(), System.currentTimeMillis())));
            log.info("[ClearPar mock] sent TradeSettledNotice for {}", s.tradeId());
        }
    }
}
