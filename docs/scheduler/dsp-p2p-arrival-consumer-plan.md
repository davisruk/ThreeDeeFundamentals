# DSP P2P Arrival Consumer Plan

Branch: `feature/dsp-p2p-arrival-consumer`

Status: implementation complete and verified; pending merge to `master`.

## Purpose

Consume exact physical totes only after warehouse transport has delivered them to a bounded P2P
station-arrival queue, then hand accepted totes to the existing P2P tipper input queue without
teleportation, payload replacement, or scheduler-thread mutation.

The required sequence is:

```text
OSR release and hydration
  -> common warehouse transport
  -> P2P terminal sensor
  -> StationRoutedToteArrivalQueue
  -> P2P-local admission callback
  -> TipperInputQueue
  -> TipperInputQueueController
  -> ToteTrackTipperFlowController
  -> existing P2P processing
```

This branch must:

- consume only the FIFO head of one exact P2P station-arrival queue;
- preserve the exact `RoutedPhysicalTote`, `PhysicalToteId`, `Tote`, renderable, and `ToteLoadPlan`;
- expose an immutable P2P-local admission request that a later sticky-line lease policy can use;
- defer without source mutation when admission is closed or tipper waiting capacity is full;
- adapt accepted routed totes into `TipperTotePayload` without fabricating business data;
- make the accepted load plan available to the existing `ToteLoadPlanProvider` boundary;
- preserve normal route-following from the warehouse terminal into the tipper approach;
- process at most one source head per controller update;
- expose immutable consumer/target snapshots for tests and debug inspection;
- compose multiple independent P2P target bindings without introducing line-selection policy.

