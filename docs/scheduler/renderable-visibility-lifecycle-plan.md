# Renderable Visibility Lifecycle Plan

Status: drafted. Implement on `feature/renderable-visibility-lifecycle`.

## Summary

Detailed implementation plan for `feature/renderable-visibility-lifecycle`.

This branch adds a cheap renderable visibility mechanism and applies it narrowly to current tote/pack visual paths. The goal is to prepare for production-scale scheduler data where loaded orders and packs must not imply active render/update/pick work.

Do not add scheduler rules, JSON loading, database storage, scheduler threading, command panels, or broad scene rewrites in this branch.

## Key Decisions

- Add visibility at `RenderableObject`, because update/draw/pick traversal already centralises there.
- Hidden renderables should be skipped before behaviour update, mesh draw, child traversal, and picking.
- Default visibility is `true` so existing scenes continue to render unless explicitly hidden.
- Use visibility for pack renderables instead of moving them to `(-50, -50, -50)` as the primary hiding mechanism.
- Keep renderable allocation timing unchanged in this branch except where a step explicitly states otherwise.
- Do not remove pack renderables from maps just because they are hidden.
- Keep logical simulation state separate from render visibility.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/renderable-visibility-lifecycle
```

## Step 1: Add Core Renderable Visibility

Allowed files:

- Update `app/src/main/java/online/davisfamily/threedee/rendering/RenderableObject.java`
- Update `app/src/main/java/online/davisfamily/threedee/rendering/selection/ScenePicker.java`
- Create/update tests under `app/src/test/java/online/davisfamily/threedee/rendering/`
- Create/update tests under `app/src/test/java/online/davisfamily/threedee/rendering/selection/`

Implementation:

- Add a private or public visibility field to `RenderableObject`, defaulting to `true`.
- Add:
  - `public boolean isVisible()`
  - `public void setVisible(boolean visible)`
- Update `RenderableObject.update(double dtSeconds)`:
  - return immediately when `!isVisible()`
  - do not update behaviours or children for hidden renderables
- Update `RenderableObject.draw(...)`:
  - return immediately when `!isVisible()`
  - do not call `tr.drawMesh(...)`
  - do not draw children
- Update `ScenePicker.recursivePick(...)`:
  - return immediately when `!ro.isVisible()`
  - hidden renderables and their children must not be pickable

Tests:

- Add `RenderableObjectVisibilityTest`.
- Cover default visibility is true.
- Cover hidden renderable does not update its behaviour.
- Cover hidden parent does not update child behaviour.
- Add `ScenePickerVisibilityTest`.
- Cover hidden selectable object is not picked.

Expected output:

- Visibility exists as a cheap early skip in update, draw, and picking.
- Existing visible renderables behave as before.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.threedee.rendering.RenderableObjectVisibilityTest --tests online.davisfamily.threedee.rendering.selection.ScenePickerVisibilityTest
```

## Step 2: Add Pack Visual Visibility Helper

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/totebag/assembly/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/sim/totebag/assembly/`

Create:

- `PackRenderableVisibility.java`
  - `public static void show(RenderableObject renderable)`
  - `public static void hide(RenderableObject renderable)`
  - `public static void hideAndResetPose(RenderableObject renderable)`

Implementation:

- `show(...)` sets visibility true.
- `hide(...)` sets visibility false.
- `hideAndResetPose(...)` sets visibility false and resets local pose:
  - translations to `0f`
  - rotations to `0f`
- Reject null renderables with `IllegalArgumentException`.

Rules:

- Do not move hidden renderables to `-50f`.
- Keep this helper small; it is for current pack visual classes only.

Test:

- Add `PackRenderableVisibilityTest`.
- Cover show/hide.
- Cover hide-and-reset clears pose.
- Cover null rejection.

Expected output:

- Current pack visual classes have one local helper for visibility toggling.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.assembly.PackRenderableVisibilityTest
```

## Step 3: Track Tote Lid Visual State

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/sim/tote/Tote.java`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/tote/`

Implementation:

- Add a boolean lid visual state to `Tote`, initially false/closed.
- `openLids()` sets the state to open before/after applying lid behaviour.
- `closeLids()` sets the state to closed before/after applying lid behaviour.
- Add:
  - `public boolean areLidsOpen()`

