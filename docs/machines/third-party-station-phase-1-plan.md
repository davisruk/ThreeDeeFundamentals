# Third Party Area Phase 1 Plan

Branch: `feature/third-party-station-phase-1`

Status: planned. Execute one step at a time and ask the user to run the focused Gradle command after each step.

## Purpose

Implement the Phase 1 Third Party Area and the data-model corrections required to support it.

This branch must deliver:

- independent CSV product-master loading and JSON 12N loading;
- correct line correlation using target order id plus line reference;
- explicit MANUAL-data exclusion and reporting;
- line-aware Third Party work selection;
- a capacity-aware logical Third Party Area;
- successful picks that update the tote load plan exactly once;
- scheduler admission and a minimal selectable through-track debug scene.

Phase 1 is state-complete and visually cheap. Do not implement detailed shelving, operatives, supplier replenishment, short picks, NS labels, Exception routing, or empty NS bags.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/machines/third-party-station-requirements.md`
3. `docs/scheduler/dsp_osr_scheduler_requirements.md`
4. `docs/machines/adapting-station-phase-1-plan.md`
5. `docs/scheduler/machine-wait-queues-plan.md`

Use these layout references for context, not pixel-perfect Phase 1 geometry:

- `docs/layout/warehouse_layout.png`
- `docs/layout/warehouse_layout_with_comments.png`

## Fixed Decisions

Do not revisit these during implementation:

- Use `FULL_PACK`, not `FULL`.
- Product master and 12N are independent inputs.
- Load `app/md/product_automation.csv` into an in-memory repository; do not add a database.
- Join 12N `productId` to CSV `dispensingProductPackColumbusCode` as strings.
- A nonblank CSV `thirdPartyLocation` means Third Party and the location is retained.
- Convert positive CSV dimensions from millimetres to metres; treat an exact `0 x 0 x 0` triple as missing dimensions.
- 12N line type, not product master, owns order-specific FULL_PACK/ADAPTED/MANUAL processing.
- MANUAL messages and lines are excluded from active simulation and reported.
- Prepared-line identity is target order id plus globally distinct line reference.
- Retain `referenceSheetNumber` for protocol fidelity, but never use it in identity/readiness matching.
- Header `sheetNumber` remains the sheet-sequencing value.
- ASSOCIATED/EMPTY/FULL_PACK orders are pharmacy-pure; ADAPTED orders may be mixed-pharmacy.
- AV02 creates physical totes for EMPTY orders; Third Party does not create totes.
- Third Party stock is unlimited.
- Use conservative release: a fulfilment tote remains upstream until every adapted dependency has a terminal outcome and every required station admission is open.
- Phase 1 picks always succeed.
- Missing product records are reported and retained as unresolved data, but full runtime handling waits for Exception Station Phase 1.
- The Third Party Area is one logical area with configurable waiting and concurrent processing capacity. Do not model adapting-style benches.
- Scheduler workers read immutable snapshots and never mutate the live area, tote load plans, route followers, or renderables.

## Deferred Contracts

The code should leave narrow extension points for these agreed future rules, but must not implement them now:

- line outcomes `COMPLETE` and `INCOMPLETE`;
- deterministic short-pick injection;
- NS fulfilment metadata carried into bags;
- FULL_PACK exception bypass when at least one line is fulfilled;
- all-incomplete empty NS bags;
- missing-master and short-pick routing to `StationType.EXCEPTION`;
- detailed issue resolution/operator controls.

Do not add `FAILED` order semantics for routine incomplete lines. An incomplete line is a terminal fulfilment outcome and can resolve an ASSOCIATED dependency without adding a physical pack.

## Implementation Vocabulary

Use these names unless an existing local type makes one unnecessary:

- `ProductMasterCsvLoader`: maps the source CSV into domain product records.
- `ProductMasterCsvRow`: package-private source DTO matching CSV headers.
- `TwelveNDatasetLoader`: loads/maps only 12N JSON messages.
- `LoadedTwelveNData`: mapped simulated orders/preparation work plus an ingestion report.
- `DspLoadReport`: immutable counts/details for ignored manual data and unresolved products.
- `DspDatasetAssembler`: combines already-loaded product master and 12N data without reading files.
- `ThirdPartyWorkType`: `DIRECT_FULFILMENT` or `ADAPTED_PREPARATION`.
- `ThirdPartyLineWork`: immutable qualifying line, outstanding quantity, product id, bin location, and work type.
- `ThirdPartyVisit`: immutable tote/order visit containing one or more line-work records.
- `ThirdPartyVisitFactory`: applies the fixed line-selection matrix.
- `ThirdPartyAreaConfig`: queue capacity, maximum concurrent visits, and processing duration.
- `ThirdPartyArea`: owns waiting/processing visit state and completion production.
- `ThirdPartyVisitState`: `WAITING`, `PROCESSING`, or `COMPLETED` where needed in snapshots.
- `ThirdPartyAreaSnapshot`: immutable scheduler/debug state.
- `ThirdPartyCompletion`: one completed visit, emitted exactly once.
- `ThirdPartyPackPlanFactory`: injected conversion from line work/product metadata to Phase 1 `PackPlan`s.
- `ThirdPartyAreaController`: applies completions to mutable tote load plans on the simulation thread.
- `ThirdPartyStationAdmissionAdapter` / `ThirdPartyStationAdmissionResolver`: candidate-aware scheduler admission following existing P2P/adapting patterns.
- `ThirdPartyAreaStopController`: fixture controller that holds, processes, and releases a tote at its assigned physical stop.
- `ThirdPartyDebugRig`: isolated Phase 1 visual/integration rig.

Naming rule:

- Use `ThirdPartyArea` for the operational facility.
- Do not introduce `ThirdPartyBench`; the real area has shelving beside a through-track, not pick benches.

## Scope

Expected production areas:

- `online.davisfamily.warehouse.sim.dsp.model`
- `online.davisfamily.warehouse.sim.dsp.io`
- `online.davisfamily.warehouse.sim.dsp.routing`
- `online.davisfamily.warehouse.sim.dsp.scheduler`
- `online.davisfamily.warehouse.sim.dsp.runtime`
- new `online.davisfamily.warehouse.sim.dsp.thirdparty`
- narrow tote-load-plan additions under `online.davisfamily.warehouse.sim.totebag.plan`
- debug integration under `online.davisfamily.warehouse.testing`

Expected test areas mirror those packages.

Do not change:

- PRL/PCR/bagger state logic;
- service-centre priority/window policy;
- threaded scheduler evaluation architecture;
- adapting storage hierarchy or bench-selection policy except mechanical key migration;
- transfer-machine architecture;
- detailed render geometry.

## Step 1: Correct Line Vocabulary And Prepared-Line Identity

Correct the existing domain before adding Third Party state.

Required changes:

- Rename `DspOrderItem.itemId` to `lineReference`.
- Update call sites mechanically; do not retain two competing identifiers.
- Change `PreparedLineKey` components to exactly:
  - `String targetOrderId`
  - `String lineReference`
- `PreparedLineKey.forPreparedLine(line)` uses `line.referenceOrderId()` and `line.lineReference()`.
- `PreparedLineKey.forDispatchLine(order, line)` uses `order.orderId()` and `line.lineReference()`.
- Remove target sheet number and line type from key equality.
- Keep `DspOrderItem.referenceSheetNumber` unchanged and loaded, but do not use it for matching.
- Keep `NotionalToteOrder.sheetNumber` unchanged for scheduler sequencing.
- Migrate adapting stores, visits, snapshots, fixtures, and tests to the corrected key without behavior changes.

Required tests:

- preparation and dispatch lines with the same line reference and target order create equal keys even when header/reference sheet values differ;
- two different line references remain distinct;
- two target order ids remain distinct;
- adapting STORE/COLLECT behavior remains green after mechanical migration.

Do not add COMPLETE/INCOMPLETE outcomes in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerStateTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest --tests online.davisfamily.warehouse.sim.dsp.adapting.*
```

