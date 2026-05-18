# Adapting Station Phase 1 Plan

Branch: `feature/adapting-station-phase-1`

Status: planned. `feature/inline-transfer-targets` is complete, and this plan assumes the standalone transfer-segment model delivered there.

## Purpose

Add the first Phase 1 merge/preparation station. Phase 1 is state-complete and visually cheap:

- no rendered racks/bins
- no detailed pack transfer animation
- placeholder station stop/renderable only
- strong machine state, queue, logical inventory, and scheduler-facing readiness

The adapting station has two visit reasons:

- `STORE`: an `ADAPTED` preparation tote deposits prepared lines into logical station storage.
- `COLLECT`: an `ASSOCIATED` or `EMPTY` dispatch tote collects staged adapted lines before travelling onward.

`FULL_PACK` orders never collect adapted lines.

## Confirmed Requirements

- `ADAPTED` totes are transient preparation carriers and may contain lines for multiple pharmacies/stores.
- After a `STORE` visit, the source tote is removed/stored and can disappear in Phase 1. It does not continue through the route.
- Prepared adapted lines become scheduler-ready only after the adapting station has processed the `STORE` visit.
- Loaded prepared-line data represents work that exists in the dataset, not automatically completed station work.
- If a fixture needs lines to be ready at startup, it must seed readiness explicitly as already-staged state.
- A `COLLECT` visit updates the collecting tote's load plan so the downstream P2P line can act on the newly added packs.
- `ASSOCIATED` and `EMPTY` orders may collect adapted lines.
- `FULL_PACK` orders do not collect adapted lines.

## Scope

Allowed production areas:

- new package under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/adapting`
- scheduler runtime readiness updates in `online.davisfamily.warehouse.sim.dsp.runtime`
- narrow updates to DSP IO/runtime factory if needed to separate "loaded prepared work" from "ready staged work"
- route/fixture integration needed for the Phase 1 debug layout

Allowed tests:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/adapting`
- narrow scheduler/runtime tests for prepared-line readiness
- route/fixture tests for adapting station integration

Do not change:

- P2P PRL/PCR/bagger logic
- scheduler service-centre window rules
- full scheduler prioritisation rules
- detailed station visuals

## Step 1: Correct Loaded Prepared Readiness Boundary

Separate loaded prepared-line work from prepared-line readiness.

Target behaviour:

- `LoadedDspData.preparedLines()` still contains prepared work from ADAPTED/MANUAL messages.
- Initial `WarehouseSchedulerSnapshot.preparedLineKeys()` should not automatically include adapted prepared lines merely because they were loaded.
- Startup-ready staged lines are allowed only through an explicit already-staged input path or test fixture helper.

Expected output:

- A dispatch order with adapted dependencies remains blocked after JSON load until readiness is added by station processing.
- Existing tests that assumed all loaded prepared lines were ready should be updated to assert loaded work separately from readiness.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.LoadedDspSchedulerRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 2: Add Adapting Domain Store

Create a logical station store for prepared adapted lines.

Suggested package:

`online.davisfamily.warehouse.sim.dsp.adapting`

Suggested classes:

- `AdaptedLineStore`
- `AdaptedLineStoreSnapshot`
- `AdaptedLineRecord`

Rules:

- Store records are keyed by existing `PreparedLineKey`.
- Store may contain multiple lines for different pharmacies/stores because `ADAPTED` preparation totes are mixed.
- Store supports:
  - `stage(line)`
  - `contains(key)`
  - `take(key)`
  - `takeAll(keys)`
  - snapshot counts

Expected output:

- Store can stage and retrieve prepared line records.
- Retrieval removes staged records.
- Missing line retrieval fails clearly or returns a structured missing result; choose one and test it.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreTest
```

## Step 3: Add Station State Machine

Add a Phase 1 adapting station machine with explicit state.

Suggested states:

- `IDLE`
- `QUEUED`
- `PROCESSING_STORE`
- `PROCESSING_COLLECT`
- `COMPLETED`
- `BLOCKED`

Suggested classes:

- `AdaptingStation`
- `AdaptingStationState`
- `AdaptingVisitType`
- `AdaptingStationSnapshot`

Rules:

- The station owns current visit state and processing timers.
- The station does not mutate route follower state directly.
- The station exposes a snapshot for scheduler/debug use.

Expected output:

- A `STORE` request moves through processing and stages lines.
- A `COLLECT` request moves through processing and returns collected lines.
- State transitions are deterministic and not based on arbitrary visual timing.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationTest
```

## Step 4: Add Input Queue And Admission Snapshot

Use the existing `MachineWaitQueue` pattern for adapting station waiting space.

Suggested classes:

- `AdaptingStationQueue`
- `AdaptingStationAdmissionSnapshot`
- or reuse `MachineWaitQueue` directly if a wrapper adds no value

Rules:

- Scheduler release admission means queue capacity exists.
- Station processing admission means the station can start the next queued visit.
- Keep these two gates separate.

Expected output:

- Queue capacity can be represented in immutable snapshots.
- A queued tote waits until the station can process it.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationQueueTest
```

## Step 5: Add STORE Visit Processing

Implement the `ADAPTED` preparation path.

Rules:

- The incoming source tote carries prepared adapted lines.
- The station stages those lines in `AdaptedLineStore`.
- The runtime/scheduler prepared-line readiness is updated only after station processing completes.
- The source tote is removed/stored/disappears for Phase 1.

Expected output:

- Before STORE processing, target dispatch dependencies remain blocked.
- After STORE processing completes, corresponding adapted `PreparedLineKey`s are present in scheduler readiness.
- The source tote no longer continues through the route.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStoreFlowTest
```

## Step 6: Add COLLECT Visit Processing

Implement the dispatch collection path for `ASSOCIATED` and `EMPTY`.

Rules:

- Determine required adapted lines from the collecting order/load plan.
- Retrieve matching staged records from `AdaptedLineStore`.
- Update the collecting tote load plan so P2P sees the collected packs.
- Do not support `FULL_PACK` collection.

Expected output:

- An `ASSOCIATED` or `EMPTY` collecting tote receives staged adapted lines.
- P2P-facing `ToteLoadPlan` includes collected packs after station processing.
- A `FULL_PACK` collect request is rejected or ignored by contract, with a test proving it cannot collect adapted lines.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest
```

## Step 7: Add Scheduler/Runtime Integration

Expose adapting readiness and queue capacity through the scheduler snapshot path.

Rules:

- Scheduler sees adapting queue capacity as station admission.
- Scheduler sees adapted dependencies as ready only when station processing has staged them.
- Scheduler should not mutate `AdaptedLineStore` directly. Simulation-thread station/controller code mutates live store state and then publishes immutable snapshots.

Expected output:

- A collecting order is blocked before required adapted lines are staged.
- The same order becomes releasable after STORE completes and readiness is visible.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.adapting.*
```

## Step 8: Add Minimal Debug Layout Integration

Add a placeholder adapting station to the debug warehouse route.

Rules:

- Use the inline transfer-target work for adapting-area routing where needed.
- Add one simple selectable station renderable or use an existing simple marker.
- Inspection should show:
  - station state
  - queue count/capacity
  - staged adapted line count
  - active visit type
- No rendered racks/bins or pack transfer animation.

Expected output:

- A STORE tote can arrive and disappear after processing.
- A later COLLECT tote can arrive, receive logical packs, and continue toward P2P.
- Visual validation can be done through motion plus inspection overlay.

Ask the user to run the focused adapting test first, then a visual run command agreed at that time.

## Completion Criteria

- Adapted line readiness is no longer implied by loading ADAPTED 12N messages.
- STORE processing stages adapted lines and makes them ready.
- STORE source totes disappear in Phase 1.
- COLLECT processing updates the collecting tote load plan for P2P.
- `FULL_PACK` never collects adapted lines.
- Station queue and station processing gates are separate.
- Visual presentation remains deliberately minimal.
