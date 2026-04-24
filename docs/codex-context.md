# Codex Context

## System Overview

- Plain Java simulation and software-rendered 3D engine built as a Gradle project.
- The current default runnable example is the tote-to-bag debug harness.
- Scene selection is now explicit via a `--scene=...` command-line switch parsed by `DebugSceneOptions`; the default remains the tote-to-bag harness.
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
- Scene-selection support: `app/src/main/java/online/davisfamily/warehouse/testing/DebugSceneOptions.java`
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
  - Now also owns zero or more mounted transfer mechanisms (`TransferZoneMechanism`).
- `TransferZoneMachine`
  - Warehouse controller state machine for reserving and triggering transfers.
- `TransferZoneController`
  - Reacts to sensor events and tells a tote when to begin transfer.
  - Now also commands and updates mounted transfer mechanisms and gates branch transfer startup on mechanism readiness.
- `MembershipSensor`
  - Detects tracked objects entering/present/exiting a configured route-distance window.
- `WindowSensor`
  - Detects tracked objects inside the transfer window itself.
- `RouteTrackFactory` / warehouse track rendering classes
  - Build conveyor/track render meshes from route definitions plus warehouse `TrackSpec`.
- `StraightConveyorFactory`
  - Builds a fixed straight conveyor assembly in local space with top run, bottom return, end wraps, end rollers, looping belt markers, and roller end-cap markers.
  - Conveyor visual speed is now explicit via `ConveyorVisualSpeed` rather than always being a single raw speed value.
- `StraightConveyorMarkerBehaviour`
  - Drives belt markers around the fixed straight conveyor assembly in local space rather than deriving motion from route geometry.
- `SteeringConveyorMechanism`
  - First concrete transfer-zone mechanism implementation; owns one governing state and can drive one-or-more renderable parts together.
  - Can now be built with an explicit initial outcome/pose so always-branch/fixed steering cases do not need a separate mechanism type.
- `TipperTrackSection`
  - Externally owned mounted route section used by the installed tipper.
- `TipperSectionInstaller` / `TipperInstallation`
  - Production-facing installed tipper surface.
  - Own mounted tipper runtime/render wiring but not route creation or tote load-plan ownership.
- `SortingSectionInstaller` / `SortingInstallation`
  - Production-facing installed sorter surface.
- `TipperToSorterSection`
  - Explicit composition helper for the paired `tipper -> sorter` path.
- `ToteLoadPlanProvider`
  - External provider keyed by tote id for active tipper load plans.
- `TipperDownstreamFlow`
  - Small downstream-capacity boundary used by the tipper controller to decide discharge acceptance and tote-release readiness.
- `IntegratedToteToBagDebugInstaller` / `IntegratedToteToBagDebugInstallation`
  - Harness-level integrated composition surface for the tote-to-bag debug path.
  - Own construction/wiring of the tote-to-bag core, upstream mounted tipper/sorter path, and tote-to-bag flow controller for the debug harness.
- `SorterOutfeedToPdcReceiveTarget`
  - Named handoff target that maps sorter outfeed directly onto the tote-to-bag PDC entry position.
- `BaggingModule` / `BaggingSectionInstaller` / `BaggingInstallation`
  - Installed bagging-machine surface.
  - Owns bagger render assembly, machine runtime installation, and the installed downstream bag receiver.
- `PackGroupReceiver`
  - Generic PCR/downstream pack-group receiver seam used by `ToteToBagFlowController`.
  - Keeps PRL/PCR release logic decoupled from concrete `BaggingMachine`.
- `BagReceiver` / `StoredBagReceiver`
  - Generic completed-bag receiver seam.
  - `StoredBagReceiver` stores received runtime `Bag` objects and can apply finite capacity.
- `BagDischarge`
  - Active bagger-outfeed lifecycle object used by `BaggingMachine`.
  - Separates bag creation, chute discharge movement, and receiver completion.
- `BagMeshFactory`
  - First-pass paper-bag mesh factory for completed bag visuals.

## Core Execution Flow

