package com.loantrans.egs;

import com.loantrans.TaskQueues;
import com.loantrans.egs.clearpar.ClearParEms;
import com.loantrans.egs.clearpar.ClearParOutboundMessage;
import com.loantrans.model.DealTermsCommitted;
import com.loantrans.model.DomainEvent;
import com.loantrans.model.TradeSettled;
import com.loantrans.servicing.ServicingPostSettlementWorkflow;
import com.loantrans.servicing.ServicingSetupWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgsActivitiesImpl implements EgsActivities {
    private static final Logger log = LoggerFactory.getLogger(EgsActivitiesImpl.class);

    private final WorkflowClient client;
    private final ClearParEms clearParEms;

    public EgsActivitiesImpl(WorkflowClient client, ClearParEms clearParEms) {
        this.client = client;
        this.clearParEms = clearParEms;
    }

    @Override
    public void publish(DomainEvent event) {
        log.info("[EGS] publish {}", event.getClass().getSimpleName());
        if (event instanceof DealTermsCommitted e) {
            routeDealTermsCommitted(e);
            return;
        }
        if (event instanceof TradeSettled e) {
            routeTradeSettled(e);
            return;
        }
        log.warn("[EGS] no route for event type {}", event.getClass().getSimpleName());
    }

    @Override
    public void publishToClearPar(ClearParOutboundMessage message) {
        log.info("[EGS->ClearPar] publish {} for trade {}",
                message.getClass().getSimpleName(), message.tradeId());
        clearParEms.publishOutbound(message);
    }

    private void routeDealTermsCommitted(DealTermsCommitted e) {
        String dealId = e.terms().dealId();
        ServicingSetupWorkflow servicing = client.newWorkflowStub(
                ServicingSetupWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TaskQueues.SERVICING_TQ)
                        .setWorkflowId("servicing-setup-" + dealId)
                        .build());
        WorkflowExecution exec = WorkflowClient.start(servicing::onTermsCommitted, e.terms());
        log.info("[EGS] DealTermsCommitted -> started {} (run {})",
                exec.getWorkflowId(), exec.getRunId());
    }

    private void routeTradeSettled(TradeSettled e) {
        String tradeId = e.tradeId();
        ServicingPostSettlementWorkflow servicing = client.newWorkflowStub(
                ServicingPostSettlementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TaskQueues.SERVICING_TQ)
                        .setWorkflowId("servicing-post-settlement-" + tradeId)
                        .build());
        WorkflowExecution exec = WorkflowClient.start(servicing::onTradeSettled, tradeId);
        log.info("[EGS] TradeSettled -> started {} (run {})",
                exec.getWorkflowId(), exec.getRunId());
    }
}
