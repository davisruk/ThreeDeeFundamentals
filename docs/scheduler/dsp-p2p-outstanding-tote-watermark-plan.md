# P2P Outstanding Inbound Tote Watermark Plan

Status: implementation in progress; Step 1 is implemented and focused-verified,
later steps remain planned. Created 2026-10-09 against clean commit
`cdce510` (`Wire live release guards and prove the production-shaped journey`).
The planning-session initial `git status --short` was empty. Remain on the
user-selected branch; do not create, switch, merge or commit branches.

This is a separate follow-on to the whole-service-centre/drained-handover plan,
not a redesign of that policy. The user authorizes each step separately.
Authorization to create this document does not authorize implementation. Execute
directly, with no agents unless the user explicitly authorizes them for that step.

## 1. Session handoff and prerequisites

Read `AGENTS.md`, then `docs/codex-instructions.md` completely, and follow its
mandatory document order. Read this entire plan, including shared contracts,
before implementing a selected step. Read these additional prerequisites before
the first implementation step; revisit the relevant boundary for later steps:

- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
- `docs/scheduler/dsp-operational-scheduling-requirements.md`
- Shared contracts in `docs/scheduler/dsp-order-owned-adapting-exception-flow-plan.md`
- Shared contracts in `docs/scheduler/dsp-configurable-station-processing-capacity-plan.md`

The existing whole-centre plan's 2026-10-08 failed/uncommitted Step 6 handoff is
historical relative to `cdce510`. The inspected source contains the corrected
current-centre AV02 allocation gate and Step 6 runtime release guards/scenarios.
Do not reconstruct the former future-centre allocation permission or failing
fixture. This planning session did not run tests and does not certify completion
of that plan's architecture review, documentation closure or full-day validation.
Preserve its release, EMPTY allocation and independently drained-line contracts.

