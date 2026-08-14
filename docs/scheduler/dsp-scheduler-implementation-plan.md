# DSP Scheduler Branch Roadmap

## Summary

This document is the scheduler programme roadmap. Detailed, step-by-step implementation instructions live in one plan document per feature branch so a weaker model can execute each branch without needing to reason across the whole scheduler programme.

Current note: deeper scheduler behavior work is paused while Phase 1 station implementations are introduced. See `docs/machines/phase-1-stations-roadmap.md`. Generic transfer-machine support, adapting station Phase 1, and simulation reset are complete and merged. `feature/third-party-station-phase-1` is active.

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

Status: active.

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

## Current Assumptions

- `master` is the integration base for scheduler branches.
- Service centres must not be mixed during release.
- Final dispatch orders/totes must be pharmacy-pure, but `pharmacyId` is line-level in 12N data.
- Adapted preparation orders may contain lines for multiple pharmacies.
- MANUAL messages and lines are excluded from active simulation and reported during ingestion.
- Prepared-line identity is target order id plus globally distinct line reference; `referenceSheetNumber` is protocol-only and not an identity discriminator.
- 12N line type owns order-specific processing intent. Product master owns Third Party bin location and physical dimensions.
- Loaded ADAPTED prepared-line data is not automatically ready. Adapted readiness should be added when an adapting bench processes STORE work, except for explicit already-staged startup fixtures.
- `FULL_PACK` orders never collect adapted lines.
- A blocked active service centre blocks later service centres.
- Scheduler v1 is threaded in the integrated debug path, with synchronous fallback still available.
- Deeper scheduler behavior depends on Phase 1 station state, queue, and readiness surfaces.
- Detailed shelving/operative visuals, short picks, NS labels, Exception routing, empty NS bags, deadlock override timers, and command-button exception handling are split into later branches.
