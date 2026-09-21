# DSP Recoverable Input Rejection Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned; implementation has not started.

## Purpose

Allow a complete full-day DSP analysis to continue when an independently attributable 12N
message or ADAPTED correlation group is erroneous, while preserving the rejected input for audit
and eventual 32R reporting.

The production-day run currently stops at:

```text
Missing ADAPTED source line for OrderSheetKey[orderId=TOTE0007170299, sheetNumber=1]
line 000243514306 key
PreparedLineKey[targetOrderId=TOTE0007170299, lineReference=000243514306]
```

Inspection of the source day established that this is an input anomaly: the line is an ADAPTED
alias in an ASSOCIATED order, but there is no corresponding ADAPTED preparation instruction. The
production result later reports the line as an unsuccessful outcome. The simulator must not invent
the missing preparation work, but this one bad line must not prevent the remaining day from being
simulated.

This plan introduces a strict boundary between:

- DSP-visible, reportable input retained for diagnostics and eventual 32R;
- executable input that may create logical slots, packs, bags, station work, scheduler work, and
  physical totes; and
- immutable rejected-input records that are terminal for the current run but remain reportable.

The plan does not implement 32R, protocol status mapping, Exception Station processing, NS labels,
empty bags, direct OSU/ASN ingestion, or MANUAL processing.

## Required Reading

Read these documents completely before implementing any step:

1. `AGENTS.md`
2. `docs/codex-instructions.md`
3. `docs/codex-context.md`
4. `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`
5. `docs/scheduler/dsp-one-retained-line-one-pack-correction-plan.md`
6. `docs/scheduler/dsp-complete-bag-demand-planning-remediation-plan.md`
7. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
8. `docs/scheduler/dsp-operational-scheduling-requirements.md`
9. `docs/scheduler/dsp_osr_scheduler_requirements.md`
10. `docs/machines/third-party-station-requirements.md`
11. `docs/machines/adapting-station-phase-1-plan.md`
12. `docs/machines/exceptions-station-requirements.md`

For the current implementation, inspect these production classes and their tests before editing:

- `TwelveNDatasetLoader`, `JsonLoaderSupport`, `DspDatasetAssembler`, `LoadedDspData`, and
  `DspDatasetLoadReport`;
- `DspFullDayInputLoader`, `DspFullDayLoadedInput`, and
  `DspFullDayBagPlanningRequestFactory`;
- `DspFullDayAnalysisRunner`, `DspFullDayProgressSnapshot`,
  `DspFullDayProgressFormatter`, `DspFullDayReportFactory`,
  `DspFullDayAnalysisReport`, and `DspFullDayReportJsonWriter`;
- `DspFullDayAnalysisRuntimeFactory` and `DspFullDayCompletionEvaluator`;
- `TwelveNDatasetLoaderTest`, `DspDatasetAssemblerTest`,
  `DspFullDayInputLoaderTest`, `DspFullDayProgressFormatterTest`,
  `DspFullDayReportFactoryTest`, `DspFullDayReportJsonWriterTest`,
  `DspFullDayAnalysisCommandTest`, and `DspFullDayAnalysisScenarioTest`.

`DspFullDayBagPlanningRequestFactoryTest` does not exist before this feature. Step 3 creates it;
inspect it before executing any later step that names it.

## Fixed Decisions

These decisions are complete and must not be redesigned during implementation.

### 1. Reportable input and executable input are different projections

After MANUAL exclusion, every successfully mapped DSP-visible fulfilment line remains in a
reportable view in source encounter order. A line may be reportable without being executable.

Only executable lines may contribute:

- a planned logical slot or reserved physical pack ID;
- bag membership, bag ordinal, or total bag count;
- an inbound manifest item or physical tote;
- Third Party, Adapting, P2P, outbound, workload, dependency, or completion work.

A rejected line is terminal for the current simulation run. It is not unfinished work and does not
block scheduler or service-centre completion. Its terminal protocol status is deliberately not
chosen here.

### 2. Rejected input is preserved separately and immutably

Use an immutable, construction-owned rejection catalog because rejected input is expected to be a
small subset. Runtime stations and scheduler hot paths must not inspect a status field on every
normal line.

Create these types in `online.davisfamily.warehouse.sim.dsp.analysis.input`:

