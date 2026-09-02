# DSP Full-Day Analysis, Metrics, And Inspection Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned; active programme work. No implementation has started.

## Purpose

Execute one loaded DSP operating day from day 0 at `06:00` until either all supported work reaches
the provisional P2P-output-closed milestone or the day +1 `00:00` hard cutoff, using the existing
profile composition:

```text
serviceCentreSupply: PRIORITY_ORDERED_OSR_LOW_WATERMARK
orderEligibility: DEPENDENCY_READY_OVERLAP
candidateRanking: PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE
p2pLineAllocation: DEADLINE_AWARE_ELASTIC_STICKY_LEASES
outboundAllocation: PHARMACY_PURE_FIXED_BAG_CAPACITY
timingCalibrationStatus: UNCALIBRATED
```

The feature adds a deterministic headless composition root, full-day execution lifecycle,
immutable metrics, provisional service-centre outcomes, machine-readable reports, and compact text
inspection. It consumes real product-master and 12N input paths but does not add a dataset to the
repository.

This is analysis infrastructure, not a calibrated production forecast. Every snapshot, inspection
view, and report must name profile `DEADLINE_AWARE_ELASTIC_STICKY_LEASES`, calibration status
`UNCALIBRATED`, and completion milestone `P2P_OUTPUT_CLOSED`. A result must never be labelled as a
dispatch, 32R, Cencora stacking, or trunker-loading outcome.

## Required Reading Before Implementation

Read, in order:

1. `docs/codex-instructions.md` and `docs/codex-context.md`;
2. this complete plan;
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`, especially Sections 11-20;
4. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`, especially Sections 7-16;
5. `docs/scheduler/dsp-deadline-aware-elastic-line-allocation-plan.md`;
6. `docs/scheduler/dsp-operational-simulation-clock-plan.md`;
7. `docs/scheduler/dsp-operational-empty-end-to-end-proof-plan.md`;
8. `docs/scheduler/dsp-station-processing-boundary-plan.md` and
   `docs/scheduler/dsp-station-route-continuation-plan.md`;
9. `docs/scheduler/dsp-scheduler-implementation-plan.md` and
   `docs/machines/phase-1-stations-roadmap.md`;
10. the exact production and test files named by the selected step.

Before executing any step, record `git status --short`. Stop if unrelated changes overlap the
selected step's change surface.

## Existing Boundaries To Preserve

- `DspDatasetAssembler` remains the only product/12N assembly boundary. MANUAL messages and lines
  remain excluded and visible through `DspDatasetLoadReport`.
- `DspOperationalClockController` follows `SimulationContext` absolute time. The runner never owns
  a second business clock or increments business time independently.
- `FixedStepExecutionDriver` remains the generic execution mechanism. Headless acceleration uses
  repeated bounded fixed steps, never one large world delta.
- `DspServiceCentreSupplyCoordinator` owns priority-ordered, low-water, rate-limited supply.
- `DspOperationalReleaseRuntimeFactory.createElasticWithAv02(...)` remains the one OSR/AV02
  operational ranking and command-application boundary.
- Every physical route uses launch hydration, common warehouse transport, terminal arrival,
  station claims, real station processing, continuation, and actual P2P completion. The full-day
  runtime must not add a direct station enqueue or a logical fast path around those owners.
- Five P2P lines retain sticky service-centre ownership. Feeding, draining, close-before-release,
  and exact committed tote assignments remain unchanged.
- `StoredBagReceiver`, `OutboundToteAllocationController`, `OutboundToteAllocator`, and
  `OutputSheetAllocator` remain the bag-to-outbound-tote owners.
- Scheduler evaluation remains pure over immutable snapshots. Headless analysis uses the existing
  synchronous evaluation source for deterministic single-process execution; it does not weaken the
  threaded production/debug compatibility path.
- Reset remains full runtime reconstruction. No mutable reset, rewind, or checkpoint restore is
  added.

## Locked Scope And Interpretation

### What a full-day run means in this branch

The runner loads the complete supplied dataset before elapsed simulation time zero. Loading does
not create totes, packs, bags, or renderables. The OSR preload, later rate-limited supply, AV02
allocation, operational release, stations, P2P, and outbound allocation then advance through the
normal simulation-thread controllers.

The run stops at the first of:

1. `ALL_SUPPORTED_WORK_COMPLETE`: every service centre has reached the provisional completion
   definition below and no controller owns pending physical work; or
2. `HARD_CUTOFF_REACHED`: the day +1 midnight snapshot is observed, all open outbound totes are
   closed once with `HARD_CUTOFF`, final metrics are captured, and no later world update occurs.

Exact day 0 `22:00` is overtime, not a stop condition. Missing an early deadline does not stop the
run.

### Provisional completion definition

Until Exception and outbound dispatch/32R exist, a service centre reaches
`P2P_OUTPUT_CLOSED` only when all of these facts hold in one immutable observation:

- its supply batch is `SUPPLY_COMPLETE` and has no upstream waiting or capacity-blocked manifest;
- no OSR or AV02 physical work for the centre remains waiting;
- every inbound physical tote is terminal at Adapting or P2P as required by its role;
- its elastic workload has zero remaining inbound totes, physical packs, and planned bags;
- no active station claim, pending station disposition, transport envelope, tipper input, or P2P
  assignment remains for the centre;
- every outbound tote for the centre is closed and no `StoredBagReceiver` contains an unallocated
  completed bag.

The implementation must derive this through a pure `DspFullDayCompletionEvaluator`; it must not
write `DspSchedulerRuntimeState` to manufacture completion.

If unresolved product lines, all-missing prescriptions requiring an Exception-created NS bag, or
another deferred Exception/MANUAL condition exists, retain it as an explicit unsupported-work item.
That service centre cannot reach the provisional completion milestone and is
`UNFINISHED_AT_HARD_CUTOFF` unless a later implemented domain boundary supplies its terminal
outcome. Never fabricate packs, bags, or completion to make the report green.

