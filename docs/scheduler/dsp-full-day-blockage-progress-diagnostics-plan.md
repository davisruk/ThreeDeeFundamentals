# DSP Full-Day Blockage Progress Diagnostics Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned. This plan authorizes no implementation by itself. The user starts each step.

## Purpose and established evidence

Keep `DEADLINE_AWARE_ELASTIC_STICKY_LEASES` for one comparable diagnostic run. The latest
`progress.log` confirms that this is the selected policy. Its service-centre 104 demand of one
line at PT0 is consistent with the uncalibrated deadline formula; it is not evidence of a wrong
policy. The run stops making tote/pack/bag progress around PT15M while continuation, transport,
and station-arrival queues are backed up and P2P line 1 stops draining. The current compact log
does not expose enough exact head, bench, or P2P state to identify the initiating blockage.

This plan adds bounded progress-boundary evidence, not a stall fix, policy change, calibration,
performance remediation, or 32R/dispatch implementation. It also places per-service-centre
allocated-bag counts on the existing closed-outbound-tote line, without lengthening the already
long service-centre lines.

## Required reading before either implementation step

Read `AGENTS.md`, `docs/codex-instructions.md` and its mandatory document order, then this
complete plan. Read the full-day analysis plan's report/progress contract and Step 15, and the
fixed-step performance plan's shared architecture/efficiency contract. Inspect the exact source
and test files named in the selected step. Record `git status --short` before editing. Preserve
existing changes. If a named file or test is absent, or the inspected code contradicts this
plan, stop and report rather than substituting an architecture.

## Shared contracts

- Run on the existing calling simulation thread. No new controller, worker, background logger,
  thread, configuration option, or logging framework. Keep the existing progress schedule,
  milestones, wall-clock measurement, console/file mirroring, flush behavior, failure behavior,
  and detailed final report unchanged.
- Do no diagnostic work inside a fixed-step update. Use the already-created immutable
  `DspFullDayAnalysisRuntimeSnapshot` at a routine progress milestone. Read extra live Adapting
  and tipper values only on that same simulation thread, after the step and only when the
  blocked-progress predicate is true. Do not cache mutable machine collections in a snapshot.
- Do not call candidate admission, bench selection, scheduler evaluation, `canAdmit`, or another
  decision method merely to produce diagnostics. No mutation, dequeue, completion, or new
  simulation step may occur for logging. No per-order/per-pack/per-PRL identity dump.
- The progress lines must be deterministic and bounded by the configured service-centre, bench,
  and P2P-line counts, plus constant-size FIFO-head details. Preserve the existing
  `ClosedOutboundTotesByServiceCentre:` prefix and its `id=count` segment verbatim so existing
  log consumers can still find that segment. Append the bag segment on the same line.
- “Completed bag” in this log means an `AllocatedOutboundBag` in outbound allocation history,
  including one in an open outbound tote. Label it `AllocatedBagsByServiceCentre`; do not imply
  that the containing outbound tote is closed or dispatched. Bag ownership comes from
  `allocatedBag.plannedBag().serviceCentreId()`, never from the P2P line's current lease.
- The Step 15 prohibition on changing runtime/station/machine code applied to that completed
  step. Step 2 below explicitly permits only additive read-only diagnostic queries in those
  owners. All existing snapshot constructors, record components, public API behavior,
  processing/admission order, line allocation, and station routing remain unchanged.

## Step 1: Append allocated bags to the existing per-centre outbound line

### Change surface and behavior

Modify only `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisRunner.java`
and `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisRunnerTest.java`.
Keep `closedOutboundTotesByServiceCentre(List<OutboundAllocationSnapshot>, List<String>)`
as the existing package-private helper and keep its tote-count segment exactly as before.
Append ` | AllocatedBagsByServiceCentre: 104=n 108=n ...` using the same ascending ID order
as the tote segment and including zero-count configured centres. The helper reads each supplied
allocation's `closedTotes()` and `allocatedBags()` once, not each tote's bag contents or the
line's current owner. Empty allocations still produce both zero-count segments. Do not change
`DspServiceCentreMetricsSnapshot`, `DspFullDayProgressFormatter`, metrics/report schemas, or
global rate/count arithmetic.

### Decision-complete tests

In `DspFullDayAnalysisRunnerTest`, extend the existing direct helper test with valid allocation
fixtures spanning two lines, at least two centres, a closed tote containing bags, an open tote
containing an allocated bag, and a configured zero-work centre. Assert the exact whole-line text,
  that the tote segment remains byte-for-byte unchanged, that allocated bags in open totes count,
  and that bags belonging to different centres on one line's historical allocation snapshots
  follow their planned service centre. Reuse
