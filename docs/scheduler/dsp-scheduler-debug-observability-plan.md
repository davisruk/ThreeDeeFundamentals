# DSP Scheduler Debug Observability Plan

Status: complete and green. Implemented on `feature/dsp-scheduler-debug-observability` and merged to `master`.

## Summary

Detailed implementation plan for `feature/dsp-scheduler-debug-observability`.

This branch makes scheduler-driven debug scenes observable through the existing selection inspection overlay. It should show enough scheduler state to verify visually that queued tote release is scheduler-selected and live P2P admission-aware, without using breakpoints.

Do not add command buttons, Swing side panels, scheduler threading, JSON loading, or new scheduling rules in this branch.

## Key Decisions

- Use the existing `SelectionInspectionRegistry` / `Inspectable` overlay first.
- Keep scheduler observability in debug/testing integration packages.
- Do not make the scheduler evaluate twice just to display diagnostics.
- Capture the last scheduler evaluation and last release application result during `ScheduledDebugToteInjectorController.update(...)`.
- Register the scheduler inspection against an existing integrated debug renderable so it can be selected in the scene.
- The practical visual target is currently `tipper_slide`; the tipper assembly root is not reliably selectable because it is an anchor renderable and its selectable children are not all routed to the root.
- Keep the current framebuffer inspection overlay; do not start the broader command-panel work from `docs/selectable-object-command-panel.md`.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/dsp-scheduler-debug-observability
```

## Step 1: Scheduler Debug State Capture

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Update `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/ScheduledDebugToteInjectorController.java`
- Update `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/ScheduledDebugToteInjectorControllerTest.java`

Create exactly:

- `SchedulerDebugState.java`
  - mutable debug/testing holder
  - method: `public SchedulerDebugSnapshot snapshot()`
  - method: `public void recordEvaluation(WarehouseSchedulerSnapshot schedulerSnapshot, SchedulerEvaluation evaluation)`
  - method: `public void recordApplied(String orderId)`
  - method: `public void recordDeferred(String orderId, String reason)`
  - method: `public void recordRejected(String orderId, String reason)`
- `SchedulerDebugSnapshot.java`
  - immutable record containing:
    - `Optional<String> activeServiceCentreId`
    - `List<String> waitingOrderIds`
    - `Optional<String> releaseOrderId`
    - `Optional<String> blockedServiceCentreId`
    - `List<String> blockedCandidateOrderIds`
    - `List<String> blockedReasons`
    - `Optional<String> lastAppliedOrderId`
    - `Optional<String> lastDeferredOrderId`
    - `Optional<String> lastDeferredReason`
    - `Optional<String> lastRejectedOrderId`
    - `Optional<String> lastRejectedReason`

Update `ScheduledDebugToteInjectorController`:

- Keep the existing constructor unchanged.
- Add an overload:
  - `public ScheduledDebugToteInjectorController(DspReleaseScheduler scheduler, DspSchedulerRuntimeState runtimeState, ScheduledTipperToteReleaseCatalog releaseCatalog, ScheduledToteReleaseTarget releaseTarget, SchedulerDebugState debugState)`
- The existing constructor delegates to the overload with `new SchedulerDebugState()`.
- During `update(...)`, store `WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot()` before evaluating.
- After scheduler evaluation, call `debugState.recordEvaluation(snapshot, evaluation)`.
- When release application succeeds, call `debugState.recordApplied(command.orderId())`.
- When release is deferred, call `debugState.recordDeferred(command.orderId(), result.reason())`.
- Before throwing for a rejected release, call `debugState.recordRejected(command.orderId(), result.reason())`.
- Add accessor:
  - `public SchedulerDebugSnapshot debugSnapshot()`

Rules:

- Do not change release behavior.
- Do not mutate `DspSchedulerRuntimeState` just for debug output.
- Do not call `scheduler.evaluate(...)` more than once per update.

Test methods:

- Add `shouldExposeLastSchedulerReleaseDecisionForDebugging()`
- Add `shouldExposeBlockedDecisionForDebugging()`
- Add `shouldExposeDeferredReleaseResultForDebugging()`
- Add `shouldExposeRejectedReleaseResultForDebugging()`

Expected output:

- The debug injector exposes the last scheduler decision and application result without changing scheduler behavior.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorControllerTest
```

## Step 2: Scheduler Inspection Text Formatter

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/SchedulerDebugInspectableTest.java`

Create exactly:

- `SchedulerDebugInspectable.java`
  - implements `online.davisfamily.threedee.debug.Inspectable`
  - constructor: `public SchedulerDebugInspectable(ScheduledDebugToteInjectorController controller)`
  - `describe()` returns a compact list of strings suitable for the existing overlay

Recommended lines:

- `Scheduler: debug`
- `Active SC: <none|id>`
- `Waiting: <comma-separated order ids or none>`
- `Release: <none|order id>`
- `Blocked SC: <none|id>`
- `Blocked candidates: <none|ids>`
- `Block: <reason>` for up to the first 4 blocked reasons
- `Last applied: <none|order id>`
- `Last deferred: <none|order id - reason>`
- `Last rejected: <none|order id - reason>`

Rules:

- Keep line count small enough for the current overlay.
- Do not expose raw Java object `toString()` output.
- Do not evaluate the scheduler inside `describe()`.
- Use the controller's `debugSnapshot()`.

Test methods:

- `shouldDescribeReleaseDecision()`
- `shouldDescribeBlockedDecisionAndReasons()`
- `shouldDescribeDeferredAndRejectedResults()`

Expected output:

- Scheduler state can be rendered by the existing debug overlay inspection system.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.SchedulerDebugInspectableTest
```

## Step 3: Register Scheduler Inspection In Integrated Debug Installer

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
- Add a focused test only if a suitable installer-level pattern already exists; otherwise rely on Step 4 visual verification plus Step 1/2 tests.

Implementation:

- Construct the `ScheduledDebugToteInjectorController` as a local variable before registering it with `sim.addController(...)`.
- Register a `SchedulerDebugInspectable` with `inspectionRegistry`.
- Use an existing stable renderable selection target in the integrated scene.
- Preferred selection target: `tipperInstallation.getTipperModule().getAssemblyRenderable()` if available.
- If that getter does not exist, use another existing selectable integrated debug object that is always present and easy to click, such as the tipper module or PDC/PCR renderable.
- Do not add a new renderable purely for scheduler debug in this branch unless no existing stable target is available.

Expected output:

- Selecting the chosen integrated debug object shows scheduler lines in the existing overlay.
- The overlay indicates whether the scheduler last released, blocked, deferred, or rejected a queued tote.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 4: Visual Check

Allowed files:

- No code changes unless the visual check exposes a concrete issue.

Visual expectations:

- Integrated tote-to-bag scene behaves as before.
- Selecting the registered debug object shows scheduler inspection lines.
- The scheduler inspection shows the active service centre.
- The scheduler inspection shows the last release order when a queued tote is released.
- If a queued tote is blocked by live P2P admission, the blocked reason is visible.
- Debug output fits in the existing overlay without obscuring the main scene more than current inspection text already does.

Ask user to run the existing integrated visual scene using their normal run configuration.

Expected output:

- The scheduler path can be verified visually without breakpoints.

## Step 5: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.* --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

Then ask user to run their trusted broader suite/visual pass.

Completion criteria:

- Focused scheduler debug observability tests pass.
- Existing scheduler and tote-to-bag focused tests pass.
- Integrated visual check shows scheduler state in the overlay.
- No scheduler behavior changes are introduced.
- No scheduler thread is introduced.
- No command-panel work is introduced.
