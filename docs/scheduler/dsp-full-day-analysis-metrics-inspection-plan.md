# DSP Full-Day Analysis, Metrics, And Inspection Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: active. Steps 1-12 are implemented and committed through `a3a0b3b`. Step 11 added the
deployed 12N line-type code `03` mapping and full-day-only unresolved Third Party product projection.
Step 12 deterministically substitutes a unique simulation ID for each later occurrence of a reused
inbound carrier barcode while preserving the original barcode in the load report. The next
external-data attempt exposed 1,315 startup-eligible physical manifests against the configured
1,200-slot OSR. Step 13 replaces the invalid all-or-fail preload assumption with a capacity-bounded
deterministic preload whose overflow enters through normal rate-limited supply. The Step 13
external-data run then exposed two analysis-entry-point observability defects: maintaining the long
command line is unnecessarily fragile, and routine console inspection constructs and prints every
unfinished identity as one enormous line without preserving progress across a stall. Step 14 adds
strict JSON-backed invocation, Step 15 adds compact persistent progress logging, and Step 16
removes the proven full-day correlation-set allocation hot path. The next profiled run exposed
separate completion, OSR, and P2P-workload costs: Steps 17-19 remove those measured or directly
masked multiplicative paths, Step 20 is a profile gate for any remaining material hotspot, and
Step 21 owns final regression, external verification, review, and closure. A broader engine and
render-integrated simulation allocation review remains deferred until the functional full-day path
is working end to end.

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
immutable metrics, provisional service-centre outcomes, machine-readable reports, strict
JSON-backed invocation, compact persistent progress logging, and detailed final text inspection. It
consumes real product-master and 12N input paths but does not add a dataset to the repository.

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
- `OsrPhysicalInventory` remains the hard physical-capacity authority. Startup selection may fill
  but never exceed it; startup-eligible overflow remains upstream and uses the supply coordinator's
  existing timed admission and capacity-blocking semantics.
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
- The effective command configuration requires an OSR low-water mark, inbound interval, outbound
  tote bag capacity, and maximum packs per bag. Values may come from the command line or the strict
  JSON invocation file. There is no invented production default for those unknown values.
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

The formatter is pure and has no renderer or `SelectionInspectionRegistry` dependency. It remains
the detailed final formatter and may include exact unfinished identities. Routine progress must not
construct or format those identity lists. `DspFullDayProgressSnapshot` and
`DspFullDayProgressFormatter` own the separate lightweight progress projection described in Step
15. The runner prints and persistently mirrors progress at start, each configured simulated
interval, each service-centre completion, and final state.

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
--config=<json path>                         optional once
--product-master=<csv path>                  required once
--orders=<json path>                         repeat one or more times in explicit-file mode
--orders-directory=<directory path>          required once in directory mode
--output=<json path>                         required once
--inspection-output=<text path>              optional
--progress-log=<text path>                   optional
--operating-date=<YYYY-MM-DD>                required
--osr-low-water-mark=<count>                 required
--inbound-interval-seconds=<positive decimal> required
--av02-capacity=<count>                      required
--outbound-bag-capacity=<count>              required
--maximum-packs-per-bag=<count>              required
--fixed-step-millis=<positive integer>       optional, default 50
--steps-per-batch=<positive integer>         optional, default 2000
--metric-sample-seconds=<positive integer>   optional, default 60
--progress-interval-seconds=<positive integer> optional, default 300
--overwrite                                  optional flag
```

"Required" above means required in the effective merged invocation. A caller may provide the
required value in the JSON configuration, on the command line, or both. A command-line value wins
over the configured value. `--overwrite` forces the effective value to true; omitting it retains the
configured value or false when no configured value exists.

The JSON configuration uses these exact camel-case properties and JSON value types:

```json
{
  "productMaster": "path string",
  "ordersDirectory": "path string",
  "output": "path string",
  "inspectionOutput": "path string",
  "progressLog": "path string",
  "operatingDate": "YYYY-MM-DD",
  "osrLowWaterMark": 0,
  "inboundIntervalSeconds": 2.0,
  "av02Capacity": 1,
  "outboundBagCapacity": 1,
  "maximumPacksPerBag": 1,
  "fixedStepMillis": 50,
  "stepsPerBatch": 2000,
  "metricSampleSeconds": 60,
  "progressIntervalSeconds": 300,
  "overwrite": false
}
```

An explicit-file configuration replaces `ordersDirectory` with
`"orders": ["first path", "second path"]`. `orders` and `ordersDirectory` are alternatives and
must not both occur in one JSON object. Null
values, duplicate JSON properties, unknown properties, blank paths, wrong JSON types, and invalid
numeric/date values are rejected. Relative paths in the JSON resolve against the normalized parent
of the configuration file; relative command-line paths retain their existing process-working-
directory meaning. There is no environment-variable, home-directory, comment, include, or secret
substitution. The configuration file does not configure the production service-centre timetable or
priorities; that remains a separately deferred scheduling-configuration change.

Exactly one order-input mode is required. Explicit-file mode uses one or more repeated `--orders`
options and retains their argument order. Directory mode uses the singleton `--orders-directory`
option and expands only its immediate children that satisfy both `Files.isRegularFile(path)` and a
case-sensitive filename suffix of `.json`. It does not recurse. Sort directory entries by
`path.getFileName().toString()` using Java `String` natural order before creating the existing
ordered order-path list. Hidden files are treated like any other entry; symbolic links follow the
default `Files.isRegularFile` behavior. Ignore nonmatching entries and reject a directory with no
matching regular JSON files.

At most one `--config` is accepted and it may occur anywhere in the argument list. Duplicate
singleton detection applies within the command-line source; an explicit command-line override of a
configured singleton is valid. If either order-input option occurs on the command line, that source
replaces the complete configured order-input mode: repeated `--orders` preserve command-line order,
while one `--orders-directory` selects directory mode. Unknown, duplicate singleton, malformed,
missing, nonexistent, or wrong-kind paths fail before loading. Supplying both order-input modes in
the effective invocation also fails before loading. Repeated `--orders` remains the only repeated
option. The normalized report, inspection, and progress-log output paths must be pairwise distinct.
The existing `app` Gradle `JavaExec` task named `dspFullDayAnalysis` and application plugin
`SoftwareRenderer` main class remain unchanged.

## Explicit Non-Goals

- calibrated route, station, label-printing, pack, bag, or operator timings;
- claiming production-accurate completion predictions;
- alternative scheduling profiles or stochastic arrival policies;
- `SoftwareRenderer` fixed-step/decimation integration or a new rendered scene;
- station-to-station visual topology, five rendered P2P assemblies, or visual polish;
- Exception Station, incomplete prepared-line resolution, NS bag creation, MANUAL, or
  MANUAL_MERGE execution;
- outbound dispatch transport, 32R, downstairs stacking, or trunker loading;
- event-driven fast-forward, checkpointing, database state, application-wide logging, log rotation,
  render-thread split, or distributed/parallel simulation;
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

## Step 9: Add Deterministic Order-Directory Input

This is a formal plan amendment added after Step 8 because a complete external operating day has
more than 5,000 order files. It changes only command-line input expansion; it does not change the
loaded-input, runtime, scheduling, metrics, or report contracts.

### Required reading for this step

- the complete command-line contract under Report And Inspection Contract;
- `DspFullDayAnalysisCommandParser`, `DspFullDayAnalysisCommand`, and
  `DspFullDayAnalysisCommandTest`.

### Required change surface

Modify only:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisCommandParser.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisCommandTest.java`.

Do not modify `DspFullDayAnalysisCommand`, `DspFullDayInputPaths`, the input loader, main, runner,
Gradle task, runtime, metrics, report, or domain code. Directory expansion must end at the existing
ordered `List<Path>` command boundary.

### Behavioral specification

- Add singleton option `--orders-directory=<directory path>` and require exactly one of the two
  order-input modes: one or more repeated `--orders`, or one `--orders-directory`.
- Preserve repeated `--orders` behavior and exact argument order without sorting.
- Validate `--orders-directory` as an existing directory, then enumerate only immediate children
  in a closed `Files.list(...)` stream. An enumeration failure is an `IllegalArgumentException`
  identifying `--orders-directory` and retaining the I/O failure as its cause.
- Retain only entries for which `Files.isRegularFile(path)` is true and whose filename ends with
  lowercase `.json` using case-sensitive matching. Do not recurse. Ignore uppercase `.JSON`, other
  extensions, subdirectory contents, and directories whose own names end in `.json`.
- Sort retained paths by `path.getFileName().toString()` with `Comparator.naturalOrder()` semantics.
  Do not use locale-aware, case-insensitive, numeric, creation-time, or filesystem enumeration
  order.
- Reject a missing path, a regular file supplied as `--orders-directory`, a directory with no
  matching regular JSON files, a duplicate `--orders-directory`, or any invocation containing both
  order-input modes. Every rejection occurs before dataset loading and output creation.
- Pass the expanded immutable order through the existing `DspFullDayAnalysisCommand` and
  `DspFullDayInputPaths` boundaries. No directory path is retained in the command, report, or
  runtime.

Local constant naming, private helper decomposition, and exact error wording remain discretionary
provided errors identify the offending option and the tests can distinguish each rejected shape.

### Decision-complete test contract

Extend `DspFullDayAnalysisCommandTest` at the package-private parser boundary:

- create direct files named `10.json`, `2.json`, `A.json`, and `a.json` in deliberately different
  creation order; parse directory mode and assert exact natural-string order
  `10.json`, `2.json`, `A.json`, `a.json` in `command.orderPaths()`;
- in that directory also create a lowercase non-JSON file, an uppercase `.JSON` file, a directory
  ending in `.json`, and a nested lowercase JSON file; assert all are ignored and recursion does
  not occur;
- retain the existing explicit repeated-file test and assert its deliberately non-sorted argument
  order is unchanged;
- separately reject both modes together, duplicate `--orders-directory`, nonexistent directory,
  regular-file-as-directory, and a directory with no matching direct regular lowercase `.json`
  file;
- for one representative invalid directory-mode invocation through `DspFullDayAnalysisMain.run`,
  assert a nonzero exit and that neither JSON nor inspection output is created.

These cases must catch an implementation that accepts the happy path but uses filesystem order,
numeric/case-insensitive ordering, recursion, case-insensitive extension matching, both modes, or
an empty expanded list.

### Expected output

A complete day can be selected with one directory argument while the loader still receives the
same deterministic nonempty ordered `List<Path>` contract used by explicit files.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest
```

### User verification

No additional user verification is required for this step. Step 21 owns post-amendment regression
and the external-data run.

Proposed commit message: `Accept full-day order directories`

## Step 10: Accept Optional Product-Barcode Length Metadata

This is a formal plan amendment added after the first external-data attempt. Deployed 12N JSON may
contain erroneous `orderDetail.productBarcodeLength` metadata. The field is not operational input,
but strict deserialization currently rejects the complete file before normal DSP mapping. The
producer may remove the field in future, so compatibility must accept both its presence and its
absence without weakening strict handling for any other unknown property.

### Required reading for this step

- `TwelveNMessageJson`, `TwelveNOrderDetailJson`, and `JsonLoaderSupport`;
- `TwelveNMessageJsonTest` and the EMPTY/missing-transport cases in `TwelveNOrderMapperTest`;
- every direct `new TwelveNOrderDetailJson(...)` call reported by repository search.

### Required change surface

Modify production only in:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/TwelveNOrderDetailJson.java`.

