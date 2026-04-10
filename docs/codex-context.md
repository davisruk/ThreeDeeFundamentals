# Codex Context

## System Overview

- Plain Java simulation and software-rendered 3D engine built as a Gradle project.
- The current runnable example is a warehouse-style scene with a tote moving around a routed track network.
- The codebase mixes a generic engine layer (`threedee`) with a warehouse-specific example layer (`warehouse`).
- Main concepts visible in code:
  - route-based movement over connected path segments
  - simulation objects updated on a per-frame tick
  - sensors and event dispatch
  - transfer zones that can redirect a moving object from one segment to another
  - renderable objects whose transforms are updated by simulation state

## Entry Points

- Main application entry point: `app/src/main/java/online/davisfamily/threedee/SoftwareRenderer.java`
- Active example scene: `app/src/main/java/online/davisfamily/warehouse/testing/TestScene.java`
- Warehouse layout and simulation setup: `app/src/main/java/online/davisfamily/warehouse/testing/WarehouseTrackFactory.java`
- Per-frame simulation orchestration: `app/src/main/java/online/davisfamily/threedee/scene/BaseScene.java`
- Route-follow movement: `app/src/main/java/online/davisfamily/threedee/behaviour/routing/RouteFollower.java`
- Warehouse tote movement and transfer handling: `app/src/main/java/online/davisfamily/warehouse/sim/tote/Tote.java`

## Key Components

- `SoftwareRenderer`
  - Creates the Swing window/canvas, constructs the scene, and runs the render loop.
- `BaseScene`
  - Owns render buffers, camera/input plumbing, the list of renderable objects, and the `SimulationWorld`.
  - Calls simulation update and then draws all objects each frame.
- `SimulationWorld`
  - Central simulation coordinator.
  - Updates sim objects, updates sensors, dispatches queued events, then updates controllers.
- `SimulationContext`
  - Holds simulation time, tracked objects, and the event queue.
- `RenderableObject`
  - Renderable scene node with mesh, transform, child objects, and optional visual behaviours.
- `RouteSegment`
  - Represents a path segment with geometry and graph connections.
  - Owns only generic routing topology and path geometry.
- `RouteConnection`
  - Connects one segment to another with a target entry distance.
- `RouteFollower`
  - Tracks a moving object’s current segment, distance along segment, travel direction, and speed.
  - Produces `RouteFollowerSnapshot` pose samples from route geometry.
- `Tote`
  - Warehouse-specific moving simulation object.
  - Owns route following, writes directly into the tote render transform, and manages transfer state.
  - Now owns a direct reference to its `RenderableObject` and caches that renderable's `ObjectTransformation`.
- `TransferZone`
  - Defines a source window on a segment and the target segment/entry point for a transfer.
  - Owns transfer-motion tuning via `TransferMotionConfig`.
- `TransferZoneMachine`
  - Warehouse controller state machine for reserving and triggering transfers.
- `TransferZoneController`
  - Reacts to sensor events and tells a tote when to begin transfer.
- `MembershipSensor`
  - Detects tracked objects entering/present/exiting a configured route-distance window.
- `WindowSensor`
  - Detects tracked objects inside the transfer window itself.
- `RouteTrackFactory` / warehouse track rendering classes
  - Build conveyor/track render meshes from route definitions plus warehouse `TrackSpec`.

## Core Execution Flow

1. `SoftwareRenderer.main()` creates a `TestScene` and starts a thread that computes `dt` and calls `scene.renderFrame(dt)`.
2. `BaseScene.renderFrame()` updates camera/input state, clears buffers, and delegates scene-specific rendering.
3. `TestScene.executeChildRenderOperations()` calls `drawObject(objects, dtSeconds, lightDirection)`.
4. `BaseScene.drawObject()` performs:
   - `sim.update(dtSeconds)`
   - `RenderableObject.update(dtSeconds)` for all renderables
   - `RenderableObject.draw(...)` for all renderables
5. `SimulationWorld.update()` performs:
   - update sim objects
   - update sensors
   - dispatch queued events
   - update controllers
6. The warehouse tote advances through its route, updates its render transform, may trigger sensors, and may enter transfer state.
7. After simulation completes for the frame, rendering uses the latest transforms.

## Movement and Routing Model

- Routes are represented as connected `RouteSegment`s backed by `PathSegment3` geometry.
- `RouteFollower` stores:
  - current segment
  - distance along current segment
  - speed
  - travel direction
- On each update:
  - if not blocked, `RouteFollower.advance(dt, blocked)` increments distance by `speed * dt`
  - when the end of a segment is reached, it chooses an adjacent connection
  - for the current code path, connection choice is simple and takes the first available candidate
