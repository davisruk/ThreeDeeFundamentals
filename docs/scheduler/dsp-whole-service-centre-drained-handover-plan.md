# Whole-Service-Centre Release and Drained-Line Handover Plan

Status: implementation in progress; Step 6 is incomplete and not ready for user
verification. Created 2026-10-08 against clean commit
`d8524b2` on `feature/dsp-full-day-analysis-metrics-inspection`. Remain on the
user-selected branch; do not create, switch, merge or commit branches automatically.
The user initiates each step separately. Planning authorization is not implementation
authorization. Execute directly; agents require the user's explicit request for the
current step.

## Session handoff (2026-10-08): Step 6 correction pending

The user approved the plan correction recorded below, but explicitly deferred its
implementation until a later session. This handoff is not authorization to edit
code or tests, run Gradle, or begin Steps 7/8. Await an explicit request to resume
the corrected Step 6. Read this entire plan and its prerequisites afresh then.

Steps 1-5 are present in the current source baseline. Step 6 production wiring and
tests are present as uncommitted work: seven modified production files, four modified
test files and three new untracked files (the release guard and its two new test
classes). Record fresh `git status --short` and preserve all of that work; do not
recreate the implementation or discard the failing scenario. This revision changes
only this plan, not any production or test file.

Latest implementation verification used the original Step 6 seven-class command on
2026-10-08: 65 tests, 64 passed, one failed; Gradle `BUILD FAILED`. The sole failure
is `DspFullDayWholeServiceCentreScenarioTest.`
`shouldHoldPreloadedAndAllocatedFutureWorkThroughUpstreamStoreFirstCollectAndLastEmptyDeparture`:
`IllegalStateException: Physical tote is not at the head of AV02 inventory: av02-000002`.
The scenario's helper now gives each sheet a distinct notional carrier ID while
retaining its logical order identity. That fixture-only correction removed an
irrelevant legacy logical sheet-sequence stall and exposed the AV02 ordering defect.

Cause: 104's EMPTY awaits ADAPTED preparation while an independently ready,
authorized 108 EMPTY allocates first. The new release gate holds 108, but AV02's FIFO
then prevents the subsequently allocated 104 tote from departing. The existing
handler reaches target acceptance and assignment commit before the FIFO departure
throws; do not mask this failure or fake rollback. This is advance allocation before
handover, not a newly discovered 104 obligation after 108 starts processing.

The user confirmed that AV02 must not allocate 108 while 104 remains the release
centre. The corrected Step 6 below replaces the former permission to allocate
future-centre EMPTY totes; it preserves FIFO and old-policy behavior. Update the
scenario's superseded positive `futureAllocated` expectation, not its delayed
STORE/COLLECT/EMPTY journey or completion proof. The complete original Step 6 diff
was reviewed and `git diff --check` passed before this planning revision. The revised
implementation, revised focused command, user regression/install and full-day run
are all unproven. The prior checkpoint exhausted its correction cycles; this
architectural correction was not implemented as another speculative retry.

## 1. Purpose and agreed meaning

Create a selectable, deadline-independent policy **set**,
`WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER`, as a conservative operational baseline.
Keep `DEADLINE_AWARE_ELASTIC_STICKY_LEASES` selectable and preserve its allocation
algorithm. Select the complete compatible set, not individual policy components.

Process centres in descending configured priority, with service-centre ID ascending
as the deterministic tie-break. Make all available P2P lines usable by the current
centre and actually spread independent new assignments across them. Do not calculate
line demand from deadlines, workload weights, safety factors or observed throughput.
Deadlines remain reporting inputs; the existing hard cutoff remains run control.

"The current centre has been processed" means **every executable inbound release
obligation has committed**, not every bag is complete. Count all executable OSR
manifests, including ADAPTED, and every executable EMPTY sheet's AV02 departure.
Count obligations still held upstream or not yet allocated. Do not infer completion
from current OSR occupancy, logical order status, supply authorization, tote selection,
AV02 allocation, or an empty local queue.

For the new set, AV02 EMPTY allocation also stays within that current release
centre. If its EMPTY work is unready, wait; do not allocate ready EMPTY work from
a later centre. All executable EMPTY obligations are known from input before the
run; readiness becoming true later is not discovery of a new obligation. This rule
concerns inbound EMPTY carriers, not independently allocated P2P outbound totes.

No next-centre processing release occurs before that global release boundary.
Afterwards, a line transfers independently only after no old-centre inbound tote is
still due to it, its entire processing path drains, and its output tote closes.
Another line may still finish the old centre. Initially do **not** enqueue new-centre
totes behind old-centre totes on a leased line, even in sequential FIFO order.

OSR low-water backfill already exists. Leave its authorization, rate, capacity,
preload and ADAPTED-first supply order unchanged. Storage admission for a later
centre is not permission to release that centre into DSP processing.

NS means **Not Supplied**. The user confirmed that unresolved product-master lines
are candidates for future NS handling: Exceptions prints special labels associated
with the bag; store staff fulfil those lines locally. A missing product-master row
does not prove a physical wholesaler short pick. Until Exceptions exists, retain
these as explicit NS candidates excluded from executable work, not resolved lines.
Do not manufacture packs, bags, labels, outbound totes or station visits for them.

## 2. Fresh-session reading and execution gate

Do not reconstruct decisions from chat history or a compacted summary. Before the
first action of every implementation session:

1. Read `AGENTS.md`.
2. Read `docs/codex-instructions.md` **completely** and follow its mandatory reading
   order. Its named route-continuation document is
   `docs/scheduler/dsp-station-route-continuation-plan.md`.
3. Read this entire plan, including shared contracts, all steps, review and closure.
4. Read these current domain authorities completely, in this order:
   - `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`;
   - `docs/scheduler/dsp-operational-scheduling-requirements.md`;
   - `docs/machines/exceptions-station-requirements.md`.
5. Read the shared contracts of
   `docs/scheduler/dsp-order-owned-adapting-exception-flow-plan.md` and the complete
   `docs/scheduler/dsp-configurable-station-processing-capacity-plan.md`. These explain
   current bin ownership, first-COLLECT, partial bags and configurable station capacity.
6. Read the selected step and its named source/tests. Relevant boundary references:
   `dsp-p2p-sticky-line-leases-plan.md`,
   `dsp-deadline-aware-elastic-line-allocation-plan.md`,
   `dsp-rate-limited-service-centre-supply-plan.md`,
   `dsp-osr-processing-release-plan.md`, `dsp-av02-operational-allocation-plan.md`,
   and `dsp-outbound-tote-allocation-plan.md`, all under `docs/scheduler/`.
   Read their contracts when the selected step touches that boundary. Read
   `docs/tote-to-bag-requirements.txt` and `docs/bagging_machine_requirements.txt`
   before the runtime/handover integration step.

The entry documents contain historical status paragraphs. Current requirements and
this plan govern this feature; historical plans remain records, not instructions to
reimplement completed work. Order-owned bins, order-wide preparation, first-COLLECT,
physical membership validation independent of arrival order, and configurable
station processing are present in the inspected baseline. This does not declare
outstanding review/closure steps of other plans complete.