1. `SoftwareRenderer.main()` parses `DebugSceneOptions`, creates a `TestScene`, and starts a thread that computes `dt` and calls `scene.renderFrame(dt)`.
2. `BaseScene.renderFrame()` updates camera/input state, clears buffers, and delegates scene-specific rendering.
3. `TestScene` installs one explicit scene/harness based on `DebugSceneKind`; `executeChildRenderOperations()` syncs that runtime and then calls `drawObject(objects, dtSeconds, lightDirection)`.
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
7. In the tipper path, the shared tipper controller can:
   - capture the tote on the mounted tipper section
   - resolve the tote load plan through `ToteLoadPlanProvider`
   - run the local tip / discharge sequence
   - delegate downstream acceptance / occupancy rules through `TipperDownstreamFlow`
8. In the tote-to-bag bagger path:
   - `ToteToBagFlowController` reserves a downstream `PackGroupReceiver` before PRL release
   - PCR delivers the released group to that receiver without knowing whether it is a bagger
   - `BaggingMachine` receives the group, creates a runtime `Bag`, runs `BagDischarge`, and completes the `BagReceiver` only after discharge completes
   - downstream bag receiver capacity can make the bagger unavailable, indirectly preventing PRL/PCR release through the `PackGroupReceiver` seam
9. After simulation completes for the frame, rendering uses the latest transforms.

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
  - the controller commands any transfer-zone-mounted mechanisms toward the decided outcome
  - if branching, the machine reserves the tote
  - when the tote enters the window sensor region, branch transfer begins only after the required mechanisms report ready
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
- Transfer-zone-mounted mechanisms currently follow the same general update order as other warehouse systems:
  - the controller updates attached mechanisms every frame
  - branch startup is delayed until the reserved tote is in the window and mechanisms are ready
  - continue decisions currently command the mechanism state but do not reserve the tote or change route-follow motion

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
- transfer-zone-mounted mechanisms
- mounted tipper / sorter machine installations
- tote-load-plan providers
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
  - the old immediate `TRANSFERRING` to `ACTIVE` path has been removed, but the controller is still becoming the owner of more responsibilities (decision handling, mechanism commands, readiness gating)
  - if mechanism types diversify further, it may be worth separating mechanism orchestration from tote-transfer startup
- The first path-derived conveyor visual approach hit believable rendering limits:
  - route-driven conveyor markers around end rollers flashed and visually overshot the rollers
  - this is the main reason the work pivoted toward a fixed straight conveyor assembly rather than continuing to derive conveyor visuals from route spans
- The current straight conveyor proof is intentionally visual-first:
  - it is now much closer to the intended conveyor look than the procedural route-derived attempt
  - there is still a very minor marker disappearance right at the belt/roller tangent in some frames, likely a tiny depth/coplanar edge case
  - that issue is considered minor and intentionally deferred for now
- A renderer bug affecting clipped conveyor faces was diagnosed and fixed:
  - the main issue was not mesh winding in the straight conveyor geometry
  - the real fault was near-plane triangle clipping rebuilding clipped triangles without preserving vertex order
  - that could flip winding on clipped output triangles and trigger incorrect backface culling, especially on the straight conveyor proof
  - `Vertex.clipTriangleNear(...)` now reconstructs clipped triangles by walking original triangle edges in order
  - backface culling is also now performed in view space rather than from projected integer screen-space winding
  - projected screen coordinates are now carried as floats deeper into rasterisation, which also improved stability slightly
- The current document reflects observed code structure and runtime behaviour.
  - It is not a design proposal and does not describe unimplemented intended architecture.

## Latest Session Update

- Bagging-machine runtime/control work progressed materially:
  - `BagDischarge` is now wired into `BaggingMachine`
  - bag creation, chute discharge, and receiver completion are separate lifecycle steps
  - `BaggingMachine` now has `WAITING_FOR_RECEIVER` when the downstream bag receiver cannot reserve
  - `ToteToBagFlowController` now depends on `PackGroupReceiver` rather than concrete `BaggingMachine`
  - `PackGroupReceiver` now includes receive-state and transfer-completion callbacks needed by PCR handoff
  - `StoredBagReceiver` now stores runtime `Bag` objects and supports finite capacity
  - the debug receiver is capacity-limited and auto-empties after being full for a short timer via `DebugBagReceiverAutoEmptyController`
  - active bag discharge is rendered down the chute and received bags are rendered in a simple tote-style debug receiver
  - `BagMeshFactory` now provides a first-pass tall/narrow paper-bag mesh; it is better than the former box but still not final visual fidelity