Notation below: `M/` means
`app/src/main/java/online/davisfamily/warehouse/sim/dsp/`; `T/` means
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/`. Every named existing
file must exist. Files marked **new** are intentionally absent from the baseline.
Expand these prefixes before opening or editing files.

Implementation-session request:

> Read AGENTS.md, docs/codex-instructions.md in full and its mandatory document
> order, then this plan in full and the selected step's prerequisites. Implement
> Step N only. Record git status --short before editing, preserve existing changes,
> use apply_patch for every edit, and use no agents, redesign, later steps or
> commits. Run only that step's specified implementation verification command.
> Review the complete step diff against its shared contracts, run git diff --check,
> and report final git status --short, results and unproven criteria. Stop on a
> contradiction, missing named existing file/test or unresolved architectural choice.

After each step, record its actual files, exact verification command/result,
remaining unproven criteria and next authorized boundary in this plan's execution
record. Do not silently replace failed assertions with weaker claims. Allow at
most two mechanical correction/reverification cycles; then stop and report.

## 2. Motivation and agreed scope

The user's full-day run initially processed approximately 13-15 totes per
simulated minute, then slowed substantially at centre 116. Inspection of
`C:\misc\cpas-test\scheduler-testing\progress.log` showed a staggered handover:
at 07:37 only line 5 had moved to 116, with 217 of its 520 OSR totes already
released; other lines were still finishing 108. At 08:36 lines 1-4 had all 31 PRLs
idle while line 5 had 28 non-idle PRLs and the shared transport was full. The
arrival head was bound to line 5. These observations motivate a bounded backlog;
they are not a calibrated throughput model or proof of every stall's cause.

The shared inbound track really is FIFO: a blocked consuming P2P holds following
totes. Preserve that behaviour. Do not bypass/reorder its head. Limit committed
inbound assignments to each line so an early available line cannot accumulate
unbounded inbound work while the others finish the preceding centre.

The user selected a configurable initial default of **8 outstanding totes per
line**, the same positive integer for all five lines. This is a provisional
watermark, not an optimum; all five lines together can have at most 40 outstanding
P2P-assigned inbound totes at that default.

The feature applies only to `WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER`. Preserve
`DEADLINE_AWARE_ELASTIC_STICKY_LEASES`, including its default selection. No measured
throughput, workload weights, pharmacy-volume redistribution, tote-plan rewrite,
hysteresis, completion exception or PRL/bagger workload budget is introduced.

## 3. Shared lifecycle and ownership contracts

### 3.1 Counted interval

An outstanding tote is a physical inbound tote whose P2P assignment has committed
but whose actual tipper completion has not successfully completed. Count OSR and
AV02/EMPTY P2P assignments alike, including routes that visit another station
before P2P. Count assigned totes upstream, in transport, waiting at station/P2P
queues, waiting for the tipper and actively tipping.

Start once, only when the underlying release handler returns `applied()` after
assignment commit. Stop once, only at the real `TipperToteCompletedListener`
callback after the existing lifecycle and station-processing completion chain
succeeds. Evaluation, proposal, AV02 allocation, arrival, tipper-input acceptance,
first pack discharge and elapsed time are not these accounting boundaries.

After actual tipping finishes, downstream packs, PRLs, PCRs, unfinished bags and
outbound totes do not count. Their work still participates in the existing fully
drained-line handover. Reopening this inbound capacity does not transfer a lease,
advance the release centre, close output or imply downstream completion.

An OSR ADAPTED tote released only to upstream STORE without a P2P assignment is
not counted. Its existing release obligation still counts as released. EMPTY
allocation before physical AV02 departure also does not increment this count.

### 3.2 Single mutable owner and publication

Extend `WholeServiceCentreReleaseLedger`; do not introduce a second ledger,
completion controller, scheduler cursor, global cache or runtime-wide registry.
It remains simulation-thread-owned. Workers see only immutable snapshots.

Maintain a configured-line-order `LinkedHashMap<P2pLineId, Integer>` of counts,
initialized to zero, and a private direct physical-tote-ID index of committed
P2P tipping obligations. Each obligation stores the exact immutable
`P2pPhysicalToteAssignment` and a completed flag. Insert only on applied P2P
release. Retain completed entries for this runtime's lifetime to reject duplicate
terminal callbacks; never publish this identity index. This is bounded by that
runtime's committed input, not a cross-runtime cache.

Preserve release `version`: one increment per applied inbound departure only.
Add independent `outstandingVersion`: one increment per committed P2P assignment
and one per successful actual tipper completion. No increment on a rejected,
deferred or failed operation. A STORE-only release changes release version but
not outstanding version. Tipping completion changes outstanding version but not
release version, unreleased source counts, cumulative committed counts or cursor.

Invalidate the ledger's cached immutable snapshot on either kind of successful
mutation. Repeated reads of unchanged state return the same instance. Previously
published snapshots and nested maps never change. Existing release/AV02/planner
suppliers continue to capture this same shared ledger snapshot; no separate copy
of the live accounting is authoritative. Their existing identity-based caches
may invalidate on an actual tipping event, which is a genuine state change.

Constructing a new runtime starts both versions/counts at zero and has no old
callbacks or obligations. Runtime reconstruction remains reset; add no reset API.

### 3.3 Exact APIs and compatibility

Append these components to `WholeServiceCentreReleaseSnapshot`:

```java
long outstandingVersion,
int p2pOutstandingToteWatermark,
Map<P2pLineId, Integer> outstandingP2pToteCounts
```

Add `int outstandingToteCount(P2pLineId lineId)`, an O(1) lookup rejecting null or
unknown configured IDs with `IllegalArgumentException`. Canonical construction
requires nonnegative versions, positive watermark, nonnull line keys/counts and
`0 <= count <= watermark`. Copy counts into an immutable map in configured-line
order, matching the existing committed-count maps' key set/order. For no executable
centres, the ledger still publishes all configured lines with zero counts; permit
that zero-only map. Do not add a whole-day identity scan or extra cross-map total
reconciliation. Preserve all existing snapshot validation.

Keep the existing six-argument snapshot constructor. Delegate it to the expanded
constructor with outstanding version 0, watermark `Integer.MAX_VALUE`, and zero
counts in the first centre's existing line order (empty map if no centres).
Keep the existing three-argument ledger constructor and delegate it to a new
four-argument constructor whose last argument is the positive integer watermark,
using `Integer.MAX_VALUE` for compatibility. These historical domain construction
paths are intentionally uncapped in practical use; production full-day wiring
will explicitly provide the configured value in Step 4.

Add ledger methods:

```java
void validateTippingCompletion(PhysicalToteId physicalToteId, P2pLineId lineId)
void recordTippingCompleted(PhysicalToteId physicalToteId, P2pLineId lineId)
```

Read-only validation rejects null/unknown IDs, uncommitted or completed totes,
wrong line, zero count and outstanding-version overflow with
`IllegalArgumentException`. Recording revalidates before any mutation and wraps
invalid state in `IllegalStateException`; then marks completed, decrements the
exact line once, increments outstanding version and invalidates publication.

Extend `validateUnreleased`'s assignment validation with tipping-ID uniqueness
and outstanding-version overflow checks. Retain exact source, sheet, physical
identity, centre, destination and existing cumulative-counter validation. Normal
watermark exhaustion is not an invalid command and must not become rejection.
`recordApplied` checks remaining capacity before any ledger mutation; calling it
for a capped line is an invariant failure, not silent overfill. After delegate
success its deterministic accounting update inserts the obligation and increments
both line outstanding count and outstanding version once, alongside the existing
release update. Non-P2P commands skip these additions.

The simulation-thread delegate call is synchronous and does not interleave a
tipper callback or another release. Live prevalidation must establish everything
needed for non-failing post-`applied` bookkeeping. Preserve underlying assignment,
bag-pin and physical-departure ownership; do not implement compensating rollback
for a delegate that partially mutates then throws.

### 3.4 Allocation and live acceptance

The pure whole-centre allocator filters a line when
`outstandingToteCount(lineId) >= p2pOutstandingToteWatermark`. Apply this after
existing bag compatibility, first-route admission and lease-owner/availability
checks. Do not change the current ranking of remaining lines: unleased tier,
then cumulative committed tote count, then pharmacy affinity, then configured
order. Outstanding count is a hard filter, not a new balancing score.

Add `P2pLineAllocationBlockReason.OUTSTANDING_TOTE_WATERMARK`. If no line is
selected and at least one otherwise compatible/admissible/current-owner-or-
available line was excluded by the cap, return this reason. If none was otherwise
eligible, preserve `NO_COMPATIBLE_P2P_LINE`; preserve the earlier ineligible-centre
`NO_ELASTIC_LINE_BUDGET` result. A pinned bag whose only eligible line is capped
waits: never break its pin or assign another centre's draining line.

`WholeServiceCentreReleaseCommandHandler` must recheck the proposed line against
the fresh shared ledger snapshot before invoking its delegate. Retain existing
current-centre, exact-command and feeding/available-line checks. A capped proposal
returns `deferredResult` with detail beginning `OUTSTANDING_TOTE_WATERMARK:` and
the line's count/limit. Never retarget a stale proposal inside apply: reevaluate
later even if another line has space. No downstream acceptance, assignment, bag
pin or source departure occurs for a capped proposal. A successful application
uses the same existing delegate then the ledger's post-applied recording.

These rules use existing `P2P_LINE_ALLOCATION` operational block propagation;
no new scheduler/metrics block category. Existing metrics precedence may report
OSR waiting ahead of a P2P block: do not change that precedence or claim every
watermark wait appears in the P2P metrics category.

### 3.5 Completion composition

Create **new** `M/p2p/allocation/WholeServiceCentreP2pToteCompletedListener.java`,
implementing `online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener`.
Constructor, in this exact order:

```java
(P2pLineId lineId, WholeServiceCentreReleaseLedger ledger,
 TipperToteCompletedListener delegate)