Before editing, record `git status --short`; preserve all existing changes. Use
`apply_patch` for every edit. A named **existing** file/test absent, material source
contradiction, or unresolved architectural choice requires stopping and reporting.
Files explicitly marked **new** below are to be created, not treated as missing.
Do not silently revise this plan during implementation. Do not commit.

Run only the selected step's implementation command, with ordinary Gradle output.
User verification is separately owned. After implementation, review the **complete
step diff** against every shared and step contract, run `git diff --check`, and
report final `git status --short`. Stop before the next step. Follow the repository's
two-correction-cycle limit. Report files, identities/ownership, compatibility,
tests, exact verification result, unproven criteria and readiness for user verification.

## 3. Inspected baseline and boundaries

Java source root (`M`) is
`app/src/main/java/online/davisfamily/warehouse/sim/dsp/`.
Test root (`T`) is
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/`.
Paths below relative to M/T refer to those exact roots.

- `analysis/DspFullDayAnalysisMain.profile` always builds the deadline-aware profile;
  command/config records and parser have no policy selection.
- `analysis/DspUncalibratedFullDayProfile` has a 20-component constructor and fixed
  policy identifiers. Preserve that constructor through delegation when adding selection.
- `p2p/allocation/DspP2pElasticAllocationRuntimeFactory` owns the allocation factory
  and composes sticky leases. `DspP2pElasticAllocationRuntime` publishes the resulting
  immutable leases/allocation pair. Reuse these composition and reporting carriers;
  their historical "elastic" names do not make deadlines mandatory for the new set.
- `P2pElasticAllocationSnapshot` currently accepts only the deadline-aware identifier.
  `P2pServiceCentreLineDemandSnapshot.requiredLines` must be at least one. The new
  planner publishes a demand only for the current release centre, not zero-demand
  placeholder rows for retired centres. Do not weaken that value validation.
- `runtime/operational/DspOperationalReleaseRuntimeFactory` checks the old identifier
  in two elastic entry points and constructs the OSR/AV02 command handlers.
- Those handlers obtain downstream acceptance before committing assignment/departure.
  The generic handlers and the machine controllers remain unchanged. Add a bounded
  outer command handler for live centre validation and successful-release accounting.
- `av02/Av02AllocationSnapshotFactory` currently skips blocked higher-priority work
  and selects the first eligible centre. That remains the old-policy behavior, but
  is incompatible with the new release barrier and AV02's FIFO. Step 6 adds a
  snapshot-based current-centre gate to EMPTY allocation and its fresh revalidation.
- Sticky assignment history remains inspectable after a lease changes owner. It is
  **history**, not a list of currently outstanding arrivals. Filter by current owner
  and the remaining-work projection before using it for drain decisions.
- `analysis/input/DspFullDayInputPreflight` already excludes unknown-product lines
  and the affected ADAPTED/fulfilment pair. Known sibling lines remain executable.
  An all-excluded sheet creates no executable physical tote. Do not alter this partition.
- `CompletionSnapshotSource.indexUnsupportedWork` currently makes unresolved-product
  diagnostics blocking unsupported work. Both completion Observation and completion
  snapshot independently validate completion predicates; keep those contracts aligned.
- All five line snapshots share one outbound allocator. The runner's centre summary
  currently counts it five times. Step 1 repairs reporting, not allocation ownership.

## 4. Shared fixed contracts

### 4.1 Identity, ownership and compatibility

Preserve physical tote IDs, exact manifest multiplicity, incoming order/sheet keys,
prepared-line keys, immutable source provenance, bag keys/correlations, derived output
sheet numbering, pharmacy/service-centre purity, and hard bag/tote pinning. No
reassignment of committed work, no pack migration between lines, no global patient
or pharmacy pinning rule added by this feature. Pharmacy affinity remains preference.

ADAPTED first under contested release capacity, FULL_PACK parallel eligibility,
order-wide preparation readiness, designated first-COLLECT and physical overpick/
underpick behavior remain unchanged inside the selected centre. Generic P2P machinery,
PCR's one-group baseline, timings, transport FIFO/arrival ownership, station claims,
continuation and EMPTY allocation mutation mechanics remain unchanged. The only
EMPTY selection change is the new-set current-centre gate specified in Step 6;
the old set retains its eligible-centre fallback. Do not tune timings to
prove throughput. Keep UNCALIBRATED and provisional P2P output closure.

Old public constructor shapes and runtime-factory overloads delegate to their existing
deadline-aware behavior. Old selection does not instantiate the new release ledger
or execute the new eligibility/allocation/retention strategies. Do not edit
`DeadlineAwareElasticP2pAllocationPlanner`,
`DeadlineAwareElasticStickyP2pLineAllocationPolicy` or `ElasticP2pLeaseRetentionPolicy`.
NS completion/reporting correction is deliberately shared by both sets; preserving
the old allocation algorithm does not preserve its erroneous NS completion blockage.

### 4.2 Released obligations and handover

Index executable obligations once at initialization. OSR obligation identity is exact
`PhysicalToteId`; AV02 obligation identity is exact EMPTY `OrderSheetKey` before a
physical tote is allocated. Multiple manifests of one sheet remain separate OSR
obligations. A committed departure counts once, only after the underlying handler
returns `applied()`. Failed, deferred, rejected, stale and duplicate commands never
advance the centre cursor or counters. No counts from completion/logical state.

The earliest centre with an unreleased obligation is the release centre. It cannot
be skipped because blocked, held upstream, not supplied yet or not allocated at AV02.
Apply that same release-centre identity to new-set AV02 allocation; supply
authorization for a later centre does not permit its EMPTY carrier allocation.
Once the cursor advances, that next centre may allocate ready EMPTY totes even
while predecessor lines drain; physical departure remains subject to the separate
release/line-availability gate. Do not use `eligibleServiceCentreId` to decide
EMPTY allocation or add a second centre cursor.
Centres with no executable obligations are absent from the release sequence; retain
their NS/reporting evidence. A wholly non-executable dataset still follows the existing
input-loader rejection; this feature does not add an empty-run mode.

The release gate requires the release centre to be authorized **and** to have at least
one owned usable line or an unleased quiescent line. It gates ADAPTED as well as
P2P-required releases. When all its obligations commit, advance to the next centre;
do not permit that centre to use a line still owned by its predecessor. A line owned
by a finished-release centre is retained until all its pinned inbound work is consumed,
all processing drains, and its outbound tote closes. Close and release remain separate
simulation updates through `P2pLeaseReleaseController`.

### 4.3 Efficient publication and thread contract

Mutable accounting, caches and commands are simulation-thread-owned. Worker evaluation
receives only immutable snapshots; it never reads the ledger, registry, supply or
machine objects. Build stable obligation/owner/order indexes once, then maintain
counts with committed release events. Eligibility is O(1) per candidate plus at most
five line checks. Do not scan all manifest/EMPTY obligations on every fixed step.

The ledger returns the same immutable state snapshot when its mutation version is
unchanged. Snapshot collections are defensively copied only on publication changes;
older snapshots cannot change. Do not copy obligation identity sets into every snapshot.
Line assignment-count lookup for allocation is precomputed, not a stream over lease
history per candidate. Cache retention's pending-by-line projection by actual work and
assignment-list identities, not the freshly allocated enclosing lease snapshot.
Activity checks remain fresh. The existing workload projection caches remain in use;
new strategies do not add whole-day scans, duplicate bag planning, renderables, global
caches, polling controllers for counts or object pools.

Wall time is report-only; no sleeps or wall-time thresholds in policy tests. Use
snapshot identity, immutable old snapshots, callback counts and ordered transitions
to prove efficiency and no-mutation behavior.

### 4.4 Interim NS semantics

Retain `loadReport.unresolvedProductLines` and `reportableOrders` unchanged. Count
**input-line occurrences**, not distinct products, physical missing packs, prescriptions
or NS labels. ADAPTED/source and fulfilment occurrences may both be represented; do
not deduplicate them into an invented clinical/physical NS total.

For interim supported-work completion, remove only the typed unresolved-product
classification from blocking unsupported work. Do not parse diagnostic strings to
decide which entries are nonblocking. MANUAL/omitted-work and other unsupported
conditions keep their current behavior. Add separate NS-candidate counts and pending
reporting. Existing physical missing-pack/PDC/affected-bag/marked-tote/pending-empty-bag
counts must not change. NS candidates imply output closure WITH_EXCEPTION for an
existing runtime centre when actual supported processing completes, but do not mark
an arbitrary physical outbound tote or claim Exception processing occurred.

## Step 1. Correct the shared outbound summary count

Read M `analysis/DspFullDayAnalysisRunner.java`,
`analysis/runtime/DspHeadlessP2pLineRuntime.java`,
`analysis/runtime/DspFullDayAnalysisRuntimeFactory.java`,
`outbound/OutboundAllocationSnapshot.java`, and T
`analysis/DspFullDayProgressOutputTest.java`,
`analysis/DspFullDayAnalysisRunnerTest.java`.

Modify only the runner's `closedOutboundTotesByServiceCentre` reporting helper and
those tests. Count a closed tote once by exact physical tote ID and an allocated bag
once by BagKey across supplied snapshots. Validate repeated identities have identical
published values; conflicting duplicates fail clearly. Skip repeated snapshot objects
before traversing them, using an identity-based set local to this report call. This
supports the shared full-day allocator and genuinely independent fixture allocations.
Use the existing value equality of `OutboundToteSnapshot` and
`AllocatedOutboundBag` for duplicate checks, not enclosing snapshot equality or
collection iteration order. Keep insertion order of the configured centre output.
Keep closed-tote counts distinct from all allocated-bag counts, including bags in open
totes. Do not change the allocator or replace line snapshots with line-local owners.

Required tests: five references to one snapshot; equivalent distinct snapshots;
independent snapshots; overlap in identities; conflicting duplicate tote/bag;
allocated bag in an open tote; deterministic zero entries and service-centre order;
existing progress and wall-clock formatting remains compatible. The five-line case
must produce 491/3228 rather than 2455/16140 with representative identities/counts
(small fixtures with the same multiplication failure are sufficient).

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayProgressOutputTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest
```

