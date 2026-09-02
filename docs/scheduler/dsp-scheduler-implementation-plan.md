# DSP Scheduler Branch Roadmap

## Summary

This document is the scheduler programme roadmap. Detailed, step-by-step implementation instructions live in one plan document per feature branch so a weaker model can execute each branch without needing to reason across the whole scheduler programme.

Current note: generic transfer-machine support, adapting station Phase 1, Third Party Area Phase 1, simulation reset, and the scheduler worker-thread boundary are complete and merged. Logical/physical identity, inbound tote lifecycle, bag planning/provenance, outbound physical tote allocation, OSR physical inventory, the operational simulation clock, rate-limited service-centre supply, physical OSR processing release, dependency-ready operational release, operational route-target integration, OSR outbound route launch, physical warehouse transport routing, P2P-local arrival consumption, sticky P2P service-centre leases, deadline-aware elastic line allocation, AV02 operational allocation, generic station processing, generic station route continuation, and the operational EMPTY end-to-end proof are complete, verified, and merged to `master`; the proof merged at `afe40f5`. Full-day execution, metrics, and inspection using the explicitly uncalibrated elastic profile are active planned work on `feature/dsp-full-day-analysis-metrics-inspection`.

The scheduler architecture remains snapshot/command based:

```text
WarehouseSchedulerSnapshot -> DspReleaseScheduler -> SchedulerCommand / BlockedDecision
```

Machine/controllers own live mutable simulation state on the simulation thread. The scheduler reads immutable snapshots and emits commands. The simulation thread applies those commands at safe points.

## Branch Strategy

Use short-lived feature branches from `master`.

Recommended flow:

```powershell
git switch master
git pull
git switch -c feature/<scheduler-feature>
```

After a branch is green and committed, merge it back to `master`, then start the next branch from updated `master`.

Avoid a long-lived `feature/dsp-scheduler` parent branch unless there is a deliberate decision to keep all scheduler work off `master` until the whole scheduler stack is complete. The current preference is feature branches merged to `master` in order.

## Branches

### `feature/dsp-scheduler-domain`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-domain-plan.md`

Purpose:

- Add the pure DSP scheduler domain model.
- Add route derivation from product master data.
- Add immutable scheduler snapshots and station admission snapshots.
- Add dependency evaluation, service-centre windowing, release decisions, and scheduler commands.
- Add a snapshot-based P2P admission contract without live tote-to-bag integration.

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```

### `feature/dsp-scheduler-line-readiness`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-line-readiness-plan.md`

Purpose:

- Correct the scheduler domain so 12N line-level `pharmacyId` and prepared-line references are represented.
- Enforce pharmacy purity for dispatch orders: `ASSOCIATED`, `EMPTY`, and `FULL_PACK`.
- Keep `ADAPTED` as a mixed-pharmacy preparation batch flow.
- Replace notional-tote-level adapted/manual readiness with target-order line readiness.
- Keep this branch domain-only; do not add OSR release integration.

### `feature/dsp-scheduler-osr-integration`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-osr-integration-plan.md`

Purpose:

- Add scheduler-driven release for queued debug OSR totes after the existing bootstrap tote.
- Add a simulation-thread command application path for `ReleaseOrderCommand`.
- Replace/wrap debug queued tote injection so queued tote release is selected by scheduler decisions.
- Add scheduler-controlled queued tote renderables to the scene only at release time.
- Keep scheduler evaluation synchronous for this branch; do not add a scheduler thread yet.

Explicit non-goal:

- Do not remove the current primary/bootstrap tote special case from `TipperSectionInstaller` / `TipperToSorterSection` in this branch.

### `feature/dsp-scheduler-p2p-live-admission`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-p2p-live-admission-plan.md`

Purpose:

- Add a simulation-thread snapshot adapter over the existing tote-to-bag local state.
- Avoid calling live `ToteToBagFlowController` state from a scheduler thread.
- Add an adapter from scheduler order/tote data to `ToteLoadPlan`.
- Feed live P2P admission into scheduler station admission snapshots.
- Add candidate-specific station admission resolution so P2P can evaluate the actual candidate tote instead of using one static station-wide answer.

### `feature/dsp-scheduler-debug-observability`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-debug-observability-plan.md`

Purpose:

- Make scheduler-driven debug scenes visibly attributable to scheduler decisions.
- Capture the last scheduler evaluation and release application result from the debug injector.
- Display active service centre, waiting orders, release decisions, blocked candidates, and blocked reasons through the existing selection inspection overlay.
- Avoid breakpoints as the primary way to verify scheduler behavior.
- Do not add command buttons, Swing panels, scheduler threading, JSON loading, or new scheduling rules in this branch.

Notes:

- Scheduler debug state is exposed through `SchedulerDebugState` / `SchedulerDebugSnapshot`.
- `SchedulerDebugInspectable` formats the latest scheduler evaluation/application result for the existing inspection overlay.
- The integrated debug scene currently registers scheduler inspection against `tipper_slide`, because that is the reliable selectable part of the tipper assembly.
- Longer term, the tipper assembly should route selectable children to a common root selection target.

### `feature/dsp-scheduler-json-loading`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-json-loading-plan.md`

Purpose:

- Add product master and 12N loaders after sample schemas are provided.
- Keep product classification sourced from product master data.
- Revisit whether a database is useful after measuring in-memory loading and query shape.

Notes:

- This completed branch records the original JSON-loading implementation. Its combined product/12N model and active MANUAL preparation assumptions are superseded by `docs/machines/third-party-station-requirements.md` and `docs/machines/third-party-station-phase-1-plan.md`.
- Jackson databind is available through the Gradle version catalog.
- `online.davisfamily.warehouse.sim.dsp.io` now contains product master loaders, raw 12N DTOs, 12N dispatch/preparation mappers, `LoadedDspData`, and `LoadedDspSchedulerRuntimeFactory`.
- 12N dispatch messages become existing `NotionalToteOrder` / `DspOrderItem` objects.
- Manual/adapted 12N preparation messages produce prepared `DspOrderItem`s and `PreparedLineKey`s, not dispatch orders.
- Loaded data remains domain/runtime state only; no renderables are created by JSON loading.