```

Require nonnull collaborators and a configured line. In
`onToteCompleted(Tote tote, SimulationContext context)`, require nonnull arguments,
derive `new PhysicalToteId(tote.getId())`, validate the exact ID/line in the ledger,
call the existing delegate first, then record tipping completion. Duplicate,
unknown, uncommitted and wrong-line callbacks fail before delegate invocation.
Delegate failure propagates without decrement; do not invent rollback. A wrong
Tote instance with the same ID is rejected by the existing station-processing
delegate's exact-object check, and must not decrement.

Wrap the existing shared `StationProcessingP2pToteCompletedListener` separately
for each whole-centre line in the full-day factory. Do not replace or reorder its
existing inbound lifecycle, operational lifecycle and station CONSUME completion.
The tipper invokes completion only when tipping/reset/discharges have actually
finished and its existing occupied-path condition permits release. Do not edit
generic tipper/transfer-zone machinery, add polling, or decrement at input
acceptance. The deadline-aware runtime receives its original listener unchanged.

### 3.6 Configuration and report contract

Add one scalar `p2pOutstandingToteWatermark` to the validated command and full-day
profile. Define one default constant on `DspUncalibratedFullDayProfile`:
`DEFAULT_P2P_OUTSTANDING_TOTE_WATERMARK = 8`. Accept integers 1 through
`Integer.MAX_VALUE`; no zero/unlimited switch or per-line object.

- JSON: optional top-level integer `"p2pOutstandingToteWatermark": 8`.
- CLI: `--p2p-outstanding-tote-watermark=8` and paired
  `--p2p-outstanding-tote-watermark 8`.
- Precedence: explicit CLI > JSON > 8. Both CLI forms share singleton duplicate
  detection. Missing, blank, fractional, nonnumeric, zero, negative and overflowing
  values fail. Explicit JSON null, string, boolean, array and object fail too.
- Validate JSON integer type, int range and positivity in the config loader
  before merging overrides. Invalid JSON must fail even with a valid CLI override.
- Parse/report a valid value under either policy, but enable accounting/enforcement
  only under the whole-centre policy. Do not change the scheduler-policy default.

Append the integer component to the command and profile; preserve their existing
19/18/17-argument command and 21/20-argument profile constructor shapes by
delegating to the expanded canonical constructors with default 8. Preserve all
other factory method signatures and defaults. The raw nullable JSON binding must
append `Integer p2pOutstandingToteWatermark`; it is not a new configuration owner.

Report configuration includes `p2pOutstandingToteWatermark` and boolean
`p2pOutstandingToteWatermarkEnabled`. The latter reflects the selected policy,
not whether the value differs from 8. CLI and Main must carry the chosen scalar
through to the profile without dropping timetable/station-processing overrides.

### 3.7 Efficiency and non-goals

Each apply/completion/cap lookup uses maintained counts and direct ID indexes:
O(1) apart from the existing centre-cursor advancement. Pure allocation examines
only configured lines. Snapshot rebuilding occurs only after real state changes,
with the existing bounded centre/line maps; no per-tick reconstruction when
unchanged and no scan of manifests, leases, lifecycle histories, bags or metrics
to derive production outstanding counts. Tests may independently reconcile their
small fixture histories. Report formatting may traverse five published line counts.

No controller registration reorder, machine duration/capacity changes, routing
changes, AV02 FIFO changes, source inventory redesign, generic runtime API changes,
downstream-completion workaround or new threaded mutable access. In particular,
actual line handover still needs all old processing/output work to drain even
when outstanding inbound count is zero.

## 4. Implementation steps

For each step: read its named files completely before editing; record initial
`git status --short`; edit only its owned files plus this plan's execution record;
run only its exact focused command below; review the complete step diff including
new files against Section 3; run `git diff --check` (also check untracked new files);
report final `git status --short` and unproven criteria. No staging or commits.
Do not run full-suite/install/real-data checks reserved to the user.

### Step 1 — Indexed accounting and immutable snapshot

Owned existing files:

- `M/scheduler/policy/WholeServiceCentreReleaseLedger.java`
- `M/scheduler/policy/WholeServiceCentreReleaseSnapshot.java`
- `T/scheduler/policy/WholeServiceCentreReleaseLedgerTest.java`

Create **new** `T/scheduler/policy/WholeServiceCentreReleaseSnapshotTest.java`.
Implement Sections 3.1-3.3 only. Update class descriptions to distinguish release
accounting from the additional actual-tipping interval. No policy/guard/wiring
change. Existing three-argument runtime ledger construction remains uncapped;
the finite configured limit does not activate in production until Step 4.

Required tests:

1. Positive finite limit and configured-line-order zeros; reject nonpositive limit.
2. Successful OSR and generated AV02 P2P IDs increment exactly once; proposal,
   validation, AV02 allocation alone and non-P2P STORE do not increment.
3. Fill a limit-2 line; third `recordApplied` fails before any ledger mutation.
   Complete one exact tote; a later applied release can use the freed slot.
4. Successful completion decrements once, not at arrival or input acceptance;
   unknown/wrong-line/uncommitted/duplicate completion fails without mutation.
5. Release version/cursor/source counts/cumulative balancing counts survive
   completion unchanged; check independent outstanding-version sequence.
6. Unchanged snapshot identity, changed publication on each real event, old map
   immutability, null/unknown query, invalid snapshot count/limit/key sets and
   valid empty-centre publication. Six-argument compatibility has zero counts/MAX.
7. Fresh ledger starts at zero without changing a partially used old ledger.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedgerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshotTest
```

