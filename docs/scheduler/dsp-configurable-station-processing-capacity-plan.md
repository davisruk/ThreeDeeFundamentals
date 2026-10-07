# Configurable Adapting and Third Party Processing Plan

Status: planned; no implementation authorized by this document. The user wants to
discuss P2P timings before initiating implementation. Each step requires separate user
initiation. Execute directly, without subagents unless explicitly requested for that step.

## Purpose and agreed operational assumptions

Correct and configure the full-day station model, rather than introduce an artificial
fast-test time scale. The user's initial settings are:

| Setting | Initial configured value |
|---|---|
| Third Party duration | 20 simulated seconds per visit |
| ADAPTED STORE duration | 60 simulated seconds per tote |
| ASSOCIATED/EMPTY COLLECT duration | 10 simulated seconds per tote |
| Physical Adapting benches | Six, named `bench-1` through `bench-6` |
| Independent processing positions | Three per physical bench |
| Waiting capacity | Three totes per physical bench, shared across its positions |

This gives 18 active processing positions and 18 waiting spaces, not 18 physical
benches and not three waiting spaces per position. STORE and COLLECT share positions
and the bench FIFO. Third Party concurrency remains one and waiting capacity remains
16; only its processing duration is exposed by this slice.

STORE duration includes identification, printing/applying labels, and distributing all
packs from the source tote into the correct blue bins. That work normally happens
locally before the bins are placed in storage. Do not add label printers, operator
entities, per-pack movement, local-bin transfer animation, or storage travel timers.
COLLECT duration includes locating the order's bins and emptying them wholly into the
collecting tote. Later sheets still visit Adapting and use the same COLLECT duration,
even when that order's bins were already drained by its designated first sheet.

These durations are estimates, not calibrated production measurements. Keep
`UNCALIBRATED`, the existing scheduler policy IDs, and provisional output-closure
milestones. No particular wall-clock reduction or deadline outcome is promised.

## Required reading and repository gate

Follow `AGENTS.md` and `docs/codex-instructions.md` in their mandatory order. Then read
this entire plan, the Adapting and Third Party station requirements, and these relevant
contracts:

- `docs/scheduler/dsp-order-owned-adapting-exception-flow-plan.md`, shared contracts
  and all of Step 2, especially prepare-before-drain and committed first-COLLECT;
- `docs/scheduler/dsp-station-processing-boundary-plan.md`, Adapting claim/completion
  contracts and the one-completion-per-update rule;
- `docs/scheduler/dsp-station-route-continuation-plan.md`, exact destination,
  replacement-plan identity, and global disposition FIFO;
- `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`, timing/profile,
  runtime architecture and report contracts;
- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`, Adapting order-owned
  bins and incoming/outgoing sheet distinction;
- `docs/scheduler/dsp-operational-scheduling-requirements.md`, order-wide readiness
  and designated-first-sheet release gating.

Inspect the exact production/test classes named for the selected step before editing.
Package abbreviations below are relative to
`app/src/main/java/online/davisfamily/warehouse/sim/dsp/`; tests use the corresponding
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/` package unless stated otherwise.

Record `git status --short`, preserve prior changes, and use `apply_patch` for every
edit. The planning worktree already contains the uncommitted collected-pack dimensions
fix and earlier PDC/headless geometry tests. Do not discard or reimplement those changes.
Reinspect the actual worktree at execution time rather than assuming it is still dirty.
Stop if a named existing class/test is missing, the source materially contradicts the
specified flow, or an implementation-significant decision remains unresolved. Do not
change the plan during direct implementation, run another step, or commit.

## Inspected starting point

1. `DspUncalibratedFullDayProfile.uncalibrated(...)` currently defines one
   `adapting-bench-1`, one processing position, a 60-second common STORE/COLLECT duration,
   four waiting places, and Third Party `(waiting=16, concurrent=1, duration=60)`.
2. `AdaptingDebugRig.createBenches(...)` defines the six physical `bench-1` through
   `bench-6` benches. Its existing one-position processing and visual topology remain
   unchanged; this plan configures the headless runtime, not new rig visuals.
3. `AdaptingBench` owns one visit, state, timer and completion. `AdaptingArea` owns one
   FIFO/`MachineWaitQueue` per bench. Increasing that queue does not create concurrency.
