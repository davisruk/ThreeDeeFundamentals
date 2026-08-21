# DSP Operational Route-Target Integration Plan

Branch: `feature/dsp-operational-route-target-integration`

Status: plan ready; implementation not started.

## Purpose

Connect dependency-ready operational OSR release to production simulation-owned route-entry
waiting boundaries. A selected target ID must identify the same bounded queue during immutable
scheduler evaluation and during live command application.

This branch must:

- add a non-rendering route-entry queue for physical manifests released from OSR;
- preserve each `OsrProcessingReleaseRequest`, including exact `PhysicalToteId`, manifest, and
  release time;
- represent Third Party, Adapting, and P2P route-entry targets with configured queue instances;
- make candidate-specific live station resolution and route-target queue capacity part of the
  immutable operational snapshot;
- require an open station admission and an existing queue target before a candidate is eligible;
- revalidate queue capacity when `OsrProcessingReleaseCommandHandler` invokes the target;
- commit OSR departure and lifecycle activation only after queue acceptance succeeds;
- provide a production runtime composition that wires inventory, lifecycle, snapshot creation,
  scheduler evaluation, target registry, handler, and controller;
- preserve the scheduler worker/simulation-thread mutation boundary;
- retain the legacy order-centric debug scheduler and current visual rigs as compatibility paths.

This branch does not hydrate renderable totes from queued manifests, move totes on tracks, process
station work, implement EMPTY/AV02, implement sticky P2P service-centre leases, select among P2P
lines using leases or pharmacy affinity, add deadlines, change station animations, or run a full
day's dataset.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-dependency-ready-operational-release-plan.md`
4. `docs/scheduler/dsp-osr-processing-release-plan.md`
5. `docs/scheduler/dsp-scheduler-implementation-plan.md`
6. `docs/scheduler/machine-wait-queues-plan.md`

Inspect these classes before each affected step:

- `OsrProcessingReleaseTarget`
- `OsrProcessingReleaseTargetRegistry`
- `OsrProcessingReleaseRequest`
- `OsrProcessingReleaseCommandHandler`
- `DspOperationalReleaseSnapshot`
- `DspOperationalReleaseSnapshotFactory`
- `OperationalRouteEntrySelector`
- `OperationalRouteEntryAdmissionPolicy`
- `OperationalStationAdmissionResolver`
- `SnapshotOperationalStationAdmissionResolver`
- `StationAdmissionResolver`
- `StationAdmissionSnapshot`
- `ThirdPartyStationAdmissionResolver`
- `ThirdPartyStationAdmissionAdapter`
- `AdaptingStationAdmissionResolver`
- `AdaptingStationAdmissionAdapter`
- `P2pStationAdmissionResolver`
- `P2pCapacityStationAdapter`
- `DspOperationalReleaseController`
- `MachineWaitQueue`

## Fixed Decisions

Do not revisit these decisions during implementation:

- The route-entry queue is a simulation-domain waiting boundary. It stores release requests and
  creates no `Tote`, `Pack`, `RenderableObject`, route follower, or track geometry.
- One queue instance represents one opaque target ID. Target IDs are configured, trimmed,
  nonblank, and globally unique within the operational runtime.
- A target also has exactly one `StationType`. The implemented route-entry station types are
  `THIRD_PARTY`, `ADAPTING`, and `P2P`.
- MANUAL and MANUAL_MERGE remain outside active loaded data. Do not create placeholder targets for
  them. Their candidates remain blocked by missing target/admission state.
- EMPTY has no inbound OSR manifest and remains outside this branch.
- Queue capacity is a manually configured physical waiting capacity. Do not calculate it from
  track length or tote dimensions.
- Compose the established `MachineWaitQueue` for bounded FIFO physical-ID storage. Keep the full
  request map in `OperationalRouteEntryQueue`; do not copy the generic queue's capacity logic.
- Queue order is FIFO and preserves accepted request identity and release time.
- A queue rejects duplicate physical tote IDs. It must not silently replace or append a second
  request for the same physical tote.
- `accept(...)` returns deferred when the queue is full, rejected when identity is invalid or
  duplicated, and applied only after the request is stored exactly once.
