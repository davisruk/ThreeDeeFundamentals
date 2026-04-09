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
  - Also currently carries transfer-zone and warehouse rendering metadata.
- `RouteConnection`
  - Connects one segment to another with a target entry distance.
- `RouteFollower`
  - Tracks a moving object’s current segment, distance along segment, travel direction, and speed.
  - Produces `RouteFollowerSnapshot` pose samples from route geometry.
- `Tote`
  - Warehouse-specific moving simulation object.
  - Owns route following, writes directly into the tote render transform, and manages transfer state.
- `TransferZone`
  - Defines a source window on a segment and the target segment/entry point for a transfer.
- `TransferZoneMachine`
  - Warehouse controller state machine for reserving and triggering transfers.
- `TransferZoneController`
  - Reacts to sensor events and tells a tote when to begin transfer.
- `MembershipSensor`
  - Detects tracked objects entering/present/exiting a configured route-distance window.
- `WindowSensor`
  - Detects tracked objects inside the transfer window itself.
- `RouteSceneBuilder`
  - Builder for route segments and their warehouse-specific track/transfer metadata.
  - Despite package location, this is not purely generic.
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
  - `LATERAL_TRANSFER`: interpolate from current position to the target segment entry position
- On completion:
  - the tote updates its `RouteFollower` to the target segment and target distance
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
  - target entry distance
  - start/end positions
  - elapsed time and duration
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

- `RouteSegment` currently includes warehouse rendering/guide metadata, not just generic routing concerns.
- `RouteSceneBuilder` lives under `threedee.behaviour.routing` but depends heavily on warehouse track and transfer concepts.
- `TransferZone` is located under `threedee` but depends on warehouse-specific strategy and guide concepts.

## Important Constraints and Assumptions

- Current runnable warehouse example is forward-moving only in practice.
- The warehouse example relies on track layout for looping; reversal is not currently part of the warehouse behaviour.
- `SimulationWorld` update order matters:
  - sim objects move first
  - sensors observe after movement
  - event handlers react in the same frame
- Tote position is path-derived, not physics-simulated.
- Tote orientation is currently mostly derived from path direction each update, with temporary override logic during some transfer cases.
- Link segments are marked via `PathSegment3.isLinkSegment()` and are treated specially by tote yaw logic.
- The scene update and rendering run on a custom thread started from the Swing setup.

## Known Complexities or Risks

- Transfer timing is fragile:
  - lateral transfer consumes time, but downstream route progress is not continuously accumulated during the transfer
  - this likely contributes to the visible hesitation after transfer completion
- Tote yaw/orientation handling is fragile:
  - normal updates recompute yaw from route direction
  - transfer-specific yaw preservation is implemented with ad hoc override logic
  - transitions from link segments back to non-link segments are especially easy to get wrong
- `RouteFollower` connection choice is simplistic:
  - multiple candidates are currently resolved by taking the first one
- Warehouse concerns bleed into generic routing classes, making future engine/framework separation harder.
- Sensors reuse mutable cached event instances before publishing them into the event queue.
  - This is safe only if no later mutation can affect already-queued events; the current approach is risky.
- `TransferZoneController` state transitions are hard to reason about:
  - it transitions to `TRANSFERRING` and then immediately to `ACTIVE` after starting a transfer
- The current document reflects observed code structure and runtime behaviour.
  - It is not a design proposal and does not describe unimplemented intended architecture.
