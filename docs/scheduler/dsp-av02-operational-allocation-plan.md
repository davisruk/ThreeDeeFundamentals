# DSP AV02 Operational Allocation Plan

Branch: `feature/dsp-av02-operational-allocation`

Status: implementation in progress; Steps 1-7 are complete and Step 8 is next.

## Purpose

Make logical `EMPTY` work a first-class operational fulfilment flow by introducing one physical
inbound fulfilment tote at AV02, then sending that tote through the same station-routing, sticky P2P,
hydration, transport, arrival, and consumption boundaries used by OSR-originated work.

This branch is the final physical-work prerequisite before full-day execution and metrics. It does
not implement Exception Station behavior.

## Locked Requirements

Do not revisit these decisions during implementation.

### Two independent physical-tote supply boundaries

- AV02 introduces an inbound `PRE_P2P` fulfilment tote only for a logical `OrderType.EMPTY` order.
- A 12N sheet number greater than one does not by itself cause AV02 allocation.
- Non-EMPTY `FULL_PACK` and `ASSOCIATED` work continues to use its inbound physical manifest from
  OSR. Do not generalize AV02 eligibility to those order types.
- P2P/bagging independently introduces outbound physical totes from each line's logical reservoir.
  Existing `OutboundToteAllocator` capacity closure and `OutputSheetAllocator` generated-sheet
  behavior remain unchanged.
- If one logical order spans multiple outbound totes, output allocation creates additional sheet
  numbers. AV02 does not create those outbound totes or output sheets.

### AV02 allocation and waiting

- EMPTY remains logical and consumes no OSR capacity before AV02 allocation.
- Supply authorization is proven only by
  `DspSupplySnapshot.authorizedEmptyOrderSheetKeys()`.
- An EMPTY order may be allocated only when its own adapted dependencies are terminal and it has no
  active physical assignment.
- AV02 has a configurable bounded waiting inventory. Baseline capacity is one physical tote.
- Allocation creates at most one tote per simulation update and is instantaneous in this phase; no
  reservoir conveyor, operator duration, geometry, or animation is added.
- Allocate in the existing service-centre/pharmacy/source ordering: eligible work from the
  highest-priority service-centre cohort first, then pharmacy group, source sequence, sheet number,
  order ID. There is no order-type priority.
- Use an injected deterministic monotonic physical-ID source. Baseline test IDs use
  `av02-000001`, `av02-000002`, and so on. Never derive a physical ID from order ID, sheet number,
  notional tote ID, or pharmacy.
- `Av02ToteLifecycleController` remains the lifecycle mutation boundary. Extend its surrounding
  composition rather than duplicating its `PRE_P2P` registration/assignment behavior.
- Allocation also installs an empty `ToteLoadPlan` for the new physical ID. Third Party and
  Adapting collection may add packs through the existing mutable load-plan registry.

### Operational release and ranking

- A physically allocated AV02 tote waits at AV02 until downstream route-entry acceptance and the
  normal operational scheduler select it.
- OSR and AV02 physical candidates must be evaluated in one detached operational snapshot and one
  global ranking pass. Do not add a second scheduler that can release another tote independently.
- Preserve at most one applied operational release command per completed scheduler evaluation.
- Represent physical source explicitly as `OSR` or `AV02`. Do not fabricate an
  `InboundToteManifest` for EMPTY and do not put AV02 totes in `OsrPhysicalInventory`.
- Route-entry selection begins after the source boundary. An EMPTY tote may first visit Third Party,
  Adapting COLLECT, or P2P according to its existing `RouteRequirements` and completed work.
- Candidate-specific route admission and queue capacity remain authoritative. AV02 inventory
  capacity is allocation capacity, not downstream station admission.
- Ranking remains pharmacy-grouped. EMPTY orders are pharmacy-pure and contribute their logical
  pharmacy directly because they have no inbound manifest.

### Sticky P2P and workload

- Every EMPTY tote requiring P2P receives one exact immutable `P2pPhysicalToteAssignment` before it
  leaves AV02, including when its first station is Third Party or Adapting.
- Elastic feeding/acquisition budgets and direct-P2P admission apply exactly as for OSR-originated
  physical totes.
- Command application commits the AV02 departure and P2P assignment on the simulation thread.
  P2P arrival only revalidates; it never chooses or changes a line.
- Once AV02 allocation creates a `PRE_P2P` tote, P2P remaining-work snapshots count that physical
  tote until `CONSUMED_AT_P2P`. It is no longer reported as an unallocated EMPTY diagnostic.
- Authorized EMPTY sheets not yet allocated remain explicit EMPTY workload diagnostics so elastic
  demand does not silently forget them.
- Existing pinned-assignment, draining-line, output-close, and lease-release contracts are
  unchanged.

### Source-neutral transport identity

- The current route-launch and P2P-arrival path obtains identity from
  `OsrOutboundRouteLaunchRequest.releaseRequest().manifest()`. Replace that downstream assumption
  with one immutable source-neutral physical-route identity carrying:
  - physical tote ID;
  - physical source (`OSR` or `AV02`);
  - logical order sheet key and order type;
  - service-centre ID and pharmacy IDs;
  - source sequence number;
  - first route destination;
  - optional exact P2P assignment.