- A target application must not remove the request from its queue if the later OSR inventory or
  lifecycle commit unexpectedly fails. Such a failure is an invariant violation; do not invent a
  rollback protocol in this slice.
- The command handler remains downstream-first: target acceptance, then OSR departure, then
  lifecycle activation at one authoritative simulation time.
- Candidate-specific station admission is evaluated on the simulation thread by the existing
  `StationAdmissionResolver` chain. Mutable areas and queues are never read on the scheduler
  worker.
- The immutable operational snapshot stores the exact resolved station admission for each
  `PhysicalToteId`. Do not reuse one station-wide admission for every candidate.
- The resolved station admission must include a selected target ID when open. That ID must resolve
  to a configured route-entry queue for the same station type.
- Effective admission is open only when both the existing station admission and selected queue
  capacity are open. Preserve the station's blocked reason before considering queue capacity.
- If the station-selected target is unknown or belongs to another station, block the candidate in
  the immutable snapshot. Do not allow command application to discover routine configuration
  mismatch after selection.
- Target queue capacity is snapshotted for worker evaluation and revalidated live by
  `QueuedOsrProcessingReleaseTarget.accept(...)`.
- Third Party and P2P admission adapters currently omit selected IDs; add configured target IDs.
  Keep their existing capacity and candidate rules unchanged.
- Adapting already selects a bench ID. Configure one route-entry target queue per selectable bench
  using that exact bench ID. Do not introduce a second adapting target namespace.
- P2P uses one configured route-entry target in this branch. Multiple P2P target selection and
  sticky service-centre ownership belong to the next branch.
- The route-entry queue is consumed by later route/machine integration. Do not drain it
  automatically in this feature merely to keep tests moving.
- Preserve the existing three-argument operational snapshot-factory path for focused legacy
  fixtures. Production runtime assembly must use the new candidate-specific live-admission path.
- The production runtime composition does not own inventory, lifecycle, clock, manifest catalog,
  logical runtime state, or station components. It wires supplied instances and owns only its
  operational evaluation source/controller lifecycle.
- `DspOperationalReleaseRuntime.close()` is idempotent and closes its controller/evaluation source
  exactly once.
- Reset remains reconstruction. Do not add mutable `reset()` methods to queues, registries,
  controller, evaluation source, inventory, or lifecycle objects.
- The user runs Gradle. Ask for the focused command after each coding step and wait for feedback.

## Package And Vocabulary

Create route-entry queue and target types under:

```text
online.davisfamily.warehouse.sim.dsp.osr.release.route
```

Create immutable candidate-admission and runtime composition types under the existing:

```text
online.davisfamily.warehouse.sim.dsp.scheduler.operational
online.davisfamily.warehouse.sim.dsp.runtime.operational
```

Use these names:

- `OperationalRouteTargetDefinition`
- `OperationalRouteEntryQueue`
- `OperationalRouteEntryQueueSnapshot`
- `QueuedOsrProcessingReleaseTarget`
- `OperationalRouteTargetRegistry`
- `OperationalCandidateRouteAdmission`
- `OperationalCandidateRouteAdmissionFactory`
- `CandidateOperationalStationAdmissionResolver`
- `DspOperationalReleaseRuntime`
- `DspOperationalReleaseRuntimeFactory`

Do not create another physical inventory, lifecycle ledger, scheduler command, target interface,
logical order catalog, renderable payload, or generic machine-state framework.

## Step 1: Define Route-Target Configuration And Queue Snapshot

Create immutable records equivalent to:

```java
public record OperationalRouteTargetDefinition(
        StationType stationType,
        String targetId,
        int waitingCapacity) {}

public record OperationalRouteEntryQueueSnapshot(
        StationType stationType,
        String targetId,
        int capacity,
        List<PhysicalToteId> physicalToteIds) {}
```

Rules:

- reject null station type, blank target ID, negative capacity, null lists/elements, duplicate
  physical IDs, and occupancy greater than capacity;
- normalize target ID once at construction;
- permit zero-capacity targets so a configured target can be intentionally closed;
- preserve FIFO physical-ID order with defensive immutable copies;
- expose `occupancy()`, `remainingCapacity()`, and `canAccept()` as derived methods rather than
  stored fields.

