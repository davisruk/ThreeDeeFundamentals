# DSP Full-Day Fixed-Step Performance Remediation Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned. This document authorizes no implementation by itself. The user starts each
implementation step separately and decides when a verified step may be committed.

## Purpose And Measured Evidence

Remove six code-audit-confirmed sources of work whose cost is currently proportional to the
50 ms fixed-step polling rate or to whole-day collection size when the represented state has not
changed. This is a bounded low-hanging-fruit remediation before another broad JFR investigation.
It preserves the current snapshot/publication architecture; it does not replace it with a shared
mutable world model.

The committed runner clock-read correction at `f6b20a3` is independently verified: the user's
early 45-second recording contained 0 `DspFullDayMetricsCollector.snapshot()` samples in 2,608
execution samples, and the `PT7M` recording contained 0 in 1,622. The full-day run nevertheless
remained around ten wall-clock minutes per simulated minute. In the `PT7M` recording,
`P2pBagCorrelationAssignmentSnapshot.lineFor`,
`P2pLineLeaseCatalogSnapshot.findAssignment`,
`P2pBagCorrelationRequirementCatalog.requirementsFor`,
`Av02AllocationSnapshotFactory.validateLogicalOrderIdentities`,
`OsrProcessingReleaseSnapshot`, `DspOperationalReleaseSnapshot`, and metrics-derived maps remained
visible. Weighted allocation also attributed about 102 GB beneath `StreamSupport.stream`; that is
sampled allocation weight, not exact retained bytes or CPU time.

The source audit then established these six concrete repeated paths:

1. each of five `AssignedLineWorkPlanProvider` instances scans every planned bag correlation on
   every `ToteToBagFlowController.update(...)` merely to recover the correlations already assigned
   to that line;
2. completion evaluation runs every fixed step and repeatedly derives whole-day supply,
   lifecycle, OSR, AV02, and planned-bag projections even when the corresponding immutable owner
   snapshot is unchanged;
3. `DspAv02AllocationRuntimeController` rebuilds and validates a complete allocation snapshot on
   every fixed step, including periods in which all exact immutable inputs and the no-command
   result are unchanged;
4. OSR release repeatedly scans lifecycle assignment history, rebuilds the same physical release
   snapshot, and rejoins unchanged physical/logical/manifest inputs before evaluating genuinely
   dynamic route and P2P admission;
5. P2P lease snapshots repeatedly filter the complete physical-assignment ledger once per line,
   while each activity probe copies the complete PRL map solely to calculate two aggregate counts;
6. metrics reads detailed P2P line snapshots on every fixed step although they are needed only to
   publish a report, and reads three other snapshot suppliers whose values are not consumed at all.

These findings justify the six steps below. They do not prove that these are the only remaining
costs or that the run will meet the eventual minutes-level target. The post-batch user gate decides
whether another code audit, a focused profile, a functional P2P-utilisation investigation, or a
larger architectural proposal is warranted.

## Required Reading For Every Step

Before implementing any step, read `AGENTS.md`, `docs/codex-instructions.md`, and its complete
mandatory reading order; then read this complete plan. Read the full-day plan's Existing
Boundaries, Full-Day Runtime Architecture, Metrics Contract, Step 35, and its architecture-review
contract. Read the completed clock-read remediation plan and the exact production/test files named
by the selected step. Record `git status --short` before editing and preserve all existing user
changes.

The implementation agent must stop rather than choose an alternative if a named type/test is
missing, an inspected owner does not publish stable immutable snapshot identity as stated here, or
implementation exposes an unresolved choice affecting public compatibility, state ownership,
invalidation, controller order, deterministic ordering, mutation sequencing, threading, completion
semantics, or P2P lease/allocation behavior.

## Fixed Architecture, Efficiency, And Verification Contract

- Simulation controllers and mutable owners remain simulation-thread-owned. Existing scheduler
  worker boundaries remain unchanged. No new thread, executor, synchronization, asynchronous
  publication, or worker mutation is allowed.
