# DSP Operational EMPTY End-To-End Proof Plan

Branch: `feature/dsp-operational-empty-end-to-end-proof`

Status: complete and verified; pending merge to `master`.

Verified closure:

- the focused Step 5 regression set and complete Gradle test suite are green by user verification;
- the end-of-feature architecture review returned PASS for every review item, with no FAIL or
  UNPROVEN result;
- the implementation adds only
  `app/src/test/java/online/davisfamily/warehouse/sim/dsp/av02/DspAv02OperationalAllocationScenarioTest.java`;
  no production source changes were made;
- the feature branch is complete and verified but is not yet merged to `master`.

## Purpose

Prove that the already-implemented production boundaries form one coherent operational path for
logical `EMPTY` work:

```text
authorized/dependency-ready logical EMPTY
    -> bounded AV02 inbound PRE_P2P allocation
    -> shared OSR/AV02 operational ranking and release
    -> launch hydration and warehouse transport
    -> real Third Party or Adapting processing where required
    -> source-neutral station continuation
    -> exact pinned P2P arrival and actual tipper completion
    -> terminal inbound CONSUMED_AT_P2P lifecycle
    -> independent P2P-created outbound tote allocation
```

This is a proof branch, not a new runtime-mechanism branch. Repository inspection confirms that the
production allocation, release, routing, processing, continuation, lifecycle, bag-planning, and
outbound-allocation APIs required by the deferred AV02 acceptance catalogue now exist. The feature
added one integration scenario class and made no production-code changes.

The proof closes the last physical-work prerequisite before a separately planned full-day execution,
metrics, and inspection branch. It does not claim calibrated throughput and does not introduce a
full-day runner.

## Required Reading Before Implementation

Read, in order:

1. `docs/codex-instructions.md` and `docs/codex-context.md`;
2. this complete plan;
3. `docs/scheduler/dsp-station-processing-boundary-plan.md`;
4. `docs/scheduler/dsp-station-route-continuation-plan.md`;
5. `docs/scheduler/dsp-av02-operational-allocation-plan.md`, especially Steps 8-9, Step 13, and
   `Deferred Operational EMPTY End-To-End Proof`;
6. `docs/scheduler/dsp-operational-route-target-integration-plan.md`;
7. `docs/scheduler/dsp-osr-outbound-route-launch-plan.md`;
8. `docs/scheduler/dsp-warehouse-transport-routing-plan.md`;
9. `docs/scheduler/dsp-p2p-sticky-line-leases-plan.md` and
   `docs/scheduler/dsp-deadline-aware-elastic-line-allocation-plan.md`;
10. `docs/scheduler/dsp-outbound-tote-allocation-plan.md`;
11. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`, especially Sections 7-9;
12. the exact production and test files named by the selected step.

Before executing any step, record `git status --short` and stop if unrelated work overlaps the
single test file owned by this plan. The current branch was created from commit `fd671f0`, where
station route continuation is already merged into `master`.

## Locked Scope And Change Surface

Create only:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/av02/DspAv02OperationalAllocationScenarioTest.java`.

Do not modify any production source, existing test, debug scene, fixture utility, Gradle file, or
dataset. If implementing the four scenarios exposes a production defect or a missing public
composition boundary, stop and report the exact gap. Do not repair or redesign production code
under this proof plan.

All helpers remain private nested values/classes in the new scenario test. In particular, do not
move a combined fixture into a production package and do not add test hooks to `SimulationWorld`,
station runtimes, transport, P2P, lifecycle, bagging, or outbound allocation.

## Existing Production Boundaries To Use Unchanged

### AV02 allocation and source identity

- Build allocation decisions through `Av02AllocationSnapshotFactory` from fresh
  `WarehouseSchedulerSnapshot`, `DspSupplySnapshot`, `Av02InventorySnapshot`, and
  `PhysicalToteLifecycleSnapshot` values.
- Apply the selected command through `Av02AllocationController`; do not call
  `Av02ToteLifecycleController.allocateFor(...)`, `Av02PhysicalToteInventory.store(...)`, or
  `MapBackedToteLoadPlanRegistry.putLoadPlan(...)` to manufacture scenario progress.
- Use `DeterministicAv02PhysicalToteIdAllocator`. Fresh fixtures must allocate `av02-000001` first
  and, after capacity is released, `av02-000002` second.
