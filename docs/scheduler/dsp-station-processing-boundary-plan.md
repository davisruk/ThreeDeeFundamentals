# DSP Generic Station Processing Boundary Plan

Branch: `feature/dsp-station-processing-boundary`

Status: planned; no implementation has started.

## Purpose

Introduce the production ownership boundary between a bounded station-arrival FIFO and the
existing Third Party, Adapting, and P2P machine implementations. The boundary must represent four
distinct facts without turning the stations into one generic machine:

1. one exact routed physical tote has arrived at one exact station destination;
2. that station has accepted and claimed the tote for local processing;
3. the station's existing domain processing has completed exactly once; and
4. ownership has ended in either a same-tote `CONTINUE` disposition or a terminal `CONSUME`
   disposition.

This branch ends at the disposition boundary. A separately planned route-continuation branch will
select the next destination and republish `CONTINUE` work into source-neutral warehouse transport.

## Required Reading Before Implementation

Read, in order:

1. `docs/codex-instructions.md` and `docs/codex-context.md`;
2. this complete plan;
3. `docs/scheduler/dsp-av02-operational-allocation-plan.md`, especially its source-neutral
   transport contract, Step 13, and deferred operational EMPTY proof;
4. `docs/scheduler/dsp-warehouse-transport-routing-plan.md`;
5. `docs/scheduler/dsp-p2p-arrival-consumer-plan.md`;
6. `docs/machines/phase-1-stations-roadmap.md`;
7. `docs/machines/third-party-station-requirements.md` and
   `docs/machines/third-party-station-phase-1-plan.md`;
8. `docs/machines/adapting_station_requirements.md` and
   `docs/machines/adapting-station-phase-1-plan.md`;
9. the exact production and test files named by the selected step.

Before executing any step, record `git status --short` and stop if unrelated work overlaps that
step's change surface.

## Existing Boundaries To Preserve

### Warehouse transport arrival

- `WarehouseTransportArrivalController` remains the only terminal-sensor-to-station publication
  path.
- `StationRoutedToteArrivalQueue` owns the exact `RoutedPhysicalTote` while it is waiting.
- A claimant processes only the exact FIFO head, at most one head per binding per simulation
  update.
- Local deferral leaves the queue, route follower, tote, renderable, load plan, destination, and
  sticky P2P assignment unchanged.
- Downstream acceptance happens before the exact FIFO head is dequeued. A different or missing
  dequeue after acceptance is an invariant failure.

### Third Party

- `ThirdPartyArea` retains waiting/concurrency ownership and its processing timer.
- `ThirdPartyAreaController` remains the only boundary that applies completed picks to
  `MutableToteLoadPlanRegistry` and pack provenance.
- `ThirdPartyVisitFactory` remains the source of direct versus ADAPTED-preparation work.
- The production adapter replaces only the arrival/stop/pass-through assumptions currently private
  to `ThirdPartyAreaStopController`; it does not move those debug-route classes into production.
- Every valid Third Party completion produces `CONTINUE`. An ADAPTED preparation tote must still
  visit Adapting STORE later; a fulfilment tote continues toward its remaining route.

### Adapting

- `AdaptingArea`, `AdaptingBench`, `AdaptedLineStore`, and `AdaptingAreaController` retain bench
  selection, timers, logical storage, prepared-line readiness, collection, and load-plan mutation.
- The arrival destination target id is the already selected `AdaptingBenchId`. Arrival revalidates
  capacity at that exact bench and never silently chooses a different bench.
- `COLLECT` produces `CONTINUE` with the replacement load-plan instance installed by
  `AdaptingAreaController`.
- `STORE` produces `CONSUME`, calls `InboundToteLifecycleController.consumeAtAdapting(...)`, and
  never publishes the source ADAPTED tote for later P2P work.
- The production adapter replaces only the arrival/bench-stop/return assumptions currently private
  to `AdaptingBenchStopController`; it does not adopt that rig's route geometry.

### P2P

- Preserve candidate-specific sticky arrival admission, exact line/destination revalidation,
  contained-pack payload creation, `P2pTipperArrivalTarget`, `TipperInputQueue`, and
  `TipperInputQueueController`.
- The station claimant must not call `ToteTrackTipperFlowController.acceptNextTote(...)`.
- The claim remains active while the tote waits at tipper input and while the long-lived
  tote-to-bag machine processes it.
- Actual `TipperToteCompletedListener` notification completes the claim with `CONSUME`; acceptance
  into tipper input is not processing completion.
- `OperationalLifecycleP2pToteCompletedListener` remains the source-neutral OSR/AV02 lifecycle
  mutation boundary. Wrap it; do not copy or weaken its source-resolution logic.
- P2P-created outbound totes, output sheets, completed bags, and dispatch publication remain wholly
  outside this branch.

## Locked Architecture

### Package and types

Create the generic production types under:

```text
online.davisfamily.warehouse.sim.dsp.station.processing
```

Use these exact public types:

- `StationProcessingDispositionType` enum with exactly `CONTINUE` and `CONSUME`;
- `StationProcessingClaim`;
- `StationProcessingDisposition`;
- `StationProcessingAdmissionDecision`;
- `StationProcessingTarget`;
- `StationProcessingCompletionController`;
- `StationProcessingCoordinator`;
- `StationProcessingSnapshot`;
- `StationArrivalClaimController`;
- `StationArrivalClaimControllerSnapshot`;
- `StationProcessingBinding`;
- `StationConsumedToteController`;
- `StationProcessingOrderCatalog`;
- `DspStationProcessingRuntime`;
- `DspStationProcessingRuntimeFactory`.

Create the station adapters beside their existing domains:

- `thirdparty.ThirdPartyStationProcessingTarget`;
- `thirdparty.ThirdPartyStationProcessingController`;
- `adapting.AdaptingStationProcessingTarget`;
- `adapting.AdaptingStationProcessingController`;
- `p2p.arrival.P2pStationProcessingTarget`;
- `p2p.arrival.StationProcessingP2pToteCompletedListener`.

Do not create a generic station machine, generic processing timer, second station wait queue,
second tote-load-plan registry, or second route engine.

### Claim contract

`StationProcessingClaim` is an immutable record with this constructor shape:

```java
public StationProcessingClaim(
        RoutedPhysicalTote routedTote,
        Duration claimedAt)
```

It validates non-null input, nonnegative time, and exact physical identity across the launch
request, load plan, tote, renderable, and route follower through the existing
`RoutedPhysicalTote` contract. It exposes convenience accessors for physical tote id and exact
destination, but stores no copied tote/renderable/route values.

One shared `StationProcessingCoordinator` owns all active claims by `PhysicalToteId`, insertion
ordered. A physical id may be claimed only once and may not be claimed after a completed
disposition exists. The coordinator is simulation-thread-owned and is never passed to a scheduler
worker.

### Disposition contract

`StationProcessingDisposition` is an immutable record with this constructor shape:

```java
public StationProcessingDisposition(
        StationProcessingClaim claim,
        StationProcessingDispositionType type,
        ToteLoadPlan currentLoadPlan,
        Duration completedAt)
```

It requires the current plan's physical id to match the claim and completion time not to precede
claim time. It retains the exact claim, and therefore the exact tote, renderable, original launch
identity, arrived destination, route follower, source identity, and optional pinned P2P assignment.
It also carries the exact current load-plan instance after Third Party or Adapting mutation.

The coordinator completes an active claim exactly once, removes it from active ownership, appends
the disposition to one insertion-ordered unbounded FIFO, and retains a completed-id set so a
dequeued disposition cannot be recreated. The FIFO is deliberately unbounded: it is an internal
ownership handoff, and P2P's void completion callback cannot safely apply bounded downstream
backpressure after machine processing has completed. The next branch may leave a `CONTINUE`
disposition queued until warehouse transport can accept it.

Expose simulation-thread methods equivalent to:

```java
public void validateCanClaim(RoutedPhysicalTote routedTote, Duration claimedAt)
public StationProcessingClaim claim(RoutedPhysicalTote routedTote, Duration claimedAt)
public StationProcessingClaim requireActiveClaim(PhysicalToteId physicalToteId)
public void validateCanComplete(
        PhysicalToteId physicalToteId,
        StationProcessingDispositionType type,
        ToteLoadPlan currentLoadPlan,
        Duration completedAt)
public StationProcessingDisposition complete(
        PhysicalToteId physicalToteId,
        StationProcessingDispositionType type,
        ToteLoadPlan currentLoadPlan,
        Duration completedAt)
public Optional<StationProcessingDisposition> peekDisposition()
public Optional<StationProcessingDisposition> dequeueDisposition()
public List<StationProcessingDisposition> pendingDispositions()
public StationProcessingSnapshot snapshot()
```

`validateCanComplete(...)` performs every coordinator check without mutation. After it succeeds on
the simulation thread, the matching `complete(...)` call must be mechanically non-failing unless
an intervening same-thread mutation violates an invariant.

`StationProcessingSnapshot` contains values only: ordered active physical ids with destination and
claim time, ordered pending disposition ids/types/destinations/times, completed count, and last
completed id/type. It must not expose `RoutedPhysicalTote`, `Tote`, `RenderableObject`, route
followers, mutable plans, targets, controllers, or the coordinator.

### Arrival claim protocol

`StationProcessingAdmissionDecision` has `permit()` and `defer(String reason)` factories.

`StationProcessingTarget` has these responsibilities:

```java
OperationalRouteDestination destination();
StationProcessingCoordinator coordinator();
StationProcessingAdmissionDecision evaluate(RoutedPhysicalTote routedTote);
StationProcessingClaim accept(RoutedPhysicalTote routedTote, Duration claimedAt);
```

`evaluate(...)` is a live, simulation-thread, non-mutating revalidation. `accept(...)` repeats all
identity and admission checks, mutates the existing local station boundary, and registers the exact
claim in the shared coordinator before returning it. A returned claim must contain the same exact
`RoutedPhysicalTote` instance supplied by the caller.