- OSR request/command APIs may retain compatibility constructors or adapters, but transport,
  hydration, routed-tote, station-arrival, and P2P-arrival code must consume the source-neutral
  identity.
- Preserve exact object ownership through launch queue, hydration, transport ingress, station FIFO,
  and P2P tipper input. Do not introduce a second route engine or direct AV02-to-P2P call.
- Renderables remain lazy: a tote is created only when its AV02 launch is hydrated for warehouse
  transport. A waiting AV02 inventory entry is non-rendered state.

### Failure and mutation rules

- Snapshot construction and worker evaluation are immutable and side-effect free.
- AV02 allocation and departure mutations occur only on the simulation thread.
- Prevalidate ID uniqueness, sheet availability, AV02 capacity, load-plan absence, route target,
  and proposed P2P assignment before mutation.
- A stale, duplicate, capacity-blocked, or downstream-rejected command leaves AV02 inventory,
  lifecycle assignment, load plan, P2P leases, and transport queues unchanged.
- Once downstream acceptance succeeds, remove exactly the selected AV02 inventory entry, retain its
  active `PRE_P2P` assignment, and commit its P2P lease/assignment exactly once.
- Reset remains complete runtime/scene reconstruction. Do not add mutable reset methods.

## Non-Goals

- Exception Station, short picks, NS labels, empty NS bags, or incomplete fulfilment.
- AV02 reservoir geometry, animation, operative timing, or stock depletion.
- Changes to outbound P2P tote allocation or generated output sheet semantics.
- Calibrated station timings or throughput claims.
- Full-day execution, metrics history, 32R generation, or dispatch/trunker loading.
- A second scheduler, route engine, rendering thread, or live mutable worker input.

## Package And Vocabulary

Place AV02 allocation/inventory types under:

```text
online.davisfamily.warehouse.sim.dsp.av02
```

Preferred names:

- `Av02AllocationConfig`
- `Av02AllocatedTote`
- `Av02InventorySnapshot`
- `Av02PhysicalToteInventory`
- `DeterministicAv02PhysicalToteIdAllocator`
- `Av02AllocationCandidate`
- `Av02AllocationSnapshot`
- `Av02AllocationSnapshotFactory`
- `Av02AllocationController`
- `AllocateEmptyToteAtAv02Command`
- `ReleasePhysicalToteFromAv02Command`
- `Av02OperationalCommandHandler`

Place source-neutral route identity beside existing launch types under:

```text
online.davisfamily.warehouse.sim.dsp.osr.release.launch
```

Preferred names:

- `OperationalPhysicalToteSource`
- `OperationalPhysicalToteIdentity`
- `OperationalRouteLaunchRequest`

Minor naming adjustments are allowed only when an existing source type makes one necessary. Do not
move established machine-state classes or create AV02-specific copies of transport routing.

## Step 1: Lock Physical Source And AV02 Domain Values

Scope:

- Add `OperationalPhysicalToteSource` with exactly `OSR` and `AV02`.
- Add immutable source-neutral physical identity without changing existing launch behavior yet.
- Add validated AV02 config, allocated-tote value, inventory snapshot, and deterministic ID source.
- Require `OrderType.EMPTY`, `PhysicalToteRole.PRE_P2P`, matching sheet/service-centre identity,
  distinct physical IDs/sheets, positive capacity, and deterministic insertion order.
- Prove AV02 and outbound-P2P tote supply are distinct concepts; do not reference
  `OutboundToteAllocator` from AV02 types.

Do not add controllers, scheduler candidates, or transport changes in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationDomainTest --tests online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleControllerTest
```

Proposed commit message: `Add AV02 physical allocation domain`

## Step 2: Add Bounded AV02 Physical Inventory

Scope:

- Implement `Av02PhysicalToteInventory` as the sole owner of allocated-but-not-released AV02 tote
  entries.
- Add capacity, FIFO order, lookup by physical ID and sheet, occupancy, remaining capacity, and
  immutable snapshot APIs.
- Reject duplicate physical IDs, duplicate sheets, non-EMPTY entries, overflow, unknown departure,
  and double departure without mutation.
- Record deterministic departure history separately from current occupancy.
- Keep lifecycle and load-plan mutation outside the inventory.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventoryTest
```

Proposed commit message: `Add bounded AV02 tote inventory`

## Step 3: Build Immutable AV02 Allocation Eligibility

Scope:

- Build `Av02AllocationSnapshot` from `WarehouseSchedulerSnapshot`, `DspSupplySnapshot`, AV02
  inventory, and physical lifecycle state.
- Select only logical `OrderType.EMPTY` sheets present in the authorized EMPTY set.
- Reuse the existing prepared-line dependency semantics. A candidate with an unresolved adapted
  dependency is reported blocked and is not allocated.
- Exclude terminal logical work, already allocated/actively assigned sheets, and sheets already in
  AV02 inventory.
- Apply the existing highest-priority eligible service-centre cohort and pharmacy-group/source
  ordering. Preserve one candidate per logical sheet and deterministic blocked reasons.
