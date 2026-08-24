# DSP AV02 Operational Allocation Plan

Branch: `feature/dsp-av02-operational-allocation`

Status: decision-complete plan; implementation has not started.

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

## Step 8: Remove The Inbound-Manifest Assumption From Transport

Scope:

- Add source-neutral `OperationalRouteLaunchRequest` and migrate downstream hydration/transport
  interfaces to it.
- Adapt the existing OSR launch target/controller to publish that request while preserving exact
  OSR release/manifest ownership in the OSR-specific upstream object.
- Add the AV02 launch adapter/controller using the same bounded launch-to-transport ownership
  sequence. Both sources feed the existing warehouse transport ingress and route catalog; do not
  duplicate route followers, transfer routing, in-flight registry, arrival controllers, or station
  queues.
- Change `RoutedPhysicalTote` and `P2pArrivalAdmissionRequest.from(...)` to use source-neutral
  identity rather than dereferencing an inbound manifest.
- Migrate `DetachedOutboundToteFactory`, `DetachedToteRenderableFactory`, and load-plan hydration to
  source-neutral launch requests. Preserve exact request/load-plan/tote/renderable identity checks.
- Keep compatibility adapters for OSR-focused fixture construction where practical; update tests
  mechanically where the static request type must become source-neutral.
- Prove AV02 and OSR requests traverse the same transport runtime, preserve source identity, arrive
  at the exact first station, and never teleport directly to P2P.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.*
```

Proposed commit message: `Generalize warehouse launch identity`

## Step 9: Count Allocated EMPTY Totes As P2P Work

Scope:

- Extend `P2pServiceCentreWorkSnapshotFactory` to resolve an EMPTY sheet's active `PRE_P2P`
  assignment from lifecycle state without requiring an inbound manifest.
- Count the allocated physical tote under its exact service centre until `CONSUMED_AT_P2P`.
- Retain only authorized, unallocated EMPTY sheets in
  `unallocatedEmptyOrdersByServiceCentre`; remove a sheet from diagnostics as soon as AV02 creates
  its physical assignment.
- Extend `P2pWorkloadSnapshotFactory` validation so AV02 physical totes are validated through
  lifecycle/source-neutral identity rather than `InboundToteManifestCatalog`.
- Preserve existing manifest validation for OSR-originated totes and reject ambiguous/mismatched
  source identity.
- Prove elastic demand sees unallocated EMPTY diagnostics before allocation, physical tote work
  after allocation, and no remaining work after P2P consumption.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticP2pAllocationPlannerTest
```

Proposed commit message: `Include AV02 totes in elastic workload`

## Step 10: Compose AV02 Into The Operational Runtime

Scope:

- Extend elastic operational runtime composition with AV02 config, allocation controller,
  inventory, deterministic ID allocator, shared mutable load-plan registry, and composite command
  handler.
- Register controller order explicitly: clock/supply updates, AV02 allocation snapshot/application,
  operational snapshot/evaluation/application, launch hydration, transport ingress/arrival, local
  station consumers, and lease retention.
- Ensure every scheduler-worker input is detached and immutable. No worker may call AV02 inventory,
  lifecycle, load-plan registry, lease registry, or route queues.
- Add compact AV02 inspection: capacity/occupancy, waiting IDs/sheets, last allocation/departure,
  blocked reason, source identity, and current route/P2P assignment.
- Reuse `warehouse_transport_state` for fixture diagnostics unless a selectable AV02 placeholder is
  already available. Do not add detailed geometry.
- Preserve synchronous fallback, threaded evaluation, and `ALT+R` reconstruction.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalRuntimeTest --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Proposed commit message: `Compose AV02 operational allocation`

## Step 11: Prove The EMPTY Flow End To End

Scope:

- Add a deterministic simulation-time scenario containing:
  - one direct-P2P EMPTY order;
  - one Third Party-only EMPTY order whose initial load plan is empty;
  - one EMPTY order with adapted dependencies;
  - OSR-originated FULL_PACK and ASSOCIATED work in the same service-centre cohort;
  - a later authorized service centre;
  - AV02 and downstream queue backpressure.
- Prove authorization does not consume OSR capacity, unresolved dependencies prevent AV02
  allocation, and AV02 capacity bounds physical creation.
- Complete dependencies explicitly, allocate one deterministic physical tote, install its empty
  load plan, rank it globally with OSR candidates, pin it to one elastic feeding line, and release it
  to its real first station.
- For Third Party/Adapting cases, prove station completion mutates the same physical tote's load plan
  and onward transport retains physical/source/P2P identity.
- Prove P2P consumption terminates the inbound `PRE_P2P` tote while outbound allocation introduces
  a separate physical tote. Capacity overflow remains covered by existing outbound generated-sheet
  tests rather than being attributed to AV02.
- Prove no fabricated EMPTY manifest, no OSR occupancy/departure event, no duplicate command, no
  tote teleport, no cross-pharmacy output, and no wall-clock waits.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest
```

Proposed commit message: `Prove operational EMPTY tote flow`

## Step 12: Regression, Visual Check, And Branch Closure

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.p2p.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Then ask the user to run:

```powershell
.\gradlew test
```

Visual checks:

```powershell
.\gradlew run --args="--scene=dsp-warehouse-transport"
.\gradlew run --args="--scene=tote-to-bag"
```

Verify:

- AV02 allocation/waiting/source diagnostics are readable;
- an AV02 tote is not rendered until warehouse launch hydration;
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
- exact physical/source/load-plan/P2P identity survives the complete transport chain;
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
