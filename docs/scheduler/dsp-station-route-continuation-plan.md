# DSP Generic Station Route Continuation Plan

Branch: `feature/dsp-station-route-continuation`

Status: complete, verified, and merged to `master` at `fd671f0`.

## Purpose

Complete the production handoff after generic station processing. One simulation-thread controller
must consume the shared station-disposition FIFO, terminally acknowledge `CONSUME`, and send each
same-tote `CONTINUE` disposition through the existing source-neutral warehouse transport path to
its next required station.

The feature preserves the exact inbound physical journey across Third Party and Adapting COLLECT:
source identity, physical tote id, release provenance, tote, renderable, route follower, current
replacement load plan, and pinned P2P assignment. Adapting STORE and P2P remain terminal inbound
boundaries. A continued tote is already published in the simulation, so transport ingress must
recognise exact-object re-entry without adding the tote or renderable to the world a second time.

This branch does not implement the deferred operational EMPTY end-to-end proof. It establishes and
proves the production continuation APIs that proof will use.

## Required Reading Before Implementation

Read, in order:

1. `docs/codex-instructions.md` and `docs/codex-context.md`;
2. this complete plan;
3. `docs/scheduler/dsp-station-processing-boundary-plan.md`;
4. `docs/scheduler/dsp-av02-operational-allocation-plan.md`, especially Steps 8-9, Step 13, and
   `Deferred Operational EMPTY End-To-End Proof`;
5. `docs/scheduler/dsp-warehouse-transport-routing-plan.md`;
6. `docs/scheduler/dsp-osr-outbound-route-launch-plan.md`;
7. `docs/scheduler/dsp-operational-route-target-integration-plan.md`;
8. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`, especially Sections 7-9;
9. `docs/machines/phase-1-stations-roadmap.md`;
10. the exact production and test files named by the selected step.

Before executing any step, record `git status --short` and stop if unrelated work overlaps that
step's change surface.

## Existing Boundaries To Preserve

### Station processing ownership

- `StationProcessingCoordinator` remains the sole owner of active claims and pending dispositions.
- Third Party and Adapting completion controllers remain the only producers of their domain
  dispositions. P2P completion remains callback-driven.
- The pending disposition collection remains one global completion-ordered FIFO across station
  types. A blocked head prevents later dispositions from overtaking it.
- `StationConsumedToteController` remains the presentation owner for `CONSUME`: closed lids,
  `HELD`, and invisible exactly once. Continuation must not recreate that presentation behavior.
- A `CONTINUE` disposition remains held and visible until continuation has secured downstream
  transport ownership.

### Route intent and destination order

- `DspRouteDeriver` and immutable `RouteRequirements` remain the source of route intent.
- Required station order remains exactly:

  ```text
  THIRD_PARTY -> ADAPTING -> MANUAL -> P2P -> MANUAL_MERGE
  ```

- This feature supports completed `THIRD_PARTY` and `ADAPTING` stations and next destinations
  `ADAPTING` and `P2P`. A required MANUAL or MANUAL_MERGE step must fail explicitly; neither may be
  silently skipped. A `P2P` `CONTINUE` disposition is invalid because P2P consumes inbound totes.
- Do not mutate `RouteRequirements` or add a mutable completed-station list. The current claim's
  exact destination is the completed station, and the pure selector chooses the first required
  station after it.

### Exact next target

- An Adapting continuation uses `AdaptingVisitFactory.profileFor(order)` and
  `AdaptingArea.selectBenchFor(profile)` on the simulation thread. A rejected selection is a
  deterministic deferral that leaves the disposition and tote unchanged.
- The selected Adapting target is exactly `new OperationalRouteDestination(ADAPTING,
  selection.benchId().value())`. Later station arrival revalidates that exact bench and never
  redirects to another bench.
- A P2P continuation uses the exact destination in the already committed
  `P2pPhysicalToteAssignment`. It never reruns elastic allocation, lease selection, or scheduler
  admission.
- The pinned assignment must match the disposition physical id, service centre, and P2P station.
  Missing or mismatched assignment is an invariant failure before mutation.

### Source-neutral transport

- `OsrOutboundTransportQueue` keeps its existing name for compatibility but already carries
  source-neutral `RoutedPhysicalTote` values; it is the only continuation output queue.
- `WarehouseTransportIngressController` remains the only boundary that admits a queued payload to
  active in-flight transport.
- Initial hydrated totes are unpublished. Continued totes must be the same already-published
  `Tote` and `RenderableObject`. A same-id payload containing different physical objects is a
  conflict, not a continuation.
- Continued transport reuses the existing `WarehouseRouteCatalog` common entry segment, distance,
  and direction. It preserves the exact `RouteFollower` object but deliberately rebinds that
  object to the common entry after downstream queue acceptance.
- Re-entry at the common entry is the Phase 1 topology seam. Do not add station-to-station route
  geometry, a second route catalog, or a second route engine in this branch. The route through the
  configured warehouse network to the next station is still real and terminal-sensor driven.
- Ingress must not append an already-published exact tote/renderable to `SimulationWorld` or the
  renderables list again. It registers the new routed payload in flight and releases the held tote
  to `MOVING` only after successful ingress.

## Locked Architecture

### Package and public types

Create route-continuation types under:

```text
online.davisfamily.warehouse.sim.dsp.station.continuation
```

Use these exact public types:

- `StationRouteContinuationDecision`;
- `StationRouteContinuationSelector`;
- `StationRouteContinuationTargetResolver`;
- `OperationalStationRouteContinuationTargetResolver`;
- `StationRouteContinuationController`;
- `StationRouteContinuationControllerSnapshot`;
- `DspStationRouteContinuationRuntime`;
- `DspStationRouteContinuationRuntimeFactory`.

Add the transport enum:

- `transport.routing.WarehouseTransportPublicationState` with exactly `UNPUBLISHED`,
  `PUBLISHED_EXACT_OBJECTS`, and `PHYSICAL_ID_CONFLICT`.

Do not create a continuation queue, route-progress ledger, second disposition ledger, second
transport publisher, mutable route plan, or station-specific continuation controller.

### Disposition acknowledgement and repeated station claims

The completed station-processing branch deliberately retained completed physical ids permanently
because no downstream continuation existed. Evolve that contract without weakening terminal
ownership.

`StationProcessingCoordinator` must distinguish:

- an active claim;
- an unacknowledged completed disposition still present in the FIFO;
- a terminally consumed physical id; and
- cumulative completion/acknowledgement history.

Retain an internal last-completion time by physical id after a `CONTINUE` acknowledgement. A later
claim of that same id must have `claimedAt >=` its previous `completedAt`; time-travelling claims
fail without mutation. This history is simulation-thread state and is represented only through
existing/current value fields, not exposed as a live map.

Add these exact methods:

```java
public void validateCanAcknowledgeDisposition(
        StationProcessingDisposition expectedHead)