This branch does not consume directly from OSR, choose a P2P line, assign or release sticky
service-centre leases, add pharmacy affinity, process Adapting or Third Party arrivals, implement
post-P2P outbound transport, change bag allocation, create a second route engine, calibrate conveyor
timings, or run a full production day.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-warehouse-transport-routing-plan.md`
3. `docs/scheduler/machine-wait-queues-plan.md`
4. `docs/scheduler/dsp-scheduler-implementation-plan.md`
5. `docs/tipper-route-mounted-machine-architecture.md`

Inspect these classes before each affected step:

- `RoutedPhysicalTote`
- `StationRoutedToteArrivalQueue`
- `StationRoutedToteArrivalQueueSnapshot`
- `StationRoutedToteArrivalRegistry`
- `OperationalRouteDestination`
- `InboundToteManifest`
- `TipperInputQueue`
- `TipperTotePayload`
- `TipperInputQueueController`
- `ToteTrackTipperFlowController`
- `ToteLoadPlan`
- `ToteLoadPlanProvider`
- `ContainedPackLayout`
- `TipperToSorterSection`
- `RouteSegment`
- `RouteConnection`
- `RouteFollower`
- `SimulationWorld`
- `SimulationController`

## Fixed Decisions

Do not revisit these decisions during implementation:

- `StationRoutedToteArrivalQueue` remains the upstream ownership boundary. The consumer never reads
  `OsrPhysicalInventory`, a route-launch queue, or an outbound transport queue.
- One consumer binding represents one exact `OperationalRouteDestination` whose station type is
  `P2P`. The source queue destination, consumer destination, and target destination must be equal.
- The consumer does not select among the five production P2P lines. Upstream routing has already
  selected the target ID. Later sticky-lease work may influence selection and admission but cannot
  bypass this boundary.
- Admission is revalidated locally after physical arrival. The callback receives immutable values
  only; it must not receive `Tote`, `RenderableObject`, `RouteSegment`, a mutable queue, or a machine.
- The admission request contains physical tote ID, exact P2P destination, service-centre ID,
  `OrderSheetKey`, `OrderType`, and ordered distinct pharmacy IDs from the inbound manifest.
- Use `P2pArrivalAdmissionDecision.permit()` and `defer(reason)`. Do not name a static factory the
  same as a record component; this avoids Java record accessor collisions.
- This branch supplies an allow-all policy for composition and deterministic test policies for
  deferral. It contains no lease map, line owner, quiescence rule, or service-centre mutation.
- Process only the source FIFO head and at most one tote per update. A deferred head blocks later
  totes in that same line queue; do not skip or reorder it.
- A full `TipperInputQueue` is normal backpressure. Leave the exact routed tote in the station queue
  and retry on a later update.
- Payload adaptation is explicit through `P2pTipperPayloadFactory`. It must reuse
  `routedTote.tote()` and `routedTote.renderable()` by identity and derive contained-pack layout from
  the exact `ToteLoadPlan` plus supplied tote geometry/layout configuration.
- The core consumer must not instantiate `ToteGeometry`, guess interior dimensions, or hard-code
  debug layout values. Production/debug composition supplies the payload factory; focused tests may
  use a deterministic factory.
- The P2P target owns the accepted load-plan lookup used by `ToteTrackTipperFlowController` and
  implements `ToteLoadPlanProvider`. Registering the same physical ID and exact plan is idempotent;
  a different plan for an existing ID is an invariant failure.
- `TipperInputQueue` must reject duplicate tote IDs before changing tote motion state. Add a
  read-only `contains(String toteId)` query and preserve its existing FIFO/snapshot contract.
- The downstream target validates all identities and capacity before mutation, then registers the
  exact load plan and enqueues the exact payload in one simulation-thread call. No controller can
  interleave inside that call.
- After downstream acceptance, dequeue the source and require the dequeued object to be the same
  `RoutedPhysicalTote` instance that was peeked. A mismatch is an invariant failure, not a retry.
- A post-accept visual-source listener may register pack visuals. It must be idempotent for the same
  tote ID and must not decide admission. Listener failure is an invariant/configuration error; do
  not add rollback machinery in this slice.
- Do not call `ToteTrackTipperFlowController.acceptNextTote(...)` from the arrival consumer. The
  existing `TipperInputQueueController` remains the only queue-to-tipper processing boundary.
- Do not change a tote's current route segment, distance, direction, speed, or renderable pose during
  arrival consumption. Queueing keeps the tote `HELD`; the existing queue controller changes it to
  `MOVING` only when the tipper can accept it.
- Each binding includes a route-continuity definition from the warehouse terminal segment to the
  configured tipper approach/tipper segment. Validate a deterministic connected route by object
  identity at composition time. Reject missing paths, cycles before the target, or ambiguous
  multiple next connections. Do not infer connectivity from coordinates.
- Existing controller order is preserved. The arrival consumer is registered after transport and
  existing P2P controllers; one-update latency before a newly queued tote can drain is acceptable
  and deterministic.
- Runtime snapshots contain immutable IDs, destinations, occupancies, counts, and blocked reasons
  only. No mutable simulation object crosses to a scheduler worker.
- Reset remains full scene reconstruction. Do not add mutable reset methods.
- The user runs Gradle. After every coding step, ask the user to run the focused command below,
  propose the listed commit message if green, and wait for feedback.

## Package And Vocabulary

Create P2P arrival-consumer types under:

```text
online.davisfamily.warehouse.sim.dsp.p2p.arrival
```

Use these names unless an existing source type requires a minor adjustment:

- `P2pArrivalAdmissionRequest`
- `P2pArrivalAdmissionDecision`
- `P2pArrivalAdmissionPolicy`
- `AllowAllP2pArrivalAdmissionPolicy`
- `P2pTipperPayloadFactory`
- `P2pTipperArrivalAcceptedListener`
- `P2pTipperArrivalTarget`
- `P2pTipperArrivalTargetSnapshot`
- `P2pArrivalRouteBinding`
- `P2pArrivalConsumerController`
- `P2pArrivalConsumerControllerSnapshot`
- `P2pArrivalConsumerBinding`
- `DspP2pArrivalConsumerRuntime`
- `DspP2pArrivalConsumerRuntimeFactory`

## Step 1: Harden The Existing Tipper Input Queue

Scope:

- Add `TipperInputQueue.contains(String toteId)` with null/blank validation.
- Reject duplicate IDs before calling `setInteractionMode(HELD)` or mutating the underlying queue.
- Validate capacity before changing tote state.
- Preserve FIFO identity and existing `MachineWaitQueueSnapshot` behavior.
- Add focused tests proving duplicate and full enqueue failures leave the candidate tote's prior
  motion state unchanged and leave the existing payload intact.

Do not add DSP types to the generic tote-to-bag package.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueTest --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueControllerTest
```