## Step 2: Replace Synthetic Product Classification With CSV Master Data

Add a dedicated CSV loader and change the product domain to match the real source.

Dependency decision:

- Add `com.fasterxml.jackson.dataformat:jackson-dataformat-csv` using the existing Jackson version in `gradle/libs.versions.toml`.
- Use Jackson CSV parsing with a header schema. Do not split CSV lines manually; product names contain punctuation and must be parsed structurally.

Change `ProductMasterRecord` to contain:

- trimmed Columbus product id;
- display name;
- optional normalized Third Party bin location;
- optional `PackDimensions` in metres.

Provide `boolean thirdParty()` as a derived convenience method if useful. Do not retain `ProductCategory` as routing authority. Remove `ProductCategory` once all production/test references have migrated.

`ProductMasterCsvLoader` rules:

- map `dispensingProductPackColumbusCode` to product id without numeric conversion;
- preserve leading zeroes in string fields;
- trim names and locations;
- map blank `thirdPartyLocation` to empty optional;
- parse positive `length`, `width`, and `height` millimetres and divide by 1000;
- map an exact `0 x 0 x 0` triple to missing dimensions so the product remains available for routing/identity lookup;
- reject blank/duplicate product ids, partially missing dimension triples, and other invalid/nonpositive dimensions clearly;
- ignore unused source columns after structured parsing;
- support `load(Path)` and `loadString(String)` for focused tests.