### `feature/renderable-visibility-lifecycle`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/renderable-visibility-lifecycle-plan.md`

Purpose:

- Add cheap visibility/skipping support to `RenderableObject`.
- Apply it to totes, contained packs, free packs, and bags.
- Ensure loaded order/pack data does not imply active renderable creation.

Notes:

- `RenderableObject` visibility now skips update, draw, and picking.
- Pack visual paths use `PackRenderableVisibility` rather than off-screen translation for hidden/reset state.
- `Tote.areLidsOpen()` now drives contained pack visibility in the integrated tipper-to-sorter path.
- Downstream debug positioning explicitly shows active packs after they leave the tipper/sorter area.

### `feature/machine-wait-queues`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/machine-wait-queues-plan.md`

Purpose:

- Correct the scheduler/machine architecture exposed during renderable visibility visual checks.
- Separate "can enter station waiting space" from "can this machine process the tote now".
- Add a small machine wait queue primitive with manually configured capacity.
- Apply it first to the debug P2P/tipper input path.
- Change scheduler release admission for the debug P2P path to queue capacity, while keeping `ToteToBagFlowController.canAdmit(...)` as the local tipper processing gate.

Notes:

- This work is intentionally inserted mid DSP scheduler implementation before further scheduler behaviour.
- Machine queue admission is now explicit in the integrated debug P2P path.
- Do not reintroduce ad hoc two-slot state inside `ToteTrackTipperFlowController`; use the wait queue abstraction.
- `MachineWaitQueue` / `MachineWaitQueueSnapshot` provide the generic FIFO queue primitive.
- `TipperInputQueue` stores queued `TipperTotePayload`s for the debug P2P path.
- `QueuedReleaseP2pAdmission` gates scheduler-side P2P release by queue capacity.
- `QueuedTipperFlowScheduledToteReleaseTarget` releases scheduled totes into the input queue rather than directly into the tipper flow.
- `TipperInputQueueController` drains the queue into `ToteTrackTipperFlowController` when the tipper flow can accept the next tote.
- `DebugToteLidController` is a rig-only temporary lid opener that opens inbound tote lids after actual motion starts, preserving future room for a real lid-opening machine.

### `feature/dsp-scheduler-thread`

Status: complete and green.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-thread-plan.md`

Purpose:

- Move scheduler evaluation to a separate thread only after synchronous snapshot/command integration is proven.
- Publish immutable `WarehouseSchedulerSnapshot`s to the scheduler thread.
- Publish `SchedulerCommand`s back to a simulation-thread command queue.
- Keep all live simulation mutations on the simulation thread.
- Keep a synchronous evaluation source as an explicit fallback.
- Wire the integrated debug scene through the threaded evaluation source only after the fallback boundary is in place.

Notes:

- This branch is the first concrete enforcement of the future thread boundary:
  - worker threads receive immutable snapshots
  - worker threads return decisions/results
  - the simulation thread applies all mutations
- Rendering remains on the current thread model in this branch. Render-thread separation is deferred, but this boundary should make that later split easier.
- `SchedulerEvaluationSource` is the fallback boundary.
- `SynchronousSchedulerEvaluationSource` preserves the old behavior.
- `ThreadedSchedulerEvaluationSource` uses one named platform thread through `Executors.newSingleThreadExecutor(...)`; virtual threads are not used.
- `ScheduledDebugToteInjectorController` can use either source.
- The integrated debug scene uses threaded evaluation.
- The scheduler overlay exposes mode, in-flight state, and last completed evaluation sequence.

### `feature/inline-transfer-targets`

Status: completed and merged. The initial target-selection work was completed, then superseded by the standalone transfer-machine modelling work.

Detailed implementation doc:

- `docs/machines/inline-transfer-targets-plan.md`
- `docs/machines/transfer-machine-standalone-plan.md`

Purpose:

- Extend transfer-zone routing so one physical transfer window can choose a concrete target segment, entry distance, and travel direction.
- Support inline transfer layouts required by the adapting area.
- Keep transfer control separate from transfer renderables, with explicit transfer-controlled route segments as the preferred model.

### `feature/adapting-station-phase-1`

Status: complete and merged.

Detailed implementation doc:

- `docs/machines/adapting-station-phase-1-plan.md`

Purpose:

- Add the Phase 1 adapting station now that standalone transfer-machine support is available.
- Support STORE visits from ADAPTED preparation totes.
- Support COLLECT visits for ASSOCIATED/EMPTY dispatch totes.
- Stage adapted prepared lines logically and only mark them ready after STORE processing.
- Update collecting tote load plans so P2P can process collected packs.
- Keep visuals minimal and defer racks/bins/pack animation to Phase 2.

Notes:

- The adapting area supports multiple benches and per-bench stop sensors in the debug scene.
- Store/pharmacy affinity selects preferred benches while preserving deterministic fallback.
- Logical adapting storage now models `bench -> rack -> shelf -> bin` without rendered storage visuals.
- STORE source totes disappear after bench completion.
- COLLECT totes return to the main line after load-plan update.

### `feature/simulation-reset`

Status: complete and merged.

Detailed implementation doc:

- `docs/runtime/simulation-reset-plan.md`

Purpose:

- Add safe active-scene reset through `ALT+R`.
- Apply reset on the game-loop thread rather than Swing's event-dispatch thread.
- Add debug-runtime disposal so reset does not retain threaded scheduler workers.
- Reinstall the same debug scene with fresh simulation/runtime/renderable state while preserving camera and display modes.

Explicit non-goals:

- no scheduler behavior changes
- no rewind/forward history
- no simulation/render thread split

### `feature/third-party-station-phase-1`

Status: complete, verified, and merged.

Detailed documents:

- `docs/machines/third-party-station-requirements.md`
- `docs/machines/third-party-station-phase-1-plan.md`

Purpose:

- Separate CSV product-master loading from 12N JSON loading.
- Correct prepared-line identity to target order id plus globally distinct line reference.
- Exclude historical MANUAL data from active simulation and report it during ingestion.
- Add line-aware direct and ADAPTED-preparation Third Party work selection.
- Add a capacity-aware logical Third Party Area beside a through-track.
- Update tote load plans after successful picks so ADAPTED work can continue to Adapting and fulfilment work can continue to P2P.
- Preserve conservative dependency release and defer short picks/NS labels/Exception routing.

Implemented outcome:

- CSV product-master loading and JSON 12N ingestion are independent.
- Third Party visits are derived at line level and admitted against immutable, candidate-specific capacity state.
- Direct picks update fulfilment tote plans exactly once; ADAPTED preparation is available to the Adapting store/collect lifecycle.
- The `third-party` debug scene, inspection, full tests, visual checks, and `ALT+R` reset verification are green.
- Missing-master handling, short picks, incomplete outcomes, NS labels, and Exception routing remain assigned to the Exception Station branch.

### `feature/dsp-logical-physical-identity`

Status: complete, green, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-logical-physical-identity-plan.md`