- Existing immutable snapshots remain the inter-component publication boundary. This plan adds
  direct immutable indexes and owner-local single-entry reuse; it does not authorize direct
  cross-owner mutation or a central mutable world store.
- Every cache introduced here is an instance field owned by the named factory/controller/source,
  holds at most the most recent value for its named input dimension, uses constant-time reference
  identity checks against exact immutable inputs, builds and validates the replacement completely
  before publication, and replaces it on the first input-identity change. Equal-but-distinct inputs
  are changes.
- Do not add static/global caches, thread-locals, synchronization, weak maps, object pools,
  fingerprints, deep equality cache keys, time-based expiry, cache-size policies, or generic cache
  frameworks. Do not retain mutable machine collections in an immutable publication.
- A cache hit must avoid the named high-cardinality traversal. Do not move that traversal into a
  key, validator, supplier, helper, logging call, `equals`, hash calculation, or another fixed-step
  path. Constant-size checks over the five configured P2P lines are allowed where explicitly
  specified.
- Preserve exact encounter order, configured line order, service-centre order, candidate order,
  first-transition selection, command revalidation, mutation order, completion timing, block
  precedence, metrics arithmetic, progress/report values, and deterministic repeated-run output.
- Keep all existing public constructors, record components, accessors, overloads, and value
  semantics compatible. Additive query methods are allowed only where explicitly named below.
- Keep the 50 ms fixed step, controller registration order, line count, operational policies,
  machine timings, deadlines, hard cutoff, metrics sample interval, report cadence, and headless
  runner behavior unchanged. Do not introduce event-driven fast-forward or skip machine updates.
- Do not change input loading/rejection, MANUAL handling, Exception/NS behavior, 32R/dispatch,
  calibration, visual topology, transport behavior, station behavior, bag planning, or source
  data.
- The implementation agent runs only the focused command named by the selected step, then reviews
  the complete step diff, runs `git diff --check`, and records final `git status --short`. The user
  owns broader tests, installation, external data, JFR capture, and performance scripts.
- Each deterministic efficiency test is mandatory. Output equivalence alone is insufficient. A
  step is not ready if its test cannot distinguish reuse from reconstructing or rescanning the
  unchanged high-cardinality input.

## Step 1: Publish Correlations By Assigned P2P Line

### Required change surface

Modify:

- `P2pBagCorrelationAssignmentSnapshot.java`
- `DspFullDayAnalysisRuntimeFactory.java`
- `P2pBagCorrelationAssignmentSnapshotTest.java`
- `DspFullDayAnalysisRuntimeFactoryTest.java`

Create:

- `analysis/runtime/AssignedLineWorkPlanProvider.java`
- `analysis/runtime/AssignedLineWorkPlanProviderTest.java`

Remove the private nested `AssignedLineWorkPlanProvider` from the runtime factory after replacing
it with the package type. Do not modify `ToteToBagFlowController`, assignment commit order, the
assignment registry, the requirement catalog, or allocation policy in this step.

### Implementation contract

`P2pBagCorrelationAssignmentSnapshot` must build, during its existing constructor validation, an
immutable `Map<P2pLineId, Set<String>>` from the already validated encounter-ordered assignment
list. Each value is an immutable encounter-ordered set. Add
`public Set<String> correlationIdsFor(P2pLineId lineId)`. It rejects null and returns the stored set
or `Set.of()` for an unrepresented line. Preserve the existing assignment list, correlation map,
constructors, accessors, `equals`, `hashCode`, and `toString`; the new derived index does not enter
value semantics.

