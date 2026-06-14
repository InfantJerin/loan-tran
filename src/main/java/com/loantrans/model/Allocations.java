package com.loantrans.model;

import java.util.List;

public record Allocations(String tradeId, List<AllocationLine> lines) {}
