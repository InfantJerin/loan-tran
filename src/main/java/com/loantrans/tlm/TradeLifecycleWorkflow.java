package com.loantrans.tlm;

import com.loantrans.model.Allocations;
import com.loantrans.model.BookTradeEvent;
import com.loantrans.model.TradeConfirmation;
import com.loantrans.model.TradeSettledNotice;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TradeLifecycleWorkflow {
    @WorkflowMethod
    void run(BookTradeEvent event);

    @SignalMethod
    void onTradeConfirmation(TradeConfirmation confirmation);

    @SignalMethod
    void onAllocations(Allocations allocations);

    @SignalMethod
    void onTradeSettled(TradeSettledNotice settled);
}
