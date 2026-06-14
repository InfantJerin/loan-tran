package com.loantrans.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DealTermsCommitted.class, name = "DealTermsCommitted"),
        @JsonSubTypes.Type(value = TradeSettled.class, name = "TradeSettled")
})
public sealed interface DomainEvent permits DealTermsCommitted, TradeSettled {}
