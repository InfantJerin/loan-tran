package com.loantrans;

import com.loantrans.ctl.MockedLedgerActivitiesImpl;
import com.loantrans.di.DocumentIntelligenceWorkflowImpl;
import com.loantrans.dts.DealTermSetupWorkflowImpl;
import com.loantrans.egs.EgsActivitiesImpl;
import com.loantrans.egs.clearpar.ClearParEms;
import com.loantrans.egs.clearpar.ClearParInboundConsumer;
import com.loantrans.egs.clearpar.InMemoryClearParEms;
import com.loantrans.egs.clearpar.MockClearParSimulator;
import com.loantrans.servicing.ServicingPostSettlementWorkflowImpl;
import com.loantrans.servicing.ServicingSetupWorkflowImpl;
import com.loantrans.tlm.TradeLifecycleWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerApp {
    private static final Logger log = LoggerFactory.getLogger(WorkerApp.class);

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        ClearParEms clearParEms = new InMemoryClearParEms();

        Worker dts = factory.newWorker(TaskQueues.DTS_TQ);
        dts.registerWorkflowImplementationTypes(DealTermSetupWorkflowImpl.class);

        Worker servicing = factory.newWorker(TaskQueues.SERVICING_TQ);
        servicing.registerWorkflowImplementationTypes(
                ServicingSetupWorkflowImpl.class,
                ServicingPostSettlementWorkflowImpl.class);

        Worker tlm = factory.newWorker(TaskQueues.TLM_TQ);
        tlm.registerWorkflowImplementationTypes(TradeLifecycleWorkflowImpl.class);

        Worker di = factory.newWorker(TaskQueues.DI_TQ);
        di.registerWorkflowImplementationTypes(DocumentIntelligenceWorkflowImpl.class);

        Worker ctl = factory.newWorker(TaskQueues.CTL_TQ);
        ctl.registerActivitiesImplementations(new MockedLedgerActivitiesImpl());

        Worker egs = factory.newWorker(TaskQueues.EGS_TQ);
        egs.registerActivitiesImplementations(new EgsActivitiesImpl(client, clearParEms));

        factory.start();

        new ClearParInboundConsumer(client, clearParEms).start();
        new MockClearParSimulator(clearParEms).start();

        log.info("Workers started on queues: {}, {}, {}, {}, {}, {}",
                TaskQueues.DTS_TQ, TaskQueues.SERVICING_TQ, TaskQueues.TLM_TQ,
                TaskQueues.DI_TQ, TaskQueues.CTL_TQ, TaskQueues.EGS_TQ);
        log.info("ClearPar inbound consumer and mock simulator running.");
        log.info("Ctrl+C to stop.");
    }
}