- `RouteFollower.buildSnapshot()` samples:
  - position from path geometry
  - forward direction from segment orientation
  - up/tangent vector from geometry
- `Tote.update()` uses that snapshot to write translation and yaw into the tote `ObjectTransformation`.
- Segment transitions are graph-based, not physics-based:
  - the follower snaps from one segment to the connected segment’s configured entry distance
  - no interpolation is performed by `RouteFollower` itself between connected segments

## Transfer Behaviour

- Transfer zones are attached to a source `RouteSegment`.
- In the warehouse example, transfer-zone machines are created for the transfer zones on the top segment.
- Each machine creates:
  - an approach membership sensor
  - a transfer-window sensor
  - a controller registered as a listener for detection and transfer-complete events
- Runtime flow:
  - the tote moves normally on its current segment
  - the approach sensor detects entry before the transfer window
  - the transfer decision strategy decides whether to branch or continue
  - if branching, the machine reserves the tote
  - when the tote enters the window sensor region, the controller calls `tote.beginTransfer(...)`
- Transfer in `Tote` currently has two phases:
  - `WAITING_FOR_ALIGNMENT`: keep following the source route until the tote reaches the transfer centre distance
  - `LATERAL_TRANSFER`: move along a fixed straight transfer segment from the aligned source pose to a calculated merge point on the target segment
- Transfer merge-point selection is now geometry-driven:
  - the tote calculates a fixed merge distance on the target segment using current route speed plus the zone's `TransferMotionConfig`
  - the merge search is clamped between per-zone minimum and maximum offsets from the target entry distance
- Transfer motion config is warehouse-owned:
  - `TransferMotionConfig` lives under `warehouse.sim.transfer`
  - `WarehouseRouteBuilder` supplies defaults by transfer type and also exposes overloads so individual transfer sites can provide explicit configs
- On completion:
  - the tote updates its `RouteFollower` to the target segment and the precomputed merge distance
  - a `TransferCompletedEvent` is published
  - the machine clears its active transfer state

## State and Behaviour Management

- `SimulationWorld` is the main owner of simulation state progression.
- `SimulationContext` stores tracked objects so sensors can inspect their latest route snapshots.
- `Tote` has warehouse-specific motion state:
  - `MOVING`
  - `HELD`
  - `BLOCKED`
  - `TRANSFERRING`
- During transfer, `TransferMotionState` tracks:
  - source and target segments
  - source transfer centre
  - target merge distance
  - start/end positions
  - transfer world length and distance travelled
  - transfer phase
- `RenderableObject` behaviours are a separate visual/transform update mechanism.
- In the current warehouse path, tote locomotion is not driven by a `RenderableObject` behaviour.
  - The `Tote` sim object directly mutates the renderable tote’s transform.
  - Child tote lid motion is handled by render behaviours (`PingPongRotationBehaviour`).

## Architectural Boundary

### Intended Generic Engine Concepts

- simulation ticking and event dispatch
- renderable scene graph and transforms
- path geometry
- route graph traversal
- generic sensors over tracked route-following objects

### Warehouse-Specific Concepts

- totes
- conveyor/track layout rules
- transfer zones and transfer machines
- guide openings and clearances
- warehouse track rendering specs and mesh generation

### Current Boundary Tension In Code

- The core generic/warehouse ownership split is substantially cleaner than earlier sessions:
  - generic route topology lives under `threedee.behaviour.routing`
  - warehouse transfer topology/runtime lives under `warehouse.sim.transfer`
  - warehouse track rendering lives under `warehouse.rendering.model.tracks`
- The main remaining top-level coupling is the runnable example entry point:
  - `SoftwareRenderer` in `threedee` directly boots the warehouse `TestScene`
  - this is acceptable for the current single-example application, but it is still application-level coupling rather than a framework/plugin boundary

## Important Constraints and Assumptions

- Current runnable warehouse example is forward-moving only in practice.
- The warehouse example relies on track layout for looping; reversal is not currently part of the warehouse behaviour.
- `SimulationWorld` update order matters:
  - sim objects move first
  - sensors observe after movement
  - event handlers react in the same frame
- Detection/transfer events still use object ids rather than object references:
  - this keeps the current event model simple and low-allocation
  - listeners/controllers that need the concrete object still perform a secondary lookup through `SimulationContext`
- Event identity/thread-boundary policy is still intentionally unresolved:
  - using object references in events would remove current id fragility for in-process listeners
  - keeping compact event payloads may be preferable if simulation and rendering are later split across threads
- Tote position is path-derived, not physics-simulated.
- Tote orientation is currently mostly derived from path direction each update, with temporary override logic during some transfer cases.
- Link segments are marked via `PathSegment3.isLinkSegment()` and are treated specially by tote yaw logic.
- The scene update and rendering run on a custom thread started from the Swing setup.