- `DspInputRejectionReason`, an enum with exactly:
  - `MALFORMED_12N_MESSAGE`;
  - `MISSING_ADAPTED_SOURCE`;
  - `MISSING_ADAPTED_FULFILMENT`;
  - `DUPLICATE_ADAPTED_SOURCE`;
  - `DUPLICATE_ADAPTED_FULFILMENT`;
  - `ADAPTED_SOURCE_FULFILMENT_MISMATCH`;
  - `UNCLASSIFIED_CORRELATION_ANOMALY`.
- `DspRejectedLine`, retaining:
  - the complete immutable original `DspOrderItem`;
  - its source `OrderSheetKey` and source `OrderType`;
  - optional target/reportable `OrderSheetKey`;
  - source message encounter index;
  - zero-based original source line index;
  - optional `PreparedLineKey`;
  - typed reason;
  - nonblank diagnostic text;
  - optional exception class name; and
  - an immutable stack-trace line list.
- `DspInputRejectionCatalog`, retaining the encounter-ordered line records and the
  `TwelveNRejectedInputMessage` records defined below, and building
  its immutable counts-by-reason and rejected-lines-by-target-order indexes once in its
  constructor. Expose constant-time reason counts and target-order lookup. Do not rebuild indexes
  on reads.

`DspRejectedLine.targetOrderSheetKey()` is present for a rejected fulfilment alias. It is empty for
an orphan source line when no target fulfilment sheet exists. Do not fabricate a target sheet from
`referenceSheetNumber`; that field remains protocol metadata and not correlation identity.

### 3. Source metadata is retained during assembly

Create `DspRetainedInputLine` in `online.davisfamily.warehouse.sim.dsp.io`. It contains the complete
`DspOrderItem`, source `OrderSheetKey`, source `OrderType`, source message encounter index, and
zero-based source line index.

Extend `LoadedDspData` with an immutable encounter-ordered `retainedInputLines` component. Existing
public convenience constructors must continue to work by deriving deterministic fixture metadata
from their `orders`; production loading must supply the real metadata. Do not add mutable side
tables.

The source message encounter index is the position in the ordered input path list. It is distinct
from the retained order sequence currently used by scheduler source ordering. A malformed or
MANUAL message still has an input encounter index but does not consume retained scheduler sequence.

### 4. MANUAL data remains outside this model

Existing MANUAL-message and MANUAL-line exclusion is unchanged. MANUAL records do not enter the
reportable fulfilment view, the executable projection, or the rejection catalog. Existing aggregate
MANUAL counts remain the only report for that deliberately unsupported flow.

### 5. Correlation is validated once before bag planning

Create `DspFullDayInputPreflight` and `DspFullDayInputProjection` in
`online.davisfamily.warehouse.sim.dsp.analysis.input`.

`DspFullDayInputPreflight.project(LoadedDspData assembledData,
DspInputRejectionCatalog loadRejections)` is the sole full-day owner of recoverable ADAPTED
correlation validation. It returns:

- `LoadedDspData executableData`;
- immutable encounter-ordered `List<NotionalToteOrder> reportableOrders`, taken after MANUAL
  exclusion but before executable filtering; and
- one combined immutable `DspInputRejectionCatalog`.

Build method-local insertion-ordered indexes once:

- product records by product ID;
- retained line metadata by source order sheet and line reference;
- ADAPTED source participants by `PreparedLineKey`;
- ADAPTED fulfilment participants by `PreparedLineKey`;
- inbound manifests by source order sheet.

Each `PreparedLineKey` defines an isolated dependency group. Validate groups in first-participant
encounter order and participants in source encounter order. The meaningful key remains target order
ID plus globally distinct line reference.

### 6. Dependency-closed rejection rules

Apply exactly these rules:

- One fulfilment alias and no source: reject the fulfilment line as
  `MISSING_ADAPTED_SOURCE`.
- One source and no fulfilment alias: reject/exclude the source line as
  `MISSING_ADAPTED_FULFILMENT`. It has no reportable target line and therefore no eventual 32R line
  of its own.
- More than one source for a key: reject every source and every fulfilment participant in that
  group as `DUPLICATE_ADAPTED_SOURCE`.
- More than one fulfilment alias for a key: reject every source and every fulfilment participant in
  that group as `DUPLICATE_ADAPTED_FULFILMENT`. If both sides are duplicated, source duplication is
  the primary reason because source validation is evaluated first; diagnostics must mention both
  cardinalities.