public StationProcessingDisposition acknowledgeDisposition(
        StationProcessingDisposition expectedHead)
```

Rules:

- require non-null input and require `expectedHead` to be the exact current FIFO-head instance;
- remove exactly that head once;
- for `CONTINUE`, remove the temporary completed-id lock only at acknowledgement, allowing a later
  station to claim the physical id again;
- for `CONSUME`, retain a permanent terminal-id lock so the physical id can never be claimed again;
- preserve the existing cumulative `long completedCount` and last-completed values when a
  disposition is acknowledged;
- record cumulative acknowledged-continuation and acknowledged-consume counts and the last
  acknowledged id/type in the value snapshot;
- repeated, out-of-order, unknown, active, or already-acknowledged operations fail without mutation.

Keep `dequeueDisposition()` as a deprecated compatibility method. It returns `Optional.empty()`
when the FIFO is empty; otherwise it captures the exact head and returns
`Optional.of(acknowledgeDisposition(head))`. Its changed postcondition is explicit: dequeueing
`CONTINUE` acknowledges downstream ownership and permits a later claim; dequeueing `CONSUME`
remains terminal. Production code in this branch must use `acknowledgeDisposition(...)`, never the
compatibility method.

The coordinator must increment the already-cumulative `long completedCount` on every completed
station visit rather than derive it from a unique-id set. Existing active and pending snapshot
entries remain value-only and ordered. Extend `StationProcessingSnapshot` with
cumulative acknowledgement counts and optional last-acknowledged id/type; do not expose live
claims, dispositions, totes, plans, or coordinator references.

### Pure route sequencing

`StationRouteContinuationSelector` exposes exactly:

```java
public Optional<StationType> nextStation(
        RouteRequirements routeRequirements,
        StationType completedStation)
```

It validates non-null input, requires the completed station to be required by the supplied route,
and returns the first required station strictly after it in the locked route order. It does not
select target ids or inspect mutable station state. No next station is a valid empty result at the
pure selector level; a production `CONTINUE` with no next station is an invariant failure in the
controller.

Do not modify or delegate this behavior to `OperationalRouteEntrySelector`: that type owns first
station selection for immutable scheduler evaluation, while continuation is a simulation-thread
post-processing concern.

### Target-resolution contract

`StationRouteContinuationDecision` is an immutable value with factories:

```java
public static StationRouteContinuationDecision continueTo(
        OperationalRouteDestination destination)
public static StationRouteContinuationDecision defer(String reason)
```

Exactly one of destination or a nonblank deferral reason is present.

`StationRouteContinuationTargetResolver` exposes:

```java
StationRouteContinuationDecision resolve(
        StationProcessingDisposition disposition,
        NotionalToteOrder order,
        StationType nextStation)
```

`OperationalStationRouteContinuationTargetResolver` is the only production implementation. Its
constructor receives the exact `AdaptingArea` and `AdaptingVisitFactory` used by station processing.
Use this exact constructor:

```java
public OperationalStationRouteContinuationTargetResolver(
        AdaptingArea adaptingArea,
        AdaptingVisitFactory adaptingVisitFactory)
```

It follows these rules:

- `ADAPTING`: derive the exact visit profile, validate sheet/service-centre/order-type continuity,
  call `area.selectBenchFor(profile)`, defer with its nonblank reason when rejected, and otherwise
  return that exact bench destination;
- `P2P`: require the exact retained assignment and validate physical id and service centre before
  returning `assignment.destination()`;
- any other next station fails explicitly.

Target resolution is non-mutating. It does not reserve a bench, enqueue a station arrival, mutate a
lease, or inspect route geometry. The eventual exact Adapting arrival target performs the existing
live capacity revalidation.

### Continued launch and routed payload

Add to `OperationalRouteLaunchRequestFactory`:

```java
public static OperationalRouteLaunchRequest continueTo(
        OperationalRouteLaunchRequest previousRequest,
        OperationalRouteDestination nextDestination)
```

It returns a new request retaining the exact previous `releaseRequest` instance and the supplied
next destination. Existing constructor validation continues to enforce direct P2P assignment
matching. Do not change release time, source, physical identity, sheet, order type, service centre,
pharmacy order, or assignment.

The continuation controller creates one new `RoutedPhysicalTote` containing that new launch
request, the disposition's exact `currentLoadPlan`, and the claim's exact existing `Tote` and
`RenderableObject`. This intentionally creates a new immutable per-leg routing envelope without
creating new physical or visual objects.

### Publication-state contract

Keep `WarehouseTransportPublisher.contains(PhysicalToteId)` for source compatibility and add:

```java
WarehouseTransportPublicationState publicationState(
        RoutedPhysicalTote routedTote)