The new final package class implements `ToteToBagWorkPlanProvider` and has the constructor
`AssignedLineWorkPlanProvider(P2pLineId lineId, ToteToBagWorkPlanProvider allWork,
Supplier<P2pBagCorrelationAssignmentSnapshot> assignmentSnapshotSupplier)`. Validate all three
arguments. Each public query obtains exactly one non-null assignment snapshot. `expectedPackCount`
returns empty unless `snapshot.lineFor(correlationId)` equals this line, then delegates once to
`allWork`. `expectedCorrelationIds` returns `snapshot.correlationIdsFor(lineId)` directly; it must
not read `allWork.expectedCorrelationIds()`, stream/filter all correlations, or copy the retained
set. The runtime factory passes `correlationAssignments::snapshot`.

### Decision-complete test contract

- In `P2pBagCorrelationAssignmentSnapshotTest`, prove per-line sets preserve assignment encounter
  order, are immutable, return the identical retained set on repeated reads, isolate lines, and
  return empty for an unknown line. Keep duplicate-correlation rejection tests green.
- In `AssignedLineWorkPlanProviderTest`, use a counting `allWork` implementation whose
  `expectedCorrelationIds()` throws. Prove the provider returns only its assigned set without
  calling that method, reads the snapshot once per query, delegates pack count only for its line,
  and sees a newly published assignment snapshot on the next query.
- In `DspFullDayAnalysisRuntimeFactoryTest`, retain the existing five-line composition and dynamic
  work discovery assertions and update only construction expectations required by extraction.

### Expected output

Each line's per-step synchronization is proportional to correlations assigned to that line and
does not scan all planned correlations. Malformed/unassigned work, assignment pinning, and all
machine behavior remain unchanged.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.AssignedLineWorkPlanProviderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

### User verification

No additional user verification is required for this step. Do not run JFR yet.

Proposed commit message: `Index assigned P2P line correlations`

## Step 2: Reuse Change-Bounded Completion Projections

### Required change surface

Modify `DspFullDayAnalysisRuntimeFactory.java` and
`DspFullDayAnalysisRuntimeFactoryTest.java`. Create
`analysis/runtime/DspFullDayCompletionProjectionCache.java` and
`DspFullDayCompletionProjectionCacheTest.java`. Do not change
`DspFullDayCompletionEvaluator`, `DspFullDayCutoffController`, its controller position, hard-cutoff
republishing, or public ad-hoc completion reads.

### Implementation contract

`DspFullDayCompletionProjectionCache` is a final, package-private, simulation-thread-owned helper
constructed with the already immutable `manifestsByServiceCentre` and
`plannedBagsByServiceCentre` indexes. It owns five independent single-entry projections:

- supply identity -> configured service-centre IDs and capacity-blocked counts by centre;
- lifecycle identity -> non-terminal inbound counts by centre and the encounter-ordered list of all
  non-terminal physical tote IDs;
- OSR identity -> waiting counts by centre;
- AV02 identity -> waiting counts by centre;
- outbound identity -> allocated bag keys, remaining planned bag/pack counts by centre, and open
  outbound counts by centre.

Its package-private accessors are exactly
`SupplyProjection supplyProjection(DspSupplySnapshot)`,
`LifecycleProjection lifecycleProjection(PhysicalToteLifecycleSnapshot)`,
`OsrProjection osrProjection(OsrInventorySnapshot)`,
`Av02Projection av02Projection(Av02InventorySnapshot)`, and
`OutboundProjection outboundProjection(OutboundAllocationSnapshot)`. Define those five projections
as package-private nested records with only the fields named above; they are consumed by
`CompletionSnapshotSource` and directly identity-tested by the matching package test.

Expose one package-private accessor per projection. Each accessor validates non-null input, returns
the same immutable projection object for the same exact input reference, and rebuilds only its own
projection for a different reference. Build-before-publish applies even when validation fails.
Projection records defensively publish immutable maps/lists/sets and preserve source encounter
order. Do not combine all five identities into one cache: a frequent outbound change must not
invalidate unchanged lifecycle, supply, OSR, or AV02 work.

