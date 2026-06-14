# Loan Transformations — Temporal Orchestration Design

Learning-purpose design for orchestrating loan-platform controllers as Temporal workflows. Runs against a local Temporal server (not production).

## Goal

Model the loan platform as a set of controllers, each with its own Temporal task queue, and orchestrate cross-controller flows as durable workflows. Two flows are in scope for the first pass:

1. **Product setup** — set up a deal's terms, then hand off to servicing.
2. **Book trade** — book a trade through TLM, interact with ClearPar, settle on the ledger, then hand off to servicing.

## Components

| Component | Kind | Task queue | Notes |
|---|---|---|---|
| **EGS** — External Gateway Service | Ingress / egress + event router + vendor adapters | `egs-tq` | Receives external events; exposes `publish(DomainEvent)` (internal routing → starts downstream workflows via `WorkflowClient.start`) and `publishToClearPar(...)` (outbound ClearPar EMS publish). Also runs a `ClearParInboundConsumer` thread that reads ClearPar's inbound EMS topic and signals running TLM workflows. ClearPar lives modularly under `com.loantrans.egs.clearpar`. |
| **DTS** — Deal Term Service controller | Workflow worker | `dts-tq` | Owns deal-term setup. Writes terms to the ledger via CTL. |
| **DI** — Document Intelligence controller | Workflow worker | `di-tq` | Placement in the flows is TBD — see open questions. |
| **Servicing** controller | Workflow worker | `servicing-tq` | Picks up post-setup and post-settlement work. |
| **TLM** — Trade Lifecycle Management controller | Workflow worker | `tlm-tq` | Owns the book → allocate → settle lifecycle. Talks to ClearPar via EGS. |
| **CTL** — Spanner Ledger controller | Workflow worker (**mocked**) | `ctl-tq` | The single source of truth for ledger writes (deal terms, trade booking, trade settled). Spanner integration is mocked for now. |
| **ClearPar** | External system | — | Loan trade settlement platform. Reached through EGS. |
| **Temporal server** | Orchestration runtime | — | Local instance. Hosts workflows, signals, and per-controller task queues. |

## High-level architecture

```mermaid
flowchart LR
    ClearPar[(ClearPar)]

    subgraph EGSbox["EGS"]
        EGSin[ingress/egress]
        EGSpub[publish activity<br/>routes events → workflows]
    end

    subgraph Temporal["Temporal (local) — workflows + task queues"]
        DTS[[DTS worker<br/>dts-tq]]
        TLM[[TLM worker<br/>tlm-tq]]
        Servicing[[Servicing worker<br/>servicing-tq]]
        CTL[[CTL worker — mocked<br/>ctl-tq]]
        DI[[DI worker<br/>di-tq]]
    end

    Spanner[(Spanner<br/>mocked)]

    EGSin -- product setup --> DTS
    EGSin -- book trade --> TLM
    TLM <-- allocations --> EGSin
    EGSin <-- messages --> ClearPar

    DTS -- activity --> CTL
    TLM -- activity --> CTL
    CTL -. mocked .-> Spanner

    DTS -- publish event --> EGSpub
    TLM -- publish event --> EGSpub
    EGSpub -- WorkflowClient.start --> Servicing
```

## Flow A — Product setup (choreography across controllers)

Trigger: a `ProductSetup` event arrives at EGS from upstream.

**Within DTS:** orchestration — one `DealTermSetupWorkflow` on `dts-tq` calls `LedgerActivities.writeDealTerms` on `ctl-tq`, then publishes a `DealTermsCommitted` event through `EgsActivities.publish` on `egs-tq`, then ends.

**Between DTS and Servicing:** choreography — DTS has no source-level knowledge of Servicing. The `publish` activity owns the routing table; it pattern-matches on the event type and calls `WorkflowClient.start` on the appropriate downstream workflow. Adding a new consumer is a new route in `EgsActivitiesImpl`, not a DTS change.

```mermaid
sequenceDiagram
    autonumber
    participant Upstream
    participant EGSin as EGS ingress
    participant DTS as DTS workflow (dts-tq)
    participant CTL as CTL activity (ctl-tq, mocked)
    participant EGSpub as EGS publish (egs-tq activity)
    participant Servicing as Servicing workflow (servicing-tq)

    Upstream->>EGSin: ProductSetup event
    EGSin->>DTS: StartWorkflow(DealTermSetup, payload)
    DTS->>DTS: validate + transform terms
    DTS->>CTL: writeDealTerms(activity)
    CTL-->>DTS: ledger ack (mocked)
    DTS->>EGSpub: publish DealTermsCommitted
    EGSpub->>Servicing: WorkflowClient.start (routes by event type)
    EGSpub-->>DTS: ack
    DTS-->>EGSin: workflow completes
    Servicing->>Servicing: post-setup tasks
```