- Current bagger architecture guidance:
  - PRL/PCR should remain coupled only to `PackGroupReceiver`
  - receiver fullness should remain outside PRL/PCR and outside the bagger's ownership policy
  - the bagger may become unavailable because its downstream receiver is full, but PRL/PCR should observe only the generic receiver seam
  - the current debug receiver auto-empty is not production tote move-on behavior

- Active scene selection has been cleaned up:
  - `SoftwareRenderer` now accepts `--scene=...`
  - `DebugSceneKind`, `DebugSceneOptions`, and `DebugSceneRuntime` now define the explicit scene-selection path
  - `TestScene` no longer depends on comment/uncomment switching between harnesses
- The tote-to-bag integrated debug path now has an explicit harness-level composition surface:
  - `IntegratedToteToBagDebugInstaller` now owns the integrated tote-to-bag debug composition wiring that previously lived inline in `ToteToBagDebugRig`
  - `IntegratedToteToBagDebugInstallation` now exposes the installed integrated debug package
  - `ToteToBagDebugRig` is now thinner and primarily retains debug-facing visual sync and inspection responsibilities
- The sorter-outfeed to PDC live boundary is now represented by a named handoff target:
  - `SorterOutfeedToPdcReceiveTarget` now implements `PackReceiveTarget`
  - the integrated tote-to-bag path no longer uses an anonymous inline receive-target class for sorter-outfeed to PDC wiring

- The tipper / sorter separation work has now reached the intended architectural baseline:
  - `TipperEntryModule` and `TipperEntryModuleBuilder` have been removed
  - `TipperTrackSection` / `TipperTrackSectionInstaller` now own the externally-created mounted route section
  - `TipperSectionInstaller` / `TipperInstallation` now define the production-facing installed tipper surface
  - `SortingSectionInstaller` / `SortingInstallation` now define the production-facing installed sorter surface
  - `TipperToSorterSection` is now the explicit paired composition helper
- Tote load-plan ownership has been moved behind `ToteLoadPlanProvider`:
  - demo tote/load fixtures still exist only under `warehouse.testing`
  - the installed tipper path no longer treats demo tote content as intrinsic machine ownership
- Local tipper sequencing and downstream occupancy concerns are now separated:
  - `ToteTrackTipperFlowController` now owns the shared tipper-side capture / tip / discharge sequence
  - downstream-specific acceptance / release state is now represented through `TipperDownstreamFlow`
  - `SorterTipperDownstreamFlow` implements the sorter-backed path
  - a debug-only immediate receive implementation under `warehouse.testing` proves the alternate downstream path
- The branch now proves both:
  - `tipper -> sorter`
  - `tipper -> non-sorter debug-only receive target`
- The current code should therefore be treated as a repeatable mounted-machine composition pattern for future machines rather than as a still-unfinished tipper / sorter untangling exercise.

- The tote-to-bag / tipper-entry integration boundary has been cleaned up materially:
  - handoff boundary types now exist under `warehouse.sim.totebag.handoff`
  - `PackHandoffPoint`, `MachineHandoffPointId`, `PackHandoffPointProvider`, `PackReceiveTarget`, and `PackReleaseSource` now define the intended machine handoff vocabulary
- `TipperEntryModule` now exposes named handoff points for:
  - tipper pack discharge
  - sorter pack intake
  - sorter pack outfeed
- Placeholder sorter-underflow conveyor ownership has been moved out of the reusable entry assembly:
  - the reusable `TipperEntryModule` no longer owns the placeholder sorter outfeed conveyor/renderable
  - `ToteTrackTipperDebugRig` now explicitly composes that placeholder outfeed conveyor as rig-owned proving equipment
- The integrated tote-to-bag harness no longer routes through that placeholder conveyor:
  - `ToteToBagDebugRig` now feeds sorter output directly onto the real PDC
  - the integrated PDC has been extended upstream so it physically occupies the sorter-underflow span in the integrated harness
- Tote-to-bag core x/z layout has been decoupled and cleaned up further:
  - PCR x placement is no longer implicitly tied to `pdcCenterX`
  - `ToteToBagCoreLayoutSpec` now carries an independent `pcrCenterX`
  - PRL-to-PDC and PRL-to-PCR z spacing is now derived from a single `prlGap` layout value rather than from ad hoc fixed absolute z positions
  - PRL render placement now follows the same derived layout rather than using a hard-coded visual z