Proposed commit message: `Harden P2P tipper input queue admission`

## Step 2: Define Immutable P2P Arrival Admission

Scope:

- Add the admission request, decision, and policy types.
- Normalize/validate nonblank service-centre ID and reason values at construction.
- Preserve pharmacy IDs in first-manifest-occurrence order with duplicates removed.
- Add `AllowAllP2pArrivalAdmissionPolicy` returning `permit()` without mutable state.
- Test request immutability, invalid values, allow-all behavior, and deterministic deferred reasons.

Do not refer to route segments, queue objects, renderables, or machines from these types.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionTest
```

Proposed commit message: `Define P2P arrival admission contract`

## Step 3: Adapt Exact Routed Totes At A P2P Target

Scope:

- Add `P2pTipperPayloadFactory` as a pure routed-tote-to-payload boundary.
- Add `P2pTipperArrivalTarget` around one exact P2P destination and one `TipperInputQueue`.
- Make the target implement `ToteLoadPlanProvider` using its accepted exact load plans.
- Validate destination, physical ID, tote identity, renderable identity, payload identity, duplicate
  state, and capacity before mutation.
- Register the plan, enqueue the payload, and notify the optional accepted listener exactly once.
- Expose an immutable target snapshot with destination, queue capacity/occupancy, queued physical
  IDs, and accepted count.
- Add tests for exact identity, plan lookup, duplicate/conflicting-plan rejection, full target
  behavior, listener notification, and no partial mutation on validation failure.

Use a no-op listener when pack visuals are not installed. Do not create pack renderables here.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTargetTest
```

Proposed commit message: `Add exact P2P tipper arrival target`

## Step 4: Validate Route Continuity Into The Tipper

Scope:

- Add `P2pArrivalRouteBinding` with exact terminal and tipper-entry segments.
- Validate at construction that following `getNextConnections()` reaches the configured tipper
  entry through one deterministic chain.
- Accept terminal-equals-entry as a valid zero-hop binding.
- Reject null segments, no path, a cycle before entry, and more than one forward connection before
  entry.
- Keep route objects inside the simulation-side binding; expose only segment labels in snapshots.
- Test all accepted and rejected topology cases.

Do not add coordinate comparisons or mutate `RouteFollower`.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBindingTest
```

Proposed commit message: `Validate P2P arrival route continuity`

## Step 5: Consume One P2P Arrival Head Safely

Scope:

- Add `P2pArrivalConsumerController` for one source queue, admission policy, route binding, payload
  factory, and downstream target.
- Build the admission request from the exact head manifest.
- On each valid update: return if empty; defer if policy blocks; defer if target is full; otherwise
  create the payload, accept it downstream, then dequeue and verify exact source object identity.
- Validate finite, nonnegative `dtSeconds` and nonnull `SimulationContext` consistently with current
  controllers.
- Expose a snapshot containing source/target destination, occupancies/capacities, head physical ID,
  last accepted physical ID, last blocked physical ID/reason, and cumulative accepted count.
- Use stable blocked-reason constants for `ADMISSION_DEFERRED` and `TIPPER_INPUT_FULL`, retaining the
  policy reason separately.
- Test empty updates, one-per-update behavior, FIFO order, admission deferral, target backpressure,
  retry, exact identity, malformed/non-P2P binding rejection, and snapshot immutability.

Do not dequeue first and do not skip a blocked head.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalConsumerControllerTest
```

