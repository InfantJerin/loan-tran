# Loan Transformations — Temporal Orchestration Design

Learning-purpose design for orchestrating loan-platform controllers as Temporal workflows. Runs against a local Temporal server (not production).

## Goal

Model the loan platform as a set of controllers, each with its own Temporal task queue, and orchestrate cross-controller flows as durable workflows. Two flows are in scope for the first pass:

1. **Product setup** — set up a deal's terms, then hand off to servicing.
2. **Book trade** — book a trade through TLM, interact with ClearPar, settle on the ledger, then hand off to servicing.

## Components

| Component | Kind | Task queue | Notes |
|---|---|---|---|
| **EGS** — External Gateway Service | Ingress / egress | `egs-tq` | Receives external events (product setup, book trade) and forwards to the right controller. Scope still TBD — see open questions. |
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
    EGS[EGS<br/>External Gateway Service]
    ClearPar[(ClearPar)]

    subgraph Temporal["Temporal (local) — workflows + task queues"]
        DTS[[DTS worker<br/>dts-tq]]
        TLM[[TLM worker<br/>tlm-tq]]
        Servicing[[Servicing worker<br/>servicing-tq]]
        CTL[[CTL worker — mocked<br/>ctl-tq]]
        DI[[DI worker<br/>di-tq]]
    end

    Spanner[(Spanner<br/>mocked)]

    EGS -- product setup --> DTS
    EGS -- book trade --> TLM
    TLM <-- allocations --> EGS
    EGS <-- messages --> ClearPar

    DTS --> CTL
    TLM --> CTL
    CTL -. mocked .-> Spanner

    CTL -- terms written --> Servicing
    CTL -- trade settled --> Servicing
```

## Flow A — Product setup

Trigger: a `ProductSetup` event arrives at EGS from upstream.

Durable execution: a single `DealTermSetupWorkflow` on `dts-tq` that calls a `WriteDealTerms` activity on `ctl-tq`. When it completes, it emits a signal/event that starts `ServicingWorkflow` on `servicing-tq`.

```mermaid
sequenceDiagram
    autonumber
    participant Upstream
    participant EGS
    participant DTS as DTS workflow (dts-tq)
    participant CTL as CTL workflow (ctl-tq, mocked)
    participant Servicing as Servicing workflow (servicing-tq)

    Upstream->>EGS: ProductSetup event
    EGS->>DTS: StartWorkflow(DealTermSetup, payload)
    DTS->>DTS: validate + transform terms
    DTS->>CTL: WriteDealTerms(activity)
    CTL-->>DTS: ledger ack (mocked)
    DTS-->>EGS: DealTermsCommitted event
    EGS->>Servicing: StartWorkflow(ServicingSetup, deal)
    Servicing->>Servicing: post-setup tasks
```

## Flow B — Book trade

Trigger: a `BookTrade` event originates from the trading-desk side, arrives at EGS, and is routed to the TLM event log.

Durable execution: a `TradeLifecycleWorkflow` on `tlm-tq` that owns the whole booking → allocation → settlement lifecycle. It writes to CTL at two points (booking, settled) and round-trips through EGS to ClearPar for allocations. On settlement, it hands off to Servicing.

```mermaid
sequenceDiagram
    autonumber
    participant Desk as Trading desk
    participant EGS
    participant TLM as TLM workflow (tlm-tq)
    participant CTL as CTL workflow (ctl-tq, mocked)
    participant ClearPar
    participant Servicing as Servicing workflow (servicing-tq)

    Desk->>EGS: BookTrade event
    EGS->>TLM: StartWorkflow(TradeLifecycle, trade)

    TLM->>CTL: BookTrade(activity) — record booking on ledger
    CTL-->>TLM: ledger ack (mocked)

    TLM->>EGS: PublishToClearPar(trade)
    EGS->>ClearPar: trade message
    ClearPar-->>EGS: allocations
    EGS-->>TLM: signal: AllocationsReceived

    TLM->>TLM: apply allocations
    TLM->>CTL: SettleTrade(activity) — write "trade settled"
    CTL-->>TLM: ledger ack (mocked)

    TLM-->>EGS: TradeSettled event
    EGS->>Servicing: StartWorkflow(ServicingPostSettlement, trade)
```

## Why each controller gets its own task queue

- **Independent scaling.** Each controller's worker pool can scale based on its own load (ledger writes vs. external round-trips are very different shapes).
- **Failure isolation.** A stuck activity in CTL doesn't starve DTS or TLM workers.
- **Clear ownership.** Activity routing is explicit — a workflow on `tlm-tq` calling a CTL activity has to target `ctl-tq`.
- **Mocking surface.** CTL being on its own queue makes it trivial to swap the mocked worker for a real Spanner-backed one later without touching callers.

## What "mocked CTL" means here

The CTL worker registers the same activity signatures it will have in production (`WriteDealTerms`, `BookTrade`, `SettleTrade`), but the implementations return canned acks and log the payload instead of writing to Spanner. Callers (DTS, TLM) don't know the difference.

## Open questions

1. **EGS scope vs. Temporal as control plane.** EGS is described as ingress/egress, but Temporal already orchestrates the cross-controller flow. Two plausible shapes:
   - *Thin EGS:* EGS is just an event-bus adapter (receives external events, starts workflows, publishes outbound). All orchestration lives in Temporal workflows.
   - *Fat EGS:* EGS is the control plane that decides which workflows to start and in what order. Temporal is just per-controller durable execution.
   Decision needed before writing the EGS skeleton.
2. **Where does DI (Document Intelligence) fit?** It's a controller but doesn't appear in either flow above. Candidates: a pre-step before DTS (extract terms from a term-sheet PDF), or a parallel branch off the product-setup workflow.
3. **EGS ↔ workflow interaction primitive.** For the ClearPar round-trip, is the right primitive a Temporal **signal** back into the running TLM workflow, or should TLM poll via an activity? Signals are more idiomatic for "wait for an external event"; activities are simpler if EGS can synchronously call back.
4. **Idempotency keys.** Each cross-controller call (especially CTL writes) should carry a workflow-derived idempotency key so retries don't double-write. Worth designing once before any controller is built.
5. **Replay safety in mocked CTL.** Even mocked, the CTL activities should be deterministic and safe to retry — otherwise the mock will hide bugs that the real Spanner client would also have.

## Next step (suggested)

Stand up the skeleton: a Temporal worker per controller (all in one process is fine for learning), each registered on its own task queue, with no-op activities. Then implement Flow A end-to-end with mocked CTL before touching TLM/ClearPar. Flow B is meaningfully harder because of the ClearPar round-trip — getting Flow A working first will force the EGS-scope decision above.