- The sorter-outfeed live boundary now uses the newer handoff abstraction directly:
  - `PackSink` has been removed from the active code path and deleted
  - `ToteTrackTipperFlowController`, `TipperEntryModule`, and the rigs now use `PackReceiveTarget` for sorter outfeed handoff
  - `SorterOutfeedDebugConveyor` now implements `PackReceiveTarget` directly
- A final structural cleanup has started to make the proven architecture more explicit in code:
  - `TipperModule` and `SortingModule` now exist under `warehouse.sim.totebag.assembly`
  - `TipperEntryModule` now composes and exposes those module objects via getters rather than being only one monolithic reusable type
- Current practical state after this session:
  - isolated tipper rig still proves sorter-outfeed plugability using an explicit placeholder conveyor
  - integrated tote-to-bag rig now shows the intended physical ownership more accurately, with the real PDC running under the sorter
  - the remaining follow-up is more about further decomposition / API cleanup than about missing the intended thin-slice behaviour

- The previously isolated tote-track tipper rig has now been extracted into a reusable mounted entry module:
  - `TipperEntryModule` now lives under `warehouse.sim.totebag.assembly`
  - `TipperEntryModuleBuilder` provides the assembly/install entry point
  - `TipperEntryLayoutSpec` now captures the mounted entry module root pose
- The old `ToteTrackTipperDebugRig` is now only a thin wrapper around that reusable entry module rather than owning all construction itself.
- `ToteToBagDebugRig` now mounts the reusable tipper-entry module against the tote-to-bag core through an explicit upstream module attachment point.
- `ToteToBagAttachmentPoint` now includes `UPSTREAM_MODULE_ROOT`.
- `ToteToBagFlowController` can now be used in a mode where upstream tipper/sorter ownership is external and packs simply arrive onto the PDC.
- `ToteTrackTipperFlowController` now supports an optional downstream `PackSink` so sorter-outfeed packs can be handed into the tote-to-bag PDC without collapsing controller ownership.
- The active `TestScene` is now back on the tote-to-bag debug harness after the integration and alignment fixes.
- Two route-track rendering bugs were fixed while integrating the mounted tipper module:
  - `RenderableTrackFactory.createConveyorEndRoller(...)` now includes the sampled route point `y` when placing route-backed conveyor end rollers
  - `TrackBuilder` now includes sampled route point `y` when building route-backed deck meshes, guide meshes, and roller transforms
- Those two fixes were validated against both the oval and parallel route scenes before leaving them in place.
- The integrated tipper-entry module discharge path now correctly converts local tipper anchors into world space before constructing the discharge polyline, fixing the earlier “flying packs to a stray point” behaviour seen after initial integration.
- The integrated tote-to-bag seam is now visually aligned closely enough for the current thin slice:
  - sorter-outfeed to PDC x/z alignment is attachment-driven
  - y alignment now depends on the mounted tipper-side world height being respected consistently by both route-backed tracks and straight conveyor assemblies
- Remaining cleanup items identified from this session:
  - the upstream module mount in `ToteToBagDebugRig` still uses tuned numeric offsets and should eventually become named layout values
  - `TipperEntryModule` is still large and still mixes assembly, visual sync, and layout/math helpers

- Tote-to-bag isolated tipper proving work has advanced further on `feature/tote-track-tipper-rig`.
- `TestScene` is currently switched back to the isolated tipper rig rather than the parallel-track scene.
- The isolated tipper rig now includes a reusable contained-pack tote layout helper:
  - `ContainedPackLayout` lives under `warehouse.sim.totebag.layout`
  - tote-contained packs now use deterministic tote-local layered placement rather than hash-scattered overlap
- The isolated sorter/outfeed visual proof has been materially refined:
  - the sorter now reads as a bridge-like machine over a straight conveyor rather than as a single block
  - the hopper is hollow/open rather than solid
  - the conveyor is now anchored under the sorter from a named sorter-local anchor point rather than from hand-tuned rotated offsets
  - sorter queue visuals now read as a vertical drop path above the conveyor entry
  - packs are now inserted onto the sorter outfeed conveyor near the under-hopper point rather than at the conveyor start
  - sorter outfeed conveyor speed and visual speed are now intentionally kept aligned in the rig