- One source and one fulfilment alias whose product ID, pharmacy ID, patient ID, prescription ID,
  line type, or service-centre relationship does not match: reject both as
  `ADAPTED_SOURCE_FULFILMENT_MISMATCH`.
- A valid one-to-one group remains executable subject to the existing product-master projection.
  If either participant is excluded by the existing unresolved-product policy, exclude the entire
  pair from executable input so an alias cannot outlive its source. Keep unresolved-product
  reporting authoritative; do not relabel this established case as a new malformed-correlation
  reason.

For every rejected group, remove all participants from executable orders, prepared-line lists and
keys, and inbound manifests. Rebuild those immutable collections once after all groups have been
classified. Preserve source order among retained elements.

If an order retains at least one executable line, retain the order and its matching non-EMPTY
manifest with only those lines. If an order retains no executable lines, omit the executable order
and manifest. In particular, an all-rejected EMPTY order creates no AV02 tote, and an all-rejected
physical inbound order does not enter OSR or warehouse transport.

### 7. Narrow unclassified fallback

Named validation rules must be ordinary explicit branches, not exception-driven control flow.

Once a dependency group and all of its participants have been identified, its side-effect-free
validation may catch an unexpected `RuntimeException` and reject that complete group as
`UNCLASSIFIED_CORRELATION_ANOMALY`. The records must include the exception class, message, complete
stack trace, all affected source records, and a prominent `REQUIRES INVESTIGATION` diagnostic.

Never catch `Error`. Never apply this fallback while rebuilding projections, planning bags,
constructing runtime state, updating stations, evaluating completion, or executing simulation
steps. If a failure cannot be attributed safely to one complete dependency group, fail the run.
There must be no broad `catch (Exception)` that converts planner or runtime defects into data
rejections.

### 8. Individually malformed 12N files are recoverable at the loader boundary

Create `TwelveNInputMessage`, `TwelveNRejectedInputMessage`, and `TwelveNLoadResult` in
`online.davisfamily.warehouse.sim.dsp.io`.

Add `TwelveNDatasetLoader.loadRecovering(List<Path>)`. For each path in supplied order it must:

1. read the complete file;
2. if the file cannot be read, fail the load because this is an environmental/global input
   failure;
3. parse and structurally map the individual JSON document;
4. on a JSON syntax, JSON shape, or message-structure failure attributable to that document,
   retain a `TwelveNRejectedInputMessage` and continue with the next path.

Successful `TwelveNInputMessage` values retain path and zero-based encounter index. Rejected
values retain the same identity plus the implicit `MALFORMED_12N_MESSAGE` reason, exception
diagnostics, and stack trace. Keep existing `load`, `loadString`,
and `loadStrings` strict for current callers and focused parser tests.

The full-day loader alone uses `loadRecovering`. A malformed message cannot contribute reportable
lines because they cannot be parsed reliably; it is represented at message level in the rejection
catalog. Product-master parse failures, unreadable paths, invalid full-day configuration, and
dataset-global failures remain fatal.

### 9. The bag planner remains strict

`DspFullDayBagPlanningRequestFactory` consumes only `projection.executableData()`. Keep its missing
source, duplicate key, identity mismatch, manifest mismatch, and unclaimed-observation checks. If
preflight allows invalid correlated input to reach this factory, that is an implementation bug and
the run must fail.

Do not catch planner exceptions or move correlation recovery into `DeterministicBagPlanner`.
Every retained executable line still represents exactly one logical slot and at most one physical
pack. `numberOfPacks` and `numberOfPacksPicked` remain behaviourally irrelevant.

### 10. Reporting and process outcome

Extend `DspFullDayLoadedInput`, `DspFullDayProgressSnapshot`, and `DspFullDayAnalysisReport` with the
authoritative immutable rejection catalog. Retain compatibility constructors where tests or
non-full-day callers require them, defaulting to an empty catalog.

The start, periodic, completion, and final progress blocks show bounded aggregate rejection counts
only: rejected line count, rejected message count, and counts by reason. Do not traverse rejected
records per progress interval. The final detailed JSON and inspection output contain every rejected
record in source encounter order, including diagnostics and stack trace for unclassified cases.