### Provisional timetable outcomes

Use exactly:

- `ON_TARGET`: provisional completion at or before `targetCompletion`;
- `OVERTIME_BUT_DISPATCHABLE`: after target but at or before `latestAllowedCompletion`;
- `MISSED_TRUNKER`: completed after latest allowed but before hard cutoff;
- `UNFINISHED_AT_HARD_CUTOFF`: no provisional completion at the cutoff.

These are analytical outcomes at milestone `P2P_OUTPUT_CLOSED`. Report the lateness against target
and latest deadline separately. Do not infer a real truck result.

### Timing and topology

- The runtime uses an explicit `DspUncalibratedFullDayProfile`. Every placeholder duration and
  capacity is a constructor field and is serialized into the report.
- The command line requires an OSR low-water mark, inbound interval, outbound tote bag capacity,
  and maximum packs per bag. There is no invented production default for those unknown values.
- The profile fixes five P2P lines and 31 PRLs per line, uses the existing production timetable and
  one-hour downstream duration, and defaults headless execution to a 50 ms fixed step, 2,000 steps
  per driver advance, and 60-second metric samples. These are simulation/execution defaults, not
  calibrated operational timings, and may be overridden only through validated profile fields.
- Use a source-neutral, non-rendered Phase 1 route catalogue with one common entry and direct
  terminal route per configured Third Party target, Adapting bench, and P2P line. It uses real route
  followers, sensors, ingress, in-flight, arrival, station, and continuation controllers. It adds no
  visual station-to-station topology or `SoftwareRenderer` integration.
- Active physical objects may retain minimal `RenderableObject` identity because existing routing
  contracts require it. Create those objects lazily with a null renderer and a shared trivial mesh;
  never add a full-day logical record to the renderable list and never invoke draw/pick behavior.

## Required P2P Correction For Dynamic Full-Day Work

The current `ToteToBagFlowController` receives one fixed `ToteToBagBatchPlan`. That is sufficient
for bounded rigs but not for future totes assigned dynamically to five lines. A full-day runtime
must not guess a line-local plan or split one bag correlation across P2P lines.

Introduce a simulation-thread-owned bag-correlation assignment boundary:

- every planned bag correlation is pinned to at most one `P2pLineId`;
- a tote requiring a correlation already pinned to a line may be assigned only to that line;
- a tote whose required correlations are pinned to different lines is an invariant failure before
  scheduler or command mutation;
- an unpinned correlation is committed atomically with the tote's exact P2P assignment;
- assignments never move and remain audit history after bag allocation;
- line work-plan providers expose expected pack counts only for correlations committed to that
  line;
- the long-lived tote-to-bag controller discovers newly committed correlations before admitting a
  candidate tote, adds them to outstanding expected work once, and never reopens a completed
  correlation.

This is a hard bag-assembly invariant, not an alternative scheduling profile. The profile ID stays
`DEADLINE_AWARE_ELASTIC_STICKY_LEASES`.

Use these production types under
`online.davisfamily.warehouse.sim.dsp.p2p.bag`:

- `P2pBagCorrelationRequirement`;
- `P2pBagCorrelationAssignment`;
- `P2pBagCorrelationAssignmentSnapshot`;
- `P2pBagCorrelationAssignmentRegistry`;
- `P2pBagCorrelationRequirementCatalog`;
- `P2pBagCorrelationRequirementCatalogFactory`;
- `BagCoherentOperationalP2pReleaseAssignmentCommitter`.

Add generic tote-to-bag types under `online.davisfamily.warehouse.sim.totebag.plan`:

- `ToteToBagWorkPlanProvider` with
  `OptionalInt expectedPackCount(String correlationId)` and ordered
  `Set<String> expectedCorrelationIds()`;
- `FixedToteToBagWorkPlanProvider`, adapting the existing immutable `ToteToBagBatchPlan`.

`P2pBagCorrelationRequirementCatalogFactory` derives requirements from the immutable
`BagPlanningResult` and `PlannedPackTrace` provenance. For OSR, group by input physical tote ID.
For AV02, resolve the allocated physical ID through its `OrderSheetKey`. A tote with no physical
packs has no correlation requirement; it remains unsupported Exception work when no normal planned
bag can complete.

Extend `P2pLineAllocationRequest` with the immutable requirement set and assignment snapshot while
retaining its existing constructor as a compatibility path with empty correlation state. The
deadline-aware policy filters its normal affinity/budget tiers by correlation compatibility before
choosing a line. The sticky compatibility policy receives the same hard filtering. Neither policy
may move an existing correlation.

`BagCoherentOperationalP2pReleaseAssignmentCommitter.prepare(...)` first prepares the existing
lease/tote-assignment commit, then fully validates the correlation commit. Its returned commit runs
the existing commit first and the prevalidated registry commit second; both are mechanically
non-failing on the simulation thread. A rejected downstream target executes neither commit.

Modify `ToteToBagFlowController` to accept `ToteToBagWorkPlanProvider` through one new canonical
live-input constructor that has no bootstrap `ToteLoadPlan`. In that form the existing
`ToteTrackTipperFlowController` is the sole owner that loads each accepted tote into
`TippingMachine`; `ToteToBagFlowController` observes/drains the shared tipping/sorting machines but
must not call its compatibility-only `loadToteIfNeeded()`. Existing `ToteLoadPlan`/
`ToteToBagBatchPlan` constructors delegate through `FixedToteToBagWorkPlanProvider` and retain their
one bootstrap-load behavior. Before `canAdmit(...)` and at the beginning of `update(...)`, sync new
expected correlations from the provider. Reject an arriving pack whose expected count is absent,
preserve arrival-driven PRL assignment, and retain every existing fixed-rig behavior.

## Full-Day Runtime Architecture

Create the runtime and reporting packages:

```text
online.davisfamily.warehouse.sim.dsp.analysis
online.davisfamily.warehouse.sim.dsp.analysis.metrics
online.davisfamily.warehouse.sim.dsp.analysis.report
online.davisfamily.warehouse.sim.dsp.analysis.runtime
```

### Configuration and input

Use:

- `DspFullDayInputPaths`, containing one product-master CSV path and a nonempty ordered list of 12N
  JSON paths;
- `DspUncalibratedFullDayProfile`, containing operating date, `OsrInventoryConfig`,
  `ServiceCentreSupplyConfig`, `FixedIntervalInboundToteArrivalPolicy`, `Av02AllocationConfig`,
  `P2pElasticAllocationConfig`, `OutboundToteConfig`, maximum packs per bag, fixed step,
  maximum steps per advance, metric sample interval, route speed, queue capacities, Third Party
  capacity/duration, Adapting bench definitions/durations, and P2P placeholder durations;
- `DspFullDayLoadedInput`, retaining `LoadedDspData`, `BagPlanningResult`, load report, and
  timetable;
- `DspFullDayInputLoader`, which calls `ProductMasterCsvLoader`, `TwelveNDatasetLoader`,
  `DspDatasetAssembler`, `DeterministicBagPlanner`, and the existing pack/load-plan factories in
  source order.

Validate all profile fields before creating a `SimulationWorld`. Require exactly five uniquely
named P2P definitions, 31 PRLs per line, timetable coverage and priority equality for every loaded
service centre, distinct input paths, nonempty retained work, and a hard cutoff after normal end.
Input/load issues remain immutable report data; structural identity conflicts fail before runtime
registration.

### Physical line composition

Use:

- `DspHeadlessP2pLineConfig`;
- `DspHeadlessP2pLineRuntime`;
- `DspHeadlessP2pLineRuntimeSnapshot`;
- `DspHeadlessP2pLineRuntimeFactory`.

The factory creates, per line, real `TippingMachine`, `SortingMachine`, `PdcConveyor`, 31
`PrlConveyor`s and matching `PdcDiversionDevice`s, `PcrConveyor`, `BaggingMachine`,
`StoredBagReceiver`, `ToteTrackTipperFlowController`, `ToteToBagFlowController`,
`TipperInputQueueController`, `OutboundToteAllocationController`, and
`ToteToBagP2pLineActivityProbe`. It supplies the line's dynamic `ToteToBagWorkPlanProvider` and the
actual P2P completion listener. It creates no conveyor/bagger meshes or inspection registrations.

Add an empty-start canonical `ToteTrackTipperFlowController` constructor that omits the initial
`Tote`; existing constructors delegate without behavioral change. Only
`TipperInputQueueController` may call `acceptNextTote(...)` in the full-day runtime.

Controller order within a line is tipper flow, tote-to-bag flow, tipper input dispatch, outbound
allocation, then lease activity observation. Preserve existing machine-before-controller ordering
from `SimulationWorld`.

### Whole-runtime composition

Use:

- `DspFullDayRuntimeState` enum with `RUNNING`, `ALL_SUPPORTED_WORK_COMPLETE`, and
  `HARD_CUTOFF_REACHED`;
- `DspFullDayAnalysisRuntime`;
- `DspFullDayAnalysisRuntimeSnapshot`;
- `DspFullDayAnalysisRuntimeFactory`;
- `DspFullDayCutoffController`;
- `DspFullDayCompletionEvaluator` and `DspServiceCentreCompletionSnapshot`.

The factory owns one `SimulationWorld` and composes, in this order:

1. operational clock;
2. loaded scheduler state, manifest catalogue, lifecycle ledger, load-plan registry, OSR bootstrap,
   supply plan/coordinator/controller, and AV02 inventory;
3. immutable bag planning, output-sheet allocator, outbound allocator, correlation registry, five
   headless P2P line runtimes, and elastic lease runtime;
4. AV02 allocation snapshot/controller loop and the single synchronous elastic OSR/AV02 release
   runtime;
5. route launch, source-neutral warehouse transport, direct headless route sensors, and exact
   destination arrival queues;
6. real Third Party and Adapting areas/controllers and the shared station-processing runtime;
7. station continuation using the same coordinator, route catalogue, transport queue, publisher,
   and load-plan registry;
8. completion/cutoff controller;
9. metrics collector last, so every sample observes all mutations from that fixed step.

Add `DspAv02AllocationRuntimeController` beside the existing AV02 types. It owns a monotonic
snapshot sequence, builds one fresh `Av02AllocationSnapshot` per update, and gives its selected
command plus that same-sequence fresh revalidation snapshot to the existing
`Av02AllocationController`. It allocates at most one EMPTY per update and exposes only immutable
snapshot/diagnostic values. Do not add a second AV02 scheduler or bypass the existing controller.

The runtime exposes immutable snapshots and `close()`. Close is idempotent, closes evaluation
sources/runtimes, and does not mutate simulation results. A second run always creates a new runtime.

`DspFullDayCutoffController` acts once when the clock first reaches hard cutoff. It closes every
open outbound tote in configured P2P-line order through `closeForHardCutoff(...)`, records the
cutoff state, and permits the last metrics capture. It does not consume inbound totes, complete
orders, clear queues, release leases, or continue processing after cutoff.

## Metrics Contract

Create:

- `DspFullDayBlockCategory` with `DEPENDENCY`, `STATION_CAPACITY`, `OSR_STATE`,
  `P2P_ASSIGNMENT`, and `UNSUPPORTED_WORK`;
- `DspFullDayOccupancySample`;
- `DspP2pLineMetricsSnapshot`;
- `DspServiceCentreMetricsSnapshot`;
- `DspFullDayMetricsSnapshot`;
- `DspFullDayMetricsCollector`.

