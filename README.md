# loan-trans

Learning sandbox for orchestrating loan-platform controllers as Temporal workflows. See [design.md](design.md) for the overall idea.

## What's in here

Five controllers, each on its own Temporal task queue, all hosted in one process for now:

| Controller | Task queue | Role |
|---|---|---|
| DTS | `dts-tq` | Deal term setup |
| Servicing | `servicing-tq` | Post-setup / post-settlement work |
| TLM | `tlm-tq` | Trade lifecycle (stubbed) |
| DI | `di-tq` | Document intelligence (stubbed) |
| CTL | `ctl-tq` | Ledger writes (**mocked** — logs only, no Spanner) |
| EGS | `egs-tq` | `publish` activity — owns the event → downstream-workflow routing table and starts the consumer via `WorkflowClient.start` |

**Flow A (product setup)** is wired end-to-end as choreography between DTS and Servicing:

```
ProductSetupStarter
  └─> DealTermSetupWorkflow (dts-tq)
        ├─> LedgerActivities.writeDealTerms       (ctl-tq, mocked)
        ├─> EgsActivities.publish(DealTermsCommitted)  (egs-tq)
        │     └─> routes by event type → WorkflowClient.start
        │           └─> ServicingSetupWorkflow    (servicing-tq)
        └─> ends
```

DTS has **no source-level dependency** on Servicing — no import, no workflow-class reference. The contract between them is the `DealTermsCommitted` event schema. Adding a new consumer (audit, risk, reporting) is a new branch inside `EgsActivitiesImpl.publish`, not a DTS change.

There is intentionally **no event bus** at this stage — the `publish` activity directly starts the downstream workflow. Temporal retries failed activities, so if the start call fails (network blip, downstream worker offline), the publish is retried until it succeeds. A real bus (Kafka / outbox / NATS) becomes worth introducing when you need fan-out to consumers in other processes, replayable history, or an audit log queryable independently of Temporal. See the corresponding decision in [design.md](design.md).

**Flow B (book trade)** is wired end-to-end with a mocked ClearPar EMS:

```
BookTradeStarter
  └─> TradeLifecycleWorkflow (tlm-tq)
        ├─> LedgerActivities.bookTrade                    (ctl-tq, mocked)
        ├─> EgsActivities.publishToClearPar(UploadTrade)  (egs-tq)
        │     └─> InMemoryClearParEms.outbound
        │           └─> MockClearParSimulator
        │                 ├─> emits TradeConfirmation     (after ~500ms)
        │                 └─> emits Allocations           (after ~1s)
        ├─> Workflow.await(confirmation)  ← signal arrives via ClearParInboundConsumer
        ├─> Workflow.await(allocations)   ← signal arrives via ClearParInboundConsumer
        ├─> LedgerActivities.writeAllocations             (ctl-tq, mocked)
        ├─> EgsActivities.publishToClearPar(SettlementCoordination)
        │     └─> MockClearParSimulator → emits TradeSettledNotice
        ├─> Workflow.await(settled)       ← signal arrives via ClearParInboundConsumer
        ├─> LedgerActivities.settleTrade                  (ctl-tq, mocked)
        └─> EgsActivities.publish(TradeSettled)           (egs-tq)
              └─> routes by event type → WorkflowClient.start
                    └─> ServicingPostSettlementWorkflow   (servicing-tq)
```

TLM has **no source-level dependency** on ClearPar message-transport details or on Servicing. It calls `EgsActivities.publishToClearPar(...)` and waits on signals it declared — the consumer thread inside EGS does the lookup and signaling. The TradeSettled handoff at the end uses the same choreography path as Flow A.

The ClearPar EMS is mocked the same way CTL is — an in-memory queue with a `MockClearParSimulator` standing in for the real vendor. Code lives in `com.loantrans.egs.clearpar`.

## Prerequisites

- Java 17+
- Maven 3.8+
- A Temporal server running locally on the default port `localhost:7233` (you said you have this).

## Run it

In one terminal, start the workers:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.WorkerApp
```

In another terminal, fire a product-setup event (Flow A):

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.ProductSetupStarter
```

Or fire a book-trade event (Flow B — drives the full ClearPar saga):

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.BookTradeStarter
```

Both starters accept an optional id as the first argument:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.ProductSetupStarter -Dexec.args="deal-123"
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.BookTradeStarter      -Dexec.args="trade-123"
```

## What to look at

- **Worker terminal** — log order is `[DTS]` → `[CTL mock]` → `[EGS] publish DealTermsCommitted` → `[EGS] DealTermsCommitted -> started servicing-setup-…` → `[Servicing]`. Two workflows ran, but neither knew about the other directly — the `publish` activity bridged them.
- **Temporal Web UI** (usually `http://localhost:8233` for the dev server, or `http://localhost:8080` for docker-compose setups):
  - `dts-setup-<dealId>` — its event history shows activities on `ctl-tq` and `egs-tq` only. No `ChildWorkflowExecutionStarted` event. That's the source-level decoupling, made visible.
  - `servicing-setup-<dealId>` — a separate top-level execution started from inside the `publish` activity, not parented by DTS.

## Things to try once it's running

1. **Add a second consumer of `DealTermsCommitted` without touching DTS.** Add an `AuditWorkflow`, register it on a new task queue, and add one `else if` branch in `EgsActivitiesImpl.publish`. Re-run the starter — both Servicing and Audit fire. DTS code never changed. That's the payoff.
2. **Kill the worker mid-activity.** Restart it. The DTS workflow resumes from its event history — that's the durability story.
3. **Make the mocked CTL throw** on the first call, succeed on the second. Watch Temporal retry without any code in the caller.
4. **Take down the Servicing worker, then run the starter.** The `publish` activity tries to start `ServicingSetupWorkflow`, which succeeds (Temporal accepts it), but no worker is polling `servicing-tq`, so it sits pending. Bring the Servicing worker back — it picks up the pending workflow. That's why a separate bus isn't needed for this kind of outage: Temporal already buffers.
5. **Move CTL to a second JVM process.** Split the registrations across two `WorkerApp` mains. Same code path; this is what production scaling looks like.