- Several small commits now capture the current isolated tipper progression:
  - `e53bf5e` Add reusable tote pack layout helper
  - `944e94f` Refine tipper rig sorter visuals
  - `ead4a2c` Refine sorter outfeed bridge and handoff
  - `22cff62` Refine sorter through-flow visuals
- An architectural direction was clarified for later integration:
  - the tipper should follow the same general integration conventions as mounted transfer machines
  - however, it should not be forced into transfer-zone-specific semantics when its responsibilities differ
  - the preferred direction is a broader route-mounted-machine convention, with current transfer machines as one family and the tipper as another
  - the tote/route side should be treated as the primary integration boundary to stabilise first, because the tipper is conceptually a specialised track section that captures/holds/releases a tote before handing packs into conveyor-local transport
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
- Conveyor work was explored on the `conveyer` branch rather than on `master`.
- A first transfer-zone mechanism layer has been added:
  - `TransferOutcome` was introduced as a warehouse-level continue/branch outcome enum
  - `TransferZoneMechanism` and `MechanismMotionState` were added under `warehouse.sim.transfer.mechanism`
  - `SteeringConveyorMechanism` was added as the first concrete mechanism implementation
  - `TransferZone` can now own zero or more mounted mechanisms
  - `TransferZoneController` now commands and updates mechanisms and gates branch startup on mechanism readiness
  - `TransferZoneMachine` now tracks which reserved tote is currently in the transfer window
- The earlier idea of modelling conveyors mainly as path-derived route/track spans was reconsidered:
  - tracks still need procedural generation
  - conveyors do not appear to need general procedural generation for the intended use cases
  - curved conveyors are not currently a target requirement; real curved conveyors would be treated as different equipment rather than as bent straight belts
- A reusable fixed straight conveyor proof was added:
  - `StraightConveyorFactory` now builds a local-space straight conveyor assembly with a top belt, bottom return, end wraps, end rollers, and looping markers
  - `StraightConveyorMarkerBehaviour` drives those markers in local space around the straight conveyor assembly
  - this proof scene is now the active `TestScene` wiring via `WarehouseTrackFactory.setupStraightConveyorTest(...)`
  - the proof visually validates the preferred direction: fixed conveyor assemblies rather than route-derived conveyor geometry
- Straight conveyor reuse and detail were pushed further:
  - roller end-cap marker strips were added so roller rotation is readable as the conveyor runs
  - the straight conveyor assembly was tested at much smaller scales and still reads acceptably for compact transfer-machine use
  - `StraightConveyorFactory` is now the practical reusable assembly API for both standalone tests and transfer-zone-mounted machines
- The steering transfer mechanism now uses the reusable conveyor assembly rather than ad hoc local meshes:
  - `WarehouseTrackFactory` mounts two compact straight conveyor child assemblies side by side under the steering mechanism root
  - those child conveyors sit on a thin shared base that rotates with the mechanism
  - spacing, height, and footprint were tuned against the current transfer-zone visuals in the parallel track scene
- The oval bottom conveyor has been switched away from the old route-derived conveyor visual path:
  - the bottom run now renders plain support/guides plus a mounted `StraightConveyorFactory` assembly aligned to the segment
  - this keeps the preferred fixed-conveyor direction in the main oval route scene rather than only in the standalone proof scene
- Conveyor visual speed is now explicitly modelled separately from transport speed:
  - `StraightConveyorFactory` now takes a `ConveyorVisualSpeed` configuration rather than a single raw speed
  - the oval bottom conveyor is currently configured to match tote/transport speed
  - the compact steering conveyors keep an independent fixed visual speed because matching tote speed looked visually wrong at that scale
- Steering mechanism initial pose can now be specified:
  - `SteeringConveyorMechanism` accepts an explicit initial outcome/pose
  - always-transfer/fixed-steering cases can now reuse the same mechanism type rather than introducing a specialised duplicate
  - the current parallel-track setup uses this by starting always-transfer steering mechanisms already in branch pose
