package com.loantrans.dts;

import com.loantrans.model.ProductSetupEvent;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DealTermSetupWorkflow {
    @WorkflowMethod
    String setup(ProductSetupEvent event);
}