Purpose:

- Add typed logical `OrderSheetKey` and physical `PhysicalToteId` identities.
- Replace the unused logical-valued `ToteType` with an explicitly physical tote role.
- Add physical tote lifecycle states and validated transitions.
- Add simulation-thread-owned assignment history with logical/physical cardinality enforcement.
- Publish immutable lifecycle snapshots for later scheduler and inspection integration.

Explicit non-goals:

- no 12N `transportContainer` mapping;
- no station, tote-load-plan, scheduler-command, machine, or renderable migration;
- no bag planning, outbound tote allocation, generated sheets, or Exception behavior.

Follow-on branch:

- `feature/dsp-inbound-tote-lifecycle`

### `feature/dsp-inbound-tote-lifecycle`

Status: complete, green, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-inbound-tote-lifecycle-plan.md`

Purpose:

- Retain 12N `transportContainer` as typed physical inbound tote identity.
- Keep logical order sheets separate from physical inbound manifests.
- Group several inbound manifests under one logical `OrderSheetKey` while preserving manifest contents.
- Register and advance inbound physical totes through the lifecycle ledger without activating all loaded data.
- Keep EMPTY manifest-free until AV02 allocates a PRE_P2P physical tote.
- Separate logical station admission profiles from physical Adapting and Third Party visits.
- Make tote load plans explicitly physical while retaining narrow generic string bridges.

Explicit non-goals:

- no OSR inventory/replenishment policy;
- no bag planning or patient/prescription provenance;
- no outbound tote allocation or output-sheet splitting;
- no Exception Station behavior or 32R.

Follow-on branch:

- `feature/dsp-bag-planning-provenance`

### `feature/dsp-bag-planning-provenance`

Status: complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-bag-planning-provenance-plan.md`

Purpose:

- Retain 12N patient and prescription identity at line level.
- Add deterministic typed bag identity using prescription plus ordinal.
- Preserve immutable physical-pack source provenance through Third Party and Adapting work.
- Plan actual physical packs into configurable pack-count bags.
- Emit P2P-compatible correlation groups while retaining source, fulfilment, physical input tote, and bag traces.

Implemented outcome:

- 12N line mapping retains validated patient and prescription identity.
- `BagKey` provides deterministic prescription-plus-ordinal bag identity.
- A simulation-owned provenance registry retains immutable physical-pack source facts through Third Party and Adapting creation paths.
- Deterministic bag planning groups actual physical packs by prescription behind a replaceable pack-count capacity policy.
- Rewritten P2P tote load plans preserve physical tote, pack, dimension, and ordering identity while replacing only bag correlation.
- Cross-station tests prove source/fulfilment/input-tote/bag traceability, deterministic overflow, and absence of fabricated packs for missing logical lines.
- Focused tests, complete tests, visual checks, and `ALT+R` reset checks are green.

Compatibility note:

- The deprecated eight-argument `DspOrderItem` constructor remains as a fixture bridge with line-specific placeholder patient and prescription values; modified production code uses complete 12N identity.

Explicit non-goals:

- no outbound physical tote allocation or reservoir;
- no pharmacy/service-centre tote closure policy;
- no output-sheet splitting;
- no short-pick outcome, NS bag, Exception Station, or 32R behavior.

Follow-on branch:

- `feature/dsp-outbound-tote-allocation`

### `feature/dsp-outbound-tote-allocation`

Status: complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-outbound-tote-allocation-plan.md`

Purpose:

- Allocate completed planned bags to independently supplied outbound physical totes per P2P line.
- Enforce service-centre purity, pharmacy purity, configurable bag-count capacity, and closed-tote immutability.
- Preserve best-effort patient affinity without reordering or violating hard constraints.
- Record bag-to-outbound-tote and source-to-output-sheet allocation separately from immutable provenance.
- Generate deterministic output sheets when one logical source sheet would otherwise be active on two outbound totes.
- Advance outbound tote and logical-sheet assignment history through the existing physical lifecycle ledger.
- Bridge the existing generic stored bag receiver to DSP allocation through a simulation controller.

Implemented outcome:

- Each P2P line independently supplies deterministic outbound physical tote identities and maintains at most one open receiving tote.
- Completed bags are allocated in receiver order with service-centre purity, pharmacy purity, and configurable bag-count capacity.
- Inbound physical totes remain consumed at P2P and are never reused for outbound dispatch.
- Outbound tote closure advances lifecycle assignments from `OUTBOUND_BAG` to terminal-tote `OUTBOUND` state without reopening closed totes.
- Deterministic generated output sheets prevent one logical output sheet from being active on two physical outbound totes while preserving source order ID and immutable pack/bag provenance.
- `OutboundToteAllocationController` resolves authoritative planned bag correlations, validates ordered physical pack IDs, and removes runtime bags only after successful allocation.
- Multi-line and end-to-end scenarios verify independent line state, capacity splitting, pharmacy changes, lifecycle separation, and generated-sheet behavior.
- Focused tests, the complete suite, visual checks, and `ALT+R` reset verification are green.

Explicit non-goals:

- no OSR physical inventory or service-centre supply;
- no scheduler P2P line leases;
- no finite empty-tote reservoir or reservoir geometry;
- no Exception behavior, all-missing NS bag, or 32R;
- no outbound tote renderable, database, or new thread.

Deferred all-missing/NS behavior:

- All-missing prescriptions currently create no physical or planned bag and therefore no outbound allocation.
- Exception Station work must create the physical empty NS bag and route it to a dedicated pharmacy-pure outbound tote.
- NS labels, Exception outcomes, and 32R remain deferred; the current allocator does not fabricate scheduler orders or bags.

Follow-on branch:

- `feature/dsp-osr-physical-inventory`

### `feature/dsp-osr-physical-inventory`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-osr-physical-inventory-plan.md`

