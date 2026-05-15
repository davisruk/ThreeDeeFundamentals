# Transfer Machine Standalone Plan

Branch: `feature/inline-transfer-targets`

Status: planned. This replaces the visual-only follow-up in `inline-transfer-targets-plan.md`.

Related requirements: `docs/machines/transfer-machine-requirements.md`

## Purpose

Replace the current overlay-style transfer-zone modelling with an explicit transfer-controlled route segment/window while keeping transfer behaviour separate from transfer renderables.

This must be done before `feature/adapting-station-phase-1`, because the adapting area needs inline transfer junctions that can route totes cleanly without source-track overlays or automatic target guide openings.

## Scope

Allowed production areas:

- `app/src/main/java/online/davisfamily/warehouse/sim/transfer`
- `app/src/main/java/online/davisfamily/warehouse/sim/tote/Tote.java`
- `app/src/main/java/online/davisfamily/warehouse/rendering/model/tracks`
- narrowly needed route/path support in `app/src/main/java/online/davisfamily/threedee/behaviour/routing`
- debug fixture code in `app/src/main/java/online/davisfamily/warehouse/testing`

Allowed tests:

- `app/src/test/java/online/davisfamily/warehouse/sim/transfer`
- `app/src/test/java/online/davisfamily/warehouse/sim/tote`
- `app/src/test/java/online/davisfamily/warehouse/rendering/model/tracks`
- existing transfer visual/fixture tests if present

Do not change:

- DSP scheduler domain or scheduler thread logic
- adapting station implementation
- P2P/tote-to-bag controller logic
- machine wait queue logic

## Design Rules

- Keep transfer control separate from transfer renderables.
- The transfer-controlled area should be explicit route/path geometry, not an interval hidden inside a longer source segment.
- Target guide openings must not be added automatically for every target.
- Guide openings and guide suppression are explicit layout metadata.
- Keep existing single-target transfer behaviour source-compatible where practical.
- Do not implement a general route graph router.
- Do not implement multi-source transfer routing in this slice, but avoid naming/API choices that would prevent it later.
- Tote orientation after transfer must be controlled by an explicit policy.

## Step 1: Add Failing Test For Current Builder Assumption

Update the existing builder test that currently asserts inline transfer target guide openings.

Files:

- `app/src/test/java/online/davisfamily/warehouse/rendering/model/tracks/WarehouseRouteBuilderTest.java`

Required changes:

- Add or adjust a failing test that proves an inline transfer target does not automatically receive a guide opening.
- The test should still prove the source side can receive an explicit source opening.

Expected output:

- The new/updated test fails against current code because `addInlineTransfer(...)` still creates target openings.
- No production behaviour is changed yet.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilderTest
```

## Step 2: Stop Automatic Target Guide Openings

Update `WarehouseRouteBuilder.addInlineTransfer(...)`.

Files:

- `app/src/main/java/online/davisfamily/warehouse/rendering/model/tracks/WarehouseRouteBuilder.java`
- `app/src/test/java/online/davisfamily/warehouse/rendering/model/tracks/WarehouseRouteBuilderTest.java`

Required changes:

- Remove the loop that adds `CONNECTION_TARGET` guide openings for every `TransferTarget`.
- Keep the explicit source opening for the source side.
- Adjust test names and assertions so they describe source opening only.
- Do not add new target-opening APIs in this step.

Expected output:

- Inline transfer targets retain normal guides.
- Builder tests pass.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilderTest
```

## Step 3: Add Transfer Orientation Policy

Add an explicit orientation policy used by tote transfer motion.

Suggested enum:

```java
public enum TransferOrientationPolicy {
    PRESERVE_TOTE_ORIENTATION,
    ALIGN_TO_TARGET_TRAVEL
}
```

Files:

- new file under `app/src/main/java/online/davisfamily/warehouse/sim/transfer`
- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferTarget.java` or `TransferRoutingDecision.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/tote/Tote.java`
- focused tote/transfer tests

Rules:

- Preserve current behaviour by default.
- Existing call sites should remain source-compatible through overloads or default policy.
- `ALIGN_TO_TARGET_TRAVEL` should align tote yaw to the selected target direction and travel direction.
- Do not add `ALIGN_TO_MECHANISM` yet unless the implementation is trivial and tested.

Expected output:

- Existing transfer tests remain green.
- New tests prove preserved yaw and target-aligned yaw are distinct.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.tote.ToteTest --tests online.davisfamily.warehouse.sim.transfer.*
```

## Step 4: Introduce Explicit Transfer Window Segment Builder Support

Add a builder path that creates or accepts a short route segment representing the transfer-controlled window.

Suggested API shape:

```java
addStandaloneTransfer(
    String transferId,
    RouteSegment transferSegment,
    List<TransferTarget> targets,
    TransferTargetDecisionStrategy strategy,
    TransferMotionConfig motionConfig)
```

Exact naming may vary, but the API must make these concepts clear:

- the transfer machine is controlled from `transferSegment`
- the transfer segment is not just an interval over a longer source segment
- targets are possible outbound landing segments
- render metadata is separate

Files:

- `WarehouseRouteBuilder.java`
- `WarehouseSegmentMetadata.java` if needed
- tests under `rendering/model/tracks`

Rules:

- Do not remove old `addDirectTransfer(...)` or `addTransferToLink(...)`.
- Do not migrate visual rigs in this step.
- The transfer segment may be rendered with a normal `TrackSpec`, a guide-less `TrackSpec`, or not rendered by track factory, depending on existing factory constraints. Keep this step focused on topology and metadata.

Expected output:

- A route can be built as `source -> transferSegment -> continueTarget`.
- The transfer machine metadata belongs to the transfer segment.
- No automatic target guide openings are created.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.rendering.model.tracks.*
```

## Step 5: Move TransferZoneMachine Sensors To The Transfer Segment

Update machine creation/controller usage so standalone transfer machines detect totes on the transfer segment.

Files:

- `TransferZoneMachine.java`
- `TransferZoneController.java`
- `TransferZone.java`
- transfer controller tests

Rules:

- For standalone transfers, the approach/window sensors should be on the transfer segment.
- A `CONTINUE` decision should clear reservation and let normal route following continue to the next connected segment.
- A transfer decision should reserve, wait for mechanism readiness, and call `Tote.beginTransfer(...)` with the selected target and orientation policy.
- Keep legacy interval-based construction working until existing rigs are migrated.

Expected output:

- Existing transfer controller tests pass.
- New standalone transfer controller test proves a tote transfers from the transfer segment to a selected target.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.TransferZoneControllerTest --tests online.davisfamily.warehouse.sim.transfer.*
```

## Step 6: Update Inline Transfer Visual Rig To Use Standalone Segment

Migrate the `inline-transfer` debug scene.

Files:

- `app/src/main/java/online/davisfamily/warehouse/testing/WarehouseTrackFactory.java`
- `app/src/main/java/online/davisfamily/warehouse/testing/DebugSceneKind.java`
- `app/src/main/java/online/davisfamily/warehouse/testing/TestScene.java`

Required layout:

```text
source track -> transfer window segment -> optional/default continue track
                          |
                          +-> left target
                          +-> right target
```

Rules:

- The source track must stop at the transfer segment.
- Target tracks must start from the transfer machine edges/exit points.
- Target tracks must keep full guides unless explicitly suppressed.
- The transfer renderable remains separate from transfer behaviour.
- The current two-conveyor renderable may continue to be used.

Expected output:

- `--scene=inline-transfer` shows the transfer machine at the junction.
- The source track no longer visually runs through the transfer machine.
- Target tracks no longer have accidental guide openings.
- Totes route alternately to the target tracks.

Ask the user to run focused tests first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.* --tests online.davisfamily.warehouse.rendering.model.tracks.*
```

Then ask the user to run the visual scene with:

```powershell
--scene=inline-transfer
```

## Step 7: Migrate Existing Transfer Visual Rigs

Revisit existing transfer scenes that use interval/overlay transfer zones.

Files:

- `WarehouseTrackFactory.java`
- any existing tests/fixtures tied to `oval-track` or `parallel-track`

Rules:

- Do not change unrelated scene content.
- Keep old transfer behaviour visually equivalent.
- Use standalone transfer segment modelling where it fits.
- If a parallel transfer needs a receiving window, model it as an ordinary short track segment with explicit guide metadata only.

Expected output:

- Existing transfer scenes still run.
- The transfer machine remains selectable/inspectable.
- No source track renders through a transfer machine unless the scene deliberately models that.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.* --tests online.davisfamily.warehouse.rendering.model.tracks.*
```

Then ask for visual checks of:

```powershell
--scene=oval-track
--scene=parallel-track
--scene=inline-transfer
```

## Step 8: Clean Up Legacy Overlay Assumptions

After all fixtures are migrated, remove or quarantine obsolete assumptions.

Files:

- `TransferZone.java`
- `WarehouseRouteBuilder.java`
- `RouteTrackLayoutFactory.java`
- tests that mention transfer intervals or automatic target guide openings

Rules:

- Do not remove compatibility methods if other code still uses them.
- If compatibility methods remain, document that they are legacy helpers.
- Remove tests whose only purpose was asserting target guide openings.
- Make `suppressGuidesInTransferZones` either effective or replace it with explicit guide metadata. Prefer the smallest safe change.

Expected output:

- Transfer docs and tests describe standalone transfer windows as the preferred model.
- Legacy overlay transfer remains only where intentionally supported.
- No code path adds target guide openings merely because a segment is a transfer target.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.transfer.* --tests online.davisfamily.warehouse.rendering.model.tracks.* --tests online.davisfamily.warehouse.sim.tote.ToteTest
```

## Step 9: Completion Check

Before closing the branch:

- run the focused transfer test suite
- visually inspect all transfer rigs
- confirm adapting station plan can use the standalone transfer segment model
- update `docs/machines/adapting-station-phase-1-plan.md` if it references old overlay transfer behaviour

Ask the user to run whichever wider test command they prefer for branch closure.

## Completion Criteria

- Inline transfer target selection uses an explicit transfer-controlled segment/window.
- Transfer renderables remain separate from transfer behaviour.
- Target route segments do not receive automatic guide openings.
- Tote orientation is controlled by an explicit transfer orientation policy.
- Existing single-target transfer behaviour still works.
- Existing transfer visual rigs are migrated or clearly marked as legacy.
- Adapting station phase 1 can proceed without relying on overlay transfer-zone mechanics.