The collector is simulation-thread-owned. It receives suppliers of immutable clock, supply, OSR,
AV02, lifecycle, workload/allocation, lease/activity, operational release, transport,
station, outbound, and completion snapshots. It never reads live collections through reflection or
passes mutable owners to a worker.

On every fixed step it integrates durations using `dtSeconds`. Classification for one blocked
physical/logical unit is mutually exclusive in this precedence order:

1. unsupported deferred-domain work;
2. dependency not terminal;
3. held upstream/OSR/AV02 availability or capacity;
4. selected station/transport admission capacity;
5. elastic budget, compatible-line, sticky assignment, or P2P-local admission.

Record counts and aggregate simulated duration by service centre and category; do not sum several
simultaneous reasons for the same unit in one step. Preserve the latest typed underlying reason for
inspection.

At elapsed zero, every configured sample interval, every service-centre completion, normal end,
and hard cutoff, append one bounded `DspFullDayOccupancySample` containing business time, OSR
occupancy/capacity/low-water mark, upstream waiting, admitted/departed inbound counts, closed
outbound tote/bag counts, and active line owners. Do not store per-object snapshots at every fixed
step.

Metrics must expose:

- profile/policy IDs, calibration and milestone;
- requested and achieved execution speed;
- OSR occupancy history, min/max/mean and net flow;
- configured inbound interval/rate, actual admitted counts/rate, and capacity-blocked time;
- outbound closed-tote and allocated-bag counts/rate;
- supply authorization time/state and upstream waiting by service centre;
- deadline, completion time/outcome, unfinished sheet/tote/bag counts, target/latest lateness;
- P2P owner, feeding/draining state, input/processing/output state, busy seconds, utilization,
  consumed-tote throughput, allocated-bag throughput, and closed-tote throughput per line;
- dependency outcomes and block duration by the five categories;
- elastic required/desired/owned/unmet lines and infeasibility history;
- MANUAL exclusions, unresolved products, and unsupported deferred work.

Utilization is `busySimulationDuration / observedSimulationDuration`, where a line is busy when its
existing `P2pLineActivitySnapshot` is not fully quiescent. Rates use simulated elapsed duration and
integer event deltas, not wall-clock time. Achieved execution speed alone uses runner-supplied real
duration.

## Report And Inspection Contract

Create:

- `DspServiceCentreCompletionOutcome` with the four outcome values above;
- `DspCompletionMilestone` with only `P2P_OUTPUT_CLOSED` in this branch;
- `DspFullDayTerminationReason` with `ALL_SUPPORTED_WORK_COMPLETE` and
  `HARD_CUTOFF_REACHED`;
- `DspServiceCentreAnalysisResult`;
- `DspFullDayAnalysisReport`;
- `DspFullDayReportFactory`;
- `DspFullDayReportJsonWriter`;
- `DspFullDayInspectionSnapshot`;
- `DspFullDayInspectionFormatter`;
- `DspFullDayAnalysisRunner`;
- `DspFullDayAnalysisMain`.

`DspFullDayReportFactory` consumes only the final immutable runtime, execution, metrics, and input
snapshots. Results are ordered by timetable priority descending then normalized service-centre ID;
line results use configured line order; occupancy samples use time order; issues use source order.

`DspFullDayReportJsonWriter` uses the existing Jackson dependency and maps Java time values to
explicit ISO-8601 strings and durations to integer nanoseconds plus readable ISO duration. Do not
serialize live domain objects or rely on an implicit Java-time module. Serialize fully in memory,
write UTF-8 to a temporary sibling file, then move it to the exact caller-supplied path using
`ATOMIC_MOVE` when supported and a same-directory replace fallback otherwise. Create only the
target parent directories, clean a failed temporary file, and refuse to overwrite an existing
report unless the command includes `--overwrite`.

`DspFullDayInspectionFormatter` produces deterministic compact lines for:

- run/profile/calibration/milestone and current business time/phase;
- requested/achieved speed and termination state;
- OSR occupancy/low-water/net flow and inbound/outbound rates;
- each service centre's supply state, deadline, remaining work, desired/owned lines, block summary,
  completion/outcome/lateness;
- each P2P line's owner, feeding/draining state, queue/activity, utilization, bags and outbound tote;
- current operational release decision/block and transport/station ownership;
- load exclusions and unsupported work.

The formatter is pure and has no renderer or `SelectionInspectionRegistry` dependency. The runner
prints inspection at start, each simulated hour, each service-centre completion, and final state.

`DspFullDayAnalysisRunner` constructs `FixedStepExecutionDriver` with
`FixedStepExecutionConfig.headless(...)`. Add
`FixedStepExecutionDriver.recordHeadlessRealElapsed(Duration)` so the runner can execute one batch,
measure that same batch outside the driver, and then account its real duration without emitting an
extra simulation step. The method is valid only in `HEADLESS_ANALYSIS`, validates nonnegative
finite duration, and changes only total-real-time/achieved-speed accounting. Tests inject a
monotonic `LongSupplier`, while the public constructor uses `System::nanoTime`. The runner checks
termination after every emitted fixed step so it never advances past the first terminal snapshot.
It closes the runtime in a `finally` block and returns the immutable report.

Command-line contract:

```text
--product-master=<csv path>                  required once
--orders=<json path>                         required one or more times, retained in argument order
--output=<json path>                         required once
--inspection-output=<text path>              optional
--operating-date=<YYYY-MM-DD>                required
--osr-low-water-mark=<count>                 required
--inbound-interval-seconds=<positive decimal> required
--av02-capacity=<count>                      required
--outbound-bag-capacity=<count>              required
--maximum-packs-per-bag=<count>              required
--fixed-step-millis=<positive integer>       optional, default 50
--steps-per-batch=<positive integer>         optional, default 2000
--metric-sample-seconds=<positive integer>   optional, default 60
--overwrite                                  optional flag
```