Purpose:

- Add configurable physical OSR capacity with a production baseline of 1,200 totes.
- Bootstrap every retained physical inbound manifest for Letchworth `104` and Swansea `108` at the 06:00 start.
- Keep EMPTY startup authorization separate from physical occupancy.
- Preserve multiple physical manifests for one logical sheet without collapsing identity.
- Expose immutable inventory, capacity, grouping, and departure snapshots for later supply and scheduler work.
- Keep inventory membership separate from lifecycle registration, activation, and scheduler order status.

Implemented outcome:

- `OsrInventoryConfig` provides configurable capacity and deterministic preload service-centre configuration, with production defaults of 1,200 totes and service centres `104` and `108`.
- `OsrInventoryBootstrapFactory` creates an atomic preload from assembled physical manifests while preserving dataset order and keeping EMPTY startup authorization separate.
- `OsrPhysicalInventory` owns physical admission and explicit departure commits on the simulation thread.
- `OsrInventorySnapshot` exposes immutable occupancy, remaining capacity, physical lookup, departure history, and deterministic service-centre/order-type grouping.
- Multiple physical manifests for one logical sheet remain distinct throughout inventory and scenario coverage.
- Focused tests, the complete suite, visual smoke checks, and reset checks are green.

Contracts for later supply and release branches:

- physical inventory transitions must not be conflated with lifecycle activation or scheduler status;
- a release path commits `recordDeparture(...)` only after downstream acceptance succeeds;
- EMPTY authorization consumes no physical OSR slot;
- supply and release logic must operate per physical manifest and derive logical-sheet summaries when required.

Explicit non-goals:

- no low-water service-centre authorization;
- no rate-limited inbound supply;
- no operational clock implementation;
- no scheduler physical-release command integration;
- no OSR renderables, metrics history, database, or new thread.

Follow-on branch:

- `feature/dsp-operational-simulation-clock`

### `feature/dsp-operational-simulation-clock`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-operational-simulation-clock-plan.md`

Purpose:

- Map absolute simulation elapsed time to configurable DSP operating date/time starting at `06:00`.
- Represent post-midnight time with an explicit operating-day offset.
- Expose immutable normal-operation, overtime, and hard-cutoff clock snapshots.
- Provide generic bounded fixed-step execution for realtime, accelerated visual, and headless semantics.
- Preserve pending simulation backlog under a configured work budget and report requested/achieved speed.
- Bridge business-time snapshots to `SimulationWorld` without changing existing machine duration units.

Implemented outcome:

- `OperationalDayTime` represents local operating time with an explicit non-negative day offset.
- `DspOperationalClockConfig` provides configurable operating boundaries and the production `06:00`/`22:00`/day +1 midnight baseline.
- Stateless `DspOperationalClock` maps absolute elapsed simulation time to immutable business-time snapshots using rounded nanoseconds.
- `DspOperationalClockController` follows authoritative `SimulationContext` time without accumulating an independent clock.
- `FixedStepExecutionDriver` supports realtime, accelerated visual, and headless semantics through repeated bounded steps, retained backlog, render-due signals, and immutable speed snapshots.
- Deterministic scenario coverage advances a complete operating window, proves exact boundary semantics, and proves reconstruction/reset behavior.
- Focused tests, complete tests, visual smoke checks, and reset checks are green.

Contracts for later branches:

- scheduler and supply logic consume immutable clock snapshots rather than wall-clock time;
- day offsets remain explicit for post-midnight timetable and deadline values;
- renderer integration applies each emitted fixed step separately and must not pass a scaled frame delta to the world;
- hard cutoff remains observational until a later command/application plan defines its mutations;
- achieved-speed measurement uses caller-supplied real duration and never enters pure scheduler evaluation.

Explicit non-goals:

- no service-centre timetable or completion outcome;
- no low-water authorization or rate-limited supply;
- no physical OSR release or scheduler command changes;
- no renderer-loop decimation wiring or complete headless warehouse runner;
- no render-thread separation, metrics history, or full-day execution.

Follow-on branch:

- `feature/dsp-rate-limited-service-centre-supply`

### `feature/dsp-rate-limited-service-centre-supply`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`

Purpose:

- Retain 12N `orderPriority` and derive deterministic service-centre supply order.
- Authorize later service centres when physical OSR occupancy reaches a configurable inclusive low-water mark.
- Admit authorized physical manifests individually at a configurable simulation-time rate.
- Preserve ADAPTED-first upstream order, physical manifest identity, and OSR capacity.
- Authorize EMPTY sheets logically without consuming an OSR slot.
- Publish immutable supply state for later scheduler, inspection, and metrics work.

Explicit non-goals:

- no physical OSR processing release or lifecycle activation;
- no scheduler candidate ranking or P2P allocation;
- no trunker timetable/deadline outcome;
- no renderables, visual rig wiring, complete full-day run, or new thread.

Implemented contracts to preserve:

- `DspServiceCentreSupplyPlanFactory` derives immutable priority-ordered service-centre batches from retained 12N priority and physical manifests.
- `DspServiceCentreSupplyCoordinator` owns live authorization and rate-limited admission progress on the simulation thread.
- `DspServiceCentreSupplyController` advances that coordinator from authoritative `DspOperationalClockSnapshot` values and ignores frame delta for supply timing.
- `DspSupplySnapshot` is the immutable boundary for later scheduler, inspection, and metrics consumers.
- OSR capacity is checked only for a manifest that is due. Genuine capacity blocks retain the head and resume without a burst; capacity that clears before a future due time preserves normal catch-up behavior.

Follow-on planning target:

- physical OSR processing release, connecting stored physical manifest identity to downstream acceptance, inventory departure, lifecycle activation, and scheduler commands without collapsing those transitions.

### `feature/dsp-osr-processing-release`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-osr-processing-release-plan.md`

Purpose:

- Derive immutable per-physical-tote release candidates from current OSR inventory and lifecycle state.
- Preserve multiple physical manifests for one logical sheet while enforcing one active sheet assignment.
- Add a typed physical OSR release command alongside the legacy order-centric debug command.
- Revalidate worker-produced commands against live simulation-thread inventory, lifecycle, identity, target, and clock state.
- Obtain downstream acceptance before committing OSR departure and lifecycle activation.
- Free OSR capacity for the completed rate-limited supply stream.

Explicit non-goals:

- no scheduler candidate ranking or physical command emission;
- no changes to `WarehouseSchedulerSnapshot`, `DspReleaseScheduler`, or existing visual debug release behavior;
- no EMPTY/AV02 allocation, sticky P2P leases, deadline-aware line allocation, renderables, or full-day execution.

Implemented contracts to preserve:

- `OsrProcessingReleaseSnapshotFactory` derives ordered physical candidates solely from current OSR inventory and lifecycle snapshots.
- `OsrProcessingReleaseCandidate` retains `PhysicalToteId`, `OrderSheetKey`, order type, service centre, source sequence, and any active same-sheet blocker without collapsing repeated manifests.
- `ReleasePhysicalToteFromOsrCommand` remains distinct from the legacy order-centric `ReleaseOrderCommand`.
- `OsrProcessingReleaseCommandHandler` revalidates live inventory, lifecycle, command identity, target identity, and one authoritative operational-clock snapshot on the simulation thread.
- Successful release invokes `OsrProcessingReleaseTarget.accept(...)` first, then commits `OsrPhysicalInventory.recordDeparture(...)`, then `InboundToteLifecycleController.activate(...)` at the same elapsed simulation time.
- Deferral, rejection, stale commands, target exceptions, and validation failures do not mutate local inventory or lifecycle state.
- A released slot is visible to `DspServiceCentreSupplyCoordinator`, allowing a due capacity-blocked physical manifest to resume without reordering.

Follow-on planning target:

- `feature/dsp-dependency-ready-operational-release`, composing physical candidate snapshots into scheduler evaluation, adding pharmacy-grouped deterministic ranking, and emitting the typed physical command.

### `feature/dsp-dependency-ready-operational-release`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

- `docs/scheduler/dsp-dependency-ready-operational-release-plan.md`

Purpose:

- Join immutable physical OSR candidates to exact logical order/dependency state.
- Make ADAPTED and FULL_PACK independently eligible and ASSOCIATED dependent only on its own prepared lines.
- Rank fully eligible physical work by service-centre cohort, stable pharmacy groups, and deterministic source order without order-type priority.
- Select only the first route-entry target and emit `ReleasePhysicalToteFromOsrCommand`.
- Preserve worker-thread evaluation and simulation-thread command application through the completed physical handler.
- Keep legacy order-centric scheduler/debug behavior unchanged.

Explicit non-goals:

- no EMPTY/AV02 physical allocation;
- no sticky P2P leases or active-line pharmacy affinity;
- no deadline-aware elastic line allocation;
- no production visual target migration, renderables, calibrated timing, or full-day run.

Implemented contracts to preserve:

- Physical candidates join exact manifest and logical-sheet identity without collapsing repeated manifests.
- ADAPTED and FULL_PACK are independently eligible; ASSOCIATED checks only its own ADAPTED prepared-line keys.
- First-route-entry admission and explicit selected target gate release; downstream stations do not gate OSR entry.
- Ranking chooses the highest-priority service-centre cohort containing eligible work, then stable pharmacy group and deterministic physical source order, with no order-type priority.
- The pure scheduler emits one typed physical command plus observable typed blocks.
- Operational synchronous/threaded sources preserve immutable worker evaluation; the simulation-thread controller applies through `OsrProcessingReleaseCommandHandler` without legacy logical release mutation.
- Fresh snapshots drive retry after deferral, and stale commands are rejected by live handler revalidation without duplicate downstream mutation.

Follow-on planning target:

- `feature/dsp-osr-outbound-route-launch`, because an OSR release must first enter a common
  outbound launch boundary while retaining its selected station as destination intent.
- `feature/dsp-warehouse-transport-routing` follows route launch and delivers physical totes to
  station-local arrival queues.
- P2P-local consumption and sticky service-centre leases follow physical station arrival.

### `feature/dsp-operational-route-target-integration`

Status: implementation complete, verified, and merged.

Detailed implementation doc:

`docs/scheduler/dsp-operational-route-target-integration-plan.md`

Purpose:

- Connect operational first-route target IDs to real station waiting/admission boundaries.
- Add production `OsrProcessingReleaseTarget` adapters and runtime assembly for the operational controller.
- Preserve downstream-first acceptance and simulation-thread mutation.
- Keep sticky service-centre ownership and active-line pharmacy affinity out of this integration slice.

Fixed implementation shape:

- Route-entry targets are bounded, simulation-owned, non-rendering FIFO queues of exact
  `OsrProcessingReleaseRequest` values.
- Candidate-specific live station admission is resolved on the simulation thread and captured in
  the immutable operational snapshot with the selected queue target ID.
