# Loan Transformations — Design Discussion

A conversational record of the design work that produced [design.md](design.md) and the current scaffold. Preserved as a transcript so the reasoning behind each decision is recoverable later.

---

## 1. Initial idea

**User:**

> I want to try out one temporal workflow for my loan transformations program. I have a temporal running locally, but it's not an actual production temporal. This is just for learning purposes.
>
> What I want to try out is having a deal term controller. Then I'll have a document intelligent controller. Next, I'll have a servicing controller. I also have a TLM, which is the trade lifecycle management controller. And there's a CTL, which is the Spanner controller. For now, let's mock the CTL and avoid any Spanner integrations.
>
> What I want to do is orchestrate the flows between these controllers. Each controller will have its own task queue.
>
> Flow A: A product setup event occurs. The gateway routes this event to the deal term service controller (DTS). The DTS receives the event payload into its queue, runs its controller logic, and writes it to the ledger via the CTL. Once completed, it emits an event that the servicing engine can pick up.
>
> Flow B: A book trade event originates from EGS, routed to the TLM event log. TLM interacts with external systems like ClearPar through the gateway. During these transactions, the booking is first booked into CTL, then it publishes a message back to ClearPar, gets the trade allocations, passes this to TLM. Finally there's a trade settlement, written to CTL as "trade settled", and then servicing starts.

**Assistant** clarified a few inconsistencies in the description before writing the doc:

- **CTL vs. CDL vs. CTA** — all the same ledger controller (CDL, CTA were typos).
- **ClearPar vs. ClearPath** — same external system, "ClearPar".
- **EGS** — defined as "External Gateway Service" for ingress/egress; scope (control plane vs. thin adapter) intentionally left open.
- **Format** — Markdown with Mermaid diagrams.

---

## 2. First-cut design.md

Initial design captured:

- **Components**: EGS (ingress/egress), DTS, DI, Servicing, TLM, CTL (mocked Spanner).
- **One task queue per controller** for independent scaling, failure isolation, clear ownership, and easy mocking.
- **Flow A**: product-setup event → DTS workflow → CTL activity → emits event → Servicing workflow.
- **Flow B**: book-trade event → TLM workflow → CTL (booking) → ClearPar round-trip via EGS → CTL (settled) → Servicing handoff.

Five open questions surfaced:
1. EGS scope vs. Temporal as control plane (thin vs. fat EGS).
2. Where DI fits.
3. Signal vs. activity for the ClearPar round-trip.
4. Idempotency keys for CTL writes.
5. Replay safety in mocked CTL.

---

## 3. Spotting tight coupling in the first scaffold

**User:**

> I don't have answers to all the problems at this moment. I just want to see how this is working. Then once I play around with that skeleton, I'll get more clarity.
>
> You know what? I see we're actually starting the servicing workflow *from* the deal term workflow. I think this is the part we wanted to avoid because this is creating tight coupling. It's not very loosely coupled.

```java
ChildWorkflowOptions servicingOptions = ChildWorkflowOptions.newBuilder()
    .setTaskQueue(TaskQueues.SERVICING_TQ)
    .setWorkflowId("servicing-setup-" + event.dealId())
    .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
    .build();
```

**Assistant** confirmed the instinct: `DealTermSetupWorkflowImpl` imports `ServicingSetupWorkflow`, knows its task queue, knows its workflow-id convention, knows its method signature. Adding a second consumer ("deal terms committed" → audit, analytics, notifications) means modifying DTS. That's orchestration where choreography would be cleaner — but it isn't a Temporal limitation; it was a pattern choice.

Three alternatives presented:

| Pattern | What it looks like | Coupling |
|---|---|---|
| **Orchestrating workflow (what we had)** | DTS calls Servicing directly. | DTS knows Servicing exists. |
| **Saga / parent orchestrator** | A `ProductSetupSaga` workflow calls DTS, then Servicing. | Coupling moves up to the saga — one explicit place owns the flow. |
| **Event-driven choreography** | DTS ends. A `DealTermsCommitted` event is published. A dispatcher reads it and starts `ServicingSetupWorkflow`. | DTS and Servicing have no code dependency on each other. Coupling is reified as an event schema. |