Unknown, duplicate singleton, malformed, missing, nonexistent, or directory-valued file arguments
fail before loading. Repeated `--orders` is the only repeated option. Add an `app` Gradle `JavaExec`
task named `dspFullDayAnalysis` whose main class is `DspFullDayAnalysisMain`; keep the application
plugin's `SoftwareRenderer` main class unchanged.

## Explicit Non-Goals

- calibrated route, station, label-printing, pack, bag, or operator timings;
- claiming production-accurate completion predictions;
- alternative scheduling profiles or stochastic arrival policies;
- `SoftwareRenderer` fixed-step/decimation integration or a new rendered scene;
- station-to-station visual topology, five rendered P2P assemblies, or visual polish;
- Exception Station, incomplete prepared-line resolution, NS bag creation, MANUAL, or
  MANUAL_MERGE execution;
- outbound dispatch transport, 32R, downstairs stacking, or trunker loading;
- event-driven fast-forward, checkpointing, persistence/database storage, render-thread split, or
  distributed/parallel simulation;
- mutating source data, writing reports without an explicit output path, or bundling production
  datasets in the repository.

## Step 1: Define The Uncalibrated Full-Day Input And Profile

### Required reading for this step

- `ProductMasterCsvLoader`, `TwelveNDatasetLoader`, `DspDatasetAssembler`, `LoadedDspData`, and
  `DspDatasetLoadReport`;
- `DspOperationalSchedulingBaselineFactory`, `DspOperationalClockConfig`,
  `OsrInventoryConfig`, `P2pElasticAllocationConfig`, and the configuration types named above;
- `DeterministicBagPlanner`, `DspPackPlanFactory`, and their focused tests.

### Required change surface

Create the four configuration/input types and loader named under Configuration and input, plus
focused tests under `...dsp.analysis`.

Do not create a `SimulationWorld`, controller, CLI, metrics type, report writer, or renderer in
this step.

### Behavioral specification

- Load product master once and 12N files in exact supplied order; preserve assembler source
  sequence, manifests, exclusions, unresolved product lines, logical sheet identity, and physical
  tote identity.
- Create one immutable full-dataset bag plan without creating active physical objects. Step 2 owns
  correlation requirement derivation from that plan.
- Validate profile/timetable/priority/capacity/timing inputs completely before returning.
- Require explicit unknown operational configuration while preserving the fixed five-line,
  31-PRL, timetable, downstream-duration, and explicit uncalibrated identity contracts.
- Reject structural conflicts without partial runtime state; retain supported load warnings in the
  loaded input.

### Decision-complete test contract

`DspFullDayInputLoaderTest` uses temporary product/12N files and proves deterministic file order,
mixed ADAPTED/FULL_PACK/ASSOCIATED/EMPTY assembly, MANUAL reporting, unresolved-product reporting,
bag/pack provenance, multi-manifest identity, and no renderable/simulation
creation. It separately rejects every invalid path shape and structural identity conflict.

`DspUncalibratedFullDayProfileTest` proves every validation rule, exact profile/policy/calibration
IDs, five lines/31 PRLs, production timetable/day offsets, required explicit fields, 50 ms/2,000/
60-second defaults, and immutable values.

### Expected output

One validated immutable input describes all logical work and all explicit uncalibrated execution
assumptions without starting the simulation.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfileTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Define uncalibrated full-day input`

## Step 2: Pin Planned Bag Correlations To Exact P2P Lines

### Required reading for this step

- `BagPlanningResult`, `PlannedBag`, `PlannedPackTrace`, and `DspPackPlanFactory`;
- `P2pLineAllocationRequest`, both sticky allocation policies, and
  `OperationalP2pReleaseAssignmentCommitter`;
- `ToteToBagFlowController`, `ToteToBagBatchPlan`, and their focused tests.

### Required change surface

Create the `...dsp.p2p.bag` and tote-to-bag work-plan types named above. Modify only the allocation
request/policies, elastic/sticky runtime composition, operational assignment committer composition,
and `ToteToBagFlowController` compatibility surface required by the locked correction. The exact
existing production classes are `P2pLineAllocationRequest`,
`DeadlineAwareElasticStickyP2pLineAllocationPolicy`, `StickyP2pLineAllocationPolicy`,
`DspP2pElasticAllocationRuntime`, `DspP2pElasticAllocationRuntimeFactory`,
`DspP2pStickyLeaseRuntime`, `DspP2pStickyLeaseRuntimeFactory`, and
`ToteToBagFlowController`. Add correlation-aware overloads to the two runtime factories and retain
every existing public signature as a delegating empty-correlation compatibility path. The elastic
runtime exposes the wrapped `BagCoherentOperationalP2pReleaseAssignmentCommitter` through its
existing `operationalReleaseAssignmentCommitter()` boundary, so
`DspOperationalReleaseRuntimeFactory.createElasticWithAv02(...)` remains unchanged.

Do not add a full-day runtime, station, transport, metrics, reporting, or CLI class in this step.

### Behavioral specification

- Derive exact ordered correlations per OSR physical tote and per AV02 logical sheet.
- Preserve normal policy ranking among correlation-compatible lines.
- Commit a new correlation only with an accepted exact tote assignment; rejected/deferred/stale
  release mutates neither registry, lease, nor tote assignment.
- Existing pinned correlations force the same line. Mixed existing lines fail before mutation.
- A long-lived tote-to-bag controller observes newly committed work, uses its planned expected pack
  count, and cannot report quiescence before every committed correlation completes.
- Existing fixed batch-plan constructors and tests remain source/behavior compatible.

### Decision-complete test contract

Create `P2pBagCorrelationRequirementCatalogFactoryTest`,
`P2pBagCorrelationAssignmentRegistryTest`, and
`BagCoherentOperationalP2pReleaseAssignmentCommitterTest`. Extend both allocation-policy tests and
`ToteToBagFlowControllerTest`.

Required cases include one correlation spanning two inbound totes, several correlations in one
tote, AV02 sheet resolution, no-pack work, compatible first pin, forced later line, conflicting
pins, rejected target/no commit, stale command/no commit, exact immutable history, dynamic work
after prior quiescence, no reopening completed work, and unchanged legacy fixed-plan behavior.

The tests must fail an implementation that allocates two halves of one bag to different lines,
pins before downstream acceptance, or lets a line release while a committed correlation remains.

### Expected output

Dynamic full-day assignments preserve bag assembly and provide each long-lived P2P line the exact
expected work it owns.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Pin P2P bag correlations`

