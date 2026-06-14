package com.loantrans.ctl;

import com.loantrans.model.DealTerms;
import com.loantrans.model.LedgerAck;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface LedgerActivities {
    LedgerAck writeDealTerms(DealTerms terms);

    LedgerAck bookTrade(String tradeId, String dealId, double notional);

    LedgerAck settleTrade(String tradeId);
}