4. Transport and `AdaptingStationProcessingTarget` use a physical bench ID as the exact
   route destination. The coordinator already keys active claims by physical tote ID,
   so it can own several claims for the same bench without another coordinator.
5. The processing controller advances all bench timers, then applies at most one
   generic completion per update. It currently constructs bench/coordinator snapshots
   to recover completion identity. Modified paths must instead read exact live identities.
6. A strict COLLECT completion retains a read-only order-group preview with the storage
   owner's global mutation version. Another order's STORE/COLLECT can invalidate that
   version before application. Concurrency must handle this without weakening the guard.
7. Full-day station capacity is currently calculated from the number of benches, not
   the sum of their processing positions. Both initial and live resolver capacity need
   correction. The Third Party owner already supports duration and concurrency fields.
8. Command JSON is strict: unknown/duplicate/null properties are rejected. Main builds
   the profile once; configuration must not be consulted from a simulation update.

## Shared fixed contracts

### Scope, ownership and compatibility

- No P2P timing, geometry, machine/controller, workload cost, lease, allocation, line
  count or PRL change. No service-centre policy, supply-rate, timetable, route-length,
  route-speed, fixed-step, metrics-cadence, time-scale, fast-forward or thread change.
- Keep six physical benches distinct from their internal processing positions. Route
  selection, arrival queues, destinations, claims and bin ownership never use a position
  as a replacement physical bench ID. No additional 18-target route catalogue.
- Each position has exactly one visit/state/timer/completion. Each bench has one bounded
  waiting FIFO. Busy means QUEUED, PROCESSING, COMPLETED or BLOCKED; a completed-but-not-
  committed tote still occupies its position. Free capacity only after consume/clear.
- Preserve store/pharmacy affinity and exact already-selected-bench arrival revalidation.
  Do not add load-balancing, STORE-only lanes, priority queueing, or opportunistic rerouting.
- Bins remain `(storeId, referenceOrderId, overflowOrdinal)`, independent of benches
  and positions. Do not restore sheet-owned physical bins. All source STORE work must
  complete before the designated first COLLECT; later sheets remain gated until commit.
- Legacy bench constructors and no-position APIs retain the original one-position
  behavior. Existing debug rig, stop controller and fixtures continue unchanged.
- Retain public constructor signatures via delegating overloads. Do not remove existing
  record accessors. Where components are added below, retain the old constructor and
  define its delegation explicitly; do not require unrelated callers to migrate.
- All mutation remains on the simulation thread. Workers receive immutable snapshots.
  The coordinator remains the sole owner of generic claims/dispositions.

### Efficiency and deterministic execution

- Allocate bench/position owners, ordinal IDs, immutable position lists and controller
  traversal order once at construction. A visit reuses its position owner; no position
  object is created per visit or tick. Do not add generic scheduling frameworks or caches.
- Keep a bench-owned occupied-position counter: increment once on position acceptance,
  decrement once on successful completion consumption or clearing BLOCKED. Position
  transitions notify their owning bench through package-private methods, not snapshots.
  Capacity queries and acceptance use this counter. Keep `MachineWaitQueue` as queue owner.
- Read/update only configured benches and positions in timer/completion loops. This
  bounded traversal is necessary; no scans of all orders, packs, bins, claims, manifests,
  or completion history to identify a position or recover a claim.
- Tick each position once per update, in ascending physical bench ID then ordinal order.
  Apply at most one generic Adapting completion per update, using that same tie-break.
  Advance all timers before choosing a completion, as currently. A newly started queued
  visit does not receive that already-consumed update's time.
- Use existing map-backed storage/claim/load-plan lookups. Do not call `snapshot()` to
  recover an active tote, determine capacity, select an idle position, or find its claim.
- New detailed position snapshots are not required. Admission snapshots carry bounded
  scalar counts; create them only at the existing admission/report boundaries. No new
  per-tick snapshots, streams, filtered lists, deep equality cache keys or temporary maps.
- Group/pack traversals are allowed at actual STORE/COLLECT transaction boundaries.
  Refresh a stale preview only at preparation, never by polling all pending groups.
  Existing bin snapshot cache and immutable planned-slot dimensions lookup remain intact.