Keep `InMemoryProductMasterRepository` as the runtime repository and verify all 5,498 supplied records load with 78 Third Party locations and 8 missing-dimension records. Do not use the full file as the only parser test; include small strings for edge cases.

Remove `ProductMasterJsonLoader` and `ProductMasterJsonRecord` only after all tests/callers have migrated. Do not leave JSON product master as a second source of truth.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.ProductMasterCsvLoaderTest --tests online.davisfamily.warehouse.sim.dsp.routing.*
```

## Step 3: Separate 12N Loading And Apply Manual Exclusion

Replace `DspJsonDatasetLoader` with explicit composition.

Required boundaries:

- `ProductMasterCsvLoader` reads only product master.
- `TwelveNDatasetLoader` reads only 12N JSON and does not receive product records.
- `DspDatasetAssembler` combines mapped data and reports unresolved product ids; it does not perform file IO.
- `LoadedDspData` may remain the combined result, but rename `dispatchOrders` to `orders` because retained ADAPTED preparation orders are schedulable physical tote flows.

12N mapping rules:

- Map `ADAPTED_PREPARATION` messages into `NotionalToteOrder` with `OrderType.ADAPTED`, while still exposing their prepared lines for adapting storage processing.
- Continue mapping EMPTY, ASSOCIATED, and FULL_PACK messages as orders.
- Ignore MANUAL_PREPARATION messages and increment report counts.
- Remove `DspOrderLineType.MANUAL` lines from retained orders.
- Omit an order if no lines remain and report it.
- Validate pharmacy purity after filtering.
- Preserve source sequence order among retained orders.

Sheet sequencing correction:

- In `DspDependencyEvaluator`, find the greatest retained sheet number lower than the candidate for the same notional/order grouping.
- Require that retained predecessor to be RELEASED or COMPLETED.
- If no lower retained sheet exists, do not block merely because the candidate header sheet is greater than one.
- Do not renumber source sheets.

Missing product rules:

- `DspDatasetAssembler` reports each unresolved product/line reference and retains the order data.
- Do not silently classify an unresolved product as non-Third-Party.
- `LoadedDspSchedulerRuntimeFactory` may fail clearly when asked to construct a runnable state containing unresolved products until Exception support exists; the load/assembly operation itself must still succeed and expose the report.

Delete `DspJsonDatasetLoader` after callers/tests migrate. Keep `JsonLoaderSupport` for 12N JSON.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 4: Add Line-Aware Third Party Visit And Route Derivation

Create the pure Third Party work-selection domain before the live area.

Add:

- `ThirdPartyWorkType`
- `ThirdPartyLineWork`
- `ThirdPartyVisit`
- `ThirdPartyVisitFactory`

Outstanding quantity is `line.quantity() - line.numberOfPacksPicked()`. Skip lines with no outstanding quantity.

Selection matrix:

- For `OrderType.ADAPTED`, select outstanding lines whose products have a Third Party bin; classify them as `ADAPTED_PREPARATION`.
- For `FULL_PACK`, `ASSOCIATED`, and `EMPTY`, select only outstanding `DspOrderLineType.FULL_PACK` lines whose products have a Third Party bin; classify them as `DIRECT_FULFILMENT`.
- Never select ADAPTED lines from ASSOCIATED/EMPTY for Third Party; those are collected from Adapting.
- MANUAL lines should already be absent; reject one clearly if a direct factory caller supplies it.
- Return no visit when no qualifying work exists.
- Retain line reference, product id, outstanding quantity, bin location, and work type in each line-work record.

Update `DspRouteDeriver` to use order/line lifecycle rather than `ProductCategory`:

- `requiresThirdParty` iff `ThirdPartyVisitFactory` finds qualifying work;
- the existing `requiresSortable` field represents Adapting in current code and is true for ADAPTED source orders or fulfilment orders containing ADAPTED lines;
- `requiresManual` and `requiresManualMerge` are always false for loaded active data;
- `requiresP2p` remains true for FULL_PACK, ASSOCIATED, and EMPTY and false for ADAPTED;
- start location remains AV02 only for EMPTY.

Do not rename `RouteRequirements.requiresSortable` in this branch unless compilation forces a mechanical rename. The semantic cleanup can be a later focused branch.

Required mixed-order test:

- one ASSOCIATED order contains a direct Third Party FULL_PACK line and an ADAPTED line whose product also has a Third Party bin;
- its visit contains only the direct line;
- its route requires both Third Party and Adapting;
- conservative adapted dependency evaluation still blocks release until the ADAPTED line is ready.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactoryTest --tests online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriverTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 5: Add Third Party Area State, Queue, And Concurrent Capacity

Implement the live logical area without route/render integration.

Add:

- `ThirdPartyAreaConfig`
- `ThirdPartyArea`
- `ThirdPartyVisitState` if needed by snapshots
- `ThirdPartyAreaSnapshot`
- `ThirdPartyCompletion`

Rules:

- Configuration values are positive/nonnegative as appropriate and validated.
- Waiting order is FIFO and deterministic.
- The area accepts a visit only when waiting plus immediately available processing capacity permits it.
- At most `maxConcurrentVisits` process simultaneously.
- Processing duration is deterministic in Phase 1.
- `update(dtSeconds)` starts queued work when slots open and advances active visits.
- Reject negative delta.
- A completed visit leaves active capacity and is emitted once through `drainCompletions()` or equivalent.
- The snapshot is immutable and exposes configured capacity, waiting tote/order ids, active visit summaries, and counts.
- The area does not mutate tote load plans, scheduler runtime state, route followers, or renderables.

Use `MachineWaitQueue` where it fits, but do not force it if the area needs an internal FIFO of complete `ThirdPartyVisit` values. If using both, maintain one authoritative ordering and test it.

Expected tests:

- queue limit blocks excess admission;
- two visits process concurrently when configured for two;
- FIFO waiting visit starts when a slot opens;
- completions are emitted exactly once;
- immutable snapshots do not expose mutable collections.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaTest
```

