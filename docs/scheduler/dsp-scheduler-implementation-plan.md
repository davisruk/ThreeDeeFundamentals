# DSP Scheduler Branch Roadmap

## Summary

This document is the scheduler programme roadmap. Detailed, step-by-step implementation instructions live in one plan document per feature branch so a weaker model can execute each branch without needing to reason across the whole scheduler programme.

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

Status: planned; detailed plan drafted.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-p2p-live-admission-plan.md`

Purpose:

- Add a simulation-thread snapshot adapter over the existing tote-to-bag local state.
- Avoid calling live `ToteToBagFlowController` state from a scheduler thread.
- Add an adapter from scheduler order/tote data to `ToteLoadPlan`.
- Feed live P2P admission into scheduler station admission snapshots.
- Add candidate-specific station admission resolution so P2P can evaluate the actual candidate tote instead of using one static station-wide answer.

### `feature/dsp-scheduler-json-loading`

Status: planned.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-json-loading-plan.md`

Purpose:

- Add product master and 12N loaders after sample schemas are provided.
- Keep product classification sourced from product master data.
- Revisit whether a database is useful after measuring in-memory loading and query shape.

### `feature/renderable-visibility-lifecycle`

Status: planned.

Detailed implementation doc:

- `docs/scheduler/renderable-visibility-lifecycle-plan.md`

Purpose:

- Add cheap visibility/skipping support to `RenderableObject`.
- Apply it to totes, contained packs, free packs, and bags.
- Ensure loaded order/pack data does not imply active renderable creation.

### `feature/dsp-scheduler-thread`

Status: planned.

Detailed implementation doc:

- `docs/scheduler/dsp-scheduler-thread-plan.md`

Purpose:

- Move scheduler evaluation to a separate thread only after synchronous snapshot/command integration is proven.
- Publish immutable `WarehouseSchedulerSnapshot`s to the scheduler thread.
- Publish `SchedulerCommand`s back to a simulation-thread command queue.
- Keep all live simulation mutations on the simulation thread.

## Current Assumptions

- `master` is the integration base for scheduler branches.
- Service centres must not be mixed during release.
- Final dispatch orders/totes must be pharmacy-pure, but `pharmacyId` is line-level in 12N data.
- Adapted preparation orders may contain lines for multiple pharmacies.
- Manual tote order examples are pharmacy-pure and should unlock target dispatch work through line readiness.
- Prepared-line readiness is keyed by target order id, target sheet, line id, and line type.
- A blocked active service centre blocks later service centres.
- Scheduler v1 is thread-ready but not threaded.
- JSON import, live visual injection, live P2P admission, database decisions, scheduler threading, deadlock override timers, and command-button/manual exception handling are split into later branches.
