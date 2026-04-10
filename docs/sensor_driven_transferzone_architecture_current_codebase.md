# Sensor-Driven TransferZone Architecture (Plugged into the Current Codebase)

This document replaces the earlier idea of putting transfer-window detection inside
`WarehouseSimulation`.

Instead, it moves toward the cleaner end-state you identified:

- `WarehouseSimulation` schedules and owns the simulation
- `TransferZoneSensor` detects tote approach / entry
- `TransferZoneController` manages machine state and decisions
- `TransferZoneMachine` holds runtime state
- `Tote` remains a controlled carrier, not the orchestrator

This is the better long-term shape and is still small enough to introduce incrementally.

---

## 1. The target control shape

The intended runtime flow is:

```text
WarehouseSimulation
  -> update totes
  -> update sensors
  -> update controllers
  -> sync visuals / bookkeeping
```

with:

```text
TransferZoneSensor
  -> observes tote snapshots
  -> detects approach / entry conditions
  -> calls TransferZoneController
```

and:

```text
TransferZoneController
  -> reserves tote
  -> arms zone
  -> begins transfer
  -> resets machine
```

That gives the right domain shape:

- world schedules
- sensors detect
- machines/controllers manage
- totes execute

---

## 2. WarehouseSimulation role

The world simulator should stay high level.

### Good responsibilities
- hold collections of totes, sensors, controllers, machines
- advance time
- call update methods in order
- handle spawning / release / removal of totes
- later handle overall lifecycle concerns

### Avoid these responsibilities
- transfer-window geometry checks
- station-specific logic
- decision logic
- machine-specific rules

So `WarehouseSimulation` should *not* do `detectTransferWindowForTote(...)` in the long term.

---

## 3. Minimal WarehouseSimulation skeleton

```java
package online.davisfamily.warehouse.sim;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.tote.Tote;
import online.davisfamily.warehouse.transfer.TransferZoneController;
import online.davisfamily.warehouse.transfer.TransferZoneSensor;

public class WarehouseSimulation {

    private final SimulationContext context;

    private final List<Tote> totes = new ArrayList<>();
    private final List<TransferZoneSensor> transferSensors = new ArrayList<>();
    private final List<TransferZoneController> transferControllers = new ArrayList<>();

    public WarehouseSimulation(SimulationContext context) {
        this.context = context;
    }

    public void addTote(Tote tote) {
        totes.add(tote);
    }

    public void addTransferSensor(TransferZoneSensor sensor) {
        transferSensors.add(sensor);
    }

    public void addTransferController(TransferZoneController controller) {
        transferControllers.add(controller);
    }

    public List<Tote> getTotes() {
        return totes;
    }

    public void update(double dtSeconds) {
        context.setSimulationTimeSeconds(
                context.getSimulationTimeSeconds() + dtSeconds);

        updateTotes(dtSeconds);
        updateTransferSensors();
        updateTransferControllers(dtSeconds);
    }

    private void updateTotes(double dtSeconds) {
        for (Tote tote : totes) {
            tote.update(context, dtSeconds);
        }
    }

    private void updateTransferSensors() {
        for (TransferZoneSensor sensor : transferSensors) {
            sensor.update(totes);
        }
    }

    private void updateTransferControllers(double dtSeconds) {
        for (TransferZoneController controller : transferControllers) {
            controller.update(dtSeconds);
        }
    }
}
```

This is intentionally simple and is enough for the current phase.

---

## 4. Keep GraphFollowerBehaviour as a temporary bridge

For now, you still need something to kick the simulation each frame.

A transitional bridge is still fine:

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

### Important
Only one object should own this bridge behaviour, otherwise the simulation will tick multiple times per frame.

Later, this bridge can disappear and the simulation can be updated directly from the scene/game loop.

---

## 5. Tote changes needed

### Required
Tote should expose its last route-following result:

```java
private RouteFollowerSnapshot lastRouteSnapshot;
```

with:

```java
public RouteFollowerSnapshot getLastRouteSnapshot() {
    return lastRouteSnapshot;
}
```

and update it during normal movement:

```java
RouteFollowerSnapshot snapshot =
        routeFollower.advance(dtSeconds, isMotionBlocked());

lastRouteSnapshot = snapshot;
applySnapshot(snapshot);
```