## Step 6: Apply Successful Picks To Tote Load Plans

Add simulation-thread completion application.

Required changes:

- Allow a `ToteLoadPlan` to contain zero pack plans while a third-party-only EMPTY tote is travelling to its first pick.
- Keep downstream P2P admission responsible for rejecting/holding an empty plan that has not completed required picks.
- Add an immutable helper such as `withAdditionalPackPlans(...)` that returns a new plan, rejects duplicate pack ids, and preserves existing order.
- Continue replacing plans through `MutableToteLoadPlanRegistry`; do not expose mutable pack-plan lists.

Add:

- `ThirdPartyPackPlanFactory`
- `ThirdPartyAreaController`

The controller:

- updates the live area;
- drains each completion once;
- creates one `PackPlan` per outstanding pack quantity through the injected factory;
- replaces the tote's plan with the augmented immutable plan;
- records completed line references so a visit cannot be reapplied;
- exposes completion state for route/debug integration.

Phase 1 pack correlation decision:

- Keep correlation derivation behind `ThirdPartyPackPlanFactory`.
- Focused tests inject a deterministic factory.
- The debug fixture may use order id as a temporary bag correlation and line reference plus pack ordinal as pack id.
- Do not declare that fixture correlation to be the final patient/prescription bagging rule.

Use product-master dimensions in created pack plans. Retain bin location in completion/debug state, but do not decrement stock.