If construction succeeds with one or more exclusions, print a concise startup diagnostic and make
the final report explicitly state `completedWithInputExclusions=true`. Preserve the existing
runtime termination reason and process exit code `0`; exclusions do not fabricate supported work
or change a genuine hard-cutoff result. Fatal configuration, construction, planner, and runtime
failures keep the existing nonzero command result.

Do not create a second diagnostic file. The existing optional durable progress log and final JSON
report are the durable outputs.

### 11. Future 32R boundary

The reportable order view and rejected-line catalog retain enough source order, line identity, and
reason information for later 32R work. This plan does not map internal reasons to protocol status.
In particular, do not encode status `58`, status `61`, or any other 32R code in validation.

An entirely rejected fulfilment order has a terminal logical reporting outcome and no physical DSP
tote. Future 32R generation must therefore support a non-physical terminal trigger in addition to
the normal exit-sensor trigger. That future behavior is documented here but not implemented.

### 12. Exception Station separation

Malformed-input rejection is not an operational Exception Station event. It creates no physical
pack, bag, empty bag, NS label, station visit, or failure waiver. The Exception Station requirements
continue to govern failures discovered after executable physical work exists.

## Efficiency And Publication Contract

- Loading, correlation classification, projection rebuild, and rejection catalog construction are
  linear in paths, messages, orders, lines, manifests, and correlation participants.
- Use method-local `LinkedHashMap`/`LinkedHashSet` indexes during construction and build each index
  once.
- Publish immutable lists, sets, maps, projections, and rejection records.
- Build catalog counts and target-order lookup indexes once; all catalog reads used by reports are
  constant-time except deliberate final-detail iteration.
- Runtime scheduler, station, workload, metrics, snapshot, completion, and P2P paths receive only
  executable data and perform no rejected-status checks or rejection scans.
- Progress formatting uses precomputed catalog counts and never scans lines or records.
- Do not introduce fingerprints, equality scans on reads, global/static caches, thread-locals,
  synchronization, weak maps, object pools, mutable duplicate indexes, or invalidation machinery.
- Do not move full-day traversal into snapshots, logging, metrics, completion evaluation, or any
  repeatedly executed method.

## Step 1: Preserve Source Identity And Recover Individual Message Parse Failures

Implement the loader and assembly metadata foundation only.

Required production changes:

- add `TwelveNInputMessage`, `TwelveNRejectedInputMessage`, and `TwelveNLoadResult`;
- add strict-read/recoverable-parse support to `TwelveNDatasetLoader` without weakening existing
  strict methods;
- add `DspRetainedInputLine`;
- add sourced assembly entry point `DspDatasetAssembler.assembleSourced(...)` and retain existing
  `assemble(...)` as a compatibility adapter;
- extend `LoadedDspData` with immutable retained-line metadata and compatibility construction.

Do not add correlation rejection or change full-day behavior in this step.

Required tests:

- successful paths preserve supplied path order and encounter indexes;
- one malformed JSON document is retained as a rejected message while later documents load;
- an unreadable file remains fatal;
- strict loader methods still fail on malformed input;
- source line indexes survive MANUAL-line filtering without renumbering later original lines;
- MANUAL records do not enter retained-line metadata;
- compatibility assembly derives deterministic fixture metadata.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNDatasetLoaderTest --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message:

```text
Preserve 12N source identity and recover malformed messages
```

## Step 2: Add Immutable Rejection Domain And Dependency-Closed Preflight

Add the types and exact rules in Fixed Decisions 2, 5, 6, and 7.

The preflight must be pure: it receives assembled immutable data plus loader-level message
rejections and returns a new immutable projection. It performs no runtime mutation and invokes no
station or planner.

Required tests in new `DspFullDayInputPreflightTest`:

- the reproduced ASSOCIATED alias with no source is reportable, rejected as
  `MISSING_ADAPTED_SOURCE`, and absent from every executable collection;
- an orphan source is rejected as `MISSING_ADAPTED_FULFILMENT` and creates no executable source
  work;
- duplicate sources reject every participant without first-wins behavior;
- duplicate fulfilment aliases reject every participant without first-wins behavior;
- mismatched product, pharmacy, patient, prescription, line type, and service centre each reject
  the complete pair;
- one valid pair remains unchanged and preserves source/alias identity;
- an unresolved product on either side excludes the complete pair while retaining existing
  unresolved-product reporting;
