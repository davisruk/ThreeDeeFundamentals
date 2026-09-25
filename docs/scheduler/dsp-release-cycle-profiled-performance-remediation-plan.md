# DSP Release-Cycle Profiled Performance Remediation Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned. This document authorizes no implementation by itself. The user
starts each step separately and decides when a verified step may be committed.

## Purpose And Evidence

Reduce repeated work in the existing 50 ms fixed-step release cycle, using the
25 September 2026, 90-second JFR of the newer full-day dataset as the baseline.
The external baseline artifacts are
`C:\misc\cpas-test\scheduler-testing\dsp-performance-hotspots.jfr` and
`C:\misc\cpas-test\scheduler-testing\progress.log`; they are not repository
inputs or files to edit.
The run took approximately 20-30 wall-clock seconds per simulated minute and
reached PT4M. Of 6,349 execution samples, the class-qualified inclusive shares
were `DspOperationalReleaseScheduler.evaluate` 1,950 (30.71%),
`DspFullDayMetricsCollector.update` 982 (15.47%),
`DspOperationalReleaseSnapshotFactory.create` 645 (10.16%),
`OperationalCandidateRouteAdmissionFactory.create` 607 (9.56%),
`ThirdPartyVisitFactory.planFor` 288 (4.54%), and
`P2pBagCorrelationAssignmentSnapshot.compatibleWith` 204 (3.21%). The
route-admission and visit-plan shares are nested within release-snapshot
construction; `compatibleWith` is within scheduler evaluation. Do not add
them as independent percentages.

The user-supplied `jdk.ObjectAllocationSample` analysis found approximately
11.71 GB of sampled weight at `ThirdPartyVisitFactory.planFor` beneath broadly
named `update()` stacks and 2.52 GB at `compatibleWith` beneath broadly named
`evaluate()` stacks. These are sampled weights, not exact allocated bytes. The
broad `update()` group includes `SimulationWorld.update` and the release
controller; only 1.97 GB of its 24.93 GB weight contains the exact
`DspFullDayMetricsCollector.update`. The broad `evaluate()` group contains
other evaluate methods; only 5.94 GB of its 9.85 GB weight contains the exact
`DspOperationalReleaseScheduler.evaluate`. The one-off metrics `snapshot()`
appeared in just 2 of 6,349 execution samples and is not a target here.

`DspOperationalReleaseController.update` builds a new release snapshot whenever
its evaluation source can submit. The snapshot factory computes fresh route
admissions for current candidates. Third Party admission currently asks
`ThirdPartyVisitFactory.planFor(order)` on each such evaluation even though the
order and full-day product master are stable; the area-capacity snapshot is not
stable. During scheduler evaluation, line compatibility repeatedly creates a
stream/Optional pipeline over required bag correlations even though the
assignment snapshot already has a correlation-ID index.

These two concrete corrections form a bounded first pass. They do **not**
promise a full-day runtime target or authorize skipping whole evaluations.
After the shared measurement gate, decide from fresh evidence whether a later
plan should address candidate evaluation cadence, further indexed lookups, or
other dominant paths. The PT12M operational stall and the proposed
service-centre-first scheduling policy remain separate work. Do not alter
either in this plan.

## Required Reading And Baseline

Before each implementation step, read `AGENTS.md`, `docs/codex-instructions.md`
and its mandatory reading order, this entire plan, the Existing Boundaries,
Full-Day Runtime Architecture, Metrics Contract, and Step 35 of
`docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`, and the
shared contracts of
`docs/scheduler/dsp-full-day-fixed-step-performance-remediation-plan.md`.
Read the production and test files named in that step completely. Record
`git status --short` before editing and preserve all pre-existing changes.
If an expected file or ownership assumption differs,
stop and report rather than redesigning the step.

The user owns the external dataset and schedule, JFR capture and analysis
scripts, full-suite/regression runs, and same-data performance comparison.
The implementation agent runs only the focused Gradle command in the selected
step, reviews that step's complete diff, runs `git diff --check`, and reports
the final `git status --short`. Do not run the external full-day executable,
JFR, performance tests, or broader Gradle tests during implementation.

## Shared Fixed Contracts

