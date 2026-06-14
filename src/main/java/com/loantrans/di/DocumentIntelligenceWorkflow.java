package com.loantrans.di;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentIntelligenceWorkflow {
    @WorkflowMethod
    void extract(String documentRef);
}
