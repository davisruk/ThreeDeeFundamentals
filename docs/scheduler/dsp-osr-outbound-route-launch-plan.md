# DSP OSR Outbound Route-Launch Plan

Branch: `feature/dsp-osr-outbound-route-launch`

Status: plan ready; implementation not started.

## Purpose

Correctly bridge operational OSR release to the warehouse transport network without placing a
released tote directly at its first processing station. A selected Third Party, Adapting, or P2P
target is routing destination intent. Physical hydration begins at the common OSR outbound
boundary, after which later transport routing moves the tote through the warehouse and delivers it
to a station-local arrival queue.

The intended physical sequence is:

```text
OSR
  -> shared outbound route-launch queue
  -> hydrated outbound transport queue
  -> warehouse conveyor/transfer routing
  -> station-local arrival queue
  -> station processing
```

This branch must:

- replace production use of one fictional station-local release queue per target with target
  adapters feeding one shared OSR outbound launch queue;
- retain the selected `StationType` and exact target ID as immutable route destination metadata;
- preserve command target validation and downstream-first OSR departure semantics;
- gate OSR departure by shared outbound launch capacity, not by direct `TipperInputQueue` capacity;
- preserve candidate-specific station/target selection while separating destination selection from
  physical placement;
- hydrate a released request into one detached routed physical tote on the simulation thread;
- transfer hydrated totes into a bounded generic outbound transport queue;
- expose immutable launch/transport state for later routing, inspection, and tests;
- keep current per-target route-entry queues as an explicit compatibility path for completed tests
  and legacy fixtures;
- prove P2P-bound work remains at the OSR outbound transport boundary and is not inserted into a
  P2P tipper queue.

