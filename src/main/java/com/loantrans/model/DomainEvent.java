package com.loantrans.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DealTermsCommitted.class, name = "DealTermsCommitted")
})
public sealed interface DomainEvent permits DealTermsCommitted {}
