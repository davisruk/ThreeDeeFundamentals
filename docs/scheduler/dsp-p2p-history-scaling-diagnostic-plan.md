# P2P Retained-History Scaling Diagnostic Plan

Status: planned, not implemented. Created 2026-10-15 against clean commit
`a26ffc0` on `feature/dsp-full-day-analysis-metrics-inspection`.
The planning-session initial `git status --short` was empty.

Authorization covers this plan and its entry-point handoff only. The user
initiates each step separately. No production optimization is authorized.

## 1. Purpose, boundaries and session handoff

Determine whether two existing P2P paths become materially more expensive as
retained assignment history grows, without replaying hours of simulated work:

1. `P2pLineAllocationRequestFactory.create(...)`, including its unsuccessful
   `P2pLineLeaseCatalogSnapshot.findAssignment(...)` check for each pending tote.
2. `P2pLineLeaseCatalogSnapshot` construction, including cross-line assignment-ID
   uniqueness validation repeated by runtime snapshot callers.

Read `AGENTS.md`, then `docs/codex-instructions.md` completely and follow its
mandatory document-reading order. Read this entire plan before a selected step.
Remain on the user-selected branch. Record `git status --short` before editing,
preserve existing changes, use `apply_patch` for every source/document edit, and
use no agents, staging, commits, branch operations or later-step work. Generated
probe reports are execution artifacts, not manually edited source files.

Notation: `M/` = `app/src/main/java/online/davisfamily/warehouse/sim/dsp/`;
`T/` = `app/src/test/java/online/davisfamily/warehouse/sim/dsp/`.
Expand these prefixes before opening files. Stop on a contradiction, a missing
named existing file/test, or an unresolved architectural choice. Do not replace
this plan during implementation. Follow the entry-point correction-cycle limit.
After every step, review its complete diff including new untracked files, run
`git diff --check` and a no-index whitespace check for new files, and report final
`git status --short`, exact verification results and unproven criteria. Append
the actual files/results and next boundary to this plan's execution record, then
stop. Do not advance automatically.

### Existing evidence and limits

- The production lookup scans all five lines' retained assignments. The request
  factory invokes it before line-allocation policy evaluation. An unassigned
  pending tote therefore requires an unsuccessful full-history search.
- The lease registry retains assignment history across lease release. Its
  mutable-owner lookup is indexed, but the published catalog lookup is not.
- The synchronous full-day operational controller evaluates candidates each
  simulation step. The default profile uses 50 ms steps, or 1,200 steps per
  simulated minute. Not every candidate necessarily reaches P2P request creation.
- Catalog construction rebuilds an assignment-ID uniqueness set. Reusing the
  per-line immutable lists does not skip this catalog-level traversal.
- These paths predate the watermark changes. The cap can nevertheless increase
  retries by keeping work pending. The experiment cannot exonerate that indirect
  interaction or identify the cause of the isolated six-minute log interval.
- Completion checking also revisits completed bags, and lifecycle/outbound work
  rebuilds history-derived structures after mutations. Those are follow-on
  suspects, not additional kernels to add to this first diagnostic.

The original evidence is `C:\misc\cpas-test\scheduler-testing\progress.log`.
It is background context only: the probe must not read or overwrite it, load the
real dataset, advance a world, or depend on external configuration.

The watermark plan remains independent: Steps 1-6 are implemented and
focused-verified; its broader Step 6 user verification and Steps 7-8 remain open.
This diagnostic does not close those gates or alter watermark semantics.

## 2. Fixed experiment contracts

### 2.1 Change surface and prerequisites

Only these implementation files may be added/modified, in their assigned step:

- **New** `T/p2p/lease/P2pHistoryScalingFixtures.java`
- **New** `T/p2p/lease/P2pHistoryScalingFixturesTest.java`
- **New** `T/p2p/lease/P2pHistoryScalingProbe.java`
- **New** `T/p2p/lease/P2pHistoryScalingProbeTest.java`
- Existing `app/build.gradle`: one test-classpath `JavaExec` task only.
- This plan's execution record, and the bounded handoff update in Step 3.