- Tests prove stable position/list identity and prohibited snapshot calls; no brittle
  wall-clock thresholds. Throughput improvements are user-observed, not asserted by timing.

## Locked configuration and API design

### JSON additions and fallback

Add optional top-level `thirdParty` and `adapting` objects to the existing invocation
file. JSON-only configuration in this slice: no new command-line timing flags and no
separate station-configuration file. The agreed example, to merge into the user's
existing `scheduler_conf.json` after implementation, is:

```json
{
  "thirdParty": {
    "processingDurationSeconds": 20
  },
  "adapting": {
    "storeDurationSeconds": 60,
    "collectDurationSeconds": 10,
    "processingPositionsPerBench": 3,
    "waitingCapacityPerBench": 3,
    "benchIds": ["bench-1", "bench-2", "bench-3", "bench-4", "bench-5", "bench-6"]
  }
}
```

This fragment is not a complete invocation; keep existing data/output/schedule options.
Do not edit the external live configuration or progress log as part of this plan.

Every new property is optional. Absent objects/properties retain the existing baseline
value, not the example value. Empty objects are permitted and mean no overrides.
Explicit nulls, unknown nested properties, duplicates, strings used as numbers and
fractional capacities are rejected. Durations are finite positive JSON numbers in
seconds; positions are positive integers; waiting capacity is a nonnegative integer.
Bench IDs are a nonempty array of trimmed nonblank strings, unique after trimming.
Reject route-target collisions with `third-party-1` and existing P2P target IDs before
world/controller construction. Use checked capacity sums/products and reject overflow.

Uniform positions/timings apply to each configured bench. Preserve baseline bench IDs
when `benchIds` is absent. Preserve baseline Third Party concurrency/waiting capacities.
Zero durations remain accepted by legacy programmatic constructors where already
supported; new command JSON deliberately requires positive durations.

### Configuration/profile types

- Extend `analysis/DspFullDayAnalysisConfigJson` with nested raw records
  `ThirdPartyJson(BigDecimal processingDurationSeconds)` and
  `AdaptingJson(BigDecimal storeDurationSeconds, BigDecimal collectDurationSeconds,
  Integer processingPositionsPerBench, Integer waitingCapacityPerBench,
  List<String> benchIds)`. Append the two nullable object components.
- Create package-private immutable `analysis/DspFullDayStationProcessingOverrides` with
  `OptionalDouble thirdPartyDurationSeconds`, `OptionalDouble adaptingStoreDurationSeconds`,
  `OptionalDouble adaptingCollectDurationSeconds`, `OptionalInt processingPositionsPerBench`,
  `OptionalInt waitingCapacityPerBench`, `Optional<List<String>> benchIds`, and `empty()`.
  Validate values and defensively copy IDs once. Parser converts raw JSON to this value.
- Append `stationProcessingOverrides` to `DspFullDayAnalysisCommand`. Retain its exact
  old constructor, delegating to `empty()`. No new runtime-owned configuration supplier.
- Extend nested `DspUncalibratedFullDayProfile.AdaptingBenchDefinition` to
  `(String id, double storeDurationSeconds, double collectDurationSeconds,
  int processingPositions)`. Retain `(String id, double processingDurationSeconds)`
  delegating to equal durations and one position; retain `processingDurationSeconds()`
  as the legacy alias of STORE duration. Require finite nonnegative programmatic
  durations and positive positions. Keep the outer profile's components/constructor.
- `DspFullDayAnalysisMain.profile(...)` merges overrides once into the baseline. Reuse
  unchanged objects when absent. When needed, construct bench definitions, Third Party
  config or queue capacities once, changing only the named fields. Legacy factories
  still return the original one-bench/60-second baseline.

### Physical bench and processing-position types

- Create package-private final `adapting/AdaptingProcessingPosition`. Extract the current
  bench visit/state/timer/completion logic into it, retaining STORE/COLLECT behavior.
  Its constructor receives owning `AdaptingBench`, one-based ordinal, shared store,
  STORE seconds and COLLECT seconds. Define package-private `int ordinal()`,
  `AdaptingVisit activeVisit()` and `PhysicalToteId activeToteId()`; the latter two return
  null only while idle, and return the retained visit/ID rather than constructing them.
  Retain the current accept/start/tick/peek/consume/clear behavior. A BLOCKED visit
  retains its identity until explicit clearing, as currently.
  Retain package-private state and snapshot accessors matching the old bench logic;
  its snapshot carries the owning physical bench ID. Timer code does not call it.
