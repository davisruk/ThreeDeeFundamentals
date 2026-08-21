# DSP Warehouse Transport Routing Plan

Branch: `feature/dsp-warehouse-transport-routing`

Status: decision-complete plan ready. Start from updated `master` after
`feature/dsp-osr-outbound-route-launch` is verified and merged.

## Purpose

Move hydrated physical totes from the common OSR outbound transport boundary through real route
segments and transfer machines to bounded station-local arrival queues. Destination metadata must
drive physical routing; it must never teleport a tote from OSR release or hydration into P2P,
Adapting, or Third Party processing.

The required sequence is:

```text
OSR operational release
  -> shared launch request queue
  -> detached hydration
  -> shared outbound transport queue
  -> warehouse transport ingress/publication
  -> route segments and transfer machines
  -> terminal arrival sensor
  -> station-local routed-tote arrival queue
  -> later station-specific consumer
```

This branch must:

- bind each exact `OperationalRouteDestination` to simulation-owned route topology;
- provide the production detached-tote factory omitted intentionally from the route-launch branch;
- publish accepted routed totes into the simulation and render collection exactly once;
- preserve one authoritative in-flight record per physical tote;
- make transfer decisions from immutable destination intent, not order type or product data;
- stop a tote at its terminal arrival boundary and retry if its station-local arrival queue is full;
- transfer ownership to the exact destination queue only after that queue accepts the routed tote;
- expose immutable transport, in-flight, route, and arrival snapshots for inspection and tests;
- prove Third Party, Adapting, and P2P destinations share the physical outbound route before
  diverging;
- keep scheduler workers isolated from route followers, transfer machines, sensors, queues, and
  renderables.

This branch does not consume P2P arrivals into `TipperInputQueue`, assign sticky P2P line leases,
choose among five P2P lines, process Adapting or Third Party visits, implement post-station
continuation, model tote collision/spacing, construct the full production warehouse, implement
Exception routing, calibrate conveyor timing, or process a full day's dataset.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-osr-outbound-route-launch-plan.md`
3. `docs/scheduler/dsp-scheduler-implementation-plan.md`
4. `docs/scheduler/machine-wait-queues-plan.md`
5. `docs/machines/transfer-machine-requirements.md`
6. `docs/machines/transfer-machine-standalone-plan.md`
7. `docs/tipper-route-mounted-machine-architecture.md`

Inspect these classes before each affected step:

- `OperationalRouteDestination`
- `RoutedPhysicalTote`
- `OsrOutboundTransportQueue`
- `LoadPlanOsrOutboundToteHydrator`
- `DetachedOutboundToteFactory`
- `RouteSegment`
- `RouteFollower`
- `RouteConnection`
- `Tote`
- `TransferTarget`
- `TransferRoutingDecision`
- `TransferTargetDecisionStrategy`
- `TransferZoneMachine`
- `TransferZoneController`
- `MachineWaitQueue`
- `SimulationWorld`
- `SimulationContext`
- `DetectionEvent`
- `RenderableToteFactory`

## Fixed Decisions

Do not revisit these decisions during implementation:

- OSR is not physically adjacent to any destination station. Every hydrated tote enters one common
  outbound route before destination-specific divergence.
- `OperationalRouteDestination(stationType, targetId)` remains the routing key. Do not infer a
  route from `OrderType`, `RouteRequirements`, pharmacy, product, or load-plan contents.
- The route catalog is simulation-owned mutable topology. Scheduler snapshots contain only
  immutable target IDs, labels, capacities, and physical identities; no `RouteSegment`, `Tote`,
  sensor, machine, or renderable crosses onto a scheduler worker.
- Use existing `RouteSegment`, `RouteFollower`, standalone `TransferZoneMachine`,
  `TransferTarget`, and `TransferTargetDecisionStrategy` APIs. Do not create a second path engine.
- Do not change `RouteFollower` to understand DSP destinations. Destination-aware divergence is a
  warehouse transfer-decision concern.
- Hydration creates a detached tote already bound to the exact common route-entry segment, entry
  distance, and travel direction. Transport ingress validates that binding; it does not replace or
  mutate the follower silently.