Station timing/completion is deliberately separate from claiming. Define:

```java
public interface StationProcessingCompletionController extends SimulationController {
    String processingControllerId();
    Set<OperationalRouteDestination> destinations();
    StationProcessingCoordinator coordinator();
}
```

One completion controller may cover several destinations that share one mutable area, as the
Adapting benches do. Its id is nonblank and stable; destinations are a nonempty immutable set. A
destination may be covered by at most one completion controller in one runtime. P2P has no such
controller because its existing tipper completion callback is authoritative.

`StationArrivalClaimController` owns one `StationProcessingBinding`, converts absolute simulation
seconds to `Duration` using rounded nanoseconds, and follows this order:

1. peek the exact FIFO head; clear prior block state and stop when empty;
2. require source and target destinations to be exactly equal;
3. call target `evaluate(...)`; on deferral record id/reason and leave all ownership unchanged;
4. call target `accept(...)`; this is the mutation boundary and downstream now owns the claim;
5. dequeue the source and require the dequeued object to be the same head instance;
6. record one successful claim and process no second head during that update.

Do not set a new route segment, motion state, visual offset, lid state, next destination, or new
launch request during claim. The arrived tote remains physically held until its station owns a
later state transition.

### Order and load-plan revalidation

`StationProcessingOrderCatalog` is constructed from the retained `List<NotionalToteOrder>`, indexes
exactly by `OrderSheetKey`, rejects duplicate sheets, and returns the exact immutable order. It is
not a mutable scheduler-status store. Expose exactly:

```java
public Optional<NotionalToteOrder> find(OrderSheetKey orderSheetKey)
public NotionalToteOrder require(OrderSheetKey orderSheetKey)
public List<NotionalToteOrder> orders()
```

Before Third Party or Adapting accepts a claim, require:

- the route identity sheet exists in the order catalog;
- order type and service centre equal the route identity;
- the current `MutableToteLoadPlanRegistry` entry exists and is the same object instance as
  `RoutedPhysicalTote.loadPlan()`;
- the station-specific visit factory derives a visit whose sheet and physical id match the claim;
- the route destination station type and configured target id match the adapter.

After successful domain completion, resolve the load plan again and require the replacement/current
registry entry before publishing the disposition. Do not mutate the `RoutedPhysicalTote` record or
the old immutable plan in place.

### Terminal consume presentation

`StationConsumedToteController` observes, but does not dequeue, newly published `CONSUME`
dispositions. Exactly once per physical id it closes the tote lids, sets its motion state to
`HELD`, and makes the exact renderable invisible. It does not move the renderable off-screen,
remove logical plans or provenance, remove a `SimObject` from `SimulationWorld`, or publish an
empty-tote return journey. Visibility is the established lifecycle mechanism because
`SimulationWorld` has no removal API.

`CONTINUE` dispositions remain held and visible. Their route and motion state are unchanged until
the route-continuation branch accepts them.

### Threading and failure rules

- All arrival evaluation, claim mutation, station state, load-plan mutation, lifecycle mutation,
  disposition publication, and consume presentation run on the simulation thread.
- No live station-processing object is added to scheduler snapshots or worker inputs.
- Stale identity, duplicate claim/completion, wrong station/target, missing or changed load plan,
  mismatched selected bench, and invalid lifecycle fail before new processing-boundary mutation.
- Capacity/admission closure is a deterministic deferral and retains FIFO ownership.
- Once an existing station machine has accepted a visit/payload, an impossible coordinator or
  source-dequeue mismatch is an invariant failure; do not catch it and continue with partial state.
- Domain state machines retain their existing failure semantics. This branch does not redesign
  Third Party pick atomicity or Adapting store/take atomicity.
- Reset remains whole-runtime reconstruction; add no mutable reset method.

## Explicit Non-Goals

- Selecting a next station or mutating `RouteRequirements` to record completed stations.
- Creating a continued `OperationalRouteLaunchRequest` or changing its destination.
- Enqueueing a disposition into launch, hydration, ingress, in-flight, route, or station-arrival
  transport.
- Adding station-to-station topology, track geometry, or visual stop controllers.
- Replacing `ThirdPartyArea`, `AdaptingBench`, P2P/tipper, PRL/PCR, or bagging state machines.
- Publishing P2P outbound totes or implementing dispatch/32R behavior.
- Exception Station, MANUAL, MANUAL_MERGE, lid-machine, short-pick, or NS-bag behavior.
- Calibrated timings, full-day execution, metrics, or visual polish.

## Step 1: Define Exact Claim And Disposition Ownership

### Required change surface

Create:

- `StationProcessingDispositionType.java`
- `StationProcessingClaim.java`
- `StationProcessingDisposition.java`
- `StationProcessingCoordinator.java`
- `StationProcessingSnapshot.java`
- matching tests under `...dsp.station.processing`.

Do not modify transport, station-domain, lifecycle, P2P, tote-to-bag, renderer, or debug files.