User verification: no additional check required for this reporting-only step.

## Step 2. Separate deferred NS candidates from blocking unsupported work

Read M `io/DspDatasetAssembler.java`, `io/UnresolvedProductLine.java`,
`analysis/input/DspFullDayInputPreflight.java`, `analysis/DspFullDayInputLoader.java`,
`analysis/DspFullDayLoadedInput.java`,
`analysis/DspFullDayCompletionEvaluator.java`,
`analysis/DspServiceCentreCompletionSnapshot.java`,
`analysis/runtime/DspFullDayAnalysisRuntimeFactory.java` (CompletionSnapshotSource),
`analysis/report/DspFullDayProgressSnapshot.java`, `DspFullDayProgressFormatter.java`,
`DspFullDayAnalysisReport.java`, `DspFullDayReportFactory.java`,
`DspFullDayReportJsonWriter.java`, `DspFullDayInspectionFormatter.java`, all in
`analysis/report/` for the last six filenames. Read the existing T classes named in
the command below and `analysis/input/DspFullDayInputPreflightTest.java`.

Create M `analysis/input/DspDeferredNsCandidateCatalog.java`. Constructor accepts
`DspFullDayLoadedInput`; retains its immutable unresolved-product list and reportable
orders by reference, precomputes a deterministic immutable count map by centre, and
exposes `inputLineCount()`, `inputLineCountByServiceCentreId()` and
`inputLineCountFor(String)`. Do not recreate/enrich physical pack or bag plans. Return
stable collection identities. Original input/reportable data retains patient,
prescription, pharmacy, sheet, quantities and line identity for future NS work.

At runtime construction create one catalog. In `indexUnsupportedWork`, stop adding
unresolved-product strings to blocking per-centre unsupported lists; do not change
other entries. Existing load report and unresolved warning generation remain intact.

Append nonnegative `int nsCandidateInputLineCount` to completion Observation and
`DspServiceCentreCompletionSnapshot`. Preserve **both** former exception-aware and
pre-exception constructor shapes through delegation with zero. Pass the catalog's
count for each runtime centre. NS counts do not enter the physical completion-zero
predicates. Include count > 0 in WITH_EXCEPTION closure determination in **both**
evaluator and snapshot validation. Keep completion times/outcomes and cutoff mechanics.
Metrics continue reading the original completion snapshot; no new per-tick NS scans.

Add report/progress methods `nsCandidateInputLineCountByServiceCentreId()` and
`completedWithNsCandidates()`. The map is based on the typed load-report list at
report/progress projection boundaries (not machine ticks). The completion flag is
true only when state is `ALL_SUPPORTED_WORK_COMPLETE` and total NS count > 0;
it is false while running, at hard cutoff and after failure. Keep the existing
`completedWithInputExclusions` meaning for rejection-catalog exclusions; do not combine
distinct classifications under that flag. Add the NS map and flag to report JSON,
one bounded `NsCandidatesPendingByServiceCentre:` progress line, and inspection.
Include `nsCandidateInputLines:n` alongside existing per-centre exception counts.
Report-only NS centres appear in the map even if no executable centre row exists;
do not fabricate supply/inventory/completion rows. Warn that NS labels/Exception
completion have not happened. Existing unresolved warning strings remain available.