## Step 3: Compose One Headless Production P2P Line

### Required reading for this step

- tote-to-bag installers and controllers named under Physical line composition;
- `ToteToBagP2pLineActivityProbe`, `P2pStationProcessingTarget`,
  `StationProcessingP2pToteCompletedListener`, and `OutboundToteAllocationController`;
- `DspP2pArrivalConsumerScenarioTest` and the P2P half of
  `DspAv02OperationalAllocationScenarioTest`.

### Required change surface

Create the four headless-line types. Add only the empty-start constructor to
`ToteTrackTipperFlowController` and focused compatibility tests.

Do not compose supply, scheduler release, Third Party, Adapting, warehouse transport, metrics,
reports, CLI, meshes, or debug inspection.

### Behavioral specification

- Build one real 31-PRL line without rendered machinery.
- Accept exact routed totes only through station claim -> tipper input -> input controller.
- Use the new live-input tote-to-bag constructor so the tipper-input path, not a fixed bootstrap
  plan, loads every tote exactly once.
- Use dynamic correlation plans, real tipper/sorter/PDC/PRL/PCR/bagger behavior, actual completion
  callback, stored-bag receiver, outbound allocator, and live activity probe.
- Preserve separate inbound consumption and outbound tote identity.
- Expose fresh value snapshots; close is idempotent and reset is reconstruction.

### Decision-complete test contract

`DspHeadlessP2pLineRuntimeFactoryTest` proves dependency validation before controller registration,
exact 31-PRL construction, controller order, no render/inspection registration, exact supplied
owners, and five independently constructible line IDs.

`DspHeadlessP2pLineRuntimeTest` drives several totes, including a correlation spanning two totes,
from exact station arrival through actual tipper completion and bag allocation. Assert line activity,
PRL completion, one completed bag, distinct outbound tote, no duplicate allocation, dynamic later
work, quiescence only after output closure, immutable old snapshots, and reconstruction reset.

Extend `ToteTrackTipperFlowControllerTest` to prove empty start, acceptance only when clear, and
unchanged initial-tote constructors.

### Expected output

One non-rendering runtime executes the existing physical P2P state machines for an unbounded
sequence of dynamically assigned full-day totes.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Compose headless P2P line runtime`

## Step 4: Compose The Full Operational Day Runtime

### Required reading for this step

- every existing runtime factory named under Whole-runtime composition;
- `Av02AllocationSnapshotFactory`, `Av02AllocationController`, OSR/supply bootstrap factories;
- `DspAv02OperationalAllocationScenarioTest` fixture only as an integration analogue; do not copy
  its private state machine into production.

### Required change surface

Create the whole-runtime, completion, cutoff, and AV02 runtime-controller types named above. Add
focused tests. Modify an existing factory only when the plan explicitly requires supplying one
already-existing owner; preserve its convenience overload.

Do not add metrics aggregation, report DTOs/writers, CLI, renderer integration, Exception/manual
execution, dispatch, or event-driven fast-forward.

### Behavioral specification

- Compose the exact controller/ownership order locked above from one `DspFullDayLoadedInput` and
  profile.
- Create five isolated P2P lines and exact OSR/AV02 release targets for every configured
  destination.
- Use lazy minimal physical visuals only after launch/allocation and real direct headless routes for
  every leg.
- Continuously allocate eligible EMPTY work through the existing AV02 controller and rank it with
  OSR through one synchronous elastic runtime.
- Calculate provisional completion without mutation.
- At hard cutoff close all open outbound totes once and stop; leave unfinished inbound/station work
  inspectable.
- Reject any dependency mismatch before registering a controller; close partial AutoCloseable
  composition in reverse order if a later construction failure occurs.

### Decision-complete test contract

`DspAv02AllocationRuntimeControllerTest` covers monotonic snapshots, at-most-one allocation, capacity,
dependency/authorization blocking, fresh revalidation, and immutable diagnostics.

`DspFullDayCompletionEvaluatorTest` covers every completion predicate, unsupported-work blocking,
exact first completion time, old-snapshot immutability, and all four timetable outcomes without
mutating scheduler state.

`DspFullDayAnalysisRuntimeFactoryTest` uses a recording world/factory seams only where necessary to
prove validation-before-registration, exact order, five lines, shared owners, synchronous profile,
all OSR/AV02 targets, reverse cleanup on failure, and no renderer/debug runtime dependency.

`DspFullDayAnalysisRuntimeTest` drives a small mixed OSR/AV02 dataset across real Third Party,
Adapting STORE/COLLECT, continuation, all five P2P target choices, actual tipper completion, bag
allocation, output closure, supply completion, early supported completion, hard cutoff with
unfinished work, idempotent cutoff, idempotent close, and reconstruction.

For backpressure/stale cases capture complete runtime state and permit only diagnostics to change.
The tests must detect direct station enqueue, duplicate physical publication, line movement,
inbound/outbound tote reuse, fabricated missing work, post-cutoff updates, or false completion.

### Expected output

One production composition can execute loaded work through the established physical boundaries and
reach a deterministic early-completion or hard-cutoff terminal state without rendering.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.* --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeControllerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Compose full-day DSP runtime`

## Step 5: Collect Deterministic Full-Day Metrics

### Required reading for this step

- every immutable snapshot supplied to the collector;
- `P2pElasticAllocationInspection` as a compact-format analogue only;
- operational requirements Section 18.