- One publication abstraction owns adding a routed tote to `SimulationWorld` and the renderable
  collection. Tests use a deterministic fake. Publication occurs only on the simulation thread.
- A tote becomes in flight only when transport ingress accepts it for publication. The source
  `OsrOutboundTransportQueue` is dequeued only after publication and in-flight registration succeed.
- Process at most one outbound transport head per ingress-controller update. Do not burst-drain.
- Active in-flight state retains the exact `RoutedPhysicalTote`, destination, route definition, and
  publication state. Physical ID is globally unique.
- Transfer routing uses `(transferMachineId, destination targetId)` as its configured lookup key.
  Every reachable destination must have an explicit branch/continue decision at each ambiguous
  transfer machine. Missing or conflicting configuration fails before the rig runs.
- Terminal arrival is sensor-driven. On terminal entry, hold the tote before the route end and
  retain it in flight until the exact destination queue accepts it.
- A full arrival queue leaves the tote held and in flight and retries deterministically. It does not
  send the tote elsewhere, discard it, dequeue another arrival ahead of it, or mutate scheduler
  state.
- Station-local arrival queues contain exact `RoutedPhysicalTote` values and are bounded FIFO.
  They are not the legacy `OperationalRouteEntryQueue`, which contains release requests and remains
  compatibility-only.
- Successful arrival transfers ownership from in-flight transport to exactly one station-local
  arrival queue. The tote remains published and held until a later station consumer accepts it.
- P2P arrival does not call `TipperInputQueue`, `ToteTrackTipperFlowController`, or
  `ToteToBagFlowController` in this branch. A later consumer adapts the P2P arrival payload after
  sticky lease/admission policy is defined.
- Adapting and Third Party arrival also stop at their queue boundaries; existing station processing
  APIs are not invoked here.
- Reset remains reconstruction. Do not add mutable reset methods.
- The user runs Gradle. Ask for the focused command and propose a commit message after every coding
  step, then wait for feedback.

## Package And Vocabulary

Create generic transport-routing types under:

```text
online.davisfamily.warehouse.sim.dsp.transport.routing
```

Use these names unless an existing source type makes a minor naming adjustment necessary:

- `WarehouseRouteDefinition`
- `WarehouseRouteCatalog`
- `WarehouseRouteCatalogSnapshot`
- `StationRoutedToteArrivalQueue`
- `StationRoutedToteArrivalQueueSnapshot`
- `StationRoutedToteArrivalRegistry`
- `WarehouseTransportInFlightRegistry`
- `WarehouseTransportInFlightSnapshot`
- `WarehouseTransportPublisher`
- `RouteBoundDetachedOutboundToteFactory`
- `WarehouseTransportIngressController`
- `WarehouseTransportIngressControllerSnapshot`
- `WarehouseTransferRoutingTable`
- `DestinationAwareTransferTargetDecisionStrategy`
- `WarehouseTransportArrivalController`
- `WarehouseTransportArrivalControllerSnapshot`
- `DspWarehouseTransportRuntime`
- `DspWarehouseTransportRuntimeFactory`

Do not create another scheduler, lifecycle ledger, OSR inventory, launch queue, transport queue,
event bus, route follower, transfer machine, station processor, or render thread.

## Step 1: Add Bounded Station Arrival Queues

Create `StationRoutedToteArrivalQueue` around `MachineWaitQueue`, retaining exact
`RoutedPhysicalTote` values by physical ID.

Required API:

```java
public OperationalRouteDestination destination()
public boolean canAccept()
public boolean contains(PhysicalToteId physicalToteId)
public void enqueue(RoutedPhysicalTote routedTote)
public Optional<RoutedPhysicalTote> peek()
public Optional<RoutedPhysicalTote> dequeue()
public StationRoutedToteArrivalQueueSnapshot snapshot()
```

Rules:

- queue identity is the exact destination target ID;
- reject a routed tote whose destination differs from the queue destination;
- reject duplicate physical IDs before capacity checks;
- capacity zero is valid;
- preserve exact payload identity and FIFO;
- snapshot entries contain physical ID plus destination only and are immutable;
- queue methods do not publish, move, hide, or process a tote.

