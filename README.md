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
| EGS | `egs-tq` | `publish` activity + in-memory event bus + dispatcher that routes events to downstream workflows |

**Flow A (product setup)** is wired end-to-end as choreography between DTS and Servicing:

```
ProductSetupStarter
  └─> DealTermSetupWorkflow (dts-tq)
        ├─> LedgerActivities.writeDealTerms  (ctl-tq, mocked)
        ├─> EgsActivities.publish(DealTermsCommitted)  (egs-tq)
        └─> ends

EgsDispatcher (background thread)
  └─> consumes DealTermsCommitted from the in-memory bus
        └─> starts ServicingSetupWorkflow (servicing-tq)
```

DTS has **no source-level dependency** on Servicing — no import, no workflow-class reference. The contract between them is the `DealTermsCommitted` event schema. Adding a new consumer (audit, risk, reporting) is a new route in `EgsDispatcher`, not a DTS change.

The event bus is a simple in-memory `BlockingQueue` for now — events are lost on restart. Production would use Kafka / outbox / equivalent; the `EventBus` interface stays the same.

Flow B (book trade / ClearPar / settle) is intentionally stubbed — the TLM workflow exists and registers on its queue, but the body is a log line. Wire it up once Flow A makes sense.

## Prerequisites

- Java 17+
- Maven 3.8+
- A Temporal server running locally on the default port `localhost:7233` (you said you have this).

## Run it

In one terminal, start the workers:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.WorkerApp
```

In another terminal, fire a product-setup event:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.ProductSetupStarter
```

Optionally pass a deal id:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.ProductSetupStarter -Dexec.args="deal-123"
```

## What to look at

- **Worker terminal** — log order is `[DTS]` → `[CTL mock]` → `[EGS] published DealTermsCommitted` → `[EGS] DealTermsCommitted -> started servicing-setup-…` → `[Servicing]`. Two workflows ran, but neither knew about the other directly — the dispatcher bridged them.
- **Temporal Web UI** (usually `http://localhost:8233` for the dev server, or `http://localhost:8080` for docker-compose setups):
  - `dts-setup-<dealId>` — its event history shows activities on `ctl-tq` and `egs-tq` only. No `ChildWorkflowExecutionStarted` event. That's the source-level decoupling, made visible.
  - `servicing-setup-<dealId>` — a separate top-level execution started by the dispatcher, not parented by DTS.

## Things to try once it's running

1. **Add a second consumer of `DealTermsCommitted` without touching DTS.** Add an `AuditWorkflow`, register it on a new task queue, and add one `else if` branch to `EgsDispatcher.route`. Re-run the starter — both Servicing and Audit fire. DTS code never changed. That's the payoff.
2. **Kill the worker mid-activity.** Restart it. The DTS workflow resumes from its event history — that's the durability story.
3. **Make the mocked CTL throw** on the first call, succeed on the second. Watch Temporal retry without any code in the caller.
4. **Break the bus.** Stop publishing in `EgsActivitiesImpl` for a moment — DTS completes, but Servicing never starts. That's the cost of choreography: failure modes move into the bus, not the caller. Production answer: a durable bus (Kafka / outbox).
5. **Move CTL to a second JVM process.** Split the registrations across two `WorkerApp` mains. Same code path; this is what production scaling looks like.
