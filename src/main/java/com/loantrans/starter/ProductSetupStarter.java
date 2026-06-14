package com.loantrans.starter;

import com.loantrans.TaskQueues;
import com.loantrans.dts.DealTermSetupWorkflow;
import com.loantrans.model.ProductSetupEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ProductSetupStarter {
    private static final Logger log = LoggerFactory.getLogger(ProductSetupStarter.class);

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        String dealId = args.length > 0 ? args[0] : "deal-" + UUID.randomUUID();
        ProductSetupEvent event = new ProductSetupEvent(dealId, "TERM-LOAN-A", "ACME Corp", 10_000_000.00);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.DTS_TQ)
                .setWorkflowId("dts-setup-" + dealId)
                .build();

        DealTermSetupWorkflow workflow = client.newWorkflowStub(DealTermSetupWorkflow.class, options);
        log.info("Starting DealTermSetupWorkflow for {}", dealId);
        String ledgerId = workflow.setup(event);
        log.info("Workflow completed. Ledger id: {}", ledgerId);
    }
}