Update the existing scenario
`shouldDeferUnresolvedProductByServiceCentreAtHardCutoff`: rename to describe supported
completion with deferred NS, expect supported completion rather than hard cutoff,
and prove the excluded order/pack still does not exist. Add scenarios for mixed
known/unknown lines, both source/fulfilment occurrences, NS-only centre alongside a
supported centre, and NS plus independently blocking MANUAL/unsupported work. The
last must still reach cutoff/incomplete. Preserve physical missing-pack exception
tests. Old constructors give zero NS and unchanged ordinary closure. Catalog identity
and immutability tests are required in new T
`analysis/input/DspDeferredNsCandidateCatalogTest.java`.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.input.DspDeferredNsCandidateCatalogTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluatorTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest
```

User verification: full regression at Step 6; no production-day run required here.

## Step 3. Add explicit policy-set selection without silent fallback

Create M `scheduler/policy/DspSchedulerPolicy.java`, enum values exactly
`DEADLINE_AWARE_ELASTIC_STICKY_LEASES`, `WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER`.
Use enum names as stable external identifiers. Reject null, blank, unknown, case-
altered or padded values; do not normalize misspellings. Default is the old value.

Modify M `analysis/DspFullDayAnalysisCommand.java`, `DspFullDayAnalysisConfigJson.java`,
`DspFullDayAnalysisConfigLoader.java`, `DspFullDayAnalysisCommandParser.java`,
`DspFullDayAnalysisMain.java`, `DspUncalibratedFullDayProfile.java` and
`analysis/report/DspFullDayReportFactory.java`.

Append required policy enum to command/profile; preserve all existing constructor
shapes by delegating to the old enum. Append nullable `String schedulerPolicy` to
raw JSON binding. Accept only a JSON string with a supported exact value. Add
`schedulerPolicy` to the loader's `PROPERTY_NAMES`; in `validateProperty`, require
text and validate its exact enum value before Jackson binding. The loader's existing
present-null rejection remains in force; absent JSON property alone means default.
Add singleton `--scheduler-policy VALUE` and `--scheduler-policy=VALUE`, following the
existing parser's argument forms/duplicate detection. CLI overrides JSON, then
default. Validate an invalid supplied JSON value even when CLI would override it.
All existing station/config/path semantics remain unchanged.

`profileId()` and `p2pLineAllocationPolicyId()` return selected enum name; retain
`PROFILE_ID` as the legacy constant. Supply/outbound IDs remain unchanged. New-set
eligibility ID is `WHOLE_SERVICE_CENTRE_ORDER_WIDE_PREPARATION_READY` and ranking ID
is `WHOLE_SERVICE_CENTRE_ADAPTED_FIRST_PHARMACY_SOURCE_SEQUENCE`. Old ID methods
remain identical. Add `schedulerPolicy` to report configuration; mark deadline/work
weights as diagnostic-only for the new set, not as active decision inputs.

Until Step 6 composition is connected, `DspFullDayAnalysisRuntimeFactory.create`
must explicitly reject the new set with `Whole-service-centre runtime composition
is not implemented yet`. It must not run the deadline policy under the new name.
This temporary guard is removed only in Step 6. Default execution remains usable.

Update T `analysis/DspFullDayAnalysisCommandTest.java`,
`DspUncalibratedFullDayProfileTest.java` and
`analysis/report/DspFullDayReportFactoryTest.java`. Prove defaults/compatibility,
each explicit selection, CLI precedence and both forms, invalid/null/blank/numeric
JSON, unknown values and duplicate CLI. Assert every reported component ID, retained
station overrides and timetable, and the temporary fail-fast runtime behavior.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfileTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest
```

User verification: no additional check until Step 6 enables the new selection.

## Step 4. Build mutation-versioned release accounting

Create M `scheduler/policy/WholeServiceCentreReleaseLedger.java` and
`WholeServiceCentreReleaseSnapshot.java`. Read M `io/LoadedDspData.java`,
`lifecycle/InboundToteManifestCatalog.java`, `osr/OsrInventorySnapshot.java`,
`av02/Av02AllocatedTote.java`, `osr/release/OperationalPhysicalToteReleaseCommand.java`,
and both existing OSR/AV02 command handlers before writing these types.

Ledger constructor:
`(LoadedDspData executableData, DspServiceCentreTimetable timetable,
List<P2pLineDefinition> lineDefinitions)`.
Reject missing/conflicting identities and invalid configured centres before mutation.
Build a physical-ID -> manifest-obligation index and EMPTY-sheet -> obligation index,
per-centre counts and five-line assignment-count tables once. Order centres by
configured priority descending then ID ascending; omit zero-obligation centres.
Initialize all counts from the whole executable day, not only preloaded supply.

Expose `WholeServiceCentreReleaseSnapshot snapshot()`,
`void validateUnreleased(OperationalPhysicalToteReleaseCommand)` and
`void recordApplied(OperationalPhysicalToteReleaseCommand)`. Invalid validation
throws `IllegalArgumentException` before mutation; duplicate/invalid recording throws
`IllegalStateException`. The command wrapper translates pre-delegate validation
failure to rejection. Validation is read-only:
exact source, centre, sheet/physical identity, configured proposed line, current
release-centre identity and unreleased obligation. For AV02, recognize the preindexed
EMPTY sheet, accepting the real generated physical ID; never substitute order ID.
Record only a previously validated successful command; duplicate or invalid recording
is an invariant failure. Mark obligation, decrement source-specific count, increment
its centre/line committed count if it has a P2P assignment, advance cursor over fully
released centres, increment version and invalidate cached snapshot. No worker access.

Snapshot fields exactly: `long version`, immutable `List<String> orderedServiceCentreIds`,
`Optional<String> releaseServiceCentreId`, immutable
`Map<String,Integer> unreleasedOsrToteCounts`,
`Map<String,Integer> unreleasedEmptySheetCounts`, and immutable
`Map<String,Map<P2pLineId,Integer>> committedP2pToteCounts`.
Expose O(1) `allReleased(String)` and `committedToteCount(String,P2pLineId)`.
Use `boolean` and `int` return types respectively; reject unknown centre/line IDs
rather than interpreting missing data as released. A known zero remaining total is
released, even while its machine work is still draining.
Do not publish the large obligation sets. Reject null/negative/conflicting map values;
older snapshots remain immutable. Same version returns the exact same snapshot.