- partial-order rejection preserves sibling line encounter order and filters its manifest once;
- an all-rejected physical order has no executable order or manifest;
- an all-rejected EMPTY order has no executable order and cannot create a tote;
- reason and target-order indexes are immutable, built once, and reused by identity across reads;
- a group-scoped injected `RuntimeException` creates complete
  `UNCLASSIFIED_CORRELATION_ANOMALY` records with stack traces;
- a failure outside an attributable group remains fatal; and
- no `Error` is caught.

Add a package-private validator seam only if needed to inject the fallback test. It must not be a
production extension point or runtime callback.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflightTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message:

```text
Add dependency-closed DSP input rejection preflight
```

## Step 3: Integrate The Projection And Preserve Strict Planning

Change `DspFullDayInputLoader.load(...)` to execute this order exactly:

1. validate paths and load product master;
2. recover independently malformed 12N documents;
3. assemble successful sourced messages with existing MANUAL exclusion;
4. create loader-level rejected-message records;
5. preflight and partition reportable/executable input;
6. validate only executable orders/manifests against timetable and structural runtime rules;
7. construct the authoritative bag plan from executable data; and
8. publish `DspFullDayLoadedInput` containing executable data, reportable orders, rejection catalog,
   bag plan, existing load report, and timetable.

Remove the private `DspFullDayInputLoader.executableData(...)` projection after its product filtering
has moved into preflight. There must be one executable projection owner.

Keep `DspFullDayBagPlanningRequestFactory` strict. Add regression assertions proving direct factory
calls still reject missing/duplicate/mismatched correlation, while input-loader calls quarantine
the same attributable data before factory entry.

Create `DspFullDayBagPlanningRequestFactoryTest` in Step 3 as the focused owner of those direct
factory strictness regressions.

One completely rejected order within a day is valid and is absent from executable planning. The
existing full-day requirement that the complete supplied dataset contain at least one executable
order remains unchanged; do not add a second reporting-only runtime or weaken planner/runtime
nonempty-input validation in this plan.

Required tests:

- malformed message plus valid later messages loads and plans the valid subset;
- missing-source alias plus valid sibling line loads and plans only the sibling;
- all-rejected order data yields no executable tote, slot, bag, station requirement, or workload;
- valid correlation produces the same immutable slot, reserved pack ID, provenance, and bag as
  before;
- planner strict tests remain green; and
- `numberOfPacks`/`numberOfPacksPicked` remain behaviourally irrelevant.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayBagPlanningRequestFactoryTest --tests online.davisfamily.warehouse.sim.dsp.bagging.*
```

### User verification

No additional user verification is required for this step.

Proposed commit message:

```text
Integrate executable input projection with strict bag planning
```

## Step 4: Add Bounded Progress And Complete Final Reporting

Publish rejection information through the existing progress, inspection, and JSON ownership
boundaries. Do not alter runtime station or scheduler snapshots.

Required behavior:

- start progress reports aggregate exclusions before runtime advances;
- periodic progress and service-centre completion blocks reuse precomputed counts;
- final progress says whether the run completed with input exclusions;
- detailed inspection and JSON contain every rejected line/message in stable encounter order;
- unclassified records contain exception class and stack trace and say `REQUIRES INVESTIGATION`;
- named reasons use concise diagnostics without fabricated protocol status;
- rejected records are not added to `unsupportedWork` or `unfinishedIdentities`;
- command exit is zero for a completed run with recoverable exclusions and remains nonzero for
  fatal input/configuration/planner/runtime failure.

Required tests:

- progress output remains bounded as rejected-record count grows;
- progress formatter does not iterate catalog detail records;
- JSON round-trip/snapshot expectations include all rejection fields and stable ordering;
- inspection distinguishes exclusions from unsupported and unfinished work;
- command behavior proves zero versus nonzero outcomes; and
- existing failure logging remains intact for fatal exceptions.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayProgressOutputTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInspectionFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisCommandTest
```

### User verification

No additional user verification is required for this step.

Proposed commit message:

```text
Report recoverable DSP input exclusions
```

## Step 5: End-To-End, Scale, And Static Boundary Proof

Extend existing scenario and load-scale coverage; do not create a second runtime model.

Required tests:

- a mixed day containing valid ordinary, valid Third Party/ADAPTED, missing-source, orphan-source,
  duplicate, mismatched, malformed-message, partially rejected, and fully rejected orders reaches
  its normal supported terminal boundary;