No `app/src/main` edits, dependencies, plugins, production instrumentation,
profiling scripts, full-day commands, changes to existing tests, or changes to
other plans. Do not add JMH or a general benchmark framework. Probe classes live
only in test sources and must not enter the installed application.

Before Step 1, read these existing files completely:

- `M/p2p/lease/P2pLineAllocationRequestFactory.java`
- `M/p2p/lease/P2pLineAllocationRequest.java`
- `M/p2p/lease/P2pLineLeaseCatalogSnapshot.java`
- `M/p2p/lease/P2pLineLeaseSnapshot.java`
- `M/p2p/lease/P2pLineLeaseRegistry.java`
- `M/p2p/lease/P2pLineDefinition.java`
- `M/p2p/lease/P2pPhysicalToteAssignment.java`
- `M/p2p/lease/P2pLineActivitySnapshot.java`
- `M/p2p/lease/P2pInputActivitySnapshot.java`
- `M/p2p/bag/P2pBagCorrelationRequirementCatalog.java`
- `M/p2p/bag/P2pBagCorrelationAssignmentSnapshot.java`
- `T/p2p/lease/P2pLineAllocationRequestFactoryTest.java`
- `T/p2p/lease/P2pLineLeaseRegistryTest.java`

Reuse the request-factory test's valid constructor/immutable-sharing patterns,
not its one-line/deadline-aware fixture. For call-frequency interpretation, read
the `update`/`evaluate` paths of existing
`M/runtime/operational/DspOperationalReleaseController.java`,
`M/runtime/operational/SynchronousOperationalReleaseEvaluationSource.java`, and
`M/scheduler/operational/DspOperationalReleaseScheduler.java`. Read existing
`M/p2p/lease/DspP2pStickyLeaseRuntime.java` and the registry's `snapshot` path
before attributing the isolated constructor result to a runtime call site.

### 2.2 Synthetic fixtures

Create package-private final `P2pHistoryScalingFixtures` with
`static Fixture create(int historicalCount, int pendingCount)`. Reject negative
history or nonpositive pending counts with `IllegalArgumentException`.
Its nested immutable records are:

```java
record Candidate(OperationalPhysicalToteSource source, PhysicalToteId toteId,
        OrderSheetKey sheetKey) {}
record Fixture(int historicalCount, List<P2pLineLeaseSnapshot> lines,
        P2pLineLeaseCatalogSnapshot catalog,
        P2pLineAllocationRequestFactory requestFactory,
        Map<PhysicalToteId, P2pPhysicalToteAssignment> indexedAssignments,
        List<Candidate> candidates) {}
```

Construction rules, all outside measured intervals:

1. Exactly five definitions, in order `diagnostic-line-1` through `-5`, with
   P2P targets `diagnostic-target-1` through `-5`.
2. Create `historicalCount` assignments with unique IDs `history-0` onward and
   service centre `104`; distribute global index `i` to line `i % 5`. Their
   destinations must match that line's definition. They represent audit history,
   not currently outstanding work. The lease snapshot does not encode terminal
   lifecycle state; do not fabricate a lifecycle ledger or claim a full journey.
3. Append exactly 40 current assignments `current-0` through `current-39`, centre
   `125`, round-robin across the same lines. Each line therefore has eight
   current assignments independent of history size. Total retained assignments
   are always `historicalCount + 40`, not `historicalCount`.
4. All five current owners are `Optional.of("125")`. Activity on each line is
   `new P2pInputActivitySnapshot(6, 1, true, 0)`, idle pack path/bagging and no
   open outbound tote. Construct line snapshots through their public constructor.
   Old-centre assignments remain legal historical records on these current leases.
5. Publish immutable per-line lists and the catalog. Build a test-only immutable
   `LinkedHashMap` ID index in line order, rejecting duplicates. This is the
   indexed lookup control, never an injected production replacement.
6. Create `pendingCount` distinct absent IDs `pending-0` onward and sheet keys
   `new OrderSheetKey("pending-order-" + i, 1)`. Alternate OSR/AV02 sources by
   even/odd index. Prebuild the candidates. None may collide with any assignment.