`CompletionSnapshotSource` retains exact fresh per-invocation capture of clock, line activity and
completed correlations, station claims/dispositions, active transport, launch/arrival queues, and
lease state. Replace only the five derivations listed above. Iterate the cached non-terminal tote
ID list for dynamic owner resolution. Read remaining bag/pack and open-outbound counts from the
outbound projection. Continue to evaluate every service centre on every invocation so completion
is observed on the first eligible fixed step and hard cutoff still republishes after output close.
Do not cache final completion snapshots or first-completion-time mutation.

### Decision-complete test contract

- `DspFullDayCompletionProjectionCacheTest` must assert object identity reuse independently for all
  five projections, replacement only for the changed dimension, equal-but-distinct replacement,
  immutable detached values, exact counts/order, and failed-build retention of the prior cache.
- `DspFullDayAnalysisRuntimeFactoryTest` must retain exact early completion, unfinished counts,
  dynamic transport/station/line blocking, first-completion time, and hard-cutoff behavior. Add a
  scenario in which unchanged large planned-bag/lifecycle inputs are used across two completion
  evaluations while a dynamic station value changes; assert changed completion output while the
  package cache tests supply the independent no-rebuild proof.

### Expected output

Completion remains exact per fixed step, but whole-day owner projections are rebuilt only when
their publishing owner changes.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayCompletionProjectionCacheTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluatorTest
```

### User verification

No additional user verification is required for this step. Do not run JFR yet.

Proposed commit message: `Reuse full-day completion projections`

## Step 3: Skip Unchanged No-Command AV02 Evaluations

### Required change surface

Modify only `DspAv02AllocationRuntimeController.java` and
`DspAv02AllocationRuntimeControllerTest.java`. Do not modify
`Av02AllocationSnapshotFactory`, `Av02AllocationController`, inventory/lifecycle mutation, or
public constructors and accessors.

### Implementation contract

Add a private `AllocationInputs` value containing the exact scheduler, supply, lifecycle, and AV02
inventory snapshot references captured once from the existing suppliers. Constructor evaluation
remains sequence 0 and stores its inputs. At the start of each update, capture a new input value.
If all four references are identical to the previous evaluated inputs and the previous allocation
snapshot had no command, return without incrementing `nextSequence`, rebuilding either allocation
or runtime snapshot, or invoking the command controller. Thus sequence denotes a genuine AV02
evaluation, not a poll.

If any reference changed, or the previous allocation contained a command, preserve the current
sequence exactly: allocate the next sequence, build the primary snapshot from the captured inputs,
invoke `Av02AllocationController`, and let its revalidation callback capture all four suppliers
again and build a fresh same-sequence revalidation snapshot immediately before mutation. Never
reuse the primary snapshot as command revalidation. Publish the runtime snapshot only after the
controller returns. On failure, retain the last published runtime snapshot and clear transient
fields in `finally` as today.

Do not use `equals`, clock/time, `dtSeconds`, diagnostic text, or command contents as cache keys.
Do not suppress reevaluation after a command-bearing evaluation, even if the command did not
mutate.

### Decision-complete test contract

Extend `DspAv02AllocationRuntimeControllerTest` to prove:

- repeated unchanged no-command updates preserve runtime-snapshot and allocation-snapshot object
  identity and sequence;
- replacing each of scheduler, supply, lifecycle, and inventory independently causes one new
  evaluation and sequence increment, including equal-but-distinct values;
- a command-bearing primary snapshot still obtains a distinct fresh revalidation snapshot and
  performs at most one allocation in the existing mutation order;
- a command-bearing evaluation is reconsidered on the next update even when a failed/no-op command
  leaves supplier identities unchanged;
- invalid `dtSeconds`, supplier failure, and allocation failure publish no partial snapshot.

### Expected output

Long blocked/empty AV02 periods perform constant-time identity checks rather than whole-scheduler
validation each fixed step, while actionable commands retain fresh revalidation and mutation
semantics.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeControllerTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest
```

### User verification

No additional user verification is required for this step. Do not run JFR yet.