User verification: no additional check for this step. Real callback and full-day
integration remain unproven until Steps 3/4/6.

### Step 2 — Pure capacity filter and live stale-proposal guard

Owned existing files:

- `M/p2p/lease/P2pLineAllocationBlockReason.java`
- `M/p2p/allocation/WholeServiceCentreP2pLineAllocationPolicy.java`
- `M/scheduler/policy/WholeServiceCentreReleaseCommandHandler.java`
- `T/p2p/allocation/WholeServiceCentreP2pLineAllocationPolicyTest.java`
- `T/scheduler/policy/WholeServiceCentreReleaseCommandHandlerTest.java`
- `T/scheduler/operational/DspOperationalReleaseSchedulerTest.java`

Also read `M/p2p/allocation/WholeServiceCentreP2pAllocationPlanner.java`,
`M/scheduler/operational/DspOperationalReleaseScheduler.java` and existing
`T/p2p/allocation/DeadlineAwareElasticStickyP2pLineAllocationPolicyTest.java`;
do not change them. Implement Section 3.4. Keep the guard constructor/API and
scheduler block propagation unchanged.

Tests must prove: cap-1 available line chosen, capped otherwise-best line skipped,
all otherwise eligible lines capped gives typed reason, pinned capped line waits,
no-compatible and foreign-centre reasons unchanged, foreign/draining owner never
reassigned, cumulative ranking/affinity/tie order unchanged among uncapped lines.
Capture a proposal at cap-1; apply another real release to reach the cap; the
old proposal then defers with zero delegate calls and no inventory, assignment,
bag pin or ledger changes. After an actual ledger completion and refreshed
snapshot the command can apply. Do not retarget during apply. Verify applied,
rejected, deferred and throwing delegates and non-P2P current-centre releases.
Verify operational blocked-candidate reason propagation, without changing metrics
precedence or assuming every waiting candidate reaches the P2P gate.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicyTest
```

User verification: no additional check. Production activation remains Step 4.

### Step 3 — Actual-completion listener wrapper

Create **new**:

- `M/p2p/allocation/WholeServiceCentreP2pToteCompletedListener.java`
- `T/p2p/allocation/WholeServiceCentreP2pToteCompletedListenerTest.java`

Read existing `M/p2p/arrival/StationProcessingP2pToteCompletedListener.java` and
`T/p2p/arrival/StationProcessingP2pToteCompletedListenerTest.java` completely.
Also read existing `M/p2p/lease/InboundLifecycleP2pToteCompletedListener.java` and
`M/p2p/lease/OperationalLifecycleP2pToteCompletedListener.java` for the unchanged
lifecycle composition. Copy only small fixture construction into the new test
if existing package-private test helpers are inaccessible; do not widen them.
Read `app/src/main/java/online/davisfamily/warehouse/sim/totebag/control/TipperToteCompletedListener.java`
and `ToteTrackTipperFlowController.java` in that same directory. No existing
production listener or generic machine changes. Implement Section 3.5, without
runtime wiring in this step.

Tests: real ledger with applied OSR/AV02 assignments; normal callback delegates
while count is still 1 then decrements to 0; invalid/duplicate callback invokes
no delegate; wrong line and uncommitted tote fail; null collaborators/arguments
fail; throwing delegate leaves count/version unchanged. Use the real existing
station-processing/lifecycle listener composition for an exact-object regression:
successful callback consumes the claim and lifecycle before the count drops;
wrong object with the same ID fails without decrement. An input-acceptance-only
journey still has count 1. No synthetic success after delegate failure.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pToteCompletedListenerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListenerTest
```