## Flow B — Book trade (orchestration inside TLM, choreography out)

Trigger: a `BookTrade` event arrives at EGS (today, fired directly by `BookTradeStarter`).

**Within TLM:** orchestration — one `TradeLifecycleWorkflow` on `tlm-tq` owns the whole booking → upload → allocations → settlement-coordination → settled lifecycle. It calls `CTL` at three points (book, write-allocations, settle), and uses `EgsActivities.publishToClearPar` for every outbound message. All inbound responses arrive as **signals**, with `Workflow.await` checkpoints between them.

**EGS as bidirectional ClearPar bridge (mono-EGS shape):**
- *Outbound* — `publishToClearPar` is an EGS activity on `egs-tq`; the impl writes to the mocked EMS outbound topic.
- *Inbound* — a `ClearParInboundConsumer` thread inside EGS reads the EMS inbound topic, looks up the running workflow by id (`tlm-trade-<tradeId>`), and signals it. No HTTP webhook — all message-based per ClearPar's actual transport.
- ClearPar lives in `com.loantrans.egs.clearpar` — modular under EGS, not a separate component. Split into its own task queue when slow ClearPar work starts blocking unrelated EGS work; split into its own service only when org-shape forces it.

**Between TLM and Servicing:** choreography — same pattern as DTS→Servicing. TLM publishes a `TradeSettled` domain event; EGS routes it to `ServicingPostSettlementWorkflow`. TLM does not import Servicing.

```mermaid
sequenceDiagram
    autonumber
    participant Desk as Trading desk
    participant EGSin as EGS ingress
    participant TLM as TradeLifecycleWorkflow (tlm-tq)
    participant CTL as CTL activity (ctl-tq, mocked)
    participant EGSpub as EGS publish (egs-tq)
    participant EMS as ClearPar EMS (mocked)
    participant CPmock as ClearPar (mocked simulator)
    participant Inbox as EGS inbound consumer
    participant Servicing as Servicing workflow (servicing-tq)

    Desk->>EGSin: BookTrade event
    EGSin->>TLM: StartWorkflow id=tlm-trade-T1

    TLM->>CTL: bookTrade
    CTL-->>TLM: ack

    TLM->>EGSpub: publishToClearPar(UploadTrade)
    EGSpub->>EMS: outbound queue
    EMS->>CPmock: deliver UploadTrade
    TLM->>TLM: Workflow.await(confirmation)

    CPmock->>EMS: inbound queue: TradeConfirmation
    EMS->>Inbox: deliver
    Inbox->>TLM: signal onTradeConfirmation
    TLM->>TLM: Workflow.await(allocations)

    CPmock->>EMS: inbound queue: Allocations
    EMS->>Inbox: deliver
    Inbox->>TLM: signal onAllocations

    TLM->>CTL: writeAllocations
    CTL-->>TLM: ack

    TLM->>EGSpub: publishToClearPar(SettlementCoordination)
    EGSpub->>EMS: outbound queue
    EMS->>CPmock: deliver SettlementCoordination
    TLM->>TLM: Workflow.await(settled)

    CPmock->>EMS: inbound queue: TradeSettledNotice
    EMS->>Inbox: deliver
    Inbox->>TLM: signal onTradeSettled

    TLM->>CTL: settleTrade
    CTL-->>TLM: ack

    TLM->>EGSpub: publish TradeSettled (domain event)
    EGSpub->>Servicing: WorkflowClient.start (post-settlement)
```

## Why each controller gets its own task queue

- **Independent scaling.** Each controller's worker pool can scale based on its own load (ledger writes vs. external round-trips are very different shapes).
- **Failure isolation.** A stuck activity in CTL doesn't starve DTS or TLM workers.
- **Clear ownership.** Activity routing is explicit — a workflow on `tlm-tq` calling a CTL activity has to target `ctl-tq`.
- **Mocking surface.** CTL being on its own queue makes it trivial to swap the mocked worker for a real Spanner-backed one later without touching callers.