Proposed commit message: `Skip unchanged AV02 allocation polls`

## Step 4: Reuse OSR Release Inputs And Lifecycle Assignment Indexes

### Required change surface

Modify:

- `PhysicalToteLifecycleSnapshot.java`
- `OsrProcessingReleaseSnapshotFactory.java`
- `DspOperationalReleaseSnapshot.java`
- `DspOperationalReleaseSnapshotFactory.java`
- `PhysicalToteLifecycleSnapshotTest.java`
- `OsrProcessingReleaseSnapshotFactoryTest.java`
- `DspOperationalReleaseSnapshotTest.java`
- `DspOperationalReleaseSnapshotFactoryTest.java`.

Do not modify scheduler ranking/evaluation, route-admission capture, command handling, downstream
acceptance, inventory departure, lifecycle activation, or worker-thread boundaries.

### Implementation contract

Convert `PhysicalToteLifecycleSnapshot` from a record to a final immutable class while preserving
the exact two-argument constructor, `totes()`, `assignments()`, record-equivalent `equals`,
`hashCode`, and `toString`. During construction, build immutable encounter-ordered indexes for the
first active assignment by `OrderSheetKey`, all active assignments by `PhysicalToteId`, and all
assignment history by `OrderSheetKey`. Existing three query methods use direct map lookup and
return retained immutable values; unknown keys return empty values. Duplicate behavior continues
to follow the previous encounter-order/first-match contract.

`OsrProcessingReleaseSnapshotFactory` owns one cached snapshot keyed by exact inventory and
lifecycle snapshot references. Same-reference calls return the identical
`OsrProcessingReleaseSnapshot`; either changed reference performs all existing validation and
candidate creation before replacing the cache. Failed creation retains the previous cache.

`DspOperationalReleaseSnapshotFactory` adds one single-entry immutable joined-candidate projection
keyed by exact physical snapshot, manifest catalog, optional AV02 snapshot, and logical snapshot
references. The projection contains one validated immutable candidate state owned by
`DspOperationalReleaseSnapshot`: joined candidates, candidate-by-tote index, matching pharmacy
groups/group indexes, and candidate-to-group index.

Add package-private nested `DspOperationalReleaseSnapshot.CandidateState`, package-private static
`validatedCandidateState(List<DspOperationalReleaseCandidate>,
List<ServiceCentrePharmacyGroup>)`, and package-private static
`fromValidatedCandidateState(CandidateState, Map<StationType, StationAdmissionSnapshot>,
Set<PreparedLineKey>, List<OperationalCandidateRouteAdmission>,
P2pLineLeaseCatalogSnapshot, Map<OperationalRouteDestination, Boolean>,
Optional<P2pElasticAllocationSnapshot>)`. Existing public constructors still perform every current
candidate/group validation by building a candidate state, and all public accessors/value behavior
remain unchanged. The trusted factory retains the validated candidate state's immutable list and
indexes, but still freshly copies/validates station admissions, prepared lines, route admissions,
P2P target admissions, leases, and elastic allocation on every call.

All factory overloads use the candidate-state projection. On a hit, they do not recopy candidates,
rebuild candidate/group maps, or rerun stable group validation. On any changed key reference, repeat
all current join and candidate/group validation. Publish a replacement projection only after route
admission and final snapshot construction both succeed; any failure retains the previous
projection. Existing logical and manifest index caches remain; do not duplicate them inside the
joined projection.

### Decision-complete test contract

- `PhysicalToteLifecycleSnapshotTest` proves all three indexed queries preserve previous results,
  order, first-match behavior, immutability, detachment, and record-compatible value semantics.
- `OsrProcessingReleaseSnapshotFactoryTest` proves same-reference result identity, replacement on
  either input, equal-but-distinct replacement, and no publication after failed validation.