```

`SimulationWorldWarehouseTransportPublisher` must retain, by physical id, the exact published
`Tote` and `RenderableObject`, not an old routing-envelope identity. Its result is:

- `UNPUBLISHED` when the physical id has never been published;
- `PUBLISHED_EXACT_OBJECTS` when id, tote, and renderable are the exact published objects;
- `PHYSICAL_ID_CONFLICT` when the id is known but either object differs.

`publish(...)` remains initial-publication only and rejects both published states. Existing
validation before world/render-list mutation remains unchanged.

Test publishers must implement the same semantic contract; do not add a production fallback that
treats `contains(id)` alone as exact-object proof.

### Continuation-controller protocol

`StationRouteContinuationController implements SimulationController` receives exact instances of:

- `StationProcessingCoordinator`;
- `StationProcessingOrderCatalog`;
- `MutableToteLoadPlanRegistry`;
- `DspRouteDeriver`;
- `StationRouteContinuationSelector`;
- `StationRouteContinuationTargetResolver`;
- `WarehouseRouteCatalog`;
- `OsrOutboundTransportQueue`;
- `WarehouseTransportPublisher`.

Use this exact constructor in the dependency order above:

```java
public StationRouteContinuationController(
        StationProcessingCoordinator coordinator,
        StationProcessingOrderCatalog orderCatalog,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        DspRouteDeriver routeDeriver,
        StationRouteContinuationSelector selector,
        StationRouteContinuationTargetResolver targetResolver,
        WarehouseRouteCatalog routeCatalog,
        OsrOutboundTransportQueue transportQueue,
        WarehouseTransportPublisher publisher)
```

It processes at most one FIFO head per update.

For a `CONSUME` head:

1. require the exact tote to be `HELD`, its lids closed, and the exact renderable invisible,
   proving `StationConsumedToteController` has presented it;
2. require no same physical id in the continuation transport queue;
3. prevalidate exact FIFO acknowledgement;
4. acknowledge the same head and record one terminal acknowledgement;
5. do not derive a route, change route state, or publish transport work.

If terminal presentation has not occurred yet, record a deterministic deferral and leave the head
unchanged. This makes controller ordering safe, although production composition registers consume
presentation before continuation.

For a `CONTINUE` head, perform all of these read-only checks in order:

1. require exact claim/disposition identity and `HELD`, visible physical presentation;
2. resolve the retained order by launch sheet and require order type and service centre to match
   the source-neutral release identity;
3. require the mutable load-plan registry entry to be the exact disposition `currentLoadPlan`;
4. derive fresh `RouteRequirements` from that exact order and select the next station after the
   claim's exact completed station;
5. reject no-next, unsupported MANUAL/MANUAL_MERGE, or P2P-as-completed cases;
6. resolve the exact next target; a resolver deferral records the head id/reason and stops;
7. resolve both current and next route definitions, require the current follower to remain on the
   completed destination's exact terminal segment, require no active machine reservation, and
   require the tote to remain `HELD`;
8. require publisher state `PUBLISHED_EXACT_OBJECTS`; unpublished or conflicting objects fail;
9. require the transport queue not to contain the physical id and to have capacity;
10. construct the next launch request and routed envelope and prevalidate coordinator
    acknowledgement.

After every check succeeds, the mutation boundary is exactly:

1. close the same tote's lids and retain `HELD`;
2. rebind the same route follower to the next definition's exact common entry segment, distance,
   and direction, then snap the tote to that route position;
3. enqueue the new exact routed envelope into `OsrOutboundTransportQueue`;
4. acknowledge the exact disposition head, making a `CONTINUE` physical id eligible for its later
   station claim;
5. record one success.

After prevalidation, route setters, queue enqueue, and acknowledgement must be mechanically
non-failing on the single simulation thread. Do not catch an invariant failure and continue with
partial state. A full transport queue, rejected Adapting selection, or terminal presentation not
yet applied is a normal deferral and must mutate nothing.

The controller never calls a station target, station-arrival queue, transport publisher's
`publish(...)`, in-flight registry, transfer machine, or lifecycle controller directly.

### Transport ingress re-entry

Update `WarehouseTransportIngressController` without adding a second ingress controller:

1. retain its existing route, follower-binding, duplicate in-flight, and capacity checks;
2. query `publicationState(head)`;
3. for `UNPUBLISHED`, call `publish(head)` as today;
4. for `PUBLISHED_EXACT_OBJECTS`, skip `publish(...)` and proceed with the exact new routed
   envelope;
5. for `PHYSICAL_ID_CONFLICT`, record a block with no mutation;
6. register the exact envelope in flight, dequeue the same transport head, then set the tote to
   `MOVING`;
7. process at most one head per update.

Extend the canonical `WarehouseTransportIngressControllerSnapshot` record with cumulative
initial-publication and exact-object-reentry counts. Their sum must equal
`successfulIngressCount`. Existing accessor names for head, last, block, capacity, and total count
remain source-compatible; update the focused tests that directly call the record constructor. No
production class outside `WarehouseTransportIngressController` constructs this snapshot.

Initial publication still happens exactly once. Re-entry never adds a second trackable object or
renderable, and a held continued tote cannot move while waiting in the bounded transport queue.

### Runtime composition and controller order

`DspStationRouteContinuationRuntimeFactory.create(...)` receives the exact dependencies listed for
the controller plus `SimulationWorld`. Validate every dependency before registering exactly one
continuation controller. It does not construct or register station machines, consume presentation,
transport ingress, transfer machines, or arrival controllers.

Use this exact factory signature:

```java
public DspStationRouteContinuationRuntime create(
        SimulationWorld simulationWorld,
        StationProcessingCoordinator coordinator,
        StationProcessingOrderCatalog orderCatalog,
        MutableToteLoadPlanRegistry loadPlanRegistry,
        DspRouteDeriver routeDeriver,
        StationRouteContinuationSelector selector,
        StationRouteContinuationTargetResolver targetResolver,
        WarehouseRouteCatalog routeCatalog,
        OsrOutboundTransportQueue transportQueue,
        WarehouseTransportPublisher publisher)