- Expose `NO_AV02_CAPACITY`, `EMPTY_NOT_AUTHORIZED`, `DEPENDENCY_NOT_READY`, and
  `ACTIVE_PHYSICAL_ASSIGNMENT` as stable typed reasons.
- Emit at most one immutable `AllocateEmptyToteAtAv02Command` containing logical sheet identity and
  the snapshot sequence needed for stale-decision rejection. The command must not contain an
  allocated physical tote ID.
- Prove an entirely blocked higher-priority centre does not block an eligible lower-priority centre,
  matching current operational cohort behavior.

Do not allocate a physical ID in snapshot construction or on a worker thread.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationSnapshotFactoryTest
```

Proposed commit message: `Evaluate AV02 allocation eligibility`

## Step 4: Allocate AV02 Totes On The Simulation Thread

Scope:

- Add `Av02AllocationController` that consumes the detached `AllocateEmptyToteAtAv02Command` and
  applies at most one allocation per simulation update.
- Revalidate authorization, dependencies, capacity, active assignment, ID uniqueness, and load-plan
  absence against fresh simulation-thread state.
- Allocate the next physical ID only after successful revalidation. Then use
  `Av02ToteLifecycleController.allocateFor(...)`, add the matching `Av02AllocatedTote` to AV02
  inventory, and install `new ToteLoadPlan(physicalToteId, List.of())` in the existing mutable
  load-plan registry.
- Structure validation so all expected failures occur before the first mutation. Treat an
  unexpected failure after mutation begins as an invariant failure; do not silently continue with
  partial state.
- Prove one-update allocation, full-capacity blocking, deterministic IDs, stale decisions, no
  duplicate sheet assignment, and reset by reconstruction.

Do not release the tote from AV02 or create a renderable in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

Proposed commit message: `Allocate EMPTY totes at AV02`

## Step 5: Generalize Operational Physical Candidates

Scope:

- Add a source-neutral operational physical candidate carrying the locked identity fields.
- Adapt OSR candidates from their exact manifest and AV02 candidates from inventory plus the exact
  logical order. Never create an EMPTY manifest.
- Change `DspOperationalReleaseCandidate` and snapshot validation to consume the source-neutral
  candidate. Preserve a compatibility constructor/factory for existing OSR-focused tests.
- Build one candidate list containing current OSR inventory candidates and current AV02 inventory
  candidates, then one pharmacy-group catalog that includes EMPTY logical pharmacy identity.
- Preserve all existing identity, status, service-centre priority, route-requirement, and source
  ordering validation.
- Verify OSR-only snapshots are byte-for-byte/equality compatible where their public contract is
  unchanged.

Do not change scheduler selection or command application in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotTest
```

Proposed commit message: `Join OSR and AV02 operational candidates`

## Step 6: Select AV02 Work Through The Existing Scheduler

Scope:

- Add a common typed physical-release command contract implemented by the existing OSR command and
  new `ReleasePhysicalToteFromAv02Command`.
- Update operational selection/decision to preserve source identity and emit the correct command
  without changing ranking or producing more than one selection.
- Apply dependency, route-entry admission, elastic P2P budget, active-pharmacy affinity, direct-P2P
  destination, and exact assignment rules identically to OSR and AV02 candidates.
- Ensure the AV02 source itself is not treated as the first processing station; first route entry is
  Third Party, Adapting, or P2P.
- Preserve existing OSR command constructors and focused tests.
- Test mixed OSR/AV02 ranking, direct P2P, Third Party-first, Adapting-first, queue backpressure,
  elastic budget exhaustion, third service centre outside the concurrency window, and no
  order-type priority.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspAv02OperationalReleaseTest
```

Proposed commit message: `Schedule AV02 physical releases`

## Step 7: Apply AV02 Departure And Sticky Assignment Safely

Scope:

- Add `Av02OperationalCommandHandler` and a composite operational command handler that dispatches
  OSR and AV02 commands by exact type.
- Replace the manifest-specific P2P commit preparation input with a source-neutral commit request
  containing exact physical ID, service centre, and proposed assignment. Keep an OSR adapter so
  completed OSR tests and callers continue to work.
- For AV02, revalidate current inventory membership, lifecycle role/state, active sheet assignment,
  load-plan identity, selected route target, and P2P assignment before downstream acceptance.
- Obtain downstream acceptance first. On success commit the P2P assignment and remove exactly the
  AV02 inventory head/entry. Retain the existing active `PRE_P2P` lifecycle assignment; AV02
  departure is not an OSR lifecycle activation.
- Rejected/deferred/stale commands leave inventory, lifecycle, load plan, leases, and downstream
  queues unchanged. Repeated application is rejected idempotently.
- Perform at most one command mutation per scheduler result.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommitTest
```

Proposed commit message: `Apply AV02 releases atomically`

## Step 8: Establish The Source-Neutral Launch Contract

### Design intent

This step introduces the immutable contract needed by transport without migrating the transport
runtime yet. The split is deliberate: Step 8 must compile with the existing OSR transport path
still intact, and Step 9 performs the mechanical downstream migration. Pharmacy identity must be
preserved explicitly because an AV02 EMPTY tote initially has an empty load plan and therefore its
pharmacy cannot be reconstructed from packs or an inbound manifest.