Modify focused coverage in:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/TwelveNMessageJsonTest.java`.

Mechanically update the direct record-constructor fixtures in:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetAssemblerTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/lifecycle/InboundPhysicalToteLifecycleScenarioTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/osr/DspOsrPhysicalInventoryScenarioTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/supply/DspRateLimitedServiceCentreSupplyScenarioTest.java`.

Do not modify `JsonLoaderSupport`, `TwelveNDatasetLoader`, `TwelveNOrderMapper`,
`TwelveNLineMappingSupport`, `DspDatasetAssembler`, the command line, reports, runtime, or domain
logic. Do not add production data or its path to the repository.

### Behavioral specification

- Add nullable `Integer productBarcodeLength` to `TwelveNOrderDetailJson` immediately before
  `orderLines`, matching the source JSON property name and the existing nullable integer length
  metadata pattern.
- When the property is present with an integral JSON value, deserialize and retain that value.
  When it is absent or explicitly `null`, deserialize it as `null` without failure.
- Treat the value as inert protocol metadata. Do not validate it, derive a product barcode from it,
  expose it through DSP domain objects or reports, or make scheduler/runtime behavior depend on it.
- Keep Jackson strict for every other unknown property. Do not add `@JsonIgnoreProperties`, disable
  `FAIL_ON_UNKNOWN_PROPERTIES`, or introduce a generic extension-property map.
- Pass `null` for the new component in every direct constructor fixture named above. Those are
  mechanical source-compatibility updates and must not change fixture behavior or assertions.
- Preserve existing transport-container semantics exactly: an omitted `transportContainer`
  deserializes as `null`; tote type `03` maps to EMPTY with no inbound manifest; physical inbound
  order types still reject a missing transport container.

### Decision-complete test contract

Extend `TwelveNMessageJsonTest` to prove all of the following through `JsonLoaderSupport`:

- a representative message containing numeric `orderDetail.productBarcodeLength` deserializes and
  retains the exact `Integer` value;
- a representative message omitting `productBarcodeLength` deserializes and exposes `null`;
- an otherwise representative `orderDetail` containing a different unknown property is still
  rejected, proving the implementation did not broadly relax strict JSON binding.

Retain the existing `TwelveNOrderMapperTest.shouldMapEmptyWithoutInboundManifest` and
`shouldRejectMissingTransportContainerForPhysicalInboundOrder` cases unchanged. Running that test
class must prove the record change does not alter the already-correct EMPTY/physical distinction.
Compilation must cover all four mechanically updated direct-constructor fixture classes.

### Expected output

Both deployed files containing the erroneous metadata and future corrected files omitting it pass
strict JSON binding. The metadata has no effect on mapped DSP work, and missing
`transportContainer` remains valid only for EMPTY input.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJsonTest --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapperTest
```

### User verification

No additional user verification is required for this step. Step 21 owns the post-change focused
regression, complete suite, and external-data run.

Proposed commit message: `Accept optional 12N barcode metadata`

## Step 11: Accept Third Party 12N Lines And Defer Unresolved Short Picks

This is a formal plan amendment added after the next external-data attempt. Production 12N data
uses order-line type code `03` for Third Party lines. The later ASSOCIATED representation of a
Third Party line that was prepared through an ADAPTED order uses code `02`, so the associated order
retains the existing prepared-line dependency and collection semantics. Of the code-`03` lines in
the inspected day, every line whose product exists in the supplied product master has a Third Party
location. A smaller set refers to products absent from the product master; operationally those are
short picks whose totes would visit the not-yet-implemented Exception Station.

### Required reading for this step

- `DspOrderLineType`, `DspOrderItem`, `NotionalToteOrder`, and `DspOrderModelTest`;
- `TwelveNLineMappingSupport`, `TwelveNOrderMapper`, and `TwelveNOrderMapperTest`;
- `DspDatasetAssembler`, `LoadedDspData`, `UnresolvedProductLine`, and
  `DspDatasetAssemblerTest`;
- `DspFullDayInputLoader`, `DspFullDayLoadedInput`, `DspFullDayInputLoaderTest`, and
  `DspFullDayAnalysisScenarioTest`;
- `InboundToteManifest`, `PreparedLineKey`, `DspRouteDeriver`, and `ThirdPartyVisitFactory`;
- the unresolved-product unsupported-work handling in `DspFullDayAnalysisRuntimeFactory`.

### Required change surface

Modify production in:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderLineType.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/UnresolvedProductLine.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetAssembler.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayInputLoader.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayAnalysisRuntimeFactory.java`.

Modify focused coverage in:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderModelTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/TwelveNOrderMapperTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetAssemblerTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayInputLoaderTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisScenarioTest.java`.

Do not add a `THIRD_PARTY` member to `DspOrderLineType`; change `ThirdPartyVisitFactory`,
`DspRouteDeriver`, `LoadedDspSchedulerRuntimeFactory`, generic scheduler/runtime behavior, station
processing, or Exception Station behavior; fabricate a product, pack, bag, pick, completion, or
physical tote; mutate source JSON/CSV; or add production data or production paths to the repository.

### Behavioral specification

#### Source code `03`

- Extend `DspOrderLineType.fromCode` so trimmed source code `03` returns the existing
  `DspOrderLineType.FULL_PACK` value. This is an input normalization alias, not a fourth domain line
  type. Keep `DspOrderLineType.FULL_PACK.code()` equal to canonical code `05`.
- Keep code `01` as `MANUAL`, code `02` as `ADAPTED`, code `05` as `FULL_PACK`, and reject every
  other unknown code exactly as before.
- Continue to use the product master as the source of Third Party location and physical dimensions.
  A normalized code-`03` line receives Third Party routing only when its product-master record has a
  Third Party location.
- For an ADAPTED order containing source code `03`, the enclosing `OrderType.ADAPTED` continues to
  make Third Party work `ADAPTED_PREPARATION`; the pack then follows the established Adapting STORE
  lifecycle. Do not infer the later associated dependency from this source code.
- The corresponding line in a later ASSOCIATED order is source code `02`, maps to
  `DspOrderLineType.ADAPTED`, blocks on its `PreparedLineKey`, and is collected through the existing
  Adapting path. The alias must not cause a duplicate Third Party pick for that associated line.

#### Temporary unresolved-product deferral

- Extend `UnresolvedProductLine` with a required, trimmed `serviceCentreId`. Populate it in
  `DspDatasetAssembler` from the retained order so unsupported work can be attributed only to its
  owning service centre. Preserve `orderId`, `lineReference`, and `productId` unchanged.
- Keep `DspDatasetAssembler`'s generic output semantics otherwise unchanged: it reports unresolved
  lines and retains them in assembled orders, prepared lines, prepared-line keys, and manifests.
  Generic callers therefore retain their current fail-fast behavior.
- In `DspFullDayInputLoader`, add a private static
  `LoadedDspData executableData(LoadedDspData assembledData)` helper and call it immediately after
  assembly and before loaded-data validation and bag planning. This helper is only for data produced
  by `DspDatasetAssembler`. Determine known products from `LoadedDspData.products()` and remove
  every line whose product ID is unknown from executable orders, prepared lines, prepared-line key
  sets, and inbound manifests.
- Rebuild a partially retained `NotionalToteOrder` with all original identity, service-centre,
  sheet, order-type, priority, and source-sequence values and the remaining lines in their original
  order. Omit an order when no executable lines remain.
- Rebuild a partially retained `InboundToteManifest` with `withItems`, retaining its physical tote
  ID, order sheet, order type, service centre, and source sequence. Omit a manifest when no
  executable lines remain; never construct an empty manifest.
- Retain only prepared lines whose products are known. Recompute `loadedPreparedLineKeys` from those
  retained prepared lines using `PreparedLineKey.forPreparedLine`. Intersect
  `startupReadyPreparedLineKeys` with the recomputed loaded keys so an excluded line cannot remain
  ready at startup.
- Preserve products and the original `DspDatasetLoadReport` exactly in the executable projection.
  In particular, do not increment `omittedOrderCount`: unresolved lines and all-unresolved orders
  remain represented by `unresolvedProductLines`, not by the existing MANUAL/empty-after-filter
  omission metric.
- Keep the existing requirement that a full-day input contain at least one executable supported
  order. Do not weaken `DspFullDayInputLoader.validateLoadedData` for an all-unresolved dataset.
- Change `DspFullDayAnalysisRuntimeFactory.unsupportedFor(serviceCentreId)` to include only
  unresolved-product issues whose new `serviceCentreId` equals the requested service centre. Keep
  each issue in the report and unsupported-work output. The affected service centre cannot claim
  supported completion and reaches the hard cutoff until Exception Station behavior exists; other
  service centres are not blocked by that issue.
- Treat this projection as a temporary Exception Station deferral. Valid sibling lines may execute,
  but no behavior may imply that an unresolved line was picked, packed, bagged, or completed.

### Decision-complete test contract

Extend `DspOrderModelTest` to prove source code `03` maps to `DspOrderLineType.FULL_PACK`, canonical
`FULL_PACK.code()` remains `05`, whitespace trimming still applies, and a representative unknown
code is rejected.

Extend `TwelveNOrderMapperTest` with representative mapping coverage proving:

- a code-`03` line in an ADAPTED message is retained as a `FULL_PACK` domain line while the order
  remains `OrderType.ADAPTED`;
- a code-`02` line in an ASSOCIATED message remains `ADAPTED`, preserving prepared-line collection
  semantics;
- existing `01`, `02`, and `05` mapping behavior remains unchanged.

Update `DspDatasetAssemblerTest` for the added `serviceCentreId` component and assert that an
unresolved issue carries the exact owning service centre while the generic assembled order and
manifest still retain the unresolved line.

Extend `DspFullDayInputLoaderTest` with a deterministic mixed fixture containing:

- a known code-`03` Third Party line;
- a known sibling line;
- an unresolved code-`03` line in a partially supported order/manifest;
- an order/manifest containing only an unresolved line.

Through the public loader, assert that code `03` is normalized, known lines remain in source order,
the unresolved sibling is absent from executable order/manifest/prepared state, the all-unresolved
order and manifest are absent, no empty manifest is constructed, and every unresolved issue remains
in the unchanged report with its owning service centre. Assert that bag planning creates no pack or
bag provenance for an unresolved line.

Extend `DspFullDayAnalysisScenarioTest` with a bounded end-to-end run containing supported work in
two service centres and an additional unresolved product in one of them. Prove runtime construction
and execution do not throw, the unresolved line creates no Third Party/P2P pack or completion, the
issue appears as unsupported work only for its owning service centre, the unaffected service centre
may reach supported completion, and the overall run reaches the exact hard cutoff without
fabricated completion for the affected service centre.

### Expected output

Production 12N code-`03` Third Party lines enter the established Third Party flow without adding a
new domain line type. Their later code-`02` ASSOCIATED lines retain normal prepared-line collection.
Products absent from the product master remain explicit, service-centre-specific unsupported short
picks but do not abort a full-day run before the deferred Exception Station is implemented.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderModelTest --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapperTest --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest
```

### User verification

No additional user verification is required for this step. Step 21 owns the post-change focused
regression, complete suite, and repeated external-data run.

Proposed commit message: `Accept third party 12N lines`

## Step 12: Normalize Reused Inbound Carrier Barcodes

This is a formal plan amendment added after the next external-data attempt. The inspected production
day contains a small number of transport-container barcodes that occur in more than one retained
12N message. These represent separate DSP tote journeys even when the same real carrier was
returned and reused. The simulation intentionally keeps its established invariant that one
`PhysicalToteId` identifies exactly one DSP journey: the first retained occurrence keeps the source
barcode and every later occurrence receives a deterministic synthetic ID during dataset assembly.