- rejected lines create no slots, physical packs, bags, station work, P2P requirements, workload,
  completion obligations, AV02 allocations, OSR manifests, or transport launches;
- a fully rejected service-centre subset does not create physical work but remains visible in final
  rejection reporting;
- valid work before and after every anomaly completes in encounter order;
- the rejection catalog remains a small immutable side projection and does not alter runtime
  snapshot cardinality;
- generated scale input proves linear construction through deterministic visit counters or indexed
  lookup identity, not wall-clock thresholds; and
- static audit proves runtime packages do not reference `DspRejectedLine`,
  `TwelveNRejectedInputMessage`, or perform rejection/status scans.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadScaleTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.*
```

Then run:

```powershell
git diff --check
```

### User verification

After the architecture review below passes, the user runs the complete suite and the external
production day as part of Step 35 of
`docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`:

```powershell
.\gradlew test
```

The external run must pass the former missing-source failure, report that line as
`MISSING_ADAPTED_SOURCE`, continue processing later valid input, and terminate at supported
completion or the exact hard cutoff. It must exit zero unless a genuine fatal failure occurs.

Proposed commit message:

```text
Prove recoverable input rejection end to end
```

## Acceptance Criteria

- The complete production day does not stop for a recognized, independently attributable input
  anomaly.
- The reproduced line is retained for reporting and excluded from all executable and physical
  work.
- Every named correlation anomaly rejects the complete dependency group with deterministic order;
  duplicates never use first-wins behavior.
- Narrow unclassified quarantine cannot suppress planner, runtime, JVM `Error`, configuration, or
  environmental failures.
- MANUAL data remains excluded and is not reclassified as rejected DSP input.
- Reportable, executable, and rejected projections are immutable and have one authoritative
  construction owner.
- A partially rejected order executes only its valid lines.
- A fully rejected order creates no physical tote, pack, bag, station work, or completion
  obligation but remains available for future non-physical 32R reporting.
- Existing one-retained-line/one-pack semantics and numeric-field irrelevance remain unchanged.
- The bag planner remains a strict invariant backstop.
- Runtime stations, scheduler, workload, P2P, metrics, snapshots, and completion do not scan or
  branch over rejected lines.
- Construction is linear and catalog lookup is constant-time as specified.
- Progress remains bounded; final outputs contain complete diagnostics.
- Recoverable exclusions produce exit code zero; genuine implementation/global failures remain
  fatal.
- No 32R status, Exception Station outcome, NS label, empty bag, direct upstream ingestion, or
  MANUAL execution is implemented.

## End-Of-Feature Architecture Review

After Steps 1-5 pass, review the complete diff and report PASS, FAIL, or UNPROVEN with concrete
class/method evidence for every acceptance criterion. Additionally prove:

- every recovery catch is located at the individual-document loader boundary or inside an already
  identified side-effect-free correlation group;
- no recovery catch surrounds bag planning or runtime construction/execution;
- all rejection-group participants are removed from orders, prepared-line keys, manifests, and
  physical planning together;
- reportable line identity and source ordering survive projection;
- full rejection of an order cannot allocate an OSR/AV02/transport tote;
- planner strictness has not been weakened;
- rejection count/index objects are built once and reused;
- periodic progress and runtime snapshots perform no full rejection traversal; and
- all explicit deferrals remain absent.

No model-run command is authorized for the architecture review. Use the verified step results,
static code inspection, and user-reserved full-suite/external results as evidence.

## Documentation Closure

After implementation, architecture review, complete-suite verification, and external-data
verification are green:

- mark this plan complete and verified with commit and verification evidence;
- update `docs/codex-context.md` and `docs/codex-instructions.md` from planned to implemented;
- update the full-day plan's Step 35 with the observed rejection counts and external-run result;
- update the one-line correction and complete-bag remediation notices to identify the implemented
  preflight boundary;
- retain 32R status mapping, 32R generation/transmission, and non-physical terminal triggering as
  explicit future work; and
- do not mark Exception Station requirements implemented merely because malformed input is now
  quarantined.

If implementation requires a choice affecting API ownership, lifecycle, correlation, ordering,
caching, threading, input compatibility, runtime completion, or deferred Exception/32R behavior,
stop and amend this plan through explicit user agreement before continuing.