Create T `scheduler/policy/WholeServiceCentreReleaseLedgerTest.java`. Prove multiple
same-sheet manifests, ADAPTED counting, upstream/unallocated EMPTY obligations,
priority ties, zero-work-centre omission, delayed last obligation, duplicate/wrong
source/sheet/centre/line rejection without mutation, precise version/cursor/counts,
stable snapshot identity and old-snapshot independence. Validation alone never counts.
Use a large static fixture and repeated snapshot calls to prove no reconstruction;
do not benchmark wall time.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedgerTest
```

User verification: no additional check required for this isolated domain step.

## Step 5. Implement pure allocation, eligibility and drained-line strategies

Read M `p2p/allocation/P2pElasticAllocationSnapshot.java`,
`P2pServiceCentreLineDemandSnapshot.java`, `P2pWorkloadSnapshotFactory.java`,
`DeadlineAwareElasticStickyP2pLineAllocationPolicy.java`,
`ElasticP2pLeaseRetentionPolicy.java`, M `p2p/lease/P2pLineAllocationRequest.java`,
`P2pLineLeaseSnapshot.java`, `P2pLeaseReleaseController.java`,
`P2pServiceCentreWorkSnapshot.java`, and M
`scheduler/operational/DspOperationalReleaseScheduler.java`,
`scheduler/operational/DspOperationalReleaseSnapshot.java`, and
`analysis/metrics/DspFullDayMetricsCollector.java` (block accumulation/classification).

Create M `scheduler/policy/WholeServiceCentrePolicySnapshot.java` with
`WholeServiceCentreReleaseSnapshot releases`,
`Optional<String> eligibleServiceCentreId`, and
`List<P2pLineId> availableUnleasedLineIds`. Immutable and bounded by configured lines.
Append `Optional<WholeServiceCentrePolicySnapshot> wholeServiceCentrePolicy` to
`P2pElasticAllocationSnapshot`, with the existing seven-argument constructor delegating
to empty. Accept exactly the two enum IDs. Old ID requires absent whole metadata;
new ID requires present metadata. Preserve every existing identity/line validation.
Do not add a field to the generic operational snapshot/request: both already carry
the allocation snapshot. Unknown profile strings still fail.

Modify `DspOperationalReleaseSnapshot.validateElasticAllocation` with an explicit
profile branch. Keep configured-line equality for both modes and leave the old
mode's candidate-demand-or-exclusion validation unchanged. In new mode, every P2P
candidate's centre must be present in the release snapshot's preindexed count maps;
it need not have a demand row. Future, unsupplied and finished-release centres are
blocked by the eligibility policy, not represented as allocation errors. Validate
that any demand row belongs to `releaseServiceCentreId`, and that the optional
eligible ID, when present, equals that release ID. Validate available-unleased IDs
are configured, unique and actually unleased/quiescent in the captured lease catalog.
Reject malformed/mixed metadata rather than silently treating it as allow-all.
Do not relax the deadline mode or add fake exclusion issues for intentional sequencing.

Create M `p2p/allocation/WholeServiceCentreP2pAllocationPlanner.java` with
`create(clock,supply,workload,timetable,leases,releases,downstreamHandlingDuration)`
using those existing snapshot/domain types. Decisions use immutable inputs only.
For supplied/authorized, use exactly `PRELOADED`, `AUTHORIZED` or `SUPPLY_COMPLETE`
from `ServiceCentreAuthorizationState`, as the existing planner does; supplied
current centres must have an authorization time. Compute eligible ID only for
the ledger's current release centre when supplied/authorized and at least one line
is owned by it or unleased+quiescent. Exclude lines owned by another centre.
Do not consider deadlines, costs or urgency for any selection.

Publish at most one demand row: current release centre if supplied and it has
remaining estimated work. Raw/required = configured line count (five); desired =
owned-current lines + available unleased lines when eligible, otherwise zero.
Feeding IDs are current-owned lines; draining IDs empty in this active row;
additional and unmet follow existing value formulas; withinConcurrencyWindow=true;
issues empty. Report-only deadline/workload fields retain their actual values;
adjustedSingleLineWork = workload.estimatedSingleLineWork, never consulted for demand.
Snapshot maximumConcurrentServiceCentres=1 describes release permission, not lingering
drain owners. Finished-release owners have no demand row; retention handles them.
No fabricated zero-required rows and no false deadline infeasibility.

Reuse the same `WholeServiceCentrePolicySnapshot` when the release snapshot identity,
eligible ID and available-unleased ID list are unchanged. Compare the at-most-five
IDs before allocating a replacement list/metadata record. Clock changes alone do
not invalidate this metadata. A new allocation envelope/deadline is appropriate
when its report timestamp changes; do not reuse a stale reporting timestamp.

Create M `p2p/allocation/WholeServiceCentreP2pLineAllocationPolicy.java` implementing
existing `P2pLineAllocationPolicy`; override `profileId()` with the exact new enum
name. Use its existing `P2pLineAllocationDecision allocate(P2pLineAllocationRequest)`
signature. Require matching new metadata and eligible centre.
For each of five configured lines, first enforce correlation compatibility, exact
ownership/quiescence and existing candidate-specific first-P2P-route admission.
Among compatible lines, prefer available unleased lines ahead of owned lines, then
lowest ledger committed-tote count for this centre, then active-pharmacy affinity,
then configured line order. This seeds all five for independent work and prevents
same-owner preference concentrating everything on line 1. Already pinned bag
requirements can leave only one compatible line; never violate them to balance.
No per-candidate lease-history scan. Use existing allocation block reasons for no
budget/no compatible line. This policy never mutates ledger or registry.

Create M `scheduler/operational/OperationalServiceCentreReleasePolicy.java` with
`Optional<OperationalReleaseBlock> blockFor(DspOperationalReleaseCandidate candidate,
DspOperationalReleaseSnapshot snapshot)` and a static `allowAll()` returning a
stateless instance that always returns empty, plus
`WholeServiceCentreReleaseEligibilityPolicy.java` implementing
it from immutable whole metadata. Add a scheduler overload accepting this policy;
all former overloads use allow-all. Call it before dependency/admission/correlation
work for each candidate. Block every candidate outside eligible centre, including
ADAPTED and AV02. Do not merely filter the already-ranked result.
Add `SERVICE_CENTRE_SEQUENCE` to `OperationalReleaseBlockType`. In the collector's
private `MutableOperationalBlockCounts`, add `sequenceCount`/`sequenceReason`,
accumulate that type using `saturatingAdd`, and append those fields to its private
`OperationalBlockCounts` projection. In `classify`, after unsupported/dependency
checks but before the ordinary OSR waiting calculation, return `OSR_STATE` for a
positive sequence count with its supplied reason. Do not classify this as station
capacity or let a generic upstream-waiting reason hide the explicit release barrier.
No public metric record shape change is needed. Existing block categories and old
mode classification remain unchanged.
Use the existing `AdaptedFirstPharmacyGroupedSourceSequenceRankingPolicy` unchanged
after this gate; no new sorting algorithm is needed.

Create M `p2p/allocation/WholeServiceCentreP2pLeaseRetentionPolicy.java`, constructor
`Supplier<WholeServiceCentreReleaseSnapshot>`. Implement existing
`P2pLeaseRetentionPolicy`, with `Optional<P2pLeaseRetentionDecision>
firstTransition(P2pLineLeaseCatalogSnapshot leases,P2pServiceCentreWorkSnapshot work)`.
Use `ElasticP2pLeaseRetentionPolicy.completionTransition` as the decision/action
analogue, but do not copy its deadline allocation lookup or per-call remaining-ID
set construction. Null inputs/supplier results fail before any mutation.
For each leased line in configured order: require its owner allReleased; require no
assignment for **that owner/line** whose physical ID remains in work; require fresh
`processingDrained()`. If open output exists, return
`CLOSE_FOR_APPLICABLE_WORK_COMPLETION`, else `RELEASE_LEASE`. Leave closure/release
to the existing controller and registry. Other old-centre lines can keep draining.
Build pending counts once when work snapshot or individual assignment-list identities
change; cache those identities independently of changing line activity. Do not build
a remaining-tote set or scan historical assignments on unchanged ticks. Never treat
historical assignments of an earlier owner as arrivals due to a new owner.

Create T `p2p/allocation/WholeServiceCentreP2pAllocationPlannerTest.java`,
`WholeServiceCentreP2pLineAllocationPolicyTest.java`,
`WholeServiceCentreP2pLeaseRetentionPolicyTest.java`, and update T
`scheduler/operational/DspOperationalReleaseSchedulerTest.java` for the new gate.
Required scenarios: independent totes seed five lines; least-count deterministic
selection and ties; pharmacy affinity cannot defeat seeding; pinned bag compatibility
overrides balance; inadmissible/foreign-owner/nonquiescent lines rejected; no mutation;
all later-centre order types held even when earlier centre wholly blocked; unsupplied
current centre not skipped; no-next-line-available pause; different deadlines/weights/
clock urgency give identical decisions when physical facts match; no early release
with upstream outstanding assignment or reserved/active/discharging/expected bag work;
closure followed by release on separate updates; another line may remain busy;
historic-owner assignments do not block handover; unchanged inputs reuse pending
projection and ledger snapshot, but changed work/list invalidates it. Add snapshot
compatibility/metadata validation cases in the planner test.
Update T `scheduler/operational/DspOperationalReleaseSnapshotTest.java` for future
and unsupplied centre candidates without demand, captured-line/metadata mismatch,
and unchanged old-mode rejection. Update T
`analysis/metrics/DspFullDayMetricsCollectorTest.java` to prove sequence blocks use
OSR_STATE and retain the explicit reason even while upstream work is waiting.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pAllocationPlannerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pLeaseRetentionPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollectorTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicyTest
```