### Behavioral specification

- Claiming retains the exact routed tote and records rounded simulation time.
- Active claims remain insertion ordered across mixed destinations.
- Unknown, duplicate, already-completed, negative-time, and identity-mismatched operations fail
  without mutation.
- Completion requires the exact physical id and a matching current plan, moves ownership once from
  active to pending disposition, and retains the exact claim and exact current-plan instance.
- Completion before claim time and repeated completion fail without changing active, pending, or
  completed history.
- Disposition dequeue is FIFO and does not permit the physical id to be reclaimed.
- The immutable snapshot exposes ordered value data and no live mutable object.

### Expected output

One simulation-thread ownership ledger can explain whether each physical tote is actively owned by
a station or awaits post-processing disposition handling.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDomainTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinatorTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshotTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Define station processing ownership`

## Step 2: Claim Exact Station Arrival Heads

### Required change surface

Create:

- `StationProcessingAdmissionDecision.java`
- `StationProcessingTarget.java`
- `StationProcessingCompletionController.java`
- `StationProcessingBinding.java`
- `StationArrivalClaimController.java`
- `StationArrivalClaimControllerSnapshot.java`
- their focused tests.

Use a test-only target; do not add a production station adapter in this step. Keep
`StationRoutedToteArrivalQueue` unchanged.

### Behavioral specification

- Empty update is a no-op.
- Permitted acceptance gives the target the exact head and exact rounded claim time, then dequeues
  that same instance and increments the count once.
- A two-entry FIFO claims only the first entry per update.
- A deferred target leaves both entries, tote motion, route state, renderable, and plan untouched.
- A null decision, wrong returned claim, mismatched destination, disappearing/different dequeue,
  or target contract violation fails clearly.
- Snapshot reports source occupancy/capacity, destination, head id, active blocked id/reason, last
  claimed id, and successful count using immutable values.

### Expected output

All three production station adapters can share one arrival-to-claim controller without sharing
their machine logic.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingAdmissionDecisionTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBindingTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerSnapshotTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Claim routed station arrivals`

## Step 3: Adapt Third Party Processing

### Required change surface

Create:

- `station.processing.StationProcessingOrderCatalog` and its test;
- `thirdparty.ThirdPartyStationProcessingTarget`;
- `thirdparty.ThirdPartyStationProcessingController`;
- `thirdparty.ThirdPartyStationProcessingTargetTest`;
- `thirdparty.ThirdPartyStationProcessingControllerTest`.

Do not modify `ThirdPartyArea`, `ThirdPartyAreaController`, `ThirdPartyAreaStopController`,
`ThirdPartyDebugRig`, product-master loading, provenance contracts, or Third Party
admission/scheduler classes. The existing `area.canAccept()`, `area.submitVisit(...)`,
`areaController.update(...)`, and `areaController.completionForTote(...)` APIs are sufficient.

### Locked adapter behavior

Construct the target with, in order:

```java
public ThirdPartyStationProcessingTarget(
        OperationalRouteDestination destination,
        StationProcessingOrderCatalog orderCatalog,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        ThirdPartyVisitFactory visitFactory,
        ThirdPartyArea area,
        StationProcessingCoordinator coordinator)
```

Require `destination.stationType() == THIRD_PARTY`. Admission resolves and validates the exact
order/plan/visit and defers only when `area.canAccept()` is false. A routed tote sent to Third Party
with no Third Party visit is a stale route invariant, not pass-through success.

Acceptance repeats revalidation, calls `area.submitVisit(visit)`, requires success, then registers
the exact claim.

Construct the one area-level completion controller with:

```java
public ThirdPartyStationProcessingController(
        String processingControllerId,
        Set<OperationalRouteDestination> destinations,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        ThirdPartyAreaController areaController,
        StationProcessingCoordinator coordinator)
```

All destinations must be THIRD_PARTY. Its update calls `areaController.update(dtSeconds)`, checks
active claims for its destination set in claim order, and publishes at most one new completion per
update. When `completionForTote(...)` appears, require the same visit identity, resolve the current
replacement plan from the registry, and complete with `CONTINUE` at the rounded absolute time from
the supplied `SimulationContext`.

Do not close lids, set motion to moving, select the next station, or dequeue the disposition.

### Behavioral specification

- Direct FULL_PACK and EMPTY Third Party picks mutate the existing plan exactly once and produce
  one `CONTINUE` disposition containing the exact replacement plan.
- ADAPTED preparation work also produces `CONTINUE`; it is not consumed at Third Party.
- Capacity closure defers the exact FIFO head without creating a visit or claim.
- Wrong source identity, sheet, type, service centre, destination, absent visit, missing/stale plan,
  duplicate physical id, and mismatched completion fail before new boundary mutation.
- Repeated domain completion observation cannot publish twice.
- Existing `ThirdPartyPickFlowTest` remains unchanged in behavior.

### Expected output