## What "mocked CTL" means here

The CTL worker registers the same activity signatures it will have in production (`WriteDealTerms`, `BookTrade`, `SettleTrade`), but the implementations return canned acks and log the payload instead of writing to Spanner. Callers (DTS, TLM) don't know the difference.

## Decisions so far

- **Paradigm by boundary.** Orchestration **inside** a bounded context (each controller is one Temporal workflow). Choreography **between** bounded contexts (controllers publish/consume domain events through EGS). See the conversation around the DTS→Servicing refactor for the reasoning.
- **EGS is medium-weight.** Owns the inter-domain event → workflow-start routing table; does not run the step-by-step business saga inside any controller. That resolves the earlier "thin vs. fat EGS" question.
- **Ledger writes are activities, not events.** CTL is part of each controller's saga (atomic with the state transition, idempotent, replay-safe), so it stays an activity call. Wrapping CTL writes as events would lose compensation guarantees.
- **Domain events are published via a Temporal activity that directly starts the downstream workflow — no separate event bus at this stage.** Temporal task queues are *worker routing* (one task → one worker), not pub-sub, so the platform itself doesn't give us a bus. An in-memory bus between a publish activity and a separate dispatcher thread is *less* durable than a direct call: Temporal retries failed activities until they succeed, but an in-memory queue is lost on JVM restart. The decoupling we wanted (publisher doesn't import consumer) comes from the *dispatcher pattern* — one component owning the routing table — not from the queue. So the seam is `EgsActivities.publish` and the routing table inside its impl; the impl directly calls `WorkflowClient.start(...)` on the downstream workflow. Reach for a real bus (Kafka, NATS, transactional outbox) only when one of these starts to matter: fan-out to consumers we don't own, replayable event history, cross-cluster federation, or an audit log queryable independently of Temporal.
- **ClearPar lives inside EGS (mono-EGS shape), not as a separate component.** EGS is defined as the gateway for all external I/O, so vendor adapters belong there. Code is modular by vendor (`com.loantrans.egs.clearpar`); runtime is one process, one `egs-tq`. Move the vendor to its own task queue when slow vendor calls start blocking unrelated EGS work; move it to its own service only when on-call, release cadence, or compliance scope diverges enough that the EGS team can't ship without coordinating with a vendor-specific team.
- **Inbound from ClearPar uses signals; the workflow id is the correlation key.** ClearPar communication is fully asynchronous and message-based (no HTTP). Outbound activities publish to an EMS topic and complete. Inbound responses arrive via a separate consumer thread that addresses the specific running workflow by id (`tlm-trade-<tradeId>`) and signals it. `Workflow.await` makes the wait durable: the worker can die for hours and the workflow resumes at the same `await` once the signal arrives. Signal handlers must be idempotent because EMS delivery is at-least-once — the standard discipline for any message-driven system. `signalWithStart` covers the race where a callback arrives before the workflow has been started.

## Open questions

1. **Where does DI (Document Intelligence) fit?** It's a controller but doesn't appear in either flow above. Candidates: a pre-step before DTS (extract terms from a term-sheet PDF), or a parallel branch driven by a `DocumentReceived` event.
2. **EGS ↔ workflow interaction for ClearPar round-trip (Flow B).** Temporal **signal** back into the running TLM workflow, or polling via an activity? Signals are more idiomatic for "wait for an external event."
3. **Idempotency keys.** Each cross-controller call (especially CTL writes) should carry a workflow-derived idempotency key so retries don't double-write. This becomes more pressing now that the publish activity is itself retried by Temporal — `WorkflowClient.start` with a deterministic workflow id makes the publish idempotent for free, but the underlying business call needs its own key.
4. **Event schema versioning.** Now that domain events are the contract between controllers, breaking changes to event shape break consumers silently. Versioning policy needed before more than two events exist.
5. **Replay safety in mocked CTL.** Even mocked, the CTL activities should be deterministic and safe to retry — otherwise the mock will hide bugs that the real Spanner client would also have.

## Next step (suggested)

Stand up the skeleton: a Temporal worker per controller (all in one process is fine for learning), each registered on its own task queue, with no-op activities. Then implement Flow A end-to-end with mocked CTL before touching TLM/ClearPar. Flow B is meaningfully harder because of the ClearPar round-trip — getting Flow A working first will force the EGS-scope decision above.
