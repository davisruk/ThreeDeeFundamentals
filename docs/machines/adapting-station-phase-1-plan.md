# Adapting Station Phase 1 Plan

Branch: `feature/adapting-station-phase-1`

Status: planned. `feature/inline-transfer-targets` is complete, and this plan assumes the standalone transfer-segment model delivered there.

## Purpose

Add the first Phase 1 merge/preparation station area. Phase 1 is state-complete and visually cheap:

- no rendered racks/bins
- no detailed pack transfer animation
- placeholder bench/station stop renderables only
- strong machine state, queue, logical inventory, and scheduler-facing readiness

The adapting area contains multiple adapting bench stations. A tote is routed to one bench based on available waiting/processing capacity. Each bench has two visit reasons:

- `STORE`: an `ADAPTED` preparation tote deposits prepared lines into logical station storage.
- `COLLECT`: an `ASSOCIATED` or `EMPTY` dispatch tote collects staged adapted lines before travelling onward.

`FULL_PACK` orders never collect adapted lines.

## Confirmed Requirements

- `ADAPTED` totes are transient preparation carriers and may contain lines for multiple pharmacies/stores.
- After a `STORE` visit, the source tote is removed/stored and can disappear in Phase 1. It does not continue through the route.
- Prepared adapted lines become scheduler-ready only after an adapting bench has processed the `STORE` visit.
- Loaded prepared-line data represents work that exists in the dataset, not automatically completed station work.
- If a fixture needs lines to be ready at startup, it must seed readiness explicitly as already-staged state.
- A `COLLECT` visit updates the collecting tote's load plan so the downstream P2P line can act on the newly added packs.
- `ASSOCIATED` and `EMPTY` orders may collect adapted lines.
- `FULL_PACK` orders do not collect adapted lines.
- The adapting area has multiple bench stations. Phase 1 can use a small fixed count in tests/fixtures, but the domain should not assume exactly one station.
- Scheduler release admission is area-level: a tote may be released to the adapting area only when at least one compatible bench has waiting or processing capacity.
- Bench selection should be deterministic when multiple benches can accept a tote. Prefer the store/pharmacy's nearest bench when known, then fall back deterministically.
- Store/pharmacy storage is logical in Phase 1, but it must be represented in the backend as `bench -> rack -> shelf -> bin` so later visual work can render storage placeholders and pack movement without changing station semantics.
- Phase 1 storage allocation should be simple and deterministic. Do not build an optimisation planner yet; create bins/shelves/racks dynamically when configured capacities are reached.

## Implementation Vocabulary

Use these names consistently unless the existing code strongly indicates a better local naming convention:

- `AdaptingArea`: owns multiple benches and exposes area-level admission/selection.
- `AdaptingBench`: one physical/manual bench station. This is the state machine that processes one visit at a time.
- `AdaptingBenchId`: stable bench identifier. Phase 1 may use a simple string or small value object; tests should use ids like `bench-1`, `bench-2`.
- `AdaptingVisit`: immutable request describing one tote/order visit to a bench.
- `AdaptingVisitType`: `STORE` or `COLLECT`.
- `AdaptingBenchSnapshot`: immutable state/capacity snapshot for one bench.
- `AdaptingAreaSnapshot`: immutable area snapshot containing bench snapshots and area-level admission result data.
- `AdaptingBenchSelection`: result of area admission, containing accepted/blocked plus selected `AdaptingBenchId` when accepted.
- `AdaptedLineStore`: shared logical storage for staged adapted lines. It is owned by the adapting area or an area controller, not by the scheduler worker.
- `AdaptingStorageLocation`: logical storage assignment for a staged line, including `AdaptingBenchId`, rack index/id, shelf index/id, bin index/id, and store/pharmacy id.
- `AdaptingStorageConfig`: simple capacity settings, such as lines per bin, bins per shelf, and shelves per rack.
- `AdaptingStorageMap`: maps store/pharmacy identifiers to their preferred `AdaptingBenchId`.
- `AdaptingStorageLayout`: owns the logical `bench -> rack -> shelf -> bin` hierarchy and dynamically creates bins/shelves/racks when capacity is reached.
- `StoredAdaptedLineRecord`: staged adapted line plus its `AdaptingStorageLocation`, if keeping location metadata outside `AdaptedLineRecord` is cleaner.

Naming rule:

- Do not introduce both `AdaptingStation` and `AdaptingBench` for the same concept. In this plan, `AdaptingBench` is the station machine. If existing code already has an `AdaptingStation` type before implementation starts, rename the plan consistently before coding.

Snapshot rule:

- Scheduler-visible snapshots must be immutable.
- Scheduler-visible snapshots must not expose mutable `AdaptedLineStore`, `MachineWaitQueue`, live totes, route followers, renderables, or controllers.

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

- A dispatch order with adapted dependencies remains blocked after JSON load until readiness is added by bench processing.
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

## Step 3: Add Bench State Machine

Add a Phase 1 adapting bench machine with explicit state.

Suggested states:

- `IDLE`
- `QUEUED`
- `PROCESSING_STORE`
- `PROCESSING_COLLECT`
- `COMPLETED`
- `BLOCKED`

