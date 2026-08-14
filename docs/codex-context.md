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

Simulation and rendering currently run sequentially on the same game-loop thread. Swing input runs on the event-dispatch thread, and threaded DSP scheduler evaluation runs on its own worker. Render-thread separation has not been implemented.

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

1. `docs/machines/third-party-station-phase-1-plan.md` while the current branch is active
2. `docs/machines/third-party-station-requirements.md`
3. `docs/scheduler/dsp_osr_scheduler_requirements.md`
4. `docs/scheduler/dsp-scheduler-implementation-plan.md`
5. `docs/machines/phase-1-stations-roadmap.md`

Current scheduler decisions:

- The latest completed scheduler-adjacent branch is `feature/dsp-scheduler-thread`.
- Generic standalone transfer-machine support, adapting station Phase 1, and simulation reset are complete and merged.
- The active branch is `feature/third-party-station-phase-1`.
- Completed scheduler branches: domain, line readiness, OSR integration, live P2P admission, debug observability, JSON loading, renderable visibility/lifecycle, machine wait queues, and scheduler thread.
- Scheduler decisions are visible in the existing selection overlay through scheduler debug state.
- The integrated debug scene currently exposes scheduler inspection by selecting `tipper_slide`.
- The active branch separates the real CSV product-master export from 12N JSON loading. Loaded data must not create renderables.
- Renderable visibility/lifecycle support is complete. Hidden renderables are skipped early in update/draw/pick, and current pack visual paths use visibility to hide contained or inactive packs.
- Service centres are processed as whole release windows.
- Totes from different service centres should not be mixed, except naturally when one service centre finishes and the next begins.
- If the active service centre is blocked by dependencies or capacity, hold the active window rather than skipping to the next service centre.
- The scheduler should model P2P as an admission/capacity boundary first.
- Machine wait queues now separate scheduler release admission from machine processing admission in the integrated debug P2P path:
  - release admission means there is station input waiting space
  - machine processing admission remains local to the downstream machine
  - for P2P, scheduler release is based on input queue capacity
  - `ToteToBagFlowController.canAdmit(...)` remains the local tipper processing gate
- Scheduler evaluation now preserves the thread boundary:
  - simulation thread builds immutable snapshots
  - scheduler worker computes decisions only
  - simulation thread applies commands/mutations
  - synchronous evaluation remains available as a fallback
  - integrated debug inspection exposes scheduler mode, in-flight state, and last completed evaluation sequence

Use `docs/machines/third-party-station-phase-1-plan.md` for the active branch and execute one verified step at a time.

## Active Work: Third Party Area Phase 1

The active branch is `feature/third-party-station-phase-1`.

Current branch contract:

- Load `app/md/product_automation.csv` independently from 12N JSON into an in-memory repository.
- Treat nonblank `thirdPartyLocation` as Third Party and retain bin location plus dimensions.
- Use 12N line type for order-specific processing; product master does not decide FULL_PACK/ADAPTED/MANUAL flow.
- Correct prepared-line identity to target order id plus line reference. `referenceSheetNumber` is protocol-only.
- Exclude MANUAL messages/lines and report them during ingestion.
- Add successful direct and ADAPTED-preparation Third Party picks with configurable area capacity.
- Preserve conservative OSR dependency release.
- Defer short picks, incomplete outcomes, NS labels, Exception routing, stock tracking, and detailed shelving/operative visuals.

## Phase 1 Station Direction

Phase 1 station work should be state-complete and visually cheap.

Core rules:

- Implement station state machines, input wait queues, route stops, scheduler-facing availability, and logical inventory effects first.
- Use placeholder renderables and simple inspection text only where needed to verify behavior.
- Do not spend Phase 1 effort on detailed station meshes, bins/racks, pack transfer animation, or visual polish.
- Defer detailed presentation and animation to separate Phase 2 visualisation plans.

Planned Phase 1 order:

- inline transfer targets: complete
- adapting station: Phase 1 complete and merged
- simulation reset runtime interlude: complete and merged
- Third Party Area: active
- Exception Area: next after Third Party
- tote lid open/close machines

Adapting station Phase 1 established the hardest merge/preparation model:

- `STORE`: adapted/preparation totes deposit prepared packs into logical station storage.
- `COLLECT`: collecting/dispatch totes collect prepared packs from logical station storage before P2P/tote-to-bag.
- Loaded ADAPTED prepared-line data is work to process, not automatically completed readiness.
- STORE processing makes adapted prepared-line keys scheduler-ready.
- The source ADAPTED tote is removed/stored after STORE and can disappear in Phase 1.
- COLLECT updates the collecting tote load plan so P2P can process the newly collected packs.
- `FULL_PACK` orders never collect adapted lines.

## DSP Model Notes

Use the terminology in `docs/scheduler/dsp_osr_scheduler_requirements.md`.

Important distinctions:

- `Notional Tote` is the logical grouping/correlation unit.
- `Physical Tote` / `Load Unit` is the actual container.
- `OrderType` controls start location, dependencies, routing intent, and lifecycle.
- `ToteType` controls physical carrier role/capability.
- Historical `MANUAL_FLOW` data exists but is excluded from active simulation.

Order types:

- `ADAPTED`
- `EMPTY`
- `ASSOCIATED`
- `FULL_PACK`

Data-source split:

- 12N line type owns order-specific FULL_PACK/ADAPTED/MANUAL processing intent.
- Product master owns Third Party bin location and physical dimensions.
- A product may be automatable in general but appear on an ADAPTED line because Columbus made an order-specific labelling decision.
- MANUAL line type is excluded from active simulation.

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

Implemented visibility support:

- `RenderableObject` has a visibility flag.
- Hidden renderables skip update, draw, and picking.
- `Tote.areLidsOpen()` supports contained pack render decisions.
- `PackRenderableVisibility` controls current pack renderable show/hide/reset behavior.
- The integrated/debug tipper rigs open inbound source tote lids through `DebugToteLidController` after actual motion starts, rather than opening them in `TipperDemoFixtures`. This lets closed-lid contained pack visibility be verified visually.

Threading status:

- scheduler evaluation is separated through immutable snapshots and result polling
- simulation and rendering are not separated; `BaseScene.drawObject(...)` updates simulation and then draws on the same game-loop thread
- simulation objects still directly mutate some renderable transformations
- a future render split should first introduce a render-pose/snapshot boundary for dynamic totes, packs, bags, and moving machine parts
- prefer a latest-complete double-buffered snapshot over an accumulating FIFO of stale render frames

## Remaining Machine Work

These machines still need implementation using the established machine-state/install-result style:

- Third Party Area Phase 1 (active)
- exception station Phase 1
- lid opening machine
- lid closing machine
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