- A wireframe-debug rendering issue was diagnosed and fixed:
  - filled triangles were largely correct, but wireframe mode showed long stray edge spans on clipped geometry
  - the wireframe path was drawing triangle edges after only near-plane clipping, without full frustum clipping in clip space
  - `TriangleRenderer` now clips wireframe edges against the full homogeneous view frustum before projecting to screen space
  - this made the wireframe debug view materially more trustworthy for long conveyor spans and other clipped geometry
- The current straight conveyor proof has these known characteristics:
  - the belt is thinner and tangentially aligned to the roller/wrap geometry
  - two markers are phase-shifted so one should normally be visible on the top run and one on the bottom run
  - the marker spans across the width of the belt by design
- A clipping/culling investigation was completed:
  - disappearing belt faces in the straight conveyor proof were initially suspected to be a winding problem
  - runtime testing and screenshots showed the more important symptom was clipped triangles being rebuilt with incorrect winding
  - `Vertex.clipTriangleNear(...)` was fixed to preserve edge order when reconstructing clipped triangles
  - `TriangleRenderer` now culls in view space instead of using projected integer screen-space winding
  - the straight conveyor proof, oval scene, and parallel scene all appeared materially more stable after the fix

## Next Session Guidance

- Session focus:
  - read `docs/tote-to-bag-requirements.txt` before starting tote-to-bag work so the current agreed direction, completed phases, and next slice are understood from the document rather than inferred from stale context
  - do not make code or document changes unless the user explicitly directs that work in the current session
- For tote-to-bag cleanup/integration follow-up:
  - keep the current tipper-entry module mounted into the tote-to-bag harness; do not regress back to placeholder upstream machine boxes
  - treat the PDC/PRL/PCR transport cell as the stable core and the mounted tipper entry as the current canonical upstream module shape
  - keep the integrated harness on the real extended PDC under the sorter; do not reintroduce the placeholder sorter-underflow conveyor into the integrated path
  - the next cleanup slices should likely be:
    - replace the remaining mount magic numbers in `ToteToBagDebugRig` with named layout values/spec entries
    - continue splitting `TipperEntryModule` further now that `TipperModule` and `SortingModule` exist, so assembly, visual sync, and helper geometry/path math are not all in one class
    - decide whether the explicit scene-selection path should eventually grow beyond command-line / IDE launch-profile control into a richer launcher if that becomes worthwhile
    - consider whether any remaining tipper-to-sorter path should also move from composition/controller wiring toward a more explicit transfer/handoff object where that improves reuse
- Keep using the `conveyer` branch for ongoing conveyor work; `master` should remain unchanged.
- Treat the straight conveyor assembly as the canonical direction for conveyor visuals.
  - Reuse the single straight conveyor assembly model for transfer-focused machines and fixed conveyor-backed runs.
  - Do not continue investing in route-derived/procedural conveyor-span rendering for transfer devices.
- The intended modelling direction now appears to be:
  - tracks remain procedural warehouse infrastructure
  - conveyors are reusable straight assemblies
  - transfer-zone mechanisms arrange and animate one or more child conveyor assemblies under a parent mechanism/envelope transform
- The next practical step should be to continue propagating the reusable conveyor assembly into the remaining conveyor-backed scenes and machine types:
  - keep replacing any remaining route-derived or ad hoc conveyor visuals with mounted `StraightConveyorFactory` assemblies where appropriate
  - continue treating popup/steering devices as procedural placement of standard conveyor modules across a parent envelope, not procedural generation of each conveyor's belt geometry
- Suggested sequence for the next session:
  - keep the standalone straight conveyor test available as a sizing/debug reference, but continue validating real usage in the oval and parallel route scenes
  - review whether any remaining conveyor-backed track visuals should now become mounted fixed assemblies rather than route-derived spans
  - decide whether additional transfer-machine variants can be expressed as `SteeringConveyorMechanism` plus initial-outcome/strategy configuration rather than new mechanism classes
  - perform hot path analysis on the render loop and identify opportunities to reduce immutable-object creation / transient allocation inside per-frame rendering code
- The current default runnable scene is the tote-to-bag integration harness:
  - `TestScene` now selects scenes explicitly via `DebugSceneKind`
  - `TOTE_TO_BAG` remains the default when no `--scene=...` switch is supplied
  - `setupOvalTrack(...)` and `setupParallelTracks(...)` remain the best focused scenes for validating shared route-track rendering changes outside tote-to-bag
