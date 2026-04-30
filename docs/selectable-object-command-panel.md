# Selectable Object Command Panel

## Purpose

Future debug tooling should allow selected scene objects to expose executable commands as well as inspection text.

Example target behaviour:

- right-click a tote in the 3D view
- debug UI shows the existing inspection data
- debug UI also shows buttons such as `Open lids` and `Close lids`
- pressing a button executes the command on the selected domain object

This is intended first as debug / harness tooling. However, the design should not block later use as a simulation command surface for operations such as stopping a bagging machine.

## Current State

Selection is already available through the 3D framework.

- `MouseHandler` right-click calls `Scene.pickAt(...)`.
- `BaseScene.selectAt(...)` uses `ScenePicker` to ray-pick a `RenderableObject`.
- `SelectionManager` stores the selected renderable.
- `RenderableObject.getSelectionTarget()` allows child renderables, such as tote lids, to select their parent object.
- `SelectionInspectionRegistry` maps selected renderables to `Inspectable` providers.
- `DebugUtils.drawDebugText(...)` draws inspection lines directly into the framebuffer overlay.

The current debug display is not a Swing panel. It is rendered text inside the software-rendered image.

## Preferred Direction

Use a Swing side panel for interactive commands rather than adding button hit-testing to the framebuffer overlay.

Reasoning:

- Swing already provides buttons, layout, focus, mouse interaction, and enabled/disabled states.
- The software framebuffer overlay can remain simple diagnostic drawing.
- Command execution stays out of the renderer.
- The 3D framework only needs to expose selected-object metadata and command descriptors.

## Proposed API Shape

Introduce a command descriptor:

```java
public record DebugCommand(
        String id,
        String label,
        BooleanSupplier enabled,
        Runnable action) {
}
```

Extend the inspection concept to include commands. This can be done either by replacing `Inspectable` or by adding a sibling interface.

Preferred option:

```java
public interface DebugInspectable {
    List<String> describe();
    List<DebugCommand> commands();
}
```

`SelectionInspectionRegistry` can then evolve into a selected-object debug registry:

- register a renderable selection target
- provide inspection text
- provide command descriptors
- resolve by `renderable.getSelectionTarget()` as it does today

## Example Tote Registration

The tote itself should own domain operations such as:

- `tote.openLids()`
- `tote.closeLids()`

The debug registration should wire those operations into commands:

```java
debugRegistry.register(toteRenderable, new DebugInspectable() {
    @Override
    public List<String> describe() {
        return List.of(
                "Type: Tote",
                "State: " + tote.getInteractionMode());
    }

    @Override
    public List<DebugCommand> commands() {
        return List.of(
                new DebugCommand("open_lids", "Open lids", () -> true, tote::openLids),
                new DebugCommand("close_lids", "Close lids", () -> true, tote::closeLids));
    }
});
```

The renderable should not own the tote behaviour. It is only the selection anchor.

## Swing Integration

`SoftwareRenderer` currently adds only the render `Canvas` to the frame.

Future structure should be closer to:

- frame content pane uses `BorderLayout`
- canvas is added to `BorderLayout.CENTER`
- debug command panel is added to `BorderLayout.EAST`
- the scene or selection manager exposes the current selected debug model
- the panel refreshes when selection changes

The panel can initially be simple:

- selected object id
- inspection text as labels
- one button per command
- button enabled state from `DebugCommand.enabled()`
- button action calls `DebugCommand.action().run()`

## Selection Change Notification

The current `SelectionManager` only stores selected state. It does not publish selection changes.

Add a small listener mechanism:

```java
public interface SelectionListener {
    void selectionChanged(RenderableObject selected);
}
```

`SelectionManager.setSelected(...)` and `clear()` can notify listeners when the selected object changes.

This avoids polling from the Swing panel.

## Command Types

Commands should be split conceptually into two categories.

Visual/debug-local commands:

- affect only renderable behaviour or debug harness presentation
- examples: open tote lids, close tote lids
- can initially be simple `Runnable` actions while the command panel is proving itself

Simulation-authoritative commands:

- change simulation state or machine control policy
- examples: stop bagging machine, hold a conveyor, release a held tote, pause a transfer mechanism
- must execute inside the simulation/scene update path rather than directly from the Swing event thread
- should rely on existing machine readiness and next/previous-state contracts to propagate effects upstream/downstream

Stopping a bagging machine is a good example of the second category. The panel command should not directly mutate arbitrary render state. It should request a machine-control action; the simulation then applies that action, and upstream systems naturally observe the resulting unavailable / blocked state through the existing readiness seams.

## Threading And Command Dispatch

The render loop currently runs on its own thread while Swing events run on the EDT.

Command actions that mutate simulation/domain state should be treated carefully.

For the first debug implementation, it is acceptable to execute simple visual commands such as tote lid open/close directly if the current threading assumptions remain unchanged. Simulation-authoritative commands should be queued onto the simulation/render thread rather than mutating state from the EDT.

Recommended first implementation:

- document the debug-only threading assumption
- keep commands small and local
- treat direct execution as valid only for visual/debug-local commands

Recommended simulation-command implementation:

- introduce a scene command queue
- Swing button action enqueues a command request
- `BaseScene.renderFrame(...)` drains command requests before simulation update
- the command request resolves to a domain action on the selected object or registered controller
- machine/controller state then propagates through normal simulation readiness and reservation seams

Possible command shape:

```java
public enum DebugCommandExecutionMode {
    IMMEDIATE_DEBUG,
    SIMULATION_QUEUED
}
```

`DebugCommand` can then include an execution mode:

```java
public record DebugCommand(
        String id,
        String label,
        DebugCommandExecutionMode executionMode,
        BooleanSupplier enabled,
        Runnable action) {
}
```

For queued commands, `action` should be run only by the scene/simulation thread.

## Implementation Slices

1. Introduce command model.
   - Add `DebugCommand`.
   - Add `DebugInspectable` or extend `Inspectable`.
   - Keep existing text-only registrations working through an adapter/default method.

2. Add command-aware registry.
   - Continue resolving through `RenderableObject.getSelectionTarget()`.
   - Provide `describe(selected)` and `commands(selected)`.

3. Add selection change notifications.
   - Extend `SelectionManager`.
   - Keep existing selection toggling behaviour.

4. Add Swing debug panel.
   - Create a small panel class that renders selected id, inspection lines, and command buttons.
   - Wire it from `SoftwareRenderer` or scene construction.

5. Register first real commands.
   - Use tote `openLids()` and `closeLids()` as the first proving case.
   - Keep the current framebuffer inspection overlay until the Swing panel is proven.

6. Add queued simulation command support.
   - Add a scene-level command queue.
   - Route `SIMULATION_QUEUED` commands through that queue.
   - Prove with a machine command such as stopping / holding the bagging machine.
   - Verify upstream gating responds through existing readiness contracts rather than direct upstream coupling.

7. Optional later cleanup.
   - Decide whether framebuffer inspection text should remain, be reduced, or be replaced by the Swing panel.
   - Revisit whether command descriptors need richer state such as confirmation prompts, categories, or tooltips.

## Non-Goals

- Do not make `RenderableObject` responsible for domain logic.
- Do not add custom button hit-testing to the framebuffer overlay unless Swing proves unsuitable.
- Do not couple framework selection directly to warehouse-specific types such as `Tote`.
- Do not build a general application UI framework yet.
- Do not bypass existing machine readiness / reservation seams when a command affects simulation flow.
