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

public class EgsDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EgsDispatcher.class);

    private final WorkflowClient client;
    private final EventBus bus;
    private final Thread thread;

    public EgsDispatcher(WorkflowClient client, EventBus bus) {
        this.client = client;
        this.bus = bus;
        this.thread = new Thread(this::run, "egs-dispatcher");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
        log.info("[EGS] dispatcher started");
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                route(bus.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("[EGS] dispatch failure", e);
            }
        }
    }

    private void route(DomainEvent event) {
        if (event instanceof DealTermsCommitted e) {
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
            return;
        }
        log.warn("[EGS] no route for event type {}", event.getClass().getSimpleName());
    }
}