This is an input-normalization accommodation for production run data, not physical-carrier reuse
modeling. The simulation does not add carrier availability, lifecycle reactivation, or a dependency
between occurrences. It accepts the small possibility that two normalized journeys derived from
one real carrier could be live simultaneously. Synthetic datasets created for tests or analysis
must continue to use unique source transport-container barcodes except for the focused fixtures
that prove this normalization boundary.

### Required reading for this step

- `DspDatasetAssembler`, `MappedTwelveNOrder`, `TwelveNMessageJson`, and
  `DspDatasetAssemblerTest`;
- `InboundToteManifest`, `InboundToteManifestCatalog`, `PhysicalToteId`, and
  `PhysicalToteLifecycleLedger`;
- `DspDatasetLoadReport`, `UnresolvedProductLine`, and their existing construction/test sites;
- `DspFullDayInputLoader`, `DspFullDayLoadedInput`, and `DspFullDayInputLoaderTest`;
- `DspFullDayReportFactory`, `DspFullDayReportJsonWriter`,
  `DspFullDayInspectionFormatter`, `DspFullDayReportTestSupport`, and their focused tests;
- the full-day runtime construction path in `DspFullDayAnalysisRuntimeFactory` and the bounded
  scenario fixtures in `DspFullDayAnalysisScenarioTest`.

### Required change surface

Create:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/InboundToteIdSubstitution.java`.

Modify production in:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetAssembler.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetLoadReport.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/lifecycle/InboundToteManifest.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayReportFactory.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayReportJsonWriter.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayInspectionFormatter.java`.

Modify focused coverage in:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/io/DspDatasetAssemblerTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayInputLoaderTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisScenarioTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayReportTestSupport.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayReportJsonWriterTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayReportFactoryTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayInspectionFormatterTest.java`.

Do not modify `PhysicalToteId`; relax uniqueness in `InboundToteManifestCatalog`, lifecycle,
supply, OSR, AV02, transport, station, P2P, or outbound components; add carrier reuse/admission
state; change source JSON records; write normalized source files; derive behavior from production
file paths; or alter order, sheet, line, product, pack, bag, service-centre, source-sequence, or
prepared-line identity.

### Behavioral specification

#### Deterministic substitution

- `DspDatasetAssembler` remains the sole normalization boundary. Apply substitution after a
  non-MANUAL message has been mapped and its retained lines are known, but before its inbound
  manifest is added to `inboundToteManifests`. Messages with no inbound manifest, including EMPTY,
  do not participate. A MANUAL message remains excluded and does not reserve or consume an inbound
  identity occurrence.
- Compare source transport-container identities through the mapped manifest's normalized
  `PhysicalToteId`; therefore existing trimming semantics apply. Preserve input iteration order,
  which the directory loader already establishes from ascending JSON filename order.
- The first retained manifest for a source `PhysicalToteId` keeps that exact ID. For the second and
  each later retained manifest, replace only the manifest's `PhysicalToteId` with a generated ID
  using base form `dsp-reused-<source-id>-<one-based-occurrence>`, where the occurrence is `2`, `3`,
  and so on for that source ID.
- Before mapping messages, collect every nonblank transport-container payload present in the input
  message list into a reserved source-ID set. A generated candidate must not equal any reserved
  source ID, any retained first-occurrence ID, or any earlier generated ID. If the base candidate
  collides, append `-<one-based-collision-attempt>` beginning with `-1` until an unused candidate is
  found. Candidate generation and collision resolution must be deterministic for identical ordered
  input.
- Add `InboundToteManifest.withPhysicalToteId(PhysicalToteId)` returning a manifest with only that
  component replaced. Use it for substitution after any retained-line `withItems(...)` projection;
  do not mutate a mapped/source JSON object.
- Construct all later bag plans, load plans, lifecycle records, supply entries, route identities,
  metrics, and outcomes from the normalized manifests through existing production paths. No
  downstream component receives two equal inbound `PhysicalToteId` values, and all existing
  duplicate guards remain unchanged.

#### Audit reporting

- Add immutable record `InboundToteIdSubstitution` with required fields:
  `PhysicalToteId sourcePhysicalToteId`, `PhysicalToteId substitutedPhysicalToteId`, positive
  `int occurrenceNumber` greater than one, and nonnegative `long sourceSequenceNumber`. Reject null
  IDs, equal source/substituted IDs, occurrence values below two, and negative source sequences.
- Extend `DspDatasetLoadReport` with an ordered
  `List<InboundToteIdSubstitution> inboundToteIdSubstitutions`. Defensively copy and reject null
  lists/elements. Preserve source
  processing order. Keep a delegating constructor with the existing four-argument signature that
  supplies an empty substitution list, and update `empty()` accordingly, so unrelated fixtures and
  callers remain source-compatible.
- Record exactly one substitution entry for each later retained occurrence, using the mapped
  manifest's unchanged `sourceSequenceNumber`. Do not count a first occurrence, excluded MANUAL
  input, EMPTY input, or a message omitted because no non-MANUAL lines remain.
- Preserve the complete load report through the Step 11 executable-data projection. Substitution
  is informational: it is not unsupported work, does not increment omission/MANUAL/unresolved
  counts, and does not prevent a service centre from completing.
- In JSON, add ordered `load.inboundToteIdSubstitutions` objects containing
  `sourcePhysicalToteId`, `substitutedPhysicalToteId`, `occurrenceNumber`, and
  `sourceSequenceNumber`. Do not add the substitution list to metrics or duplicate it elsewhere in
  the JSON contract.
- Add `reusedInboundToteIds=<count>` to the compact text `Load:` line. Add one report warning when
  the list is nonempty, stating that reused inbound carrier barcodes were assigned distinct DSP
  journey IDs; do not emit one warning per occurrence. Exact mappings remain available in JSON.

### Mutation sequence

For each retained mapped message:

1. map and filter the message exactly as before;
2. create the retained manifest, including any `withItems(...)` projection;
3. determine that source ID's next occurrence without changing any downstream collection;
4. for a later occurrence, generate and reserve a collision-free ID, create the replacement
   manifest, and append its audit entry;
5. add the final manifest and existing logical/prepared-line data in their current deterministic
   order;
6. after all messages are processed, construct `InboundToteManifestCatalog` unchanged so it remains
   the final assertion that every simulation journey ID is unique.

Validation or generation failure must occur before the affected manifest is published. Assembly is
already an all-or-nothing return operation, so no rollback API is required.

### Decision-complete test contract

Replace the existing `DspDatasetAssemblerTest` expectation that duplicate physical IDs are rejected
with focused coverage proving:

- two retained manifests with one source barcode produce the unchanged first ID and
  `dsp-reused-<source-id>-2` for the second, while retaining their exact input order, distinct sheet
  identities, service centres, items, and source sequence numbers;
- a third occurrence receives suffix `-3`, and the report contains exactly two ordered audit
  entries with source ID, replacement ID, occurrence, and sequence matching those manifests;
- a source input already equal to a would-be generated base forces deterministic `-1` collision
  resolution without changing which occurrence keeps each source ID;
- MANUAL, EMPTY, and omitted-after-line-filter messages do not consume occurrences or create audit
  entries;
- unique retained source IDs are unchanged and produce an empty substitution list;
- the existing manifest catalog still rejects duplicate IDs when constructed directly, proving
  uniqueness was normalized at assembly rather than weakened downstream.

Extend `DspFullDayInputLoaderTest` through the public ordered-directory loading boundary with two
retained JSON files sharing a transport-container barcode. Assert ascending filename order chooses
the unchanged first occurrence, executable `LoadedDspData` preserves both distinct normalized
manifests, bag planning/load plans use the corresponding distinct IDs, and the load report survives
the unresolved-product projection unchanged.

Extend `DspFullDayAnalysisScenarioTest` with a bounded supported fixture containing two otherwise
independent inbound journeys that share a source barcode and use distinct order sheets. Prove public
runtime construction no longer throws a duplicate-ID error, both normalized journeys pass through
existing lifecycle/runtime ownership, neither is collapsed, and the run reaches its expected
supported terminal result. Assert the substitution is informational rather than unsupported work.

Extend `DspFullDayReportTestSupport` with one reusable report fixture whose loaded input contains a
substitution. Use it in `DspFullDayReportJsonWriterTest` to prove the JSON load array preserves the
exact ordered mapping, in `DspFullDayReportFactoryTest` to prove one summary warning is present only
when substitutions exist, and in `DspFullDayInspectionFormatterTest` to prove the text load line
exposes the count. The existing unique-input report proves the empty-array/no-warning/zero-count
behavior. Existing tests using the four-argument `DspDatasetLoadReport` constructor must continue to
compile without broad fixture rewrites.

The tests must catch an implementation that silently drops a later manifest, renames the first
occurrence, depends on unordered-map iteration, generates an ID colliding with a later source ID,
normalizes only the report but not downstream load plans, treats reuse as unsupported work, or
weakens a downstream duplicate guard.

### Expected output

Production input may reuse a transport-container barcode across separate retained 12N messages.
Every retained message still becomes a uniquely identified DSP journey, existing runtime
uniqueness contracts remain intact, and reports retain a deterministic mapping back to the source
carrier barcode without modeling physical-carrier reuse.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.*
```

### User verification

No additional user verification is required for this step. Step 21 owns the post-change focused
regression, complete suite, and repeated external-data run.

Proposed commit message: `Normalize reused inbound tote identifiers`

## Step 13: Bound Startup OSR Preload And Rate-Limit Its Overflow

This is a formal plan amendment added after the next external-data attempt. The production day has
1,315 physical manifests for configured startup service centres `104` and `108`, while the OSR
capacity is 1,200. The existing bootstrap fails before simulation because it assumes every manifest
for those centres is already resident at 06:00. The revised model keeps the first capacity-bounded
subset resident and treats the deterministic remainder as already-authorized startup supply waiting
upstream. No manifest is omitted, and OSR capacity is never relaxed.

This amendment changes the earlier OSR-plan assumption that an over-capacity startup selection must
fail atomically. It does not reinterpret the configured 1,200 capacity, increase capacity to fit one
dataset, or authorize later service centres early. Startup centres remain logically authorized at
elapsed zero; only their excess physical manifests arrive after startup.

### Required reading for this step

- `docs/scheduler/dsp-osr-physical-inventory-plan.md`, especially its startup ordering, atomic
  inventory mutation, physical identity, and EMPTY authorization contracts;
- `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`, especially startup-centre state,
  ADAPTED-first ordering, due-time, capacity-block, recovery, and snapshot-count contracts;
- `OsrInventoryBootstrapFactory`, `OsrBootstrapState`, `OsrPhysicalInventory`, and
  `OsrInventorySnapshot`;
- `DspServiceCentreSupplyPlanFactory`, `DspServiceCentreSupplyPlan`,
  `ServiceCentreSupplyBatch`, `DspServiceCentreSupplyCoordinator`,
  `ServiceCentreAuthorizationState`, `PhysicalToteSupplyState`, and their snapshot records;
- the OSR/supply construction in `DspFullDayAnalysisRuntimeFactory` and the exact focused tests
  named below.

### Required change surface

Modify production in:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/osr/OsrBootstrapState.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/osr/OsrInventoryBootstrapFactory.java`;
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/supply/DspServiceCentreSupplyCoordinator.java`.