### Important
Do **not** call `evaluateWarehouseInteractions(...)` anymore.

The tote should not scan the world.

---

## 6. TransferZoneSensor responsibility

A sensor should:
- observe tote snapshots
- decide whether a tote is in its approach window
- decide whether a tote is in its transfer window
- notify the correct controller

It should not:
- own the whole machine state
- own the transfer decision strategy
- mutate tote internals directly

That belongs to the controller and tote API.

---

## 7. Minimal TransferZoneSensor skeleton

```java
package online.davisfamily.warehouse.transfer;

import java.util.List;

import online.davisfamily.threedee.routing.RouteFollowerSnapshot;
import online.davisfamily.threedee.routing.TransferZone;
import online.davisfamily.warehouse.tote.Tote;

public class TransferZoneSensor {

    private final TransferZoneController controller;
    private final TransferZone zone;
    private final double triggerDistance;

    public TransferZoneSensor(TransferZoneController controller,
                              TransferZone zone,
                              double triggerDistance) {
        this.controller = controller;
        this.zone = zone;
        this.triggerDistance = triggerDistance;
    }

    public void update(List<Tote> totes) {
        for (Tote tote : totes) {
            RouteFollowerSnapshot snapshot = tote.getLastRouteSnapshot();
            if (snapshot == null) {
                continue;
            }

            if (snapshot.currentSegment() != zone.getSourceSegment()) {
                continue;
            }

            double distance = snapshot.distanceAlongSegment();

            if (isInApproachWindow(distance)) {
                controller.onToteApproaching(tote, snapshot);
            }

            if (isInTransferWindow(distance)) {
                controller.onToteEnteredTransferWindow(tote, snapshot);
            }
        }
    }

    private boolean isInApproachWindow(double distanceAlongSegment) {
        double start = zone.getStartDistance();

        return distanceAlongSegment >= start - triggerDistance
                && distanceAlongSegment < start;
    }

    private boolean isInTransferWindow(double distanceAlongSegment) {
        return distanceAlongSegment >= zone.getStartDistance()
                && distanceAlongSegment <= zone.getEndDistance();
    }
}
```

This is enough to get the architecture moving in the right direction.

---

## 8. Debounce / repeated detection

A raw sensor like the above may call the controller every frame while the tote remains in the same range.

That is often acceptable at first if:
- the controller is state-aware
- repeated calls are ignored safely

For example:
- `onToteApproaching(...)` should only do anything when machine is `IDLE`
- `onToteEnteredTransferWindow(...)` should only do anything for the reserved tote

That may be sufficient initially.

### Later refinement
If needed, add edge detection to the sensor by remembering:
- which tote IDs are currently inside the approach window
- which tote IDs are currently inside the transfer window

But do not complicate the first version unless necessary.

---

## 9. Revised TransferZoneController role

The controller should own:
- reservation logic
- transfer decision
- zone state transitions
- reset logic

It should not directly mutate tote internals field-by-field.

It should instead call high-level tote methods.

### Recommended controller API

```java
public void onToteApproaching(Tote tote, RouteFollowerSnapshot snapshot)
public void onToteEnteredTransferWindow(Tote tote, RouteFollowerSnapshot snapshot)
public void onTransferComplete(Tote tote)
public void update(double dtSeconds)
```

---

## 10. Revised TransferZoneController example

```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.threedee.routing.RouteFollowerSnapshot;
import online.davisfamily.warehouse.tote.Tote;

public class TransferZoneController {

    private final TransferZoneMachine machine;
    private final TransferDecisionStrategy decisionStrategy;

    public TransferZoneController(TransferZoneMachine machine,
                                  TransferDecisionStrategy decisionStrategy) {
        this.machine = machine;
        this.decisionStrategy = decisionStrategy;
    }

    public void update(double dtSeconds) {
        machine.update(dtSeconds);

        if (machine.getState() == TransferZoneState.RESETTING
                && machine.getTimeInStateSeconds() > 0.1) {
            machine.clearActiveTransfer();
        }
    }

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

    public void onToteEnteredTransferWindow(Tote tote, RouteFollowerSnapshot snapshot) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.beginTransfer(machine);

        machine.transitionTo(TransferZoneState.TRANSFERRING);
    }

    public void onTransferComplete(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.releaseTransfer(machine);
        machine.transitionTo(TransferZoneState.RESETTING);
    }

    public TransferZoneMachine getMachine() {
        return machine;
    }
}
```