- Keep the 50 ms fixed step, controller registration order, synchronous
  evaluation source, candidate order, ranking, dependencies, station admission,
  lease pinning, command revalidation, mutation order, deadlines, metrics, and
  reports unchanged. No scheduler or simulation clock fast-forward.
- Live Third Party area admission must be read for each current candidate
  evaluation; do not cache `ThirdPartyAreaSnapshot` or
  `StationAdmissionSnapshot`. Only the order/product-master-derived visit plan
  may be reused. No candidate may be treated as admissible merely because its
  static plan exists.
- Preserve immutable inter-component publication, simulation-thread ownership,
  exact order-sheet and physical-tote identity, full validation and failure
  behavior, and deterministic encounter order. Do not introduce a static cache,
  global mutable state, worker mutation, synchronization, weak map, or object
  pool. Build each immutable derived catalog completely before publication.
- Keep all existing public constructors, overloads, record components, and
  accessors compatible. The only new public APIs are those named below.
- Keep input partitioning, Third Party work selection, adapting/P2P behavior,
  outbound allocation, transport, station continuation, OSR supply, policy
  configuration, and cutoff behavior unchanged. Do not modify the user's JFR
  scripts or external data.
- Each step requires both behavior assertions and an efficiency assertion that
  fails if the named repeated work is restored. A sampled percentage is not a
  unit-test assertion or a promised speedup.

## Step 1: Reuse Static Third Party Visit Plans In Full-Day Admission

### Required reading and change surface

Read `ThirdPartyVisitFactory`, `ThirdPartyVisitPlan`,
`ThirdPartyStationAdmissionResolver`, `ThirdPartyStationAdmissionAdapter`,
`OperationalCandidateRouteAdmissionFactory`, `DspRouteDeriver`,
`DspFullDayAnalysisRuntimeFactory`, `NotionalToteOrder`, and
`InMemoryProductMasterRepository`. Read `ThirdPartyVisitFactoryTest`,
`ThirdPartySchedulerIntegrationTest`,
`OperationalCandidateRouteAdmissionFactoryTest`, and
`DspFullDayAnalysisRuntimeFactoryTest`.

Create in `online.davisfamily.warehouse.sim.dsp.thirdparty`:

- `ThirdPartyVisitPlanSource`: a public functional interface with
  `Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order)`;
- `ThirdPartyVisitPlanCatalog`: a public final, immutable implementation of
  that interface;
- `ThirdPartyVisitPlanCatalogTest` and
  `ThirdPartyStationAdmissionResolverTest`.

Modify only `ThirdPartyStationAdmissionResolver.java` and
`DspFullDayAnalysisRuntimeFactory.java` in production. Extend only
`ThirdPartySchedulerIntegrationTest.java` and
`DspFullDayAnalysisRuntimeFactoryTest.java` among existing tests. Do not change
`ThirdPartyVisitFactory`, `DspRouteDeriver`, the route-admission factory, the
adapter, production station processing, or a generic/debug runtime.

### Implementation contract

1. Construct `ThirdPartyVisitPlanCatalog(List<NotionalToteOrder> orders,
   ThirdPartyVisitFactory visitFactory)` once during full-day runtime
   composition, using executable `data.orders()` and the existing full-day
   `thirdPartyVisitFactory` backed by the immutable in-memory product master.
   For each order in encounter order, validate non-null and unique
   `OrderSheetKey`, call `visitFactory.planFor(order)` exactly once, and retain
   the original immutable order value plus its possibly empty plan. A null
   argument, duplicate key, null plan result, or present plan whose sheet,
   service centre, or order type differs from its input order fails construction.
   Build a local map first and publish only after all entries succeed. Retain no mutable
   input list or map. Do not cache a thrown result.
2. Catalog `planFor(order)` rejects null and unknown sheet keys, and rejects a
   same-key order with a different value. An equal-but-distinct order is
   accepted. It returns the same previously computed `Optional`/plan instance
   on every call. Do not rescan lines or consult the product master on lookup.
   This catalog is scoped to one full-day runtime and retains its entries for
   that runtime's lifetime; it is not used by generic/debug runtimes.