Modify focused coverage in:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/osr/OsrBootstrapStateTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/osr/OsrInventoryBootstrapFactoryTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/osr/DspOsrPhysicalInventoryScenarioTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/supply/DspServiceCentreSupplyCoordinatorBootstrapTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/supply/DspRateLimitedInboundSupplyTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/supply/DspServiceCentreSupplyFlowTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisScenarioTest.java`.

Do not modify `OsrInventoryConfig` or its production baseline; `OsrPhysicalInventory` capacity or
admission rules; `DspServiceCentreSupplyPlanFactory` ordering; `ServiceCentreSupplyBatch` or
`preloadedAtStart` meaning configured startup-service-centre membership; either supply-state enum;
low-water authorization for non-startup centres; operational release, lifecycle, scheduler,
routing, stations, P2P, metrics, or report schemas. Do not drop, defer as unsupported, or fabricate
completion for startup overflow.

### Behavioral specification

#### Capacity-bounded bootstrap

- Extend `OsrBootstrapState` by appending an ordered
  `List<PhysicalToteId> startupOverflowPhysicalToteIds` component. Its canonical constructor must
  reject a null list, null elements, and duplicate IDs, and must store an immutable defensive copy.
  Keep the existing two-argument constructor as a delegating overload that supplies `List.of()` so
  existing complete-bootstrap callers remain source-compatible.
- `OsrInventoryBootstrapFactory` must continue selecting every retained manifest whose service
  centre occurs in `OsrInventoryConfig.preloadServiceCentreIds()`, preserving assembled
  `LoadedDspData.inboundToteManifests()` order across service centres. Validate the complete selected
  sequence, including distinct non-null physical IDs, before creating or mutating inventory.
- Let `initialCount = min(selectedCount, config.capacity())`. Store exactly the selected prefix
  `[0, initialCount)` through one existing atomic `OsrPhysicalInventory.storeAll(...)` call. Return
  the physical IDs of the selected suffix `[initialCount, selectedCount)` as the bootstrap state's
  overflow list in unchanged assembled-data order. An oversized selection is valid and must not
  throw merely because `selectedCount > capacity`.
- If the selected count fits, behavior remains unchanged and the overflow list is empty. If no slot
  exists after the bounded prefix, the inventory is exactly full; no overflow manifest is inserted
  until the supply coordinator observes capacity through normal simulation progress.
- Continue authorizing every EMPTY sheet for every configured startup centre at elapsed zero,
  independently of which physical manifests fit. EMPTY work consumes no inventory slot.

#### Coordinator bootstrap validation and provenance

- During `DspServiceCentreSupplyCoordinator` construction, resolve every overflow ID to exactly one
  manifest in a `preloadedAtStart` supply batch. Before publishing coordinator state, reject an
  overflow ID that is unknown, belongs to a non-startup batch, is currently stored or departed, or
  is duplicated; reject a startup-batch manifest that is neither currently stored nor listed as
  overflow. Preserve the existing rejection of stored inventory outside startup batches, departed
  bootstrap history, duplicate plan IDs, and mismatched startup EMPTY authorization.
- Add a private immutable-membership set of the IDs actually resident at bootstrap, named
  `initiallyPreloadedPhysicalToteIds` or a mechanically equivalent local name. This set is permanent
  provenance: an initially resident tote remains counted as preloaded after departure. A startup
  overflow tote admitted later is never reclassified as initially preloaded merely because its
  batch has `preloadedAtStart == true`.
- For each initially resident physical manifest, initialize `PRELOADED_IN_OSR`. For each startup
  overflow manifest, initialize `AUTHORIZED_WAITING`. For each non-startup manifest, retain
  `HELD_UPSTREAM`.
- Every startup batch remains authorized at `Duration.ZERO`, and all of its EMPTY keys are
  authorized immediately. A startup batch with overflow has internal authorization state
  `AUTHORIZED`; a startup batch without overflow retains `PRELOADED`. Only one overflowing startup
  batch is physically active at a time.
- Select the first active startup-overflow batch from existing `plan.batches()` order. When it is
  exhausted, select the next startup batch with pending overflow in that same order. Within each
  batch, use its existing `physicalManifests()` order and skip initially stored IDs; this preserves
  the established ADAPTED-first, then source-sequence ordering for physical arrivals. The bootstrap
  prefix alone preserves assembled-data order; do not reorder that already-resident prefix.

#### Timed overflow admission and continuation

- Schedule the first pending overflow manifest for one full positive arrival-policy interval after
  elapsed zero. For each subsequent startup-overflow batch, schedule its first pending manifest one
  full interval after the elapsed time at which the preceding active batch was exhausted. Do not
  admit overflow in the constructor or at elapsed zero.
- Feed startup overflow through the existing `admitDuePhysicalManifests(...)` path and
  `OsrPhysicalInventory.store(...)`. A due head against a full OSR becomes
  `BLOCKED_BY_OSR_CAPACITY`; before its due time it remains `AUTHORIZED_WAITING`.
- Preserve existing recovery semantics: after capacity becomes available, admit exactly the
  blocked head at the current elapsed time, schedule the following pending manifest one full
  interval later, and stop admissions for that advance. Skipped clock updates may catch up only
  arrivals that were never capacity-blocked. Never burst accumulated blocked startup overflow.
- `admittedAfterStartupCount` and arrival-policy ordinal include startup overflow admissions as well
  as later-centre admissions. They exclude the bounded bootstrap prefix.
- Once all overflow for an active startup batch has entered OSR or departed, return its internal
  state to `PRELOADED`, clear its active timing, and activate the next startup-overflow batch as
  above. Preserve the existing effective-state rule: a startup batch reports `SUPPLY_COMPLETE` only
  when none of its physical manifests waits upstream and every one has departed the OSR.
- Only after all startup overflow is exhausted may the existing low-water rule authorize the first
  `HELD_UPSTREAM` non-startup service centre. That authorization still occurs on a later coordinator
  advance, in existing plan order, at occupancy less than or equal to the configured low-water
  mark. Startup overflow itself is already authorized and must not wait for low water.

#### Snapshot accounting

- Derive `PhysicalToteSupplySnapshot` stored-state classification from permanent initial-preload
  provenance, not from the batch-wide `preloadedAtStart` flag. Initially resident IDs report
  `PRELOADED_IN_OSR` while stored; admitted startup-overflow IDs report `STORED_IN_OSR`; either may
  later report `DEPARTED_FROM_OSR`.
- Per service centre, `preloadedCount` is the number of IDs in
  `initiallyPreloadedPhysicalToteIds`; `admittedCount` is the number of non-initial IDs that have
  reached `STORED_IN_OSR` or `DEPARTED_FROM_OSR`; and `upstreamWaitingCount` includes
  `HELD_UPSTREAM`, `AUTHORIZED_WAITING`, and `BLOCKED_BY_OSR_CAPACITY`. These counts must partition
  the batch total without double counting.
- Global admitted-after-startup count includes admitted startup overflow and remains consistent
  with the sum of per-centre admitted counts. Existing report and metrics consumers receive the
  corrected values through unchanged snapshot APIs.

### Initialization and mutation sequence

1. The bootstrap factory validates the full startup-eligible sequence and computes immutable prefix
   and suffix values without mutation.
2. The mutation boundary is the one atomic `storeAll(...)` of the bounded prefix; then the factory
   publishes inventory, EMPTY authorization, and ordered overflow IDs together.
3. The coordinator first validates the complete inventory/overflow/plan partition and EMPTY set
   without mutating inventory.
4. It records permanent initial-preload provenance and initializes all authorization and physical
   states, then schedules only the first startup-overflow batch.
5. During simulation, every overflow admission rechecks live inventory capacity and mutates only
   through `store(...)`; failed or blocked admission leaves inventory membership and queue order
   unchanged.
6. Batch transition updates coordinator state and due time only after the preceding batch has no
   pending manifest. Non-startup low-water authorization remains last.

### Decision-complete test contract

Extend `OsrBootstrapStateTest` to prove ordered overflow IDs are defensively copied and immutable,
the two-argument constructor produces an empty list, and null/duplicate overflow IDs are rejected.

Replace `OsrInventoryBootstrapFactoryTest.shouldFailClearlyWhenInitialPreloadExceedsCapacity` with
coverage proving an over-capacity selection stores exactly the first capacity-sized prefix in
assembled-data order, returns every remaining ID in exact suffix order, retains all startup EMPTY
authorization, and does not put an overflow ID in inventory/history. Retain the existing fitting,
multi-manifest, order, EMPTY, and later-centre tests.

Replace `DspOsrPhysicalInventoryScenarioTest.shouldRejectAnOverCapacityInitialDatasetAtomically`
with an assembled-data scenario proving bounded occupancy, exact prefix/suffix partition, no lost
manifest, and unchanged per-physical-ID identity. This test must fail an implementation that simply
raises capacity or silently truncates the dataset.

Extend `DspServiceCentreSupplyCoordinatorBootstrapTest` with a two-startup-centre fixture whose
bootstrap prefix cuts through one centre. Assert all startup EMPTY keys and authorization times are
present at zero, only prefix IDs are `PRELOADED_IN_OSR`, overflow IDs are
`AUTHORIZED_WAITING`, the first overflowing batch in supply-plan order is active, and initial
preloaded/admitted/waiting counts partition each batch. Add representative negative cases for an
unknown overflow ID, an overflow ID in a non-startup batch, and an omitted startup manifest. These
three cases are sufficient for cross-boundary validation; retain existing inventory and EMPTY
mismatch coverage.

Extend `DspRateLimitedInboundSupplyTest` to prove the first startup overflow is not admitted at zero
or before its due time, becomes capacity-blocked when due against a full OSR, and admits exactly once
after one explicit `recordDeparture(...)`. Assert the next overflow receives a full interval from
recovery and cannot burst in the same advance. Prove the admitted ordinal/count includes overflow,
and an admitted startup-overflow tote reports `STORED_IN_OSR` rather than
`PRELOADED_IN_OSR`.

Extend `DspServiceCentreSupplyFlowTest` with overflow in two startup batches followed by one normal
later batch. Prove startup batches drain one at a time in existing plan order and existing
ADAPTED-first physical order, both were authorized at zero, no occupancy exceeds capacity, and the
later batch cannot authorize until all startup overflow has entered and the low-water boundary is
subsequently met. Prove blocked recovery does not reorder or burst.

Extend `DspFullDayAnalysisScenarioTest` through public runtime construction with a bounded supported
fixture containing more startup physical manifests than a deliberately small OSR capacity. Prove
construction no longer throws, initial occupancy equals capacity, every excess manifest is visible
as upstream startup overflow, all manifests retain lifecycle/runtime ownership, and the scenario
reaches its expected supported terminal result after capacity is released. The scenario must catch
an implementation that fixes only the isolated factory but leaves coordinator bootstrap rejecting
partial startup residence.

The combined tests must also preserve the empty-overflow compatibility path and catch an
implementation that admits overflow eagerly, waits for low water before processing startup
overflow, admits two startup batches concurrently, misclassifies all startup-centre totes as
preloaded, loses an EMPTY authorization, bypasses `OsrPhysicalInventory.store(...)`, or allows a
later centre to overtake startup overflow.

### Expected output

A full production day may contain more startup-centre physical manifests than the OSR can hold at
06:00. Runtime construction fills the OSR with the deterministic first 1,200, exposes the remaining
115 as authorized upstream work, and admits them at the configured physical arrival rate as OSR
capacity becomes available. All 1,315 manifests remain represented and the OSR never exceeds its
configured capacity.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapStateTest --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryBootstrapFactoryTest --tests online.davisfamily.warehouse.sim.dsp.osr.DspOsrPhysicalInventoryScenarioTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyCoordinatorBootstrapTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspRateLimitedInboundSupplyTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyFlowTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest
```

