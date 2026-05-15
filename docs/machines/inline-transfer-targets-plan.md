# Inline Transfer Targets Plan

Branch: `feature/inline-transfer-targets`

Status: partially superseded. Steps 1-7 describe the target-selection work already introduced on this branch. The visual follow-up has been superseded by `docs/machines/transfer-machine-requirements.md` and `docs/machines/transfer-machine-standalone-plan.md`.

## Purpose

The adapting area needs transfer machines that can move a tote from one source window to one of multiple possible target routes. The current transfer model supports only:

- `CONTINUE`: stay on the current route segment
- `BRANCH`: transfer to the single target segment owned by the `TransferZone`

That is enough for simple adjacent-track transfers, but not enough for an inline transfer where one physical transfer machine can route a tote left or right onto different tracks with different travel directions.

This branch extends the transfer model so a transfer decision can name the exact target segment, entry distance, and target travel direction.

## Scope

Allowed production areas:

- `app/src/main/java/online/davisfamily/warehouse/sim/transfer`
- `app/src/main/java/online/davisfamily/warehouse/sim/tote/Tote.java`
- `app/src/main/java/online/davisfamily/warehouse/rendering/model/tracks`
- narrowly needed debug fixture code in `app/src/main/java/online/davisfamily/warehouse/testing`

Allowed tests:

- `app/src/test/java/online/davisfamily/warehouse/sim/transfer`
- existing transfer/tote tests if they need small compatibility updates

Do not change:

- DSP scheduler domain rules
- adapting station state
- P2P/tote-to-bag controller logic
- machine wait queues

## Design Rules

- Keep existing `BRANCH` / `CONTINUE` behaviour source-compatible where practical.
- Do not model one physical inline transfer as overlapping `TransferZone`s. `WarehouseSegmentMetadata` currently rejects overlapping zones, and overlapping physical machines would be the wrong abstraction.
- Add an explicit transfer target/result concept instead of adding special-case booleans.
- The target travel direction must be explicit for routed transfers. Do not assume it is the same as the source tote's current `RouteFollower` direction.
- Route selection belongs in a strategy/controller decision, not inside `Tote`.
- `Tote` should only execute the selected transfer target.

## Step 1: Add Target Value Object

Create a small immutable target model in `online.davisfamily.warehouse.sim.transfer`.

Suggested class:

```java
public record TransferTarget(
        RouteSegment segment,
        float entryDistance,
        TravelDirection travelDirection) {
}
```

Validation:

- `segment` must not be null.
- `entryDistance` must be within the segment length.
- `travelDirection` must not be null.

Add focused tests for invalid values and a valid target.

Expected output:

- New target object compiles.
- Existing tests remain source-compatible because no existing API is removed.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.TransferTargetTest
```

## Step 2: Add Transfer Decision Result

Add a decision result that can express either continue or transfer-to-target.

Suggested class:

```java
public final class TransferRoutingDecision {
    public static TransferRoutingDecision continueOnCurrentRoute();
    public static TransferRoutingDecision transferTo(TransferTarget target);
}
```

Keep compatibility helpers so existing `TransferDecisionStrategy` implementations can still return `BRANCH` / `CONTINUE` until they are migrated.

Expected output:

- Existing `AlwaysTransferStrategy` and `ToggleStrategy` still work.
- New strategies can return a selected target without relying on `TransferZone.getTargetSegment()`.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.TransferRoutingDecisionTest
```

## Step 3: Teach Tote Transfer About Target Direction

Update `Tote.beginTransfer(...)` so the transfer target travel direction is supplied explicitly.

Expected behaviour:

- Existing callers can pass the current travel direction and preserve current behaviour.
- New callers can land a tote on the target segment travelling `FORWARD` or `REVERSE`.
- Facing/yaw preservation still works across the transfer and link-segment logic.

Keep this as a narrow method signature change. Do not refactor unrelated tote motion.

Expected output:

- Existing transfer behaviour remains green after caller updates.
- A new test proves a tote can transfer to a target segment with explicit reverse direction.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.tote.ToteTest
```

## Step 4: Wire TransferZoneController To Selected Targets

Update `TransferZoneController` so it can begin transfer using the selected `TransferTarget`.

Rules:

- `CONTINUE` clears the reservation as today.
- transfer-to-target reserves the tote, waits for the window sensor, waits for mechanisms, then calls `Tote.beginTransfer(...)` with the selected target.
- Existing single-target `TransferZone` behaviour should be implemented as a compatibility path that creates a `TransferTarget` from:
  - `TransferZone.getTargetSegment()`
  - `TransferZone.getTargetStartDistance()`
  - current tote travel direction

Expected output:

- Current direct transfer fixtures still behave as before.
- Controller tests can assert that the selected target, not merely the zone default, is used.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.TransferZoneControllerTest
```