3. Retain both existing public `ThirdPartyStationAdmissionResolver`
   constructors and their behavior by delegating to `visitFactory::planFor`.
   Add the public four-argument overload
   `(StationAdmissionResolver fallbackResolver,
   ThirdPartyVisitPlanSource planSource,
   Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier,
   String targetId)` for full-day composition. Reject null collaborators as
   the existing constructors do. The resolver calls the source only for
   `StationType.THIRD_PARTY`, obtains a **fresh** area snapshot, and passes both
   to the existing adapter. Other station types still delegate unchanged.
4. In `DspFullDayAnalysisRuntimeFactory`, create the catalog before wiring the
   release runtime, then pass it to the private
   `stationAdmissionResolver(...)` composition helper and the new resolver
   overload. Keep the existing factory for actual station visits and all other
   consumers. Do not alter release-snapshot, candidate, or station-admission
   ordering. If precomputing every executable order exposes a new failure not
   already covered by full-day input/route validation, stop and report the
   contradiction rather than weakening prevalidation.

### Decision-complete tests

- `ThirdPartyVisitPlanCatalogTest`: use a counting `ThirdPartyVisitFactory`
  subclass or equivalent counting fixture. Prove one derivation per distinct
  order at construction and zero further derivations across repeated lookups;
  preserve line order and the exact immutable plan instance. Cover absent
  Third Party work as a cached empty result, equal-but-distinct order lookup,
  null/unknown/altered/duplicate orders, null factory/result, a mismatched
  returned plan, and failure
  before publication. The test must fail if `planFor` is called per lookup.
- `ThirdPartyStationAdmissionResolverTest`: with the catalog, evaluate the
  same Third Party order against an open then full area snapshot. Prove that
  admission changes with live capacity while plan derivation does not recur;
  preserve the existing target ID and blocked reason. Prove a non-Third-Party
  request delegates and does not touch the catalog. With an existing
  constructor, prove the factory remains consulted on each call.
- `ThirdPartySchedulerIntegrationTest`: retain existing no-plan and full-area
  behavior and add a catalog-backed admission case through the scheduler
  resolver boundary, including no state mutation on a blocked decision.
- `DspFullDayAnalysisRuntimeFactoryTest`: exercise the full-day composition
  through two fixed-step release evaluations with the same retained input
  orders; assert the evaluation sequence advances, the runtime remains
  functional, and candidate order is deterministic. The resolver test owns
  the independently controlled live-capacity transition. Review of the
  full-day wiring, the catalog counting test, and the shared JFR gate must
  establish that full-day admission does not call
  `ThirdPartyVisitFactory.planFor` per fixed step.

### Expected output

Order-level Third Party visit plans are derived once per full-day runtime;
dynamic Third Party capacity is still evaluated on every release snapshot.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitPlanCatalogTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationAdmissionResolverTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartySchedulerIntegrationTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

### User verification

No external run is required for this individual step. The shared gate below
owns same-data performance verification after both steps.

## Step 2: Remove Per-Line Correlation Compatibility Pipelines

### Required reading and change surface

Read `P2pBagCorrelationAssignmentSnapshot`,
`P2pBagCorrelationRequirement`, `P2pLineAllocationRequest`,
`P2pLineAllocationRequestFactory`,
`DeadlineAwareElasticStickyP2pLineAllocationPolicy`,
`DspOperationalReleaseScheduler`, and
`P2pBagCorrelationAssignmentSnapshotTest`,
`P2pLineAllocationRequestFactoryTest`, and
`DspOperationalReleaseSchedulerTest`.

Modify only `P2pBagCorrelationAssignmentSnapshot.java` in production and
extend only those three named test classes. Do not change catalog or registry
ownership, correlation publication, line-choice tiers, release decisions,
assignment commit, or any public API.

### Implementation contract

Replace `compatibleWith(requirements, lineId)`'s stream/Optional pipeline with
one ordinary iteration over the supplied set. Retain null-argument rejection,
the existing empty-set result, requirement encounter order, and first
conflict short-circuit. For each requirement, use its already validated
correlation ID to look up `assignmentsByCorrelation` directly. Unassigned
correlations remain compatible; an assigned correlation is compatible only
with the requested line. Do not create a collection, stream, Optional, or
derived index per call. Preserve `find`, `lineFor`, snapshot immutability,
constructor validation, and value semantics. Do not cache a compatibility
answer across snapshots or line IDs.

### Decision-complete tests

