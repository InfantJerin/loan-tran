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

**Flow A (product setup)** is wired end-to-end:

```
ProductSetupStarter
  └─> DealTermSetupWorkflow (dts-tq)
        ├─> LedgerActivities.writeDealTerms  (ctl-tq, mocked)
        └─> ServicingSetupWorkflow            (servicing-tq, child workflow, abandoned on parent close)
```

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

- **Worker terminal** — you'll see log lines from `[DTS]`, `[CTL mock]`, and `[Servicing]` in that order. That's two workflows and one activity running across three task queues.
- **Temporal Web UI** (usually `http://localhost:8233` for the dev server, or `http://localhost:8080` for docker-compose setups) — open the workflow named `dts-setup-<dealId>` and look at:
  - The event history (activity scheduled on `ctl-tq`, child workflow started on `servicing-tq`).
  - The child workflow `servicing-setup-<dealId>` as a separate execution.

## Things to try once it's running

These map to the open questions in [design.md](design.md):

1. **Kill the worker mid-activity.** Restart it. The workflow should resume — that's the durability story.
2. **Make the mocked CTL throw** on the first call, succeed on the second. Watch Temporal retry without any code in the caller.
3. **Replace the child-workflow handoff with a signal.** This is closer to "DTS emits an event, Servicing picks it up" and decouples the two more cleanly.
4. **Move CTL to a second JVM process.** Same code, just split the registrations across two `WorkerApp` mains. This is what production scaling looks like.