- Target queue capacity is checked in the snapshot and revalidated during handler application.
- A production runtime composition wires fresh snapshots, scheduler evaluation, target registry,
  downstream-first command handling, and the operational controller.
- Third Party, Adapting, and P2P targets are covered; EMPTY, MANUAL, renderable hydration, sticky
  leases, and line affinity remain out of scope.

Verified implementation contracts:

- Bounded route-entry queues retain exact physical manifest identity and release time.
- Candidate-specific immutable admissions bind scheduler selection to the same live target queue
  used by downstream-first command application.
- Fresh operational snapshots observe queue mutation without worker access to mutable state.
- Production runtime composition supports supplied synchronous or threaded evaluation and closes
  its controller/evaluation source idempotently.
- Focused, full-suite, visual, and reset verification are green.

Follow-on planning target:

- `feature/dsp-osr-outbound-route-launch` replaces the earlier direct P2P queue-consumer proposal.
  It must establish common OSR outbound launch and hydration without bypassing warehouse transport.
- `feature/dsp-warehouse-transport-routing` then maps destination intent to physical paths and
  station-local arrival queues before P2P-local consumption or sticky leases are added.

### `feature/dsp-osr-outbound-route-launch`

Status: complete, verified, and merged.

Detailed implementation doc:

`docs/scheduler/dsp-osr-outbound-route-launch-plan.md`

Purpose:

- Feed all destination-specific operational targets into one globally ordered OSR outbound launch
  queue.
- Preserve target IDs as route destination intent without placing totes into station-local queues.
- Hydrate detached physical totes at the OSR outbound boundary into a bounded generic transport
  queue.
- Establish the correct boundary for later warehouse routing and station-arrival work.

Implemented contracts:

- One shared bounded launch FIFO preserves exact release order across Third Party, Adapting, and
  P2P destinations.
- Shared launch acceptance remains upstream of OSR departure and lifecycle activation.
- Detached hydration resolves an existing exact load plan and preserves physical identity across
  request, plan, tote, renderable, and route follower.
- A bounded generic transport FIFO accepts at most one hydrated head per simulation update.
- Full transport prevents hydration allocation; expected hydration failure leaves both queues
  unchanged for deterministic retry.
- No destination station queue, tipper, machine controller, or scene publication is touched.

Completed physical-routing feature:

- `feature/dsp-warehouse-transport-routing`, with detailed plan at
  `docs/scheduler/dsp-warehouse-transport-routing-plan.md`; P2P-local arrival consumption was
  implemented separately and is complete, verified, and merged, followed by sticky leases.

### `feature/dsp-warehouse-transport-routing`

Status: implementation complete, verified, and merged; focused regression and full test suites,
focused visual transport behavior, and deterministic reset reconstruction are green.

Detailed implementation doc:

`docs/scheduler/dsp-warehouse-transport-routing-plan.md`

Purpose:

- Publish detached outbound totes onto a common OSR outbound route through one simulation-owned
  ingress boundary.
- Map exact destination target IDs to route topology and destination-aware transfer decisions.
- Track physical totes in flight without exposing mutable route state to the scheduler worker.
- Stop and hand off arrived totes into bounded station-local arrival queues.
- Preserve physical travel between OSR and P2P; no destination may be populated directly by OSR
  release or hydration.

Implemented contracts:

- One common route-entry segment owns publication of all detached routed totes.
- Destination-aware transfer decisions use exact active target metadata and existing standalone
  transfer-machine mechanics.
- One in-flight registry owns exact routed payloads until terminal arrival succeeds.
- Terminal sensors are the only writers to bounded Third Party, Adapting, and P2P station-arrival
  queues; full queues retain held pending totes for deterministic retry.
- Immutable route, ingress, in-flight, arrival, and station queue snapshots support inspection and
  tests without exposing live topology to scheduler workers.
- The focused `dsp-warehouse-transport` scene proves mixed routing and visually separates a
  queue-owned P2P tote from a capacity-blocked terminal tote.

Follow-on sequence:

- `feature/dsp-p2p-arrival-consumer` is complete, verified, and merged. Its explicit local
  admission callback lets a closed P2P boundary defer without dequeuing the station arrival payload.
- `feature/dsp-p2p-sticky-line-leases` second, supplying authoritative service-centre admission and
  line selection through that boundary.
- Adapting and Third Party consumers can later use the same station-arrival contract.

### `feature/dsp-p2p-arrival-consumer`

Status: implementation complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-p2p-arrival-consumer-plan.md`

Purpose:

- Consume exact P2P station-arrival FIFO heads behind an immutable local admission callback.
- Adapt the same routed tote, renderable, and load plan into the existing `TipperInputQueue`.
- Preserve normal connected route movement into `ToteTrackTipperFlowController`.
- Compose independent per-target consumers without implementing line selection or sticky leases.

Implemented contracts:

- Local immutable admission and bounded tipper-input capacity are revalidated after physical
  station arrival without consuming directly from OSR or warehouse transport ingress.
- Source ownership changes only after exact target acceptance, preserving tote, renderable, load
  plan, route segment, distance, and direction through blocked retries.
- Existing `TipperInputQueueController` and connected route following remain the only path into
  tipper processing.
- Production contained-pack layout uses supplied tote geometry; visual-source registration remains
  lazy and separate from controller updates.
- Independent P2P consumer bindings expose immutable ordered snapshots and do not select lines or
  own service-centre leases.
- Focused/full tests and transport/tote-to-bag visual/reset checks are green.

### `feature/dsp-p2p-sticky-line-leases`

Status: implementation complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-p2p-sticky-line-leases-plan.md`

Purpose:

- Pin every physical tote requiring P2P to one exact line independently of its first route-entry
  station.
- Enforce optional sticky service-centre ownership at scheduling, command application, and physical
  P2P arrival boundaries.
- Prefer active-pharmacy affinity while retaining deterministic service-centre and source ranking.
- Publish complete immutable P2P activity snapshots and release leases only after owner work,
  processing, expected groups, bag output, and outbound tote state have drained.