- `AdaptingBench` owns a construction-time `List<AdaptingProcessingPosition>` and the
  occupied counter. Add constructor `(String id, AdaptedLineStore store,
  double storeDurationSeconds, double collectDurationSeconds, int processingPositions)`.
  The old three-argument constructor delegates to equal durations/one position.
- Add public `processingCapacity()` and `occupiedProcessingPositions()`. Add package-
  private `position(int ordinal)` and `positions()` (the same immutable list instance
  on repeated calls), plus `firstIdlePosition()` returning an optional existing owner
  after a bounded ordinal scan. The live position owners are not worker publications.
  `canAcceptVisit()` uses occupied count versus capacity. `tick(double)` ticks all owned
  positions once. Validate ordinals before mutation.
- No-position mutating methods `acceptVisit`, `startProcessing`, `consumeCompletion`,
  `clearBlocked`, and singular `peekCompletion` are compatibility operations for a
  one-position bench; reject use on a multi-position bench explicitly. New production
  code always uses an exact position. Existing single-position callers need no edits.
- Preserve package-private `bindStorageMap(...)` and the existing one-argument
  `commitOrderGroup(...)` entry used by legacy single-position area-controller code;
  delegate to the shared store/position without duplicating store ownership. Step 3
  adds the exact-completion/fresh-group commit entry; Step 2 must still compile against
  the unchanged area controller.
- Keep `state()` and `snapshot()` as representative compatibility views: lowest ordinal
  occupied position, or position 1 when all idle. Snapshot bench ID remains the physical
  ID. With one position their values are unchanged. Multi-position capacity must never
  be inferred from this representative state/tote. No new record components in
  `AdaptingBenchSnapshot`; no fake aggregate tote ID.
- Append `processingCapacity` and `occupiedProcessingPositions` integers to
  `AdaptingBenchAdmissionSnapshot`. Its old five-argument constructor delegates to
  capacity 1 and occupied 0 for IDLE, 1 otherwise. Validate the count range.
  `canStartQueuedVisit()` uses free-position count and a nonempty queue, not IDLE state.

### Area, exact position and transaction identity

- `AdaptingArea` still owns one `BenchSlot`/FIFO per physical bench. Submission assigns
  the lowest idle ordinal only if the bench FIFO is empty; otherwise append to the FIFO.
  Never bypass an older queued visit. Capacity acceptance checks free position or queue.
  Precisely: with an empty FIFO, permit when a position is idle or the queue has room;
  with a nonempty FIFO, permit only when the queue has room. An unusual idle-position/
  full-FIFO state therefore defers a new visit instead of accepting then failing enqueue.
  Older work is dispatched by the explicit dispatch API, never hidden in admission.
  Retain the existing singular `dispatchNextQueuedVisit` API for one-position benches;
  reject multi-position use.
- Add `startQueuedPositions(AdaptingBenchId)` to start every accepted QUEUED position
  for that bench; it is invoked after claim acceptance, not before. Add
  `dispatchQueuedVisits(AdaptingBenchId)` to fill idle positions from the FIFO in order,
  starting them only in the production completion controller. Retain singular legacy
  dispatch semantics for one-position callers. `stationSnapshot()` sums occupied
  positions, not non-IDLE physical benches; queued remains pending FIFO visits.
- Add overloads `applyBenchCompletion(benchId, positionOrdinal)` and
  `prepareBenchCollect(benchId, positionOrdinal, currentLoadPlan)` in area controller.
  Existing overloads delegate to ordinal 1 and reject a multi-position bench instead
  of accidentally choosing another tote. No scanning to find a completion by tote ID.
- Append `positionOrdinal` and `currentOrderGroup` to `PreparedAdaptingCollect`.
  Keep its old six-argument constructor, delegating to ordinal 1 and the completion's
  required group. The token holds the exact retained completion, exact plan instances,
  exact position and refreshed transaction group. Validate identities before mutation.