Create `StationRoutedToteArrivalRegistry` from an ordered list of exact destination queues. Reject
duplicate target IDs globally, including duplicates with different station types. Expose exact queue
lookup by target ID and one immutable ordered snapshot list.

Tests cover supported destinations, wrong destination, duplicate/full/zero capacity, FIFO, exact
identity, registry validation, fresh snapshots, and immutability.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalRegistryTest
```

Proposed commit message: `Add routed tote station arrival queues`

## Step 2: Define Destination Route Topology

Create `WarehouseRouteDefinition` containing:

- exact `OperationalRouteDestination`;
- exact common route-entry `RouteSegment`;
- validated entry distance;
- entry `TravelDirection`;
- terminal arrival sensor ID;
- terminal route-segment identity used for arrival validation.

The common entry segment object must be the same for every definition in this branch. Destination
routes diverge later through transfer machines. A terminal sensor ID and target ID are distinct and
must not be conflated.

Create `WarehouseRouteCatalog` from an ordered list of definitions. It must:

- reject nulls, duplicate target IDs, duplicate terminal sensor IDs, unsupported destination
  stations, inconsistent common entry segment/distance/direction, and null/blank terminal topology;
- resolve by exact destination, target ID, or terminal sensor ID;
- reject a target-ID lookup when the supplied station type differs;
- expose `WarehouseRouteCatalogSnapshot` using route labels and IDs only, never mutable topology.

Do not claim complete reachability from `RouteSegment` connections in this step. A lateral
`TransferTarget` is an explicit topology edge and is not necessarily represented by
`RouteSegment.connectTo(...)`. Step 6 validates each complete destination path using both normal
route connections and configured transfer-target edges. Never infer reachability from geometry
coordinates or labels.

Tests use a small shared-entry topology with Third Party, Adapting, and P2P terminals and cover all
validation, lookup, reachability, ordering, and snapshot immutability.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinitionTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalogTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalogSnapshotTest
```

Proposed commit message: `Define warehouse destination route topology`

## Step 3: Track Exact In-Flight Routed Totes

Create `WarehouseTransportInFlightRegistry` with explicit configurable capacity and exact
`RoutedPhysicalTote` ownership.

Required behavior:

- `canAccept()`, `contains(...)`, `register(...)`, `find(...)`, `markArrivalPending(...)`, and
  `completeArrival(...)`;
- reject duplicates before capacity;
- `completeArrival(...)` requires the same exact payload instance and returns it;
- preserve registration order for deterministic snapshots;
- expose destination, current route label, tote motion state, and whether terminal arrival is
  pending in `WarehouseTransportInFlightSnapshot`;
- build snapshots only on the simulation thread from current tote follower state;
- snapshots contain no mutable tote, route, renderable, or queue references.

Do not move or publish totes in this registry. Tests cover capacity, exact identity, completion,
fresh route/motion observations, duplicate/unknown completion, and immutable old snapshots.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightRegistryTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshotTest
```

Proposed commit message: `Track routed totes in warehouse transport`

## Step 4: Build Detached Totes On The Common Route Entry

Create a small rendering boundary:

```java
@FunctionalInterface
public interface DetachedToteRenderableFactory {
    RenderableObject create(
            OsrOutboundRouteLaunchRequest request,
            ToteLoadPlan loadPlan);
}
```

Create `RouteBoundDetachedOutboundToteFactory implements DetachedOutboundToteFactory` with explicit
dependencies on `WarehouseRouteCatalog`, `DetachedToteRenderableFactory`, configured route speed,
and tote offsets/yaw policy.

Behavior:

1. Resolve the exact route definition from the request destination.
2. Create one renderable through the supplied factory.
3. Require renderable ID to equal the physical tote ID.
4. Create one `RouteFollower` with the physical ID, exact common entry segment, entry distance,
   configured direction, and finite positive speed.
5. Create a closed-lid `Tote` and return one validated `RoutedPhysicalTote` retaining the exact
   request and load plan.

Do not add the tote/renderable to a world/list, create pack renderables, or invoke a destination
station. Tests cover all destinations, exact entry binding, direction/speed, identity failures,
null renderables, closed lids, no publication, and dependency validation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.RouteBoundDetachedOutboundToteFactoryTest
```

