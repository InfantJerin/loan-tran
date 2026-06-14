package com.loantrans.tlm;

import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class TradeLifecycleWorkflowImpl implements TradeLifecycleWorkflow {
    private static final Logger log = Workflow.getLogger(TradeLifecycleWorkflowImpl.class);

    @Override
    public void run(String tradeId, String dealId, double notional) {
        log.info("[TLM] received trade {} for deal {} — stub, Flow B not wired yet", tradeId, dealId);
    }
}