Required classes:

- `AdaptingBench`
- `AdaptingBenchState`
- `AdaptingVisitType`
- `AdaptingVisit`
- `AdaptingBenchSnapshot`

Rules:

- The bench owns current visit state and processing timers.
- The bench does not mutate route follower state directly.
- The bench exposes an immutable snapshot for scheduler/debug use.
- `AdaptingVisitType.STORE` is only valid for adapted preparation work.
- `AdaptingVisitType.COLLECT` is only valid for collecting dispatch work.

Expected output:

- A `STORE` request moves through processing and stages lines.
- A `COLLECT` request moves through processing and returns collected lines.
- State transitions are deterministic and not based on arbitrary visual timing.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchTest
```

## Step 4: Add Bench Input Queues And Area Admission Snapshot

Use the existing `MachineWaitQueue` pattern for each adapting bench's waiting space.

Required classes:

- `AdaptingArea`
- `AdaptingBenchId`
- `AdaptingBenchAdmissionSnapshot`
- `AdaptingAreaAdmissionSnapshot`
- `AdaptingBenchSelection`
- or reuse `MachineWaitQueue` directly if a wrapper adds no value

Rules:

- Scheduler release admission means at least one compatible bench queue or processing slot has capacity.
- Bench selection is deterministic; if several benches can accept, choose the lowest bench id.
- Bench processing admission means a specific bench can start the next queued visit.
- Keep these two gates separate.
- `AdaptingArea.selectBenchFor(visit)` should return `AdaptingBenchSelection.accepted(benchId)` or a blocked result.
- Compatibility in Phase 1 means:
  - the bench can accept the visit type
  - the bench queue has capacity or the bench can start immediately
  - future compatibility constraints can be added without changing scheduler call sites

Expected output:

- Per-bench queue capacity can be represented in immutable snapshots.
- Area-level capacity can be represented without exposing mutable bench objects to scheduler code.
- A queued tote waits at its selected bench until that bench can process it.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaAdmissionTest
```

## Step 5: Add STORE Visit Processing

Implement the `ADAPTED` preparation path.

Rules:

- The incoming source tote carries prepared adapted lines.
- The selected `AdaptingBench` stages those lines in `AdaptedLineStore`.
- The runtime/scheduler prepared-line readiness is updated only after bench processing completes.
- The source tote is removed/stored/disappears for Phase 1.
- `AdaptingArea` or an area controller applies STORE completion to the shared store and publishes readiness; the scheduler worker must not mutate the store.

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
- `AdaptingArea` or an area controller applies COLLECT completion to the shared store and tote load plan; the scheduler worker must not mutate either object.

Expected output:

- An `ASSOCIATED` or `EMPTY` collecting tote receives staged adapted lines.
- P2P-facing `ToteLoadPlan` includes collected packs after bench processing.
- A `FULL_PACK` collect request is rejected or ignored by contract, with a test proving it cannot collect adapted lines.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest
```

## Step 7: Add Scheduler/Runtime Integration

Expose adapting readiness and multi-bench area capacity through the scheduler snapshot path.

Rules:

- Scheduler sees adapting area capacity as area admission.
- Scheduler decisions can identify the selected adapting bench target when the area has capacity.
- Scheduler sees adapted dependencies as ready only when bench processing has staged them.
- Scheduler should not mutate `AdaptedLineStore` directly. Simulation-thread station/controller code mutates live store state and then publishes immutable snapshots.
- Candidate evaluation should use the same deterministic selection contract as the runtime area:
  - filter compatible bench snapshots with capacity
  - sort by `AdaptingBenchId`
  - choose the first
- If the scheduler emits a release command for an adapting-bound tote, that command/result must carry the selected `AdaptingBenchId` so the simulation-thread controller can route the tote to the matching bench path.

Expected output:

- A collecting order is blocked before required adapted lines are staged.
- The same order becomes releasable after STORE completes and readiness is visible.
- An adapting-bound tote is blocked when every bench is full.
- An adapting-bound tote is assigned to a deterministic bench when multiple benches can accept.
- The selected bench id is visible in the scheduler/runtime result used by the simulation thread.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.adapting.*
```

## Step 8: Add Store-Aware Adapting Storage And Bench Selection

Add logical storage hierarchy and store affinity so STORE and COLLECT visits are routed to the bench nearest the store/pharmacy they service.

Required classes or equivalent:

- `AdaptingStorageLocation`
- `AdaptingStorageConfig`
- `AdaptingStorageMap`
- `AdaptingStorageLayout`
- `StoredAdaptedLineRecord` or `AdaptingStorageAssignment` if useful for returning selected storage metadata with staged records

Rules:

- `ADAPTED` totes may contain lines for multiple pharmacies/stores. Do not assume an adapted STORE visit is pharmacy-pure.
- The storage structure is logical in Phase 1 but should model `bench -> rack -> shelf -> bin`.
- Configure simple capacities:
  - lines per bin
  - bins per shelf
  - shelves per rack