### Required change surface

Create only the metrics types and tests named in Metrics Contract. Add the collector to the runtime
factory last as already specified, and expose its immutable snapshot from the runtime.

Do not add reporting/JSON/CLI or change domain mutation.

### Behavioral specification

- Integrate exact fixed-step simulated duration with mutually exclusive block classification.
- Sample at the locked event/interval boundaries without per-step object-history growth.
- Derive rates/utilization from simulation time and cumulative domain deltas.
- Retain typed unsupported/load issues and explicit profile/calibration/milestone.
- Produce immutable, deterministic ordering and preserve old snapshots.

### Decision-complete test contract

`DspFullDayMetricsCollectorTest` covers zero state, interval boundaries, simultaneous block
precedence, each block category, block transition timing, normal end/hard cutoff, OSR min/max/mean,
inbound/outbound net flow, each throughput rate, P2P utilization, elastic infeasibility, completion
event sampling, unsupported work, no double counting, immutable history, and overflow-safe long
duration/count arithmetic.

`DspFullDayMetricsScenarioTest` drives the Step 4 runtime with two concurrent centres, later supply,
dependency blocking, station backpressure, draining/released lines, outbound closure, one on-time
centre and one unfinished centre. Assert metrics against exact domain history, not arbitrary update
counts.

### Expected output

Every run exposes comparable immutable deadline, flow, occupancy, blockage, throughput, and
utilization measurements with explicit analytical limitations.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.*
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Collect full-day DSP metrics`

## Step 6: Produce Outcomes, JSON Reports, And Text Inspection

### Required reading for this step

- final runtime/metrics snapshots from Steps 4-5;
- existing Jackson loader conventions;
- `P2pElasticAllocationInspection` and scheduler inspection formatters.

### Required change surface

Create the report and inspection types named in Report And Inspection Contract and focused tests.

Do not add the runner, CLI, Gradle task, renderer overlay, or new domain mutation.

### Behavioral specification

- Produce the four exact provisional outcomes from first completion/deadline/cutoff facts.
- Include every required metric, config value, policy ID, warning, and unfinished identity in stable
  order.
- Serialize explicit strings/primitives without live objects or implicit Java-time support.
- Format compact current/final inspection from immutable values only.
- Refuse accidental overwrite and leave an existing file unchanged on refusal/serialization
  failure by serializing completely before opening the target.

### Decision-complete test contract

`DspFullDayReportFactoryTest` covers all four outcomes, exact deadline boundaries, target/latest
lateness, termination reasons, stable ordering, unsupported work, profile/calibration/milestone,
and immutable report values.

`DspFullDayReportJsonWriterTest` parses emitted JSON back with Jackson and asserts the complete
schema, ISO time/duration representation, deterministic arrays, UTF-8, parent creation, overwrite
refusal/allowance, and no partial target replacement on failure.

`DspFullDayInspectionFormatterTest` asserts every locked section, stable line wrapping/order,
explicit `UNCALIBRATED`/`P2P_OUTPUT_CLOSED` labels, unfinished identities, and absence of calibrated,
dispatch, or trunk-loaded claims.

### Expected output

The terminal and current analytical state are available as a stable JSON report and readable text
without depending on the graphical inspection overlay.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.report.* --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInspectionFormatterTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Report full-day DSP analysis`

## Step 7: Add The Headless Runner And Command-Line Entry Point

### Required reading for this step

- `FixedStepExecutionConfig`, `FixedStepExecutionDriver`, `FixedStepExecutionSnapshot`, and their
  tests;
- `app/build.gradle` and the report writer contract.

### Required change surface

Create `DspFullDayAnalysisRunner`, `DspFullDayAnalysisMain`, a package-private command parser, and
their focused tests. Modify `FixedStepExecutionDriver` only to add
`recordHeadlessRealElapsed(Duration)` and extend `FixedStepExecutionDriverTest` for that method. Add
only the `dspFullDayAnalysis` `JavaExec` task to `app/build.gradle`.

Keep `SoftwareRenderer` as the application main class. Do not wire fixed-step execution into the
render loop.

### Behavioral specification

- Parse the exact command contract, validate before load, run bounded headless fixed steps, and
  stop at the first terminal state without one extra step.
- Measure achieved speed outside the generic driver; never sleep or use wall time for domain
  decisions.
- Print inspection at the locked milestones, write final JSON and optional text, close on success
  or failure, and return a nonzero process exit for invalid input/run/write failure.
- A fake monotonic clock produces byte-identical deterministic reports for identical input/profile.

### Decision-complete test contract

`DspFullDayAnalysisRunnerTest` covers early completion, exact hard cutoff, no post-terminal update,
bounded batches, measured speed, inspection milestones, close on every exit, and deterministic
repeat runs with a fake clock.

`DspFullDayAnalysisCommandTest` covers every required/repeated/optional option, unknown/duplicate/
malformed inputs, no overwrite, successful JSON/text output, and nonzero error return without a
partial report.

Do not add a Gradle build-logic test. Compilation of the main class plus the Step 9 user invocation
of `:app:dspFullDayAnalysis` verifies the task boundary.

### Expected output

Users can run one explicitly uncalibrated production-day analysis without opening the renderer.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.threedee.sim.framework.time.FixedStepExecutionDriverTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Run headless full-day DSP analysis`

## Step 8: Prove Full-Day Determinism, Scale, And Inspection

### Required reading for this step

- `DspAv02OperationalAllocationScenarioTest` and
  `DspDeadlineAwareElasticLineAllocationScenarioTest`;
- the public full-day runtime/runner/report entry points only.

### Required change surface

Create only:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisScenarioTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayLoadScaleTest.java`.

Use private synthetic input builders. Do not add a production fixture, test hook, bundled
production dataset, shortened domain path, or alternate scheduler.

### Behavioral specification