- AV02 inventory capacity is exactly one. EMPTY remains absent from `OsrPhysicalInventory` and
  `InboundToteManifestCatalog` throughout.

### One operational release boundary

- Compose the real runtime through
  `DspOperationalReleaseRuntimeFactory.createElasticWithAv02(...)` with a
  `SynchronousOperationalReleaseEvaluationSource` and the real deadline-aware elastic scheduler.
  Synchronous evaluation is the established deterministic production fallback; this proof must not
  use sleeps or re-prove worker scheduling.
- Supply the exact `OsrOutboundRouteLaunchTargetRegistry` shared by OSR and AV02. Every configured
  Third Party, Adapting, and P2P destination used by the fixture has both source adapters.
- Drive `DspOperationalReleaseRuntime.controller()` from the fixture's single
  `SimulationWorld`/`SimulationContext`. Do not apply `ReleasePhysicalToteFromAv02Command` or an OSR
  command directly.
- At most one command may be applied per completed evaluation. A full shared launch queue is a
  normal deferral and must not mutate either source, the lifecycle ledger, load plans, leases, or
  assignments.

### Hydration, transport, station processing, and continuation

- Use `OsrOutboundRouteLaunchController`, `LoadPlanOsrOutboundToteHydrator`,
  `RouteBoundDetachedOutboundToteFactory`, `WarehouseTransportIngressController`,
  `WarehouseTransportInFlightRegistry`, `WarehouseTransportArrivalController`, terminal detection,
  and real `StationRoutedToteArrivalQueue` instances.
- Initial launch must enter through the shared launch queue. Continued legs must enter through the
  same `OsrOutboundTransportQueue` and common-entry transport path. Never enqueue a station arrival
  directly, call a station-processing target directly, or construct the next routed envelope in the
  test.
- Compose one shared `StationProcessingCoordinator` through
  `DspStationProcessingRuntimeFactory`. Use real `ThirdPartyStationProcessingTarget`,
  `AdaptingStationProcessingTarget`, `P2pStationProcessingTarget`,
  `ThirdPartyStationProcessingController`, and `AdaptingStationProcessingController` instances.
- Compose `DspStationRouteContinuationRuntimeFactory` with the same coordinator, order catalogue,
  mutable load-plan registry, `DspRouteDeriver`, live `AdaptingArea`, route catalogue, transport
  queue, and exact-object publisher.
- Third Party and Adapting COLLECT must replace the immutable `ToteLoadPlan` only through their real
  controllers. The continuation must carry the exact replacement instance. P2P must complete only
  through the actual `ToteTrackTipperFlowController` completion callback wrapped by
  `StationProcessingP2pToteCompletedListener` and
  `OperationalLifecycleP2pToteCompletedListener`.

### Sticky P2P assignment

- Use five real `P2pLineDefinition` values and the existing elastic allocation runtime. Every
  P2P-required AV02 release must leave AV02 with one committed exact
  `P2pPhysicalToteAssignment`, even when its first station is Third Party or Adapting.
- Station continuation and P2P arrival consume only that committed assignment. The fixture must not
  call `P2pLineLeaseRegistry.acquireLease(...)` or `commitAssignment(...)` for the EMPTY tote being
  proved.
- P2P arrival revalidates physical id, service centre, destination, and assignment; it never selects
  a different line.

### Inbound completion and independent outbound work

- Actual tipper completion must terminate the AV02 tote's exact `PRE_P2P` assignment with
  `CONSUMED_AT_P2P` and transition that same physical record to
  `PhysicalToteLifecycleState.CONSUMED_AT_P2P`.
- The consumed AV02 tote must remain held, closed-lid, and invisible; it is never reused by outbound
  allocation.
- For the outbound half of the proof, use a real `StoredBagReceiver`, a `BagPlanningResult` whose
  planned pack trace retains the AV02 source sheet/physical-id provenance, and
  `OutboundToteAllocationController`/`OutboundToteAllocator`. Introduce the completed runtime bag
  through the receiver reservation/begin/complete contract, not by calling allocator mutation
  directly.
- `DeterministicOutboundToteIdSource` must create an id with the `outbound-` family that differs from
  every `av02-` and `osr-` id. `OutputSheetAllocator` owns the outbound output sheet; AV02 does not.

## Scenario Fixture Contract