Mapped to the earlier open question: this is the "thin EGS vs. fat EGS" debate. Initial scaffold was "thin EGS, fat workflows." Choreography pushes toward "fat EGS, thin workflows."

---

## 4. Defining orchestration vs. choreography

**User:**

> Orchestration — you know what happens next.
> Choreography — you react to what happened before.
> Is this the difference?

**Assistant** — close, but missing the *who*:

- **Orchestration:** the **sender** knows what happens next and names it.
- **Choreography:** the **receiver** knows what it cares about and listens for it.

Both still involve cause-and-effect with a "before" and "next." The difference is *which side of the wire holds the knowledge of the flow*.

The deeper consequence: **the coupling never disappears, it just changes direction.** Orchestration couples callers to callees ("I know who comes next"). Choreography couples listeners to event schemas ("I know what shape of event I care about"). The event schema becomes the contract, and *it* is what you must not break casually.

---

## 5. Which paradigm fits loans

**User:**

> Which is the right paradigm for a loans domain, workflow orchestration or event choreography?

**Assistant** — neither alone, hybrid with a rule:

> **Orchestration inside a bounded context. Choreography between bounded contexts.**

DDD lands here too, and loans follows it especially cleanly because the bounded contexts (deal terms, trade lifecycle, servicing, ledger) are already how the business thinks.

**Why for loans specifically:**

- *Inside a controller (orchestration):* a trade lifecycle is a long-lived stateful saga — book → wait days for allocations → settle → compensate. You want one place that knows "where is trade X right now," can be replayed for audit, can timeout, can compensate. Splitting it into a dozen event handlers makes "where is this trade?" unanswerable — a real problem when an MD calls at 4pm asking about a $50M trade.
- *Between controllers (choreography):* the set of things that care about `TradeSettled` grows over time (servicing today, then risk, then compliance, then regulatory reporting, then agent-bank feed). If TLM holds the list, every new consumer is a TLM change.

Mapped to components:

| Boundary | Paradigm | Concretely |
|---|---|---|
| DTS internal: validate → transform → write terms | Orchestration | One workflow on `dts-tq` |
| TLM internal: book → allocate → settle → compensate | Orchestration | One workflow on `tlm-tq` |
| Servicing internal | Orchestration | Workflows on `servicing-tq` |
| DTS → Servicing | Choreography | `DealTermsCommitted` event, EGS routes to `ServicingSetupWorkflow` |
| TLM → Servicing/Risk/Reporting | Choreography | `TradeSettled` event, N subscribers |
| Controller → CTL | **Orchestration (activity call)** | CTL writes are *part of* each controller's saga, with retries and idempotency keys. Eventing them away would lose compensation guarantees. |

This also resolved the earlier open question: **EGS is medium-weight** — it owns inter-domain event → workflow-start routing, but does not run intra-domain saga steps.

Smell test: if a Temporal workflow imports another bounded context's workflow class, you've crossed the line — publish an event instead. If a saga is implemented as five disconnected event handlers, you've crossed it the other way — collapse them.

---

## 6. First choreography refactor — with an in-memory bus

**User:** yes, refactor.

**Assistant** scaffolded:
- `DomainEvent` sealed interface + `DealTermsCommitted` record.
- `EventBus` interface + `InMemoryEventBus` (`BlockingQueue`).
- `EgsActivities.publish(DomainEvent)` activity on `egs-tq`.
- `EgsDispatcher` background thread reading the bus and starting `ServicingSetupWorkflow` via `WorkflowClient.start`.
- DTS workflow modified: drop the child-workflow call, call `egs.publish(new DealTermsCommitted(terms))` instead. No more `ServicingSetupWorkflow` import.

---

## 7. Pushback: do we actually need a bus?

**User:**

> Why should we introduce the event bus / Kafka layer here? Can we use the Temporal task queue for that, publishing the messages?

**Assistant** — three threads to untangle:

**(a) Temporal task queues are NOT pub-sub.** A task queue is *worker routing*. One task → one worker. No subscriptions, no fan-out, no replay. You cannot use a task queue *as* an event bus.

**(b) But Temporal itself IS the publish mechanism — and for current scope you should use it.** Any activity can call `WorkflowClient.start(ServicingSetupWorkflow, ...)`. That *is* the publish. The in-memory bus and dispatcher we just added could be deleted; the activity directly starts the downstream workflow via a routing table. DTS still doesn't import Servicing — same decoupling, fewer moving parts.

