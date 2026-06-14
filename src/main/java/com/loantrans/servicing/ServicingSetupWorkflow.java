package com.loantrans.servicing;

import com.loantrans.model.DealTerms;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ServicingSetupWorkflow {
    @WorkflowMethod
    void onTermsCommitted(DealTerms terms);
}
