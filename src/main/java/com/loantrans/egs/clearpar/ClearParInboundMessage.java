package com.loantrans.egs.clearpar;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.loantrans.model.Allocations;
import com.loantrans.model.TradeConfirmation;
import com.loantrans.model.TradeSettledNotice;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClearParInboundMessage.Confirm.class, name = "Confirm"),
        @JsonSubTypes.Type(value = ClearParInboundMessage.Alloc.class, name = "Alloc"),
        @JsonSubTypes.Type(value = ClearParInboundMessage.Settled.class, name = "Settled")
})
public sealed interface ClearParInboundMessage {
    String tradeId();

    record Confirm(TradeConfirmation payload) implements ClearParInboundMessage {
        @Override public String tradeId() { return payload.tradeId(); }
    }

    record Alloc(Allocations payload) implements ClearParInboundMessage {
        @Override public String tradeId() { return payload.tradeId(); }
    }

    record Settled(TradeSettledNotice payload) implements ClearParInboundMessage {
        @Override public String tradeId() { return payload.tradeId(); }
    }
}