```

The current `DspWarehouseTransportRuntimeFactory` already has a package-private overload that
accepts a supplied `WarehouseTransportPublisher`. Step 3 makes that exact overload public. Keep the
existing public convenience overload unchanged; it continues to construct
`SimulationWorldWarehouseTransportPublisher` for callers that do not compose continuation. The
production continuation composition creates one publisher, supplies it to the public
publisher-taking warehouse overload, and supplies the same instance to the continuation factory.
Do not expose the live publisher through a runtime snapshot or create it independently in both
factories.

Production composition order is:

1. create one `SimulationWorldWarehouseTransportPublisher`, then create
   `DspWarehouseTransportRuntime` through the publisher-taking overload so launch hydration,
   ingress, transfer, and arrival controllers retain their established order;
2. create `DspStationProcessingRuntime`, registering claim controllers, domain completion
   controllers, and `StationConsumedToteController`;
3. create `DspStationRouteContinuationRuntime`, registering continuation last.

Consequences:

- a station completion can be presented/continued later in the same simulation update;
- transport ingress has already run, so a newly continued tote enters in-flight transport on the
  next update;
- a newly ingressed tote cannot move until the following tracked-object update;
- reconstruction, not mutable reset, creates fresh runtime state.

`DspStationRouteContinuationRuntime` exposes fresh value snapshots, `isClosed()`, and idempotent
`close()`. Close does not unregister controllers or mutate coordinator/transport state.

### Snapshot contract

`StationRouteContinuationControllerSnapshot` contains only immutable values:

- optional current head physical id and disposition type;
- optional selected next station and destination for the current head;
- optional blocked physical id and nonblank reason;
- cumulative continued and consumed-acknowledged counts;
- optional last handled physical id, disposition type, and next destination (destination empty for
  terminal consume).

The snapshot must not expose orders, route requirements, plans, claims, dispositions, totes,
renderables, route followers, mutable queues, target resolver, coordinator, or controller.

## Threading And Failure Rules

- All route derivation, live target selection, follower mutation, transport enqueue,
  acknowledgement, ingress, and station claim mutation run on the simulation thread.
- No route-continuation object enters scheduler snapshots or worker inputs.
- Expected capacity and Adapting selection closure are deferrals with complete state equality.
- Stale plan identity, changed order identity, invalid route sequence, missing/mismatched P2P
  assignment, missing topology, nonterminal current route state, publisher conflict, duplicate
  transport ownership, and coordinator mismatch are invariant failures before mutation.
- Do not retry an invariant failure by guessing another target or reconstructing identity.
- Same-thread validation followed by mutation is the transaction boundary. If an allegedly
  impossible post-mutation invariant fails, propagate it and stop; do not dequeue another item.
- Reset remains whole-runtime reconstruction.

## Explicit Non-Goals

- The deferred AV02 operational EMPTY allocation/release/end-to-end scenario.
- Scheduler-worker next-destination selection or new scheduler commands.
- Rerunning sticky P2P allocation, moving a committed assignment, or changing a line lease.
- Reserving Adapting capacity for the duration of physical transport.
- A new station-to-station topology, conveyor geometry, transfer machine, terminal sensor, or
  visual scene.
- Direct station-arrival enqueue, station-target invocation, or teleporting directly into P2P.
- Continuing `CONSUME`, reusing Adapting STORE/P2P inbound totes, or creating outbound P2P totes.
- P2P outbound dispatch, 32R, Exception, MANUAL, MANUAL_MERGE, lid-machine, short-pick, or NS-bag
  behavior.
- Route calibration, full-day execution, metrics, renderer timing, or visual polish.
- Renaming the established OSR-named source-neutral launch/transport compatibility types.

## Step 1: Make Disposition Acknowledgement Safe For Multi-Station Journeys

### Required change surface

Modify:

- `app/src/main/java/.../dsp/station/processing/StationProcessingCoordinator.java`;
- `app/src/main/java/.../dsp/station/processing/StationProcessingSnapshot.java`;
- `app/src/main/java/.../dsp/thirdparty/ThirdPartyStationProcessingTarget.java`;
- `app/src/main/java/.../dsp/adapting/AdaptingStationProcessingTarget.java`;
- `app/src/main/java/.../dsp/p2p/arrival/P2pStationProcessingTarget.java`;
- `app/src/test/java/.../dsp/station/processing/StationProcessingCoordinatorTest.java`;
- `app/src/test/java/.../dsp/station/processing/StationProcessingSnapshotTest.java`;
- `app/src/test/java/.../dsp/thirdparty/ThirdPartyStationProcessingTargetTest.java`;
- `app/src/test/java/.../dsp/adapting/AdaptingStationProcessingTargetTest.java`;
- `app/src/test/java/.../dsp/p2p/arrival/P2pStationProcessingTargetTest.java`;
- focused existing tests that call `dequeueDisposition()` and assert the old permanent lock.

Do not modify `StationProcessingTarget`, `StationArrivalClaimController`, station-domain
controllers, transport, routing, lifecycle, or runtime composition in this step.

### Behavioral specification

- An unacknowledged `CONTINUE` still blocks a duplicate claim.
- Acknowledging the exact FIFO-head `CONTINUE` permits the same physical id to be claimed at a
  different destination with a later/nondecreasing claim time.
- A later claim whose time precedes that physical id's prior completion is rejected with complete
  state equality.
- `StationProcessingCoordinator.validateCanEvaluateClaim(RoutedPhysicalTote)` validates only the
  coordinator's ownership eligibility: the routed tote is non-null and its physical id has no
  active claim, unacknowledged disposition, or acknowledged terminal consumption. It deliberately
  performs no claim-time chronology check because target evaluation has no simulation-time input.
- `StationProcessingCoordinator.validateCanClaim(RoutedPhysicalTote, Duration)` retains full input,
  ownership, and previous-completion chronology validation. Each production target calls
  `validateCanEvaluateClaim(...)` from `evaluate(...)`, then calls the full
  `validateCanClaim(..., claimedAt)` in `accept(...)` before its first local station mutation.
  `StationProcessingCoordinator.claim(...)` repeats the full validation before coordinator
  mutation.
- Do not use `Duration.ZERO` as an evaluation sentinel and do not add time to the public
  `StationProcessingTarget.evaluate(...)` contract.
- That second claim and completion add a second cumulative completion without erasing the first
  completion history.
- Acknowledged `CONSUME` remains permanently unclaimable.
- A non-head or merely equal reconstructed disposition cannot be acknowledged.
- Failed acknowledgement and failed repeated claim/completion preserve every active, pending,
  terminal, count, and last-value field.
- Compatibility `dequeueDisposition()` delegates to the same acknowledgement rules.
- Old snapshots remain immutable after acknowledgement and a later claim.

### Decision-complete test contract

`StationProcessingCoordinatorTest` must exercise the public coordinator boundary:

- Third Party claim -> `CONTINUE` -> acknowledge -> Adapting claim of the same physical id ->
  `CONTINUE`, asserting exact per-leg claims and cumulative count two;
- a post-acknowledgement claim before the prior completion time is rejected without mutation;
- pending `CONTINUE` duplicate claim rejection before acknowledgement;
- terminal `CONSUME` rejection both before and after acknowledgement;
- wrong exact head, out-of-order head, repeated acknowledgement, and empty acknowledgement with
  complete state equality;
- compatibility dequeue for one `CONTINUE` and one `CONSUME`.

`StationProcessingSnapshotTest` proves cumulative counts, acknowledgement counts, last values,
ordered active/pending values, old-snapshot stability, and absence of live objects. Existing
boundary scenario tests that intentionally dequeue dispositions must update only their post-dequeue
claimability expectation; station behavior remains unchanged.

Each of `ThirdPartyStationProcessingTargetTest`, `AdaptingStationProcessingTargetTest`, and
`P2pStationProcessingTargetTest` must seed an acknowledged prior `CONTINUE` for the candidate
physical id and prove:

- `evaluate(...)` permits the otherwise-valid continued candidate without mutating coordinator or
  local station state;
- `accept(...)` with a claim time before the prior completion fails before visit/payload/queue
  mutation and preserves complete coordinator and local target state;
- `accept(...)` with a later/nondecreasing claim time succeeds through the existing real target
  boundary.

### Expected output

The coordinator can own several ordered station visits for one physical journey without allowing
overlap, premature reclaim, duplicate completion, or reuse after terminal consumption.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinatorTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingBoundaryScenarioTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pStationProcessingTargetTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Acknowledge station dispositions safely`