## Step 5: Add Inline Transfer Strategy Support

Add a strategy or strategy adapter that can choose between multiple named targets for one physical transfer window.

Suggested shape:

```java
public interface TransferTargetDecisionStrategy {
    Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine);
}
```

This may coexist with the old `TransferDecisionStrategy` through an adapter. Keep the old interface only if it avoids broad fixture churn.

Expected output:

- One physical transfer machine can choose target A, target B, or continue.
- The strategy does not mutate route or tote state directly.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.strategy.*
```

## Step 6: Add Builder Support For Multi-Target Inline Transfer

Extend `WarehouseRouteBuilder` with a method for one transfer zone and multiple possible targets.

Suggested method:

```java
addInlineTransfer(
    String transferId,
    RouteSegment sourceSegment,
    float sourceTransferCentreDistance,
    float openingLength,
    GuideSide sourceOpenSide,
    List<TransferTarget> targets,
    TransferTargetDecisionStrategy strategy,
    TransferMotionConfig motionConfig)
```

Keep rendering simple:

- one source guide opening
- target guide openings for each target
- one selectable transfer mechanism renderable

Expected output:

- Existing `addDirectTransfer(...)` and `addTransferToLink(...)` continue working.
- New builder path can model one source window with two outbound directions.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.rendering.model.tracks.*
```

## Step 7: Add A Debug/Fixture Scenario

Add a focused fixture or test route with:

- source segment entering the transfer window
- left outbound target
- right outbound target
- explicit target travel directions

The test should assert route follower segment and travel direction after transfer completion. Avoid relying on visual timing alone.

Expected output:

- The inline transfer can route to either target.
- The tote lands with the expected travel direction.
- Existing transfer visual rigs still work.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.*
```

## Superseded Follow-Up Slice: Add Inline Transfer Visual Rig

Status: superseded by `transfer-machine-standalone-plan.md`.

The initial visual rig exposed a deeper modelling issue: inline transfer machines should not be represented as transfer intervals overlaid onto a longer source track, and transfer targets should not receive automatic guide openings. Continue with `docs/machines/transfer-machine-standalone-plan.md` before closing this branch.

Purpose:

- Provide a visual scene that exercises one physical inline transfer window choosing between multiple target tracks.
- Make the new behaviour inspectable without relying only on unit tests.
- Keep this as a debug/fixture addition only; do not change scheduler, adapting station, or P2P logic.

Allowed production/debug files:

- `app/src/main/java/online/davisfamily/warehouse/testing/DebugSceneKind.java`
- `app/src/main/java/online/davisfamily/warehouse/testing/TestScene.java`
- `app/src/main/java/online/davisfamily/warehouse/testing/WarehouseTrackFactory.java`
- narrowly needed transfer strategy helper files if the scene needs a simple alternating target strategy

Allowed tests:

- existing transfer tests if the new strategy helper needs coverage
- no broad visual automation required for this slice

Implementation steps:

1. Add a new debug scene kind, suggested CLI value: `inline-transfer`.
2. Add `WarehouseTrackFactory.setupInlineTransferTargets(...)`.
3. Build one source track, one inline transfer zone, and two outbound target tracks.
4. Use `WarehouseRouteBuilder.addInlineTransfer(...)` with two `TransferTarget`s using different `TravelDirection`s.
5. Use a simple deterministic target-selection strategy, such as alternating left/right for each arriving tote or always choosing one target first while keeping the second target selectable by a small strategy change.
6. Register the steering transfer inspection overlay so clicking the transfer shows source, default target, mechanism state, and readiness.
7. Wire the scene into `TestScene.installScene(...)`.

Expected output:

- Running the debug scene shows a tote entering one transfer window and moving to the selected outbound target.
- The scene proves the route uses one physical transfer zone rather than two overlapping transfer zones.
- The inspection overlay still works for the transfer mechanism.
- Existing `oval-track` and `parallel-track` debug scenes continue to work.

Ask the user to run the focused compile/test command first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.* --tests online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilderTest
```

Then ask the user to run the visual scene command used locally for this project, with:

```powershell
--scene=inline-transfer
```

## Completion Criteria

- Existing transfer examples still work.
- Inline transfer target selection is represented as one physical transfer machine with multiple possible targets.
- Target travel direction is explicit and tested.
- No adapting station logic has been introduced in this branch.
- If the visual follow-up slice is implemented, `--scene=inline-transfer` demonstrates the multi-target inline transfer path.
