# WarehouseSimulation Skeleton + Machine-Driven Transfer Detection

This document proposes the **next phase** for the current codebase:

- keep `RouteFollower` and normal travel working
- stop making `Tote` responsible for finding/interpreting transfer zones
- introduce a small top-level `WarehouseSimulation`
- move transfer detection to the machine/infrastructure side

The goal is to improve the **shape of control** without trying to solve all transfer motion details at once.

---

## 1. Why this phase is the right next step

Your current baseline is now in a sensible place:

- `RouteFollower` works
- `RouteFollowerSnapshot` works
- `GraphFollowerBehaviour` is a thin bridge
- plain travel is stable when `evaluateWarehouseInteractions(...)` is not called

That means you now have a clean platform for the next architectural move.

The main remaining control-shape problem is this:

- `Tote` currently has the machinery/detection logic
- but in the domain, the **track/machine** should be active
- the tote should be a controlled carrier, not the orchestrator

So the next move is not “add more tote logic”.
It is:

**add a small simulation layer that lets machines observe and act on totes**

---

## 2. Immediate architectural target

Introduce:

```java
WarehouseSimulation
```

with responsibility for:
- owning active simulation objects
- calling `tote.update(...)`
- calling controller updates
- running machine-driven transfer detection
- defining update order

This gives you one place where the simulation tick lives.

---

## 3. Minimal class shape

```java
package online.davisfamily.warehouse.sim;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.tote.Tote;
import online.davisfamily.warehouse.transfer.TransferZoneController;

public class WarehouseSimulation {

    private final SimulationContext context;
    private final List<Tote> totes = new ArrayList<>();
    private final List<TransferZoneController> transferControllers = new ArrayList<>();

    public WarehouseSimulation(SimulationContext context) {
        this.context = context;
    }

    public void addTote(Tote tote) {
        totes.add(tote);
    }

    public void addTransferController(TransferZoneController controller) {
        transferControllers.add(controller);
    }

    public void update(double dtSeconds) {
        context.setSimulationTimeSeconds(
                context.getSimulationTimeSeconds() + dtSeconds);

        updateTotes(dtSeconds);
        detectTransferWindows();
        updateTransferControllers(dtSeconds);
    }

    private void updateTotes(double dtSeconds) {
        for (Tote tote : totes) {
            tote.update(context, dtSeconds);
        }
    }

    private void detectTransferWindows() {
        for (TransferZoneController controller : transferControllers) {
            for (Tote tote : totes) {
                detectTransferWindowsForTote(controller, tote);
            }
        }
    }

    private void updateTransferControllers(double dtSeconds) {
        for (TransferZoneController controller : transferControllers) {
            controller.update(dtSeconds);
        }
    }

    private void detectTransferWindowsForTote(TransferZoneController controller, Tote tote) {
        // implemented below
    }
}
```

This is intentionally small.

---

## 4. Update order

For the current phase, the update order should be:

### Phase 1
Update all totes:
- normal route following
- apply route snapshot to transform

### Phase 2
Let infrastructure observe totes:
- approach window
- transfer window

### Phase 3
Update controllers:
- resetting
- state cleanup
- later more logic

This is enough to move the control shape in the right direction without introducing a big simulation framework.

---

## 5. Keep GraphFollowerBehaviour only as a bridge

For now, `GraphFollowerBehaviour` can simply call:

```java
simulation.update(dtSeconds);
```

instead of calling one tote directly.

That is a good transition step.

### Transitional bridge example

```java
package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.Behaviour;
import online.davisfamily.threedee.RenderableObject;
import online.davisfamily.warehouse.sim.WarehouseSimulation;

public class GraphFollowerBehaviour implements Behaviour {

    private final WarehouseSimulation simulation;

    public GraphFollowerBehaviour(WarehouseSimulation simulation) {
        this.simulation = simulation;
    }

    @Override
    public void update(RenderableObject ro, float dtSeconds) {
        simulation.update(dtSeconds);
    }
}
```

### Important note
Only one object in the scene should own this behaviour, otherwise you will update the simulation multiple times per frame.

A better long-term approach is to remove this bridge entirely and drive the simulation from the scene/game loop, but this is good enough for transition.

---

## 6. Required Tote changes

To support machine-driven detection, `Tote` should expose its last route result.

Add:

```java
private RouteFollowerSnapshot lastRouteSnapshot;
```

and accessor:

```java
public RouteFollowerSnapshot getLastRouteSnapshot() {
    return lastRouteSnapshot;
}
```

Then in update:

```java
RouteFollowerSnapshot snapshot =
        routeFollower.advance(dtSeconds, isMotionBlocked());

lastRouteSnapshot = snapshot;
applySnapshot(snapshot);
```

### Important
For this phase, keep `evaluateWarehouseInteractions(...)` disabled or remove it from the call path.

Detection is now moving outward into `WarehouseSimulation`.

---

## 7. The Tote should not scan the world

This is the most important conceptual change.

### Remove responsibility from Tote
Do not make the tote do this:

- loop through transfer zones
- decide whether it is approaching a machine
- call transfer controllers itself

### Instead
Let `WarehouseSimulation` or transfer-side observers do this by inspecting:

```java
tote.getLastRouteSnapshot()
```

That way:
- the tote is passive
- machines/infrastructure are active
- control shape matches the domain better

---

## 8. Machine-driven transfer detection

Now the `WarehouseSimulation.detectTransferWindowsForTote(...)` method can perform detection externally.

```java
private void detectTransferWindowsForTote(TransferZoneController controller, Tote tote) {

    if (tote.getLastRouteSnapshot() == null) {
        return;
    }

    var snapshot = tote.getLastRouteSnapshot();
    var machine = controller.getMachine();
    var zone = machine.getDefinition();

    if (snapshot.currentSegment() != zone.getSourceSegment()) {
        return;
    }

    double distance = snapshot.distanceAlongSegment();

    if (isInApproachWindow(zone, distance)) {
        controller.onToteApproaching(tote, snapshot);
    }

    if (isInTransferWindow(zone, distance)) {
        controller.onToteEnteredTransferWindow(tote, snapshot);
    }
}
```

---

## 9. Window helpers

For this phase, keep them in `WarehouseSimulation` or a small utility.

```java
private boolean isInApproachWindow(TransferZone zone, double distanceAlongSegment) {
    double start = zone.getStartDistance();
    double triggerDistance = 20.0; // make configurable later

    return distanceAlongSegment >= start - triggerDistance
            && distanceAlongSegment < start;
}
```

```java
private boolean isInTransferWindow(TransferZone zone, double distanceAlongSegment) {
    return distanceAlongSegment >= zone.getStartDistance()
            && distanceAlongSegment <= zone.getEndDistance();
}
```

### Note
This fixes the inverted logic that appeared in the current tote-side helper.

---

## 10. Controller API refinement

To support machine-driven detection more cleanly, update the controller API slightly.

### Current style
```java
onToteEnteredTransferWindow(Tote tote)
```

### Better
```java
onToteEnteredTransferWindow(Tote tote, RouteFollowerSnapshot snapshot)
```

That keeps the controller decoupled from tote internals and gives it enough context for debugging / future checks.

Example:

```java
public void onToteEnteredTransferWindow(Tote tote, RouteFollowerSnapshot snapshot) {
    if (machine.getReservedTote() != tote) {
        return;
    }

    // later: start transfer motion
}
```

---

## 11. Single ownership of tote state

You previously identified an important problem:
- both controller and tote were mutating tote state

This phase should improve that.

### Recommendation
Controllers should not directly set low-level tote fields such as:
- raw interaction enum
- direct flags
- internal transfer motion state

Instead, controllers should call high-level methods on tote such as:

```java
tote.reserveForTransfer(machine);
tote.beginTransfer(machine);
tote.releaseTransfer(machine);
```

That keeps tote state ownership inside the tote.

### For this phase
Even if you do not fully refactor to command objects yet, at least move toward that API shape.

---

## 12. Revised Tote API

Suggested direction:

```java
public class Tote {

    private TransferZoneMachine reservedTransferZone;
    private TransferZoneMachine activeTransferZone;
    private ToteMotionState motionState;

    public void reserveForTransfer(TransferZoneMachine machine) {
        if (reservedTransferZone == null) {
            reservedTransferZone = machine;
        }
    }

    public void beginTransfer(TransferZoneMachine machine) {
        if (reservedTransferZone != machine) {
            return;
        }

        activeTransferZone = machine;
        motionState = ToteMotionState.TRANSFERRING;
    }

    public void releaseTransfer(TransferZoneMachine machine) {
        if (reservedTransferZone == machine) {
            reservedTransferZone = null;
        }
        if (activeTransferZone == machine) {
            activeTransferZone = null;
        }
        if (motionState == ToteMotionState.TRANSFERRING) {
            motionState = ToteMotionState.MOVING;
        }
    }
}
```