7. Construct one request factory with all five route admissions `true`,
   `Optional.empty()` elastic allocation, empty correlation requirements and
   empty correlation assignments. Every request uses centre `125`,
   `List.of("diagnostic-pharmacy")` and `p2pFirstRouteStation=true`.

This is a valid isolated request/lease-boundary fixture, not an instantiated
whole-centre runtime. Eight current assignments provide representative bounded
live state; no watermark ledger/policy is executed or timed. Request-source
alternation exercises both accepted request inputs without modelling inventory.

### 2.3 Kernels and matrix

Normal history sizes: `[0, 500, 2000, 5000]`; pending sizes: `[100, 400, 800]`.
Construct and validate all fixtures once, before warm-up. Four kernels only:

| Kernel | Timed operation | Samples/cases |
|---|---|---|
| `REQUEST_FACTORY` | Existing factory `create(...)` for every candidate | 12 history/pending pairs |
| `LOOKUP_LINEAR` | Existing catalog `findAssignment(candidate.toteId())` | Same 12 pairs |
| `LOOKUP_INDEX_CONTROL` | `Optional.ofNullable(index.get(candidate.toteId()))` | Same 12 pairs |
| `CATALOG_REBUILD` | `new P2pLineLeaseCatalogSnapshot(fixture.lines())` | Four history sizes; pending count reported as 0 |

The lookup control measures only lookup, not an alternative request factory.
Compare it directly with `LOOKUP_LINEAR`; never advertise that ratio as a
complete allocator or application speedup. Map construction/memory is excluded
from lookup timing and must be disclosed as an unmeasured control cost.

There are 40 cases per round. Lookup/request cases perform 20 passes through all
candidates per sample; catalog cases perform 200 constructions per sample.
Prebuilt line snapshots are reused by the constructor kernel: it isolates
catalog validation, not the extra work of collecting activities/creating lines.

### 2.4 Measurement discipline, bounds and output

Create public final `P2pHistoryScalingProbe` with `public static void main(String[] args)`.
CLI accepts only `--mode=matrix|smoke`, `--run-index=1|2|3` and
`--output-dir=<path>`, in that equals form. Require all three exactly once;
reject unknown/duplicate/missing/invalid values before creating output files.
Reject an existing output directory. Never delete or overwrite results.

One JVM process executes one run index. Matrix mode has 5 discarded warm-up
rounds followed by 7 measured rounds. Each round executes all cases once.
Canonical case order is kernel order from the table, then increasing history,
then increasing pending count. Rotate that list left by
`(roundIndex * 7 + runIndex * 11) % caseCount`, reversing on odd round indexes
(zero-based indexes, including warm-up). This distributes ordering effects
deterministically; it does not guarantee absence of JVM/GC bias.
Measured sample indexes are 0 through 6 (0 through 2 in smoke mode), independent
of the preceding warm-up round indexes. Warm-up rows are not written to samples.

Use `System.nanoTime()` for elapsed intervals. Use a private static volatile
object sink, written once per timed operation with the actual result, plus a
primitive checksum validated after the interval. Checksum increments are one
per request, one per successful lookup (zero expected for misses), and the
catalog's five-line count per construction. Do not compute request/catalog
`hashCode`, deep equality or `toString` inside timing. The common sink overhead
is included and reported, not subtracted. No JUnit assertion, formatting, I/O,
fixture/index/factory creation or metrics-bean discovery belongs in a kernel.

Resolve management beans before warm-up. Outside each timed interval, capture
current-thread allocated bytes using `com.sun.management.ThreadMXBean` only if
supported and already enabled; otherwise report `NA`. Do not enable counters.
Also capture summed supported GC collection count/time counters around each
sample. Preserve unavailable values as `NA`, never zero; deltas with unavailable
or decreasing counters are `NA`. GC deltas are process-wide observations, not
proof that this kernel alone caused a collection. Do not force GC, sleep, change
collector/JIT flags, spawn workers, sample CPU stacks or start JFR.
Aggregate collection count and collection time independently; if any collector
returns an unavailable value for that metric, its aggregate is `NA`. Capture
in this order: GC-before, allocation-before, start nanoTime, kernel, end nanoTime,
allocation-after, GC-after. Formatting and checks follow all those captures.

