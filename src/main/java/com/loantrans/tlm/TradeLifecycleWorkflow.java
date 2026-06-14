package com.loantrans.tlm;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TradeLifecycleWorkflow {
    @WorkflowMethod
    void run(String tradeId, String dealId, double notional);
}