Required tests:

- direct Third Party completion adds packs to an existing fulfilment plan;
- third-party-only EMPTY starts with an empty plan and receives packs;
- ADAPTED preparation completion adds packs to its source plan and does not publish adapted readiness;
- repeated completion application cannot duplicate packs;
- downstream can observe the replaced plan through the existing provider interface.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyPickFlowTest --tests online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanTest
```

## Step 7: Add Scheduler Admission Integration

Expose live area capacity through the existing snapshot/admission architecture.

Add:

- `ThirdPartyStationAdmissionAdapter`
- `ThirdPartyStationAdmissionResolver`

Follow the established P2P and Adapting resolver composition:

- use static snapshot fallback for unrelated station types;
- for `StationType.THIRD_PARTY`, evaluate candidate-specific visit work against the live immutable area snapshot/admission adapter;
- return open when the candidate has no Third Party visit;
- return blocked with a stable reason when area waiting/processing capacity is exhausted;
- do not enqueue or mutate the live area during scheduler evaluation;
- do not select a bench/target id because the area is one logical destination.

Preserve evaluation order:

- dependency blocks are collected before release is accepted;
- an ASSOCIATED/EMPTY order with unresolved ADAPTED lines remains blocked even if Third Party has capacity;
- once dependencies are ready, Third Party admission participates with Adapting/P2P admission under the existing conservative all-required-stations policy.

Required tests:

- qualifying order is blocked when Third Party is full;
- non-Third-Party order is unaffected;
- mixed ASSOCIATED order remains dependency-blocked before adapted readiness;
- the same order becomes a release candidate after readiness when all station admissions are open;
- scheduler evaluation does not change area state.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartySchedulerIntegrationTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseSchedulerTest
```

## Step 8: Add Route Stop And Simulation Controller Integration

Connect released totes to a physical through-track stop without detailed visuals.

Add `ThirdPartyAreaStopController` or equivalent under the testing/runtime integration package.

Rules:

- Use an explicit route sensor/hold distance, not track-length timing guesses.
- A tote with an outstanding Third Party visit is held at an available logical processing position.
- A tote with no outstanding Third Party visit passes without stopping.
- A held tote is enqueued/applied to the live area exactly once.
- Completion releases the same tote along its existing route.
- ADAPTED completion routes onward toward the Adapting boundary.
- FULL_PACK/ASSOCIATED/EMPTY completion routes onward toward their remaining fulfilment path and eventual P2P.
- Completed visits cannot retrigger when the tote crosses later sensors.
- For a Phase 1 visual route with fewer physical stop markers than logical concurrent slots, keep the visual rig capacity consistent with its stop count. Domain tests may exercise larger logical capacity.