User verification: no additional check. Factory composition remains unproven.

### Step 4 — Configuration and atomic full-day activation

Owned existing production files:

- `M/analysis/DspFullDayAnalysisCommand.java`
- `M/analysis/DspFullDayAnalysisConfigJson.java`
- `M/analysis/DspFullDayAnalysisConfigLoader.java`
- `M/analysis/DspFullDayAnalysisCommandParser.java`
- `M/analysis/DspFullDayAnalysisMain.java`
- `M/analysis/DspUncalibratedFullDayProfile.java`
- `M/analysis/runtime/DspFullDayAnalysisRuntimeFactory.java`
- `M/analysis/report/DspFullDayReportFactory.java`

Owned existing tests:

- `T/analysis/DspFullDayAnalysisCommandTest.java`
- `T/analysis/DspUncalibratedFullDayProfileTest.java`
- `T/analysis/runtime/DspFullDayAnalysisRuntimeFactoryTest.java`
- `T/analysis/report/DspFullDayReportFactoryTest.java`

Implement Section 3.6. Add allowed JSON property and strict validation before
binding; use the parser's existing positive-integer helper and scheduler-policy
paired-option pattern. Update all canonical calls in these owned files. Existing
callers outside this step must compile through compatibility constructors, not
through repository-wide mechanical rewrites.

Also read existing `M/analysis/runtime/DspHeadlessP2pLineConfig.java`; its existing
constructor/listener accessor is the wiring boundary and must not change.

In the factory's whole-centre branch, construct the existing optional shared
ledger with the explicit profile watermark. In its five-line loop, wrap the
existing shared station-processing completion listener with the new per-line
listener and pass that to `DspHeadlessP2pLineConfig.toteCompletedListener`.
Pass the same ledger already used by allocation, release guards and AV02 gating.
The deadline-aware branch still passes the original listener with no ledger or
watermark enforcement. Activate finite limits and real decrementing together in
this step; never leave an enabled cap with no completion callback.