Proposed commit message: `Build outbound totes on warehouse route entry`

## Step 5: Publish Transport Heads Through One Ingress Boundary

Create:

```java
public interface WarehouseTransportPublisher {
    boolean contains(PhysicalToteId physicalToteId);
    void publish(RoutedPhysicalTote routedTote);
}
```

The production composition supplies one publisher that adds the tote to `SimulationWorld` and its
renderable to the scene collection exactly once. It owns an exact published-ID index, validates the
world/render-list inputs before either collection is mutated, and rejects duplicate IDs before
publication. Keep this composition adapter outside generic engine packages. Its two validated append
operations are one acceptance boundary for the controller; expected validation failures must occur
before either append.

Create `WarehouseTransportIngressController implements SimulationController` with dependencies on
`OsrOutboundTransportQueue`, `WarehouseRouteCatalog`, `WarehouseTransportInFlightRegistry`, and the
publisher.

On each update:

1. Validate context and finite nonnegative delta.
2. Return idle when the transport queue is empty.
3. Inspect only the exact FIFO head.
4. Resolve its exact destination route; an unknown route records a block without mutation.
5. Validate the tote follower is bound to the definition's exact entry segment, distance, and
   direction and require `publisher.contains(...)` to be false.
6. If in-flight capacity is full or the physical ID is already active, record a block and return.
7. Publish the exact payload.
8. Register that exact payload in flight.
9. Dequeue and require the same source payload.
10. Record one successful ingress.

Expected route/configuration failures remain queued and inspectable. Unrelated runtime publisher
failures propagate and leave the source queued; the publisher contract must be atomic from the
controller's perspective. Process at most one per update.

Expose capacity/occupancy, source head, last ingress, blocked ID/reason, and successful count in an
immutable controller snapshot. Tests cover one-per-update FIFO, publication before source removal,
unknown route, mismatched follower binding, capacity, duplicate identity, publisher failure,
snapshot history, and no destination-queue mutation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshotTest
```

Proposed commit message: `Publish outbound totes into warehouse transport`

## Step 6: Route Transfer Machines By Destination

Create `WarehouseTransferRoutingTable` configured from explicit entries keyed by:

```text
transfer machine ID + destination target ID
```

Each entry retains one existing `TransferRoutingDecision`, including branch/continue outcome,
`TransferTarget`, travel direction, and orientation policy. The table must validate:

- nonblank unique machine/target keys;
- every route-catalog destination has one explicit decision at every ambiguous transfer machine it
  can reach;
- configured target segments are reachable from that machine's transfer topology;
- each complete destination path is reachable from the common entry to its terminal by traversing
  normal `RouteConnection` edges plus the configured `TransferTarget` edges by object identity;
- no decision changes destination metadata or tote identity.

Construct the table with the route catalog plus the ordered transfer-machine topology it validates;
do not maintain a second disconnected list of machine IDs and target segments.

Create `DestinationAwareTransferTargetDecisionStrategy implements TransferTargetDecisionStrategy`
with dependencies on one machine ID, the routing table, and the in-flight registry. For a detected
tote, resolve its exact active routed payload and destination, then return the configured decision.
Unknown/non-active totes or missing decisions return empty and record an inspectable strategy block;
they do not default to the first branch.

Do not modify `RouteFollower`, `TransferZoneController`, or transfer animation mechanics unless a
focused regression proves an existing bug independent of DSP routing.

Tests cover all three destinations, branch and continue decisions, exact `TransferTarget` identity,
reverse travel, orientation policy preservation, unknown tote/machine/target, duplicate table
configuration, and no scheduler/load-plan reads.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransferRoutingTableTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DestinationAwareTransferTargetDecisionStrategyTest --tests online.davisfamily.warehouse.sim.transfer.*
```