Production Third Party work can own an arrived routed tote through its real pick completion and
hand the same physical journey to the generic disposition boundary.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalogTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingControllerTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyPickFlowTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAdaptedCollectIntegrationTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Adapt Third Party station processing`

## Step 4: Adapt Adapting STORE And COLLECT Processing

### Required change surface

Create:

- `adapting.AdaptingStationProcessingTarget`;
- `adapting.AdaptingStationProcessingController`;
- `adapting.AdaptingStationProcessingTargetTest`;
- `adapting.AdaptingStationProcessingControllerTest`.

Modify:

- `adapting.AdaptingArea` and `AdaptingAreaAdmissionTest` for exact preselected-bench submission;
- `lifecycle.InboundToteLifecycleController` to add non-mutating STORE-consumption validation;
- `InboundToteLifecycleControllerTest`.

Do not modify `AdaptingBenchStopController`, `AdaptingDebugRig`, storage layout policy, pack-plan
factory behavior, or scheduler admission behavior.

### Locked lifecycle API

Add:

```java
public void validateConsumeAtAdapting(
        PhysicalToteId toteId,
        Duration consumptionTime)
```

It performs every existing `consumeAtAdapting(...)` check without mutation. Refactor
`consumeAtAdapting(...)` to call it, then preserve the existing mutation order: terminate the exact
active assignment with `CONSUMED_AT_ADAPTING`, then transition the tote lifecycle. Do not broaden
eligibility beyond manifested `ADAPTED` inbound totes.

### Locked adapter behavior

Construct the target with, in order:

```java
public AdaptingStationProcessingTarget(
        OperationalRouteDestination destination,
        StationProcessingOrderCatalog orderCatalog,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        AdaptingVisitFactory visitFactory,
        AdaptingArea area,
        StationProcessingCoordinator coordinator)
```

Require `destination.stationType() == ADAPTING`. Admission derives the exact visit, asks
the area whether the exact `AdaptingBenchId(destination.targetId())` can accept, and defers when
that bench's processing slot and FIFO are full. It does not rerun area-wide bench selection after
the tote has physically arrived.

Add these exact `AdaptingArea` APIs:

```java
public boolean canAcceptVisitAt(AdaptingBenchId benchId)
public AdaptingBenchSelection submitVisitTo(
        AdaptingBenchId benchId,
        AdaptingVisit visit)
```

Both validate the bench id. `submitVisitTo(...)` uses the same direct-start-versus-bench-FIFO
mutation as existing `submitVisit(...)`, but only for the supplied bench and returns a blocked
selection when that exact bench is full. Refactor existing `submitVisit(AdaptingVisit)` to retain
its current area-wide `selectBenchFor(...)` behavior and delegate its accepted mutation to
`submitVisitTo(...)`; preserve all existing callers and selection tests.

Acceptance repeats all checks, calls `area.submitVisitTo(benchId, visit)`, requires the returned selected
bench to equal the destination, registers the claim, and immediately calls `startProcessing()`
when that visit is installed on the selected bench in `QUEUED` state. A visit placed in the bench's
pending FIFO remains queued.

Construct one completion controller for the complete shared area:

```java
public AdaptingStationProcessingController(
        String processingControllerId,
        Set<OperationalRouteDestination> destinations,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        AdaptingArea area,
        AdaptingAreaController areaController,
        InboundToteLifecycleController inboundLifecycleController,
        StationProcessingCoordinator coordinator)
```

Its destination set contains exactly the ADAPTING destination for every bench it owns; each target
id must resolve to an existing `AdaptingBenchId`. It ticks the shared area once per simulation
update regardless of bench count. On each controller update, in sorted bench-id order:

1. tick each processing bench with `dtSeconds`;
2. for the first `COMPLETED` bench belonging to an active claim, inspect its visit before mutation;
3. for STORE, prevalidate coordinator completion and lifecycle consumption at current rounded
   simulation time;
4. call `areaController.applyBenchCompletion(benchId)` exactly once;
5. for COLLECT, resolve the exact replacement load plan and complete `CONTINUE`;
6. for STORE, call `consumeAtAdapting(...)`, resolve the retained current plan, and complete
   `CONSUME`;
7. after completion freed the bench, dispatch its next queued visit and start it when its state is
   `QUEUED`;
8. publish at most one disposition across the complete target per update.

For an immediately accepted visit, start its bench when it is `QUEUED`; for a queued visit, do not
start it until `dispatchNextQueuedVisit(...)` installs it on an idle bench. Physical route-stop and
return motion are not part of this target.

### Behavioral specification

- ASSOCIATED and EMPTY COLLECT retrieve exact staged records, replace/create the load plan once,
  and publish `CONTINUE` with that exact new instance.
- ADAPTED STORE publishes readiness through the existing controller, consumes the exact inbound
  lifecycle/assignment at rounded completion time, and publishes `CONSUME`.
- FULL_PACK collect remains rejected by `AdaptingVisitFactory`.
- Exact selected-bench capacity closure defers without queue dequeue, visit submission, claim,
  storage, load-plan, readiness, or lifecycle mutation; spare capacity at another bench does not
  redirect the arrived tote.
- Multiple benches complete deterministically in bench-id order, with at most one disposition per
  target update; queued visits retain their selected bench FIFO.
- Missing/stale plan, wrong manifest/source/type/sheet/service centre, invalid lifecycle time, and
  repeated completion fail before lifecycle/disposition mutation.
- Existing STORE and COLLECT flow tests retain their domain behavior.

### Expected output

The production Adapting boundary distinguishes same-tote collection from terminal preparation-tote
consumption while preserving its existing logical store and readiness semantics.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingControllerTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaAdmissionTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStoreFlowTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleControllerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Adapt Adapting station processing`