Every test method creates a fresh private `ScenarioFixture`; no mutable fixture state is shared
between tests. Follow the private fixture structure of
`DspAv02OperationalRuntimeTest.CombinedRuntimeFixture` for mixed release and
`DspStationRouteContinuationScenarioTest.ScenarioFixture` for physical routing. Reuse concepts, not
test classes or helpers.

The fixture owns:

- one `SimulationWorld`, one authoritative `SimulationContext`, and explicit monotonically
  increasing simulation time;
- one `DspSchedulerRuntimeState` whose snapshots supply allocation, operational release, dependency
  readiness, and workload;
- one shared `PhysicalToteLifecycleLedger`, `Av02PhysicalToteInventory` with capacity one,
  `OsrPhysicalInventory`, `InboundToteManifestCatalog`, and
  `MapBackedToteLoadPlanRegistry`;
- one `AdaptedLineStore`, one real Adapting bench/area/controller, and one real Third Party
  area/controller using zero or short deterministic processing durations;
- five P2P line definitions and one real deadline-aware elastic allocation runtime; only the one
  selected exact line needs a physical tipper/input installation in the scenario;
- one bounded shared launch queue, one bounded transport queue, one in-flight registry, one
  source-neutral publisher, one route catalogue, and arrival queues for the exact Third Party,
  Adapting, and five P2P destinations;
- one station-processing runtime and one route-continuation runtime using the same coordinator;
- one `StoredBagReceiver`, `OutboundToteAllocator`, and outbound allocation controller for the
  selected P2P line;
- immutable state-capture records described below.

Use service centre `104` at priority `999` for current work and service centre `108` at priority
`998` for later authorized work. Use these exact logical identities and source order:

- `empty-direct-104`, sheet 1, pharmacy `pharmacy-104-a`, sequence 1, direct P2P;
- `empty-third-party-104`, sheet 1, pharmacy `pharmacy-104-a`, sequence 2, Third Party then P2P;
- `empty-adapted-104`, sheet 1, pharmacy `pharmacy-104-b`, sequence 3, Adapting COLLECT then P2P;
- `osr-full-104`, sheet 1, sequence 4, an OSR FULL_PACK physical manifest;
- `osr-associated-104`, sheet 1, sequence 5, an OSR ASSOCIATED physical manifest;
- `empty-direct-108`, sheet 1, pharmacy `pharmacy-108-a`, sequence 1, direct P2P.

Use `osr-` physical IDs for OSR manifests and `outbound-` IDs for outbound totes. Every EMPTY order
contains exactly one pharmacy. Third Party and Adapting collection cases each add one deterministic
pack. The adapted case has one exact `PreparedLineKey`; it starts unresolved. Use a prepared
`DspOrderItem` from source sheet `prepared-empty-adapted-104` whose target order and line reference
match `empty-adapted-104`. Establish its `AdaptedLineStore` record/readiness by submitting a real
`AdaptingVisit.store(...)` through `AdaptingArea.submitVisit(...)`, starting the selected real bench,
and letting `AdaptingAreaController` apply the completed visit to the shared
`DspSchedulerRuntimeState`. This is prerequisite-domain input preparation, not a routed EMPTY-tote
handoff. Do not insert a prepared key into the scheduler snapshot, call
`DspSchedulerRuntimeState.addPreparedLineKey(...)`, call `AdaptedLineStore.stage(...)`, or put a
collected pack into the EMPTY tote plan directly.

Wire allocation command production exactly as the focused controller test does: keep a private
monotonic allocation snapshot sequence and a private one-shot
`AtomicReference<Optional<AllocateEmptyToteAtAv02Command>>`. A fixture method builds one fresh
snapshot with `Av02AllocationSnapshotFactory`, stores that snapshot's command in the reference, and
then advances the world. `Av02AllocationController` receives
`() -> commandReference.getAndSet(Optional.empty())` and a fresh-snapshot supplier using the same
current live state. Do not retain or resubmit a previously consumed command.

The fixture may create OSR manifests, startup inventory, product master data, route geometry,
station definitions, and initial logical orders directly because those are input state. Once a
scenario action begins, all progress must use the production controllers and queues listed above.

### Controller registration and progression

Register controllers so an update observes the established ownership sequence:

1. AV02 allocation command application;
2. operational release evaluation/application;
3. launch hydration, transport ingress, and transport arrival;
4. P2P tipper flow and tipper-input dispatch;
5. station claim controllers, domain completion controllers, and consume presentation through
   `DspStationProcessingRuntimeFactory`;
6. route continuation after consume presentation;
7. outbound allocation only when the scenario deliberately supplies a completed bag.

One update may advance more than one independent controller, but assertions target ownership events,
not update counts. Use a private
`advanceUntil(BooleanSupplier condition, double stepSeconds, int maximumSteps, String terminalState)`
helper. It repeatedly calls the composed world's real update, uses a finite positive fixed step, and
mentions `terminalState` in the failure. The maximum is only a failure guard. Do not use sleeps,
wall-clock polling, or assertions tied to the number of updates.

### State capture and identity assertions

Add private immutable `OperationalEmptyState`, `RoutedIdentityState`, and `IdentityRef<T>` values.
`OperationalEmptyState` captures at least:

- scheduler and supply snapshots;
- AV02 waiting/departed inventory and OSR stored/departed inventory;
- lifecycle snapshot, current load-plan identity by physical id, elastic lease/allocation snapshot,
  and exact committed assignments;
- operational controller, launch queue, transport queue, ingress/in-flight, station-arrival,
  coordinator, Third Party, Adapting, P2P input/tipper, and continuation snapshots;
- world trackable count, renderable count/identity/visibility, tote motion/lids/route follower;
- bag receiver content, bag-planning trace used by the scenario, outbound allocation, and output
  sheet allocation.

`RoutedIdentityState` captures the exact `OperationalPhysicalToteReleaseRequest`, source, physical
id, logical sheet, order type, role, service centre, pharmacy ids, source sequence, release time,
P2P assignment, current `ToteLoadPlan` reference, `Tote`, `RenderableObject`, and `RouteFollower`.
Compare value identity with equality and object ownership with `assertSame` through every launch,
transport, station-arrival, claim, disposition, continuation, P2P input, and completion boundary.

Every rejection/deferral assertion first captures `OperationalEmptyState`, performs the production
update that is expected to defer, and states the limited diagnostic fields allowed to change. All
inventory, lifecycle, plan, assignment, route, station, and outbound fields must remain equal. This
prevents a happy-path-only implementation from hiding partial mutation.

## Step 1: Prove Real AV02 Allocation Gating And Capacity

### Required reading for this step

- `Av02AllocationSnapshotFactory`, `Av02AllocationController`,
  `Av02PhysicalToteInventory`, and `Av02ToteLifecycleController`;
- `Av02AllocationSnapshotFactoryTest`, `Av02AllocationControllerTest`, and
  `AdaptingStoreFlowTest`;
- the fixture/state contracts in this plan.

### Required change

Create the scenario class, private fixture foundation, state captures, deterministic progression
helper, and this exact test:

```java
shouldAllocateOnlyAuthorizedDependencyReadyEmptyWithinCapacity
```

### Behavioral specification

- Start with authorization absent. A real allocation update creates no physical tote, load plan,
  assignment, or inventory history.
- Authorize the current service-centre EMPTY sheets. Authorization alone still changes neither
  physical inventory.
- Keep `empty-adapted-104`'s exact prepared key unresolved and assert its allocation candidate has
  `DEPENDENCY_NOT_READY`.
- Complete the matching prepared input through the fixture's real
  `AdaptingVisit.store(...)`/bench/`AdaptingAreaController` sequence and assert the prepared
  key/readiness appears only after completion; no AV02 tote is allocated by the store completion
  itself.
- Submit the fresh allocation command selected by `Av02AllocationSnapshotFactory`. One world update
  allocates only the highest-ranked eligible EMPTY, `empty-direct-104`, as `av02-000001`.
- Assert AV02 occupancy one, an active `PRE_P2P` assignment, an empty immutable load plan, source
  `AV02`, order type `EMPTY`, exact sheet/service centre/pharmacy/source sequence, no inbound
  manifest, and no OSR occupancy/history entry for that id.
- While capacity remains one, a fresh snapshot exposes `NO_AV02_CAPACITY` for every otherwise
  eligible unallocated EMPTY and another allocation update makes no mutation.
- The lower-priority `empty-direct-108` must remain unallocated while eligible `104` work exists.

The scenario does not release the allocated tote in this step. It proves that authorization,
dependency readiness, allocation, lifecycle registration, empty-plan installation, and inventory
capacity remain separate transitions.