Proposed commit message: `Route warehouse transfers by tote destination`

## Step 7: Hand Off Terminal Arrivals Without Teleportation

Create `WarehouseTransportArrivalController` with:

- `WarehouseRouteCatalog`;
- `WarehouseTransportInFlightRegistry`;
- `StationRoutedToteArrivalRegistry`;
- a `DetectionEvent` listener for configured terminal sensors.

Terminal `ENTER` behavior:

1. Ignore unrelated sensors and non-entry events.
2. Resolve the exact in-flight payload by event object/physical ID.
3. Require the sensor's route definition destination to equal the payload destination.
4. Set the tote to `HELD` and retain one pending arrival keyed by physical ID.
5. Do not remove it from in-flight state yet.

Controller update behavior:

- retry pending arrivals in deterministic first-detected order;
- process at most one pending arrival per update;
- when the exact destination queue is full, retain the tote held, pending, and in flight;
- when accepted, enqueue the exact payload first, then complete the exact in-flight record and
  remove pending state;
- leave the tote published and held for the later station consumer;
- duplicate terminal events are idempotent for the same payload/sensor;
- wrong sensor/destination, missing active payload, duplicate queue identity, and invariant mismatch
  remain inspectable and do not mutate ownership.

Expose pending order, last arrived ID/destination, blocked ID/reason, and successful arrival count.
Tests use explicit detection events and cover successful handoff, full-queue retry, wrong sensor,
duplicate events, source removal after queue acceptance, one-per-update ordering, and immutable
snapshots.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshotTest
```

Proposed commit message: `Hand off routed totes at station arrivals`

## Step 8: Compose The Warehouse Transport Runtime

Create `DspWarehouseTransportRuntime` exposing:

- ingress and arrival controllers;
- route-catalog snapshot;
- outbound transport snapshot;
- in-flight snapshot;
- ordered station-arrival snapshots;
- idempotent close, without owning external world/render collections.

Create `DspWarehouseTransportRuntimeFactory` that wires exact instances of:

- the shared `OsrOutboundRouteLaunchQueue` already used by the operational launch-target registry;
- prepared load-plan provider;
- route catalog and route-bound detached factory;
- a new `LoadPlanOsrOutboundToteHydrator`, bounded `OsrOutboundTransportQueue`, and
  `OsrOutboundRouteLaunchController` using that exact shared launch queue;
- publisher;
- in-flight registry;
- transfer routing table/strategies supplied to transfer-machine installation;
- station-arrival registry and terminal arrival controller.

The factory must not create an operational scheduler, launch-target registry, OSR inventory,
lifecycle ledger, station processor, tipper queue, or render thread. Composition code creates the
shared launch queue once and passes that exact instance both to
`OsrOutboundRouteLaunchTargetRegistry` and this factory. Never copy or reconstruct queued requests.

Define and document controller order for `SimulationWorld`:

1. route-launch hydration controller;
2. warehouse transport ingress controller;
3. transfer-machine controllers through their existing installation order;
4. terminal arrival controller.

Because `SimulationWorld` updates tracked totes and sensor events before controllers, a newly
published tote cannot move until the next simulation update. Tests must assert that ordering rather
than rely on arbitrary timing.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntimeTest
```

Proposed commit message: `Compose DSP warehouse transport runtime`

## Step 9: Prove End-To-End Physical Destination Routing

Add `DspWarehouseTransportRoutingScenarioTest` using real launch hydration, transport ingress,
`RouteSegment` topology, standalone transfer machines/strategies, terminal detection, and arrival
queues. Use synchronous bounded updates and explicit events where geometry timing is not the subject
of the assertion. Do not use sleeps.

Required scenarios:

1. Third Party, Adapting, and P2P requests leave one global outbound transport FIFO and are
   published through the same common route entry.
2. Exact destination metadata selects the configured transfer outcomes and terminal sensor.
3. Each tote reaches only its exact station target queue; P2P is not inserted into
   `TipperInputQueue`.
4. Two or more in-flight totes retain distinct physical identity, destination, load plan, and
   renderable identity.
