package com.loantrans.servicing;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ServicingPostSettlementWorkflow {
    @WorkflowMethod
    void onTradeSettled(String tradeId);
}