- Add `refreshOrderGroupDecision(AdaptingPreparedOrderGroup)` to `AdaptingStorageLayout`
  and a delegating method to `AdaptedLineStore`. In strict mode: if the supplied version
  is current, return the same decision; otherwise use the existing exact group lookup
  and `prepareOrderGroup` to revalidate it. Require unchanged store, order, first/later
  flag and exact ordered record values. Different contents/first-collection facts are
  a failure, not permission to substitute packs. Return the fresh decision without
  mutating storage or the retained completion. Do not change `commitOrderGroup`'s
  global-version and complete-group checks or introduce per-order epoch caches.
- Position's package-private commit entry is
  `commitOrderGroup(expectedCompletion, currentOrderGroup)`: recheck retained completion
  reference and group/record identity, then call the existing store commit. Legacy
  single-group commit delegates with its retained completion.
- Add `StationProcessingCoordinator.findActiveClaim(PhysicalToteId)` returning the
  map-backed optional claim (null ID rejected). Use it instead of scanning snapshots;
  retain `requireActiveClaim`, all ownership rules and all existing snapshot APIs.

## Step 1 - Parse settings and resolve the immutable profile

Modify the command/config classes, loader/parser, Main and profile nested definition
exactly as specified above; create `DspFullDayStationProcessingOverrides`. Inspect their
complete current files and `DspFullDayAnalysisCommandTest`/
`DspUncalibratedFullDayProfileTest` first. Preserve strict duplicate detection and add
explicit nested shape validation in the loader; Jackson coercion is not validation.
Validate checked total capacity and target-ID collisions at profile resolution.

Tests must prove: the full example gives six definitions/three positions/60 and 10
seconds/three waiting slots and Third Party 20/1/16; absent/partial/empty objects use
baseline values; old command and definition constructors work; IDs are copied/trimmed;
representative invalid nested keys/nulls/types, duplicates, nonpositive durations,
zero positions, negative waiting, duplicate/blank/empty IDs, target collisions and
capacity overflow fail before input loading/runtime creation. Existing CLI precedence
and unknown-option behavior remain unchanged. Do not add timing CLI options.

Expected output: settings can be parsed/resolved and old invocation works. Runtime
concurrency/visit behavior is not wired until Step 4; do not claim end-to-end support.

### Implementation verification

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfileTest
```

### User verification

Review the configuration fragment and fallback behavior. No external run or full-suite
checkpoint is required for this intermediate step.

## Step 2 - Reusable bench positions and separate visit timers

Create `AdaptingProcessingPosition`; modify `AdaptingBench` only, implementing the
locked position API and compatibility wrappers. Inspect `AdaptingBench`, completion,
state, visit and snapshot classes and `AdaptingBenchTest`, `AdaptingStoreFlowTest`,
`AdaptingCollectFlowTest` first. Storage transaction refresh/integration remains Step 3.

Create `AdaptingProcessingPositionTest`; extend `AdaptingBenchTest`. Prove independent
60-second STORE and 10-second COLLECT timers, no early completion/staging, three exact
simultaneous visits, fourth refusal without mutation, occupied count during QUEUED/
COMPLETED/BLOCKED, release once only after consume/clear, deterministic ordinal choice,
stable positions/list identity, and no snapshot construction during timer progression.
Prove legacy equal-duration/single-position behavior and multi-position rejection of
singular mutation APIs. Use legacy/key-specific storage for mixed timer-only tests;
do not fabricate strict prepared work or bypass first-COLLECT rules.

No rig, full-day factory, generic station controller or layout edits in this step.

### Implementation verification

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingProcessingPositionTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStoreFlowTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest
```

### User verification

No additional user verification required for this intermediate domain-only step.

## Step 3 - Bench FIFO, claims and position-specific atomic completion

Modify `AdaptingArea`, `AdaptingBenchAdmissionSnapshot`, `AdaptingAreaController`,
`PreparedAdaptingCollect`, `AdaptingStationProcessingTarget`,
`AdaptingStationProcessingController`, `AdaptedLineStore`, `AdaptingStorageLayout` and
`StationProcessingCoordinator` exactly within the locked API changes. Read their
complete source and directly corresponding tests. `AdaptingAreaAdmissionSnapshot`,
`AdaptingStationAdmissionAdapter` and the continuation selector are prerequisites but
must retain their selection/route contracts. Do not modify scheduler release gates.