### Decision-complete test contract

The test drives allocation only through `Av02AllocationController.update(...)` as part of the
composed world. Direct fixture input setup is allowed only before the captured action. Capture full
state before unauthorized, unresolved-dependency, and capacity-full updates. In each case, allow
only allocation-controller diagnostic/last-evaluation fields to differ; require physical state and
all downstream state to remain identical.

The successful assertion must prove exactly one lifecycle record and exact active assignment, not
only AV02 occupancy. It must also prove absence from both manifest lookup and all OSR stored/departed
collections. Those negative assertions catch an implementation that fabricates an EMPTY manifest or
uses OSR as the AV02 store.

### Expected output

The new scenario fixture proves the real allocation entry boundary and bounded source separation.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest.shouldAllocateOnlyAuthorizedDependencyReadyEmptyWithinCapacity
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove operational EMPTY allocation`

## Step 2: Prove One Mixed OSR And AV02 Release Boundary

### Required reading for this step

- `DspAv02OperationalRuntimeTest.CombinedRuntimeFixture`;
- `DspOperationalReleaseRuntimeFactory`, `DspOperationalReleaseController`,
  `DspOperationalReleaseSnapshotFactory`, and `CompositeOperationalCommandHandler`;
- `Av02OperationalCommandHandler`, `OsrProcessingReleaseCommandHandler`,
  `OsrOutboundRouteLaunchTargetRegistry`, and the elastic allocation runtime APIs.

### Required change

Extend only the new scenario class with this exact test and its private mixed-source helpers:

```java
shouldRankOsrAndAv02ThroughOneOperationalReleaseBoundary
```

### Behavioral specification

- Allocate `av02-000001` through Step 1's production allocation path and keep at least one eligible
  OSR `104` manifest, the remaining eligible AV02 `104` work, and authorized `108` EMPTY work in one
  immutable operational snapshot.
- Compose the release runtime only through `createElasticWithAv02(...)`. Assert its target registry
  exposes the exact configured Third Party, Adapting, and five P2P destinations for both OSR and
  AV02 sources.
- Drive a completed synchronous evaluation and assert exactly one applied command, one exact shared
  launch-queue entry, and deterministic highest-priority-service-centre/pharmacy/source ordering.
  Assert the unselected source remains unchanged.
- Before accepting another release, fill the capacity-one shared launch queue. Run another complete
  evaluation/application and assert the selected second source is deferred with complete mutable
  state unchanged.
- Let the real launch controller dequeue/hydrate the head. On the next fresh evaluation, assert the
  next eligible `104` candidate is selected; eligible `104` work prevents `empty-direct-108` from
  being selected.
- For every released AV02 candidate, including `empty-third-party-104`, assert AV02 departure and
  one exact immutable P2P assignment are committed before the launch request is enqueued. The
  launch request's first destination remains Third Party where required; the assignment does not
  teleport it to P2P.
- Assert at most one command is applied for each evaluation sequence and no direct source-specific
  scheduler/controller exists beside the combined runtime.

### Decision-complete test contract

Capture full state immediately before the launch-capacity deferral. Permit only evaluation sequence,
last-decision, and typed deferral diagnostics to change. AV02/OSR inventory, lifecycle, load plans,
leases, committed assignments, launch head, transport state, and station state must compare equal.

After each accepted release, inspect the exact command/request/envelope identities through runtime
snapshots and queue heads. For AV02 require the id to be absent from waiting inventory, present once
in departed history, active in lifecycle, assigned once to the exact P2P line, absent from OSR and
manifest data, and still source `AV02`. For OSR require the reciprocal manifested/source assertions.

One representative AV02-first and one representative OSR-first ordering arrangement are required
within the method by using fresh nested fixtures/configurations; this catches accidental fixed
source priority. Exhaustive scheduler ranking cases remain owned by existing focused tests.

### Expected output

The proof connects real AV02 allocation to the single production scheduler/release boundary without
source duplication or partial mutation under backpressure.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest.shouldRankOsrAndAv02ThroughOneOperationalReleaseBoundary
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove mixed operational EMPTY release`

## Step 3: Prove EMPTY Station Processing, Continuation, And P2P Completion

### Required reading for this step

- `DspStationProcessingBoundaryScenarioTest` and
  `DspStationRouteContinuationScenarioTest`;