- `DspOperationalReleaseSnapshotFactoryTest` proves same-input candidate object/list reuse while a
  counting route-admission factory is still called on every invocation; replacement on each key
  dimension; AV02/no-AV02 isolation; unchanged order and validation; and prior projection retention
  after failure. The test must use at least one candidate so identity proves join reuse.
- `DspOperationalReleaseSnapshotTest` keeps every public-constructor validation assertion and proves
  the package trusted path reuses the validated candidate list/indexes while rejecting invalid
  dynamic route, lease, target-admission, and elastic inputs on every invocation.

### Expected output

Unchanged OSR/lifecycle/logical state no longer repeats full assignment scans and joins, while
dynamic admission and scheduler decisions are still evaluated every fixed step.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest
```

### User verification

No additional user verification is required for this step. Do not run JFR yet.

Proposed commit message: `Reuse indexed OSR release state`

## Step 5: Remove Repeated P2P Lease And PRL Collection Scans

### Required change surface

Modify:

- `P2pLineLeaseRegistry.java`
- `P2pLineLeaseSnapshot.java`
- `ToteToBagFlowController.java`
- `ToteToBagP2pLineActivityProbe.java`
- `P2pLineLeaseRegistryTest.java`
- `P2pLineLeaseDomainTest.java`
- `ToteToBagP2pLineActivityProbeTest.java`
- `ToteToBagFlowControllerTest.java`

Create `totebag/control/PrlActivitySummary.java`. The record has exactly
`int nonIdlePrlCount` and `int packCount`, both nonnegative; its behavior is covered through
`ToteToBagFlowControllerTest`. Do not change PRL assignment, release, transfer, bagging, lease
retention, or line-allocation decisions.

### Implementation contract

`P2pLineLeaseRegistry` retains its authoritative assignment-by-physical-tote map and additionally
owns an immutable published assignment list per configured line. Initialize every line to
`List.of()`. On a genuinely new validated assignment, build a replacement list for only that line,
publish it in configured assignment encounter order, then record the existing transition. An
idempotent duplicate changes neither map, list identity, nor transition. Snapshot and validation
use the retained per-line list directly; they must not filter `assignmentsByPhysicalToteId.values()`.
No assignment-removal path is introduced.

Convert `P2pLineLeaseSnapshot` from a record to a final immutable class while preserving its exact
public four-argument constructor, component-named accessors, and record-equivalent `equals`,
`hashCode`, and `toString`. The public constructor retains all current defensive validation and
copying. Add one package-private static factory named `fromValidatedRegistryState(...)` with the
same four values; only `P2pLineLeaseRegistry` calls it. It trusts the registry-owned immutable
assignment list's per-line identity/distinctness, retains that list reference, and still performs
all owner/activity/open-outbound validation that can change per snapshot. This prevents the
per-line history from being copied and revalidated every poll without weakening arbitrary public
construction.

Add `ToteToBagFlowController.prlActivitySummary()`. It iterates the controller-owned `prlsById`
values directly once, without copying the map or exposing it, and returns exact non-idle and pack
counts. Keep `getPrlsById()` unchanged for compatibility. `ToteToBagP2pLineActivityProbe` uses the
summary and must not call `getPrlsById()` or stream/copy the PRL map.

### Decision-complete test contract

- `P2pLineLeaseRegistryTest` proves configured-line order, assignment order, isolation, retained
  per-line list identity across snapshots with no assignment change, replacement only for the
  assigned line, idempotent duplicate identity, and all existing lease validation.
- `P2pLineLeaseDomainTest` keeps all public-constructor validation/value-semantic assertions and
  proves snapshots produced by the registry retain the trusted immutable assignment-list identity.
- `ToteToBagFlowControllerTest` proves the summary at idle, with assigned empty PRLs, and with packs
  across multiple PRLs; repeated reads must not mutate controller state.
- `ToteToBagP2pLineActivityProbeTest` proves its pack-path result remains exact and uses a flow
  controller configuration for which `getPrlsById()` compatibility behavior is tested separately;
  source review confirms the probe calls only `prlActivitySummary()` for these two counts.

### Expected output

Each lease capture is proportional to five lines plus retained per-line lists, not five scans of
the global assignment ledger, and activity capture no longer allocates a 31-entry PRL map copy per
line per poll.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseDomainTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.ToteToBagP2pLineActivityProbeTest --tests online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowControllerTest
```

