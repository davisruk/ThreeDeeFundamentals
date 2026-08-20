# DSP Operational Simulation Clock Plan

Branch: `feature/dsp-operational-simulation-clock`

Status: implementation complete, verified, and merged.

Verification completed:

- all focused Step 1-8 tests are green;
- the Step 9 focused regression coverage and complete Gradle suite are green;
- the Adapting, Third Party, and integrated tote-to-bag/P2P visual smoke checks are green;
- existing scene motion remains unchanged because fixed-step execution is not yet wired into `SoftwareRenderer`;
- `ALT+R` reset behavior remains correct in the checked scenes.

## Purpose

Introduce deterministic DSP business time and a bounded fixed-step execution boundary without coupling scheduler decisions to wall-clock time or passing accelerated frame deltas directly through machines and sensors.

This branch must:

- map simulation elapsed time from a configurable operating date and normal start time;
- represent times after midnight with an explicit operating-day offset;
- distinguish normal operations, overtime, and the hard processing cutoff;
- expose immutable clock snapshots suitable for scheduler-worker, inspection, and later metrics use;
- provide configurable real-to-simulation scaling through repeated bounded fixed steps;
- represent realtime, accelerated visual, and headless analysis execution semantics;
- retain unprocessed accumulator backlog rather than dropping simulation time or increasing step size;
- report requested and achieved simulation speed from explicitly supplied elapsed durations;
- bridge the clock to the existing `SimulationWorld`/`SimulationContext` update model without changing machine duration units;
- preserve reset behavior by making a newly constructed clock/controller/driver start from deterministic zero state.

This branch does not implement service-centre timetables, trunker deadlines, completion outcomes, OSR low-water authorization, rate-limited tote supply, physical OSR release, scheduler candidate filtering, P2P leases, render-loop decimation wiring, a complete headless warehouse runner, render-thread separation, metrics history, or full-day dataset execution.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-osr-physical-inventory-plan.md`
4. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
5. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `SimulationWorld`
- `SimulationContext`
- `SimulationController`
- `BaseScene`
- `SoftwareRenderer`
- `TestScene`
- `DspSchedulerController`
- `ThreadedSchedulerEvaluationSource`
- `PhysicalToteAssignment`
- `OutboundToteAllocator`
- `OutboundToteAllocationController`
- `OsrInventoryConfig`
- `OsrInventorySnapshot`

## Fixed Decisions

Do not revisit these during implementation:

- `SimulationContext.getSimulationTimeSeconds()` remains the authoritative elapsed simulation time used by existing machines, sensors, and lifecycle controllers.
- Simulation elapsed time zero maps to the configured normal operating start. The production baseline maps zero to `06:00` on the configured operating date.
- Business time is derived from absolute elapsed simulation time. Do not maintain a second independently accumulated business clock that can drift from `SimulationContext`.
- Use `java.time.LocalDate`, `LocalTime`, `LocalDateTime`, and `Duration`. Do not use the host default time zone, `Instant.now()`, `LocalDateTime.now()`, or `System.currentTimeMillis()` in clock-domain code.
- An operating-day timestamp is represented by a non-negative day offset plus a local time. Do not encode day +1 by treating `00:00` as later than `22:00` without the day offset.
- Production operating-window defaults are normal start day 0 at `06:00`, normal end day 0 at `22:00`, and hard cutoff day 1 at `00:00`.
- At exactly the normal end, phase is `OVERTIME`. At exactly the hard cutoff and afterward, phase is `HARD_CUTOFF_REACHED`.
- The hard cutoff is an observable time condition only in this branch. It does not stop the world, close totes, mutate fulfilment outcomes, or emit scheduler commands.
- Trunker departure, downstream handling duration, per-service-centre target completion, latest completion, and completion outcomes belong to a later deadline branch. Do not add them to the base clock configuration.
- All machine and station durations remain simulation seconds. Do not scale individual machine configuration values.
- Acceleration occurs by multiplying supplied real elapsed duration into an accumulator and executing repeated fixed simulation steps. Never send one scaled large delta to `SimulationWorld.update(...)`.
- Each emitted step is exactly the configured fixed-step duration. A per-advance step budget limits work; excess complete and partial simulation time stays in the accumulator for later advances.
- Do not silently discard backlog to make the visual run catch up. Snapshot it so later inspection can report lag.
- `REALTIME` requires requested time scale `1.0`. `ACCELERATED_VISUAL` requires a finite requested scale greater than zero. `HEADLESS_ANALYSIS` advances a configured maximum batch as fast as the caller invokes it and does not request rendering.
- Render decimation is represented by configuration and `renderDue` results. Do not refactor `SoftwareRenderer`, `Scene`, `BaseScene`, or current debug rigs to consume it in this branch.
- Actual achieved speed is cumulative simulated duration divided by cumulative caller-supplied real duration. No tests use sleeps, deadlines, or narrow real-time windows.
- Headless actual-speed accounting uses real duration measured by the eventual runner and passed into the driver. The driver itself does not read a system clock.
- Clock and execution snapshots are immutable and safe to pass to scheduler-worker or inspection code.
- Mutable clock-controller and fixed-step-driver state are owned by the simulation/runtime thread. Add no locks, worker threads, or concurrent collections.
- Existing scheduler snapshot, command, OSR inventory, lifecycle, station, and rendering contracts remain unchanged.
- Existing simulation reset continues to reconstruct the scene/world. New clock/runtime objects reset by reconstruction; do not add a global reset registry.

## Package And Vocabulary

Create DSP business-clock types under:

```text
online.davisfamily.warehouse.sim.dsp.time
```

Use these names:

- `OperationalDayTime`: local time with an explicit non-negative operating-day offset.
- `DspOperatingPhase`: `NORMAL_OPERATIONS`, `OVERTIME`, or `HARD_CUTOFF_REACHED`.
- `DspOperationalClockConfig`: operating date and configured operating-window boundaries.
- `DspOperationalClockSnapshot`: immutable elapsed and business-clock state.
- `DspOperationalClock`: pure elapsed-time-to-business-time mapper.
- `DspOperationalClockController`: simulation controller that publishes the latest immutable clock snapshot.

Create generic fixed-step execution types under:

```text
online.davisfamily.threedee.sim.framework.time
```

Use these names:

- `SimulationExecutionMode`: `REALTIME`, `ACCELERATED_VISUAL`, or `HEADLESS_ANALYSIS`.
- `FixedStepExecutionConfig`: fixed step, requested scale, per-advance work budget, and visual render interval.
- `FixedStepAdvance`: immutable result of one driver advance.
- `FixedStepExecutionSnapshot`: immutable cumulative execution/speed/backlog state.
- `FixedStepExecutionDriver`: mutable runtime-thread-owned accumulator and bounded-step dispatcher.

Do not place business-date concepts in the generic engine package. Do not place the generic fixed-step accumulator in the DSP package.

## Step 1: Add Explicit Operating-Day Time

Create:

```java
public record OperationalDayTime(int dayOffset, LocalTime localTime)
        implements Comparable<OperationalDayTime>