### User verification

No additional user verification is required for this step. Step 21 owns the post-change focused
regression, complete suite, and repeated external-data run.

Proposed commit message: `Rate limit startup OSR overflow`

## Step 14: Add Strict JSON-Backed Full-Day Invocation

This formal amendment was added after the first Step 13 external-data run. It makes a complete
full-day invocation maintainable without removing or weakening the existing command-line contract.
It is command configuration only: it does not move profile ownership, configure service-centre
priorities, or change input ordering, runtime behavior, metrics, reporting, or simulation state.

### Required reading for this step

- the complete effective command and JSON contracts under Report And Inspection Contract;
- `DspFullDayAnalysisCommandParser`, `DspFullDayAnalysisCommand`,
  `DspFullDayAnalysisMain`, and `DspFullDayAnalysisCommandTest`;
- `JsonLoaderSupport` only as the existing strict-Jackson analogue. Reuse its simple Jackson
  binding approach, but do not reuse the 12N model or loader because full-day configuration has
  different path resolution and merge semantics.

### Required change surface

Create package-private production types in `online.davisfamily.warehouse.sim.dsp.analysis`:

- `DspFullDayAnalysisConfigJson`, the nullable raw Jackson binding record using `String`,
  `List<String>`, boxed numeric/boolean values, and `BigDecimal inboundIntervalSeconds` so absence
  remains distinguishable from zero or false;
- `DspFullDayAnalysisConfigLoader`, the sole JSON read/bind boundary.

Modify only:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisCommandParser.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisCommandTest.java`.

Create `docs/scheduler/dsp-full-day-analysis-config.example.json` as one valid directory-mode
configuration with non-sensitive placeholder paths and every Step 14-supported property other than
the mutually exclusive `orders` alternative. Do not modify `DspFullDayAnalysisCommand`, main,
input loader, profile, runner, report/inspection types, Gradle task, runtime, scheduler, or domain
code. Step 15 owns the `progressLog` and `progressIntervalSeconds` properties shown in the final
contract; the Step 14 binding and example omit those two properties until Step 15 implements them.

### Behavioral specification

- Add singleton `--config=<json path>`. First scan the command line only far enough to validate and
  locate that option; reject a duplicate, malformed, blank, nonexistent, or non-regular config path.
  Its position must not affect the merged result.
- `DspFullDayAnalysisConfigLoader` uses a private Jackson `ObjectMapper` configured to reject unknown
  properties and duplicate JSON keys. It reads the complete UTF-8 regular file and returns only the
  raw immutable binding record. A syntax, binding, duplicate-key, or I/O failure becomes an
  `IllegalArgumentException` that identifies `--config` and retains the original cause.
- Reject explicit JSON null for every present property, blank path/date strings, null/blank `orders`
  entries, an empty `orders` array, both JSON order-input modes, and wrong JSON scalar/array types.
  Use the existing parser's integer, decimal-seconds, date, path-kind, output-kind, and directory
  expansion semantics after merging; do not create a second weaker validation path in the loader.
- Resolve every configured path against the normalized absolute parent directory of the config file
  when it is relative. Preserve an absolute configured path unchanged apart from normal
  normalization. Do not apply config-relative resolution to command-line overrides.
- Parse the remaining command-line options with the existing duplicate and repeated-option rules.
  Merge by property: a command-line singleton replaces its configured value. `--overwrite` forces
  true. When any command-line order-input option is present, discard both configured order fields
  before applying the command-line order mode; reject command-line use of both modes as today.
- Apply the existing defaults for fixed step, steps per batch, metric sample interval, and overwrite
  only after merging. Then perform the existing required-field, exactly-one-order-mode, file,
  directory, output, and numeric validation once and construct the unchanged
  `DspFullDayAnalysisCommand`.
- Preserve byte-for-byte-equivalent effective command values between a CLI-only invocation, a
  config-only invocation, and a mixed invocation that supplies the same values. Configuration
  source/path metadata does not enter the command, profile, runtime, metrics, or final report.

Private raw-option representation, helper decomposition, and exact error wording remain
discretionary provided each failure identifies whether it came from `--config` or a named effective
option and no partially validated command is returned.

### Decision-complete test contract

Extend `DspFullDayAnalysisCommandTest` at the parser boundary to prove:

- config-only input produces the same `DspFullDayAnalysisCommand` as the equivalent CLI-only input,
  including directory expansion order and existing defaults;
- a config path may occur first, middle, or last without changing the result;
- relative product, order-directory, output, and inspection paths resolve from the config parent,
  while a relative CLI output override remains relative to the process working directory;
- CLI singleton overrides win, `--overwrite` forces a configured false value to true, and repeated
  CLI `--orders` replaces rather than appends to configured directory or explicit-file input;
- configured explicit `orders` retain exact array order and command-line directory mode can replace
  them; one representative configured directory is expanded with the exact Step 9 rules;
- separately reject duplicate/malformed/missing/non-file config paths, malformed JSON, duplicate
  JSON keys, unknown properties, explicit null, wrong types, blank/empty values, both configured
  order modes, absent effective required values, and invalid effective numeric/date/path values;
- one representative invalid config invocation through `DspFullDayAnalysisMain.run` exits nonzero
  without creating JSON or inspection output, proving main continues to enforce parser failure even
  though main itself is unchanged.

The tests must catch an implementation that silently ignores misspelled properties, accepts JSON
duplicate keys, resolves paths from the process directory, appends two order sources, applies a
default before an override, weakens current validation, or makes `--config` position-sensitive.

### Expected output

A complete full-day run can be maintained as one strict JSON document and invoked with one config
argument, while existing CLI automation and deterministic input ordering remain compatible.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest
```

### User verification

No additional user verification is required for this step. Step 21 owns regression and the
config-driven external-data run.

Proposed commit message: `Accept full-day analysis configuration`

## Step 15: Add Compact Persistent Full-Day Progress Logging

This formal amendment addresses the same external run's observability failure. Routine inspection
currently builds and emits every unfinished logical and physical identity as one very long line;
terminal scrollback retained only the tail of that line, which looked like service-centre progress
but represented no processing event. This step adds a lightweight progress projection and a
durable, flushed log without weakening the detailed final JSON/text evidence.

### Required reading for this step

- the complete Report And Inspection Contract and Step 14 effective configuration rules;
- `DspFullDayAnalysisRunner`, `DspFullDayAnalysisMain`, `DspFullDayAnalysisCommandParser`,
  `DspFullDayAnalysisCommand`, `DspFullDayReportFactory`, `DspFullDayInspectionSnapshot`, and
  `DspFullDayInspectionFormatter`;
- `DspFullDayAnalysisRunnerTest`, `DspFullDayAnalysisCommandTest`,
  `DspFullDayInspectionFormatterTest`, and `DspFullDayReportJsonWriterTest`.

### Required change surface

Create production types:

- `online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressSnapshot`;
- `online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatter`;
- package-private `online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayProgressOutput`.

