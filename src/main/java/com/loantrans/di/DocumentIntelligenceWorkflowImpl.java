package com.loantrans.di;

import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class DocumentIntelligenceWorkflowImpl implements DocumentIntelligenceWorkflow {
    private static final Logger log = Workflow.getLogger(DocumentIntelligenceWorkflowImpl.class);

    @Override
    public void extract(String documentRef) {
        log.info("[DI] extract requested for {} — stub", documentRef);
    }
}