The scenario uses at least four timetable service centres, all four active order types, OSR and
AV02 sources, more than one physical manifest for one sheet, Third Party and Adapting work, five
P2P lines, a bag spanning two inbound totes, later low-water supply, output overflow, capacity
backpressure, one early missed deadline, one post-22:00 but dispatchable provisional completion,
and unfinished unsupported work at cutoff.

Run twice from fresh composition with the same fake monotonic clock. Assert equal report and
inspection values, physical identity/provenance, assignment/correlation history, occupancy samples,
block durations, line utilization, outcomes, and no logical-record renderable expansion.

`DspFullDayLoadScaleTest` assembles approximately 110,000 synthetic pack lines but does not execute
all physical work. It proves load, bag planning, correlation indexing, and pre-runtime state create
zero physical/renderable objects and retain deterministic counts/order. It is a memory/lifecycle
boundary test, not a throughput benchmark and must not assert wall-clock duration.

### Decision-complete test contract

The scenario must fail if work teleports between owners, one bag spans lines, fixed steps are
skipped, a cutoff update is followed by processing, metrics double count a block, outputs mix
pharmacy/service centre, reports omit uncalibrated/milestone labels, or repeated runs differ.

### Expected output

The feature is proven across the complete headless control flow and at full-day data-loading scale
without claiming calibrated performance.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadScaleTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message: `Prove full-day DSP analysis`

## Step 9: Regression, External Dataset Run, Review, And Closure

Do not begin Exception Station, calibration, renderer integration, outbound dispatch, or 32R during
closure.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Run the focused regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.station.continuation.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.threedee.sim.framework.time.*
```

Then run the complete suite:

```powershell
.\gradlew test
```

An external-data run is required before marking the feature verified. Because production data is
not stored in the repository, the user supplies the actual CSV/JSON paths and explicit unknown
configuration values. Run `:app:dspFullDayAnalysis` with the exact command-line contract in this
plan, inspect the generated JSON and text, and confirm:

- the entire supplied dataset is represented in load counts/exclusions;
- the report and inspection say `UNCALIBRATED` and `P2P_OUTPUT_CLOSED` prominently;
- the run terminates cleanly at supported completion or exact hard cutoff;
- OSR, inbound/outbound rates, centre outcomes, unfinished identities, line utilization, and block
  reasons are present and internally consistent;
- no result claims calibrated production prediction, dispatch completion, 32R, or trunk loading.

No graphical run is required. This branch adds no renderer integration or visual topology.

### End-of-feature architecture review

Review the actual diff and report PASS, FAIL, or UNPROVEN for every item with concrete
class/method/control-flow evidence:

- only the named first operational profile executes and every output identifies it as uncalibrated;
- loaded logical data creates no eager physical/renderable population;
- one planned bag correlation can never be owned by two P2P lines;
- correlation and tote assignment commit only after downstream release acceptance and never move;
- long-lived P2P lines discover dynamic expected work and retain all established machine behavior;
- five isolated lines use real tipper/sorter/PDC/PRL/PCR/bagger and independent outbound owners;
- one OSR/AV02 release boundary, real routing/stations/continuation, and actual P2P completion own all
  progress without teleport/test handoff;
- fixed-step headless execution never passes a large delta, stops exactly once, and never processes
  after cutoff;
- hard cutoff closes output but does not fabricate inbound/order completion or clear unfinished
  ownership;
- completion and four outcomes use the provisional P2P-output-closed definition and configured
  timetable only;
- metrics use immutable snapshots, simulation time, mutually exclusive block accounting, bounded
  occupancy history, and deterministic order;
- JSON/text contain profile, calibration, milestone, config, load issues, outcomes, unfinished work,
  rates, occupancy, utilization, and blockers;
- synchronous headless evaluation does not change the threaded compatibility path or allow worker
  mutation;
- reports are reproducible for identical input/profile/fake real-time source and output writing is
  safe;
- no Exception/MANUAL execution, NS bag fabrication, dispatch/32R, calibrated timing, renderer-loop
  integration, new visual topology, event-driven fast-forward, mutable reset, or source-data
  mutation was added;
- identify every production file changed outside the plan's required surfaces and determine whether
  it is necessary.

### Documentation closure

After focused/full tests, the external-data run, and architecture review are green:

- mark this plan complete and verified and record actual external-run configuration separately from
  the source dataset; never commit sensitive data paths or data;
- update current programme state in `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and only stale current-position/reading-order text in
  `docs/codex-instructions.md`;
- update the runtime interlude in `docs/machines/phase-1-stations-roadmap.md`;
- record full-day analysis as explicitly uncalibrated and based on provisional P2P output closure;
- make the next programme feature an explicit user decision between Exception Station Phase 1,
  timing calibration, outbound dispatch/32R prerequisites, or renderer integration; do not select
  one during closure;
- retain MANUAL/MANUAL_MERGE, Exception/NS behavior, dispatch/32R, visual topology, and calibrated
  timing as explicit deferrals unless separately implemented and verified.

Proposed commit message: `Complete full-day DSP analysis`

## Expected Final Contract

- A caller can load a complete product/12N day and execute it headlessly through bounded fixed
  simulation steps using the named uncalibrated elastic profile.
- Dynamic P2P assignments preserve bag-correlation coherence and feed five independent long-lived
  real machine lines without moving committed work.
- Supply, OSR/AV02 release, warehouse routing, station processing/continuation, P2P completion, and
  outbound allocation retain their established ownership and thread boundaries.
- The run stops at supported provisional completion or exact hard cutoff. Cutoff closes open output
  without inventing completion for unfinished or deferred-domain work.
- Immutable metrics and reports explain deadlines, occupancy/net flow, inbound/outbound rates,
  workload, block time, line utilization/throughput, outcomes, exclusions, and unfinished work.
- Every output states that timing is `UNCALIBRATED` and completion means
  `P2P_OUTPUT_CLOSED`, not real dispatch or trunker loading.