Proposed commit message: `Consume routed P2P arrivals safely`

## Step 6: Compose Independent P2P Arrival Consumers

Scope:

- Add immutable `P2pArrivalConsumerBinding` composition input.
- Add `DspP2pArrivalConsumerRuntimeFactory` and runtime.
- Create/register one controller per binding in supplied order.
- Reject duplicate target IDs, duplicate source queue instances, duplicate target queue instances,
  mismatched destinations, and non-P2P destinations before registering any controller.
- Expose ordered immutable controller and target snapshots through the runtime.
- Keep `close()` idempotent; the runtime owns no worker and does not close transport or P2P machine
  runtimes supplied by the caller.
- Test zero/one/five bindings, validation before registration, deterministic controller order,
  snapshot ordering, and idempotent close.

Do not modify `DspWarehouseTransportRuntimeFactory` to construct P2P internals. The caller retains
the station queue and composes the P2P consumer separately.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeFactoryTest
```

Proposed commit message: `Compose P2P arrival consumer runtime`

## Step 7: Prove Arrival-To-Tipper Route And Plan Continuity

Scope:

- Add a focused scenario using a real `StationRoutedToteArrivalQueue`, connected terminal/approach/
  tipper segments, `P2pArrivalConsumerController`, `TipperInputQueueController`, and
  `ToteTrackTipperFlowController`.
- Prove the same tote/renderable reaches the tipper, its follower advances through normal route
  connections, and the exact accepted load plan is resolved when tipping starts.
- Prove a closed admission callback and a full tipper queue retain the source tote held without
  changing route segment, distance, direction, or source ownership.
- Prove retry succeeds after admission/capacity opens and no physical ID appears in both queues.
- Add a two-line case proving one line's blocked head does not block an independent P2P target.

Use deterministic update counts/state transitions, not wall-clock sleeps.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerScenarioTest
```

Proposed commit message: `Prove P2P arrival to tipper continuity`

## Step 8: Integrate Pack Layout And Visual Source Registration

Scope:

- Add the production payload-factory implementation using supplied tote interior dimensions,
  interior floor Y, and `ContainedPackLayout` gaps.
- Derive pack positions from the exact routed load plan and preserve its pack IDs/dimensions.
- Wire the accepted listener to `TipperToSorterSection.registerToteSource(...)` in a focused P2P
  composition path.
- Prove empty and populated plans, oversized-pack validation, exact payload identities, and
  idempotent visual-source registration.

Do not allocate pack renderables in a simulation controller update. Existing visual synchronization
remains responsible for lazy pack-renderable creation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.ContainedPackP2pTipperPayloadFactoryTest --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterPackVisualsTest
```

Proposed commit message: `Integrate P2P arrival pack layout`

## Step 9: Update The Warehouse Transport Debug Scene

Scope:

- Replace the transport scene's direct `P2pCapacityReturnController` removal of station arrivals
  with the real P2P arrival consumer and a visible/inspectable `TipperInputQueue` boundary.
- Keep the scene focused: a rig-only delayed drain may represent downstream tipper capacity return,
  but it must drain `TipperInputQueue`, never the station arrival queue.
- Show station-arrival and tipper-input occupancies separately in `warehouse_transport_state` and
  the P2P marker.
- Preserve distinct positions for terminal-pending, station-queued, and tipper-input-owned totes.
- Preserve the existing four routed totes, mixed destinations, backpressure observation, and
  deterministic `ALT+R` reconstruction.
- Update `DspWarehouseTransportDebugRigTest` to assert the real boundary transition and absence of
  direct station-queue draining.

Do not add a full tote-to-bag assembly to this transport-focused scene.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.*
```