Read before implementation:

- `OperationalPhysicalToteIdentity`
- `OperationalPhysicalToteReleaseRequest`
- `Av02AllocatedTote`
- `Av02AllocationController`
- `Av02OperationalCommandHandler`
- `OsrProcessingReleaseRequest`
- `OsrOutboundRouteLaunchRequest`

### Required change surface

Create:

- `online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest`
- `online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory`
- `OperationalRouteLaunchRequestTest` in the matching test package

Modify:

- `Av02AllocatedTote`
- `Av02AllocationController`
- `OperationalPhysicalToteReleaseRequest`
- `Av02OperationalCommandHandler`
- their existing focused tests and fixtures

Do not modify the route-launch queue, hydrator, routed-tote, arrival, route catalog, transport
controller, scheduler ranking, or P2P lease logic in this step.

### Locked API and validation

- Append `String pharmacyId` to `Av02AllocatedTote`. Normalize it, reject blank values, and expose
  `List<String> pharmacyIds()` returning exactly `List.of(pharmacyId)`. Do not infer pharmacy from
  the empty `ToteLoadPlan`.
- `Av02AllocationController` must copy `Av02AllocationCandidate.pharmacyId()` into the allocated
  tote. Update existing test fixtures explicitly; do not add a placeholder compatibility pharmacy.
- Extend `OperationalPhysicalToteReleaseRequest` to contain, in order:
  `OperationalPhysicalToteIdentity identity`, `List<String> pharmacyIds`, `Duration releaseTime`,
  and `Optional<P2pPhysicalToteAssignment> p2pAssignment`.
- The release request must normalize and de-duplicate pharmacy IDs in encounter order, reject null,
  blank, or empty values, and require exactly one pharmacy for `FULL_PACK`, `ASSOCIATED`, and
  `EMPTY`. Keep multi-pharmacy ADAPTED support.
- `Av02OperationalCommandHandler` must construct the release request from the exact allocated tote
  identity and its exact one-element pharmacy list. All Step 7 prevalidation, downstream-first
  acceptance, commit, and departure ordering remains unchanged.
- Define `OperationalRouteLaunchRequest` as a record containing exactly
  `OperationalPhysicalToteReleaseRequest releaseRequest` and
  `OperationalRouteDestination destination`. Provide delegating accessors for identity, source,
  physical tote ID, order sheet, order type, service centre, pharmacy IDs, release time, and P2P
  assignment. Retain the exact release-request instance; do not copy it.
- When the first destination is P2P and an assignment is present, require the destination to equal
  the assignment destination. A non-P2P first destination may retain a later P2P assignment.
- Implement `OperationalRouteLaunchRequestFactory` as a final utility class with a private
  constructor and two static methods:
  `fromOsr(OsrProcessingReleaseRequest, OperationalRouteDestination)` and
  `fromOperational(OperationalPhysicalToteReleaseRequest, OperationalRouteDestination)`.
- `fromOsr(...)` must create an `OSR` `OperationalPhysicalToteIdentity` from the exact manifest,
  using `INBOUND_PACK`, manifest source sequence, and distinct manifest pharmacy IDs. It then
  creates the source-neutral release and launch requests using the exact release time, assignment,
  and destination.
- `fromOperational(...)` must retain the exact supplied source-neutral release request and only add
  the destination. Reject an OSR identity in this method; OSR must use `fromOsr(...)` so the live
  manifest remains the authoritative conversion source.
- Leave `OsrOutboundRouteLaunchRequest` and every existing downstream signature unchanged until
  Step 9. Do not create constructors accepting either request type.

### Behavioral tests

- `shouldCreateSourceNeutralOsrLaunchWithoutLosingManifestDerivedIdentity`: convert an OSR release
  and prove source, physical ID, sheet, type, service centre, pharmacies, sequence, release time,
  destination, and optional assignment match the manifest/release.
- `shouldRetainExactOperationalReleaseRequestForAv02`: create an AV02 release request and prove the
  launch request owns the exact same object and preserves its single pharmacy.
- `shouldAllowLaterP2pAssignmentForNonP2pFirstDestination`: use Third Party or Adapting as the first
  destination and retain the exact P2P assignment.
- `shouldRejectMismatchedDirectP2pDestinationAndInvalidPharmacyIdentity`.
- Extend allocation and command-handler tests to prove the candidate pharmacy survives allocation
  and target acceptance without a manifest or populated load plan.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationDomainTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestTest
```

### User verification

No additional user-run verification is required for this step.

Proposed commit message: `Define source-neutral route launch requests`

## Step 9: Migrate The Existing Transport Path To Source-Neutral Launches

### Design intent

Both physical sources must feed one bounded launch FIFO and the existing hydration, transport,
route-following, station-arrival, and P2P-arrival runtime. The established OSR-named queue,
controller, hydrator, transport queue, and snapshots are retained by name in this feature to avoid
an unrelated rename. Their payload contract becomes source-neutral. A later naming cleanup must be
separate and behavior-preserving.

Read before implementation:

- every main/test use of `OsrOutboundRouteLaunchRequest`
- `OsrOutboundRouteLaunchQueue`, `OsrOutboundRouteLaunchTarget`, and
  `OsrOutboundRouteLaunchTargetRegistry`
- `OsrOutboundRouteLaunchController`
- `OsrOutboundToteHydrator` and `LoadPlanOsrOutboundToteHydrator`
- `DetachedOutboundToteFactory`, `DetachedToteRenderableFactory`, and
  `RouteBoundDetachedOutboundToteFactory`
- `RoutedPhysicalTote`
- `P2pArrivalAdmissionRequest`
- `DspWarehouseTransportRuntimeFactory`

### Required change surface

Create:

- `Av02OutboundRouteLaunchTarget` in the existing `osr.release.launch` package
- `Av02OutboundRouteLaunchTargetTest`

Modify all production and test signatures that currently consume
`OsrOutboundRouteLaunchRequest` downstream of a release target so they consume
`OperationalRouteLaunchRequest` instead. Delete `OsrOutboundRouteLaunchRequest.java` after all
callers are migrated. Do not retain a dual-request constructor, overloaded queue method, wrapper,
or deprecated adapter.

### Locked migration and ownership sequence

- `OsrOutboundRouteLaunchTarget.accept(OsrProcessingReleaseRequest)` converts through
  `OperationalRouteLaunchRequestFactory.fromOsr(...)` and enqueues the resulting generic request.
- `Av02OutboundRouteLaunchTarget` implements `OperationalPhysicalToteReleaseTarget`, has the same
  destination/queue constructor shape as the OSR target, converts through
  `fromOperational(...)`, and returns the same applied/deferred/rejected results for duplicate and
  full-queue conditions.
- Extend `OsrOutboundRouteLaunchTargetRegistry` to construct one OSR adapter target and one AV02
  adapter target for every configured destination. Add
  `operationalPhysicalToteReleaseTargetRegistry()` returning one immutable
  `OperationalPhysicalToteReleaseTargetRegistry` built from the AV02 targets. Keep the existing OSR
  processing registry unchanged.
- `OsrOutboundRouteLaunchQueue` continues to be the sole bounded FIFO and stores exact
  `OperationalRouteLaunchRequest` instances. Duplicate identity and FIFO invariant behavior stays
  unchanged.
- Migrate `OsrOutboundRouteLaunchController`, `OsrOutboundToteHydrator`,
  `LoadPlanOsrOutboundToteHydrator`, `DetachedOutboundToteFactory`,
  `DetachedToteRenderableFactory`, `RouteBoundDetachedOutboundToteFactory`, and
  `RoutedPhysicalTote` to the generic request. Preserve exact request and load-plan object identity.
- `P2pArrivalAdmissionRequest.from(RoutedPhysicalTote)` must read order type, service centre,
  order sheet, pharmacy IDs, source-neutral physical ID, destination, and assignment from the
  generic launch request. It must not access `InboundToteManifest`.
- Retain `P2pArrivalAdmissionRequest.from(InboundToteManifest, destination)` only as the existing
  direct compatibility factory for isolated OSR fixtures. Production routed arrival must use the
  routed-tote overload.
- Hydration order remains: peek exact launch head, validate transport capacity/duplicate absence,
  resolve exact load plan, create tote/renderable/route follower, revalidate exact head and
  capacity, enqueue transport, then dequeue the exact launch head. A failed hydration or changed
  capacity leaves the launch queue untouched and publishes no renderable.
- Both sources use `DspWarehouseTransportRuntimeFactory`; do not add an AV02 hydrator, transport
  queue, route catalog, in-flight registry, route follower, arrival controller, or station queue.
- Update `DspWarehouseTransportDebugRig` fixtures mechanically to construct generic OSR launch
  requests through `OperationalRouteLaunchRequestFactory.fromOsr(...)`. Do not add operational
  AV02 behavior to the scene until Step 12.

### Behavioral tests

- Preserve all existing OSR queue/controller/hydration/transport behavior using the generic type.
- `shouldApplyOsrAndAv02TargetsToTheSameBoundedFifo`: accept one request from each source and prove
  FIFO order, exact source identity, and common capacity.
- `shouldDeferAv02WithoutMutationWhenSharedLaunchQueueIsFull` and
  `shouldRejectDuplicateAv02PhysicalIdentity`.
- `shouldHydrateAv02OnlyAfterLaunchAcceptance`: prove no tote/renderable exists while waiting at
  AV02 and exact identity appears only after hydration.
- `shouldCreateP2pArrivalFromAv02RoutedIdentityWithoutManifest` and preserve OSR routed arrival.
- Prove a Third Party-first or Adapting-first AV02 request retains its later P2P assignment without
  being sent directly to P2P.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactoryTest --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

### User verification

No additional user-run verification is required for this step.

Proposed commit message: `Generalize warehouse launch transport`

## Step 10: Count Allocated EMPTY Totes As P2P Work

### Design intent

Unallocated EMPTY work is a logical diagnostic. Once AV02 allocates a physical tote, the diagnostic
must be replaced by physical workload until that exact `PRE_P2P` tote reaches
`CONSUMED_AT_P2P`. OSR manifests remain authoritative for OSR tote ownership; AV02 inventory and
lifecycle state are authoritative for AV02 ownership.

### Required change surface

Modify:

- `P2pServiceCentreWorkSnapshotFactory` and its test
- `P2pWorkloadSnapshotFactory` and its test
- `DspP2pStickyLeaseRuntimeFactory`
- `DspP2pElasticAllocationRuntimeFactory`
- focused runtime-factory tests affected by the signature change

Do not modify allocation policy, deadline calculation, line-count rules, lease retention rules,
outbound bag/tote allocation, or manifest contents.

### Locked APIs and source resolution

- Add the decision-complete `P2pServiceCentreWorkSnapshotFactory.create(...)` overload accepting,
  in order: order states, manifest catalog, `Av02InventorySnapshot`, lifecycle snapshot, and
  `Set<OrderSheetKey> authorizedEmptyOrderSheetKeys`.
- Keep the existing three-argument overload. It delegates with an empty AV02 snapshot and treats
  all nonterminal P2P-required EMPTY sheets in its supplied order states as authorized. This is the
  explicit compatibility behavior for existing sticky-only fixtures. Construct the compatibility
  snapshot as `new Av02InventorySnapshot(1, List.of(), List.of())`; do not add a second empty
  singleton API.
- Resolve AV02 allocation history for an EMPTY sheet before examining active assignment state.
  Search both waiting and departed AV02 inventory and require at most one matching identity.
- If no AV02 identity exists, require that no active lifecycle assignment exists. Add the sheet to
  `unallocatedEmptyOrdersByServiceCentre` only when it is authorized; omit unauthorized work.
- If an AV02 identity exists in `ACTIVE_PRE_P2P`, require exactly one active assignment for the same
  physical ID and sheet at `PRE_P2P`, require the lifecycle record to use `PRE_P2P`, and count that
  physical ID under the identity's service centre. Never also emit the unallocated diagnostic.
- If the matching lifecycle record is `CONSUMED_AT_P2P`, require there is no active assignment and
  omit both physical work and the unallocated diagnostic. AV02 history therefore prevents a
  completed authorized EMPTY sheet from reappearing as unallocated work.
- Reject duplicate AV02 history, an active EMPTY assignment with no AV02 identity, wrong
  role/stage/service centre, any other lifecycle state, or an OSR manifest for the same physical ID.
- Add a `P2pWorkloadSnapshotFactory.create(...)` overload accepting the existing inputs followed by
  `Av02InventorySnapshot` and `PhysicalToteLifecycleSnapshot`. Keep the existing overload for
  OSR-only compatibility by delegating with empty AV02 state.
- Resolve every remaining physical tote from exactly one source. OSR IDs must have a matching
  manifest and `INBOUND_PACK` lifecycle role. AV02 IDs must have a matching AV02 identity and
  `PRE_P2P` lifecycle role. Reject IDs present in both or neither source and reject service-centre
  mismatches.
- Extend `DspP2pStickyLeaseRuntimeFactory` with an overload accepting
  `Supplier<Av02InventorySnapshot>` and `Supplier<Set<OrderSheetKey>>`. Existing overloads delegate
  with empty AV02 inventory and compatibility authorization.
- `DspP2pElasticAllocationRuntimeFactory` must use current supply
  `authorizedEmptyOrderSheetKeys()`, current AV02 inventory, and current lifecycle for both initial
  validation and every allocation snapshot. Add `Supplier<Av02InventorySnapshot>` to its `create`
  signature immediately after the lifecycle supplier. Do not cache these mutable source objects;
  obtain detached snapshots on each calculation.

### Behavioral tests

- Authorized, unallocated EMPTY appears only in the diagnostic map.
- Allocation replaces that diagnostic with the exact AV02 physical ID.
- The physical ID remains until `CONSUMED_AT_P2P`, then disappears without restoring the diagnostic.
- Unauthorized EMPTY is omitted.
- OSR behavior remains unchanged.
- Missing, duplicate, wrong-role, wrong-stage, and cross-service-centre source identities fail
  before workload estimation.
- Elastic demand uses the logical diagnostic before allocation and the physical tote afterwards
  without double counting.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticP2pAllocationPlannerTest
```

