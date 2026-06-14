package com.loantrans.servicing;

import com.loantrans.model.DealTerms;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class ServicingSetupWorkflowImpl implements ServicingSetupWorkflow {
    private static final Logger log = Workflow.getLogger(ServicingSetupWorkflowImpl.class);

    @Override
    public void onTermsCommitted(DealTerms terms) {
        log.info("[Servicing] terms committed for deal {} — placeholder for post-setup tasks", terms.dealId());
    }
}