Create focused tests:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayProgressFormatterTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayProgressOutputTest.java`.

Modify production only in:

- `DspFullDayAnalysisConfigJson` and `DspFullDayAnalysisConfigLoader`;
- `DspFullDayAnalysisCommandParser` and `DspFullDayAnalysisCommand`;
- `DspFullDayAnalysisMain` and `DspFullDayAnalysisRunner`.

Modify focused coverage only in `DspFullDayAnalysisRunnerTest`,
`DspFullDayAnalysisCommandTest`, and, only where an assertion currently assumes routine full-detail
console output, `DspFullDayInspectionFormatterTest`. Update the Step 14 example JSON with
`progressLog` and `progressIntervalSeconds`.

Do not modify `DspFullDayReportFactory`, `DspFullDayInspectionSnapshot`, the detailed inspection
formatter's public/default output, final report schemas/writer, metrics collection, fixed-step
driver, Gradle task, runtime/controllers, scheduler, lifecycle, routing, stations, P2P, or domain
behavior. Do not add SLF4J/Log4j, an asynchronous logger, a background heartbeat thread, rotation,
retention, renderer logging, or per-tote event logging.

### Behavioral specification

#### Command and configuration

- Append `Optional<Path> progressLogPath` and `Duration progressInterval` to
  `DspFullDayAnalysisCommand`. Keep the existing canonical validation style; both values are
  required non-null, and the interval must be positive. Add no compatibility constructor because
  this package-private record's callers are owned by the command tests and main path.
- Add CLI options `--progress-log=<text path>` and
  `--progress-interval-seconds=<positive integer>`. Extend strict JSON binding with optional
  `progressLog` string and boxed integer `progressIntervalSeconds`. Apply the same source precedence,
  path resolution, validation, and default-after-merge rules from Step 14. Default the interval to
  300 simulated seconds and the log path to empty.
- Require normalized report, inspection, and progress-log paths to be pairwise distinct. A progress
  path must pass the existing output-kind/parent validation. Existing-file handling remains the
  single `--overwrite` policy shared by all three outputs.

#### Lightweight progress projection

- `DspFullDayProgressSnapshot` is an immutable record containing exactly
  `DspFullDayAnalysisRuntimeSnapshot runtime`, `String profileId`,
  `String calibrationStatus`, `DspCompletionMilestone completionMilestone`, and
  `DspDatasetLoadReport loadReport`. Its constructor validates non-null values and profile identity
  equality with runtime metrics and defensively relies only on those immutable snapshots.
- Add a static `from(DspFullDayAnalysisRuntimeSnapshot, DspFullDayLoadedInput,
  DspUncalibratedFullDayProfile)` factory on that record. It must not call
  `DspFullDayReportFactory.createInspectionSnapshot(...)`, scan scheduler order states or lifecycle
  tote records, derive unfinished identity strings, or retain the loaded dataset/bag plan.
- `DspFullDayProgressFormatter.describe(DspFullDayProgressSnapshot)` returns deterministic lines in
  this exact section order: run/profile/calibration/milestone and state; clock and speed; OSR
  occupancy/capacity/low-water/net flow and aggregate rates; one service-centre line in priority-
  descending then ID order; one P2P line in configured metrics order; release; transport; station;
  load counts; unsupported-work count; and total remaining sheet/tote/pack/bag counts.
- Each service-centre line contains ID, priority, supply state, unfinished sheet/tote/pack/bag
  counts, required/desired/owned/unmet line counts, completion time/outcome, and every block
  category's blocked-unit count plus latest reason. It must not contain service-centre or global
  unfinished identity strings. Routine formatting cost is bounded by service-centre/P2P/block
  category counts rather than order/tote count.
- Preserve `DspFullDayInspectionFormatter.describe(...)` as the detailed final formatter. Final JSON
  and optional `--inspection-output` continue to contain exact unfinished identities. The runner no
  longer uses the detailed formatter for routine console/progress milestones.

#### Persistent output and runner lifecycle

- `DspFullDayProgressOutput.open(PrintStream console, Optional<Path> progressLogPath,
  boolean overwrite)` validates non-null arguments, creates only the configured parent directory,
  and opens an optional UTF-8 file with `CREATE_NEW` when overwrite is false or
  `CREATE`/`TRUNCATE_EXISTING` when true. Opening failure occurs before runtime construction. It
  never closes the caller-owned console stream. Provide one package-private constructor accepting
  the console and an owned optional `Writer` solely so focused tests can inject deterministic
  write/flush/close failures; production construction must use `open(...)`.
- `print(String milestone, List<String> lines)` writes the same header
  `[dsp-full-day:<milestone>]` and lines to the console and optional file in that order, then flushes
  both before returning. It rejects blank milestones/null lines and surfaces file write/flush
  failures; it does not suppress them through `PrintWriter.checkError()` semantics. `close()` is
  idempotent, flushes and closes only the optional file, and preserves already-written content after
  normal failure or external process termination.
- Extend the runner's primary `run(...)` with progress-log path and interval. Preserve existing
  overloads by delegating with no log and the 300-second default. Open
  `DspFullDayProgressOutput` before `runtimeFactory.create(...)`, and close it after runtime closure
  in one outer `finally`/try-with-resources boundary. If runtime construction or execution throws
  after the output opens, write one `[dsp-full-day:failure]` block containing exception class and
  sanitized message, flush it, then rethrow the original failure; attach a logging failure as
  suppressed rather than replacing the simulation failure.
- Emit a start block after runtime construction. During fixed-step callbacks, emit one progress
  block on the first completed fixed step at or beyond each configured simulated-time threshold;
  advance the threshold before continuing so a step cannot duplicate a boundary. Continue emitting
  one completion block when each centre first becomes complete. Emit one final compact block after
  terminal metrics are finalized and before detailed JSON/text writing. A completion and interval
  boundary on the same step may produce both differently labelled blocks.
- Progress milestones use `progress=<ISO-8601 elapsed duration>` and
  `completion=<service-centre-id>`. All progress generation and I/O stays on the calling simulation
  thread. It must not alter fixed-step size/count, simulation time, controller ordering, scheduler
  decisions, cutoff behavior, deterministic report values, or terminal detection. Logging wall time
  remains part of achieved execution-speed measurement because it is real run cost.
- A final JSON or detailed-inspection write refusal/failure is logged as failure and leaves the
  flushed progress log available. With no configured progress path, console behavior remains the
  same compact milestone stream and no file is created.

Local buffering classes, private helper decomposition, and exact compact decimal formatting remain
discretionary provided each block is deterministic, line-oriented, immediately flushed, and
contains the specified facts without per-order/per-tote expansion.

### Decision-complete test contract

`DspFullDayProgressFormatterTest` constructs an immutable snapshot with two centres and two lines
and asserts exact section order, priority ordering, required fields, deterministic repeated output,
and aggregate remaining counts. Give each centre multiple fake unfinished identities in the loaded
runtime fixtures and assert no order ID, physical tote ID, `unfinishedIdentities`, or pipe-delimited
identity expansion enters progress output. Retain the detailed formatter test proving those exact
identities remain in final inspection.

`DspFullDayProgressOutputTest` proves console-only mirroring, byte-identical console/file blocks,
UTF-8, immediate visibility before close, parent creation, default existing-file refusal, explicit
overwrite/truncation, idempotent close, caller console remaining usable after close, and preserved
already-flushed content when a later write/close fails. Representative invalid milestone and path
collisions are covered at their owning output/parser boundaries rather than duplicated here.

Extend `DspFullDayAnalysisCommandTest` to cover CLI-only, config-only, and mixed progress settings;
the 300-second/no-file defaults; positive interval validation; progress path config-relative
resolution; CLI override; duplicate option/property rejection; all three pairwise output-path
collisions; and one existing-log/no-overwrite command run that exits nonzero without altering the
file or creating final outputs.

Extend `DspFullDayAnalysisRunnerTest` to prove start, configured interval, completion, final, and
failure milestone order; first-step-at-or-after interval behavior across a batch containing several
thresholds; no duplicate threshold; no post-terminal progress; flushed log evidence after an
injected mid-run failure; runtime and output closure on every path; compact console output; and
unchanged final report/detailed inspection values. The test must catch an implementation that logs
only at batch boundaries, builds detailed identity inspection for progress, buffers until normal
completion, replaces the original exception, or advances simulation to produce a log entry.

### Expected output

A long-running full-day process emits bounded, interpretable progress to the terminal and an
optional durable UTF-8 log. A forced stop or runtime failure leaves the last completed progress
block available, while final report and inspection retain exact diagnostic identities.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayProgressOutputTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest
```

### User verification

No additional user verification is required for this step. Step 21 owns regression and the
config-driven external-data run.

Proposed commit message: `Persist compact full-day progress`

## Step 16: Reuse Full-Day P2P Admission Correlation Snapshots

This formal amendment follows the first config-driven external run. The process remained responsive
and continuously CPU-bound, but did not reach the first 300-second simulated-time progress
milestone. A live thread dump placed the main thread in
`DspFullDayAnalysisRuntimeFactory.stationAdmissionResolver(...)`, rebuilding a `LinkedHashSet` from
all known bag-correlation requirements for one candidate admission. The observed day contains
35,318 planned bags and starts with 1,200 OSR-resident candidates, so reconstructing and defensively
copying the same large correlation sets per candidate creates an accidental multiplicative
allocation path.

This step removes only that evidenced hotspot. It does not claim that the simulation or 3D engine
is generally optimized. After the functional full-day path is complete, a separate broad
allocation/performance review will inspect constructor frequency, temporary collections, snapshot
cost, controller update frequency, rendering integration, and other hot paths using measured
evidence.

### Required reading for this step

- `DspFullDayAnalysisRuntimeFactory.stationAdmissionResolver(...)`;
- `P2pBagCorrelationRequirementCatalog`, `P2pBagCorrelationAssignmentRegistry`,
  `P2pBagCorrelationAssignmentSnapshot`, and `P2pAdmissionSnapshot`;
- `P2pStationAdmissionResolver`, `P2pCapacityStationAdapter`, and
  `OperationalCandidateRouteAdmissionFactory`;
- `P2pBagCorrelationRequirementCatalogFactoryTest`,
  `P2pBagCorrelationAssignmentRegistryTest`, `P2pStationAdmissionResolverTest`,
  `OperationalCandidateRouteAdmissionFactoryTest`, and `DspFullDayAnalysisRuntimeFactoryTest`.

### Required change surface

Create production type:

- package-private
  `online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayP2pAdmissionSnapshotSource`.

Create focused tests:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/p2p/bag/P2pBagCorrelationRequirementCatalogTest.java`;
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayP2pAdmissionSnapshotSourceTest.java`.

Modify production only in:

- `P2pBagCorrelationRequirementCatalog`;
- `P2pBagCorrelationAssignmentRegistry`;
- `DspFullDayAnalysisRuntimeFactory`.

Modify focused coverage only in `P2pBagCorrelationAssignmentRegistryTest` and, only if an existing
integration assertion needs strengthening, `DspFullDayAnalysisRuntimeFactoryTest`.

Do not modify `P2pAdmissionSnapshot`, `P2pStationAdmissionResolver`,
`P2pCapacityStationAdapter`, `OperationalCandidateRouteAdmissionFactory`, scheduler selection,
release ordering, assignment compatibility, fixed-step duration/count, progress interval/output,
metrics/report schemas, lifecycle, station/P2P machine behavior, renderer/3D-engine code, or domain
results. Do not introduce object pools, mutable published snapshots, global caches, asynchronous
evaluation, event-driven fast-forward, or a general optimization framework.

### Behavioral specification

- Extend `P2pBagCorrelationRequirementCatalog` with one final immutable correlation-ID set built
  from its already validated unique requirement map during catalog construction. Add
  `public Set<String> correlationIds()`. Repeated calls return the same immutable set instance and
  never traverse `allRequirements` or allocate another collection. Empty catalogs return the same
  stable empty value. Set iteration order is not a behavioral contract; admission uses membership.
- Extend `P2pBagCorrelationAssignmentRegistry` with
  `public Set<String> correlationIdsSnapshot()`. It initially returns one stable immutable empty
  set. Only when a commit contains genuinely new correlations, the returned simulation-thread
  action first performs the existing stale-state revalidation, then constructs the prospective
  immutable ID set from the current assignments plus all additions, then appends all additions,
  and finally publishes the precomputed set. Collection construction therefore occurs before the
  mutation boundary and cannot leave the map changed with a stale cache. Rejected, failed, and
  no-addition commits leave both assignment state and the cached set identity unchanged. Repeated
  reads between successful additions return the same immutable instance without rebuilding the
  full `P2pBagCorrelationAssignmentSnapshot` or map.
- Add package-private `DspFullDayP2pAdmissionSnapshotSource` with constructor
  `(int idlePrlCount, P2pBagCorrelationRequirementCatalog requirementCatalog,
  P2pBagCorrelationAssignmentRegistry assignmentRegistry)` and method
  `P2pAdmissionSnapshot snapshot()`. Validate non-null dependencies and a nonnegative PRL count.
  Preserve the existing full-day values `p2pCellId="dsp-p2p"` and
  `pcrAvailableForNewRelease=true`.
- The source captures `requirementCatalog.correlationIds()` once. On `snapshot()`, obtain the
  registry's cached correlation-ID set. Return the same cached `P2pAdmissionSnapshot` instance
  while that set identity is unchanged; construct exactly one replacement after a successful
  registry addition publishes a new set. This source is simulation-thread-owned and performs no
  synchronization.
- In `DspFullDayAnalysisRuntimeFactory.stationAdmissionResolver(...)`, construct one source for the
  runtime and pass `source::snapshot` to `P2pStationAdmissionResolver`. Remove the supplier body
  that calls `correlationAssignments.snapshot().correlationAssignments().keySet()` and streams
  `requirementCatalog.allRequirements()` for each candidate.
- Preserve candidate-specific admission outcomes and supplier freshness after every committed
  correlation assignment. The optimization changes allocation frequency only; it must not cache
  station occupancy, route-target admission, line activity, scheduler candidates, or any mutable
  machine state.

### Decision-complete test contract

`P2pBagCorrelationRequirementCatalogTest` constructs requirements shared across physical-tote and
logical-sheet indexes and proves `correlationIds()` contains every distinct correlation exactly
once, is immutable, is empty for an empty catalog, and returns the identical object on repeated
calls.

Extend `P2pBagCorrelationAssignmentRegistryTest` to prove the initial cached set is stable and
immutable; one successful addition publishes one new exact set; repeated reads and a same-line
commit containing no new correlation retain object identity; a rejected mixed-line commit leaves
the assignments and cached set value/identity unchanged; and a later genuine addition publishes a
new set containing old and new IDs.

`DspFullDayP2pAdmissionSnapshotSourceTest` uses a catalog with 5,000 distinct known correlations
and proves repeated `snapshot()` calls return the identical snapshot and set
objects; one successful registry addition causes exactly one replacement containing the current
active ID; subsequent reads and a no-addition commit reuse it; and a rejected commit cannot replace
it. Assert the cell ID, PRL count, PCR flag, and correlation membership remain identical to the
pre-optimization contract. This test must catch an implementation that merely moves the
`allRequirements` stream or assignment-snapshot/map reconstruction into another per-call helper.

Retain the existing resolver, operational route-admission, and runtime-factory tests to prove that
P2P/non-P2P delegation, candidate-specific outcomes, latest committed assignment visibility, five
line composition, completion, and cutoff behavior are unchanged. Do not add wall-clock timing
assertions or allocation-count thresholds to unit tests.

### Expected output