## Step 5: Bring P2P Under The Common Claim And Completion Contract

### Required change surface

Create:

- `p2p.arrival.P2pStationProcessingTarget`;
- `p2p.arrival.StationProcessingP2pToteCompletedListener`;
- their focused tests.

Modify:

- `P2pArrivalConsumerController` and snapshot only to delegate claim behavior to the generic
  `StationArrivalClaimController` while preserving its existing public constructors/accessors and
  diagnostic fields;
- `DspP2pArrivalConsumerRuntime` and `DspP2pArrivalConsumerRuntimeFactory` with a coordinator-aware
  overload;
- focused existing P2P arrival tests.

Keep `P2pArrivalConsumerBinding` unchanged.

Keep `P2pTipperArrivalTarget`, `TipperInputQueueController`, `ToteTrackTipperFlowController`,
`OperationalLifecycleP2pToteCompletedListener`, sticky lease classes, payload factories, and
outbound allocation contracts otherwise unchanged.

### Locked P2P target behavior

`P2pStationProcessingTarget` wraps exactly one existing admission policy, route binding, payload
factory, and `P2pTipperArrivalTarget`, plus the shared coordinator. Its `evaluate(...)` preserves
the current order:

1. require the exact terminal route-segment identity through `P2pArrivalRouteBinding`;
2. build `P2pArrivalAdmissionRequest.from(routedTote)` and revalidate sticky admission;
3. defer with the existing policy reason when denied;
4. defer with `TIPPER_INPUT_FULL` when the exact tipper target has no capacity.

Its `accept(...)` repeats evaluation, creates one payload, calls
`P2pTipperArrivalTarget.accept(...)`, and then registers the exact processing claim. It never calls
the tipper flow directly. The compatibility `P2pArrivalConsumerControllerSnapshot` must continue
to expose the existing admission/tipper block constants and target/source occupancy values.

Add this constructor overload to `P2pArrivalConsumerController`, after retaining the existing
five-argument constructor as a delegating compatibility path:

```java
public P2pArrivalConsumerController(
        StationRoutedToteArrivalQueue sourceQueue,
        P2pArrivalAdmissionPolicy admissionPolicy,
        P2pArrivalRouteBinding routeBinding,
        P2pTipperPayloadFactory payloadFactory,
        P2pTipperArrivalTarget target,
        StationProcessingCoordinator coordinator)
```

Add this runtime-factory overload:

```java
public DspP2pArrivalConsumerRuntime create(
        SimulationWorld simulationWorld,
        List<P2pArrivalConsumerBinding> bindings,
        StationProcessingCoordinator coordinator)
```

The existing two-argument factory method creates one private coordinator for the complete returned
runtime and delegates to the new overload, so isolated existing callers retain behavior. The new
overload uses its one supplied coordinator for every binding. Expose that coordinator's immutable
snapshot from `DspP2pArrivalConsumerRuntime`; do not expose the live coordinator. The generic
production runtime binds `P2pStationProcessingTarget` directly and must not additionally register
this compatibility runtime for the same queue.

### Locked completion listener behavior

Construct `StationProcessingP2pToteCompletedListener` with:

```java
public StationProcessingP2pToteCompletedListener(
        TipperToteCompletedListener lifecycleDelegate,
        StationProcessingCoordinator coordinator)
```

At `onToteCompleted(...)`:

1. validate non-null tote/context and convert absolute simulation time with rounded nanoseconds;
2. require the active P2P claim and its exact tote instance;
3. prevalidate a `CONSUME` completion using the claim's current plan;
4. call the existing lifecycle delegate exactly once;
5. complete the coordinator claim with `CONSUME` exactly once.

Use `OperationalLifecycleP2pToteCompletedListener` as the production delegate. A lifecycle failure
leaves the claim active and publishes no disposition. A repeated, unknown, wrong-tote-instance, or
wrong-destination callback fails before lifecycle mutation.

### Behavioral specification

- Existing P2P admission deferral, tipper-input backpressure, exact route/payload/load-plan
  continuity, one-head-per-update, and debug snapshots remain green.
- Acceptance registers an active claim but produces no disposition.
- Tipper completion for OSR FULL_PACK/ASSOCIATED and AV02 EMPTY performs the existing exact lifecycle
  transition then publishes one `CONSUME` disposition.
- Unknown, duplicate, pre-claim, wrong tote instance, lifecycle-rejected, and stale assignment
  completion cases publish nothing and preserve claim state as specified above.