Tests: default 8, JSON-only, both CLI forms, CLI-over-JSON, duplicate across forms,
missing/invalid values, JSON null/wrong types/fractional/overflow/nonpositive even
when CLI overrides. Boundary positive ints accepted. All preserved constructor
shapes default to 8; direct canonical construction rejects nonpositive values.
Profile selection and config report scalar/enabled flag correct for both policies;
station/timetable overrides retained. A bounded real factory fixture using a
custom cap 1 observes no overfill and eventual count reopening through the actual
machine callback (not manual ledger completion). Fresh runtime has zero counts.
Keep existing runtime topology/registration and baseline policy tests passing.
Build the custom-cap test profile through the expanded canonical constructor in
the owned factory test; do not use the old `withPolicy` helper to carry a custom
value until Step 6 explicitly updates that helper.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfileTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest
```

User verification: no additional check yet. Do not edit/run external full-day
configuration; real-data comparison is reserved to Step 6.

### Step 5 — Bounded diagnostics at existing report boundaries

Owned existing production:

- `M/analysis/report/DspFullDayProgressFormatter.java`
- `M/analysis/report/DspFullDayReportJsonWriter.java`

Owned existing tests:

- `T/analysis/report/DspFullDayProgressFormatterTest.java`
- `T/analysis/report/DspFullDayReportJsonWriterTest.java`
- `T/analysis/DspFullDayInspectionFormatterTest.java`

Read both existing inspection formatters (`M/analysis/DspFullDayInspectionFormatter.java`
and `M/analysis/report/DspFullDayInspectionFormatter.java`) and preserve their
delegation. The shared `wholeServiceCentreLine` formatter already serves progress
and inspection; do not duplicate its implementation.

Append `p2pOutstandingToteWatermark=8 outstandingP2pTotes={dsp-p2p-line-1=0, ...}`
before the existing `deadlinesAndWorkloadCosts=diagnosticOnly` suffix. Render every
configured line, including zeros and draining old owners, in configured order,
using `lineId.value()` rather than record `toString`. At the existing JSON
`elastic.wholeServiceCentrePolicy.releases` node append `outstandingVersion`,
`p2pOutstandingToteWatermark` and `outstandingP2pToteCounts` with string line keys.
Use only the report's captured immutable snapshot, not live suppliers/ledger or
lease-history scans. No metrics record/category, sampling cadence or runtime
snapshot-supplier change. Deadline-aware output retains its existing shape.

Tests: complete ordered zero/nonzero counts, captured old snapshot unaffected by
later mutation, same progress/inspection meanings, exact JSON scalars/map and
versions, absence of whole-centre metadata under deadline-aware policy. Existing
deferred NS and shared outbound-summary assertions remain unchanged.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInspectionFormatterTest
```

User verification: no additional check; later full-day output supplies calibration
evidence, not this formatter test.

### Step 6 — Production-shaped lifecycle and staggered-handover regressions

Create **new** `T/analysis/runtime/DspFullDayP2pOutstandingToteWatermarkScenarioTest.java`.
Modify existing `T/analysis/runtime/DspFullDayWholeServiceCentreScenarioTest.java`
only to carry the scalar in its `withPolicy` helper and add accounting assertions
to its mixed STORE/COLLECT/EMPTY journey. No production changes in this step.

Read that existing scenario completely and reuse/copy its bounded private input,
planning and profile fixture patterns; no production helper refactor. Construct
input through `LoadedDspData`, `DspFullDayInputPreflight`,
`DspFullDayBagPlanningRequestFactory` and `DeterministicBagPlanner`; drive the real
`DspFullDayAnalysisRuntimeFactory` with `runtime.update(1d)`. Do not manipulate
leases, machine queues, completion callbacks or ledger counts manually.

Common fixtures: five configured lines, 31 PRLs per line, OSR capacity 32,
low-watermark 4, inbound interval 1 second, route speed 100, preloaded relevant
centres and otherwise the existing journey-profile settings. Distinct physical
IDs, logical order IDs and order-sheet carriers; independent prescriptions/bag correlations for
independent tote assignments. Use outcome-based loops capped at 2,000 updates per
runtime with bounded diagnostics on failure, no sleeps or external day data.

