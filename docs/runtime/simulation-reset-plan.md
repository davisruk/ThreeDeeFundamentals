# Simulation Reset Plan

Branch: `feature/simulation-reset`

Status: planned. This is a short runtime usability branch between the completed adapting station Phase 1 work and the next Phase 1 station branch.

## Purpose

Add an `ALT+R` command that restores the active debug scene to its initial simulation state without restarting the application.

Reset must rebuild simulation-owned and scene-owned state safely on the game-loop thread. It must not mutate the active simulation directly from Swing's event-dispatch thread, and repeated resets must not leak scheduler worker threads or retain stale renderables/inspection registrations.

## User-Visible Contract

When the user releases `ALT+R`:

- the currently selected `DebugSceneKind` remains active
- the current simulation is discarded and reinstalled from its normal scene factory path
- simulation time returns to zero through construction of a new `SimulationWorld`
- machines, controllers, sensors, queues, totes, packs, bags, scheduler runtime state, and renderables return to their initial fixture state
- the current selected object is cleared because it belongs to the discarded scene
- camera position and camera orientation are preserved
- display/input modes such as grid, axes, debug text, wireframe, fill, and pause settings are preserved
- the reset is applied once per key command, even if the next rendered frame is delayed
- repeated resets remain safe

## Explicit Non-Goals

- rewind, fast-forward, replay, checkpoints, or event sourcing
- separating simulation and rendering threads
- fixed-timestep simulation work
- changing scheduler rules or station behavior
- resetting to an arbitrary intermediate state
- adding an in-application reset button
- changing scene selection at runtime

Rewind/forward would require deterministic simulation stepping plus snapshots or event replay. This branch should establish clean construction/disposal only; do not add history infrastructure.

## Current Constraints

- Swing key actions execute on the event-dispatch thread.
- `SoftwareRenderer` runs simulation and rendering together on its game-loop thread.
- `BaseScene` currently owns one final `SimulationWorld`.
- `TestScene` installs one final `DebugSceneRuntime` in its constructor.
- `SelectionInspectionRegistry` has no clear operation.
- `DebugSceneRuntime` has no close/dispose lifecycle.
- The integrated tote-to-bag debug scene creates a `ThreadedSchedulerEvaluationSource`; replacing the scene without closing that source would retain a daemon worker until application shutdown.

## Required Design

Use a one-shot request consumed at a safe point:

```text
Swing EDT: ALT+R action
    -> InputState.requestSimulationReset()

game-loop thread: before active runtime sync/update/draw
    -> InputState.consumeSimulationResetRequest()
    -> close old runtime
    -> clear old selection/inspection/renderables
    -> create new SimulationWorld
    -> reinstall the same DebugSceneKind
```

Do not call reset logic directly from `CommandBindings`.

Use an `AtomicBoolean` or equivalent atomic one-shot flag for the reset request. Do not rely on the existing mutable `BitSet`/`EnumSet` for this cross-thread command.

## Scope

Expected production files:

- `online.davisfamily.threedee.input.keyboard.InputState`
- `online.davisfamily.threedee.input.keyboard.CommandBindings`
- `online.davisfamily.threedee.debug.SelectionInspectionRegistry`
- `online.davisfamily.threedee.scene.BaseScene`
- `online.davisfamily.warehouse.testing.DebugSceneRuntime`
- `online.davisfamily.warehouse.testing.TestScene`
- `online.davisfamily.warehouse.testing.IntegratedToteToBagDebugInstallation`
- `online.davisfamily.warehouse.testing.IntegratedToteToBagDebugInstaller`
- `online.davisfamily.warehouse.testing.ToteToBagDebugRig`
- `online.davisfamily.warehouse.testing.AdaptingDebugRig`
- `online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorController`

Expected tests:

- keyboard/input command tests
- scheduler evaluation-source lifecycle test
- debug-scene reset/lifecycle test

Do not change machine state implementations, scheduler evaluation behavior, routing, transfer logic, or render geometry.

## Step 1: Add A One-Shot Reset Command Request

Add reset request methods to `InputState`:

```java
public void requestSimulationReset()
public boolean consumeSimulationResetRequest()
```

Implementation rules:

- back the request with `AtomicBoolean`
- `requestSimulationReset()` sets the flag
- `consumeSimulationResetRequest()` returns the current value and clears it atomically
- consuming with no pending request returns `false`
- multiple requests before consumption may coalesce into one reset

In `CommandBindings.installCommandBindings(...)`, bind `ALT+R` on key release to `InputState.requestSimulationReset()`.

Do not add reset as an `InputState.Mode`; reset is a command, not persistent/toggle state.

Expected tests:

- a new input state has no reset request
- requesting reset causes exactly one successful consumption
- a second consumption returns false
- invoking the registered `ALT+R` action requests reset

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.threedee.input.keyboard.*
```

## Step 2: Add Debug Runtime Lifecycle

Change `DebugSceneRuntime` to extend `AutoCloseable` and provide:

```java
@Override
default void close() {
}
```

No checked exception should be exposed by this override.

Make `ScheduledDebugToteInjectorController` implement `AutoCloseable`. Its `close()` method must call `evaluationSource.close()` and be safe when called once during scene replacement.

Make ownership explicit for the threaded tote-to-bag source:

- `IntegratedToteToBagDebugInstallation` must implement `AutoCloseable`
- its constructor must receive and retain the `ScheduledDebugToteInjectorController` created by `IntegratedToteToBagDebugInstaller`
- its `close()` method must call `scheduledInjectorController.close()`
- `ToteToBagDebugRig` must retain the returned `IntegratedToteToBagDebugInstallation`
- `ToteToBagDebugRig.close()` must call `installation.close()`
- `AdaptingDebugRig` must retain its injector controller in a field and its `close()` method must call `injectorController.close()`, even though its current synchronous source has a no-op close
- other debug runtimes may inherit the default no-op close

Do not shut down scheduler workers from the Swing event-dispatch thread; runtime close is called only while applying reset on the game-loop thread.

Expected tests:

- closing `ScheduledDebugToteInjectorController` closes its evaluation source
- closing a no-op `DebugSceneRuntime` is safe
- lifecycle close does not change scheduler evaluation/application behavior

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorControllerTest --tests online.davisfamily.warehouse.sim.dsp.runtime.*
```

## Step 3: Make TestScene Reinstallable

Retain the selected `DebugSceneKind` in `TestScene` and make `activeRuntime` replaceable.

Allow `BaseScene` to replace its `SimulationWorld` at reset. The narrow expected change is to remove `final` from the protected `sim` field; do not add a general mutable-world API unless tests require a read-only/package-private accessor.

Add `SelectionInspectionRegistry.clear()` to remove registrations for discarded renderables.

Add a game-loop-only method in `TestScene`, named `resetActiveScene()` or equivalent, with this exact order:

1. close `activeRuntime`
2. clear `selectionManager`
3. clear `inspectionRegistry`
4. clear the scene `objects` list
5. clear fixture-only references such as `rTote`
6. assign a new `SimulationWorld` to `sim`
7. reinstall the retained `DebugSceneKind` through the existing `installScene(...)` method

Do not recreate `TestScene`, `InputState`, `Camera`, `TriangleRenderer`, frame buffers, or Swing bindings. This preserves camera/view state and avoids replacing renderer-owned resources.

Do not add a reset/clear method to `SimulationWorld`. A new world is the reset boundary; clearing a live world risks retaining listeners, queues, or context state accidentally.

Expected tests:

- reset replaces the `SimulationWorld` instance
- reset reinstalls the same scene kind
- renderables from the old scene are not retained or duplicated
- selection and inspection state from the old scene are cleared
- repeated reset is safe

Use a lightweight scene such as `STRAIGHT_CONVEYOR` for the focused scene-reset test. Add only narrow package-private accessors needed by that test; do not expose mutable scene collections publicly.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.TestSceneResetTest --tests online.davisfamily.threedee.debug.SelectionInspectionRegistryTest
```

## Step 4: Consume Reset At The Frame Safe Point

Add this protected no-op hook to `BaseScene`:

```java
protected void processFrameCommands() {
}
```

`BaseScene.renderFrame(...)` must call `processFrameCommands()` before checking `PAUSE_ALL`.

Override the hook in `TestScene`:

```java
@Override
protected void processFrameCommands() {
    if (inputState.consumeSimulationResetRequest()) {
        resetActiveScene();
    }
}
```

This hook is the only place that may consume the simulation-reset request. Do not also consume it in `executeChildRenderOperations(...)`, `drawObject(...)`, `CommandBindings`, or `SoftwareRenderer`.

If `PAUSE_ALL` is not set, the newly installed scene may run and draw in the same frame. If `PAUSE_ALL` remains set, reset must still complete immediately, but the new scene must remain paused until the user unpauses it. Do not perform an update or draw against the closed runtime or old object list.

Do not put warehouse-specific reset logic in `BaseScene`; it owns only the generic safe-point hook.

Expected tests:

- reset is consumed while transforms are paused
- reset is consumed while all rendering/simulation is paused
- the reset request is not reapplied on later frames

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.TestSceneResetTest --tests online.davisfamily.threedee.scene.*
```

## Step 5: Regression And Visual Verification

Run the focused runtime/scheduler tests first:

```powershell
.\gradlew test --tests online.davisfamily.threedee.input.keyboard.* --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.runtime.* --tests online.davisfamily.warehouse.testing.TestSceneResetTest
```

Then ask the user to run their trusted broader suite.

Visual checks:

1. Start the default `TOTE_TO_BAG` scene, allow it to progress, then press `ALT+R`.
2. Confirm the initial totes, packs, bags, queues, machine states, and scheduler state return without restarting the window.
3. Press `ALT+R` several times and confirm no duplicated renderables or progressively duplicated behavior appears.
4. Repeat with `--scene=adapting` and confirm STORE/COLLECT flow restarts from its initial state.
5. Move the camera before reset and confirm the camera remains where the user placed it.
6. Enable debug/grid display modes before reset and confirm those modes remain enabled.
7. Pause the scene, press `ALT+R`, then unpause and confirm the new simulation starts from its initial state.

## Completion Criteria

- `ALT+R` requests reset from Swing without mutating simulation state on the EDT.
- Reset is applied once at a game-loop safe point.
- The active scene kind, camera, and display/input modes are preserved.
- A fresh `SimulationWorld` and fresh scene runtime are installed.
- Old selection, inspection registrations, renderables, controllers, sensors, listeners, queues, and scheduler state are not retained.
- Threaded scheduler evaluation is closed before its owning runtime is discarded.
- Repeated reset is safe in the default and adapting scenes.
- Rewind/forward and render-thread separation remain deferred.