Tests in `OperationalRouteEntryQueueSnapshotTest` must cover valid empty/full snapshots, zero
capacity, normalization, immutability, duplicate IDs, and inconsistent derived state.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueSnapshotTest
```

Expected result: green.

## Step 2: Implement The FIFO Route-Entry Queue And Target

Create `OperationalRouteEntryQueue` with:

```java
public boolean canAccept()
public void enqueue(OsrProcessingReleaseRequest request)
public Optional<OsrProcessingReleaseRequest> peek()
public Optional<OsrProcessingReleaseRequest> dequeue()
public OperationalRouteEntryQueueSnapshot snapshot()
```

Queue rules:

- construct it from one `OperationalRouteTargetDefinition` and one internal `MachineWaitQueue`;
- use `MachineWaitQueue` for bounded FIFO physical IDs and retain full requests in a map keyed by
  the same normalized physical IDs;
- validate that request manifest identity is non-null through the existing request/manifest types;
- check duplicate identity before capacity so duplicates are rejected consistently;
- `enqueue` throws for programming/invariant violations and for direct over-capacity use;
- `dequeue` removes both FIFO request and duplicate index entry;
- no method exposes a mutable collection.

Create `QueuedOsrProcessingReleaseTarget` implementing the existing
`OsrProcessingReleaseTarget`:

- `targetId()` delegates to the queue definition;
- `accept(null)` throws;
- duplicate physical identity returns rejected without mutation;
- full queue returns deferred without mutation;
- otherwise enqueue exactly once and return applied;
- reason text names the target and physical tote where useful.

Tests must prove FIFO behavior, request/release-time preservation, duplicate rejection, full
deferral, capacity recovery after dequeue, and exactly-once storage.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.QueuedOsrProcessingReleaseTargetTest
```

Expected result: green.

## Step 3: Add The Operational Route-Target Registry

Create `OperationalRouteTargetRegistry` from a list of
`OperationalRouteEntryQueue` instances.

It must:

- reject null queues/elements, blank normalized IDs, duplicate target IDs, and duplicate queue
  object registration;
- preserve configured order;
- find a queue by normalized target ID;
- return immutable queues/definitions for one `StationType` in configured order;
- expose `List<OsrProcessingReleaseTarget> releaseTargets()` using one
  `QueuedOsrProcessingReleaseTarget` per queue;
- expose `OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry()` without allowing
  target definitions and handler targets to diverge;
- provide immutable queue snapshots for inspection and tests.

Do not add fallback target lookup or station-name inference.

Tests must cover lookup normalization, global ID uniqueness across station types, stable order,
station filtering, immutable publication, and construction of the existing handler registry.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistryTest
```

Expected result: green.

## Step 4: Make Existing Live Admission Select Concrete Target IDs

Update only target-ID publication in the existing station adapters/resolvers:

- `ThirdPartyStationAdmissionAdapter` receives a configured Third Party target ID and publishes it
  when its existing admission result is open;
- `ThirdPartyStationAdmissionResolver` passes that configured ID;
- `P2pCapacityStationAdapter` receives a configured P2P target ID and publishes it when its
  existing candidate/capacity checks are open;
- `P2pStationAdmissionResolver` passes that configured ID;
- `AdaptingStationAdmissionAdapter` remains bench-selecting and continues to publish the selected
  bench ID;
- closed admissions publish no selected target ID;
- retain source-compatible convenience constructors only where current callers need them, but
  those compatibility constructors must not fabricate an operational target ID.

Do not change Third Party visit classification, adapting bench selection, P2P local processing
admission, capacity arithmetic, or blocked-reason precedence.

Update focused tests to prove open/closed target-ID behavior and compatibility construction.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationAdmissionAdapterTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartySchedulerIntegrationTest --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionAdapterTest --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolverTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingSchedulerIntegrationTest
```

Expected result: green.

## Step 5: Capture Exact Candidate-Specific Route Admission

Create:

```java
public record OperationalCandidateRouteAdmission(
        PhysicalToteId physicalToteId,
        StationAdmissionSnapshot stationAdmission) {}
```

Validation:

- reject null values;
- require an open effective admission to carry a selected target ID;
- preserve the existing station snapshot/capacity invariants.

Create `OperationalCandidateRouteAdmissionFactory` with dependencies on:

- `OperationalRouteEntrySelector`;
- the live `StationAdmissionResolver` chain;
- `OperationalRouteTargetRegistry`.

For each joined operational candidate, on the simulation thread:

1. Determine the first route-entry station.
2. Ask the existing live resolver for that candidate's station admission using its exact logical
   order state and the same `WarehouseSchedulerSnapshot` used for joining.
3. If no route entry exists, return no candidate admission; the existing route-entry policy must
   continue to report `ROUTE_ENTRY`.
4. Preserve a closed station admission and its reason without requiring a target.
5. For an open station admission, require its selected target to exist in the route-target
   registry and have the same station type.
6. Combine station acceptance with the selected queue snapshot. If the queue is full, publish a
   closed effective admission with a queue-specific blocked reason and no selected target.
7. If the target is unknown or has the wrong station type, publish a closed effective admission
   with an explicit configuration reason; do not throw for this inspectable operational block.
8. Otherwise publish the open admission with the exact selected target ID unchanged.

Create `CandidateOperationalStationAdmissionResolver` implementing
`OperationalStationAdmissionResolver`. It must resolve only the entry matching both candidate
physical ID and requested station type. Missing data returns null so the existing policy produces
the established admission block.

Tests must cover two candidates of one station resolving different targets, one full selected
queue, closed station precedence, unknown target, wrong-station target, missing route, and no
mutable station/queue reads by the candidate resolver.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.CandidateOperationalStationAdmissionResolverTest
```

Expected result: green.

## Step 6: Integrate Candidate Admissions Into Operational Snapshots

Extend `DspOperationalReleaseSnapshot` with an immutable ordered list of
`OperationalCandidateRouteAdmission` values.

Snapshot validation must:

- reject null entries and duplicate physical IDs;
- require every admission physical ID to identify a candidate in the same snapshot;
- require its station type to equal that candidate's first route-entry station;
- permit candidates with no admission so missing configuration remains an observable block;
- expose lookup by physical ID and station type.

Update `DspOperationalReleaseSnapshotFactory`:

- preserve the current three-argument `create(...)` path for existing focused fixtures by deriving
  candidate admissions from the immutable logical snapshot's station map;
- add a production overload taking `OperationalCandidateRouteAdmissionFactory` and use the live
  resolver/queue snapshots only while building the immutable operational snapshot;
- join/validate physical and logical candidates before resolving live admission;
- pass the same `WarehouseSchedulerSnapshot` instance into every candidate resolution;
- never retain the live resolver, queue, area, or mutable runtime state inside the snapshot.

Change the default operational scheduler admission path to use
`CandidateOperationalStationAdmissionResolver`. Keep
`SnapshotOperationalStationAdmissionResolver` available for direct compatibility tests, but the
production runtime in Step 7 must not use it.

Update tests for immutable capture, candidate-specific selection, queue-full blocking, unchanged
dependency/ranking behavior, and the compatibility overload.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntryAdmissionPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest
```

Expected result: green.

## Step 7: Add Production Operational Runtime Composition

Create `DspOperationalReleaseRuntime` as an `AutoCloseable` composition result exposing:

- `DspOperationalReleaseController controller()`;
- `OperationalRouteTargetRegistry routeTargetRegistry()`;
- immutable route-entry queue snapshots for inspection;
- `close()` delegating exactly once to the controller/evaluation source lifecycle.

Create `DspOperationalReleaseRuntimeFactory`. Its explicit inputs must include:

- `OperationalReleaseEvaluationSource`;
- `OsrPhysicalInventory`;
- `InboundToteLifecycleController`;
- `InboundToteManifestCatalog`;
- `DspSchedulerRuntimeState` or a supplier of `WarehouseSchedulerSnapshot`;
- `Supplier<DspOperationalClockSnapshot>`;
- the live `StationAdmissionResolver` chain;
- `OperationalRouteTargetRegistry`.

The factory must wire:

1. `OsrProcessingReleaseSnapshotFactory` from current inventory/lifecycle snapshots.
2. `DspOperationalReleaseSnapshotFactory` through the production candidate-admission path.
3. `OsrProcessingReleaseCommandHandler` using the route registry's exact existing target registry.
4. `DspOperationalReleaseController` using the supplied evaluation source.