## Step 2: Define Route Sequence, Exact Next Targets, And Continued Requests

### Required change surface

Create under `...dsp.station.continuation`:

- `StationRouteContinuationDecision.java`;
- `StationRouteContinuationSelector.java`;
- `StationRouteContinuationTargetResolver.java`;
- `OperationalStationRouteContinuationTargetResolver.java`;
- matching focused tests.

Modify:

- `OperationalRouteLaunchRequestFactory.java`;
- `OperationalRouteLaunchRequestTest.java` or its existing factory-focused test.

Do not modify station state, coordinator, transport queue, route follower, publisher, ingress, or
runtime composition in this step.

### Behavioral specification

- Pure sequencing covers every required/non-required combination in locked order and never returns
  the completed station again.
- A completed station absent from requirements, OSR/AV02/DISPATCH completion, and null input fail.
- No-next remains `Optional.empty()` at selector level.
- Adapting selection returns the exact selected bench; rejection is a non-mutating deferral.
- P2P selection returns only the exact pinned assignment destination and rejects missing or stale
  identity/service-centre assignment.
- MANUAL and MANUAL_MERGE target resolution fail explicitly.
- Continued requests retain the exact release-request and assignment instances while changing only
  destination; a mismatched P2P destination continues to fail through the record invariant.

### Decision-complete test contract

Create:

- `StationRouteContinuationDecisionTest` for exact factory invariants and immutable values;
- `StationRouteContinuationSelectorTest` for the full ordered matrix, invalid completed station,
  unsupported station position, and no-next;
- `OperationalStationRouteContinuationTargetResolverTest` using a real `AdaptingArea` and
  `AdaptingVisitFactory` for accepted bench, full-area deferral, deterministic bench selection,
  exact P2P assignment, and every named mismatch;
- extend `OperationalRouteLaunchRequestTest` for `continueTo(...)`, exact retained release request,
  source/pharmacy/time/assignment continuity, and invalid P2P destination.

One full-area Adapting rejection is representative of all `AdaptingBenchSelection` rejection
reasons; exhaustive area policy remains in `AdaptingAreaAdmissionTest`.

### Expected output