The normal process has a 120-second cooperative work budget from entry into the
probe, including fixture setup. Check it between fixture constructions and each
pass/catalog construction, never per candidate. Do not adapt pass counts to
timing. On expiry, stop, write only completed samples and partial metadata,
omit incomplete-case summaries, and exit nonzero. The budget is checked at safe
boundaries, not a guarantee against JVM/OS pauses. No automatic retry/escalation
of workload. Report samples before failure when possible; unexpected exceptions
also exit nonzero and must not produce a success marker.

Write UTF-8 reports only after measurement, with fixed headers/`Locale.ROOT`:

- `metadata.txt`: schema version 1; mode, run index, matrix/pass/warm-up/sample
  settings, start/end instants, completion/budget status, JVM name/version/vendor,
  JVM arguments, OS/architecture, processor count, maximum heap, management-counter
  availability, and sink/control/isolated-boundary caveats. Source revision is
  supplied separately by the user's `git rev-parse HEAD`, not a subprocess here.
- `samples.csv`: one row per completed measured sample, columns
  `runIndex,kernel,historicalCount,totalAssignmentCount,pendingCount,sampleIndex,operations,elapsedNanos,nanosPerOperation,allocatedBytes,bytesPerOperation,gcCollections,gcMillis,checksum`.
- `summary.csv`: one row per complete case, columns
  `runIndex,kernel,historicalCount,totalAssignmentCount,pendingCount,sampleCount,medianNanosPerOperation,minNanosPerOperation,maxNanosPerOperation,medianBytesPerOperation`.
  Median means the sorted middle observation; allocation median is `NA` unless
  all samples have available allocation values. Preserve raw samples and spread.

Smoke mode uses histories `[0, 10]`, pending `[2]`, one warm-up and three measured
rounds, one candidate pass/three catalog constructions per sample, and the same
120-second cooperative guard. It has 8 cases, 24 sample rows and 8 summaries.
It is correctness verification only, not performance evidence.

Keep package-private `static void run(ProbeConfig config, Path outputDirectory)`
used by `main` and JUnit tests; it may declare `IOException`. Use nested
`enum Mode { MATRIX, SMOKE }`, `record ProbeConfig(Mode mode, int runIndex)` and
`record Options(ProbeConfig config, Path outputDirectory)`. Package-private
`static Options parseArgs(String[] args)` performs CLI validation without I/O;
reject blank output paths too. Validate configs passed directly to `run`.
Mode selects fixed settings, with no hidden system-property overrides. Budget
expiry throws `IllegalStateException` after writing partial artifacts; letting
`main` propagate failure provides the nonzero process exit without `System.exit`
inside `run`. Factor median and counter-delta handling into package-private pure
methods for deterministic tests. Private helper naming and local decomposition
are discretionary; experiment contracts are not.

## 3. Ordered steps

### Step 1 - Synthetic fixtures and deterministic correctness proof

Own only the new fixture and fixture-test files and this execution record.
Implement Section 2.2, not the timed runner. Required JUnit scenarios:

1. Across the full 4-by-3 matrix, prove five ordered definitions/owners, exactly
   H historical plus 40 current assignments, round-robin distribution, exact
   centre/destination membership, eight current assignments per line, and distinct
   absent pending IDs. Prove activity is nonquiescent with the specified counts.
2. For every pending candidate, call the actual factory and prove exact ID,
   centre, pharmacy, source-independent request semantics, shared catalog and
   correlation snapshot identity, route admissions and empty requirements.
3. Compare production linear lookup with the control index for every assigned
   and pending ID, asserting the exact assignment object for hits. An already
   assigned ID must be rejected by the production request factory.
4. Prove index/lists are immutable and two independently built fixtures do not
   share mutable state. Reject invalid factory sizes. Construct a separate tiny
   invalid catalog with a cross-line duplicate physical ID and prove the real
   catalog constructor rejects it (use two otherwise valid line snapshots).