- P2P completion does not create or publish an outbound tote.

### Expected output

P2P uses the same arrival/claim/disposition vocabulary as other stations without collapsing its
two-stage tipper admission or long-lived processing path.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListenerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalConsumerControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerScenarioTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalLifecycleP2pToteCompletedListenerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Unify P2P station processing ownership`

## Step 6: Apply Terminal Consume Presentation

### Required change surface

Create:

- `StationConsumedToteController.java`;
- `StationConsumedToteControllerTest.java`.

Do not modify `SimulationWorld`, `RenderableObject`, `ToteTrackTipperFlowController`, or debug-only
tote hiders.

### Behavioral specification

- A new STORE or P2P `CONSUME` disposition makes the exact renderable invisible, closes lids, and
  leaves the exact tote held, even if the tipper controller set it moving after its callback.
- Processing is exactly once per physical id and does not dequeue or alter the disposition.
- A `CONTINUE` disposition remains visible and retains its motion/route state.
- Repeated updates are idempotent; mixed dispositions are applied in disposition FIFO order.
- The controller does not alter load-plan registry, lifecycle ledger, provenance, source identity,
  pinned P2P assignment, route follower, or outbound state.

### Expected output

`CONSUME` has a coherent terminal physical presentation without adding unsupported world removal or
empty-tote return behavior.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationConsumedToteControllerTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListenerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Terminate consumed station totes`

## Step 7: Compose One Production Station Processing Runtime

### Required change surface

Create:

- `DspStationProcessingRuntime.java`;
- `DspStationProcessingRuntimeFactory.java`;
- `DspStationProcessingRuntimeTest.java`;
- `DspStationProcessingRuntimeFactoryTest.java`.

Use `StationProcessingBinding` values supplied by the caller. Do not construct datasets, station
machines, route geometry, transport runtime, P2P line leases, or tote-to-bag machinery inside this
factory.

### Locked factory API and order

Use this factory shape:

```java
public DspStationProcessingRuntime create(
        SimulationWorld simulationWorld,
        StationProcessingCoordinator coordinator,
        List<StationProcessingBinding> bindings,
        List<StationProcessingCompletionController> completionControllers)
```

Validate all bindings before controller registration:

- no null values;
- one exact source queue instance per binding;
- one exact target instance per binding;
- unique `OperationalRouteDestination` values;
- source destination equals target destination;
- only THIRD_PARTY, ADAPTING, and P2P station types;
- every target uses the supplied coordinator, exposed through an identity accessor on the target
  interface for this validation;
- completion-controller ids are unique and their coordinator is the supplied coordinator;
- each THIRD_PARTY or ADAPTING binding destination is covered by exactly one completion controller;
- no P2P binding destination is covered by a completion controller;
- completion controllers cover no destination absent from the bindings.

Sort bindings by `stationType().name()` then `targetId()` for deterministic controller order. Sort
completion controllers by `processingControllerId()`. Add all `StationArrivalClaimController`s
first, then the completion controllers, then one `StationConsumedToteController`. A target shared
by more than one binding is forbidden. One Adapting completion controller deliberately covers all
of its bench-specific targets, ensuring the shared benches tick exactly once per update.

The runtime exposes ordered immutable claimant snapshots, the coordinator snapshot, destination
list, and `isClosed()`. `close()` is idempotent and owns no worker thread; it does not unregister
controllers or mutate station state.

The existing `DspP2pArrivalConsumerRuntimeFactory` remains available for isolated P2P composition.
Do not nest a separately registered P2P arrival runtime inside this generic runtime for the same
source queue.

### Behavioral specification

- Empty binding composition is valid and registers only the consume controller.
- Mixed Third Party, Adapting, and P2P bindings are deterministically ordered and share exactly one
  coordinator.
- Duplicate source, target, or destination and foreign coordinator fail before any controller is
  registered.
- One update can claim at most one head per independent destination; station target updates and
  consume presentation occur after all claims.
- Runtime snapshots remain value-only and reset is achieved by creating a fresh runtime.

### Expected output

Production scenes can compose all station consumers through one explicit runtime while retaining
separate domain targets and existing machine ownership.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Compose production station processing runtime`

## Step 8: Prove Mixed Station Processing Without Route Continuation

### Required change surface

Create only:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/station/processing/DspStationProcessingBoundaryScenarioTest.java`.

Use private fixture helpers. Do not add a test-only route continuation, destination rewrite,
transport republisher, or production fixture class.

### Scenario fixture

Use one shared coordinator, order catalog, mutable load-plan registry, physical lifecycle ledger,
manifest catalog, AV02 inventory where needed for direct P2P EMPTY, real Third Party and Adapting
domain controllers, and real P2P arrival/TipperInputQueue boundaries. Construct exact
`RoutedPhysicalTote` values with source-neutral identity and enqueue them only through real
`StationRoutedToteArrivalQueue`s.

Use explicit identities:

