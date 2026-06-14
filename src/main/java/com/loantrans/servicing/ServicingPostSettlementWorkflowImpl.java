package com.loantrans.servicing;

import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class ServicingPostSettlementWorkflowImpl implements ServicingPostSettlementWorkflow {
    private static final Logger log = Workflow.getLogger(ServicingPostSettlementWorkflowImpl.class);

    @Override
    public void onTradeSettled(String tradeId) {
        log.info("[Servicing] trade {} settled — placeholder for post-settlement work", tradeId);
    }
}