### Note
This does not yet solve every motion detail, but it restores sensible ownership.

---

## 13. Motion state vs reservation

You previously found another important issue:
- reservation was being treated as “blocked”

Do not do that.

### Separate concepts
- reservation = a machine has claimed the tote
- motion state = whether the tote is moving, held, blocked, or transferring

A reserved tote should normally still be moving.

So for now:

```java
private boolean isMotionBlocked() {
    return motionState == ToteMotionState.HELD
            || motionState == ToteMotionState.BLOCKED
            || motionState == ToteMotionState.TRANSFERRING;
}
```

Not when merely reserved.

---

## 14. Suggested ToteMotionState

```java
package online.davisfamily.warehouse.tote;

public enum ToteMotionState {
    MOVING,
    TRANSFERRING,
    HELD,
    BLOCKED
}
```

This is cleaner than overloading one interaction enum with everything.

---

## 15. Revised TransferZoneController shape

For this phase, the controller should own:
- zone state
- reservation decision
- transfer-zone lifecycle

But not tote internals.

### Example

```java
public void onToteApproaching(Tote tote, RouteFollowerSnapshot snapshot) {
    if (machine.getState() != TransferZoneState.IDLE) {
        return;
    }

    Optional<TransferDirection> decision = decisionStrategy.decide(tote, machine);
    if (decision.isEmpty()) {
        return;
    }

    machine.setReservedTote(tote);
    machine.setActiveDirection(decision.get());

    tote.reserveForTransfer(machine);

    machine.transitionTo(decision.get() == TransferDirection.LEFT
            ? TransferZoneState.READY_LEFT
            : TransferZoneState.READY_RIGHT);
}
```

and:

```java
public void onToteEnteredTransferWindow(Tote tote, RouteFollowerSnapshot snapshot) {
    if (machine.getReservedTote() != tote) {
        return;
    }

    tote.beginTransfer(machine);
    machine.transitionTo(TransferZoneState.TRANSFERRING);
}
```

This is a better boundary than directly mutating tote fields.

---

## 16. What this phase does NOT yet do

This phase is about **control shape**, not full transfer motion.

It does not yet require:
- full lateral transfer interpolation
- yaw freeze logic
- target segment handoff
- sensor abstraction
- command bus
- full world/event framework

Those can come next.

For now, the point is:
- the tote no longer scans the world
- the machine side is active
- the simulation has a root update loop

That is a major improvement already.

---

## 17. Practical integration plan

### Step 1
Add `WarehouseSimulation`

### Step 2
Expose `lastRouteSnapshot` from `Tote`

### Step 3
Move approach/transfer window detection from tote into `WarehouseSimulation`

### Step 4
Update `TransferZoneController` to use:
- `tote.reserveForTransfer(machine)`
- `tote.beginTransfer(machine)`

instead of direct low-level state mutation

### Step 5
Keep transfer motion inactive or stubbed until this control model is stable

This is the safest sequence.

---

## 18. Resulting control shape

After this phase, the control flow becomes:

```text
WarehouseSimulation
  -> updates Tote movement
  -> inspects tote snapshots against transfer zones
  -> calls TransferZoneController
  -> controller reserves / starts transfer on tote
  -> tote owns its internal state changes
```

That is much closer to the warehouse domain than:

```text
Tote
  -> scans transfer zones
  -> calls controller
  -> controller mutates tote internals
```

---

## 19. Final recommendation

This is the right next step for the current codebase because it improves the architecture without breaking your restored working baseline.

### Immediate benefit
- better control shape
- clearer ownership
- no longer tote-centric
- prepares naturally for multiple totes

### Later benefit
- makes it much easier to add:
  - stations
  - conveyors
  - sensors
  - top-level lifecycle/spawn management

---

## Final takeaway

**Introduce `WarehouseSimulation` now, and make transfer detection machine-driven by observing tote snapshots from outside the tote.**

That is the next clean step from the codebase you have today.