Counterintuitively: **the in-memory `BlockingQueue` is *less* durable than a direct activity call.** Temporal retries failed activities automatically; an in-memory queue is lost on JVM restart.

**(c) When a real bus pays off:**
- Fan-out across services you don't own.
- Event replay / event sourcing.
- Cross-cluster federation.
- Audit log queryable independently of Temporal.

None of those applied here.

**Recommendation:** collapse the bus. Keep `EgsActivities` (it's still the seam), make `publish` directly do `WorkflowClient.start` via a routing table, delete `InMemoryEventBus` and `EgsDispatcher`.

---

## 8. Second refactor — Temporal as the bus, decision recorded first

**User:** yes — but add the design decision to the doc *before* the code change.

**Assistant** added this bullet to [design.md](design.md) Decisions:

> **Domain events are published via a Temporal activity that directly starts the downstream workflow — no separate event bus at this stage.** Temporal task queues are *worker routing* (one task → one worker), not pub-sub, so the platform itself doesn't give us a bus. An in-memory bus between a publish activity and a separate dispatcher thread is *less* durable than a direct call: Temporal retries failed activities until they succeed, but an in-memory queue is lost on JVM restart. The decoupling we wanted (publisher doesn't import consumer) comes from the *dispatcher pattern* — one component owning the routing table — not from the queue. So the seam is `EgsActivities.publish` and the routing table inside its impl; the impl directly calls `WorkflowClient.start(...)` on the downstream workflow. Reach for a real bus (Kafka, NATS, transactional outbox) only when one of these starts to matter: fan-out to consumers we don't own, replayable event history, cross-cluster federation, or an audit log queryable independently of Temporal.

Then deleted `EventBus.java`, `InMemoryEventBus.java`, `EgsDispatcher.java`. Rewrote `EgsActivitiesImpl` to take a `WorkflowClient` and route inline. Diagrams in `design.md` updated to drop the bus box.

---

## 9. Seeing Flow A in action

**User:** how do I see it?

**Assistant** verified Temporal was reachable on gRPC 7233 and dev-UI 8233, then gave a 4-step recipe:

1. Terminal A — `mvn -q compile exec:java -Dexec.mainClass=com.loantrans.WorkerApp`. Wait for "Workers started on queues: …".
2. Terminal B — `mvn -q compile exec:java -Dexec.mainClass=com.loantrans.starter.ProductSetupStarter`.
3. Read Terminal A: should see `[DTS]` → `[CTL mock]` → `[EGS] publish DealTermsCommitted` → `[EGS] DealTermsCommitted -> started servicing-setup-…` → `[Servicing]` lines.
4. Open the UI at http://localhost:8233. Two executions per run — `dts-setup-deal-…` (no `ChildWorkflowExecutionInitiated` in its history — that's the decoupling visible in the audit trail) and a separate top-level `servicing-setup-deal-…` started by the publish activity.

Suggested two experiments:
- Comment out the servicing-worker registration in `WorkerApp.java` to show the decoupling buffers outages (workflow goes pending; Temporal task queue *is* the buffer for "consumer not ready").
- Throw once in `EgsActivitiesImpl.publish` to see Temporal retry it.

---

## 10. Debugging logs + how to disable the Servicing worker

**User:**

> I'm not able to see the logs. How do I replicate the outage of the servicing? I tried to comment out the servicing implementations but that's not the right thing to do.

**Assistant** — two answers:

**(1) Disable just Servicing:** comment out the *worker registration* in [WorkerApp.java](src/main/java/com/loantrans/WorkerApp.java), not the impl class:

```java
Worker servicing = factory.newWorker(TaskQueues.SERVICING_TQ);
servicing.registerWorkflowImplementationTypes(ServicingSetupWorkflowImpl.class);
```

The impl is a compiled type other parts of the code reference at compile time; the worker is what *polls* `servicing-tq` at runtime. Commenting the registration leaves no poller — the publish activity still successfully calls `WorkflowClient.start(...)`, Temporal accepts and queues it, the UI shows `servicing-setup-…` in `Running` state with no progress until you uncomment and restart.