Full-day P2P candidate evaluation reuses immutable known/active correlation sets and one admission
snapshot between real assignment changes. The previously observed `allRequirements().stream()` /
`LinkedHashSet` construction no longer occurs per OSR candidate. Simulation decisions and reports
remain unchanged. Broader optimization remains explicitly deferred.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalogTest --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayP2pAdmissionSnapshotSourceTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolverTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionFactoryTest
```

### User verification

Stop the current CPU-bound process and rebuild the installed distribution. Use a fresh progress-log
path because `overwrite=false`. Temporarily set `progressIntervalSeconds` to `60`, start the same
external dataset, and measure from the completed `start` block to `progress=PT1M`. The PT1M block
must appear within five wall-clock minutes and a second interval block must follow without the
process becoming unresponsive or memory growing without bound. Exact throughput is evidence for
the later broad optimization review, not a calibrated performance promise.

If PT1M does not appear within five minutes, capture a fresh `jcmd <pid> Thread.print -l`, stop the
run, and do not broaden this implementation step speculatively. Amend the plan around the newly
measured hotspot. Step 21 still owns the complete-day external run, focused regression, full suite,
review, and closure.

Proposed commit message: `Reuse full-day P2P admission snapshots`

## Step 17: Aggregate Full-Day Completion Evaluation Once Per Fixed Step

The post-Step-16 external run remained responsive and CPU-bound without reaching PT1M. A 45-second
JFR profile attributed 2,779 of 3,412 main-thread execution samples to
`PhysicalToteLifecycleLedger.snapshot()`. `CompletionSnapshotSource.get()` currently invokes that
full-map copy twice for every matching inbound manifest, then invokes it again from per-centre
helpers. With 4,924 manifests this creates approximately 9,848 full lifecycle snapshots and about
48.5 million map insertions during one completion evaluation. The cutoff controller requests that
evaluation every fixed step.

This step removes the full-day completion multiplicative work as one coherent change. It does not
alter completion semantics or optimize general lifecycle ownership.

### Required reading for this step

- `DspFullDayAnalysisRuntimeFactory.CompletionSnapshotSource` and its construction site;
- `DspFullDayCutoffController`, `DspFullDayAnalysisRuntime`, and
  `DspFullDayCompletionEvaluator`;
- snapshot APIs used by the source for lifecycle, supply, OSR, AV02, outbound allocation, station
  processing, transport, P2P lines, and leases;
- `DspFullDayAnalysisRuntimeFactoryTest`, `DspFullDayCompletionEvaluatorTest`, and
  `DspFullDayAnalysisScenarioTest`.

### Required change surface

Modify production only in `DspFullDayAnalysisRuntimeFactory`. Keep
`CompletionSnapshotSource` private and simulation-thread-owned; do not introduce a second
completion owner or change `DspFullDayCutoffController` frequency/semantics.

Modify focused coverage only in `DspFullDayAnalysisRuntimeFactoryTest` and
`DspFullDayAnalysisScenarioTest`.

Do not modify lifecycle, supply, OSR, AV02, outbound, station, transport, P2P, metrics, report, or
progress types in this step. Do not throttle completion checks, skip fixed steps, add mutable global
caches, or change the conservative unknown-owner behavior.

### Behavioral specification

- During `CompletionSnapshotSource` construction, pre-index immutable input by service-centre ID:
  inbound manifests, planned bags, unresolved-product diagnostics, and inbound physical-tote
  ownership. Preserve source order in every indexed list and publish immutable maps/lists.
- One `get()` invocation captures each changing owner at most once: lifecycle, supply, OSR, AV02,
  outbound allocation, station processing, P2P lease state, each P2P line, active routed totes,
  launch queue, outbound transport queue, and each station-arrival queue. Helpers receive those
  captured values; no helper calls an owner `snapshot()` method.
- Build dynamic physical-tote ownership once from the immutable inbound ownership plus captured
  AV02, outbound, and active-route state. Preserve the current overwrite order and exact owner
  result.
- Aggregate lifecycle, allocated bags/packs, station claims/dispositions, transport envelopes,
  tipper input, live P2P assignments, open outbound totes, and completed-but-unallocated bags by
  service centre in one pass per captured collection. Do not rescan a complete dynamic collection
  separately for each service centre.
- Preserve the existing conservative rule that an unknown physical-tote owner contributes to every
  service centre for the affected transport/live-work count. Preserve the current rule that an
  unowned P2P line's input contributes to every service centre.
- Reuse pre-indexed planned-bag and unsupported-work lists. Remaining bag and pack counts may scan
  only the selected centre's pre-indexed bag list and the one captured allocated-bag-key set.
- Return a fresh immutable list of completion snapshots for each evaluation because completion
  values and first-completion timestamps can change. Reuse only stable indexes and the immutable
  snapshots captured for that evaluation.
- Preserve every observation field, completion outcome, deterministic service-centre order, first
  completion timestamp, hard-cutoff behavior, and report/progress value.

### Decision-complete test contract

Extend `DspFullDayAnalysisRuntimeFactoryTest` with a multi-centre fixture containing inbound,
terminal, outbound, and unknown-owner work. Prove that one fixed-step completion evaluation retains
the exact per-centre counts and conservative attribution rules before and after state transitions.
The test must cover an unowned line and an unknown transport owner so a one-pass implementation
cannot silently narrow the prior semantics.

Extend `DspFullDayAnalysisScenarioTest` to retain early completion and hard-cutoff behavior with the
same outcomes and deterministic service-centre order. Do not add wall-clock assertions.

### Expected output

One completion evaluation performs one lifecycle snapshot and bounded one-pass aggregation rather
than thousands of complete snapshot copies and repeated whole-day scans. Completion behavior and
all observable values remain unchanged.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluatorTest
```

### User verification

Rebuild the installed distribution, use a fresh progress-log path, set
`progressIntervalSeconds` to `60`, and start the same external day. PT1M must appear within five
wall-clock minutes. Capture a new 45-second JFR profile after the start block. Confirm
`PhysicalToteLifecycleLedger.snapshot()` and `PhysicalToteLifecycleSnapshot.<init>` are no longer
dominant and that `CompletionSnapshotSource` performs no per-manifest snapshot construction. Stop
the run after collecting this checkpoint; Step 20 owns the final performance gate.

Proposed commit message: `Aggregate full-day completion snapshots`

## Step 18: Publish Indexed, Change-Bounded OSR Inventory Snapshots

The same JFR profile attributed 5.3% of main-thread samples to linear
`OsrInventorySnapshot.findStored(...)` scans while the much larger completion cost was still
present. `DspServiceCentreSupplyCoordinator.snapshot()` checks stored/departed membership for every
manifest, and `OsrPhysicalInventory.snapshot()` currently reconstructs equivalent immutable state
on every read.

### Required reading for this step

- `OsrPhysicalInventory`, `OsrInventorySnapshot`, and all their direct production callers;
- `DspServiceCentreSupplyCoordinator.snapshot()` and `physicalToteSnapshot(...)`;
- `OsrInventorySnapshotTest`, `OsrPhysicalInventoryTest`,
  `DspServiceCentreSupplyCoordinatorBootstrapTest`, and
  `DspRateLimitedServiceCentreSupplyScenarioTest`.

### Required change surface

Modify production only in `OsrInventorySnapshot` and `OsrPhysicalInventory`.

Modify focused coverage only in `OsrInventorySnapshotTest`, `OsrPhysicalInventoryTest`,
`DspServiceCentreSupplyCoordinatorBootstrapTest`, and, only if existing integration assertions
need strengthening, `DspRateLimitedServiceCentreSupplyScenarioTest`.

Do not modify supply admission/rate behavior, OSR capacity, lifecycle transitions, release command
semantics, preload/overflow behavior, or caller APIs.

### Behavioral specification

- Convert `OsrInventorySnapshot` from a record to a final immutable class while preserving the
  public three-argument constructor, `capacity()`, `storedTotes()`, `departedTotes()`, every existing
  derived method, and value-based `equals`, `hashCode`, and `toString` semantics.
- During construction, validate and copy the ordered stored/departed lists once. Build immutable
  stored-manifest-by-ID and departed-ID indexes once. `contains`, `findStored`, and `hasDeparted`
  use those indexes without streams, list traversal, or per-call collections.
- Build immutable stored-tote groupings by order sheet and service centre and immutable occupancy
  maps by service centre and order type during construction. Repeated derived reads return the same
  immutable list/map instances; missing groups return a stable immutable empty list.
- `OsrPhysicalInventory` owns one current immutable snapshot. Construct the initial empty snapshot
  once. After complete validation, a successful non-empty `storeAll` mutation publishes exactly one
  replacement snapshot; a successful departure publishes exactly one replacement. Rejected
  operations and empty `storeAll` retain the existing snapshot identity.
- Existing snapshots remain point-in-time immutable after later inventory mutations. Mutation
  ordering, capacity checks, seen-ID protection, stored/departed order, and exceptions remain
  unchanged. No synchronization is added because the inventory remains simulation-thread-owned.

### Decision-complete test contract

Extend `OsrInventorySnapshotTest` to prove indexed lookup/grouping values, deterministic ordering,
immutability, and stable object identity for repeated stored/departed/group/occupancy reads. Prove
value equality/hash behavior between independently constructed equivalent snapshots.

Extend `OsrPhysicalInventoryTest` to prove repeated `snapshot()` calls return the identical object;
one successful store/departure publishes one replacement; old snapshots remain unchanged; and
empty/rejected operations retain identity. Existing capacity, duplicate, order, and readmission
tests remain green.

Retain the focused supply tests to prove indexed snapshots do not alter startup prefix/overflow,
authorization, rate limiting, or supply-state values.

### Expected output