Continuation can determine the next station and exact live target without scheduler mutation or
route-state mutation.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationDecisionTest --tests online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationSelectorTest --tests online.davisfamily.warehouse.sim.dsp.station.continuation.OperationalStationRouteContinuationTargetResolverTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Resolve station continuation targets`

## Step 3: Admit Already-Published Exact Totes Back Into Transport

### Required change surface

Create:

- `WarehouseTransportPublicationState.java`;
- its focused enum/contract test if needed by local convention.

Modify:

- `WarehouseTransportPublisher.java`;
- `SimulationWorldWarehouseTransportPublisher.java`;
- `WarehouseTransportIngressController.java`;
- `WarehouseTransportIngressControllerSnapshot.java`;
- `DspWarehouseTransportRuntimeFactory.java` only to make its existing publisher-taking overload
  public while retaining the current convenience overload;
- `WarehouseTransportIngressControllerTest.java`;
- `WarehouseTransportIngressControllerSnapshotTest.java`;
- `DspWarehouseTransportRuntimeFactoryTest.java`;
- `DspWarehouseTransportRoutingScenarioTest.java` only for publisher test-double compilation and
  the exact re-entry regression named below.

Do not modify station-processing, route selection, coordinator, arrival, transfer routing,
`SimulationWorld`, `Tote`, or `RouteFollower` APIs.

### Behavioral specification

- An unpublished head follows the existing publish/register/dequeue path and increments initial
  publication plus total ingress.
- A head with the same published id, exact tote, and exact renderable skips publication,
  registers/dequeues the new envelope, changes `HELD` to `MOVING`, and increments re-entry plus
  total ingress.
- A same-id payload with a different tote or renderable is blocked with queue, follower, motion,
  world, renderables, in-flight state, and counts unchanged.
- Existing route-binding, in-flight-capacity, duplicate-active, publisher-failure, FIFO, and
  one-per-update behavior remains unchanged.
- `publish(...)` still rejects a second call even for exact objects.
- Snapshot invariants require initial count plus re-entry count equals total count.
- The publisher-taking warehouse runtime factory overload uses the exact supplied publisher; the
  convenience overload remains behaviorally and source compatible.

### Decision-complete test contract

`WarehouseTransportIngressControllerTest` adds separate cases for unpublished, exact-object
re-entry, conflicting tote, conflicting renderable, full in-flight capacity before re-entry, and
held motion until successful ingress. Assert publisher invocation count, exact registered envelope,
source dequeue ordering, and no second world/renderable append.

`DspWarehouseTransportRuntimeFactoryTest` proves the real publisher reports all three states and
retains existing `contains(...)` behavior. `DspWarehouseTransportRoutingScenarioTest` needs one
bounded regression that completes an initial arrival, constructs a second routing envelope around
the same physical objects, rebinds it to common entry, and proves a second in-flight/arrival leg
without duplicate publication. It does not select a station continuation target in this step.

Update every anonymous/test publisher explicitly. Do not use a permissive default method.

### Expected output

The established ingress becomes source-neutral across first publication and same-object route
re-entry while continuing to reject physical identity conflicts.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRoutingScenarioTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Support exact tote transport reentry`

## Step 4: Continue Or Terminally Acknowledge One Disposition

### Required change surface

Create:

- `StationRouteContinuationController.java`;
- `StationRouteContinuationControllerSnapshot.java`;
- `StationRouteContinuationControllerTest.java`;
- `StationRouteContinuationControllerSnapshotTest.java`.

Use focused private fakes only for the target resolver and publisher. Use real coordinator, order
catalog, load-plan registry, route deriver, route catalog, transport queue, tote, renderable, and
route follower values. Do not add production test hooks.

Do not modify station-domain controllers, lifecycle controllers, transport ingress, transfer
routing, arrival queues, or runtime factories in this step.

### Behavioral specification

- Empty update is idle and clears a prior normal block.
- One update handles at most the exact FIFO head.
- Presented `CONSUME` is acknowledged terminally without any route/transport mutation.
- Unpresented `CONSUME` defers without mutation.
- Permitted `CONTINUE` follows the exact prevalidation and mutation sequence in Locked
  Architecture, retains exact physical objects/current plan/release request/assignment, and
  acknowledges only after transport enqueue.
- Full transport or target deferral preserves complete pre-update state including follower segment,
  distance, direction, lids, motion, plan, FIFO, and acknowledgement history.
- Every named stale/invariant case fails before mutation.
- Snapshots are fresh immutable values and do not expose live objects.

### Decision-complete test contract

`StationRouteContinuationControllerTest` must include:

1. empty update and one-head-per-update FIFO;
2. exact Third Party -> P2P continuation retaining one source-neutral release request, assignment,
   tote, renderable, follower, and replacement plan while creating a new routing envelope;
3. exact Third Party -> Adapting continuation using the resolver-selected bench;
4. exact Adapting COLLECT -> P2P continuation;
5. presented Adapting STORE/P2P `CONSUME` acknowledgement with permanent claim rejection and no
   transport output;
6. unpresented consume, full transport, and Adapting resolver deferral with complete state equality;
7. stale registered plan, missing order, order type/service-centre mismatch, invalid/no-next route,
   unsupported manual next step, missing/mismatched P2P assignment, missing current/next route,
   wrong current terminal segment, non-held tote, invisible continue renderable, active machine
   reservation, unpublished tote, publisher object conflict, and duplicate transport id.

For failure cases, capture a reusable value/object-identity state containing coordinator snapshot,
pending exact heads, registry plan, transport snapshot/head, follower binding, tote motion/lids,
renderable visibility/identity, target resolver calls, publisher state/calls, and controller
snapshot. Each case asserts every field unchanged except the controller's block diagnostics for
normal deferrals. Representative null constructor inputs may be table-driven; every semantic case
above is individually required.

`StationRouteContinuationControllerSnapshotTest` proves all optional/count invariants, immutable
old snapshots, last terminal destination emptiness, last continuation destination presence, and no
live references.

### Expected output