**(2) Logs missing:** three likely causes — (a) starter ran before workers were up (the "Workers started on queues: …" line is the gate), (b) Maven `-q` hiding output (drop it temporarily), (c) the starter's last line tells you whether the round-trip happened; if it ends with `Workflow completed. Ledger id: …`, the logs *did* appear in Terminal A and just need to scroll up; if Terminal B hangs, the worker isn't polling.

---

## 11. Flow B brainstorm — the trade lifecycle saga

**User:**

> TLM is a separate workflow. Trade booking → book to CTL → upload to ClearPar (publish a message? maybe via EGS, maybe different tool — figure it out) → ClearPar acknowledges → returns trade confirmation → ClearPar sends trade allocations → write to CTL → settlement notice from ClearPar → send settlement coordination to ClearPar → ClearPar acks with trade settled → commit to CTL. This is owned by TLM. Tightly coupled with the trade lifecycle, so the trade controller can own it. But how?

**Assistant** — TLM is one saga, owned by TLM. Don't choreograph the internal steps. The model extends cleanly with three new Temporal primitives we hadn't used:

**(a) Signals** — for "wait until ClearPar calls us back":

```java
@WorkflowInterface
public interface TradeLifecycleWorkflow {
    @WorkflowMethod void run(BookTradeEvent event);
    @SignalMethod   void onAllocations(Allocations allocations);
    @SignalMethod   void onTradeSettled(TradeSettled settled);
}
```

```java
ctl.bookTrade(...);
clearPar.uploadTrade(...);                       // synchronous activity
Workflow.await(() -> allocations != null);       // durable wait — could be days
ctl.writeAllocations(...);
clearPar.sendSettlementCoordination(...);
Workflow.await(() -> settled != null);
ctl.commitTradeSettled(...);
egs.publish(new TradeSettled(...));              // hand off downstream
```

`Workflow.await` is the magic — the worker can crash, restart, run for days; when ClearPar finally calls, the signal arrives and the workflow resumes at the exact line.

**(b) Outbound activities for ClearPar** — same shape as `CTL.writeDealTerms`.

**(c) EGS ingress that signals running workflows** — the inbound callback handler looks up the workflow by id and signals it. The **workflow id is the correlation key** (`tlm-trade-T1`). No mapping table.

Open design choice raised: ClearPar egress in EGS (one queue, simpler) vs. dedicated `clearpar-tq` adapter (better isolation per vendor quirks). I tentatively recommended the dedicated adapter on isolation grounds.

---

## 12. Two corrections — async messaging, and signal reliability

**User:**

> Corrections:
> 1. `clearPar.uploadTrade(...)` — it is an asynchronous message we publish to their EMS. All ClearPar communication is via message. No HTTP hook.
> 2. Is signal + await in Temporal a reliable pattern?

**Assistant:**

**Correction 1 simplifies the design:**
- *Outbound* — `publishUploadTrade(...)` is a fire-and-forget activity that publishes to ClearPar's outbound EMS topic. Activity completes once the broker confirms persistence.
- *Inbound* — a long-running message consumer subscribes to ClearPar's inbound topics. For each message, it looks up the running workflow by trade id and signals it. The "webhook handler" boxes from before become "EMS consumer thread reading from `clearpar.inbound.allocations` topic."

The workflow shape stays the same; just no synchronous confirmation in the upload activity.

**Correction 2 — signal + await is the intended primitive, very reliable:**

- **Signals are durably persisted** in event history before the server acks the sender. Once acked, delivery is guaranteed.
- **`Workflow.await` is durable** — not a thread sleep. State machine is checkpointed; on worker restart, replay stops at the same await.
- **A workflow can sit in `await` for days or weeks.** Temporal makes no distinction between 5ms and 5 weeks. The worker holds no resource during the wait.
- **Signals delivered while the worker is offline are buffered** in history; replay applies them when the worker comes back.
- **Determinism preserved** — signal handler is just another history event.

**Where reliability needs *your* care** — the bridge from EMS consumer to signal call:

| Risk | Mitigation |
|---|---|
| Consumer dies after reading message, before `signal` → message redelivered → signal sent twice | **Signal handlers must be idempotent.** `if (allocations != null) return;` is enough. |
| Consumer dies after `signal` returns but before `ems.ack` → redelivered → signal again | Same idempotency check covers it. |
| Signal call fails (network blip) | Consumer retries with backoff before acking the EMS message. |
| Callback arrives for a workflow that completed | Temporal drops signals for completed workflows. Log and move on. |
| Callback arrives before the workflow has been started | Use **`signalWithStart`** — atomically starts and signals. |

Pattern that captures all of this: **at-least-once delivery from EMS → idempotent signal handler → ack after success.** Same discipline as any message-driven system.

---

## 13. ClearPar inside EGS, not a separate component

**User:**

> Is ClearPar adapter going to be a separate controller or service? Why can't it sit in the EGS? EGS by definition is the gateway for any inbound/outbound events or messages. Also it's the control plane. Let's brainstorm.

**Assistant** — fair pushback. The previous recommendation conflated two questions:

1. **Conceptual ownership:** does ClearPar belong inside EGS's bounded context?
2. **Runtime topology:** does ClearPar get its own task queue / worker?

You can say "EGS owns it" on (1) AND "yes own queue" on (2) without contradiction.

Three shapes:

| Shape | Logical home | Runtime | Right when |
|---|---|---|---|
| **A. Mono-EGS** | EGS | One process, one `egs-tq` | Right now. Learning. |
| **B. EGS with vendor-internal queues** | EGS | One process; separate workers/queues per vendor (`clearpar-tq`). Code under `egs.clearpar`, `egs.bloomberg`. | A slow vendor call blocks unrelated EGS work. |
| **C. Separate service** | Its own bounded context | Own deploy, own team, own repo | Vendor work has different release cadence, on-call, or compliance scope. |

Revised recommendation: **A.** Modular EGS via packages (`com.loantrans.egs.clearpar`); split when symptoms appear, not before.

---

## 14. Symmetry — EGS as bidirectional ClearPar bridge

**User:**

> EGS is a control plane. It does the routing. When it reads messages from ClearPar, it's going to write to the TLM task queue. When TLM wants to publish to ClearPar, it writes to the EGS-ClearPar queue and EGS picks it up.

**Assistant** — right intuition, two terminology bridges:

**Inbound — "EGS reads from ClearPar, writes to the TLM task queue":**

Runtime: that's exactly what happens. Mechanism: **signal**, not generic enqueue.

- EGS consumer reads from ClearPar's inbound EMS topic.
- Builds the workflow id (`tlm-trade-<tradeId>`) — that's the addressing.
- Calls `client.signalWorkflow(workflowId, "onAllocations", payload)`.
- Temporal persists the signal in history **and schedules a workflow task on `tlm-tq`**.
- The TLM worker pulls that task, runs the signal handler, the `Workflow.await` condition flips, workflow proceeds.

So "EGS writes to the TLM task queue" *is true at the runtime level* — Temporal schedules work on `tlm-tq` as a result. The API is `signalWorkflow(...)` because the addressing is by **specific running workflow**, not by topic.

**Outbound — "TLM writes to EGS to publish to ClearPar":**

`egs.publishToClearPar(new UploadTrade(...))` — an EGS activity on `egs-tq`. The activity worker publishes to EMS. Same shape as `egs.publish(DealTermsCommitted)` from Flow A.

**Symmetry:**

| Direction | TLM-side primitive | EGS-side primitive | Task queue doing the work |
|---|---|---|---|
| Outbound | call activity `egs.publishToClearPar(...)` | activity impl publishes to EMS | `egs-tq` |
| Inbound | `Workflow.await` + `@SignalMethod` handler | consumer thread → `client.signalWorkflow(...)` | `tlm-tq` |

EGS is the router/translator at the boundary in both directions. TLM never references ClearPar. ClearPar never references TLM. Workflow id is the correlation key. One `egs-tq` for both directions for now; split when symptoms appear.

---

## 15. Flow B scaffold

Created:

- **Models** — `BookTradeEvent`, `AllocationLine`, `Allocations`, `TradeConfirmation`, `TradeSettledNotice`, `TradeSettled` (internal domain event).
- **ClearPar package** (`com.loantrans.egs.clearpar`) —
  - `ClearParOutboundMessage` sealed interface; `UploadTradeMessage`, `SettlementCoordinationMessage`.
  - `ClearParInboundMessage` sealed interface with nested `Confirm`, `Alloc`, `Settled` variants wrapping the inbound payloads.
  - `ClearParEms` interface + `InMemoryClearParEms` (two BlockingQueues — outbound, inbound).
  - `MockClearParSimulator` — consumes outbound, publishes plausible inbound responses with 500ms latency.
  - `ClearParInboundConsumer` — reads inbound, looks up workflow id `tlm-trade-<tradeId>`, signals.
- **TLM** — `TradeLifecycleWorkflow` interface with three `@SignalMethod`s; impl runs the full saga with three `Workflow.await` checkpoints and idempotency guards in signal handlers.
- **CTL** — added `writeAllocations`.
- **EGS** — added `publishToClearPar`; `publish` now routes both `DealTermsCommitted` and `TradeSettled`.
- **Servicing** — new `ServicingPostSettlementWorkflow` as the second domain-event consumer (proves the same choreography pattern works for Flow B).
- **WorkerApp** — wires `InMemoryClearParEms`, starts `ClearParInboundConsumer` and `MockClearParSimulator`, registers the new servicing workflow.
- **`BookTradeStarter`** main fires a `BookTradeEvent` into `tlm-tq`.

[design.md](design.md) Flow B section rewritten with the full sequence diagram; new decisions added (mono-EGS, signals + workflow-id correlation).

---

## 16. The Jackson polymorphic-type bug

After running `BookTradeStarter` the first time, activity retries kept failing with:

```
Cannot construct instance of `ClearParOutboundMessage` (no Creators, like default constructor, exist)
```

Cause: `@JsonTypeInfo` + `@JsonSubTypes` were on `DomainEvent` and `ClearParInboundMessage` but missing from `ClearParOutboundMessage`. Without the annotations, Jackson didn't add an `@type` discriminator on serialize, and on deserialize couldn't pick a concrete subclass for the interface.

Fix — add to [ClearParOutboundMessage.java](src/main/java/com/loantrans/egs/clearpar/ClearParOutboundMessage.java):

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UploadTradeMessage.class, name = "UploadTrade"),
    @JsonSubTypes.Type(value = SettlementCoordinationMessage.class, name = "SettlementCoordination")
})
public sealed interface ClearParOutboundMessage
        permits UploadTradeMessage, SettlementCoordinationMessage { String tradeId(); }
