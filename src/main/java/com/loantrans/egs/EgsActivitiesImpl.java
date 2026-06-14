package com.loantrans.egs;

import com.loantrans.TaskQueues;
import com.loantrans.model.DealTermsCommitted;
import com.loantrans.model.DomainEvent;
import com.loantrans.servicing.ServicingSetupWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgsActivitiesImpl implements EgsActivities {
    private static final Logger log = LoggerFactory.getLogger(EgsActivitiesImpl.class);

    private final WorkflowClient client;

    public EgsActivitiesImpl(WorkflowClient client) {
        this.client = client;
    }

    @Override
    public void publish(DomainEvent event) {
        log.info("[EGS] publish {}", event.getClass().getSimpleName());
        if (event instanceof DealTermsCommitted e) {
            routeDealTermsCommitted(e);
            return;
        }
        log.warn("[EGS] no route for event type {}", event.getClass().getSimpleName());
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
}
