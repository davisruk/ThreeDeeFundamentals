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

The active major work is a lifecycle-first DSP/OSR scheduling programme. FULL_PACK and ASSOCIATED are logical order types whose inbound physical totes are never reused as outbound dispatch totes. Logical/physical identity, inbound tote lifecycle, bag planning/provenance, outbound physical tote allocation, OSR physical inventory, and the operational simulation clock are complete, verified, and merged. Rate-limited service-centre supply is the current planned feature on `feature/dsp-rate-limited-service-centre-supply`.

Read:

1. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp_osr_scheduler_requirements.md`
4. `docs/scheduler/dsp-scheduler-implementation-plan.md`
5. `docs/machines/exceptions-station-requirements.md`
6. `docs/machines/phase-1-stations-roadmap.md`

Current scheduler decisions:

- Generic standalone transfer-machine support, adapting station Phase 1, Third Party Area Phase 1, simulation reset, and the scheduler worker-thread boundary are complete and merged.
- Completed scheduler branches: domain, line readiness, OSR integration, live P2P admission, debug observability, JSON loading, renderable visibility/lifecycle, machine wait queues, and scheduler thread.
- Scheduler decisions are visible in the existing selection overlay through scheduler debug state.
- The integrated debug scene currently exposes scheduler inspection by selecting `tipper_slide`.
- Third Party Phase 1 separates the real CSV product-master export from 12N JSON loading. Loaded data does not create renderables.
- Renderable visibility/lifecycle support is complete. Hidden renderables are skipped early in update/draw/pick, and current pack visual paths use visibility to hide contained or inactive packs.
- Service-centre supply authorization and individual OSR processing release are distinct operations.
- Letchworth (`104`) and Swansea (`108`) physical inbound totes are preloaded in OSR at the 06:00 start; later service centres are authorized in descending configured priority as OSR occupancy reaches a configurable low-water mark.
- Authorization feeds physical totes into OSR through a configurable rate-limited stream. Baselines are 1,200 totes/hour peak and 400 totes/hour for a representative busy hour.
- Upstream supply for an authorized service centre is ADAPTED first, followed by FULL_PACK and ASSOCIATED. EMPTY remains logical until AV02 supplies a physical tote.
- The OSR may contain more than one authorized service centre. ADAPTED and FULL_PACK may process concurrently, and an ASSOCIATED/EMPTY order becomes eligible when its own preparation dependencies are terminal.
- P2P service-centre isolation is enforced through sticky line ownership rather than a single global service-centre release window. A line cannot change service centre until it is fully quiescent and its current outbound tote is closed.
- Candidate ranking is pharmacy-grouped and deterministic. There is no confirmed ASSOCIATED/EMPTY-before-FULL_PACK priority.
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
- Bag planning now preserves patient and prescription identity from 12N, assigns deterministic prescription-plus-ordinal `BagKey` values, and retains immutable physical-pack source provenance.
- Planned DSP tote loads preserve physical tote, pack, dimensions, and order while replacing legacy correlations with planned bag correlations at the P2P boundary.
- Missing logical lines do not create synthetic physical packs or bags.
- OSR physical inventory now counts distinct inbound manifests against configurable capacity, with a production baseline of 1,200 totes.
- Startup preload defaults to every retained physical manifest for Letchworth (`104`) and Swansea (`108`) in assembled dataset order.
- EMPTY startup authorization is represented separately and consumes no physical OSR capacity.
- Several physical manifests may belong to one logical sheet and remain distinct inventory entries.
- Physical inventory admission/departure, lifecycle registration/activation, and scheduler status are separate transitions.
- Physical departure is an explicit simulation-thread commit performed only after a future downstream release has been accepted.
- Immutable OSR snapshots expose occupancy, remaining capacity, grouping, physical lookup, and departure history without yet extending `WarehouseSchedulerSnapshot`.
- Outbound allocation now supplies independent physical tote identities per P2P line, enforces service-centre/pharmacy purity and bag-count capacity, and records deterministic source-to-output sheet allocation without mutating provenance.
- The generic `StoredBagReceiver` remains the bagger boundary; `OutboundToteAllocationController` applies completed runtime bags to DSP allocation state on the simulation thread.
- Closed outbound totes retain active `OUTBOUND` sheet assignments for later dispatch/32R work. Generated sheets prevent one source output sheet from being active on two outbound totes.
- DSP business time now derives from absolute elapsed simulation time through stateless `DspOperationalClock` mapping.
- Production clock defaults are day 0 `06:00`, day 0 `22:00`, and day 1 `00:00` hard cutoff. Post-midnight time retains an explicit day offset.
- Immutable clock snapshots distinguish normal operations, overtime, and hard cutoff reached. Exact `22:00` is overtime; exact day +1 midnight is hard cutoff reached.
- `DspOperationalClockController` follows `SimulationContext` absolute time and must be registered before later scheduler-snapshot consumers.
- Generic fixed-step execution supports realtime, accelerated visual, and headless semantics with bounded steps, retained backlog, render-due signals, and requested/achieved speed snapshots.
- Fixed-step execution is not yet wired into `SoftwareRenderer`; current visual scene timing and rendering remain unchanged.
- Hard cutoff is currently observational and performs no tote closure, fulfilment mutation, scheduler command, or run termination.

The agreed next programme is split into short-lived branches from `master`:

1. logical/physical identity domain;
2. inbound physical tote lifecycle and 12N mapping;
3. bag planning and provenance;
4. outbound physical tote allocation;
5. OSR physical inventory and preload;
6. operational simulation clock;
7. rate-limited service-centre supply;
8. dependency-ready operational release and pharmacy-grouped ranking;
9. sticky P2P service-centre leases;
10. deadline-aware elastic line allocation;
11. full-day analysis, metrics, and inspection.

Each branch must have its own decision-complete, step-based plan before implementation. Exception Station Phase 1 should resume after the bag/provenance and outbound-tote foundation is in place, because short picks, NS bags, and exception correction must operate on the correct physical lifecycle.

Current programme position:

- logical/physical identity: complete and merged;
- inbound physical tote lifecycle and 12N transport-container mapping: complete and merged;
- bag planning and provenance: complete, verified, and merged, with detailed plan at `docs/scheduler/dsp-bag-planning-provenance-plan.md`;
- outbound physical tote allocation: complete, verified, and merged, with detailed plan at `docs/scheduler/dsp-outbound-tote-allocation-plan.md`;
- OSR physical inventory and preload: complete, verified, and merged, with detailed plan at `docs/scheduler/dsp-osr-physical-inventory-plan.md`;
- operational simulation clock: complete, verified, and merged, with detailed plan at `docs/scheduler/dsp-operational-simulation-clock-plan.md`;
- rate-limited service-centre supply: detailed plan ready at `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`; implementation is current;
- Exception Station Phase 1 now has the required lifecycle/bag/outbound foundation but remains a separate later feature.

Compatibility note:

- The deprecated eight-argument `DspOrderItem` constructor remains intentionally available for legacy fixtures. It generates line-specific placeholder patient and prescription identities; modified DSP production paths use real 12N identity.

## Completed Work: Third Party Area Phase 1

The implementation is complete, verified, and merged.

Current branch contract:

- Load `app/md/product_automation.csv` independently from 12N JSON into an in-memory repository.
- Treat nonblank `thirdPartyLocation` as Third Party and retain bin location plus dimensions.
- Use 12N line type for order-specific processing; product master does not decide FULL_PACK/ADAPTED/MANUAL flow.
- Correct prepared-line identity to target order id plus line reference. `referenceSheetNumber` is protocol-only.
- Exclude MANUAL messages/lines and report them during ingestion.
- Successful direct and ADAPTED-preparation Third Party picks use configurable area capacity and exactly-once completion handling.
- Preserve conservative OSR dependency release.
- Defer short picks, incomplete outcomes, NS labels, Exception routing, stock tracking, and detailed shelving/operative visuals.
- The `third-party` debug scene covers ADAPTED preparation, ASSOCIATED direct fulfilment, pass-through, FULL_PACK Third Party routing, inspection, and reset.
- An integration test proves a Third Party ADAPTED source line can be stored and later appear in the corresponding ASSOCIATED tote plan through Adapting collection.
- Focused tests, the complete Gradle suite, visual checks, and `ALT+R` reset verification passed.

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
- Third Party Area: Phase 1 complete and merged
- logical/physical tote lifecycle, bag provenance, and outbound allocation: complete, verified, and merged
- OSR physical inventory and preload: complete, verified, and merged
- operational simulation clock: complete, verified, and merged
- rate-limited service-centre supply: detailed plan ready; current feature branch
- Exception Area: lifecycle foundation is available; implementation remains deferred to its own branch
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

- A logical order sheet is the planning, dependency, and reporting unit identified by `orderId + sheetNumber`; it is not a physical tote.
- A physical tote/load unit is an independently identified carrier whose assignments change over its lifecycle.
- One logical order sheet may be manifested by multiple inbound physical totes.
- Inbound FULL_PACK and ASSOCIATED physical totes terminate at P2P and are never reused as outbound dispatch totes.
- Outbound physical totes are introduced at bagging output, are pharmacy- and service-centre-pure, and contain completed bags rather than inbound loose packs.
- `OrderType` controls start location, dependencies, routing intent, and lifecycle.
- `ToteType` controls physical carrier role/capability.
- Historical `MANUAL_FLOW` data exists but is excluded from active simulation.

Additional lifecycle rules:

- `lineReference` is the globally distinct ADAPTED-to-ASSOCIATED line correlation.
- `referenceOrderId` identifies the target ASSOCIATED logical order; `referenceSheetNumber` remains protocol data and is not used as a meaningful discriminator.
- Bag grouping is prescription-based where possible and may split when configured bag capacity is reached.
- Each P2P instance has one current outbound receiving tote, with configurable bag-count capacity and best-effort patient affinity.
- EMPTY receives its physical tote at AV02. If exceptional work requires an empty NS bag, allocate a dedicated pharmacy-pure outbound tote rather than introducing cross-pharmacy complexity.
- Physical tote assignment history and pack/bag provenance must remain inspectable; a mutable current owner field alone is insufficient.

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

- exception station Phase 1 (resume after lifecycle, bag-provenance, and outbound-tote foundations)
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