User verification: integration and regression follow in Step 6; no daily run yet.

## Step 6. Wire live release guards and prove the production-shaped journey

Read M `analysis/runtime/DspFullDayAnalysisRuntimeFactory.java`,
`p2p/allocation/DspP2pElasticAllocationRuntimeFactory.java`,
`runtime/operational/DspOperationalReleaseRuntimeFactory.java`,
`CompositeOperationalCommandHandler.java` in that runtime package,
both existing OSR/AV02 command handlers, `p2p/lease/StrictP2pReleaseAssignmentCommitter.java`,
`p2p/bag/BagCoherentOperationalP2pReleaseAssignmentCommitter.java`, plus existing T
`analysis/runtime/DspFullDayConcurrentAdaptingScenarioTest.java`,
`DspFullDayPdcPackDispositionTest.java` and `DspFullDayAnalysisRuntimeFactoryTest.java`.
Also read M `av02/Av02AllocationSnapshotFactory.java`,
`Av02AllocationBlockReason.java`, `Av02AllocationController.java`,
`DspAv02AllocationRuntimeController.java`, `Av02PhysicalToteInventory.java`, and
`Av02InventorySnapshot.java`, all in `av02/`, plus existing T
`av02/Av02AllocationSnapshotFactoryTest.java`, `Av02AllocationControllerTest.java`
and `DspAv02AllocationRuntimeControllerTest.java` in that test package. The EMPTY
allocation correction below is part of Step 6, not a new later step.

Create M `scheduler/policy/WholeServiceCentreReleaseCommandHandler.java`, implementing
`SchedulerCommandHandler`, constructor
`(SchedulerCommandHandler delegate, WholeServiceCentreReleaseLedger ledger,
Supplier<P2pElasticAllocationSnapshot> liveAllocationSupplier)`.
Accept only the existing OSR/AV02 physical release commands. Application sequence:

1. Read a fresh whole-policy allocation and validate exact current eligible centre.
2. Read-only ledger validation of the exact unreleased obligation.
3. If P2P assignment exists, require its line to be a live feeding-current or available
   unleased line, with matching destination; never retarget a stale proposal.
4. Delegate to the existing handler, which revalidates inventory/lifecycle/route and
   prepares lease/correlation commit before downstream acceptance.
5. **Mutation boundary remains the delegate's existing downstream acceptance.** On
   applied return only, record the ledger departure. On rejected/deferred return,
   return it without ledger mutation. On thrown invariant failure, propagate it;
   do not fake rollback, decrement counts or mask partial external mutation.

Ledger validation/prepared facts must make post-applied accounting deterministic and
non-failing under the single-thread contract. Before calling the delegate, check all
counter/line/source conditions that accounting will require. Duplicate/stale work
returns a rejected or deferred result before delegate/target invocation. Use no new
registry mutation or target callback outside the established commit sequence.

Add a most-specific `createElasticWithAv02` overload to the operational runtime factory
with final `Optional<WholeServiceCentreReleaseLedger>` after existing missing-pack
supplier. Existing overloads pass empty. Wrap `CompositeOperationalCommandHandler`
only when present; use `elasticRuntime::allocationSnapshot` for live revalidation.
At both existing elastic profile checks accept supported matching IDs, rather than
hard-coding the old one; validate the evaluation source ID against runtime allocation
ID. A mixed set must fail before registering its operational controller.

Add a most-specific `createWithoutArrivalConsumers` overload to the P2P allocation
runtime factory with final policy enum and `Optional<WholeServiceCentreReleaseLedger>`.
All old overloads pass old enum/empty. Private composition selects planner and
retention once, not per candidate. Old branch remains the existing classes. New
branch requires ledger, uses the Step 5 planner/retention and existing cached work/
workload factories, supplying profile downstream duration only for reporting.
No new duplicate arrival consumers or line instances. Keep the existing runtime and
snapshot types; no parallel hierarchy or general-purpose policy registry.

In full-day runtime construction, create the ledger once for the new enum, before
composition registration. Share that exact instance across new allocation, retention
and outer release guard. Select new eligibility/allocation policies and existing
ADAPTED-first ranking; old enum keeps its original choices. Remove Step 3's temporary
guard only after all these seams are connected, including the AV02 allocation gate
below. Later-centre OSR preload/supply remains permitted; later-centre EMPTY carrier
allocation is not permitted until that centre becomes the release centre.

### Corrected AV02 EMPTY allocation composition

Modify only M `av02/Av02AllocationSnapshotFactory.java`,
`Av02AllocationBlockReason.java`, `DspAv02AllocationRuntimeController.java` and
the existing full-day runtime composition for this correction. Do not introduce a
parallel allocator, general policy registry, new polling controller or mutable worker
input. Leave `Av02AllocationController`, `Av02PhysicalToteInventory`,
`Av02OperationalCommandHandler`, physical ID generation, load-plan/lifecycle mutation,
FIFO departure and generic routing unchanged. Do not alter legacy logical release
marking, preparation dependencies, first-COLLECT or input sheet identities to make
this gate pass. Do not reduce AV02 capacity or tune timings to hide the defect.

Add an overload of `Av02AllocationSnapshotFactory.create` with final
`Optional<WholeServiceCentreReleaseSnapshot> wholeServiceCentreReleases` after its
existing lifecycle snapshot parameter. The existing five-argument method delegates
with empty. Reject a null Optional before evaluation. Empty selects exactly the old
algorithm. Present selects the new gate using only detached immutable facts:

- Validate candidate centre membership in the preindexed release count maps;
  missing membership is an invalid composition, not permission to fall back.
- Add `SERVICE_CENTRE_SEQUENCE` to `Av02AllocationBlockReason`. For each unallocated
  EMPTY candidate outside `releaseServiceCentreId` (including an absent cursor),
  publish that block and do not run its dependency evaluation or select it. Retain
  the candidate for deterministic inspection; do not filter an already selected
  command. Centre checks are O(1); no obligation scans or ledger access.
- Within the current centre retain existing authorization, dependency, capacity,
  active-assignment and priority/pharmacy/source checks. If none is ready, publish
  no command, even when a future-centre EMPTY is otherwise ready and authorized.
- Readiness and allocation do not advance release counts. Only the existing applied
  AV02 departure advances the ledger. An absent current centre yields no allocation.

Add a most-specific `DspAv02AllocationRuntimeController` constructor preserving its
existing eight parameters (including `Av02AllocationSnapshotFactory`) and appending
`Optional<Supplier<WholeServiceCentreReleaseSnapshot>> wholeServiceCentreReleaseSupplier`.
Both former constructor shapes delegate with empty. Reject a null Optional;
a configured supplier returning null is an invariant failure before allocation,
never a fallback to old behavior. The supplier is called only on the simulation
thread. Pass empty in old full-day composition; in new composition pass a supplier
of the exact shared ledger's `snapshot()`, not the P2P eligible-centre projection.
No second ledger or centre ordering state is introduced.

Extend the controller's private `AllocationInputs` with the optional release facts.
Capture once per evaluation and once again for existing fresh revalidation, and pass
those captured facts to the factory overload. Compare the contained release snapshot
by identity in `sameInputReferences`, alongside its existing inputs. Cursor/version
changes must invalidate the no-command fast path even if scheduler/supply/inventory/
lifecycle references do not change. Reuse the optional release wrapper while the
ledger snapshot reference is unchanged; keep that bounded cache controller-owned.
Unchanged blocked inputs retain the existing published runtime snapshot/sequence.
Do not rebuild whole-day indexes or allocation snapshots merely because time ticks.

The existing `Av02AllocationController` remains the sole mutation boundary. Its
fresh snapshot must include the new gate. If a selected command is no longer valid
against fresh release facts, existing command equality/eligibility revalidation
must stop before allocating an ID, registering lifecycle state, storing inventory
or installing a load plan. Do not merely suppress a command after these mutations.

Update the three named AV02 tests above for these explicit obligations:

- Factory: blocked/unready or unauthorized current-centre EMPTY plus a ready,
  authorized future-centre EMPTY selects no new-mode command and reports the future
  sequence block. Also cover current outstanding OSR with no current EMPTY, current
  EMPTY ready, cursor advancement to the next centre, absent cursor, null Optional
  and invalid candidate centre. Former API/empty facts preserve the existing
  `shouldAllowEligibleLowerPriorityCentreWhenHigherPriorityCentreIsBlocked` case.
- Runtime: stable blocked release facts reuse snapshot identity/sequence; changed
  release facts alone trigger reevaluation. A selected command invalidated by fresh
  release facts causes zero ID allocations and no inventory/lifecycle/load-plan
  mutation. A null or throwing configured supplier preserves published state and
  causes no allocation. Former constructors preserve existing behavior.
- Allocation controller: exercise its existing fresh-snapshot boundary with a
  new-mode selected command and a sequence-blocked fresh snapshot; prove no physical
  ID request or lifecycle/inventory/load-plan mutation. Do not change its algorithm.

Keep the existing mixed full-day scenario's delayed upstream 104 work, ADAPTED STORE,
ASSOCIATED first-COLLECT, last 104 EMPTY, preloaded 108 FULL_PACK and independently
ready/authorized 108 EMPTY. Rename the scenario to describe holding future EMPTY
allocation rather than positively expecting advance allocation. Until
`releases.allReleased("104")`, assert no 108 waiting or departed AV02 tote, no
allocated 108 EMPTY lifecycle assignment, and no 108 OSR departure. Observe the
ready/authorized 108 EMPTY as sequence-blocked while 104's EMPTY is unready; absence
of supply authorization alone must not satisfy the proof. Prove the 104 EMPTY
allocates/departs, its exact obligation advances the cursor, and only then the 108
EMPTY allocates/departs. Retain bounded supported completion, first-COLLECT,
STORE/global-count, exact commitment/output and NS assertions. This prevents both
the old skip-blocked allocator and an over-restrictive never-allocate-next fix from
passing. Use normal production updates; do not preallocate or mutate registries in
the integration fixture. Full-day old-mode coverage must include a blocked higher
centre with a ready authorized later EMPTY, proving the old selection still works.

### Existing Step 6 projections and integration proof

Update `analysis/metrics/DspFullDayMetricsCollector.serviceCentreMetrics` in new mode
to derive actual owned-line counts from the captured lease catalog, including old
owners draining without demand rows. Do not call fresh machine probes per centre.
Only new active centre has a desired budget; retired centres report desired=0 with
actual owned counts until handover. Keep deadline mode metrics unchanged.
Also update the collector's line-metric projection in new mode: leased current-centre
lines are feeding, leased owners with `releases.allReleased(owner)` are draining,
and unleased lines are neither. Do not rely on retired demand rows to label draining
lines. Test actual owner counts and per-line feeding/draining flags through handover.

Add a bounded new-mode progress/inspection line from
`runtime.elastic().allocation().wholeServiceCentrePolicy()`:
`WholeServiceCentre: releaseCentre=... eligibleCentre=... unreleasedOsr=...
unreleasedEmpty=... availableUnleasedLines=...`.
Serialize the same optional metadata in allocation JSON. No duplicate live reads,
obligation dumps, per-tick logging or wall-time decisions. Report active policy IDs
and diagnostic-only deadline costs accurately. NS and existing exception counts
remain separate. Update command test to expect usable new mode instead of the
temporary rejection.
Modify the existing progress/inspection formatters and report JSON writer for these
projections; do not create a separate logging controller or machine snapshot supplier.

Create T `scheduler/policy/WholeServiceCentreReleaseCommandHandlerTest.java` and
`analysis/runtime/DspFullDayWholeServiceCentreScenarioTest.java`; update existing
runtime-factory, command, progress and JSON tests for connected metadata/selection.
Use small deterministic fixtures through real full-day composition, not hand-mutated
lease registries or direct machine calls, and bounded `advanceUntil` outcomes.

Required integration proof:

- Five independent FULL_PACK totes of one centre commit to five distinct lines.
- A next centre can be OSR-preloaded and its EMPTY sheets supply-authorized, but no
  future EMPTY carrier is AV02-allocated and no next-centre departure occurs before
  the last current-centre obligation, including delayed upstream/EMPTY work.
  After cursor advancement, allocation resumes for the next centre without waiting
  for all predecessor lines to drain; departure still needs a usable line.