This gives better ownership boundaries than directly setting raw tote fields.

---

## 11. Tote API direction

To support the sensor/controller model, `Tote` should expose a narrow control API.

### Example
```java
public class Tote {

    private TransferZoneMachine reservedTransferZone;
    private TransferZoneMachine activeTransferZone;
    private ToteMotionState motionState = ToteMotionState.MOVING;

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

        // later:
        // initialise TransferMotionState here
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

This keeps tote state ownership inside tote, while machines remain the active agents.

---

## 12. Motion blocking and reservation must stay separate

You already identified this issue earlier, and it remains important.

A tote that is merely reserved for transfer should usually still be moving.

So do not let reservation imply blocked motion.

### Recommended motion state
```java
package online.davisfamily.warehouse.tote;

public enum ToteMotionState {
    MOVING,
    TRANSFERRING,
    HELD,
    BLOCKED
}
```

### Recommended block rule
```java
private boolean isMotionBlocked() {
    return motionState == ToteMotionState.HELD
            || motionState == ToteMotionState.BLOCKED
            || motionState == ToteMotionState.TRANSFERRING;
}
```

This is cleaner than using reservation as a motion block.

---

## 13. TransferZoneMachine remains the runtime state holder

Keep this as the dynamic runtime object that owns:

- current zone state
- reserved tote
- active direction
- time in state

That is still the correct place for machine runtime state.

No change of direction is needed there.

---

## 14. Integration sequence for the current codebase

Because your working baseline is now restored, use this order.

### Step 1
Add `WarehouseSimulation`

### Step 2
Change the temporary bridge so that one `GraphFollowerBehaviour` updates the simulation

### Step 3
Expose `lastRouteSnapshot` from `Tote`

### Step 4
Add `TransferZoneSensor`

### Step 5
Move transfer detection out of `Tote`

- stop using `evaluateWarehouseInteractions(...)`
- let `TransferZoneSensor` drive controller calls

### Step 6
Update `TransferZoneController` so it uses:
- `tote.reserveForTransfer(machine)`
- `tote.beginTransfer(machine)`
- `tote.releaseTransfer(machine)`

rather than mutating tote internals directly

### Step 7
Leave actual transfer motion disabled or stubbed until this control shape is stable

This keeps the slice narrow and testable.

---

## 15. What this phase intentionally does NOT solve

This phase is about **control architecture**, not full transfer behavior.

It does not yet require:
- full lateral transfer interpolation
- yaw freeze/orientation blending
- geometry-perfect handoff onto target segment
- generic sensor framework
- event bus
- full spawn/release logic
- station/conveyor support

Those can follow once the control shape is sound.

---

## 16. Why this is better than world-side detection

Compared with earlier `WarehouseSimulation.detectTransferWindowsForTote(...)`, this version is better because:

- `WarehouseSimulation` stays orchestration-level
- transfer logic is localized around the transfer zone subsystem
- sensors become reusable concepts for other machinery
- control shape matches the domain better
- later introduction of stations/conveyors becomes much more natural

So this is a worthwhile improvement over the intermediate design.

---

## 17. Longer-term evolution

Once this phase is stable, a natural next step is to generalize the pattern:

### Current
- `TransferZoneSensor`
- `TransferZoneController`
- `TransferZoneMachine`

### Later
- `StationArrivalSensor`
- `LidStationController`
- `LidStationMachine`

- `ConveyorOccupancySensor`
- `ConveyorController`
- `ConveyorMachine`

That is exactly the kind of scalable reactive plant architecture you were aiming for.

---

## 18. Final recommendation

For the current codebase, the best next move is:

- add a minimal `WarehouseSimulation`
- keep it as scheduler/registry only
- add `TransferZoneSensor`
- move detection there
- leave tote passive
- let controller manage the machine and request tote actions

That gets you closer to the right architecture without destabilizing the working route-following base.

---

## Final takeaway

**Yes — the correct follow-on from the current codebase is a sensor-driven transfer subsystem, with `WarehouseSimulation` acting only as the high-level scheduler.**

That is the cleanest next step and the best fit for the domain you’re modelling.