Arrival order: validate routed identity/current plan/destination and coordinator
eligibility; accept into the exact bench position/FIFO; claim the exact routed tote;
start accepted QUEUED positions. Full bench+FIFO deferral retains the exact arrival
head, routed objects and all prior ownership. Queued totes retain coordinator claims.

Update order: compute completion time once; tick each configured position once;
scan the precomputed bench/ordinal order for the first COMPLETED position with an
active claim; validate exact tote, physical bench destination, visit and current plan;
apply only that completion; dispatch/start queued visits at that bench; return.
No coordinator/bench snapshot is used in this flow. Retain skipping a domain completion
without a generic claim, as the current controller does, without consuming it.

STORE: preserve current prevalidation of generic CONSUME and inbound lifecycle,
publish prepared keys through area completion, consume source lifecycle, verify the
retained load plan, then complete the coordinator. Release only the exact position.

Strict COLLECT transaction sequence (do not reorder):

1. Validate generic CONTINUE against the current registered plan and exact claim.
2. Read the exact position's retained completion. Validate store/order/visit identity.
3. Refresh that completion's group decision through the storage owner. Its records
   and first/later facts must match the retained preview; unrelated global-version
   changes alone must not fail a valid pending collection.
4. Prepare prospective packs using existing planned-slot dimensions/correlations;
   validate provenance; prepare the exception observer; construct the prospective
   replacement plan; validate generic continuation against that exact plan.
5. Before mutation, recheck position/completion reference, registered-plan reference,
   provenance and storage's existing exact version/group validation.
6. Mutation boundary: drain using the refreshed group; register provenance; install
   the replacement plan; consume only this position's retained completion.
7. Complete coordinator CONTINUE using the exact replacement instance. Only then run
   the observer commit that publishes missing-pack facts and first-COLLECT readiness.

Prepare/commit one completion at a time. Never pre-prepare a batch of storage decisions
then commit them after another group mutates storage. A decision explicitly prepared
before a later mutation still fails at commit; the controller may obtain a fresh
decision during a subsequent preparation, not silently retry a partial commit.
No expected validation failure drains bins, publishes provenance/replacement plans,
consumes the completion, or publishes first-COLLECT. Post-drain unexpected failure is
fatal as before. Preserve legacy COLLECT behavior and empty later strict visits.

Extend `AdaptingAreaAdmissionTest`, `AdaptingCollectFlowTest`,
`AdaptingStationProcessingTargetTest`, `AdaptingStationProcessingControllerTest`,
`AdaptedLineStoreTest` and `StationProcessingCoordinatorTest`. Required proofs:

- Three active plus three waiting accepted at one bench; seventh deferred without
  mutation. FIFO head fills lowest available ordinal; no overtaking; no fresh visit
  uses idle capacity ahead of a waiting visit. Station counts report 3 active/3 queued.
- Concurrent STORE/COLLECT claims share one physical destination, use independent
  timers and exact plans, and each completion consumes only its own position/claim.
- Simultaneous completions cross one per update in deterministic bench/ordinal order;
  queued work starts without receiving duplicate elapsed time. Completed positions
  retain capacity until application, including when validation fails.
- At least two strict COLLECT previews for different orders exist together; committing
  one changes storage version. Preparing/committing the other succeeds with unchanged
  ordered packs. Also test unrelated STORE invalidation. Explicit stale token commit
  still fails without mutation, and changed group facts cannot be refreshed away.
- Failed provenance, observer and prospective continuation validation preserve bins,
  exact current plans, position completion, ledger and first-COLLECT marker. Stale
  position/completion or current-plan identity rejects before drain. Preserve the
  existing successful observer-after-coordinator assertion.
  For the prospective-plan negative case, use a test-only `ToteLoadPlan` subclass whose
  existing overridable `withAdditionalPackPlans(...)` returns a plan for a different
  physical tote. Register/claim that exact subclass instance as the valid current
  plan. Rejection must occur before drain; do not weaken token validation merely to
  force the rejection to occur inside the coordinator or introduce production hooks.
- A test bench whose `snapshot()` throws proves modified processing does not build
  that view. Prove indexed coordinator queries return the exact retained claim or empty
  without changing coordinator state, and review the controller source for absence of
  coordinator snapshot calls. The coordinator is final: do not remove `final`, build
  a mock framework, or add production instrumentation solely for this assertion.