- `DspP2pArrivalConsumerScenarioTest`, `ThirdPartyPickFlowTest`,
  `ThirdPartyAdaptedCollectIntegrationTest`, and `AdaptingCollectFlowTest`;
- the production station-processing, continuation, transport, and P2P completion classes named in
  this plan.

### Required change

Extend only the new scenario class with this exact test:

```java
shouldPreserveAv02IdentityThroughThirdPartyAndAdaptingToP2pCompletion
```

The method contains two fresh sub-scenarios: Third Party -> P2P for
`empty-third-party-104`, and Adapting COLLECT -> P2P for `empty-adapted-104`. Direct P2P is already
proved across the AV02 branch; assert it as a compatibility control only if the shared fixture needs
it, but do not add a third duplicate route scenario.

### Behavioral specification

For each sub-scenario:

- authorize, dependency-ready, allocate, rank, and release the EMPTY through the Step 1-2
  production entry points; do not prebuild an `Av02AllocatedTote`, assignment, or launch request;
- observe the selected non-P2P first destination in the shared launch request, transport in-flight
  registry, exact station-arrival FIFO, and active station claim before any P2P ownership exists;
- drive real Third Party or Adapting processing to completion. Assert the old empty plan remains
  immutable, the shared registry is replaced exactly once, and the completion disposition contains
  the exact replacement plan instance with its one deterministic pack;
- let the route-continuation controller acknowledge the exact FIFO head only after accepting a new
  routed envelope into the bounded transport queue;
- observe common-entry exact-object re-entry, the next P2P terminal arrival, P2P station claim,
  tipper-input acceptance, and actual tipper completion without direct target calls or station
  enqueue;
- assert one initial transport publication and one exact-object re-entry, with one world tote,
  one renderable, and the same `Tote`, `RenderableObject`, and `RouteFollower` references on both
  route envelopes;
- assert the release request, source `AV02`, physical id, sheet, service centre, pharmacy, source
  sequence, release time, and exact pinned assignment are unchanged through every boundary. The
  first-destination field remains historical release provenance; the per-leg destination changes
  only through `OperationalRouteLaunchRequest.continueTo(...)`;
- before actual tipper completion, lifecycle remains active `PRE_P2P` and no consume disposition or
  outbound tote exists. Only the real callback terminates the assignment/lifecycle and produces the
  terminal `CONSUME` acknowledgement;
- after completion, require `CONSUMED_AT_P2P`, termination reason `CONSUMED_AT_P2P`, held/closed/
  invisible inbound presentation, empty station/continuation/transport ownership, and no duplicate
  callback mutation.

For Third Party, assert its produced pack plan is the exact one propagated onward. For Adapting,
establish the matching prepared record/readiness through real STORE completion before EMPTY
allocation, then assert COLLECT removes that exact stored record and propagates the corresponding
collected pack plan. Do not stage the record or add a prepared key directly.

### Backpressure and no-overtaking proof

In one sub-scenario, keep the continuation transport queue full when the station publishes
`CONTINUE`. Capture full state and update again. The same disposition must remain the global FIFO
head, the tote remains held/visible at the completed station, its route follower and plan do not
change, and no P2P arrival/claim appears. After capacity opens, that exact disposition continues;
no later work overtakes it.

Before exact tipper completion, invoke the production P2P completion wrapper with a different tote
object carrying the same id and assert the complete state is unchanged. Then allow the real tipper
completion. This negative case proves exact physical-object ownership rather than id-only success.

### Decision-complete test contract

All route progress is driven through `SimulationWorld.update(...)`, terminal detection, and real
controllers. Direct coordinator calls are permitted only after terminal completion to assert that a
consumed physical id cannot be reclaimed; they must not manufacture progress.

For both sub-scenarios, record `RoutedIdentityState` at launch, first station arrival/claim,
continuation queue, re-entry/in-flight, P2P arrival/claim/input, and terminal completion. Require
exact object identity where the contract preserves objects and a new immutable routing-envelope
identity where continuation deliberately creates one.

After terminal acknowledgement, repeat updates and the wrong-object callback and compare the full
terminal state. Cumulative completion/acknowledgement diagnostics, lifecycle history, AV02 departed
history, assignment history, plan, visibility, and outbound state must be exactly-once.

### Expected output