Proposed commit message: `Expose P2P arrival consumption in transport scene`

## Step 10: Regression, Visual Check, And Branch Closure

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.totebag.assembly.* --tests online.davisfamily.warehouse.sim.totebag.control.* --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
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

- P2P arrivals visibly move from station-arrival ownership to tipper-input ownership;
- a full tipper input retains the next tote at the station boundary;
- capacity return allows FIFO progress without teleportation or overlap;
- Third Party and Adapting routes remain unchanged;
- the tote-to-bag scene still processes queued totes and pack visuals correctly;
- `ALT+R` reconstructs both scenes deterministically;
- inspection reports separate station and tipper queue states.

Architecture verification:

- no OSR or route-launch queue is consumed by P2P code;
- terminal arrival remains the only path into station-arrival queues;
- P2P arrival consumption does not choose a line or mutate sticky ownership;
- the consumer never calls `ToteTrackTipperFlowController.acceptNextTote(...)`;
- route follower state is not rewritten during queue handoff;
- exact tote/renderable/load-plan identity is preserved;
- scheduler workers receive immutable values only;
- no new scheduler thread, path engine, render thread, or mutable reset API exists.

Before branch closure:

- [x] mark this plan implementation complete and verified;
- [x] record final admission, payload, route-continuity, ownership, and snapshot contracts;
- [x] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [x] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [x] confirm focused/full tests and visual/reset checks are green;
- [ ] create the decision-complete plan for `feature/dsp-p2p-sticky-line-leases` only after this
  branch is merged; intentionally deferred until that prerequisite is satisfied.

Proposed commit message: `Complete P2P arrival consumer feature`

## Final Implemented Contract

- One consumer owns one exact P2P destination and processes no more than its station-arrival FIFO
  head per simulation update.
- Immutable local admission is evaluated before target capacity. Deferred admission and full tipper
  input leave source ownership and route-follower state unchanged for deterministic retry.
- Acceptance preserves the exact `RoutedPhysicalTote`, `PhysicalToteId`, `Tote`, renderable, and
  `ToteLoadPlan`; successful downstream acceptance precedes verified source dequeue.
- `TipperInputQueueController` remains the sole boundary that releases accepted totes into
  `ToteTrackTipperFlowController`; the arrival consumer never calls the tipper flow directly.
- `P2pArrivalRouteBinding` validates deterministic object-identity connectivity without coordinate
  inference or route mutation.
- `ContainedPackP2pTipperPayloadFactory` derives contained-pack positions from supplied tote
  interior geometry and the exact load plan. Pack renderables remain lazily created by existing
  visual synchronization.
- `TipperToSorterP2pArrivalAcceptedListener` provides the explicit visual-source registration
  adapter for full P2P composition without placing rendering behavior in the core consumer.
- Ordered immutable consumer and target snapshots expose destination, queue occupancy/capacity,
  accepted IDs/counts, and stable blocked reasons without leaking mutable simulation objects.
- Independent P2P bindings cannot block one another. Line selection and sticky service-centre
  ownership remain deliberately outside this feature.
- The `dsp-warehouse-transport` scene now drains only the bounded tipper-input placeholder, never
  the station-arrival queue, and displays both ownership layers independently.
- Focused tests, the complete Gradle suite, `dsp-warehouse-transport`, `tote-to-bag`, and `ALT+R`
  reconstruction checks were green on 2026-08-24.

## Expected Final Contract

- Warehouse transport owns a P2P tote until terminal arrival enqueues the exact routed payload.
- The station-arrival queue owns it until local admission and tipper waiting capacity both accept.
- The tipper input target then owns the same tote, renderable, and load plan.
- `TipperInputQueueController` alone releases it into existing tipper processing.
- Local admission can later be replaced by sticky line-lease policy without changing transport,
  queue ownership, payload adaptation, or tipper processing.