```

Important nuance — Temporal stores activity inputs as payloads when the activity is *scheduled*. The currently-stuck workflow's history already has the bad JSON; even with fixed code, its retries will keep failing. Have to terminate it in the UI and start a fresh run.

---

## 17. Ordering and failure handling

**User:**

> The happy path works, but it's not always going to be successful. ClearPar can reject. There might be other event notifications. And if you're creating a trade per workflow, how do you preserve the sequence of order in which trades should be processed? Then the failure cases — how does the workflow handle them? If I can figure that out, I'm super confident this works.

**Assistant** — two distinct questions.

### Ordering

**Default: trades are independent and run in parallel.** Each `BookTradeStarter` invocation creates its own workflow with its own id. Temporal happily runs as many as workers can poll.

**Where ordering matters:** same-deal operations (amendments after the original booking; sequential cash-flow application; prerequisite checks like "deal terms must be committed before trades").

Patterns:

| Pattern | Shape | When to use |
|---|---|---|
| **Singleton workflow per ordering key** | `DealLifecycleWorkflow` with id `deal-<dealId>` owns serialization. Booking events arrive as signals; processed one at a time. | Cleanest when "the deal" is the natural unit. |
| **Prerequisite check + retry** | Trade workflow queries deal state at start; throws if not ready → Temporal retries until prereq is met. | When prereqs are usually met and waiting is rare. |
| **Per-deal task queue** | All trades on a deal share a queue with concurrency=1. | Coarse; rarely the right tool — coordination should be domain-modeled. |

### Failure handling

| Failure | Handling today | Right tool |
|---|---|---|
| Transient infra (network, EMS hiccup, CTL down) | Activity retries automatically | `ActivityOptions.setRetryOptions(...)` |
| **ClearPar rejects the upload** | Workflow hangs forever waiting on confirmation — **bug** | Additional signal: `onRejection(Reason)`. Branch on either signal. |
| **ClearPar never responds** | Hangs forever — **bug** | `Workflow.await(Duration, condition)` returns false on timeout. |
| **Trade canceled by desk** | No path | `WorkflowClient.cancel(workflowId)` → workflow catches `CanceledFailure`, compensates. |
| **Worker crashes** | Handled — replay + `await` durability | Nothing to do. |
| **Compensation** (CTL booked, then ClearPar rejects → reverse booking) | No path | `io.temporal.workflow.Saga` — register undo per forward step, `saga.compensate()` on failure. |

Concretely:

```java
Saga saga = new Saga(new Saga.Options.Builder().build());
try {
    ctl.bookTrade(tradeId, event.dealId(), event.notional());
    saga.addCompensation(() -> ctl.cancelTradeBooking(tradeId));

    egs.publishToClearPar(new UploadTradeMessage(tradeId, ...));

    boolean responded = Workflow.await(
        Duration.ofHours(2),
        () -> confirmation != null || rejection != null);

    if (!responded) { saga.compensate(); egs.publish(new TradeTimedOut(tradeId)); return; }
    if (rejection != null) {
        saga.compensate();
        egs.publish(new TradeRejected(tradeId, rejection.reason()));
        return;
    }
    // happy path continues...
} catch (Exception e) {
    saga.compensate();
    egs.publish(new TradeFailed(tradeId, e.getMessage()));
    throw e;
}
```

Key observations:
1. Rejection is just another signal.
2. Timeout is a `Duration` argument to `await`. No timers to manage manually.
3. The `Saga` class tracks undo actions; `compensate()` runs them in reverse on failure. Each ledger op needs an undo activity — that's a real design exercise.

---

## 18. Deal as ordering key — and child workflows for trades

**User:**

> So it should be a deal-specific workflow because a deal is the only business key through which we can guarantee order. The gateway can pick up the trade and the deal id, or ClearPar can allow EGS to convert trade → deal id so the trade event will be at the deal level.
>
> But if I put it at the deal level, how do I make sure trades within the deal are allocated and rejected accordingly? A deal can have multiple trades, but not all trades should be confirmed. A few can be rejected. How does it work?

**Assistant** — instinct is right, lands on a well-known pattern: **parent deal workflow, child trade workflows.** The deal workflow owns ordering and aggregate state; each trade child runs its own ClearPar saga independently. Multiple children can be in different states — one settled, one rejected, three in-flight — and none affect the others.

```
DealLifecycleWorkflow (id: "deal-D1")
  ├─ holds aggregate state (position, allocations summary, list of trades)
  ├─ receives book-trade signals from EGS
  ├─ starts TradeLifecycleWorkflow as a child per trade
  ├─ receives outcome signals back (settled / rejected / failed)
  └─ enforces deal-level ordering for things that need it

TradeLifecycleWorkflow (id: "tlm-trade-T1")  -- child of deal-D1
  └─ ClearPar saga with its own success / reject / compensate path
```

How rejection works in this model: **each trade child runs its own saga independently.** A rejection in T1 triggers compensation only in T1's workflow. T2 and T3 don't know and don't care.

```
Deal D1
  ├─ T1: book → upload → confirm → allocate → settle → signal Deal: onTradeSettled(T1, $3M)
  ├─ T2: book → upload → REJECT (KYC) → compensate (reverse CTL booking) → signal Deal: onTradeRejected(T2)
  └─ T3: still in-flight
