# DSP Scheduler Thread Plan

Status: complete and green. Implemented on `feature/dsp-scheduler-thread` and merged to `master`.

## Summary

This branch moves DSP scheduler evaluation off the simulation thread while preserving the current synchronous path as an easy fallback.

The architectural boundary is:

```text
simulation thread builds immutable WarehouseSchedulerSnapshot
    -> scheduler evaluation source
    -> scheduler worker computes SchedulerEvaluation
    -> simulation thread polls result and applies release command
```

The scheduler worker must never receive live mutable simulation objects, renderables, controllers, queues, or machines. Only immutable scheduler snapshots cross to the worker. All command application and runtime state mutation remains on the simulation thread.

This branch should not add new scheduling rules, JSON loading, database storage, renderables, command-panel controls, full warehouse layout, or render-thread work.

## Key Decisions

- Keep the existing synchronous scheduler behavior available through an explicit synchronous evaluation source.
- Add a threaded evaluation source behind the same small interface.
- The integrated debug scene may use the threaded source, but it must be possible to switch back to synchronous construction with a small installer change.
- The threaded source should use one named platform thread through `Executors.newSingleThreadExecutor(...)`.
- Do not use virtual threads in this branch. The scheduler worker is a single long-lived evaluation worker, not a high-concurrency blocking workload.
- The simulation thread should submit snapshots and poll completed evaluations; it should not block waiting for the scheduler.
- At most one scheduler evaluation should be in flight for this first threaded slice.
- If a command cannot be applied because the release target is no longer ready, keep the existing deferred behavior and do not mark the order released.
- Scheduler debug inspection should continue to work, including blocked/release/deferred/rejected state.
- This work establishes the same snapshot/result/command boundary that can later inform a render-thread split, but render threading is out of scope.

## Step 1: Add Scheduler Evaluation Result Types

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/runtime/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/runtime/`

Create:

- `SchedulerEvaluationResult.java`
  - immutable record carrying:
    - `long sequence`
    - `WarehouseSchedulerSnapshot snapshot`
    - `SchedulerEvaluation evaluation`
  - validate non-null snapshot/evaluation
  - reject negative sequence

Expected output:

- A simulation-thread-pollable result object exists that can be passed to debug state recording and command application.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.SchedulerEvaluationResultTest
```

## Step 2: Add Evaluation Source Interface And Synchronous Implementation

Allowed files:

- Create/update files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/runtime/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/runtime/`

Create:

- `SchedulerEvaluationSource.java`
  - `boolean canSubmit()`
  - `void submit(WarehouseSchedulerSnapshot snapshot)`
  - `Optional<SchedulerEvaluationResult> pollResult()`
  - `void close()`

- `SynchronousSchedulerEvaluationSource.java`
  - dependencies: `DspReleaseScheduler`
  - on `submit(snapshot)`, evaluate immediately on the calling thread and store one result
  - `pollResult()` returns and clears the stored result
  - `canSubmit()` returns true when no unpolled result exists
  - `close()` is a no-op

Rules:

- This interface is the fallback boundary.
- Do not change `DspReleaseScheduler`.
- Do not introduce threads in this step.

Expected output:

- The existing scheduler behavior can be expressed through the new source interface without behavior changes.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.SynchronousSchedulerEvaluationSourceTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseSchedulerTest
```

## Step 3: Add Threaded Scheduler Evaluation Source

Allowed files:

- Create/update files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/runtime/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/runtime/`

Create:

- `ThreadedSchedulerEvaluationSource.java`
  - dependencies: `DspReleaseScheduler`, worker thread name
  - implements `SchedulerEvaluationSource`
  - uses `Executors.newSingleThreadExecutor(...)`
  - creates one named platform thread through a small local `ThreadFactory`
  - does not use `Thread.ofVirtual()` or `Executors.newVirtualThreadPerTaskExecutor()`
  - accepts a snapshot only when no evaluation is in flight and no result is waiting
  - assigns monotonically increasing sequence numbers
  - evaluates `DspReleaseScheduler.evaluate(snapshot)` on the worker thread
  - exposes completed results through `pollResult()`
  - `close()` shuts down the worker

Behavior:

- `submit(...)` should fail clearly if called while `canSubmit()` is false.
- `pollResult()` should be non-blocking.
- If scheduler evaluation throws, capture the failure in a deterministic way.
  - Prefer adding a failure field/factory to `SchedulerEvaluationResult` only if necessary.
  - The first implementation may rethrow from `pollResult()` as an unchecked exception if the test covers it clearly.
- Tests must prove evaluation does not run on the caller thread.
- Tests must close the source.

Rules:

- Use a single platform thread for this branch.
- Do not use virtual threads.
- Do not pass live simulation objects to the worker.
- Do not let the worker mutate `DspSchedulerRuntimeState`.
- Do not apply scheduler commands on the worker.

Expected output:

- A single-worker scheduler evaluator exists and can be polled safely by simulation-thread code.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.ThreadedSchedulerEvaluationSourceTest
```