One controller owns the atomic disposition-to-transport handoff and terminal FIFO drainage without
duplicating station, lifecycle, or transport responsibilities.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerTest --tests online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerSnapshotTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Continue completed station totes`

## Step 5: Compose The Production Continuation Runtime

### Required change surface

Create:

- `DspStationRouteContinuationRuntime.java`;
- `DspStationRouteContinuationRuntimeFactory.java`;
- `DspStationRouteContinuationRuntimeTest.java`;
- `DspStationRouteContinuationRuntimeFactoryTest.java`.

Do not modify `DspStationProcessingRuntimeFactory` or make further changes to
`DspWarehouseTransportRuntimeFactory` beyond Step 3's approved public overload. The composition
root supplies their shared exact dependencies and invokes the factories in the locked order.

### Behavioral specification

- Validate every dependency before registering a controller.
- Register exactly one continuation controller and return its runtime.
- Use the exact supplied coordinator, order catalog, load-plan registry, selector, resolver, route
  catalog, transport queue, and publisher; do not copy mutable owners.
- Runtime snapshots are fresh values; close is idempotent and non-mutating.
- When composed after station processing, recording-world order is claimants, domain completion,
  consume presentation, then continuation. Existing earlier warehouse controllers remain earlier.
- A fresh reconstructed runtime starts with fresh controller diagnostics while external state is
  whatever fresh composition supplied; no reset method is added.

### Decision-complete test contract

`DspStationRouteContinuationRuntimeFactoryTest` uses a private recording `SimulationWorld` and
asserts validation-before-registration for every null dependency, exact registration identity,
single registration, and order when invoked after a minimal real `DspStationProcessingRuntime`.
No controller-list accessor may be added to production `SimulationWorld`.

`DspStationRouteContinuationRuntimeTest` drives one continuation and one terminal acknowledgement
through `SimulationWorld.update(...)`, asserts fresh value snapshots, verifies idempotent close has
no behavioral effect, and reconstructs a second runtime to prove reset-by-reconstruction.

### Expected output

Production composition can add route continuation to the established station and warehouse
runtimes without changing either factory's ownership boundary.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactoryTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Compose station route continuation runtime`

## Step 6: Prove Mixed Same-Tote Continuation Through Real Boundaries

### Required change surface

Create only:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/station/continuation/DspStationRouteContinuationScenarioTest.java`.

Use private fixture helpers. Do not add a test-only handoff, direct station-target call, direct
station-arrival enqueue after initial setup, alternate route engine, or production fixture class.

### Scenario fixture

Construct a fresh fixture per test with:

- one `SimulationWorld`, renderable list, shared `StationProcessingCoordinator`, order catalog,
  mutable load-plan registry, route deriver, and production continuation resolver;
- real Third Party and Adapting areas/controllers/processing targets;
- real P2P station-processing target and tipper-input boundary where the scenario reaches P2P;
- one shared source-neutral launch/transport queue, production publisher, route catalog, in-flight
  registry, terminal arrival registry/controller, and production station/continuation runtimes;
- explicit small common-entry route topology and deterministic terminal `DetectionEvent`s;
- explicit OSR and AV02 source-neutral release identities and exact pinned P2P assignments.

Arrivals after every route leg must occur through transport ingress, in-flight registration,
terminal detection, and the exact station-arrival FIFO. Domain completion must use real Third Party
and Adapting controllers. Do not advance progress by direct coordinator completion or direct target
acceptance.

### Behavioral scenarios

1. `shouldContinueAssociatedThroughThirdPartyAdaptingAndP2pArrival`
   - start one OSR ASSOCIATED tote whose route requires Third Party, Adapting COLLECT, and P2P;
   - drive initial Third Party arrival/claim and real completion, then continuation through common
     transport to the selected exact Adapting bench;
   - drive real COLLECT replacement and continue through transport to the pinned P2P destination;
   - assert observed station order exactly Third Party -> selected Adapting bench -> pinned P2P;
   - assert one physical id, release-request identity/source/pharmacy/time, tote, renderable,
     follower, and assignment throughout, with a new routed envelope per leg and exact successive
     replacement plans;
   - assert publisher/world/renderables contain the physical objects once, ingress reports one
     initial publication and two re-entries, and coordinator cumulative completion/acknowledgement
     history is ordered.

2. `shouldContinueAv02EmptyFromAdaptingCollectToPinnedP2p`
   - start one already allocated AV02 EMPTY tote at Adapting COLLECT with an active exact P2P
     assignment;
   - drive the real COLLECT completion and replacement plan;
   - prove source `AV02`, absence of fabricated OSR manifest ownership, exact physical/sheet/
     pharmacy identity, and exact replacement plan survive transport re-entry and P2P arrival;
   - assert no scheduler rerun, lease mutation, second publication, outbound tote, output sheet, or
     bag side effect.

3. `shouldConsumeAdaptedStoreAndP2pWithoutContinuation`
   - use one OSR ADAPTED Third Party -> Adapting STORE journey and one independently arrived direct
     P2P journey;
   - prove Third Party `CONTINUE` reaches Adapting, then STORE lifecycle completion produces
     presented/acknowledged `CONSUME` with no later route leg;
   - complete the P2P claim through the real tipper completion listener and prove the same terminal
     behavior;
   - both ids remain permanently unclaimable and hidden/held, while no inbound object is reused as
     an outbound tote.

4. `shouldRetainDispositionFifoUnderContinuationBackpressure`
   - complete mixed Third Party `CONTINUE`, terminal `CONSUME`, and Adapting `CONTINUE` work in a
     known order;
   - close transport capacity or Adapting selection for the FIFO head and assert later
     dispositions do not overtake it, even if their destinations are open;
   - restore the exact blocked dependency and assert one disposition is handled per update in
     original completion order;
   - assert every blocked retry preserves exact plan, follower, presentation, route envelope,
     publisher, lifecycle, and station state.

### Decision-complete test contract

Every scenario drives controller transitions with explicit simulation time and stable terminal
events. Use a bounded `advanceUntil(BooleanSupplier, SimulationWorld, double, int)` only for real
machine/route progress; the bound is a failure guard and no assertion may depend on update count.

Capture a reusable `ContinuationState` before every blocked or terminal action containing station
FIFO snapshots, coordinator snapshot and exact pending values, plan identity, lifecycle snapshot,
Adapting/Third Party/P2P state, transport queue, in-flight/arrival state, follower segment/distance/
direction, tote motion/lids, renderable identity/visibility/list occurrence count, publisher state,
assignment, and outbound allocation state. Each negative assertion must compare all fields except
the explicitly allowed block diagnostics.

The scenario class owns cross-boundary ordering, atomic handoff, identity continuity, and duplicate
publication protection. Constructor nulls, every route-requirement matrix, every publisher conflict,
and equivalent target-rejection reasons remain in Steps 1-5 and are not repeated.

### Expected output

The branch proves production same-tote continuation across all supported station families and both
source identities, while terminal inbound journeys remain consumed and independent of outbound P2P
totes.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationScenarioTest
```