OSR state is copied and indexed only after genuine inventory changes. Fixed-step consumers reuse
one immutable snapshot and perform constant-time physical-tote membership lookups.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshotTest --tests online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventoryTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyCoordinatorBootstrapTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspRateLimitedServiceCentreSupplyScenarioTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandlerTest
```

### User verification

No separate external run is required after this step. Step 20's profile gate must confirm
`OsrInventorySnapshot.findStored`, `contains`, and `hasDeparted` no longer appear as material linear
scan sites.

Proposed commit message: `Index and reuse OSR inventory snapshots`

## Step 19: Reuse Immutable P2P Workload Plan Indexes

The JFR profile attributed about 3% of main-thread samples to
`P2pWorkloadSnapshotFactory`, principally `indexPlannedBags(...)`, while the dominant completion
cost was still present. Lease retention evaluates every fixed step, but the full-day bag plan and
manifest catalog are immutable; rebuilding and revalidating indexes over 35,318 bags and 166,930
pack traces on every evaluation is not dynamic workload calculation.

### Required reading for this step

- `P2pWorkloadSnapshotFactory`, `P2pWorkloadSnapshot`, and
  `P2pServiceCentreWorkloadSnapshot`;
- `DspP2pElasticAllocationRuntimeFactory`, `ElasticP2pLeaseRetentionPolicy`, and
  `P2pLeaseReleaseController`;
- `BagPlanningResult` and `InboundToteManifestCatalog`;
- `P2pWorkloadSnapshotTest`, `ElasticP2pLeaseRetentionPolicyTest`, and
  `DspP2pElasticAllocationRuntimeTest`.

### Required change surface

Create package-private production type
`online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadPlanIndex` and focused test
`P2pWorkloadPlanIndexTest` in the same package.

Modify production only in `P2pWorkloadSnapshotFactory`. Modify focused coverage only in
`P2pWorkloadSnapshotTest`, `ElasticP2pLeaseRetentionPolicyTest`, and
`DspP2pElasticAllocationRuntimeTest`.

Do not modify lease release frequency, allocation/planner rules, timetable/deadline calculations,
line ownership, workload costs, `BagPlanningResult`, manifest ownership, outbound allocation, or
the public `P2pWorkloadSnapshotFactory.create(...)` signatures.

### Behavioral specification

- `P2pWorkloadPlanIndex.from(BagPlanningResult, InboundToteManifestCatalog)` performs the current
  complete bag/pack/trace/manifest validation once and publishes immutable planned-bag-by-key,
  planned-bags-by-service-centre, and ordered service-centre-ID views. Preserve current encounter
  order and every existing validation failure.
- `P2pWorkloadSnapshotFactory` remains one simulation-thread-owned instance per elastic runtime. It
  stores the last bag-plan reference, last manifest-catalog reference, and their immutable plan
  index. A package-private `planIndexFor(...)` returns the identical index while both input
  identities are unchanged and constructs exactly one replacement when either identity changes.
  No equality scan, synchronization, static/global cache, or unbounded multi-input cache is added.
- Existing `create(...)` methods obtain the index through `planIndexFor(...)`. Dynamic validation of
  remaining physical totes, AV02/lifecycle state, allocated bags, costs, and lease-time work remains
  per evaluation.
- Remaining bags are selected from the indexed list for one service centre rather than rescanning
  all planned bags for every centre. Allocation results, service-centre order, estimates,
  diagnostics, and exception behavior remain unchanged.
- Generic callers that supply a different `BagPlanningResult` or `InboundToteManifestCatalog`
  instance receive a newly validated index on the next call; full-day callers reuse their stable
  input identities.

### Decision-complete test contract

`P2pWorkloadPlanIndexTest` builds data shared across centres and proves exact bag/trace validation,
deterministic service-centre order, immutable grouped views, and stable view identity.

Extend `P2pWorkloadSnapshotTest` with a 5,000-bag fixture and prove `planIndexFor(...)` returns the
same index for repeated identical input references, replaces it once when either input reference
changes, and retains all workload values across reuse. Existing invalid-input tests must prove a
replacement input is fully revalidated rather than accepted from the prior cache.

Retain lease-retention and elastic-runtime tests to prove fresh dynamic state still changes
allocation/retention decisions while immutable plan indexes are reused.

### Expected output

Full-day lease retention continues to evaluate dynamic work every fixed step, but immutable
bag/trace/manifest validation and grouping occur once for the stable full-day inputs.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadPlanIndexTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.ElasticP2pLeaseRetentionPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspDeadlineAwareElasticLineAllocationScenarioTest
```

### User verification

No separate external run is required after this step. Step 20's profile gate must confirm
`P2pWorkloadSnapshotFactory.indexPlannedBags` no longer occurs per fixed-step evaluation.

Proposed commit message: `Reuse immutable P2P workload indexes`

## Step 20: Profile-Guided Full-Day Performance Gate

This is a verification and planning gate, not authorization for further production changes.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Rebuild the installed distribution. Use a fresh progress-log path with
`progressIntervalSeconds=60`, start the same complete external day, and record wall-clock time from
the completed start block to PT1M and PT2M. Both milestones must appear, PT1M must occur within five
wall-clock minutes, memory must remain bounded, and terminal/log output must remain responsive.

Capture a 45-second JFR profile after the start block and retain it outside source control. Confirm:

- the Step 16 correlation-set reconstruction is absent;
- lifecycle snapshots are not constructed per manifest or per service centre;
- OSR physical-ID queries do not perform linear stored/departed-list scans;
- immutable P2P bag/trace/manifest indexes are not rebuilt per fixed step;
- no single avoidable full-day/DSP reconstruction or linear lookup dominates the sampled main
  thread or allocation profile.

Record the top ten application execution sites, top ten allocation sites, GC count/time, process
memory, and observed PT1M/PT2M wall-clock durations. Specifically assess
`DspOperationalReleaseSnapshot.findByPhysicalToteId`,
`P2pBagCorrelationAssignmentSnapshot.lineFor`, supply snapshot creation, and full-day runtime
snapshot/report projection. Their earlier samples were too small and masked to justify changing
their ownership yet.

If another avoidable site is material, stop before Step 21 and formally add one bounded,
decision-complete optimization step based on the new profile. Do not fold it into closure or the
later broad engine/rendering review. If no material site remains, proceed to Step 21.

## Step 21: Regression, External Dataset Run, Review, And Closure

Do not begin Exception Station, calibration, renderer integration, outbound dispatch, 32R, or the
deferred broad engine/simulation optimization review during closure.

### Implementation verification

No model-run verification is authorized in this step.

### User verification

Run the focused regression set:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.* --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.station.continuation.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.threedee.sim.framework.time.*
```

Then run the complete suite:

```powershell
.\gradlew test
```

An external-data run is required before marking the feature verified. Because production data is
not stored in the repository, the user supplies the actual CSV path, the order directory (or
explicit JSON paths), and explicit unknown configuration values. For a complete operating day,
create an external JSON configuration from the checked-in example, include a progress-log path and
an appropriate progress interval, and invoke the installed application with only
`--config=<json path>`. Do not commit the populated external configuration. Inspect the live
terminal, durable progress log, generated JSON, and detailed text and confirm:

- the entire supplied dataset is represented in load counts/exclusions;
- initial OSR occupancy is at most the configured capacity; for the observed 1,315 startup-eligible
  manifests and 1,200-slot configuration, the first 1,200 are initially resident and all remaining
  115 are represented as admitted-after-startup or upstream waiting rather than omitted;
- the report and inspection say `UNCALIBRATED` and `P2P_OUTPUT_CLOSED` prominently;
- the initial progress block appears in both terminal and log, later blocks remain compact and
  readable, simulated time and remaining counts advance, and no individual unfinished identity is
  expanded in routine progress;
- while the process is running, the progress file contains the latest flushed complete block; the
  final compact block and detailed JSON/text appear on normal termination;
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
- immutable known-correlation IDs are constructed once per requirement catalog, active-correlation
  IDs only after genuine assignment additions, and full-day P2P admission snapshots are reused
  between those additions rather than rebuilt per release candidate;
- one full-day completion evaluation captures each dynamic owner once, reuses immutable input
  indexes, and aggregates whole-day collections in one pass while preserving conservative unknown
  ownership and exact completion values;
- OSR inventory snapshots are immutable indexed values, retain record-compatible public behavior,
  and are replaced only after genuine inventory mutation rather than reconstructed on every read;
- immutable P2P bag/trace/manifest plan indexes are reused while input identities remain unchanged,
  while lifecycle, AV02, outbound, lease, clock, and remaining-work state remain fresh per
  allocation evaluation;
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
- strict JSON invocation rejects unknown/duplicate properties, preserves CLI compatibility and
  deterministic order input, resolves configured relative paths from the config file, and does not
  move effective values or config-source metadata into runtime/domain ownership;
- routine progress uses only the lightweight immutable progress projection, never constructs
  detailed unfinished identities, is flushed to console/file at the specified milestones, and
  cannot alter fixed-step execution or replace an original runtime failure;
- synchronous headless evaluation does not change the threaded compatibility path or allow worker
  mutation;
- reports are reproducible for identical input/profile/fake real-time source and output writing is
  safe;
- optional `productBarcodeLength` metadata is accepted when present or absent without entering the
  DSP domain, relaxing other unknown-property checks, or changing EMPTY transport-container
  semantics;
- source line code `03` is normalized to the existing FULL_PACK domain intent while code `02`
  continues to own later ASSOCIATED prepared-line dependency and collection;
- unresolved products are preserved as service-centre-specific unsupported load issues, excluded
  only from the full-day executable projection, and never fabricate picks, packs, bags, or
  completion;
- later retained occurrences of a reused inbound carrier barcode receive deterministic,
  collision-free DSP journey IDs during assembly; the first occurrence remains unchanged, exact
  substitutions remain ordered and auditable in the load report, and every downstream uniqueness
  guard remains intact;
- an oversized startup-service-centre selection is partitioned into a deterministic
  capacity-bounded resident prefix and a complete overflow suffix; overflow is authorized at zero,
  admitted through the existing rate/capacity owner without burst or reordering, and correctly
  separated from initial-preload provenance in immutable supply snapshots;
- no Exception/MANUAL execution, NS bag fabrication, dispatch/32R, calibrated timing, renderer-loop
  integration, new visual topology, event-driven fast-forward, mutable reset, or source-data
  mutation was added;
- identify every production file changed outside the plan's required surfaces and determine whether
  it is necessary.

### Documentation closure

After focused/full tests, the external-data run, and architecture review are green:

- mark this plan complete and verified and record the non-sensitive effective external-run
  configuration separately from the source dataset; never commit sensitive paths, populated local
  configuration, progress logs, or data;
- update current programme state in `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and only stale current-position/reading-order text in
  `docs/codex-instructions.md`;
- update the runtime interlude in `docs/machines/phase-1-stations-roadmap.md`;
- reconcile the superseded all-or-fail startup assumption in
  `docs/scheduler/dsp-osr-physical-inventory-plan.md` and startup-centre assumptions in
  `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`: mark them as amended by this
  completed full-day plan, record the capacity-bounded prefix/overflow contract, and do not rewrite
  unrelated completed-plan history;
- record full-day analysis as explicitly uncalibrated and based on provisional P2P output closure;
- record the measured full-day P2P admission, completion aggregation, OSR snapshot, and immutable
  P2P-workload index corrections together with the Step 20 profile evidence, without claiming
  general engine optimization; retain a broader render-integrated engine/simulation allocation and
  performance review as explicitly deferred work after functional full-day completion;
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
- A strict JSON file can provide the complete full-day invocation, with explicit command-line
  overrides and deterministic config-relative path handling, while timetable/priority configuration
  remains deferred.
- Compact progress is emitted at bounded simulated-time and completion milestones and may be
  mirrored to an immediately flushed persistent log; detailed unfinished identities remain in final
  JSON/text rather than routine output.
- Immutable known and active bag-correlation ID snapshots are reused by full-day P2P candidate
  admission between genuine assignment changes. Completion evaluation captures dynamic state once
  and aggregates it without per-manifest snapshot construction; OSR snapshots provide indexed
  lookup and change-bounded publication; immutable P2P workload plan indexes are reused without
  making dynamic allocation state stale. The complete render-integrated engine/simulation
  allocation profile remains deferred to a separate measured optimization effort.
- Every output states that timing is `UNCALIBRATED` and completion means
  `P2P_OUTPUT_CLOSED`, not real dispatch or trunker loading.
- 12N binding tolerates optional `productBarcodeLength` metadata while remaining strict for other
  unknown fields and preserving manifest-free EMPTY input.
- 12N order-line code `03` enters the established Third Party flow through the existing FULL_PACK
  domain intent, while later code-`02` ASSOCIATED lines retain prepared-line collection semantics.
- Product-master misses remain attributed unsupported short picks and are excluded from full-day
  execution without being represented as successful work until Exception Station behavior exists.
- Reused production transport-container barcodes are normalized into distinct deterministic DSP
  journey IDs after their first retained occurrence, with exact substitutions reported and no
  carrier-reuse state added to the lifecycle or runtime.
- Startup-service-centre physical manifests beyond configured OSR capacity remain complete,
  authorized upstream work. The deterministic capacity-sized prefix is resident at 06:00 and the
  suffix enters via ordinary rate-limited, capacity-blocked supply without exceeding OSR capacity.
