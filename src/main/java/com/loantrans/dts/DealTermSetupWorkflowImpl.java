package com.loantrans.dts;

import com.loantrans.TaskQueues;
import com.loantrans.ctl.LedgerActivities;
import com.loantrans.egs.EgsActivities;
import com.loantrans.model.DealTerms;
import com.loantrans.model.DealTermsCommitted;
import com.loantrans.model.LedgerAck;
import com.loantrans.model.ProductSetupEvent;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class DealTermSetupWorkflowImpl implements DealTermSetupWorkflow {
    private static final Logger log = Workflow.getLogger(DealTermSetupWorkflowImpl.class);

    private final LedgerActivities ctl = Workflow.newActivityStub(
            LedgerActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CTL_TQ)
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build());

    private final EgsActivities egs = Workflow.newActivityStub(
            EgsActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.EGS_TQ)
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build());

    @Override
    public String setup(ProductSetupEvent event) {
        log.info("[DTS] received product setup: {}", event);

        DealTerms terms = new DealTerms(
                event.dealId(),
                event.productCode(),
                event.borrower(),
                event.notional(),
                "ACTIVE");

        LedgerAck ack = ctl.writeDealTerms(terms);
        log.info("[DTS] terms committed to ledger {}", ack.ledgerId());

        egs.publish(new DealTermsCommitted(terms));
        log.info("[DTS] DealTermsCommitted published — DTS done");

        return ack.ledgerId();
    }
}