Do not add transfer-machine changes. Build the isolated route from existing route segments, sensors, and controllers.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.ThirdPartyAreaStopControllerTest
```

## Step 9: Add Minimal Third Party Debug Rig And Inspection

Add `DebugSceneKind.THIRD_PARTY` with CLI value `third-party` and install `ThirdPartyDebugRig` from `TestScene`.

Rig shape:

```text
source -> waiting -> Third Party Area stop(s) -> downstream sink
                         [shelving placeholder]
```

The rig is an isolated proof, not the final full warehouse layout.

Fixture scenarios must include:

1. ADAPTED source tote with a Third Party product: it stops, receives its pack, and continues toward an Adapting-labelled sink/boundary.
2. ASSOCIATED fulfilment tote with one direct Third Party FULL_PACK line plus one already-ready ADAPTED dependency: it stops, receives only the direct pack, and continues toward P2P.
3. A tote with no qualifying Third Party work: it passes without processing.
4. Capacity behavior: at least one later tote waits while configured area slots are occupied.

Use known product ids/bin locations from small fixture records or the supplied master file. Do not use unresolved product `35310` as a successful fixture.

Presentation:

- simple through-track;
- one selectable area root/placeholder;
- optional plain shelving blocks for orientation;
- no rendered individual bins or operatives;
- no animated pack transfer.

Inspection must show:

- area state/capacity;
- waiting tote ids;
- active visits;
- order/tote id;
- work type;
- line references, product ids, and bin locations;
- last completion;
- scheduler release/debug state where practical.

`ThirdPartyDebugRig` must implement the existing `DebugSceneRuntime` lifecycle. If it owns a scheduled injector/evaluation source, retain and close it exactly as the reset lifecycle requires.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.ThirdPartyDebugRigTest --tests online.davisfamily.warehouse.testing.scheduler.*
```

Then ask for a visual run:

```powershell
.\gradlew run --args="--scene=third-party"
```

Visual acceptance:

- qualifying totes visibly stop and later continue;
- a nonqualifying tote does not stop;
- waiting/active capacity is understandable through inspection;
- direct packs appear in the fulfilment tote load/inspection state;
- ADAPTED work is shown as continuing toward Adapting;
- `ALT+R` cleanly restarts the rig without duplicated objects or behavior.

## Step 10: Regression And Completion

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.* --tests online.davisfamily.warehouse.sim.dsp.routing.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.testing.ThirdParty* --tests online.davisfamily.warehouse.sim.totebag.plan.*
```

Then ask the user to run the trusted complete simulation suite and perform the final visual checks.

Before branch closure:

- update this plan status to complete and verified;
- update `docs/codex-context.md` and `docs/codex-instructions.md`;
- update the roadmap and scheduler branch roadmap;
- record any deliberate deferrals discovered during implementation;
- do not implement Exception behavior merely to make the historical full dataset runnable.

## Completion Criteria

- The supplied CSV is the sole product-master source and loads into an in-memory repository.
- 12N JSON loading is independent from product-master file IO.
- MANUAL data is excluded and reported without breaking retained sheet sequencing.
- ADAPTED preparation orders remain grouped physical tote flows.
- Prepared-line identity is target order id plus line reference.
- Third Party routing follows the fixed line-level matrix.
- The logical area supports waiting and configurable concurrent processing.
- Scheduler admission is immutable and candidate-specific.
- Successful picks update tote load plans exactly once using real master dimensions.
- ADAPTED source totes continue toward Adapting; fulfilment totes continue toward P2P.
- Conservative dependency release remains unchanged.
- Minimal debug presentation and inspection prove the behavior.
- Reset, focused tests, complete simulation tests, and visual checks are green.
- Short picks, incomplete outcomes, NS labels, Exception routing, stock tracking, and detailed visuals remain deferred and documented.