No assertion about milliseconds or speedup. Do not call the probe in this step.

Implementation verification, exactly:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixturesTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequestFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistryTest
```

User verification: no additional run required. Review the focused result and
fixture scope; timing remains unproven. Stop before Step 2.

### Step 2 - Bounded probe, artifacts and opt-in execution task

Own the new probe and probe-test files, one addition to `app/build.gradle`, and
this execution record. Read Step 1's complete files and recorded result, existing
`app/build.gradle`, and the production paths in Section 2.1 before editing.

Implement Sections 2.3-2.4. Register only `dspP2pHistoryScaling` as a `JavaExec`
task, following the existing `dspFullDayAnalysis` task's registration style but
using `sourceSets.test.runtimeClasspath`, `dependsOn testClasses`, main class
`online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbe`, group
`verification`, working directory `rootProject.projectDir`, a Java 21 toolchain
launcher, and fixed JVM arguments `-Xms256m`, `-Xmx512m`. It must not depend on
`test`, `check`, full-day execution or installation. Do not attach this task to
normal build/check/test execution. Accept ordinary JavaExec `--args` forwarding.

Required JUnit coverage in `P2pHistoryScalingProbeTest`:

- Parse all valid modes/indexes, and reject unknown/duplicate/missing/invalid
  options before writing. An existing output directory is rejected with its
  sentinel contents unchanged. Use JUnit `@TempDir`, never external paths.
- Run one real smoke experiment through `run`; assert all three artifacts,
  metadata, exact 24/8 row counts, case coverage, per-kernel operation counts,
  checksums, measured sample indexes, and nonnegative raw times. No timing bound.
- Summaries contain only measured observations: test the pure summarizer with
  deliberately different warm-up values and known measured median/min/max;
  verify `NA` allocation and unavailable/decreasing counter handling.
- Test case ordering and fixed matrix settings without running the matrix.
  Test cooperative-budget decision logic using supplied elapsed values, not a
  wall-clock delay. Test partial-summary omission for an incomplete case.
- No mutation of fixture catalog/index/assignments after all four smoke kernels;
  verify actual production results, not a hand-written scanning substitute.

Implementation verification, exactly:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixturesTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbeTest
```

Inspect the complete Gradle addition as well as source/test diffs. The model
must not run matrix mode or invoke the new task as extra verification.