The same real AV02-created EMPTY tote travels through each supported non-P2P station path, re-enters
source-neutral transport, reaches its committed P2P line, and completes its inbound lifecycle with
no teleport, duplicate publication, or test-only handoff.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest.shouldPreserveAv02IdentityThroughThirdPartyAndAdaptingToP2pCompletion
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove operational EMPTY station journey`

## Step 4: Prove Independent Outbound Tote Allocation

### Required reading for this step

- `DspOutboundToteAllocationScenarioTest`;
- `DspOutboundToteAllocationController`, `OutboundToteAllocator`,
  `OutputSheetAllocator`, and `StoredBagReceiver`;
- bag-planning/provenance values `BagPlanningResult`, `PlannedBag`, `PlannedPackTrace`, and
  `PackSourceProvenance`.

### Required change

Extend only the new scenario class with this exact test:

```java
shouldConsumeAv02InboundAndAllocateIndependentOutboundTote
```

### Behavioral specification

- Drive one AV02 EMPTY from real allocation through actual P2P completion using the direct-P2P
  route to keep this scenario focused on the lifecycle seam.
- Before tipper completion, assert the receiver and outbound allocator are empty. After completion,
  assert the inbound `av02-000001` is terminal and still no outbound physical tote exists; inbound
  consumption alone must not fabricate output.
- Create one `PlannedBag` for the completed EMPTY prescription and one `PlannedPackTrace` whose
  `PackSourceProvenance` identifies the EMPTY source line/sheet and whose input physical tote is
  `av02-000001`. The plan's bag correlation must match the real completed runtime bag.
- Put that bag through `StoredBagReceiver.reserveIncomingBag(...)`, `beginReceiving(...)`, and
  `completeReceiving(...)`. Run `OutboundToteAllocationController.update(...)` once.
- Assert the receiver is drained exactly once and one allocation exists on the same exact P2P line.
  The outbound id differs from `av02-000001`, uses the deterministic `outbound-<line>-1` family,
  has lifecycle role/state `OUTBOUND_BAG` while open, and owns the source sheet's output assignment.
- Close through `OutboundToteAllocator.closeForApplicableWorkCompletion(...)` at explicit simulation
  time and assert transition to `OUTBOUND`, pharmacy/service-centre purity, one bag, and the expected
  closure reason.
- Assert the AV02 source sheet/id remain only in immutable bag/pack provenance and inbound lifecycle
  history. AV02 inventory does not create the outbound id or output sheet, and the outbound id never
  appears in AV02 or OSR inventory/manifest data.
- A repeated outbound-controller update must not allocate the same bag twice or alter either
  lifecycle history.

### Decision-complete test contract

Capture full state at three mutation boundaries: immediately before tipper completion, after inbound
completion/before bag receiver completion, and after outbound close. Assert the inbound assignment
is terminal before the outbound assignment begins and that the two physical ids never share one
record or active assignment.

Use equality for planned values and `assertSame` for the exact `PlannedBag` retained by the
allocation. Require the allocated bag's output sheet allocation to name the EMPTY source sheet as
`sourceOwningSheetKey`; if no overflow exists, the output sheet key is that same known sheet and is
not generated. Overflow/generated-sheet behavior remains owned by existing outbound tests and is
not repeated here.

The proof must fail if an implementation reuses the inbound AV02 tote, allocates output before a
completed bag exists, loses pack/source provenance, crosses pharmacy/service-centre boundaries, or
allocates the same receiver bag twice.

### Expected output

The completed inbound EMPTY journey and its outbound dispatch container are demonstrably separate
physical lifecycles joined only by immutable bag/pack provenance and output-sheet allocation.

### Implementation verification

The implementation model runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest.shouldConsumeAv02InboundAndAllocateIndependentOutboundTote
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove EMPTY inbound outbound separation`

## Step 5: Regression And Branch Closure (Complete)

This step is complete after user verification, architecture review, and bounded documentation
reconciliation. Do not start the full-day execution/metrics feature during closure.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Run the focused integration/regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.station.continuation.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

Then run the complete suite:

```powershell
.\gradlew test
```

No visual run is required. The feature adds no production scene, geometry, or rendering behavior;
exact route/station/tote/renderable state is verified through the scenario. Visual whole-warehouse
topology and full-day execution remain separate work.

### End-of-feature architecture review

