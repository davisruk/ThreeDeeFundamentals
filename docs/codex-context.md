# Codex Context

## System Overview

- Plain Java simulation and software-rendered 3D engine built as a Gradle project.
- Main application entry point: `app/src/main/java/online/davisfamily/threedee/SoftwareRenderer.java`
- Active example scene: `app/src/main/java/online/davisfamily/warehouse/testing/TestScene.java`
- Scene selection is explicit through `DebugSceneOptions` / `DebugSceneKind`; `TOTE_TO_BAG` is the default scene.
- The codebase has:
  - generic engine/rendering/routing code under `online.davisfamily.threedee`
  - warehouse simulation and rendering code under `online.davisfamily.warehouse`

## Core Runtime Flow

1. `SoftwareRenderer.main()` parses scene options, creates `TestScene`, and starts the render loop.
2. `BaseScene.renderFrame()` updates input/camera state, clears buffers, and delegates scene work.
3. `BaseScene.drawObject()` updates the simulation, updates renderable behaviours, then draws renderables.
4. `SimulationWorld.update()` updates sim objects, sensors, events, then controllers.
5. Warehouse objects such as totes mutate their render transforms from simulation state.

Important runtime classes:

- `SimulationWorld`
- `SimulationContext`
- `RenderableObject`
- `RouteFollower`
- `RouteSegment`
- `Tote`
- `TransferZone`
- `TransferZoneMachine`
- `TransferZoneController`

## Current Machine Architecture

The current preferred machine pattern is explicit installation plus explicit handoff boundaries.

Established examples:

- `TipperSectionInstaller` / `TipperInstallation`
- `SortingSectionInstaller` / `SortingInstallation`
- `TipperToSorterSection`
- `BaggingSectionInstaller` / `BaggingInstallation`
- `IntegratedToteToBagDebugInstaller` / `IntegratedToteToBagDebugInstallation`

Useful boundaries:

- `ToteLoadPlanProvider`
- `TipperDownstreamFlow`
- `PackGroupReceiver`
- `PackGroupReservation`
- `BagReceiver`
- `BagReservation`

Keep machine-to-machine seams explicit. Do not collapse adjacent machines into one controller just because the real equipment is physically close.

## Tote-To-Bag / P2P Current State

The tote-to-bag/P2P work is materially complete and is the main reference pattern for local state machines.

Key behavior:

- `ToteToBagFlowController` is long-lived across multiple source totes.
- It owns the PDC/PRL/PCR transport cell and should not be recreated per tote.
- `ToteToBagBatchPlan` aggregates expected pack counts across one or more `ToteLoadPlan`s.
- PRL assignment is batch/order-scoped, not single-tote scoped.
- Dynamic PRL reassignment is arrival-driven:
  - seed only as many correlations as there are PRLs
  - assign an idle PRL when a pack arrives for an unassigned batch correlation
  - fail clearly if no idle PRL exists
- Tote admission gating exists so upstream can hold a tote before tipping:
  - `ToteToBagFlowController.canAdmit(ToteLoadPlan)`
  - `ToteTrackTipperFlowController.setToteAdmissionPredicate(...)`
- PRL release into PCR is based on PCR availability/current PCR work-in-flight.
- PCR-to-bagger handoff is based on downstream `PackGroupReceiver` availability.
- `BaggingMachine` separates intake/bagging state from active output discharge state.
- A later bag group can be accepted while an earlier completed bag is still discharging, if intake is clear.

Important constraint:

- The tote-to-bag controller should not reorder totes or solve global deadlock/scheduling. That belongs to the scheduler.

## Scheduler Direction

The active major work is the DSP/OSR scheduler.

Read:

1. `docs/scheduler/dsp_osr_scheduler_requirements.md`
2. `docs/scheduler/dsp-scheduler-implementation-plan.md`
3. The branch-specific plan referenced by the scheduler roadmap

Current scheduler decisions:

- The next branch is `feature/dsp-scheduler-json-loading`.
- Completed branches: domain, line readiness, OSR integration, live P2P admission, and debug observability.
- Scheduler decisions are visible in the existing selection overlay through scheduler debug state.
- The integrated debug scene currently exposes scheduler inspection by selecting `tipper_slide`.
- Product master and 12N JSON loading should produce domain data only; loaded data must not create renderables.
- Service centres are processed as whole release windows.
- Totes from different service centres should not be mixed, except naturally when one service centre finishes and the next begins.
- If the active service centre is blocked by dependencies or capacity, hold the active window rather than skipping to the next service centre.
- The scheduler should model P2P as an admission/capacity boundary first.
- Live P2P admission is candidate-specific and already uses `ToteToBagFlowController.canAdmit(...)` through the debug integration path.

Next branch:

```powershell
git switch master
git switch -c feature/dsp-scheduler-json-loading
```

Use `docs/scheduler/dsp-scheduler-implementation-plan.md` as the roadmap, then follow `docs/scheduler/dsp-scheduler-json-loading-plan.md` step by step.

## DSP Model Notes

Use the terminology in `docs/scheduler/dsp_osr_scheduler_requirements.md`.

Important distinctions:

- `Notional Tote` is the logical grouping/correlation unit.
- `Physical Tote` / `Load Unit` is the actual container.
- `OrderType` controls start location, dependencies, routing intent, and lifecycle.
- `ToteType` controls physical carrier role/capability.
- `MANUAL_FLOW` is a flow characteristic, not an `OrderType`.

Order types:

- `ADAPTED`
- `EMPTY`
- `ASSOCIATED`
- `FULL_PACK`

Product classification:

- `AUTOMATED`
- `SORTABLE`
- `MANUAL`
- `isThirdParty` is orthogonal to category

Product classification comes from product master data, not 12N.

## Renderable Lifecycle / Performance

A production-scale run may load around 110,000 packs, but only a fraction should be active renderables.

Current performance direction:

- Loaded order/item data is not the same thing as active sim/render objects.
- Orders waiting in OSR are data only.
- Packs inside unreleased OSR orders are data only.
- Create a tote renderable only when an OSR order is released or an EMPTY order allocates a tote at AV02.
- Create pack renderables only for released/active totes.
- Hide or skip contained pack renderables when the tote lid is closed.
- Once packs enter a bag/bagger black-box stage, individual pack renderables can be hidden, retired, or pooled while logical contents remain in domain state.
- Add a cheap visibility flag or equivalent mechanism before broad visual scale-up.
- Hidden renderables should be skipped early in update/render traversal.
- Avoid allocation/destruction churn inside the main tick loop.

## Remaining Machine Work

These machines still need implementation using the established machine-state/install-result style:

- lid opening machine
- tote strapping machine
- scheduler-controlled tote buffer

The production P2P/tote-to-bag area has five P2P instances, each with its own tipper, bagger, and about 31 PRLs. Keep the current separation between tipper, sorter, PDC/PRL/PCR, and bagger unless a concrete integration issue proves otherwise.

## Current Testing Practice

The user runs Gradle tasks and reports results.

When requesting verification, provide a focused command such as:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

For scheduler work, each implementation step should introduce a focused test class and ask the user to run only that test first.

Use stable event/outcome assertions. Avoid tests that depend on transient PRL/PCR state after arbitrary update counts unless the transient state itself is the contract being tested.

## Historical Reference Docs

Use these only when working in their domains:

- `docs/tote-to-bag-requirements.txt`
- `docs/bagging_machine_requirements.txt`
- `docs/tipper-route-mounted-machine-architecture.md`
- `docs/route-system.md`
- `docs/selectable-object-command-panel.md`
- `docs/conveyor-drive-representation.md`
