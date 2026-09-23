# DSP Full-Day Runner Clock-Read Remediation Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: complete and verified at `f6b20a3`. The user-owned early and `PT7M` JFR gates found zero
`DspFullDayMetricsCollector.snapshot()` samples after the change. Overall full-day performance
remains unacceptably slow and is now owned by the separate fixed-step performance remediation
plan; the narrow clock-read correction is accepted without claiming an end-to-end speed target.

## Purpose And Evidence

Remove one measured polling-driven cost from the headless full-day runner. After every fixed step,
`DspFullDayAnalysisRunner.execute(...)` currently calls
`runtime.metricsSnapshot().clock().elapsedSimulationTime()` solely to decide whether a compact
progress interval was reached. `DspFullDayAnalysisRuntime.metricsSnapshot()` calls
`DspFullDayMetricsCollector.snapshot()`, which reads all metrics suppliers and builds a complete
metrics value. The default fixed step is 50 ms, so this happens 20 times per simulated second in
addition to the collector's required per-step `update(...)`.

In the user's initial 45-second JFR, 128 of 1,920 execution samples (6.67%) contain
`DspFullDayMetricsCollector.snapshot()`; all 128 contain `DspFullDayAnalysisRunner` and have
`DspFullDayAnalysisRuntime.metricsSnapshot()` as the immediate snapshot caller. Of those 128,
68 contain `readInputs`, 65 contain the elastic operational-snapshot path, 63 contain
`P2pWorkloadSnapshotFactory.create`, and 60 contain `buildSnapshot`. These are overlapping
sampled stacks, not exclusive CPU times or a prediction of end-to-end speedup. The recording
covers startup/early work only. The stopped external run reached simulated `PT14M` in roughly
24 minutes of wall time and continued to process totes and bags; it did not prove a deadlock or
full-day completion. Transport saturation and idle P2P lines are separate observations, not
proven causes of this clock-read cost.

This is a narrow, measurement-led correction before retrying the Step 35 external run in
`dsp-full-day-analysis-metrics-inspection-plan.md`. It does not claim to solve all run-performance
or operational-throughput problems.

## Required Reading For Implementation

Read `AGENTS.md`, `docs/codex-instructions.md`, and its mandatory document order; then read this
complete plan. Read the full-day plan's Existing Boundaries, Metrics Contract, Steps 7, 15, 33,
and 35; the clock plan's absolute-time and controller-snapshot contracts; and these exact files:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisRunner.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayAnalysisRuntime.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspFullDayAnalysisRuntimeFactory.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/metrics/DspFullDayMetricsCollector.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/time/DspOperationalClockController.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/DspFullDayAnalysisRunnerTest.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/metrics/DspFullDayMetricsCollectorTest.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/time/DspOperationalClockControllerTest.java`

Record `git status --short` before editing. Preserve the user's untracked JFR-analysis script
and every other pre-existing change. Stop if the inspected repository contradicts the clock or
runner assumptions below.

## Fixed Contract And Boundaries

- `SimulationContext` remains the authoritative elapsed simulation time. The registered
  `DspOperationalClockController` derives and retains the latest immutable
  `DspOperationalClockSnapshot` from that absolute time. Its `snapshot()` returns that already
  published value; it does not construct a full metrics snapshot.
- The full-day runtime factory registers the clock controller before later consumers and gives
  `DspFullDayMetricsCollector` the same `clockController::snapshot` supplier. The runner executes
  on the calling simulation thread after `runtime.update(stepSeconds)` returns. Reading the
  clock controller's latest snapshot at that point therefore observes the same completed step as
  the current metrics-based clock read.
- Change only the per-step elapsed-time read in `DspFullDayAnalysisRunner.execute(...)` to
  `runtime.clockController().snapshot().elapsedSimulationTime()`. Keep the state check before
  `runtime.update(...)`, the update, the progress-threshold check and advancement, and progress
  publication in their existing order. Do not retain a separate clock or accumulate elapsed time
  from `stepSeconds` or wall time.
- Leave `DspFullDayAnalysisRuntime.metricsSnapshot()`,
  `DspFullDayMetricsCollector.snapshot()` and `update(...)`, their public APIs, and all suppliers
  unchanged. The collector must still observe and account for every fixed step. Start, periodic,
  completion, batch, and final runtime snapshots remain at their existing boundaries; the
  optimization removes only the extra full metrics snapshot requested for the per-step progress
  predicate.
- Progress interval crossing remains first completed step at or beyond the threshold; one step
  cannot duplicate a threshold. A terminal step may still emit its interval block, and no world
  update or progress block may occur after termination. Completion, cutoff, speed accounting,
  logging failure semantics, deterministic reports, and report contents remain unchanged.
- Do not change fixed-step size, batch size, progress interval defaults, runner constructors,
  runtime APIs, thread ownership, scheduler/lease/transport behavior, metrics sampling frequency,
  caching, input handling, or output schemas. Add no mutable cache, test hook, instrumentation
  counter, new dependency, or background work.

## Step 1: Remove The Per-Step Full-Metrics Clock Read

### Change surface

Modify production only in `DspFullDayAnalysisRunner.java`, at the elapsed read inside the
`FixedStepExecutionDriver.advance(...)` callback. Modify tests only in
`DspFullDayAnalysisRunnerTest.java`. No other production or test file should change.

### Test contract