### User verification

No additional user verification is required for this step. Do not run JFR yet.

Proposed commit message: `Remove repeated P2P lease collection scans`

## Step 6: Separate Fixed-Step Metrics Inputs From Report Inputs

### Required change surface

Modify only `DspFullDayMetricsCollector.java` and
`DspFullDayMetricsCollectorTest.java`. Keep the public `SnapshotSuppliers` record and its component
order, constructor, validation, and source compatibility unchanged. Do not change runtime-factory
wiring, block classification, duration/event arithmetic, occupancy sampling, report construction,
or metrics update cadence.

### Implementation contract

Replace the private all-purpose `MetricInputs` read with private update/report input values:

- update inputs contain exactly the values read by constructor initialization and `update(...)`;
- report inputs add detailed `p2pLineSnapshots` for `snapshot()`/line metrics;
- `transportInFlightSnapshotSupplier`, `outboundTransportSnapshotSupplier`, and
  `stationProcessingSnapshotSupplier` remain compatibility components of `SnapshotSuppliers` but
  are never invoked because no collector calculation consumes them;
- `p2pLineSnapshotsSupplier` is invoked only by public `snapshot()`, never by constructor or
  `update(...)`;
- every supplier that update semantics genuinely use is still read exactly once on every fixed
  step, preserving the existing metrics contract. Do not cache elastic, operational, completion,
  transport-block, station-arrival, station-claim, supply, OSR, AV02, lifecycle, outbound,
  scheduler, clock, or runtime-state values in this step.

Private method/record decomposition is discretionary only if it obeys those exact read sets.

### Decision-complete test contract

Add counting suppliers in `DspFullDayMetricsCollectorTest`. Prove constructor plus two updates never
invoke detailed-line or the three unused suppliers; each update-required supplier advances once per
update; one public `snapshot()` invokes detailed-line exactly once and still never invokes the
three unused suppliers. Keep exact block duration, occupancy, line utilization, issue history,
completion, monotonicity, and immutable snapshot assertions green. The counting test must fail if
an implementation simply moves `readInputs()` or invokes a supposedly unused supplier through a
helper.

### Expected output

Metrics still accounts for every 50 ms step, but detailed line snapshots are report-boundary work
and three dead supplier reads disappear completely.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollectorTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

### User verification

After Step 6 focused verification passes, the user runs the shared batch gate below. Step 6 is not
complete until that gate is reported.

Proposed commit message: `Separate fixed-step metrics capture from reporting`

## Shared User-Owned Functional And Performance Gate

The implementation agent must not run or edit the user's JFR scripts. After all six focused test
commands are green, the user runs the full-day plan's focused regression command, then the complete
suite, then rebuilds the installed distribution. The user runs the same external dataset,
simulation-affecting configuration, JDK, heap, and progress interval used for the `PT7M` evidence,
with new output/JFR names.

First use a bounded progress gate: record wall-clock start and the wall-clock times of the first
three complete simulated-minute progress blocks. Stop after `PT3M` if the rate is still clearly
incompatible with a minutes-level full day; a stopped run is diagnostic and does not satisfy Step
35. If progress is materially faster and stable, continue far enough to observe more than the
initial ADAPTED-heavy phase before deciding whether to attempt the complete day.

Only after the code-audit batch, take one user-owned 45-second JFR during active work and run the
existing analysis scripts. Report, with total sample context:

- fixed-step wall/simulated-time rate and whether it improves, stalls, or regresses;
- execution samples and weighted allocation for the six named paths;
- whether `AssignedLineWorkPlanProvider.expectedCorrelationIds` still traverses all work;
- AV02 factory samples during unchanged no-command periods;
- OSR lifecycle/history and candidate-join construction sites;
- completion and metrics input/capture sites;
- P2P lease assignment filtering and `ToteToBagFlowController.getPrlsById` beneath activity capture;
- GC counts, longest pause, and post-GC heap when available.

Do not require a percentage threshold from sampled data. Acceptance requires green functional
tests, no deterministic-output regression, disappearance of the specifically removed traversals
from their old polling call sites, and a positive simulated-time/wall-time movement. If a removed
site remains because the same work was moved, the relevant step fails. If the sites disappear but
performance remains orders of magnitude too slow, accept only the proven local corrections and
mark the full-day performance target UNPROVEN; use the new dominant code path to decide the next
plan.

The observation that only one P2P line appeared active is not corrected by this plan. Line
utilisation may be valid for the current service-centre demand or may be a functional defect. After
the fixed-step waste is removed, investigate it separately from line assignments, lease ownership,
elastic demand, route admission, and actual arrivals. Do not force work onto idle lines as a
performance workaround.

## Independent Architecture And Efficiency Review

The implementing Luna Max session must not perform or sign off this review. Its per-step diff
check is implementation acceptance, not independent review. After the six steps, focused tests,
user regression, and bounded performance gate, start a clean-context higher-reasoning model and
give it only:

- this plan;
- the baseline commit `f6b20a3` and final six-step commit/diff range;
- the six focused test results and user-run gate summary;
- the named production files and directly invoked owners needed to trace each path.

This deliberately bounds review cost: one review after the batch, no general repository review,
no replay of JFR extraction, and no redesign. The reviewer reports PASS, FAIL, or UNPROVEN with
class/method evidence for every item below:

- each of the six old high-cardinality polling traversals is absent and was not moved elsewhere;
- each new index/cache has exactly the owner, one-entry lifetime, reference-identity key,
  build-before-publish behavior, invalidation, and immutable publication specified here;
- equal-but-distinct inputs invalidate, failed builds do not publish, and unchanged inputs provide
  deterministic identity evidence;
- assignment/candidate/service-centre/line ordering and all validation remain exact;
- AV02 command revalidation, OSR dynamic admission, completion first-eligible-step timing, hard
  cutoff republishing, metrics per-step accounting, and P2P lease transitions remain fresh where
  required;
- no public compatibility break, extra production mechanism, global/shared mutable state,
  synchronization, background work, deep key, fingerprint, or unbounded retention was added;
- no unrelated behavior or deferred feature entered the diff;
- every changed production file is necessary for one named step and every mandatory efficiency
  test would fail for reconstruction/rescanning.

Any FAIL blocks closure. Any UNPROVEN item affecting ownership, invalidation, ordering, threading,
completion, command revalidation, or measured traversal removal is escalated to the user and the
higher-reasoning reviewer; Luna Max must not repair it by inventing a design. Mechanical test or
syntax failures may return to Luna under the unchanged plan.

## Documentation Closure

After user verification and independent review are green, a lower-cost documentation model may:

- mark this plan complete and record the six commit hashes, focused/user test results, bounded
  wall/simulated-time evidence, and honest JFR conclusions;
- update the full-day plan's status and Step 35 prerequisite to state that this remediation is
  complete, replacing its obsolete requirement that metrics read every supplier every fixed step
  with the verified update/report split;
- record the six exact owner-local reuse mechanisms in the full-day architecture-review checklist;
- update only stale active-plan/reading-order/current-position text in
  `docs/codex-instructions.md` and `docs/codex-context.md`;
- leave Step 35 open unless the complete external day and all its existing acceptance criteria
  have passed.

The closure model must not claim general performance completion from local improvements, select a
new architecture, alter deferred behavior, or fold the separate P2P-line-utilisation investigation
into this completed batch.

Proposed final plan commit message after documentation closure:
`Complete fixed-step DSP performance remediation`
