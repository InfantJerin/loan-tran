package com.loantrans.starter;

import com.loantrans.TaskQueues;
import com.loantrans.model.BookTradeEvent;
import com.loantrans.tlm.TradeLifecycleWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BookTradeStarter {
    private static final Logger log = LoggerFactory.getLogger(BookTradeStarter.class);

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        String tradeId = args.length > 0 ? args[0] : "trade-" + UUID.randomUUID();
        String dealId = args.length > 1 ? args[1] : "deal-existing";
        BookTradeEvent event = new BookTradeEvent(tradeId, dealId, 5_000_000.00);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.TLM_TQ)
                .setWorkflowId("tlm-trade-" + tradeId)
                .build();

        TradeLifecycleWorkflow workflow = client.newWorkflowStub(TradeLifecycleWorkflow.class, options);
        log.info("Starting TradeLifecycleWorkflow for {}", tradeId);
        workflow.run(event);
        log.info("Workflow completed for trade {}", tradeId);
    }
}