Review the actual feature diff and report PASS, FAIL, or UNPROVEN for each item with concrete test
method and production-control-flow evidence:

- the diff creates only the planned scenario test and closure documentation;
- only authorized, dependency-ready logical EMPTY creates an AV02 physical tote;
- AV02 allocation uses the real snapshot/controller boundary, capacity one, deterministic ids, and
  an empty initial load plan;
- no EMPTY manifest is fabricated and AV02 physical ids never enter OSR inventory/history;
- one `createElasticWithAv02(...)` runtime ranks OSR and AV02 and applies at most one command per
  evaluation;
- backpressure and stale/rejected operations leave all physical state unchanged;
- every AV02 P2P-required release commits one exact assignment before departure, including a
  non-P2P first destination;
- launch hydration, common warehouse transport, terminal arrival queues, station claims, real
  domain completion, continuation, P2P input, and tipper completion are all exercised without a
  test-only handoff or direct station enqueue;
- Third Party and Adapting replace the immutable load plan exactly once and the exact replacement
  is propagated;
- the same release identity, tote, renderable, and route follower survive every leg while a new
  immutable routing envelope represents continuation;
- initial publication occurs once, exact-object re-entry does not duplicate world/renderable
  objects, and the tote is observed at its non-P2P first station before P2P;
- only actual tipper completion terminates the exact inbound assignment/lifecycle and repeated or
  wrong-object completion mutates nothing;
- the outbound physical tote is a different deterministic id, owns an independent lifecycle/output
  sheet, and retains AV02 source identity only through immutable bag/pack provenance;
- all live mutation remains on the simulation thread and scheduler inputs remain immutable;
- no production API, debug scene, calibrated timing, full-day runner/metrics, dispatch/32R,
  Exception, MANUAL/MANUAL_MERGE, new topology, or mutable reset was added.

### Documentation closure

After user verification and architecture review are green, perform bounded documentation
reconciliation. Do not introduce a new architectural decision or begin the next feature.

- mark this plan `complete and verified` and record its final implemented contract;
- update the current-programme entry in `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and only stale current-position/reading-order text in
  `docs/codex-instructions.md`;
- update the runtime interlude in `docs/machines/phase-1-stations-roadmap.md`;
- record `feature/dsp-operational-empty-end-to-end-proof` as complete/merged according to actual
  repository state;
- make a separately planned full-day execution, metrics, and inspection branch the next programme
  work;
- retain station-to-station visual topology, outbound dispatch/32R, Exception handling,
  MANUAL/MANUAL_MERGE handling, calibrated timing, and renderer integration as explicit deferrals.

Proposed commit message: `Complete operational EMPTY proof`

## Closure Record

- Step 5 focused regression and complete-suite verification were reported green by the user.
- Architecture review passed all fifteen contract items; no unnecessary production changes or
  unresolved architectural concerns were found.
- At closure, `feature/dsp-operational-empty-end-to-end-proof` tracks the corresponding origin
  branch and remains pending merge to `master`.

## Implemented Final Contract (Verified)

- Authorized, dependency-ready logical EMPTY work receives one deterministic physical AV02
  `PRE_P2P` tote without consuming OSR capacity or fabricating a manifest.
- OSR and AV02 candidates share one immutable operational evaluation, global ranking, route-target
  admission, and at-most-one command application boundary.
- Every P2P-required AV02 tote receives one exact sticky assignment before departure, while its
  independently selected first station remains authoritative.
- The AV02 tote is hydrated once and moves through real launch, transport, terminal arrival,
  station processing, disposition, continuation, P2P arrival/input, and actual tipper completion
  boundaries.
- Third Party and Adapting COLLECT replace and propagate the exact immutable current load plan; the
  physical source, identity, objects, and assignment remain continuous across route legs.
- P2P completion terminally consumes the inbound AV02 physical journey exactly once.
- A completed bag may then enter a distinct outbound physical tote lifecycle. The outbound tote is
  never the AV02 tote; bag/pack provenance and output-sheet allocation provide the explainable join.
- The proof adds no production behavior. Station-to-station visual topology, renderer integration,
  calibrated timing, outbound dispatch/32R, Exception handling, and MANUAL/MANUAL_MERGE handling
  remain deferred. Full-day execution, metrics, and inspection are the next separately planned
  programme feature after this branch is merged.