- `STORE` processing should stage each adapted line into a logical bin derived from that line's pharmacy/store identifier.
- `COLLECT` processing should retrieve lines from the same logical bins/locations used by STORE.
- `AdaptingArea.selectBenchFor(visit)` should prefer the bench associated with the visit's relevant store/pharmacy when capacity allows.
- For `COLLECT`, the relevant store/pharmacy is the collecting order's store/pharmacy.
- For mixed `STORE` visits, derive a preferred bench by scoring the visit's lines by their storage locations:
  - count lines per preferred bench
  - choose the bench with the highest count when it has capacity
  - break ties by lowest `AdaptingBenchId`
  - if the preferred bench is full, fall back to the next compatible bench with capacity, ordered by score then bench id
- If a line has no explicit storage map entry, use a deterministic fallback location, preferably the lowest bench id with capacity.
- Do not implement complex placement optimisation in Phase 1.
- Allocation within a preferred bench is append-only/dynamic:
  - use the store/pharmacy's current open bin if it has line capacity
  - when a bin is full, create the next bin on the same shelf
  - when a shelf is full, create the next shelf on the same rack
  - when a rack is full, create the next rack for that bench
- Keep storage affinity inside the adapting area/domain. The scheduler should still ask for area admission and receive only selected target metadata such as `AdaptingBenchId`.
- Phase 1 does not need rendered bins or racks, but snapshots/debug output should expose enough logical storage information to validate routing and future visuals:
  - preferred bench for the visit
  - staged line count per bench/rack/shelf/bin
  - missing/unmapped store count if relevant

Expected output:

- A STORE visit containing lines for multiple pharmacies can stage records into multiple logical storage locations.
- A STORE visit is routed to the bench nearest the dominant/target storage location when that bench has capacity.
- A COLLECT visit is routed to the bench associated with its store/pharmacy when that bench has capacity.
- If the preferred bench is full, selection falls back deterministically without losing the original storage location metadata.
- Stored records retain enough location metadata for later visual rack/shelf/bin rendering.
- Bin, shelf, and rack creation is covered by focused tests using deliberately small capacities.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.*
```

## Step 9: Add Minimal Debug Layout Integration

Add placeholder adapting bench stations to the debug warehouse route.

Suggested Phase 1 routing shape:

```text
D--<-+-<---O
 ^ b | b ^
 | b v b |
-+-<-+->-+-
```

Legend:

- `O` is the origination side.
- `D` is the downstream destination side.
- `+` symbols are transfer machines/windows.
- `b` markers are adapting benches.
- Arrow characters show travel direction only.

Both STORE and COLLECT totes originate from `O` and may divert at the first main-line transfer into the adapting area. Inside the adapting area, an inline transfer routes the tote to the selected bench path. STORE totes terminate/disappear at the selected bench after processing. COLLECT totes stop at the selected bench, receive logical packs, continue along that bench path, and transfer back to the main line toward `D`.

The rig does not need to match the final warehouse layout, but it must prove that the scheduler/controller can direct totes to one of several adapting benches based on capacity and can return COLLECT totes to the main line without re-diverting them back into the adapting area.

Rules:

- Use the inline transfer-target work for adapting-area routing where needed.
- Add simple selectable bench renderables or existing simple markers.
- Fixture routing should map `AdaptingBenchId` to a concrete transfer target/path. Do not infer the path from list order in more than one place.
- Suggested fixture mapping:
  - `bench-1` routes to the upper bench path
  - `bench-2` routes to the lower bench path
- Use the store-aware storage map from Step 8 in the debug fixture so STORE and COLLECT totes naturally route to the bench associated with their store/pharmacy where possible.
- The first main-line transfer must divert only totes whose selected next visit is adapting STORE or adapting COLLECT.
- A COLLECT tote returning from a bench path to the main line must continue toward `D`; it must not be re-diverted into the adapting area.
- STORE tote removal happens at the selected bench after processing, not on the main line.
- Inspection should show:
  - bench id
  - bench state
  - queue count/capacity
  - staged adapted line count
  - active visit type
  - selected bench id on the adapting-area/scheduler debug surface when available
- No rendered racks/bins or pack transfer animation.

Expected output:

- A STORE tote can be routed to an available bench and disappear after processing.
- If one bench is full, a later tote can be routed to another available bench.
- A later COLLECT tote can arrive, receive logical packs, return to the main line, and continue toward P2P/`D`.
- A COLLECT tote that has returned to the main line is not reselected by the adapting diversion transfer.
- Visual validation can be done through motion plus inspection overlay.

Ask the user to run the focused adapting test first, then a visual run command agreed at that time.

## Completion Criteria

- Adapted line readiness is no longer implied by loading ADAPTED 12N messages.
- STORE processing stages adapted lines and makes them ready.
- STORE source totes disappear in Phase 1.
- COLLECT processing updates the collecting tote load plan for P2P and returns the tote to the main line.
- `FULL_PACK` never collects adapted lines.
- Area release admission, bench queue admission, and bench processing gates are separate.
- Multiple adapting benches are supported, with deterministic capacity-based bench selection.
- Visual presentation remains deliberately minimal.