```

Rules:

- reject a negative day offset;
- reject null local time;
- compare first by day offset and then by local time;
- expose `LocalDateTime onOperatingDate(LocalDate operatingDate)`;
- reject a null operating date;
- expose static factories `day0(LocalTime)` and `day1(LocalTime)`;
- do not add time-zone behavior.

Create `DspOperatingPhase` with exactly:

```text
NORMAL_OPERATIONS
OVERTIME
HARD_CUTOFF_REACHED
```

Create `OperationalDayTimeTest`.

Required tests:

- `shouldOrderTimesUsingExplicitDayOffset()`
- `shouldResolveDayOffsetAgainstOperatingDate()`
- `shouldRejectInvalidOperatingDayTime()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.OperationalDayTimeTest
```

## Step 2: Add Operational Clock Configuration

Create:

```java
public record DspOperationalClockConfig(
        LocalDate operatingDate,
        OperationalDayTime normalStart,
        OperationalDayTime normalEnd,
        OperationalDayTime hardCutoff)
```

Rules:

- reject null values;
- require `normalStart.dayOffset() == 0`;
- require `normalStart < normalEnd < hardCutoff`;
- expose resolved `normalStartDateTime()`, `normalEndDateTime()`, and `hardCutoffDateTime()` helpers;
- expose `operatingDurationUntilNormalEnd()` and `operatingDurationUntilHardCutoff()`;
- expose `productionBaseline(LocalDate operatingDate)` with day 0 `06:00`, day 0 `22:00`, and day 1 `00:00`;
- do not add service-centre timetable or downstream handling configuration.

Create `DspOperationalClockConfigTest`.

Required tests:

- `shouldCreateProductionOperatingWindowForConfiguredDate()`
- `shouldCalculateNormalAndHardCutoffDurationsFromStart()`
- `shouldRejectMisorderedOperatingWindowBoundaries()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfigTest
```

## Step 3: Add Immutable Operational Clock Snapshots

Create `DspOperationalClockSnapshot` with exactly:

```java
Duration elapsedSimulationTime
LocalDate operatingDate
LocalDateTime businessDateTime
OperationalDayTime operatingDayTime
DspOperatingPhase phase
LocalDateTime normalEndDateTime
LocalDateTime hardCutoffDateTime
```

Validation rules:

- reject null values;
- reject negative elapsed simulation time;
- require `businessDateTime` to equal `operatingDayTime.onOperatingDate(operatingDate)`;
- require phase/boundary consistency:
  - before normal end: `NORMAL_OPERATIONS`;
  - at or after normal end but before hard cutoff: `OVERTIME`;
  - at or after hard cutoff: `HARD_CUTOFF_REACHED`;
- require normal end before hard cutoff;
- expose `normalEndReached()` and `hardCutoffReached()` convenience methods;
- do not expose mutable collections or wall-clock values.

Use a package-private static factory owned by `DspOperationalClock` if it keeps phase classification in one place. Do not duplicate time-mapping arithmetic in tests or callers.

Create `DspOperationalClockSnapshotTest`.

Required tests:

- `shouldRepresentNormalOvertimeAndHardCutoffPhases()`
- `shouldExposeConfiguredBoundaryState()`
- `shouldRejectInternallyInconsistentSnapshot()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshotTest
```

## Step 4: Implement Deterministic Business-Time Mapping

Create `DspOperationalClock` around one `DspOperationalClockConfig`.

Expose:

```java
public DspOperationalClockSnapshot snapshotAt(Duration elapsedSimulationTime)
public DspOperationalClockSnapshot snapshotAtSimulationSeconds(double simulationTimeSeconds)
public DspOperationalClockSnapshot initialSnapshot()
```

Rules:

- reject null or negative duration;
- reject negative, NaN, or infinite simulation seconds;
- convert double seconds to `Duration` using rounded nanoseconds, matching current lifecycle-controller convention;
- map elapsed zero to configured normal start;
- derive business date/time by adding elapsed duration to the resolved normal start;
- derive `OperationalDayTime` from the resulting date difference and local time;
- support elapsed time after day +1 without wrapping the day offset;
- classify exact normal-end and hard-cutoff boundaries according to the fixed decisions;
- remain stateless and deterministic.

Create `DspOperationalClockTest`.

Required tests:

- `shouldMapElapsedZeroToConfiguredNormalStart()`
- `shouldMapElapsedTimeAcrossMidnightWithExplicitDayOffset()`
- `shouldClassifyExactNormalEndAndHardCutoffBoundaries()`
- `shouldMapSimulationSecondsUsingRoundedNanoseconds()`
- `shouldRejectInvalidElapsedTime()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockTest
```

## Step 5: Add Fixed-Step Execution Configuration

Create `SimulationExecutionMode` with exactly:

```text
REALTIME
ACCELERATED_VISUAL
HEADLESS_ANALYSIS
```

Create `FixedStepExecutionConfig` with exactly:

```java
SimulationExecutionMode mode
Duration fixedStep
double requestedTimeScale
int maximumStepsPerAdvance
int renderEveryAdvanceCount
```

Rules:

- reject null mode or fixed step;
- require a positive fixed step that converts to a positive finite number of seconds;
- require finite requested scale greater than zero;
- require requested scale exactly `1.0` for `REALTIME`;
- require maximum steps per advance at least one;
- require render interval at least one for visual modes;
- require render interval exactly one for `HEADLESS_ANALYSIS`; it is retained only to keep one compact configuration shape and is ignored because headless never renders;
- expose baseline factories:
  - `realtime(Duration fixedStep, int maximumStepsPerAdvance)`;
  - `acceleratedVisual(Duration fixedStep, double requestedTimeScale, int maximumStepsPerAdvance, int renderEveryAdvanceCount)`;
  - `headless(Duration fixedStep, int maximumStepsPerAdvance)`.

Do not parse command-line options or change `DebugSceneOptions` in this branch.

Create `FixedStepExecutionConfigTest`.

Required tests:

- `shouldCreateRealtimeAcceleratedAndHeadlessConfigurations()`
- `shouldRejectInvalidFixedStepAndWorkBudget()`
- `shouldEnforceExecutionModeScaleRules()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.threedee.sim.framework.time.FixedStepExecutionConfigTest
```

## Step 6: Implement The Bounded Fixed-Step Driver

Create a functional callback boundary:

```java
@FunctionalInterface
public interface SimulationStepConsumer {
    void advance(double fixedStepSeconds);
}
```

Make it public because the DSP scenario and later runtime integration call the generic driver from outside the engine timing package. Do not reuse `SimulationController`; this callback advances the complete simulation world, not one controller.

Create `FixedStepAdvance` with exactly:

```java
int executedStepCount
Duration fixedStep
Duration advancedSimulationTime
Duration pendingSimulationTime
boolean renderDue
```

Create `FixedStepExecutionSnapshot` with exactly:

```java
SimulationExecutionMode mode
Duration fixedStep
double requestedTimeScale
long completedStepCount
Duration totalRealTime
Duration totalSimulationTime
Duration pendingSimulationTime
double achievedTimeScale
```

Create `FixedStepExecutionDriver` with:

```java
public FixedStepAdvance advance(Duration realElapsedTime, SimulationStepConsumer consumer)
public FixedStepExecutionSnapshot snapshot()
```

Driver rules:

- reject null or negative real elapsed time and a null consumer;
- use integer nanoseconds for accumulator arithmetic;
- in visual modes, multiply real elapsed nanoseconds by requested scale using deterministic rounding, add to pending simulation time, and emit complete fixed steps up to the configured budget;
- retain all unexecuted complete steps and fractional remainder as pending simulation time;
- call the consumer once per step with exactly `fixedStep.toNanos() / 1_000_000_000d`;
- in `HEADLESS_ANALYSIS`, emit exactly `maximumStepsPerAdvance` steps per call regardless of real elapsed duration and never mark rendering due;
- in visual modes, make the first advance renderable, then mark rendering due according to `renderEveryAdvanceCount`;
- update cumulative real time from the supplied argument in every mode;
- calculate achieved scale as cumulative simulation nanoseconds divided by cumulative real nanoseconds, returning `0.0` while cumulative real time is zero;
- detect nanosecond multiplication/addition overflow and fail clearly rather than wrapping;
- do not use a loop to manufacture one enormous `dt`; every consumer call remains bounded;
- do not call `System.nanoTime()` or sleep.

Create `FixedStepExecutionDriverTest`.

Required tests:

- `shouldEmitRepeatedBoundedStepsForAcceleratedVisualTime()`
- `shouldRetainBacklogWhenAdvanceBudgetIsExhausted()`
- `shouldPreserveFractionalTimeUntilACompleteStepExists()`
- `shouldDecimateVisualRenderRequestsWithoutChangingSimulationSteps()`
- `shouldRunHeadlessBatchesWithoutRequestingRendering()`
- `shouldReportRequestedAndAchievedSimulationSpeed()`
- `shouldRejectInvalidElapsedTimeAndOverflow()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.threedee.sim.framework.time.FixedStepExecutionDriverTest
```

## Step 7: Bridge Business Time To SimulationWorld

Create `DspOperationalClockController implements SimulationController`.

Constructor input:

```java
DspOperationalClock clock
```

Behavior:

- initialize the latest snapshot from `clock.initialSnapshot()`;
- on `update(SimulationContext context, double dtSeconds)`, reject null context and map the context's absolute `getSimulationTimeSeconds()` value;
- ignore `dtSeconds` for business-time arithmetic so the controller cannot drift from the authoritative context time;
- expose the latest immutable snapshot through `snapshot()`;
- do not publish events, scheduler commands, or cutoff mutations;
- document that it must be registered before a controller that builds scheduler snapshots from it.

Create `DspOperationalClockControllerTest` using a real `SimulationWorld` with the controller registered.

Required tests:

- `shouldStartAtConfiguredOperatingTimeBeforeWorldAdvances()`
- `shouldFollowAbsoluteSimulationContextTime()`
- `shouldNotDriftWhenWorldUsesUnevenBoundedSteps()`
- `shouldExposeHardCutoffWithoutStoppingSimulationWorld()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockControllerTest
```

## Step 8: Add An Accelerated Operating-Day Scenario

Create `DspOperationalSimulationClockScenarioTest`.

Build:

- a production-baseline clock for a fixed test date;
- a `SimulationWorld` containing `DspOperationalClockController`;
- an accelerated visual `FixedStepExecutionDriver` with a test-sized fixed step and deterministic scale;
- a step consumer that calls `SimulationWorld.update(fixedStepSeconds)` and records the largest observed step.

Prove:

1. elapsed zero is day 0 at `06:00` and normal operations;
2. accelerated execution reaches day 0 at `22:00` through repeated bounded world updates;
3. the exact `22:00` snapshot is overtime;
4. execution continues to day 1 at `00:00`;
5. the exact midnight snapshot reports hard cutoff reached with day offset 1;
6. no world update exceeds the configured fixed step;
7. requested and achieved scale are deterministic when the driver has no remaining backlog;
8. creating a new world, clock controller, and driver reproduces the initial `06:00` state, matching scene-reset reconstruction semantics;
9. no OSR inventory, lifecycle, scheduler order, station, tote, pack, bag, or renderable is mutated or created by clock progression.

Use a sufficiently large but bounded fixed step in this scenario so the test does not require millions of iterations. Machine/sensor tests remain responsible for validating smaller production-style fixed steps.

Required test methods:

- `shouldAdvanceOperatingDayThroughBoundedSimulationSteps()`
- `shouldRepresentNormalEndAndDayOneHardCutoff()`
- `shouldRestartDeterministicallyWhenRuntimeIsReconstructed()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.DspOperationalSimulationClockScenarioTest
```

## Step 9: Regression And Branch Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.time.* --tests online.davisfamily.threedee.sim.framework.time.* --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify existing motion speed and behavior remain unchanged because the new execution driver is not yet wired into `SoftwareRenderer`;
- verify `ALT+R` still resets each scene;
- no clock overlay or visual time-scale control is expected in this branch.

Before branch closure:

- [x] update this plan status to implementation complete and verified;
- [x] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [x] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [x] record implementation details that later supply, deadline, renderer-loop, or headless-runner plans must preserve.

## Preserved Contracts For Follow-On Work

- `SimulationContext.getSimulationTimeSeconds()` remains the authoritative absolute elapsed simulation time.
- `DspOperationalClock` is a stateless mapper. Do not introduce a separately accumulated mutable business clock that can drift from the simulation context.
- Simulation elapsed zero maps to the configured normal start. The production baseline is day 0 `06:00`, day 0 `22:00` normal end, and day 1 `00:00` hard cutoff.
- `OperationalDayTime` carries a non-negative day offset explicitly; later timetable and deadline work must preserve day +1 semantics rather than comparing wrapped `LocalTime` values.
- Exact normal end is `OVERTIME`; exact hard cutoff and all later times are `HARD_CUTOFF_REACHED`.
- Hard cutoff is observational in this foundation. A later command/application branch owns tote closure, terminal outcomes, or run termination.
- `DspOperationalClockController` derives its latest immutable snapshot from the context's absolute time and ignores `dtSeconds` for business-clock arithmetic.
- Register the clock controller before any controller that builds a scheduler snapshot containing clock state.
- Existing lifecycle timestamps remain simulation-relative `Duration` values. Business date/time is a derived view and does not replace those records.
- `FixedStepExecutionDriver` uses integer nanosecond accumulation, emits only the configured fixed step, retains unexecuted backlog, and enforces a per-advance work budget.
- Visual render decimation is represented by `FixedStepAdvance.renderDue()` but is not connected to `SoftwareRenderer` in this branch.
- `HEADLESS_ANALYSIS` emits the configured step batch without requesting rendering. The eventual headless runner supplies measured real duration for achieved-speed reporting; the driver does not read a system clock.
- Requested and achieved speed, cumulative real/simulation durations, completed steps, and backlog are exposed through immutable `FixedStepExecutionSnapshot` values.
- Runtime reset reconstructs the clock controller and fixed-step driver. Do not add a global reset registry for these types.
- Later rate-limited supply must consume immutable operational clock snapshots and OSR inventory APIs without coupling either domain to renderer frame timing.

## Completion Criteria

- Simulation elapsed zero maps deterministically to the configured operating date and normal start.
- Production defaults represent day 0 `06:00`, day 0 `22:00`, and day 1 `00:00` hard cutoff.
- Post-midnight time uses an explicit non-negative day offset.
- Clock snapshots distinguish normal operations, overtime, and hard cutoff reached.
- Business time derives from absolute `SimulationContext` elapsed time without an independently accumulated clock.
- Fixed-step execution never passes scaled large deltas to the simulation consumer.
- Acceleration preserves unexecuted backlog when a work budget is exhausted.
- Realtime, accelerated visual, and headless execution semantics are represented and tested.
- Requested scale, achieved scale, cumulative elapsed durations, completed steps, and backlog are inspectable through immutable snapshots.
- Tests use explicit duration inputs and contain no sleeps or wall-clock timing assertions.
- Reconstruction resets clock and driver state deterministically.
- Existing scheduler, OSR, lifecycle, machine, rendering, and reset behavior remains unchanged.
- No supply policy, trunker timetable, deadline outcome, scheduler command, renderer-loop integration, render-thread split, or full-day run is introduced.
- Focused tests, complete tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, create the detailed plan for:

```text
feature/dsp-rate-limited-service-centre-supply
```

That branch will use operational clock snapshots and OSR inventory capacity to authorize service centres at the configured low-water boundary and feed physical manifests into OSR at configured rates while preserving per-manifest identity and ADAPTED-first upstream ordering.