Scenario A: cap 1, one centre 104, 12 FULL_PACK totes each with one distinct
one-pack prescription. Use placeholder durations
`(40, 3, 1, 1, .1, .1, .1, .1, .1, 90, .1)` in the existing record order
(90 seconds is bag sealing). Assert all five lines seed, no count ever exceeds
1, and no sixth P2P commit occurs before any actual tipper completion. Observe
actual completion reopening capacity and further commits while downstream bag
work is still pending. Observe all inbound counts zero after the final tipper
completion while at least one bag is still unfinished. Eventually require
supported-work completion, all 12 exact bags/manifest membership and zero counts.
Final release version is 12 and outstanding version 24. Never require a visible
intermediate zero count when completion and replacement release share an update.

Scenario B: cap 2, centre 104 has five independent totes; its first four contain
12 distinct one-pack prescriptions each and the fifth contains one. Centre 108
has 12 independent one-pack totes. Preload both, preserve source sequence and
priority, use the existing slow tipping/fast bagging journey durations
`(40, 3, 1, 1, .1, .1, .1, .1, .1, .1, .1)`. Require a witness where line 5 alone
has moved to 108 while lines 1-4 still own 104. Observe line 5 reach outstanding
2 and a typed watermark wait before its first 108 tote finishes tipping. No
assignment to an old foreign owner and no count over 2. Once other lines fully
drain, further 108 assignments use them and supported work completes. Do not
assert cumulative line-5 commits stay at 2: finishing/reusing slots is permitted.
Keep all existing old-centre drain/output-close and bag-pin contracts.

For each update independently reconcile each published count against the
fixture's physical assignment history and lifecycle records: assigned totes not
yet `CONSUMED_AT_P2P` count, consumed ones do not. This is a small test-only
oracle; never implement that scan in production. Historical assignments from
previous owners are consumed and do not contribute; STORE-only releases have no
P2P assignment. Read captured lifecycle/lease snapshots, not mutable internals.
The exact oracle inputs are `runtime.snapshot().elastic().leases().lines()`, each
line's `physicalAssignments()`, and
`runtime.lifecycleSnapshotSupplier().get().totes().get(assignment.physicalToteId()).state()`.
Require a lifecycle record for every assignment; a missing record is a test
failure, not an implicitly completed tote. Read existing
`M/p2p/lease/P2pLineLeaseSnapshot.java`,
`M/lifecycle/PhysicalToteLifecycleSnapshot.java` and
`M/lifecycle/PhysicalToteLifecycleState.java` before writing the oracle.

Extend existing
`shouldHoldFutureEmptyAllocationThroughUpstreamStoreFirstCollectAndLastCurrentEmptyDeparture`
with default-8 bound/oracle assertions at each update, STORE-only exclusion,
generated AV02 physical ID inclusion until its real completion, and final zero
counts. Preserve its first-COLLECT, overpick, PDC, deferred NS, final EMPTY/bag
proofs and release-version expectations (including 4 and 6). Update `withPolicy`
to pass through `base.p2pOutstandingToteWatermark()` to the expanded constructor;
do not let a custom value silently revert to 8.

Add deadline-aware comparison on Scenario A's small input at configured 1 and 8:
same stable assignment/release sequence, physical output and terminal result,
with no whole-centre metadata. Compare simulated identities/outcomes, not wall
time. Add reconstruction regression: close a partially advanced whole runtime,
construct a fresh one for the same input/profile, and require initial zero counts,
zero outstanding version and no carried callbacks. No mutable reset method.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayP2pOutstandingToteWatermarkScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeControllerTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseCommandHandlerTest
```

User verification (user-run only):

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test
.\gradlew installDist
& 'C:\Java\jdk\21.0.7\bin\java.exe' -cp 'app\build\install\app\lib\*' online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisMain --config=C:\misc\cpas-test\scheduler-testing\config\scheduler_conf.json --scheduler-policy=WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER --p2p-outstanding-tote-watermark=8 --output=C:\misc\cpas-test\scheduler-testing\report_watermark_8.json --inspection-output=C:\misc\cpas-test\scheduler-testing\inspection_watermark_8.txt --progress-log=C:\misc\cpas-test\scheduler-testing\progress_watermark_8.log
```

Use fresh output paths if these already exist; do not enable overwrite or replace
the original `progress.log`. Config itself need not be edited because CLI
overrides it. The implementation model must not run this command or write these
external paths. User may later choose other positive values for comparison.

Confirm reported configured/enabled values, each sampled count <=8, consistent
final lifecycle/count accounting, supported completion or honestly reported
cutoff, and preserved deferred NS visibility. Sampling alone does not prove
between-sample safety; the deterministic tests/live-guard contract provide that.
Compare centre 104/108/116 throughput and line distribution at like simulated
times and workload against the prior run. Record observed improvement or lack
of it without a prescribed throughput assertion. Any PRL saturation, residual
FIFO head blocking or poor pharmacy mix is evidence for later work, not permission
to add unfinished-work budgets here. Full-day completion and performance benefit
remain unproven until the user supplies results.

