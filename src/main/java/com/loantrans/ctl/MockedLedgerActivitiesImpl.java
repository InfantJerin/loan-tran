package com.loantrans.ctl;

import com.loantrans.model.DealTerms;
import com.loantrans.model.LedgerAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MockedLedgerActivitiesImpl implements LedgerActivities {
    private static final Logger log = LoggerFactory.getLogger(MockedLedgerActivitiesImpl.class);

    @Override
    public LedgerAck writeDealTerms(DealTerms terms) {
        log.info("[CTL mock] writeDealTerms deal={} product={} notional={}",
                terms.dealId(), terms.productCode(), terms.notional());
        return new LedgerAck("ledger-" + UUID.randomUUID(), terms.dealId(), System.currentTimeMillis());
    }

    @Override
    public LedgerAck bookTrade(String tradeId, String dealId, double notional) {
        log.info("[CTL mock] bookTrade trade={} deal={} notional={}", tradeId, dealId, notional);
        return new LedgerAck("ledger-" + UUID.randomUUID(), dealId, System.currentTimeMillis());
    }

    @Override
    public LedgerAck settleTrade(String tradeId) {
        log.info("[CTL mock] settleTrade trade={}", tradeId);
        return new LedgerAck("ledger-" + UUID.randomUUID(), tradeId, System.currentTimeMillis());
    }
}