This branch does not build the full warehouse track layout, route a tote through transfer machines,
consume any station-local queue, connect directly to P2P, implement sticky P2P leases, choose among
five P2P lines, implement Exception handling, change logical dependency/ranking policy, calibrate
travel times, or run a full day's dataset.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-route-target-integration-plan.md`
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`
4. `docs/scheduler/machine-wait-queues-plan.md`
5. `docs/tipper-route-mounted-machine-architecture.md`
6. `docs/machines/phase-1-stations-roadmap.md`

Inspect these classes before each affected step:

- `ReleasePhysicalToteFromOsrCommand`
- `OsrProcessingReleaseRequest`
- `OsrProcessingReleaseTarget`
- `OsrProcessingReleaseTargetRegistry`
- `OsrProcessingReleaseCommandHandler`
- `OperationalRouteTargetDefinition`
- `OperationalRouteEntryQueue`
- `OperationalRouteTargetRegistry`
- `OperationalCandidateRouteAdmissionFactory`
- `DspOperationalReleaseRuntime`
- `DspOperationalReleaseRuntimeFactory`
- `ToteLoadPlan`
- `ToteLoadPlanProvider`
- `InboundToteManifestCatalog`
- `PhysicalToteLifecycleSnapshot`
- `Tote`
- `RouteFollower`
- `RouteSegment`
- `RenderableToteFactory`

## Fixed Decisions

Do not revisit these decisions during implementation:

- OSR is not physically adjacent to P2P. P2P is normally the final processing area before bagged
  outbound flow, except for later Exception handling.
- A logical first station means "route toward this destination without an earlier processing
  visit." It never means "teleport the tote into that machine's local queue."
- The selected target ID remains destination intent. It is carried with the physical tote until
  transport routing reaches the corresponding station arrival boundary.
- One shared `OsrOutboundRouteLaunchQueue` is the authoritative production release target buffer.
  It preserves global scheduler release order across Third Party, Adapting, and P2P destinations.
- Target-specific `OsrProcessingReleaseTarget` adapters validate destination identity and append a
  destination-bearing request to the same shared queue.
- Shared launch capacity is configured from the OSR outbound/transport staging boundary. Do not use
  tipper, adapting-bench, Third Party area, PRL, PCR, or bagger capacity as this queue's capacity.
- Existing live station resolvers may continue to determine candidate destination eligibility and
  target selection in this branch. This is a scheduling decision, not physical placement. Whether
  OSR release should later depend only on network capacity is a separate policy experiment.
- Production target-capacity snapshotting must read the shared launch queue once per operational
  snapshot. Every configured destination sees that same capacity/occupancy state.
- A command still names the exact selected destination target. Unknown or wrong-station targets are
  blocked before command emission and rejected again by live registry lookup.
- The command handler remains downstream-first: launch-target acceptance, OSR departure, then
  lifecycle activation at one authoritative simulation time.
- Target acceptance stores the exact `OsrProcessingReleaseRequest` plus destination. Do not copy the
  manifest, allocate a tote, create renderables, or touch a route follower in the command handler.
- Hydration and launch consumption run only on the simulation thread after command application.
- Hydration resolves an existing `ToteLoadPlan`; it does not perform bag planning, invent packs, or
  mutate logical order status.
- Request manifest physical ID, load-plan physical ID, hydrated tote ID, renderable ID, and route
  follower ID must match exactly.
- Hydration creates a detached tote payload. Publication to a real route/scene belongs to the later
  warehouse transport-routing composition, not the release target.
- A bounded `OsrOutboundTransportQueue` owns hydrated routed totes awaiting physical transport.
  Its capacity is distinct from launch-request capacity and represents physical outbound transport
  staging.
- If the outbound transport queue is full, leave the launch request at its head and do not hydrate.
- Missing plans or expected hydration failure leave both queues unchanged and are inspectable.
- Process at most one launch request per simulation update. Do not burst-drain after capacity
  returns.
- Destination-specific FIFO is a consequence of the one shared global FIFO. Do not introduce one
  controller per station target or infer ordering from maps.
- `TipperInputQueue`, `TipperInputQueueController`, `ToteTrackTipperFlowController`, Adapting benches,
  and Third Party area queues are not dependencies of this feature.
- Keep `OperationalRouteEntryQueue` and `OperationalRouteTargetRegistry` source-compatible for
  focused compatibility tests. Production runtime composition must use the new launch registry.
- No scheduler worker reads mutable launch/transport queues. Immutable snapshots are constructed on
  the simulation thread.
- Reset remains reconstruction. Do not add mutable reset methods.
- The user runs Gradle. Ask for the focused command and a proposed commit message after every coding
  step, then wait for feedback.

## Package And Vocabulary

Create launch domain types under:

```text
online.davisfamily.warehouse.sim.dsp.osr.release.launch
```

Create generic routed-tote types under:

```text
online.davisfamily.warehouse.sim.dsp.transport
```

Use these names:

- `OperationalRouteDestination`
- `OsrOutboundRouteLaunchRequest`
- `OsrOutboundRouteLaunchQueue`
- `OsrOutboundRouteLaunchQueueSnapshot`
- `OsrOutboundRouteLaunchTarget`
- `OsrOutboundRouteLaunchTargetRegistry`
- `OperationalRouteTargetAdmissionCatalog`
- `OperationalRouteTargetAdmissionSnapshot`
- `RoutedPhysicalTote`
- `OsrOutboundToteHydrator`
- `OsrOutboundToteHydrationException`
- `OsrOutboundTransportQueue`
- `OsrOutboundTransportQueueSnapshot`
- `OsrOutboundRouteLaunchController`
- `OsrOutboundRouteLaunchControllerSnapshot`

Do not create another scheduler, physical inventory, lifecycle ledger, command type, logical route
requirements type, station-local queue, generic event bus, or render thread.

## Step 1: Define Destination-Bearing Launch Requests

Create immutable records equivalent to:

```java
public record OperationalRouteDestination(
        StationType stationType,
        String targetId) {}

public record OsrOutboundRouteLaunchRequest(
        OsrProcessingReleaseRequest releaseRequest,
        OperationalRouteDestination destination) {}
```

Rules:

- support only `THIRD_PARTY`, `ADAPTING`, and `P2P` destination stations in this slice;
- reject null/blank values and normalize target ID once;
- retain the exact release-request instance, manifest, physical ID, and release time;
- provide derived `physicalToteId()` without storing duplicate identity;
- do not contain route geometry, a `Tote`, renderables, load plans, or station-capacity state.

Tests must cover all supported stations, unsupported stations, normalization, nulls, exact object
identity, and release-time preservation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestinationTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequestTest
```

Expected result: green.

## Step 2: Add The Shared Global Launch Queue

Create `OsrOutboundRouteLaunchQueueSnapshot` containing queue ID, capacity, and immutable ordered
entries of physical ID plus destination. Expose derived occupancy, remaining capacity, and
`canAccept()`.

Create `OsrOutboundRouteLaunchQueue` using `MachineWaitQueue` for bounded FIFO physical IDs and a
map retaining full launch requests.

Required API:

```java
public boolean canAccept()
public boolean contains(PhysicalToteId physicalToteId)
public void enqueue(OsrOutboundRouteLaunchRequest request)
public Optional<OsrOutboundRouteLaunchRequest> peek()
public Optional<OsrOutboundRouteLaunchRequest> dequeue()
public OsrOutboundRouteLaunchQueueSnapshot snapshot()
```

Rules:

- capacity zero is valid;
- reject duplicate physical identities before capacity checks;
- preserve one global FIFO across destinations;
- keep full request ownership aligned with the generic queue identity list;
- never expose mutable collections;
- detect queue/map invariant mismatch clearly.

Tests must cover mixed-destination FIFO, duplicate rejection, full/zero capacity, dequeue recovery,
exact request preservation, derived snapshot state, and immutability.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueueTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueueSnapshotTest
```

Expected result: green.

## Step 3: Adapt Destination Targets To The Shared Queue

Create `OsrOutboundRouteLaunchTarget` implementing `OsrProcessingReleaseTarget` from one
`OperationalRouteDestination` and the shared launch queue.

Behavior:

- `targetId()` is the destination target ID;
- duplicate physical ID returns rejected without mutation;
- full shared queue returns deferred without mutation;
- otherwise wrap the exact release request with the configured destination and enqueue once;
- return applied only after the launch request is stored;
- do not hydrate or publish a tote.

Create `OsrOutboundRouteLaunchTargetRegistry` from:

- one shared launch queue;
- an ordered list of `OperationalRouteDestination` values.

The registry must validate globally unique target IDs, expose ordered destinations, construct one
target adapter per destination, and expose the existing `OsrProcessingReleaseTargetRegistry` using
those exact adapters. Do not reuse `OperationalRouteTargetDefinition.waitingCapacity`: that value
belongs to the compatibility per-target queue model and cannot describe one shared launch queue.

Tests must prove several target IDs share one queue, global FIFO across targets, exact destination
retention, duplicate target validation, shared-capacity deferral, and no tote/render allocation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistryTest
```

Expected result: green.

## Step 4: Generalize Immutable Target Admission Lookup

Create:

```java
public record OperationalRouteTargetAdmissionSnapshot(
        StationType stationType,
        String targetId,
        int capacity,
        int occupancy) {}

public interface OperationalRouteTargetAdmissionCatalog {
    List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions();
    OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry();
}
```

The snapshot exposes derived remaining capacity and `canAccept()` and validates normalized identity
and occupancy invariants. `snapshotAdmissions()` returns one immutable, ordered list and must reject
duplicate target IDs.

Make both registries implement the catalog:

- existing `OperationalRouteTargetRegistry` maps each target to its existing per-target queue
  snapshot for compatibility;
- new `OsrOutboundRouteLaunchTargetRegistry` captures the shared queue snapshot exactly once per
  `snapshotAdmissions()` call, then maps every destination target to that same captured
  capacity/occupancy.

Update `OperationalCandidateRouteAdmissionFactory` to depend on the catalog rather than the concrete
legacy registry. At the start of `create(...)`, call `snapshotAdmissions()` exactly once and build
one immutable target-ID lookup used for every candidate in that operational snapshot. Preserve
existing unknown-target, wrong-station, station-blocked precedence, and queue-full reason behavior.
Update reason wording to say "route admission target" rather than imply that the queue is physically
located at the station.

Tests must prove compatibility registry behavior, shared capacity for different destinations,
unknown/wrong station handling, one immutable capacity capture per candidate resolution, and no
mutable queue reads by the worker resolver.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionFactoryTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistryTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistryTest
```

Expected result: green.

## Step 5: Switch Production Operational Runtime To Launch Targets

Update `DspOperationalReleaseRuntimeFactory` to accept
`OperationalRouteTargetAdmissionCatalog` for admission snapshotting and exact command-target
registry wiring.

Preserve the completed factory overload taking `OperationalRouteTargetRegistry`; it delegates to a
new overload taking `OperationalRouteTargetAdmissionCatalog`. Add the production launch-registry
path without copying queues, targets, inventory, lifecycle, or logical state.

Update `DspOperationalReleaseRuntime` to store the admission catalog and expose
`routeTargetAdmissionSnapshots()`. Preserve `routeTargetRegistry()` and
`routeEntryQueueSnapshots()` for the legacy factory overload only; on a launch-registry runtime they
must fail immediately with an `IllegalStateException` explaining that per-target compatibility
queues are unavailable. Add `outboundRouteLaunchQueueSnapshot()` for the launch-registry runtime;
on a legacy runtime it fails with the inverse message. Do not return empty collections for the wrong
runtime mode because that would conceal a wiring error.

Tests must prove:

- exact shared queue and target registry wiring;
- candidate target ID is preserved in the queued launch request;
- Third Party, Adapting, and P2P destinations enter one global FIFO;
- fresh snapshots observe shared queue mutation;
- full launch queue blocks/defer-revalidates without OSR/lifecycle mutation;
- accepted launch request commits OSR departure and lifecycle activation at the same time;
- synchronous/threaded source behavior and idempotent runtime close remain unchanged.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalRouteTargetIntegrationScenarioTest
```