valid `PlannedBag`, `AllocatedOutboundBag`, and `OutputSheetAllocation` domain fixtures; do not
weaken outbound-snapshot validation. Extend the runner's console/file parity assertion to check
the new segment in the same line. No state is mutated by the helper.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest
```

### User verification

No additional user verification is required for this step. Stop for user acceptance before Step 2.

## Step 2: Add blockage-triggered milestone diagnostics

### Trigger, ordering, and output

Modify `DspFullDayAnalysisRunner.printProgress(...)` to receive its existing live
`DspFullDayAnalysisRuntime` alongside the immutable snapshot. Only for a milestone whose label
starts with `progress=`, append a diagnostic section when at least one of these values in that
snapshot is blocked: `continuation().blocked()`, `transportArrival().blocked()`, or
`transportIngress().blocked()`. Do not use occupancy alone as a trigger; a full queue can be
transient. Do not append diagnostics to start, completion, final, or failure blocks. Evaluate the
predicate before reading any extra live state; if false, keep the compact block exactly as it is
after Step 1. A blocked milestone prints one section every time it occurs; no signature cache or
suppression, so the log preserves how a blockage evolves. Place diagnostics after the existing
station line and before load/unsupported/remaining lines, without reordering existing sections.

Create `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayBlockedProgressFormatter.java`.
Its `describe(DspFullDayAnalysisRuntimeSnapshot, List<AdaptingBenchAdmissionSnapshot>,
Map<P2pLineId, Optional<String>>)` returns immutable lines with the following exact facts and
stable order (private helper decomposition/decimal spelling is discretionary):

1. `BlockedProgress:` with the three blocked reasons (or `none`), continuation head tote/type,
   selected next station/destination, transport-ingress head tote/destination, and its queue and
   in-flight occupancies/capacities. The optional selected destination must not be presented as
   a committed route.
2. `BlockedProgress.TransportArrival:` with pending count, first pending tote/destination/
   terminal sensor, blocked tote, and blocked reason. Use only the FIFO first element; emit
   `none` for absent fields. Do not dump every pending arrival.
3. One `BlockedProgress.Adapting[benchId]:` in ascending bench ID order, with bench state,
   active tote and visit type, remaining processing seconds, local queue occupancy/capacity,
   FIFO head tote, and bench's local admission-open value and reason. This is bench processing
   capacity, not physical bin occupancy. Do not call `AdaptedLineStore.binSnapshots()`.
4. One `BlockedProgress.P2P[lineId]:` in configured line order, with current owner from the
   matching metrics line, active tipper tote ID (or `none`), active-tipper indication,
   station-arrival and tipper-input counts, active discharge count, PRL counts for every
   `PrlState` in enum order, total PRL received-pack count, the existing pack-path counts
   (including outstanding expected bag groups and PCR packs), and bagging flags/counts
   (including receiver-completed bags and pending/active discharge). Use the line's already
   captured `DspHeadlessP2pLineRuntimeSnapshot`; do not query PRLs again or list 31 IDs.

The formatter validates non-null inputs and exact line-ID coverage in the active-tote map. It
must not infer a root cause or call a blocker “deadlocked” solely from a blocked snapshot.

### Exact additional read-only surface

- In `app/src/main/java/online/davisfamily/warehouse/sim/dsp/adapting/AdaptingArea.java`, add
  `public List<AdaptingBenchAdmissionSnapshot> benchAdmissionSnapshots()`. Reuse the existing
  sorted bench IDs and existing `AdaptingBench.snapshot()`/`MachineWaitQueue.snapshot()` values.
  Each entry reports `slot.canAcceptVisit()` and the existing local-full reason, without
  `selectBenchFor(...)` or a candidate profile. Have `admissionSnapshotFor(profile)` call this
  query for its bench list and retain its existing selection/validation behavior. Return an
  immutable ordered list.
- In `app/src/main/java/online/davisfamily/warehouse/sim/totebag/control/ToteTrackTipperFlowController.java`,
  add `public Optional<String> activeToteId()` returning the current `activeTote.getId()` or
  empty. It is an observation only; do not expose the mutable `Tote`, load plan, or controller
  state for mutation.
- In `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayAnalysisRuntime.java`,
  retain the existing `AdaptingArea` instance in a new final field, passed immediately after
  `AdaptedLineStore` by the sole construction site in
  `DspFullDayAnalysisRuntimeFactory.java`. Add
  `public List<AdaptingBenchAdmissionSnapshot> adaptingBenchAdmissionSnapshots()` delegating
  to the area. Like `adaptingBinSnapshots()`, it is an explicit simulation-thread inspection
  method, never called by `snapshot()` or an update.
- In the runner, when the predicate is true, call the runtime's bench query once and build a
  `LinkedHashMap<P2pLineId, Optional<String>>` by iterating its configured line runtimes once
  and reading each tipper's `activeToteId()` once. Pass those and the already-created runtime
  snapshot to the new formatter. Do not change `DspFullDayAnalysisRuntimeSnapshot` or any
  existing immutable record constructor.

Modify only the files above plus these focused tests:
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/adapting/AdaptingAreaAdmissionTest.java`,
`app/src/test/java/online/davisfamily/warehouse/sim/totebag/ToteTrackTipperFlowControllerTest.java`,
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayAnalysisRuntimeFactoryTest.java`,
`app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisRunnerTest.java`,
and new `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/report/DspFullDayBlockedProgressFormatterTest.java`.
Do not alter scheduler policy, admission decisions, queue capacities, station processing,
transport publication, outbound allocation, fixed-step scheduling, metrics calculations,
configuration, detailed inspection, or final JSON schema.

### Decision-complete tests

- `AdaptingAreaAdmissionTest`: two benches constructed out of ID order; one active with a queued
  tote and closed local admission, one idle and open. Assert sorted immutable snapshots with
  exact state, active ID, queue head/capacity, openness/reason; call again after a real bench
  transition and assert the first snapshot remains unchanged. Existing candidate-specific
  `admissionSnapshotFor` selection remains unchanged. The query performs no submit/dequeue.
- `ToteTrackTipperFlowControllerTest`: empty before admission, exact ID while active, empty after
  normal completion; reading the ID does not advance or capture the tote.
- `DspFullDayAnalysisRuntimeFactoryTest`: constructed full-day runtime's new bench query reports
  the configured benches in ID order and does not alter a before/after regular runtime snapshot.
  The Step 2 diff review must confirm that neither `snapshot()` nor fixed-step update calls this
  query; no production call may be added to `DspFullDayAnalysisRuntimeSnapshotFactory`.
- `DspFullDayBlockedProgressFormatterTest`: immutable fixture with one blocked FIFO head,
  another pending arrival, two out-of-order bench IDs, one occupied and one idle P2P line,
  a non-idle PRL, and an active tipper ID. Assert exact section/line ordering, head-only
  identities, count/flag fidelity, deterministic repeat output, and no large identity dump.
  Null/missing-line-ID inputs fail before output.
- `DspFullDayAnalysisRunnerTest`: add a package-private static
  `appendBlockedProgressLines(String milestone, DspFullDayAnalysisRuntimeSnapshot snapshot,
  Supplier<List<AdaptingBenchAdmissionSnapshot>> benches,
  Supplier<Map<P2pLineId, Optional<String>>> activeTipperIds, List<String> lines)` to the runner;
  `printProgress(...)` calls this exact helper. Test it with counting suppliers and an immutable
  blocked/unblocked runtime-snapshot fixture. A blocked `progress=` block inserts the diagnostic
  lines once immediately after `Station:`, while an unblocked progress and
  start/completion/final block do not call either supplier or insert diagnostics. Retain the
  existing full-run byte-identical console/log mirroring and milestone-order assertions, and
  assert no extra monotonic-clock reads. Do not rely on a long full-day run or timing threshold.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaAdmissionTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayBlockedProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest
```