## Known Complexities or Risks

- Transfer timing has been materially improved:
  - the tote no longer chases a moving target point during lateral transfer
  - current transfer smoothness now depends mainly on per-zone `TransferMotionConfig` tuning rather than on correcting the transfer kinematics themselves
- Transfer tuning is currently static configuration:
  - merge search uses fixed sampled steps and per-zone min/max merge offsets
  - additional runtime testing may still show cases where layout-specific tuning should be exposed more explicitly or derived from more geometry
- Tote yaw/orientation handling has been reworked:
  - tote facing is now modelled separately from route travel direction
  - opposite-running target tracks and link traversal preserve world-facing correctly in the tested scenarios
  - additional runtime testing may still reveal edge cases in more complex transfer layouts
- `RouteFollower` connection choice is simplistic:
  - multiple candidates are currently resolved by taking the first one
- Generic/warehouse separation around routing is materially improved:
  - generic route topology now appears to be largely back behind the intended boundary
  - the main remaining coupling concerns are the application entry point and the id-based event/controller path rather than warehouse logic living inside generic routing classes
- Sim/render linkage has been improved for totes, but id-based coupling still exists in the event path:
  - `Tote` now owns its `RenderableObject` directly, so render transform updates no longer depend on matching ids
  - transfer sensor/controller flow still resolves the tote via `DetectionEvent.objectId`
  - that remaining fragility is documented but intentionally deferred until a broader event/threading decision is made
- Sensors reuse mutable cached event instances before publishing them into the event queue.
  - this is acceptable only under the current single-threaded in-process dispatch assumptions
  - a future threading/performance review should revisit whether published events remain mutable and id-based or become a stronger boundary type
- `TransferZoneController` state transitions are hard to reason about:
  - it transitions to `TRANSFERRING` and then immediately to `ACTIVE` after starting a transfer
- The current document reflects observed code structure and runtime behaviour.
  - It is not a design proposal and does not describe unimplemented intended architecture.

## Latest Session Update

- `RouteSegment` has been cleaned back to generic routing concerns only.
- Warehouse-specific per-segment data now lives in `WarehouseSegmentMetadata`.
- Builder ownership has been split:
  - generic graph construction now lives in `RouteBuilder`
  - warehouse track and transfer construction now lives in `WarehouseRouteBuilder`
- `RouteSceneBuilder` has been removed.
- `TargetGuideOpening` was removed after verification; it was unused in the current codebase.
- `TransferZone` now lives under `warehouse.sim.transfer`; the warehouse transfer domain owns its topology/config object.
- `TransferMotionConfig` now lives under `warehouse.sim.transfer`; transfer-motion tuning is zone-owned rather than hard-coded in `Tote`.
- `RouteTrackFactory` now lives under `warehouse.rendering.model.tracks`; warehouse track rendering owns the renderable track factory and `SpecAndSegment`.
- Tote motion now separates route `TravelDirection` from tote-facing orientation.
- Transfer onto link segments now preserves world yaw through the link and resolves back to route-relative facing on exit.
- Transfer motion now uses a fixed merge-point straight-line model:
  - `WAITING_FOR_ALIGNMENT` still follows the source route until the transfer centre
  - `LATERAL_TRANSFER` now advances along a fixed world-space segment at route speed
  - on completion, the `RouteFollower` is restored at the precomputed merge distance on the target segment
- `WarehouseRouteBuilder` now provides default `TransferMotionConfig` values by transfer type and overloads for per-transfer overrides.
- `WarehouseTrackFactory` includes an explicit per-transfer `TransferMotionConfig` example for one top-to-link transfer, while other transfers still use builder defaults.
- `setupParallelTracks` has been brought up to parity with `setupOvalTrack` for simulation wiring:
  - the tote is registered as a tracked sim object
  - transfer-zone machines are created for both parallel segments
  - direct transfers can be configured to cross straight over rather than merge diagonally
- `Tote` now owns a direct `RenderableObject` reference and caches its `ObjectTransformation`.
- The remaining tote identity fragility has been narrowed to the event/controller path rather than the sim/render linkage itself.
- Remaining generic/warehouse boundary work still identified:
  - no major ownership leaks are currently called out inside the route/transfer/rendering code
  - the current application entry point still directly selects the warehouse example scene
- Recommended next step for a future session:
  - runtime-tune `TransferMotionConfig` values for different transfer layouts and decide whether the merge-search policy should become more geometry-aware
  - perform a broader architecture review before changing the event model, especially if simulation/render threading or allocation reduction becomes a priority