## Step 4: Refactor Scheduled Debug Injector To Use Evaluation Source

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/ScheduledDebugToteInjectorController.java`
- Update `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/ScheduledDebugToteInjectorControllerTest.java`
- Create focused helper fakes in the same test package if needed

Implementation:

- Keep the existing constructor that accepts `DspReleaseScheduler`.
  - It should internally wrap the scheduler in `SynchronousSchedulerEvaluationSource`.
- Add a new constructor accepting `SchedulerEvaluationSource`.
- Update `update(...)` to:
  - submit `runtimeState.snapshot()` when the release target can accept and the source can submit
  - poll a completed `SchedulerEvaluationResult`
  - record debug state using the result snapshot and evaluation
  - apply release commands on the simulation thread exactly as today
  - mark runtime state released only after `releaseTarget.release(...)` returns applied
  - record deferred/rejected outcomes as today
- Keep the current no-op behavior when the release target cannot accept.
- Do not block waiting for the threaded result.

Rules:

- Existing synchronous tests should still pass.
- Add at least one test proving a release is not applied until a polled result exists.
- Add at least one test proving deferred release does not mark the order released.

Expected output:

- The debug injector can run against either synchronous or threaded scheduler evaluation without changing release application semantics.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorControllerTest --tests online.davisfamily.warehouse.sim.dsp.runtime.*
```

## Step 5: Wire Integrated Debug Installer To Threaded Evaluation

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
- Add/update focused tests only if an existing stable installer pattern exists

Implementation:

- Construct `ThreadedSchedulerEvaluationSource` around the existing `DspReleaseScheduler`.
- Pass that source to `ScheduledDebugToteInjectorController`.
- Keep the synchronous fallback easy:
  - leave the existing scheduler constructor in `ScheduledDebugToteInjectorController`
  - keep the installer change small enough that it can switch back to the scheduler constructor if needed
- Ensure the worker source is closed when the debug runtime is no longer needed if the current runtime has a close/dispose hook.
  - If no close/dispose hook exists, document this limitation in the plan and keep the worker daemon-style or otherwise safe for app shutdown.

Rules:

- Do not change scheduler decisions.
- Do not change queue admission.
- Do not change visual injection behavior.
- Do not add render threading.

Expected output:

- The integrated debug scene evaluates scheduler decisions on the scheduler worker while still applying release commands on the simulation thread.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.runtime.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.*
```

## Step 6: Extend Scheduler Debug Output For Thread State

Allowed files:

- Update files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Update tests under `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/`

Implementation:

- Add minimal inspection state so the overlay can distinguish:
  - synchronous/threaded mode if available
  - whether an evaluation is in flight
  - last completed evaluation sequence
- Keep this small and text-only through the existing `SchedulerDebugInspectable`.

Rules:

- Do not add Swing panels or command buttons.
- Do not expose thread internals beyond useful debug state.

Expected output:

- Visual checks can confirm that the threaded path is active without using breakpoints.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.SchedulerDebugInspectableTest --tests online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorControllerTest
```

## Step 7: Visual Check

Allowed files:

- No code changes unless visual verification exposes a concrete issue.

Visual expectations:

- The integrated tote-to-bag debug scene behaves as before.
- Scheduler overlay on `tipper_slide` shows thread/evaluation state.
- Queued totes are still released through the input wait queue.
- Queue capacity still gates scheduler release.
- `ToteToBagFlowController.canAdmit(...)` still gates local tipper processing.
- No renderables, queues, machines, or controllers are mutated by the scheduler worker.
- The app exits cleanly after closing the visual scene.

## Step 8: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.* --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.totebag.*
```

Then ask the user to run their trusted broader suite and visual pass.

Completion criteria:

- Scheduler evaluation can run on a separate worker thread.
- Synchronous fallback remains available.
- Scheduler worker only receives immutable snapshots.
- Scheduler commands are applied only on the simulation thread.
- Existing scheduler debug observability still works.
- No new scheduler rules, JSON behavior, render threading, or station behavior are introduced.

## Deferred Work

- A real lifecycle/dispose mechanism for debug scenes if the current runtime has no close hook.
- General production scheduler service configuration.
- Metrics for scheduler latency and skipped submissions.
- Render-thread split using immutable render snapshots.
- Command-panel controls for manual exception/override workflows.