Keep the existing runner tests for early completion, exact hard cutoff, progress at thresholds
inside a batch, flushed failure logging, speed, and byte-identical repeated reports. Add one
runner test named `shouldReadPublishedClockForNonAlignedProgressThresholds` using the existing
`slowProfile(...)` fixture with a one-hour fixed step, three steps per batch, and a 90-minute
progress interval. Drive the real public `run(...)` path. Assert the exact progress-label sequence
`PT2H`, `PT3H`, `PT5H`, `PT6H`, `PT8H`, `PT9H`, `PT11H`, `PT12H`, `PT14H`, `PT15H`, `PT17H`,
`PT18H`, each exactly once; assert start before progress, final after the terminal `PT18H` block,
exact `HARD_CUTOFF_REACHED`, and report clock elapsed
`PT18H`. Assert the console and persisted progress log are identical. This catches an
independently accumulated clock, batch-boundary-only logging, an off-by-one threshold, and an
extra post-terminal update. The existing measured-speed and deterministic-report test remains
the report-value regression.

The source-level performance boundary cannot be proved by those output assertions: the old
full-metrics read produces the same values. Therefore the implementation review must separately
inspect the complete `execute(...)` callback and confirm it has no `metricsSnapshot()` or
`metricsCollector().snapshot()` call and obtains elapsed time only from the published clock
snapshot. Do not create a production test seam or source-text/bytecode-parsing test merely to
count this one call. The user's post-change JFR gate below supplies independent runtime evidence.

### Expected output

Each fixed step performs the existing metrics `update(...)` but does not construct an additional
complete metrics snapshot just to check progress. Existing report and progress behavior is
preserved.

### Implementation verification

The implementation agent runs exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollectorTest --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockControllerTest
```

Then review the Step 1 diff against every fixed contract, run `git diff --check`, and record
`git status --short`. Do not run the complete test suite or production-data/JFR scripts.

### User verification

No user-run test is required for Step 1. The user owns the performance gate in Step 2.

Proposed commit message: `Avoid per-step full-day metrics snapshots for progress`

## Step 2: User-Owned Equal-Work Performance Gate

No production or test edit is authorized by this step. After Step 1's focused test and review
pass, the user rebuilds the installed distribution and runs the same external dataset,
simulation-affecting configuration, JDK/heap settings, and 60-second simulated progress interval.
Because the existing external configuration has `overwrite=false`, make a copy outside source
control with distinct report, inspection, and progress-log output paths; do not alter any
simulation-affecting setting or overwrite the existing results. Use a new JFR and
analysis-output prefix; do not overwrite the existing Step 34 recording or reports. The user
captures 45 seconds near the same early simulated-work interval as the original recording and
records the nearest progress blocks and wall-clock timestamps. The user may also capture a
second 45-second recording around `PT10M` to investigate later-run costs; that later recording is
diagnostic and is not required to prove the narrow clock-read removal. The user runs their own
JFR extraction and allocation-analysis PowerShell scripts and provides summaries; the
implementation agent must not run or edit those potentially slow scripts.

Compare the before/after execution stacks and weighted allocation-by-site/class data with
equal-work context, plus young/old GC counts, longest pause, post-GC heap, and simulated-time
progress per wall-clock time. Required narrow acceptance is that the repeated stack
`DspFullDayAnalysisRunner` -> `DspFullDayAnalysisRuntime.metricsSnapshot()` ->
`DspFullDayMetricsCollector.snapshot()` disappears from fixed-step execution; legitimate
metrics snapshots at start, progress, batch, and final boundaries remain allowed. Report any
throughput change rather than imposing a noisy percentage threshold. If the baseline and new
recordings do not cover comparable work, mark quantitative speedup UNPROVEN, not zero or proven.
Do not add percentages for overlapping stack frames or treat weighted allocation samples as
exact CPU time.

If the runner stack persists, or progress/report behavior changes, Step 1 is not accepted. If
the stack disappears but the run remains slow, this remediation can be accepted as narrow while
the separate remaining bottleneck is reported and planned from new evidence. A stopped run still
does not satisfy the full-day plan's Step 35 external completion/cutoff criterion.

### Implementation verification

No model-run command is authorized in Step 2.

### User verification

The user runs:

```powershell
.\gradlew :app:installDist
& 'C:\Java\jdk\21.0.7\bin\java.exe' -cp 'app\build\install\app\lib\*' online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisMain --config=C:\misc\cpas-test\scheduler-testing\config\scheduler_conf_clock_read.json
```

The user owns the bounded external run, JFR capture, and analysis described above. The complete
Gradle suite and full-day terminal external verification remain owned by Step 35 of the full-day
plan, not this narrow gate.

Proposed commit message: none; this step creates no repository change.

## End-Of-Remediation Review And Documentation Closure

After the focused test and user performance gate, review the actual diff and report PASS, FAIL,
or UNPROVEN for direct authoritative-clock use, controller order, first-crossing progress,
terminal/no-extra-step behavior, unchanged metrics update cadence and report APIs, unchanged
threading and failure behavior, and the complete unchanged boundaries above. Confirm no
production file other than the runner changed. Do not infer the performance result from tests
alone; use the user-supplied JFR and progress evidence. No model-run command is authorized for
the review.

If review and user verification are green, mark this plan complete with the commit and measured
result, and add a brief factual result to the full-day plan's Step 35 prerequisite note. Do not
revise scheduler, P2P allocation, transport, metrics internals, or the general runtime-efficiency
rule as part of this closure. If a different performance problem is found, create a separately
authorized measurement-led plan. The full-day feature itself remains open until its Step 35
regression and terminal external run pass.