### User verification

No additional user verification is required for this step. This branch adds no debug-scene
geometry; visual station-to-station topology remains explicitly deferred.

Proposed commit message: `Prove station route continuation`

## Step 7: Regression And Branch Closure

This step is owned by the planning/review model and the user after Steps 1-6 are accepted. Do not
start the deferred operational EMPTY proof or full-day execution during closure.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Run the focused regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.continuation.* --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

Then run the complete suite:

```powershell
.\gradlew test
```

No visual run is required because the branch deliberately adds no station-to-station geometry and
the scenario proves exact common-entry re-entry. Existing visual scenes must remain
source-compatible. If implementation changes a debug scene despite this plan, stop and revise the
plan before accepting that change.

### End-of-feature architecture review

Review the actual feature diff and report PASS, FAIL, or UNPROVEN for each item with concrete
class/method/control-flow evidence:

- one global disposition FIFO is handled without overtaking;
- only exact downstream acceptance acknowledges a `CONTINUE` and permits its later station claim;
- `CONSUME` is presented before acknowledgement and remains terminally unclaimable;
- route order is derived from immutable requirements and the exact completed station without a
  mutable progress ledger or scheduler-worker mutation;
- Adapting target selection uses the real area policy and P2P uses only the committed exact
  assignment;
- continued requests preserve the exact source-neutral release request and current replacement
  plan;
- every leg preserves the same physical tote, renderable, and route-follower objects;
- re-entry uses the existing common route entry and real ingress/in-flight/terminal-arrival path;
- initial publication occurs once and exact-object re-entry never appends duplicate world/render
  objects;
- conflicting same-id physical objects fail without mutation;
- controller registration preserves station completion -> consume presentation -> continuation,
  with transport ingress processing new continuation work on a later update;
- all live mutation remains on the simulation thread and snapshots remain value-only;
- P2P assignment/lease, station domain machines, lifecycle mutation, and outbound tote allocation
  retain their established owners;
- no direct station enqueue, direct P2P teleport, second route engine, mutable reset, Exception/
  manual behavior, new topology, debug geometry, or deferred operational EMPTY proof was added;
- identify every changed production file outside the plan's required surfaces and determine whether
  it is necessary.

### Documentation closure

After user verification and architecture review are green, perform documentation closure as a
bounded reconciliation task. Do not introduce new architectural decisions or broaden programme
scope. If the verified implementation, this plan, and programme documents conflict materially,
stop and report the inconsistency.

- mark this plan `complete and verified` and record the final implemented contract;
- update current-programme status in `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and only stale current-position/reading-order text in
  `docs/codex-instructions.md`;
- update the processing/continuation interlude in `docs/machines/phase-1-stations-roadmap.md`;
- mark `feature/dsp-station-route-continuation` complete and merged/pending merge according to the
  actual repository state;
- record the deferred operational EMPTY end-to-end proof as the next separately planned branch;
- keep full-day execution/metrics after that proof;
- retain station-to-station visual topology, outbound dispatch/32R, Exception, and manual handling
  as explicit deferrals.

Proposed commit message: `Complete station route continuation`

## Final Implemented Contract

Steps 1-6 are implemented. Step 7 focused regression and complete-suite verification were completed
by the user, and the end-of-feature architecture review reported `PASS` for every review item with no
`FAIL` or `UNPROVEN` findings.

- A station owns a physical tote until it publishes one exact disposition.
- `CONSUME` is presented terminally, acknowledged once in FIFO order, and can never be reclaimed.
- `CONTINUE` remains locked against a second claim until the exact next routed envelope has been
  accepted by the bounded transport queue and the exact disposition is acknowledged.
- Route order is pure and monotonic: Third Party may continue to Adapting or P2P; Adapting COLLECT
  may continue to P2P; Adapting STORE and P2P consume.
- Adapting target selection uses the live real area policy; P2P destination is the exact committed
  sticky assignment.
- Each continued leg carries a new immutable routing envelope around the same release request,
  current replacement load plan, tote, renderable, and route follower.
- The existing common warehouse entry, ingress, in-flight registry, transfer decisions, terminal
  sensors, and station-arrival queues own every onward transport leg.
- Initial physical publication occurs once. Exact-object re-entry is accepted without duplicate
  world/renderable mutation; same-id conflicting physical objects are rejected.
- All decisions and mutation remain simulation-thread-owned; scheduler workers receive no live
  continuation state.
- P2P consumes inbound totes, and independent outbound tote allocation/dispatch remains a separate
  physical journey and later feature.

## Post-feature Direction

- The operational EMPTY end-to-end proof is active planned work on
  `feature/dsp-operational-empty-end-to-end-proof`, using the completed AV02 allocation and station
  route-continuation boundaries. Its decision-complete plan is
  `docs/scheduler/dsp-operational-empty-end-to-end-proof-plan.md`.
- Full-day execution and metrics follow that proof, using loaded 12N volumes and the explicitly
  uncalibrated profile.
- Station-to-station visual topology, outbound dispatch/32R, Exception handling, and MANUAL/
  MANUAL_MERGE handling remain explicit deferrals.
