package com.loantrans.model;

public record ProductSetupEvent(String dealId, String productCode, String borrower, double notional) {}