### Step 7 — Read-only architecture acceptance

No production/test edits or new design. Read the complete feature diff from
`cdce510` and each touched runtime boundary; distinguish pre-existing whole-centre
work from this feature. Record PASS, FAIL or UNPROVEN with file/test evidence for:

1. Exactly one owner/shared publication; no mutable worker access.
2. O(1) maintained counts/identity lookup; state-change-only publication.
3. All configured lines including zeros; immutable historical snapshots.
4. Count starts only at successful committed P2P release, including AV02.
5. Count ends only at actual successful tipper completion, not input acceptance.
6. No remaining PRL/PCR/bag work included and no early lease handover.
7. Independent versions; release counts/cursor/cumulative ranking unchanged.
8. Pure cap filter and live stale-command deferral before any acceptance.
9. Exact-ID/line and existing exact-object validation; duplicate/failure safety.
10. Strict configurable default-8 scalar/precedence/reporting and old APIs.
11. Deadline-aware runtime, FIFO, bag pins and controller order unchanged.
12. Reconstruction isolation, bounded deterministic journeys and honest external
    throughput/full-day claims.

Implementation verification: no model-run Gradle command. Read/reconcile the
recorded focused results, inspect complete diff, run `git diff --check` and report
status. A mechanical acceptance report may PASS while external performance is
explicitly UNPROVEN; never certify user-reserved results. A failed architectural
criterion stops closure and needs user direction, not a speculative refactor.

User verification: review acceptance report and Step 6 full-suite/install/day
results. No additional machine/visual run required for this headless feature.

### Step 8 — Documentation closure only

Owned existing documents:

- This plan and `docs/codex-instructions.md`
- `docs/codex-context.md`
- `docs/scheduler/dsp-operational-scheduling-requirements.md`
- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
- `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`
- `docs/scheduler/dsp-whole-service-centre-drained-handover-plan.md`

Update only affected current contracts/status/handoffs: exact counted interval,
whole-policy-only positive scalar/default8/CLI and JSON forms, maintained owner
and two versions, reporting, unchanged fully drained handover, actual verification
and deferred calibration/PRL concerns. Reconcile obsolete whole-plan Step 6
handoff with committed-source and recorded test evidence; do not mark its separate
review/closure passed without evidence. Preserve historical results as dated
history and do not rewrite unrelated completed implementation steps.

Implementation verification: no model-run Gradle command. Read the complete
documentation diff, check links/named paths and factual consistency with accepted
source/results, run `git diff --check`, report final status. No code changes.

User verification: review updated handoff and unresolved criteria. Do not label
the feature calibrated, universally bottleneck-free or full-day complete unless
the corresponding user evidence exists. Architecture implementation closure may
be recorded separately from remaining full-day/performance validation.

## 5. Execution record

Planning only, 2026-10-09: user chose default 8 outstanding totes per line.
No feature implementation or Gradle verification performed. Steps 1-8 await
separate authorization. Baseline `cdce510` is evidence of source presence, not a
test result. All feature runtime, regression, safety and performance criteria
remain unproven at plan creation.

Planning review: complete new plan and tracked handoff diff reviewed against the
agreed boundaries and inspected source; named existing files and verification
test classes checked for presence, with proposed new files explicitly identified.
No production/test edits, agents, commits or external output/configuration writes.

Step 1 completed 2026-10-09. Modified
`M/scheduler/policy/WholeServiceCentreReleaseLedger.java`,
`M/scheduler/policy/WholeServiceCentreReleaseSnapshot.java`, and
`T/scheduler/policy/WholeServiceCentreReleaseLedgerTest.java`; created
`T/scheduler/policy/WholeServiceCentreReleaseSnapshotTest.java`. The ledger now
maintains per-line outstanding counts and exact committed-tote completion state,
with independent release/outstanding versions and immutable cached publication.
The existing three-argument ledger and six-argument snapshot constructors remain
compatible and use `Integer.MAX_VALUE` watermark.

Implementation verification run exactly as specified:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedgerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshotTest
```

Result: `BUILD SUCCESSFUL`; both named test classes passed. Reviewed the complete
Step 1 diff and ran `git diff --check`. Step 1 does not wire the configured finite
limit into the full-day runtime or attach actual tipper callbacks; those remain
unproven until their separately authorized later steps. Step 2 awaits separate
user authorization.
