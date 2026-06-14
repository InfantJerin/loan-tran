package com.loantrans.egs.clearpar;

import com.loantrans.tlm.TradeLifecycleWorkflow;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearParInboundConsumer {
    private static final Logger log = LoggerFactory.getLogger(ClearParInboundConsumer.class);

    private final WorkflowClient client;
    private final ClearParEms ems;
    private final Thread thread;

    public ClearParInboundConsumer(WorkflowClient client, ClearParEms ems) {
        this.client = client;
        this.ems = ems;
        this.thread = new Thread(this::run, "clearpar-inbound");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
        log.info("[ClearPar inbound] consumer started");
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                routeToWorkflow(ems.takeInbound());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("[ClearPar inbound] consumer failure", e);
            }
        }
    }

    private void routeToWorkflow(ClearParInboundMessage msg) {
        String workflowId = "tlm-trade-" + msg.tradeId();
        TradeLifecycleWorkflow w = client.newWorkflowStub(TradeLifecycleWorkflow.class, workflowId);
        if (msg instanceof ClearParInboundMessage.Confirm c) {
            w.onTradeConfirmation(c.payload());
            log.info("[ClearPar inbound] signaled {} with TradeConfirmation", workflowId);
        } else if (msg instanceof ClearParInboundMessage.Alloc a) {
            w.onAllocations(a.payload());
            log.info("[ClearPar inbound] signaled {} with Allocations", workflowId);
        } else if (msg instanceof ClearParInboundMessage.Settled s) {
            w.onTradeSettled(s.payload());
            log.info("[ClearPar inbound] signaled {} with TradeSettledNotice", workflowId);
        }
    }
}