Expected result: green.

## Step 6: Define Generic Hydrated Routed-Tote And Transport Queue

Create `RoutedPhysicalTote` retaining:

- exact `OsrOutboundRouteLaunchRequest`;
- exact existing `ToteLoadPlan`;
- hydrated `Tote` and its renderable.

Validation must require exact physical ID across launch request, plan, tote, renderable ID, and route
follower ID. The destination is obtained from the launch request and must not be copied into another
mutable route model.

Create `OsrOutboundTransportQueue` and immutable snapshot as a bounded FIFO of
`RoutedPhysicalTote`. It represents hydrated work waiting for the physical conveyor-routing layer.

Rules:

- preserve exact routed-tote object identity and global FIFO;
- reject duplicates before capacity;
- expose destination alongside physical IDs in immutable snapshots;
- do not add totes to `SimulationWorld` or renderable lists;
- do not call station-local queues or machine controllers.

Tests must cover identity validation, mixed destinations, full/zero capacity, FIFO, duplicate
rejection, exact payload preservation, and immutability.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalToteTest --tests online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueTest --tests online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshotTest
```

Expected result: green.

## Step 7: Implement Detached OSR Outbound Hydration

Create:

```java
@FunctionalInterface
public interface OsrOutboundToteHydrator {
    RoutedPhysicalTote hydrate(OsrOutboundRouteLaunchRequest request);
}

@FunctionalInterface
public interface DetachedOutboundToteFactory {
    RoutedPhysicalTote create(
            OsrOutboundRouteLaunchRequest request,
            ToteLoadPlan loadPlan);
}
```

Create `OsrOutboundToteHydrationException` for expected missing-plan or identity failures, with a
physical-ID-specific message and optional cause.

Create `LoadPlanOsrOutboundToteHydrator` with explicit dependencies on:

- `ToteLoadPlanProvider`;
- `DetachedOutboundToteFactory`.

The detached factory is the composition boundary for the future OSR outbound route-entry geometry.
This branch must not provide a production geometry implementation because the full warehouse route
does not yet exist. Tests must provide a fixture implementation using a real minimal
`RouteSegment`, `RouteFollower`, tote geometry, and renderable. Do not use P2P
`TipperTrackSection` or `TipperTotePayload`.

Hydration behavior:

1. Resolve the exact load plan by physical ID.
2. Treat a null provider result as an expected missing-plan hydration failure.
3. Call the detached factory exactly once with the exact request and plan.
4. Reject null or identity/destination-mismatched factory results as expected hydration failures.
5. Require the returned tote's lids to be closed.
6. Return the validated `RoutedPhysicalTote`.

Rules:

- no speculative hydration while transport capacity is closed;
- no scene publication, station registration, or pack-renderable creation;
- call provider and builder once per invocation;
- missing plan is blocked hydration, never an empty synthetic plan;
- lids start closed;
- do not mutate the manifest catalog, lifecycle, or logical state.

Tests must cover exact plan/request identity, P2P destination without P2P dependencies, Third Party
and Adapting destinations, missing plan, builder mismatch/null, closed lids, detached publication,
and invocation counts.

Ask the user to run the actual focused hydrator/builder test class names created in this step.

Expected result: green.

## Step 8: Consume Launch Requests Into Outbound Transport

Create `OsrOutboundRouteLaunchControllerSnapshot` containing:

- launch and transport occupancy/capacity;
- optional head physical ID and destination;
- optional last hydrated physical ID and destination;
- optional blocked physical ID;
- blocked reason;
- successful hydration count.

Create `OsrOutboundRouteLaunchController` implementing `SimulationController` with dependencies on
the shared launch queue, outbound transport queue, and hydrator.

On each update:

1. Validate context and finite nonnegative delta.
2. Return idle if launch queue is empty.
3. If transport queue is full, record backpressure and return without hydration.
4. Peek and hydrate the exact launch head once.
5. Validate hydrated identity/destination against the still-current head.
6. Enqueue the routed tote into outbound transport.
7. Dequeue and require the same source launch request.
8. Record one successful hydration.

Failure rules:

- expected `OsrOutboundToteHydrationException`, null result, identity mismatch, or destination
  mismatch leaves both queues unchanged and records an inspectable block;
- unrelated runtime failures propagate;
- process at most one item per update;
- no direct station queue, tipper, machine, route-transfer, or render publication calls.

Tests must cover mixed-destination FIFO, full transport without hydration, missing-plan recovery,
identity/destination mismatch, duplicate destination queue identity, one-per-update behavior, source
removal only after destination acceptance, immutable snapshots, and controller-order assumptions.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchControllerTest
```