End state: { T1: SETTLED, T2: REJECTED }, settledNotional = $3M.
```

`signalWithStart` covers the "first event on a deal both creates the workflow and signals it" race. `Workflow.continueAsNew(...)` periodically (every 1000 events or every quarter) keeps history bounded for long-lived deals.

---

## 19. Splitting between repos — DTS and TLM as separate components

**User:**

> But we have separated our components as DTS and TLM services. By putting the TLM workflow inside the deal workflow, I'm creating one god-like workflow. Can there be some way we split it modularly to sit between two repos?

**Assistant** — exactly the smell test that prevents Temporal codebases from collapsing. The fix is a clean DDD move: **the same business entity (Deal) is modeled by a different aggregate in each bounded context.** Two workflows per deal, owned by two repos, communicating only through events.

| Component | Workflow | Workflow id | Owns | Cares about |
|---|---|---|---|---|
| **DTS repo** | `DealLifecycleWorkflow` | `dts-deal-D1` | Deal terms, amendments, deal termination | Product-setup events from upstream |
| **TLM repo** | `DealTradeCoordinatorWorkflow` | `tlm-deal-D1` | Trade ordering on the deal, aggregate trade position, spawning trade children, deal-level trade compensation | Book-trade events from EGS; deal-state events from DTS |
| **TLM repo** | `TradeLifecycleWorkflow` | `tlm-trade-T1` (child of `tlm-deal-D1`) | One trade's ClearPar saga | Inbound ClearPar signals |

Same `dealId` is the shared correlation key. **DTS knows nothing about trades. TLM knows nothing about deal-term structure.** Each is one repo, one team, one deploy. Only contract between them is the event schema, lived in a small shared `contracts` package.

**Who enforces what ordering:**

| Ordering concern | Lives in |
|---|---|
| Deal terms before trade booking | **TLM coordinator** rejects/queues `onBookTrade` until `onDealTermsCommitted` arrives from DTS via EGS. |
| Amendments on the same deal | **DTS** `DealLifecycleWorkflow` — serializes via signal queue. |
| Trades on the same deal | **TLM coordinator** — serializes signals into a queue, spawns children at its own pace. |
| Steps within one trade (book → upload → settle) | **TLM child** — the saga we already have. |
| Trade-level rejection / compensation | **TLM child** — local to that trade. The coordinator just observes via signal. |

TLM coordinator sketch:

```java
public class DealTradeCoordinatorWorkflowImpl implements DealTradeCoordinatorWorkflow {
    private boolean termsCommitted = false;
    private final Map<String, TradeStatus> trades = new HashMap<>();
    private double settledNotional = 0;
    private final Deque<BookTradeEvent> queue = new ArrayDeque<>();

    @Override
    public void run(String dealId) {
        while (true) {
            Workflow.await(() -> termsCommitted && !queue.isEmpty());
            BookTradeEvent next = queue.pollFirst();
            startTradeChild(dealId, next);
        }
    }

    @SignalMethod public void onDealTermsCommitted() { termsCommitted = true; }
    @SignalMethod public void onBookTrade(BookTradeEvent e) { queue.add(e); }
    @SignalMethod public void onTradeSettled(String tradeId, double notional) {
        trades.put(tradeId, TradeStatus.SETTLED); settledNotional += notional;
    }
    @SignalMethod public void onTradeRejected(String tradeId, String reason) {
        trades.put(tradeId, TradeStatus.REJECTED);
    }
    @QueryMethod public DealView snapshot() { return new DealView(trades, settledNotional); }
}
```

The coordinator imports nothing from DTS. It receives a signal `onDealTermsCommitted` triggered by EGS routing the DTS-published `DealTermsCommitted` event. **The contract is the event schema, not a Java interface.**

**Suggested staging:**

1. **Step 1 (small):** add rejection + saga compensation inside `TradeLifecycleWorkflow`. No structural change.
2. **Step 2 (medium):** add `DealTradeCoordinatorWorkflow` in TLM as parent. Move trade workflows to be its children. Route through `signalWithStart` in EGS.
3. **Step 3 (later):** physically split into two repos sharing a `contracts` artifact.

Doing them in that order means debugging each separately — saga vs. parent-child orchestration vs. inter-repo eventing.

---

## Where we left off

- Scaffold compiles, Flow A is end-to-end working (Temporal-as-publisher, no event bus).
- Flow B is end-to-end happy-path working (TLM workflow, mocked ClearPar EMS, signal + await, choreography handoff to Servicing).
- Failure handling not yet wired into Flow B (rejection signal, timeouts, saga compensation).
- Deal-level coordinator workflow not yet introduced — trade workflows are still top-level.
- DTS and TLM still in one repo (package separation reflects boundaries; physical repo split deferred).

Next decision: start with **Step 1** above (saga + rejection in trade workflow) for a clean single failure path, or jump to **Step 2** (deal coordinator + child trades) because the structural question is the one still itching.