- Preserve the existing OSR-to-route-to-transport-to-station-to-tipper ownership chain.

Follow-on boundary:

- Deadline-aware elastic allocation must reuse the sticky lease and quiescence contracts rather
  than weakening them.

Verified contracts:

- P2P-required physical totes are pinned once to an exact line before OSR departure, separately
  from their first route-entry destination.
- Lease selection is deterministic and prefers compatible owner/pharmacy affinity without allowing
  a different service centre onto an owned line.
- Command application commits leases and assignments; station arrival only revalidates them.
- Complete line activity, remaining owner work, open outbound output, and expected groups all block
  release. Output closure and lease release occur on separate simulation updates.
- Focused/full tests and warehouse transport/tote-to-bag visual/reset checks are green.

### `feature/dsp-deadline-aware-elastic-line-allocation`

Status: implementation complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-deadline-aware-elastic-line-allocation-plan.md`

Purpose:

- Add structured trunker deadlines independently of 12N `departureTime`.
- Estimate deterministic uncalibrated remaining work from physical totes, planned packs, and bags.
- Convert deadline/work demand into an ordered desired-line budget for at most two active service
  centres across the five P2P lines.
- Stop feeding surplus lines and release them only after every pinned tote and physical/output state
  has drained; never move committed assignments or pre-empt active work.
- Expose profile identity, workload, slack, required/desired/owned lines, and infeasibility for
  inspection and later full-day analysis.

Verified contracts:

- Configured timetable deadlines do not use 12N `departureTime`.
- Immutable normalized work determines required and desired counts for at most two active service
  centres across five lines; infeasibility remains observable.
- Exact feeding lines accept future assignments while draining surplus lines reject them.
- Pinned assignments never move, active work is not pre-empted, and output closes before a later
  quiescent lease release and reacquisition.
- Focused/full tests and warehouse transport/tote-to-bag visual/reset checks are green.

### `feature/dsp-av02-operational-allocation`

Status: complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-av02-operational-allocation-plan.md`

Purpose:

- Allocate inbound `PRE_P2P` physical totes only for authorized, dependency-ready logical EMPTY
  work through a bounded AV02 inventory.
- Rank OSR and AV02 physical candidates through one operational scheduler and preserve the existing
  elastic sticky P2P assignment contract.
- Replace downstream inbound-manifest assumptions with source-neutral physical route identity so
  AV02 and OSR share launch, hydration, transport, station-arrival, and P2P boundaries.
- Count allocated EMPTY physical work until P2P consumption while retaining unallocated EMPTY as an
  explicit workload diagnostic.
- Keep P2P-created outbound totes and generated output sheets independent and unchanged.

Verified implementation boundary:

- AV02 and OSR share one operational ranking, first-route launch, hydration, and transport path.
- Direct-P2P AV02 work reaches P2P lifecycle completion without a fabricated inbound manifest.
- Third Party-first and Adapting-first work reaches its production station-arrival boundary; onward
  station continuation is not faked by a test-only handoff.
- Focused regression, complete-suite, warehouse transport/tote-to-bag visual, and reset checks are
  green.

Follow-on status:

- `feature/dsp-station-route-continuation` is complete, verified, and merged to `master`;
- the operational EMPTY end-to-end proof is complete, verified, and merged to `master` at
  `afe40f5`;
- full-day execution, metrics, and inspection are active planned work on
  `feature/dsp-full-day-analysis-metrics-inspection`, using loaded 12N volumes and the explicitly
  uncalibrated profile.

### `feature/dsp-station-processing-boundary`