- Include ADAPTED STORE, ASSOCIATED COLLECT and order-wide readiness/first-COLLECT;
  not merely FULL_PACK. Include a non-P2P ADAPTED release in the global count.
- Every new-line assignment records one exact commit; bag correlations remain pinned.
- Once all old inbound work is released, hold new release while every line is busy.
  One old line drains/closes/releases while another remains active; new centre then
  uses only the freed line. Preserve per-line owner transition history and prove no
  interleaved centre input/pack/bag ownership.
- A last partial old-centre outbound tote closes before new-centre first bag/tote.
  No reopening or inherited pharmacy/output-sheet identity. Output introduced by the
  existing allocator on first bag, not speculative bagger changes.
- Supported work ends early with NS candidates pending and explicit reporting;
  source/fulfilment membership and PDC exception tests remain green.
- Stale next-centre, stale old-centre and duplicate commands call no downstream target
  and mutate no ledger/inventory/lifecycle/lease/correlation state. Target deferral/
  rejection leaves all those states unchanged. Applied OSR and AV02 commands count once.
- Explicit old selection and absent selection preserve old allocation behavior and
  constructor compatibility. A mismatched evaluation/runtime policy is rejected.
- No supported next-centre work is released when current centre has a genuine unresolved
  executable dependency. NS exclusions do not create such an obligation.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayWholeServiceCentreScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeControllerTest
```

User verification, in order (not model-run):

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test
.\gradlew installDist
```

Then use the existing installed full-day executable and the user's current config,
adding `"schedulerPolicy": "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER"`, or the explicit
CLI override. Do not edit the external config/log or overwrite output files for the
user. Observe early line spread, release barrier and first centre handover, then run
through supported completion if the user chooses. Confirm selected identifiers,
NS pending counts, exact outbound counts and preserved physical exceptions. Inspect
hard-cutoff results honestly if supported work cannot finish. Do not claim a full-day
pass from unit fixtures. No guaranteed multiplier or calibrated throughput target.
No JFR/performance tests are required. Generic rig visuals are unchanged; no new
visual verification is required unless regression exposes an affected boundary.

## Step 7. End-of-feature architecture review (read-only)

User initiates after implementation and user regression are green. Read this entire
plan, current requirements and the complete feature diff from the recorded base.
No model-run Gradle command; do not implement fixes during review.

Report PASS/FAIL/UNPROVEN with concrete class/method/control-flow evidence for:

1. Exact configuration selection/default/override and matching reported composition.
2. No deadline/weight dependence in new release, allocation or retention decisions;
   preserved hard cutoff and old algorithm.
3. Whole-day obligation accounting, exact manifest multiplicity and unallocated EMPTY;
   no centre skipping and no premature advancement. AV02 EMPTY allocation uses the
   same current release centre, blocks ready future-centre sheets before ID/lifecycle
   mutation, revalidates fresh facts and resumes after cursor advancement. Old-policy
   skip-blocked allocation remains unchanged; FIFO is not bypassed.
4. Immutable worker facts plus live simulation-thread revalidation and downstream-first
   mutation; rejected/stale/duplicate operations leave all state unchanged.
5. Deterministic real five-line distribution without moving pinned work or imposing
   new pharmacy/patient invariants.
6. Per-line no-future-input, full drain, close-before-release and independently staggered
   handover; historical assignments cannot masquerade as outstanding input.
7. Unchanged low-water supply, station/transport/continuation/machine ownership,
   dependencies, first-COLLECT, membership, output identity and timing.
8. NS input occurrences separate from physical exceptions, clean supported-work
   completion with pending reporting and no fabricated Exception processing;
   other unsupported work still blocks.
9. Shared outbound summary counted once; logs/report agree with actual identities.
10. Static indexes, change-versioned immutable ledger publication, bounded line checks,
    stable whole-policy metadata despite report-clock changes, retention invalidation
    tied to actual work/list changes and no new per-tick whole-day
    traversal. Tests prove identities/transitions, not just class existence.
11. Former API shapes/defaults and old policy execution preserved; no unnecessary
    production refactor, general plugin registry or future sequential-queue behavior.

User verification: no additional execution; review consumes the Step 6 results.
Unproven production-day completion remains clearly recorded if not yet run.

## Step 8. Documentation closure and next-work handoff

User initiates after Step 7 has no FAIL/UNPROVEN architectural item. This is a bounded
documentation step; no model-run Gradle command, no new implementation or redesign.
Read current implementation, this plan and the documents below. Use verified evidence,
not chat memory. Stop if a contract conflicts or closure requires a new decision.

- This plan: per-step verified status, exact commands/results, review evidence,
  outstanding user daily-run criteria, and actual final policy IDs/selection.
- `docs/codex-instructions.md` and `docs/codex-context.md`: current active handoff,
  fresh-session reading order, selectable baseline and next-work position. Preserve
  working rules and historical plans; do not declare unverified prior closure complete.
- Operational scheduling requirements: implemented whole-centre release/per-line
  drained handover, config/CLI precedence, reporting-only deadlines for this set,
  current-centre-only AV02 EMPTY allocation with fresh pre-mutation validation,
  explicit remaining future sequential queuing/overlap, actual ADAPTED preference and
  order-wide preparation. Remove stale "not implemented" notes only where source and
  verification prove that exact behavior.
- Lifecycle requirements: separate release-complete/output-closed/fully-supplied
  semantics and deferred NS candidates; physical exception and input/output identities
  unchanged. Reconcile stale sheet-bin/current ordinary-closure notes with verified
  order-owned bin and WITH_EXCEPTION implementation, not by changing historical plans.
- Exceptions requirements: retain the user's NS meaning, special future labels and
  local store fulfilment; metadata-only unknown-product candidates now, no station,
  label or fake bag yet. Recoverable malformed input remains a distinct classification.
  Actual Exceptions implementation follows a successful headless daily run.

User verification: read the updated documents; no additional test command. Run
`git diff --check` and report final status as for all steps. Do not commit.

## Deliberate deferrals and success limits

No next-centre queuing behind old-centre input, predictive crossover, deadline urgency,
storage-volume simulation, new P2P timing/configuration, multi-group PCR, fast-forward,
Exception station/label/bag creation, ASN/unannounced tote simulation, 32R or dispatch.
Full-day completion, wall-clock speedup and production calibration remain unproven
until the user's relevant run. A completed supported-work run with NS candidates or
known physical exception work is not a claim of complete patient supply or final DSP
dispatch completion.

## Suggested fresh-session request

> Read AGENTS.md and docs/codex-instructions.md completely and follow the mandatory
> document order. Then read docs/scheduler/dsp-whole-service-centre-drained-handover-plan.md
> completely and its required prerequisites. Implement Step N only. Follow its exact
> APIs, ownership, mutation sequence, compatibility and efficiency contracts; do not
> redesign or start another step. Record git status before editing, preserve changes,
> use apply_patch, run only the named focused verification command, review the complete
> step diff, run git diff --check and report final status. Stop on a contradiction or
> unresolved architectural choice. No agents and no commit.