### User verification

No additional user-run verification is required for this step.

Proposed commit message: `Include AV02 totes in elastic workload`

## Step 11: Compose AV02 Into Operational Release

### Design intent

AV02 allocation and OSR/AV02 operational release remain separate controller responsibilities.
Operational release uses one evaluation source, one global candidate snapshot, and the existing
exact-type `CompositeOperationalCommandHandler`. This step wires existing components; it must not
introduce a second scheduler or a second transport path.

### Required change surface

Modify:

- `DspOperationalReleaseRuntimeFactory`
- `DspOperationalReleaseRuntimeFactoryTest`

Create:

- `DspAv02OperationalRuntimeTest` under the AV02 test package

Do not modify `DspOperationalReleaseRuntime`. It continues to own only the operational evaluation
controller and route-admission catalog. AV02 allocation, transport, elastic, supply, and simulation
world lifecycle remain with the composing scene/runtime.

### Locked factory composition

- Add exactly this overload to `DspOperationalReleaseRuntimeFactory`:

  ```java
  public DspOperationalReleaseRuntime createElasticWithAv02(
          OperationalReleaseEvaluationSource evaluationSource,
          OsrPhysicalInventory inventory,
          InboundToteLifecycleController lifecycleController,
          InboundToteManifestCatalog manifestCatalog,
          Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
          Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
          StationAdmissionResolver stationAdmissionResolver,
          OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry,
          Av02PhysicalToteInventory av02Inventory,
          PhysicalToteLifecycleLedger lifecycleLedger,
          MutableToteLoadPlanRegistry loadPlanRegistry,
          DspP2pElasticAllocationRuntime elasticRuntime)
  ```

  Use the existing argument names and validation conventions. The concrete launch registry is
  required because this composition needs both its OSR and AV02 adapter registries.