Rules:

- do not create or copy mutable inventory/lifecycle/logical state;
- do not cache operational snapshots between controller submissions;
- do not create a second evaluation executor;
- do not register the controller with a `SimulationWorld`; the caller owns controller order;
- reject null suppliers and null supplier results at their established boundaries;
- runtime close is idempotent, closes the controller/evaluation source exactly once, and does not
  close externally owned station/inventory/lifecycle objects.

Tests must prove exact object wiring, fresh snapshots after queue mutation, synchronous and threaded
source compatibility, no legacy `markReleased(...)`, and idempotent runtime close with exactly one
delegated controller/evaluation-source close.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeTest
```

Expected result: green.

## Step 8: Prove Downstream-First End-To-End Application

Add `DspOperationalRouteTargetIntegrationScenarioTest` using real:

- physical manifest catalog;
- OSR inventory and inbound lifecycle controller;
- logical runtime state;
- Third Party, Adapting-bench, and P2P route target definitions/queues;
- candidate-specific live resolver chain;
- operational snapshot factory, scheduler, runtime factory, handler, and controller.

Required scenarios:

1. A Third Party candidate leaves OSR and appears exactly once in the configured Third Party queue.
2. Two ADAPTED candidates selected for different benches enter their exact bench queues; target IDs
   are not swapped or inferred from station type.
3. A P2P candidate enters the configured P2P ingress queue.
4. A full selected queue blocks evaluation without invoking the handler or changing inventory and
   lifecycle state.
5. Capacity consumed after worker evaluation but before command application causes live target
   deferral; inventory/lifecycle remain unchanged and a fresh evaluation observes the full queue.
6. Dequeueing the blocker permits a later fresh evaluation to apply the original candidate once.
7. Unknown/wrong-station target configuration is observable as a block and emits no command.
8. Successful application stores the original manifest and release time in the queue, records OSR
   departure, and activates the same physical tote at the same time.
9. Repeated physical manifests for one logical sheet remain sequenced; queue integration does not
   mutate logical release status or collapse physical identity.

Use bounded controller updates and synchronous evaluation for deterministic scenario timing. Keep
threaded behavior in its focused source tests.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalRouteTargetIntegrationScenarioTest
```

Expected result: green.

## Step 9: Run Focused And Full Regression Coverage

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.p2p.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.osr.*
```

Then ask the user to run:

```powershell
.\gradlew test
```

Expected result: both commands green.

Do not weaken assertions, increase timing windows, or migrate visual rigs to make regressions pass.

## Step 10: Compatibility Smoke Check And Branch Closure

No new visual scene is required because route-entry queues intentionally contain non-rendering
release requests. Ask the user to smoke-test the existing:

- tote-to-bag/P2P scene;
- adapting scene;
- Third Party scene;
- `ALT+R` reset in each scene.

Expected behavior: unchanged legacy visuals and reset behavior.

Before branch closure:

- [ ] update this plan status to implementation complete and verified;
- [ ] record final route queue, candidate-admission, target, and runtime composition contracts;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] confirm focused tests, complete suite, visual smoke checks, and reset checks are green;
- [ ] reassess whether sticky P2P leases can consume the new P2P target queue directly or require a
  small physical-tote hydration/queue-consumer slice first.

## Preserved Contracts For Follow-On Work

- Queued release requests retain exact physical identity, manifest provenance, and authoritative
  release time.
- Target IDs bind immutable candidate admission and live command application to one queue.
- Station admission and target queue capacity are candidate-specific immutable worker inputs.
- Queue mutation, OSR departure, and lifecycle activation occur only on the simulation thread.
- Route-entry queues do not allocate renderables or simulate downstream station processing.
- Sticky P2P lease policy may choose among P2P target queues later, but it must not change queue,
  handler, inventory, or lifecycle commit semantics.

## Follow-On Branch

After this branch is green and merged, reassess and normally create:

```text
feature/dsp-p2p-sticky-service-centre-leases
```

If a real P2P line cannot consume the non-rendering ingress request without another architectural
decision, create a narrowly scoped hydration/queue-consumer plan first. Do not hide renderable
creation or track placement inside the sticky lease policy.
