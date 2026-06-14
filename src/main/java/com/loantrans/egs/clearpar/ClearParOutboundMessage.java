package com.loantrans.egs.clearpar;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UploadTradeMessage.class, name = "UploadTrade"),
        @JsonSubTypes.Type(value = SettlementCoordinationMessage.class, name = "SettlementCoordination")
})
public sealed interface ClearParOutboundMessage
        permits UploadTradeMessage, SettlementCoordinationMessage {
    String tradeId();
}