- Do not alter or delegate away the existing `createElastic(...)`; it remains the OSR-only
  compatibility entry point.
- The combined snapshot supplier must obtain one current logical snapshot, OSR physical snapshot,
  AV02 inventory snapshot, lease snapshot, route admissions, and elastic allocation snapshot, then
  call the existing AV02-aware `DspOperationalReleaseSnapshotFactory.create(...)` overload. Every
  object submitted to the worker remains detached and immutable.
- Construct the existing OSR command handler with the OSR processing target registry.
- Construct `Av02OperationalCommandHandler` with AV02 inventory, lifecycle ledger, shared mutable
  load plans, clock supplier, the launch registry's
  `operationalPhysicalToteReleaseTargetRegistry()`, and
  `elasticRuntime.operationalReleaseAssignmentCommitter()`.
- Wrap those handlers in the existing `CompositeOperationalCommandHandler` and give that one
  handler to the one `DspOperationalReleaseController`.
- Validate that route admissions contain exactly the configured five P2P destinations as existing
  elastic composition does. Validate that every configured route destination has both an OSR and
  AV02 release adapter target before constructing the controller.
- Keep AV02 inventory inspection outside `DspOperationalReleaseRuntime`; the composing debug rig
  already owns the inventory and can register its snapshot directly.
- Rejected/deferred AV02 target acceptance leaves inventory, assignment, load plan, leases, and
  launch queue unchanged. Applied acceptance enqueues first, commits the assignment, then records
  AV02 departure exactly as established in Step 7.

### Behavioral tests

- Mixed OSR and AV02 candidates are submitted in one snapshot and produce at most one applied
  command per completed evaluation.
- Full shared launch capacity defers either source without mutation.
- AV02 release uses the exact preselected P2P assignment and the exact generic launch request.
- OSR-only `createElastic(...)`, synchronous evaluation, and threaded evaluation remain compatible.
- A worker evaluation receives detached data and performs no inventory, lifecycle, lease, load-plan,
  target, or queue mutation.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalCommandHandlerTest
```

### User verification

No additional user-run verification is required for this step.

Proposed commit message: `Compose AV02 operational release`

## Step 12: Integrate AV02 Allocation, Launch, And Inspection In The Debug Runtime

### Design intent

The existing `dsp-warehouse-transport` scene is the visual integration target. It remains a cheap
debug rig, but one scenario entry must now originate from real AV02 allocation and operational
release rather than from its scheduled OSR request list.

### Required change surface

Modify:

- `DspWarehouseTransportDebugRig`
- `DspWarehouseTransportDebugRigTest`
- inspection helpers used by `warehouse_transport_state`

Do not add AV02 geometry. Reuse `warehouse_transport_state` as the selectable diagnostic object.

### Locked controller order and fixture behavior

- Construct one `Av02AllocationConfig`, `Av02PhysicalToteInventory`, deterministic ID allocator,
  shared `PhysicalToteLifecycleLedger`, and shared `MapBackedToteLoadPlanRegistry` for the rig.
- Use the existing `Av02AllocationSnapshotFactory` and `Av02AllocationController`; the rig owns the
  suppliers needed to submit and freshly revalidate one monotonically sequenced allocation command.
  Do not duplicate allocation logic in a rig controller.
- Register controllers in this order: clock/supply fixture updates, AV02 allocation,
  operational release evaluation/application, shared launch hydration, transport ingress,
  transport arrival, local station consumers, then lease retention/activity controllers.
- Replace exactly one current scheduled OSR fixture request with one logical EMPTY order authorized
  through the supply snapshot. Keep remaining OSR route examples so mixed-source behavior remains
  visible.
- The EMPTY fixture starts as non-rendered logical work, receives `av02-000001`, waits when launch
  capacity is unavailable, and creates its tote/renderable only during shared hydration.
- Route the EMPTY fixture to its real first station according to `RouteRequirements`; do not call a
  station queue or P2P target directly.
- Extend inspection with compact lines for AV02 capacity/occupancy, waiting and departed physical
  IDs/sheets, last allocation, last operational application result, launch source, selected first
  destination, and pinned P2P line. Keep existing transport/P2P diagnostics and current panel-width
  handling.
- `ALT+R` continues to reconstruct the complete rig; no component receives a mutable reset method.

### Behavioral tests

- Before allocation there is no AV02 renderable or launch entry.
- Allocation installs one empty load plan and one waiting inventory entry but no renderable.
- Backpressure retains the exact AV02 entry and assignment without duplicate launch.
- Acceptance removes the exact inventory head, hydrates once, and publishes one renderable into the
  same transport runtime as OSR requests.
- Inspection reports source `AV02`, physical ID `av02-000001`, pharmacy, destination, and assignment.
- Existing scheduled OSR examples and reset reconstruction remain deterministic.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest
```