Expected result: green.

## Step 9: Prove End-To-End Release Without Station Teleportation

Add `DspOsrOutboundRouteLaunchScenarioTest` using real:

- manifest catalog, physical lifecycle, and OSR inventory;
- operational clock and logical runtime snapshot;
- shared launch queue and launch target registry;
- completed operational scheduler/runtime/controller;
- existing P2P, Adapting, and Third Party candidate destination selection;
- prepared load-plan registry;
- detached test hydrator with a minimal OSR outbound route segment;
- bounded generic outbound transport queue and launch controller.

Required scenarios:

1. P2P-first FULL_PACK work leaves OSR and enters the shared launch queue with P2P destination
   metadata; no P2P/tipper object is touched.
2. Third Party-first and Adapting-first work enter the same global FIFO with exact distinct targets.
3. Launch consumption hydrates each tote at the OSR outbound boundary and preserves global FIFO.
4. Full shared launch capacity blocks/defer-revalidates without OSR/lifecycle mutation.
5. Full outbound transport leaves the launch request queued and avoids hydration allocation.
6. Restored transport capacity hydrates the same request once.
7. Missing load plan leaves the request queued; adding the plan permits deterministic retry.
8. Unknown/wrong-station destinations remain observable blocks and emit no command.
9. Repeated physical manifests for one logical sheet remain sequenced and distinct.
10. Release time, manifest identity, lifecycle activation time, destination, and load plan remain
    correlated for the same physical tote.
11. Logical status remains unchanged and no legacy `markReleased(...)` path is invoked.

Use synchronous bounded updates only. Do not use sleeps or timing windows.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.DspOsrOutboundRouteLaunchScenarioTest
```

Expected result: green.

## Step 10: Regression, Architecture Check, And Branch Closure

Ask the user to run focused coverage:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.osr.*
```

Then ask the user to run:

```powershell
.\gradlew test
```

Architecture verification:

- production OSR release targets feed one shared outbound launch queue;
- P2P destination metadata does not cause `TipperInputQueue` or P2P controller calls;
- hydration starts from an OSR outbound builder abstraction, not P2P geometry;
- target-specific compatibility queues remain isolated to explicit legacy tests;
- scheduler worker inputs remain immutable;
- existing P2P, Adapting, Third Party visual rigs and `ALT+R` remain unchanged.

Before branch closure:

- [ ] mark this plan implementation complete and verified;
- [ ] record final destination, shared-launch, hydration, and transport-boundary contracts;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] confirm focused/full tests and legacy visual/reset smoke checks are green;
- [ ] create a decision-complete plan for physical warehouse transport routing and station-arrival
  boundaries before any P2P queue-consumer or sticky-lease work.

## Preserved Contracts For Physical Transport Routing

- One globally ordered launch stream carries exact physical release identity plus current route
  destination.
- OSR departure and lifecycle activation occur only after shared launch acceptance.
- Hydration occurs at the OSR outbound boundary and never at a destination station.
- Hydrated outbound work is detached and unpublished until the physical transport layer accepts it.
- Station-local queues are populated only by physical transport arrival, never by OSR command
  application or launch consumption.
- Destination target IDs are opaque routing endpoints; transport layout maps them to route paths and
  arrival boundaries.
- Future station completion may assign the next destination, but this branch selects/carries only
  the current first processing destination.
- Sticky P2P leases remain destination-selection policy and cannot bypass transport routing.

## Follow-On Branch

After this branch is green and merged, create a plan for:

```text
feature/dsp-warehouse-transport-routing
```

That branch must define route-network destination mapping, transfer decisions, station arrival
queues, and post-station continuation. P2P queue consumption and sticky leases follow those physical
arrival boundaries.