- an OSR FULL_PACK direct Third Party tote;
- an OSR ADAPTED Third Party-then-Adapting STORE tote;
- an AV02 EMPTY Adapting COLLECT tote;
- one OSR or AV02 direct P2P tote with a pinned exact line assignment.

Use deterministic explicit simulation time. Drive station completion to terminal states; do not
assert arbitrary update counts and do not use sleeps.

### Behavioral specification

1. `shouldClaimOnlyAfterExactStationAcceptance`
   - capacity/admission closure retains the exact FIFO head and creates no claim;
   - opening capacity transfers the same routed object from FIFO to the matching active claim;
   - wrong destination/bench/assignment cannot be claimed, and another adapting bench's free
     capacity cannot redirect a tote from its exact arrived destination.

2. `shouldPreserveReplacementPlanAtThirdPartyAndAdaptingCollectDispositions`
   - Third Party completion replaces the plan once and emits `CONTINUE` with that exact instance;
   - a separate Adapting COLLECT completion replaces its plan once and emits `CONTINUE` with that
     exact instance;
   - source identity, physical id, tote, renderable, route follower, arrived destination, and
     pinned P2P assignment remain the exact original values;
   - neither disposition is republished or assigned a next destination in this branch.

3. `shouldConsumeAdaptedStoreWithoutContinuingInboundTote`
   - STORE publishes prepared readiness, terminates the exact assignment/lifecycle at Adapting,
     emits `CONSUME`, and becomes held/invisible;
   - no P2P claim, outbound tote, output sheet, or bag is created.

4. `shouldCompleteP2pOnlyAtTipperCompletion`
   - P2P arrival/tipper-input acceptance creates an active claim and no disposition;
   - actual tipper completion invokes the production lifecycle delegate, emits `CONSUME`, and
     terminally hides/holds the inbound tote;
   - exact sticky assignment and source identity remain inspectable, while outbound allocation is
     untouched.

5. `shouldKeepDispositionFifoAcrossStationTypes`
   - deliberately complete mixed station work in a known order;
   - coordinator pending order exactly matches completion order regardless of claim order;
   - dequeue transfers exact disposition ownership once and completed ids cannot be reclaimed.

### Expected output

The branch proves the complete production arrival -> claim -> real domain processing -> disposition
contract for all three station families while visibly and architecturally stopping before onward
route publication.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingBoundaryScenarioTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove generic station processing boundary`

## Step 9: Regression And Branch Closure

This step is owned by the planning/review model and the user after Steps 1-8 are accepted. Do not
start route continuation during closure.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Run the focused regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

Then run the complete suite:

```powershell
.\gradlew test
```

No visual run is required: this branch adds no production route continuation or new debug-scene
geometry, and its terminal visibility behavior is covered by focused tests. Existing visual rigs
must remain source-compatible; visual verification belongs to the later continuation integration.

### Parent architecture review

Review the actual diff and verify:

- every production claim originates from one exact station-arrival FIFO head;
- downstream claim acceptance precedes exact source dequeue;
- one shared coordinator explains active and completed ownership without entering worker snapshots;
- Third Party and Adapting reuse their real domain state/mutation controllers;
- Third Party and COLLECT emit `CONTINUE` with the exact current replacement plan;
- Adapting STORE and P2P emit `CONSUME` only after their lifecycle contracts succeed;
- P2P claim begins at tipper-input acceptance but completes only at actual tipper completion;
- consumed inbound totes are held/hidden and never reused as outbound totes;
- no disposition is given a next destination or republished into transport;
- no test-only handoff, second route engine, generic machine, mutable reset, calibrated timing,
  Exception behavior, or outbound dispatch behavior was added.

### Documentation closure

After user verification is green:

- mark this plan `complete and verified` and record the final implemented contract;
- update the current-programme entries in `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and only stale current-position/reading-order text in
  `docs/codex-instructions.md`;
- update `docs/machines/phase-1-stations-roadmap.md` to mark the processing half complete;
- record the separately planned `feature/dsp-station-route-continuation` as next;
- keep the operational EMPTY end-to-end proof deferred until route continuation is complete.

Proposed commit message: `Complete station processing boundary`

## Expected Final Contract

- Warehouse transport owns a routed physical tote until terminal arrival queues it.
- The station-arrival FIFO owns it until one exact station target accepts a claim.
- The station target and shared coordinator own it throughout the existing domain machine's real
  processing lifecycle.
- Completion publishes exactly one immutable `CONTINUE` or `CONSUME` disposition with the exact
  claim and exact current load plan.
- Third Party and Adapting COLLECT preserve the same inbound physical journey for later
  continuation; Adapting STORE and P2P terminate it.
- OSR and AV02 source identity, physical id, logical sheet, tote, renderable, route follower,
  current load plan, arrived destination, and optional pinned P2P assignment remain explainable at
  the boundary.
- The simulation thread owns all live mutation; scheduler workers continue to receive immutable
  snapshots only.
- Route continuation, next-destination selection, continued transport publication, and outbound
  dispatch remain explicit later features.
