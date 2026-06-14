package com.loantrans.tlm;

import com.loantrans.TaskQueues;
import com.loantrans.ctl.LedgerActivities;
import com.loantrans.egs.EgsActivities;
import com.loantrans.egs.clearpar.SettlementCoordinationMessage;
import com.loantrans.egs.clearpar.UploadTradeMessage;
import com.loantrans.model.Allocations;
import com.loantrans.model.BookTradeEvent;
import com.loantrans.model.TradeConfirmation;
import com.loantrans.model.TradeSettled;
import com.loantrans.model.TradeSettledNotice;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class TradeLifecycleWorkflowImpl implements TradeLifecycleWorkflow {
    private static final Logger log = Workflow.getLogger(TradeLifecycleWorkflowImpl.class);

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

    private TradeConfirmation confirmation;
    private Allocations allocations;
    private TradeSettledNotice settled;

    @Override
    public void run(BookTradeEvent event) {
        String tradeId = event.tradeId();
        log.info("[TLM] received book trade {}", tradeId);

        ctl.bookTrade(tradeId, event.dealId(), event.notional());
        log.info("[TLM] trade booked to ledger");

        egs.publishToClearPar(new UploadTradeMessage(tradeId, event.dealId(), event.notional()));
        log.info("[TLM] UploadTradeMessage published; awaiting ClearPar confirmation");

        Workflow.await(() -> confirmation != null);
        log.info("[TLM] confirmation received: {}", confirmation.clearParConfirmationId());

        Workflow.await(() -> allocations != null);
        log.info("[TLM] allocations received: {} lines", allocations.lines().size());

        ctl.writeAllocations(tradeId, allocations);
        log.info("[TLM] allocations written to ledger");

        egs.publishToClearPar(new SettlementCoordinationMessage(
                tradeId, Workflow.currentTimeMillis() + Duration.ofDays(2).toMillis()));
        log.info("[TLM] SettlementCoordinationMessage published; awaiting settled notice");

        Workflow.await(() -> settled != null);
        log.info("[TLM] settled notice received");

        ctl.settleTrade(tradeId);
        log.info("[TLM] settlement committed to ledger");

        egs.publish(new TradeSettled(tradeId));
        log.info("[TLM] TradeSettled domain event published — TLM done");
    }

    @Override
    public void onTradeConfirmation(TradeConfirmation c) {
        if (confirmation != null) return;
        confirmation = c;
    }

    @Override
    public void onAllocations(Allocations a) {
        if (allocations != null) return;
        allocations = a;
    }

    @Override
    public void onTradeSettled(TradeSettledNotice s) {
        if (settled != null) return;
        settled = s;
    }
}