- In `P2pBagCorrelationAssignmentSnapshotTest`, cover empty requirements,
  unassigned requirements, same-line assignments, a conflicting assignment,
  mixed assigned/unassigned requirements, null arguments, and early exit on
  the first conflict. Use a custom encounter-ordered `Set` whose `stream()`
  throws to prove the hot method uses iteration; make its iterator count
  visited elements to prove short-circuit behavior. Repeat across two
  different immutable assignment snapshots to prove fresh ownership results.
- In `P2pLineAllocationRequestFactoryTest`, exercise the production request
  boundary with a shared correlation constrained to one line and prove that
  compatibility accepts only that line without mutating the catalog.
- In `DspOperationalReleaseSchedulerTest`, retain candidate ordering and
  block-reason behavior when a candidate is incompatible with one line but
  compatible with another; assert no command or assignment is committed for
  a wholly incompatible candidate.

### Expected output

Per-candidate/per-line compatibility no longer allocates a stream pipeline,
while exact correlation-to-line ownership remains unchanged.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequestFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest
```

### User verification

No external run is required for this individual step. The shared gate below
owns same-data performance verification after both steps.

## Shared User-Owned Functional And Measurement Gate

After both focused implementation commands pass, the user runs the relevant
release/Third Party/full-day focused regression, then the complete Gradle
suite, then rebuilds the installed distribution. The implementation agent
must not run these user-owned commands:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.*
.\gradlew test
.\gradlew :app:installDist
```

Use the same newer dataset, external timetable, `scheduler_conf.json`, JVM
options, and headless run configuration as the 25 September recording. Record
wall-clock timestamps for PT1M through PT4M. Capture a 90-second JFR over
the comparable active interval and retain the old and new progress logs.

Compare class-qualified execution-sample shares and sampled allocation
weights for `DspOperationalReleaseSnapshotFactory.create`,
`OperationalCandidateRouteAdmissionFactory.create`,
`ThirdPartyVisitFactory.planFor`,
`DspOperationalReleaseScheduler.evaluate`,
`P2pBagCorrelationAssignmentSnapshot.compatibleWith`, and
`DspFullDayMetricsCollector.update`. Report total sample counts, recording
durations, GC pauses, and elapsed PT progress. The old and new sampled totals
must not be added across overlapping stacks. The earlier log did not retain
precise PT wall-clock timestamps; if no same-configuration pre-change timing
exists, report quantitative speedup as **unproven** and treat this as a new
baseline, not a percent improvement claim.

Acceptance requires green functional tests, unchanged deterministic output
over the compared interval, no `ThirdPartyVisitFactory.planFor` underneath
full-day `ThirdPartyStationAdmissionResolver.admissionFor` during fixed-step
polling, and no `StreamSupport.stream` underneath
`P2pBagCorrelationAssignmentSnapshot.compatibleWith`. A lower sampled weight
or faster PT minute alone does not substitute for those path checks. The
full-day performance goal remains open unless a later complete run proves it.
If either old path persists, the relevant step fails. If both disappear but
PT progress remains too slow, stop this plan and use the new profile to plan
the next bounded correction; do not automatically skip unchanged release
evaluations or alter scheduler policy.

## Independent Review And Documentation Closure

After the user gate, an independent review of the two-step diff reports PASS,
FAIL, or UNPROVEN for every shared contract, static-plan ownership, build-
before-publication, stale/mismatched-order rejection, live capacity freshness,
generic-constructor compatibility, correlation first-conflict behavior,
deterministic candidate order, and absence of unrelated production changes.
Any FAIL or architectural UNPROVEN finding returns for a separately approved
correction; it is not resolved by changing the policy during review.

Only after the user gate and review are green may documentation closure:

- mark this plan complete with the two implementation commits, focused test
  results, user gate result, review result, exact JFR comparison, and explicit
  quantitative-speedup status;
- update the full-day plan's current status and Step 35 prerequisite to record
  these two mechanisms and the remaining measured bottleneck, without closing
  Step 35;
- update only stale current-position/reading-order text in
  `docs/codex-instructions.md` and `docs/codex-context.md` so future sessions
  find this plan and its verified outcome.

Do not mark the whole production-day performance target complete without its
own required full-run evidence. Do not fold the PT12M stall investigation,
blockage-triggered logging, service-centre-first policy documentation/plan, or
unmatched-tote simulation into this closure.