### User verification

No additional user-run verification is required until the final visual checkpoint in Step 14.

Proposed commit message: `Integrate AV02 warehouse launch diagnostics`

## Step 13: Prove The EMPTY Flow End To End

### Required change surface

Create `DspAv02OperationalAllocationScenarioTest` under the AV02 test package. Keep it deterministic
and simulation-time driven; do not use sleeps, wall-clock polling, arbitrary tick-count assertions,
or debug-rig geometry.

### Locked scenarios and assertions

- Include one direct-P2P EMPTY order, one Third Party-first EMPTY order with an initially empty load
  plan, one EMPTY order with adapted dependencies, OSR FULL_PACK and ASSOCIATED work in the same
  service-centre cohort, a later authorized service centre, AV02 capacity one, and downstream
  launch/route backpressure.
- Authorization alone consumes no OSR or AV02 physical capacity.
- An unresolved adapted dependency prevents AV02 allocation.
- AV02 capacity creates at most one physical tote and deterministic IDs are monotonic.
- The allocated tote has one pharmacy, an empty initial load plan, and no manifest.
- One global release ranking compares AV02 and OSR candidates and applies at most one command.
- Every P2P-required AV02 tote is pinned before departure, including a non-P2P first destination.
- Third Party/Adapting completion mutates the exact physical tote's shared load plan; onward launch,
  station FIFO, and P2P arrival retain the same source, tote, pharmacy, destination, and assignment.
- P2P consumption terminates the inbound `PRE_P2P` assignment. Existing outbound allocation then
  introduces a different physical tote; generated output sheets remain outbound-owned.
- Prove no EMPTY manifest, OSR occupancy/departure event for AV02, duplicate command, direct
  AV02-to-P2P teleport, cross-pharmacy output, or wall-clock wait.

### Implementation verification

The implementation model runs:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest
```

### User verification

No additional user-run verification is required for this step.

Proposed commit message: `Prove operational EMPTY tote flow`

## Step 14: Regression, Visual Check, And Branch Closure

### Implementation verification

No model-run verification is required. The implementation model reviews the branch diff and reports
the exact user commands below without executing them.

### User verification

Run the focused regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.p2p.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Then run the complete suite:

```powershell
.\gradlew test
```

Run visual checks:

```powershell
.\gradlew run --args="--scene=dsp-warehouse-transport"
.\gradlew run --args="--scene=tote-to-bag"
```

Verify:

- AV02 allocation/waiting/source diagnostics are readable;
- an AV02 tote is not rendered until shared warehouse launch hydration;
- OSR and AV02 physical sources remain distinguishable;
- the AV02 tote follows the selected first-station route and never teleports to P2P;
- sticky owner/pharmacy/assignment diagnostics remain correct;
- existing OSR launches, warehouse transport, P2P processing, pack/bag visuals, and outbound tote
  capacity behavior are unchanged;
- `ALT+R` reconstructs both scenes deterministically.

Architecture verification:

- only logical EMPTY causes AV02 inbound tote allocation;
- OSR occupancy and departure history never include AV02 totes;
- P2P outbound tote supply remains independent and unchanged;
- one global scheduler ranks OSR and AV02 candidates;
- workers receive immutable snapshots and all mutations remain on the simulation thread;
- exact physical/source/pharmacy/load-plan/P2P identity survives the complete transport chain;
- no manifest is fabricated for EMPTY;
- no calibrated timing claim, Exception behavior, second route engine, or mutable reset was added.

Before branch closure:

- [ ] mark this plan implementation complete and verified;
- [ ] record final AV02 allocation, waiting, release, transport, workload, and P2P contracts;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] confirm focused/full tests and visual/reset checks are green;
- [ ] create the decision-complete plan for full-day execution and metrics.

Proposed commit message: `Complete AV02 operational allocation`

## Expected Final Contract

- Authorized dependency-ready EMPTY work receives a deterministic physical `PRE_P2P` tote through
  bounded AV02 allocation without consuming OSR capacity or fabricating a manifest.
- AV02 and OSR physical candidates participate in one global operational scheduler and one-command
  application boundary.
- Every P2P-required AV02 tote is pinned to one exact elastic feeding line before departure and
  retains that assignment through its actual first-station route and P2P arrival.
- Source-neutral launch identity lets OSR and AV02 totes share hydration, warehouse transport,
  station queues, and P2P arrival without a second route engine.
- Allocated EMPTY work contributes physical P2P workload until consumption; unallocated authorized
  EMPTY remains an explicit diagnostic.
- P2P consumes the AV02 inbound tote. Independent outbound reservoirs, bag-capacity closure, and
  generated output sheet numbers remain owned by existing outbound allocation.