Rules:

- This is a visual/logical flag for render decisions, not a physical latch model.
- Do not change lid animation behavior.
- Do not introduce a lid-opening machine in this branch.

Test:

- Add `ToteLidStateTest`, or extend an existing tote test if one already covers tote basics.
- Cover new tote reports lids closed.
- Cover `openLids()` reports open.
- Cover `closeLids()` reports closed.

Expected output:

- Pack visual code can decide whether contained packs should be visible without inspecting lid child rotations.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.tote.ToteLidStateTest
```

## Step 4: Apply Visibility To Integrated Tipper-To-Sorter Pack Visuals

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/sim/totebag/assembly/TipperToSorterPackVisuals.java`
- Create/update focused tests only if a testable pattern already exists; otherwise rely on Step 6 focused tote-to-bag/integration tests plus visual check.

Implementation:

- Replace `hideDetachedPacks()` translation-to-`-50f` behavior with `PackRenderableVisibility.hideAndResetPose(renderable)` for renderables not attached to any tote.
- When a pack is positioned inside a tote:
  - set its local pose as today
  - call `PackRenderableVisibility.show(renderable)` only if that tote's `Tote.areLidsOpen()` is true
  - call `PackRenderableVisibility.hide(renderable)` if the tote lids are closed
- When a pack is detached/free or actively discharging:
  - call `PackRenderableVisibility.show(renderable)`
- When sorting module sync places queued/free packs:
  - keep those packs visible after they leave tote containment.

Rules:

- Do not remove existing pack renderables from `packRenderablesById`.
- Do not change pack containment state transitions.
- Do not change discharge timing or transfer paths.

Expected output:

- Contained packs are skipped while the tote lid is closed.
- Free/discharging/queued packs remain visible.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

## Step 5: Apply Visibility To Legacy Tipper-To-Receiver Pack Visuals

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/TipperToReceiverPackVisuals.java`
- Create/update focused tests only if practical without building a visual rig; otherwise rely on Step 6 focused tests plus visual check.

Implementation:

- Replace translation-to-`-50f` hiding with `PackRenderableVisibility.hideAndResetPose(renderable)`.
- This debug path only has a tote renderable, not a `Tote` object, so keep contained packs visible unless there is an existing direct lid-state source available.
- Show active/free discharging packs.
- Avoid inventing a new lid-state API for this legacy debug-only path.

Rules:

- Keep this as a compatibility cleanup for the old debug path.
- Do not broaden the branch into legacy rig refactoring.

Expected output:

- The old tipper-to-receiver debug path uses renderable visibility instead of off-screen hiding for inactive/free-pack reset.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest
```

## Step 6: Focused Integration And Visual Check

Allowed files:

- No code changes unless the focused run or visual pass exposes a concrete issue.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.testing.scheduler.*
```

Then ask the user to run the integrated tote-to-bag visual scene.

Visual expectations:

- Totes, machines, and conveyors render as before.
- Pack renderables are visible when packs are free, discharging, queued, or otherwise active.
- Contained packs are not rendered/pickable while their tote lid is closed in the integrated tipper-to-sorter path.
- Opening lids makes contained pack renderables visible again.
- Selecting hidden packs should not work.
- Scheduler debug overlay on `tipper_slide` still works.

Expected output:

- Renderable visibility works in the main visual path without changing machine behavior.

## Step 7: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.threedee.rendering.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

Then ask the user to run their trusted broader suite/visual pass.

Completion criteria:

- Hidden renderables skip update, draw, and picking.
- Current visible renderables continue to behave as before.
- Pack visual paths use visibility rather than off-screen translation for hidden/reset state.
- Contained integrated tote packs can be hidden while lids are closed.
- No scheduler behavior changes are introduced.
- No renderable preloading from JSON data is introduced.

## Deferred Work

- Pooling/retiring bagged pack renderables.
- Creating renderables lazily from loaded production datasets.
- Root selection routing for composite machine renderables such as the tipper assembly.
- Command-panel/manual exception workflows.
- Full warehouse layout performance measurement.