Review the complete Step 2 diff against this plan; run `git diff --check` and report final
`git status --short`. No performance run, JFR, or full suite by the implementation agent.

### User verification

The user runs `.\gradlew test` as the broader regression check. After rebuilding the installed
distribution, the user may run the same recent dataset with the deadline-aware policy and a
PT1M progress interval until at least the previously observed PT15M-PT16M blockage. Inspect
the preserved progress log for the head identities, bench/PRL/active-tipper state, and the
per-centre bag figures. This is a diagnostic comparison, not proof of cause or speedup.

## End-of-feature review and documentation closure

After focused and user verification, review the actual diff and directly trace that every new
read occurs only at the blocked progress boundary, no simulation or scheduler decision changes,
the existing progress/tote-count segment remains compatible, and the diagnostic output is
bounded and truthful about what is observed versus inferred. Mark each item PASS, FAIL, or
UNPROVEN; do not call the stall fixed or the deadline-aware policy incorrect merely because a
diagnostic block appears.

After that review is green, mark this plan complete, add a short follow-on logging cross-reference
to `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md` without rewriting its
completed Step 15, and update `docs/codex-context.md` with the verified logging contract and
remaining stall investigation. No domain requirements document changes are needed because this
plan changes observation only. Change only stale reading-order/current-position text in
`docs/codex-instructions.md` if this plan becomes mandatory reading. Do not document a root
cause until the subsequent run proves one. If the run exposes a new architecture decision, stop
and create a separate stall-fix plan; a whole-service-centre-first policy remains separate later
work.