Status: complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-station-processing-boundary-plan.md`

Purpose:

- Establish one generic station-arrival FIFO, claim, real domain-processing completion, and
  disposition boundary for Third Party, Adapting, and P2P.
- Preserve exact physical identity, current load-plan identity, route state, and pinned P2P
  assignment through station ownership without turning the stations into one generic machine.
- Terminate Adapting STORE and P2P inbound journeys with `CONSUME`; preserve Third Party and
  Adapting COLLECT same-tote journeys as `CONTINUE` dispositions for later continuation.

Verified implementation boundary:

- Each claimant evaluates and accepts one exact station-arrival FIFO head before dequeuing that
  same routed object; deferred heads remain queued and unchanged.
- One simulation-thread-owned coordinator retains insertion-ordered active claims and FIFO
  immutable dispositions without entering worker snapshots.
- Third Party and Adapting use their existing area/bench controllers for timers and domain
  mutation. COLLECT dispositions retain the exact replacement load-plan instance; STORE and P2P
  emit `CONSUME` only after their lifecycle contracts succeed.
- P2P claims begin at tipper-input acceptance and complete only through the actual tipper
  completion callback. Consumed inbound totes are held/hidden and are not eligible as outbound
  totes.
- No route continuation, next-destination selection, transport republishing, Exception behavior,
  mutable reset, or outbound dispatch was added.

Follow-on status:

- `feature/dsp-station-route-continuation` is complete, verified, and merged to `master`;
- the operational EMPTY end-to-end proof is complete, verified, and merged to `master` at
  `afe40f5`;
- full-day execution, metrics, and inspection are active planned work on
  `feature/dsp-full-day-analysis-metrics-inspection`, using loaded 12N volumes and the explicitly
  uncalibrated profile.

### `feature/dsp-station-route-continuation`

Status: complete, verified, and merged to `master`.

Detailed implementation doc:

`docs/scheduler/dsp-station-route-continuation-plan.md`

Purpose:

- Complete generic same-tote handoff after Third Party and Adapting station processing.
- Terminally present and acknowledge `CONSUME` dispositions while routing supported `CONTINUE`
  dispositions through the existing source-neutral warehouse transport path.
- Preserve the exact source-neutral release request, current replacement load plan, physical tote,
  renderable, route follower, and pinned P2P assignment across every continued leg.

Verified implementation boundary:

- One simulation-thread-owned coordinator drains the global disposition FIFO without overtaking;
  exact downstream transport acceptance unlocks a `CONTINUE`, while `CONSUME` remains terminal and
  unclaimable after presentation.
- Pure route order selects only supported next destinations: Third Party to Adapting or P2P, and
  Adapting `COLLECT` to P2P. Adapting `STORE` and P2P consume inbound totes.
- Adapting uses the live area policy for exact bench selection; P2P uses only the committed exact
  assignment. Each continuation publishes a new immutable envelope around the same physical objects
  and re-enters through the existing common warehouse entry, ingress, in-flight, transfer, terminal
  sensor, and station-arrival boundaries.
- Initial publication occurs once; exact-object re-entry does not append duplicate world/renderable
  objects, and conflicting same-id physical objects fail without mutation. Scheduler worker state,
  P2P leases, lifecycle mutation, station domain ownership, and outbound allocation remain outside
  the continuation controller.

Explicit deferrals and follow-on:

- The operational EMPTY end-to-end proof is complete, verified, and merged to `master` at
  `afe40f5`.
- Full-day execution, metrics, and inspection are active planned work on
  `feature/dsp-full-day-analysis-metrics-inspection`.
- Station-to-station visual topology, outbound dispatch/32R, Exception handling, and MANUAL/
  MANUAL_MERGE handling remain deferred.

### `feature/dsp-operational-empty-end-to-end-proof`

Status: complete, verified, and merged to `master` at `afe40f5`.

Detailed implementation doc:

`docs/scheduler/dsp-operational-empty-end-to-end-proof-plan.md`

Implemented and verified contract:

- The production-boundary scenario proves authorized, dependency-ready logical EMPTY allocation
  through bounded AV02 `PRE_P2P` inventory with deterministic identity and no fabricated manifest.
- OSR and AV02 share one elastic operational ranking/release boundary, with at most one command per
  evaluation and one exact P2P assignment committed before departure.
- AV02 EMPTY work traverses real launch hydration, common warehouse transport, terminal station
  arrival, Third Party or Adapting processing, source-neutral continuation, pinned P2P arrival,
  and actual tipper completion.
- Third Party and Adapting COLLECT replace and propagate the exact immutable load plan while the
  release identity, physical tote, renderable, route follower, and assignment remain continuous.
- P2P completion consumes the inbound AV02 lifecycle exactly once; a separately allocated outbound
  tote joins through immutable bag/pack provenance and output-sheet allocation.
- The implementation adds only
  `app/src/test/java/online/davisfamily/warehouse/sim/dsp/av02/DspAv02OperationalAllocationScenarioTest.java`;
  no production code, existing test, debug scene, build, or dataset changes were made.

Verification and follow-on:

- Step 5 focused regression and complete-suite verification are green by user report, and the
  architecture review passed all contract items.
- Full-day execution, metrics, and inspection are active planned work on
  `feature/dsp-full-day-analysis-metrics-inspection`.
- Station-to-station visual topology, renderer integration, calibrated timing, outbound dispatch/
  32R, Exception, and MANUAL/MANUAL_MERGE behavior remain explicit deferrals.

### `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned and active; no implementation has started.

Detailed implementation doc:

`docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`

Planned contract:

- Load complete caller-supplied product-master and 12N datasets without eagerly creating physical
  or renderable objects.
- Execute bounded headless fixed steps from day 0 `06:00` to supported provisional completion or
  day +1 midnight through the existing supply, OSR/AV02 release, transport, station, P2P, and
  outbound owners.
- Preserve one planned bag correlation on one exact P2P line while allowing five long-lived lines
  to discover dynamically assigned work.
- Collect deterministic deadline, occupancy/net-flow, inbound/outbound rate, blocked-time,
  throughput, utilization, unfinished-work, and provisional completion metrics.
- Emit immutable JSON reports and text inspection that prominently identify
  `DEADLINE_AWARE_ELASTIC_STICKY_LEASES`, `UNCALIBRATED`, and the temporary
  `P2P_OUTPUT_CLOSED` completion milestone.

Explicit boundaries:

- Completion is analytical P2P output closure, not dispatch, 32R, stacking, loading, or a calibrated
  production prediction.
- Exception/NS behavior, MANUAL/MANUAL_MERGE, calibrated timing, renderer integration, visual
  station topology, event-driven fast-forward, and outbound dispatch remain deferred.

## Current Assumptions

- `master` is the integration base for scheduler branches.
- The OSR may contain physical totes from several authorized service centres. Sticky leases isolate
  those centres after warehouse transport delivers totes to P2P-local arrival queues.
- A P2P line must not accept another service centre until owner work is exhausted, the line is fully
  quiescent, and its current outbound tote is closed.
- Final dispatch orders/totes must be pharmacy-pure, but `pharmacyId` is line-level in 12N data.
- Adapted preparation orders may contain lines for multiple pharmacies.
- Logical order sheets use `orderId + sheetNumber`; physical inbound totes use 12N `transportContainer` and are represented separately.
- MANUAL messages and lines are excluded from active simulation and reported during ingestion.
- Prepared-line identity is target order id plus globally distinct line reference; `referenceSheetNumber` is protocol-only and not an identity discriminator.
- 12N line type owns order-specific processing intent. Product master owns Third Party bin location and physical dimensions.
- Loaded ADAPTED prepared-line data is not automatically ready. Adapted readiness should be added when an adapting bench processes STORE work, except for explicit already-staged startup fixtures.
- `FULL_PACK` orders never collect adapted lines.
- ADAPTED and FULL_PACK work may overlap, and ASSOCIATED/EMPTY work becomes eligible when its own preparation dependencies are terminal.
- Scheduler v1 is threaded in the integrated debug path, with synchronous fallback still available.
- Deeper scheduler behavior depends on Phase 1 station state, queue, and readiness surfaces.
- Detailed shelving/operative visuals, short picks, NS labels, Exception routing, empty NS bags, deadlock override timers, and command-button exception handling are split into later branches.