User verification, run from repository root with other simulations stopped:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
git rev-parse HEAD
.\gradlew :app:dspP2pHistoryScaling --args="--mode=matrix --run-index=1 --output-dir=app/build/reports/dsp-p2p-history-scaling/run-1"
.\gradlew :app:dspP2pHistoryScaling --args="--mode=matrix --run-index=2 --output-dir=app/build/reports/dsp-p2p-history-scaling/run-2"
.\gradlew :app:dspP2pHistoryScaling --args="--mode=matrix --run-index=3 --output-dir=app/build/reports/dsp-p2p-history-scaling/run-3"
```

Each task invocation launches a fresh probe JVM even if Gradle's daemon is reused.
Each complete run must contain 280 measured sample rows and 40 summary rows.
Use fresh directory suffixes if paths already exist; do not delete prior results
or add overwrite. Stop after any nonzero exit and supply its logs/partial reports;
do not continue the remaining commands blindly. Supply all three report directories
and the revision for Step 3. No full suite, install or real-data day is required.

### Step 3 - Read-only result interpretation and bounded closure

Requires separately requested analysis and user-supplied matrix results. Own
only this plan's execution record and the diagnostic handoff paragraph in
`docs/codex-instructions.md`; no code/test/build edits or reruns.

First report PASS/FAIL/UNPROVEN for the probe contract: exact production entry
points, excluded setup, result consumption, fixed work/order/budget, independent
JVMs, report completeness, smoke correctness, and no production/dependency changes.
Stop on invalid evidence rather than treating partial runs as a complete matrix.

For each kernel, compare history sizes at fixed pending count. For request and
lookup kernels also compare pending sizes at fixed history. Report per-operation
and derived per-pass costs (median per-operation times pending count), allocation
per operation, sample spread, GC observations, and all three JVM runs separately.
Do not pool raw samples across JVMs; summarize cross-run agreement descriptively.
Separate constant overhead from trends; do not force monotonic timing assertions.
Identify timer/sink-floor, compilation, noisy runs or insufficient evidence.

Compare linear and indexed lookup only on equivalent absent-ID workloads.
For requests distinguish candidate-normalization cost from the separately timed
lookup; do not subtract medians and claim an exact component attribution.
For catalog construction distinguish total-history cost from fixed five-line
work. Avoid extrapolating one constructor cost to every runtime caller blindly.

Write a concise conclusion in the execution record: evidence for/against each
scaling hypothesis, strongest next measurement target, uncertainty and scope.
Any 1,200-step/minute projection is conditional on the default 50 ms profile and
explicit candidate/call counts, not measured full-day wall time. A positive
result justifies proposing a separate measurement-led remediation plan; it does
not authorize indexing/caching production state. An inconclusive result permits
recommending a separately authorized short synthetic-state JFR recording, not
launching one or expanding kernels. Keep the other history suspects deferred.

Update only the diagnostic handoff's status/result location and next separately
authorized boundary. Do not close watermark architecture/user verification,
rewrite historical plans or certify the six-minute pause's cause.

Implementation verification: no model-run Gradle/probe command. Review the
complete diagnostic diff/results against this contract; run `git diff --check`
and report final `git status --short`, evidence and remaining unproven criteria.

User verification: review the diagnostic conclusion and choose whether to
authorize a remediation plan or additional measurement. No further run required.

## 4. Execution record

2026-10-15 planning only. Existing test/build APIs and production request/catalog
boundaries inspected; initial worktree clean at `a26ffc0`. No code, test, build,
external-log/configuration or profiling-script changes, and no Gradle/probe/JFR
execution. Steps 1-3 await separate requests. Runtime contribution, allocation
volume, scaling measurements and causal attribution remain unproven.
Planning review: read the complete new plan and entry-point diff; checked named
existing paths, matrix/row arithmetic, setup/timing separation, failure handling,
verification ownership and unchanged production boundaries. Tracked and new-file
whitespace checks passed. This records document review, not probe execution.

### Step 1 completed 2026-10-15

Initial `git status --short` was empty at `3607bbf` (`History scaling plan`).
Created only `T/p2p/lease/P2pHistoryScalingFixtures.java` and
`T/p2p/lease/P2pHistoryScalingFixturesTest.java`, plus this execution record.
No production, existing-test, build, handoff, external-file or probe changes.

The immutable fixture uses the specified five ordered lines, H historical
assignments plus 40 current assignments, alternating-source absent candidates,
the real request factory and a line-ordered test-only immutable ID index.
Eight new correctness tests cover all 12 history/pending pairs, exact IDs and
distribution/activity, real request semantics and shared batch identities,
exact-object indexed/linear lookup parity for every assigned ID and misses for
every pending ID, assigned-ID rejection for both sources, collection immutability
and defensive copies, fixture independence, invalid sizes and real catalog
cross-line duplicate rejection. No timing assertions or simulated lifecycle
completion were introduced.

Implementation verification was run exactly as specified:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixturesTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequestFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistryTest
```

The sandbox invocation could not access the user Gradle cache lock. The same
command was rerun with approved cache access: `BUILD SUCCESSFUL`. JUnit XML
confirmed 19 tests (8 new fixture, 5 existing request-factory, 6 existing registry)
with zero failures, errors or skips. No compile/test correction cycle was needed.
Reviewed both complete new-file diffs and this record against Step 1 and Section
2.2. `git diff --check` and both new-file no-index whitespace checks passed.

No additional user verification is required for Step 1. Timing, allocation
volume, runtime contribution and the original slowdown's cause remain unproven.
Step 2 requires a separate request; no runner, matrix, JFR or later-step work was
performed. Final status contains this modified plan and the two new untracked
fixture/test files only. No agents, staging or commits.