- Existing wrong-destination/FIFO ownership, single-position and debug-flow tests
  remain meaningful. No tests expect a sheet to collect before the designated drain.

### Implementation verification

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaAdmissionTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingTargetTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingControllerTest --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinatorTest
```

### User verification

No external daily run is required before runtime integration. The user may run the
complete suite with `.\gradlew test`; that broader run is not model-authorized.

## Step 4 - Wire full-day capacities, timings and truthful reporting

Modify `analysis/runtime/DspFullDayAnalysisRuntimeFactory`,
`analysis/report/DspFullDayReportFactory`, `analysis/report/DspFullDayBlockedProgressFormatter`
and `analysis/DspFullDayAnalysisRunner`. No new runtime/progress snapshot components are
needed. Read those files plus runtime snapshot/admission suppliers and report tests.

Construct each physical bench using its new durations/position count. Keep one route
destination and arrival binding per physical bench. Compute total processing positions
with checked sum once at composition; waiting total remains per-bench capacity times
bench count. Use the same total capacity in `initialStationAdmissions` and
`stationAdmissionResolver`; do not use six as processing capacity when 18 are configured.
Area live `StationSnapshot` supplies actual occupied-position counts. Keep controller
registration order, Third Party owner/controller, store ownership and all P2P wiring.

Report profile bench entries retain `id` and legacy `processingDurationSeconds` (STORE
alias), and add `storeDurationSeconds`, `collectDurationSeconds`, `processingPositions`.
Existing Third Party and queue profile fields report the effective values as now.
At start progress only, append one `StationProcessing` summary for Third Party seconds,
total Adapting positions/waiting places, then one `AdaptingConfig[bench-id]` line per
bench giving its two durations, positions and waiting capacity. Do not repeat these
static settings per minute or reread configuration on updates.

Blocked bench lines add `occupiedPositions=x/y`. For multi-position benches rename
scalar labels to `representativeTote`, `representativeVisit`, `representativeRemainingSeconds`
and `representativeState`; they describe the lowest occupied ordinal, not the whole
bench. Preserve old scalar labels for one-position diagnostic fixtures. Do not allocate
full position snapshot lists to produce these counts. Detailed position inspection,
rig visuals and per-position movement remain deferred.

Extend `DspFullDayAnalysisRuntimeFactoryTest`, `DspFullDayReportFactoryTest`,
`DspFullDayReportJsonWriterTest`, `DspFullDayBlockedProgressFormatterTest`,
`DspFullDayProgressOutputTest`, `DspFullDayAnalysisRunnerTest`; create
`analysis/runtime/DspFullDayConcurrentAdaptingScenarioTest` using production runtime
composition and preflight/bag planning, not fake readiness/claims or manual bin drains.

Scenario requirements: a six-bench/three-position profile has six physical destinations,
18-position/18-waiting capacity, at least three simultaneously processing visits on
one bench, and independent STORE/COLLECT timing. Use staggered eligible orders to
exercise parallel STORE and COLLECT on different ready orders. No collect for an
unready order. Use two multi-sheet referenced orders with distinct product dimensions;
retain designated-first collection, later empty collection, wrong-sheet pack disposition,
partial and zero-pack exception accounting, exact source identities and terminal closure.
Assert repeat-run deterministic disposition/output identities and that all claims/queues/
positions drain. Preserve the collected-pack dimension fix's regression coverage.

Prove Third Party completes only after 20 simulated seconds under the new config and
retains one concurrent visit; inspect existing `ThirdPartyAreaTest` and
`ThirdPartyStationProcessingControllerTest` as regression boundaries. Report round trip
must expose six benches/18 positions/two durations and label calibration unchanged.
Old no-override fixtures still run with the baseline. The new settings must actually
drive the owners, not merely appear in the JSON report.
Timer assertions use controlled owner updates from processing start, not elapsed zero
of a runtime that still needs to transport/claim the tote. In production-runtime tests,
allow the existing one-fixed-step quantization/controller-order convention; do not
change generic clock/timer semantics to satisfy an exact absolute-time assertion.

### Implementation verification

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayConcurrentAdaptingScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayBlockedProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayProgressOutputTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingControllerTest
```