5. Full in-flight capacity retains the outbound transport head without duplicate publication.
6. Full station arrival capacity holds the arrived tote and later admits the same exact payload.
7. Unknown route/transfer/sensor configuration blocks observably without teleportation or loss.
8. Global launch order and destination-specific arrival FIFO remain deterministic.
9. Release time, manifest, lifecycle assignment, load plan, route destination, in-flight record,
   and arrived payload remain correlated for the same physical tote.
10. Logical order status remains unchanged and no legacy route-entry queue is populated.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRoutingScenarioTest
```

Proposed commit message: `Prove physical warehouse destination routing`

## Step 10: Add A Focused Visual Transport Rig

Add a new explicit debug scene, for example `DSP_WAREHOUSE_TRANSPORT`, without replacing existing
P2P, Adapting, or Third Party scenes.

The proving layout must contain:

- one visible common OSR outbound approach;
- existing standalone transfer-machine geometry/mechanisms at route divergences;
- three clearly separated terminal tracks labelled/inspectable as Third Party, Adapting, and P2P;
- one bounded station arrival queue per terminal;
- at least three routed totes released in mixed destination order;
- scheduler/transport inspection showing launch, transport, in-flight, pending arrival, and target
  queue state.

Visual acceptance:

- every tote first appears at the same OSR outbound entry;
- no tote appears initially at a destination station;
- transfer mechanisms orient before the reserved tote reaches them;
- totes retain expected yaw according to configured transfer orientation policy;
- each tote stops at the correct terminal arrival boundary;
- a deliberately full terminal queue visibly holds a tote until capacity returns;
- no duplicate tote/renderable appears;
- existing visibility rules still hide contained packs while lids are closed;
- `ALT+R` reconstructs the entire rig and reproduces deterministic order.

Ask the user to run focused rig/scene tests created in this step, then perform the visual check.

Proposed commit message: `Add warehouse transport routing debug scene`

## Step 11: Regression, Architecture Check, And Branch Closure

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.transfer.* --tests online.davisfamily.warehouse.sim.tote.* --tests online.davisfamily.warehouse.sim.machine.queue.*
```

Then ask the user to run:

```powershell
.\gradlew test
```

Architecture verification:

- OSR release and hydration still never populate station-local queues;
- only warehouse transport ingress publishes detached routed totes;
- transfer decisions depend on exact active destination metadata;
- terminal arrival is the only path into station-local routed-tote queues;
- no P2P tipper/controller call occurs;
- scheduler workers read immutable snapshots only;
- no new path engine, scheduler, render thread, or mutable reset API exists;
- legacy target-specific route-entry queues remain compatibility-only;
- existing P2P, Adapting, Third Party, transfer, and `ALT+R` visual behavior remains green.

Before branch closure:

- [ ] mark this plan implementation complete and verified;
- [ ] record final topology, publication, in-flight, transfer-decision, and arrival contracts;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] confirm focused/full tests and visual/reset checks are green;
- [ ] create the next detailed plan only after reassessing whether P2P-local arrival consumption
  and sticky service-centre leases should be one branch or two.

Proposed commit message: `Complete warehouse transport routing feature`

## Preserved Contracts For Later Station Consumers

- Station consumers receive exact `RoutedPhysicalTote` values only after physical terminal arrival.
- A destination queue owns a published, held tote until its station-specific consumer accepts it.
- P2P line selection/lease policy cannot bypass route topology or consume directly from OSR
  transport.
- Sticky service-centre ownership is local to a selected P2P line and must revalidate at P2P arrival.
- Adapting and Third Party consumers may use destination target IDs to select their exact local
  processing boundary without changing transport routing.
- Station completion may assign a later route destination, but post-station continuation requires a
  separate lifecycle/routing plan.

## Follow-On Decision

After this branch is green and merged, reassess and plan either:

```text
feature/dsp-p2p-arrival-consumer
feature/dsp-p2p-sticky-line-leases
```

as separate branches, or one combined branch only if the arrival-consumer API cannot be tested
meaningfully without lease selection. Do not start either before physical arrival boundaries exist.