### User verification

After focused verification, the user runs:

```powershell
.\gradlew test
.\gradlew run --args="--scene=adapting"
.\gradlew installDist
```

Confirm the legacy six-bench rig looks/behaves as before; this does not demonstrate
multi-position visuals. After the currently running baseline has finished and its
outputs are retained separately, merge the example into the external configuration
and invoke the existing installed daily-run command. The implementation agent must
not edit that live file, rerun/install the app, run the daily workload, profile JFR,
or claim improved wall-clock speed from unit tests. User acceptance checks the start
settings, actual concurrent activity, dependency/exception correctness, and progression
beyond the previous failure point. Deadline/throughput results can change legitimately;
do not require exact old progress counts or pretend the configurations are equivalent.

## Step 5 - Architecture review (read-only)

After Step 4 user verification is green, review the entire feature diff, including
unchanged production boundaries directly used by it. No model-run Gradle command and
no extra user verification are required for this review step. Report PASS/FAIL/UNPROVEN
with concrete classes/control flow for each item:

1. Six physical benches and six routes, not 18 fake benches; exact destination preserved.
2. Independent reusable positions and one FIFO per bench; no hidden extra waiting slots.
3. Actual capacities/timers driven by configuration with legacy fallback/constructors.
4. Correct one-tick/one-completion sequencing, occupied counters and deterministic ties.
5. No per-tick/candidate position snapshots, whole-ledger lookup or rebuilt position lists.
6. Storage refresh is read-only and group-fact preserving; stale prepared commits reject.
7. Complete prevalidation before drain and observer publication only after continuation.
8. Bin/order/sheet, source dimensions, readiness, missing-pack and outbound contracts intact.
9. Effective reporting is truthful and representative fields are not mislabelled aggregates.
10. P2P, policies, fixed step, debug visuals and deferred scope remain unchanged.

Stop on architectural concerns; do not fix/redesign them during review. Full-day
performance improvement is UNPROVEN unless the user supplies comparable measured runs;
it is not a prerequisite to a correctness-conformant review.

## Step 6 - Documentation closure

After user verification and architecture review pass, reconcile only the implemented
facts in this plan and these documents:

- This plan: mark verified steps/review/closure, record exact focused/user outcomes,
  baseline fallback, measured results if provided and remaining P2P discussion status.
- `docs/machines/adapting_station_requirements.md`: distinguish physical bench from
  independent positions/shared waiting FIFO; separate STORE/COLLECT timing meanings;
  document estimate values as configurable assumptions, not measured facts. Clarify
  local labelling/bin work is included, not another simulated station. Explicitly retain
  current order-owned full-day storage and separate legacy coordinate storage; do not
  rewrite unrelated historical requirements or introduce storage-motion modeling.
- `docs/machines/third-party-station-requirements.md`: configurable per-visit duration,
  initial user estimate 20 seconds, existing area concurrency/waiting semantics retained.
- `docs/scheduler/dsp-operational-scheduling-requirements.md`: aggregate capacity is
  occupied positions versus total positions plus bounded bench queues; no change to
  rank/readiness/pinning. Record that old configs still select the baseline assumptions.
- `docs/codex-context.md`: append this verified capability and plan link; preserve the
  order-owned exception-flow direction and unrelated programme facts.
- `docs/codex-instructions.md`: add this completed plan under relevant station references,
  without requiring it for unrelated work or rearranging the mandatory historical order.

No implementation or new architectural decisions in closure. If sources conflict,
stop and report instead of silently resolving them. No model-run Gradle command or
additional user verification required; review the documentation diff and run
`git diff --check`. Do not edit the historical sheet-owned-bin plan or unrelated
performance plans merely to make their old status text current.

## Handoff for every implementation step

Run only that step's focused command. Review its complete diff against shared/step
contracts, run `git diff --check`, and report final `git status --short`. State files
changed, compatibility, owner/position/queue/publication behavior, tests/results and
unproven criteria. Do not run broader tests, installDist, external workloads or JFR on
the user's behalf. Do not commit. Stop for user verification/next-step initiation.

P2P timings/configuration remain a separate pre-implementation discussion. This plan
does not select their future schema, change them or authorize extending this slice.
