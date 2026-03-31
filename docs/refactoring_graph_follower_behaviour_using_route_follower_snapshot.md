# Refactoring GraphFollowerBehaviour Using RouteFollowerSnapshot

This document maps the current `GraphFollowerBehaviour` style toward a cleaner split based on:

- framework-level `RouteFollower`
- framework-level `RouteFollowerSnapshot`
- solution-level warehouse objects and controllers

The goal is not a big-bang rewrite. The goal is to peel responsibilities out of `GraphFollowerBehaviour` in a controlled order.

---

## 1. Current problem statement

`GraphFollowerBehaviour` currently appears to blend several different concerns:

### Framework-level responsibilities
- advancing along the current route segment
- handling segment transitions
- sampling path position/orientation
- maintaining distance along segment
- possibly handling forward/reverse traversal rules

### Warehouse-solution responsibilities
- identifying whether a transfer zone should affect the moving object
- invoking transfer decision logic
- reserving/committing a transfer
- stopping or redirecting the tote
- managing transfer timing / interpolation
- freezing or overriding yaw during transfer
- coordinating with track/zone-specific logic

This works, but it makes the class a convergence point for nearly every future warehouse rule.

The refactor target is:

- keep route progression generic
- move warehouse workflow into warehouse objects/controllers
- keep rendering/animation concerns separate again

---

## 2. Target architecture in one picture

### Framework side
- `RouteFollower`
- `RouteFollowerSnapshot`
- `RouteSegment`
- `PathSegment3`
- generic simulation support

### Warehouse side
- `Tote`
- `ToteInteractionMode`
- `TransferZoneMachine`
- `TransferZoneController`
- future station/tipper/conveyor controllers
- warehouse event/sensor logic

### Rendering side
- renderables reflect state
- behaviours animate state
- they do not own warehouse decision workflows

---

## 3. The most important refactor principle

Do **not** try to refactor transfer zones first.

Refactor the **generic movement core** first.

In practice that means:

### Extract first
- route advancement
- segment transition logic
- route-position sampling
- orientation sampling
- distance bookkeeping

### Leave behind for later extraction
- transfer decisions
- stop/release logic
- interaction flags
- station handling
- transfer interpolation
- zone reservations

That boundary gives you a stable framework component before touching warehouse logic.

---

## 4. Step 1 — Identify and isolate pure route-following state

Create a new framework class:

```java
package online.davisfamily.threedee.routing;

public class RouteFollower {
    private RouteSegment currentSegment;
    private double distanceAlongSegment;
    private double speedUnitsPerSecond;

    public RouteFollower(RouteSegment currentSegment, double distanceAlongSegment, double speedUnitsPerSecond) {
        this.currentSegment = currentSegment;
        this.distanceAlongSegment = distanceAlongSegment;
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }

    public RouteFollowerSnapshot advance(double dtSeconds, boolean blocked) {
        if (!blocked) {
            advanceDistance(dtSeconds);
        }
        return buildSnapshot();
    }

    private void advanceDistance(double dtSeconds) {
        // move along route
        // handle segment transitions
    }

    private RouteFollowerSnapshot buildSnapshot() {
        // derive position/orientation from current segment + distance
        return null;
    }

    public RouteSegment getCurrentSegment() {
        return currentSegment;
    }

    public double getDistanceAlongSegment() {
        return distanceAlongSegment;
    }

    public double getSpeedUnitsPerSecond() {
        return speedUnitsPerSecond;
    }

    public void setSpeedUnitsPerSecond(double speedUnitsPerSecond) {
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }
}
```

### What to move into this class
Anything in `GraphFollowerBehaviour` that only depends on:
- current segment
- route connections
- path geometry
- distance along path
- generic movement direction/rules

### What not to move
Anything that depends on:
- transfer zones
- transfer strategies
- tote reservations
- rendering-specific animation state
- warehouse station semantics

---

## 5. Step 2 — Create RouteFollowerSnapshot

Use the snapshot as the framework/solution boundary.

```java
package online.davisfamily.threedee.routing;

import online.davisfamily.threedee.Vec3;

public record RouteFollowerSnapshot(
        RouteSegment currentSegment,
        double distanceAlongSegment,
        Vec3 position,
        Vec3 forward,
        Vec3 up,
        double segmentLength,
        double remainingDistanceOnSegment
) {
}
```

### Why this matters
This lets `RouteFollower` return a complete movement result without knowing what kind of object is moving.

That is the clean separation you want.

---

## 6. Step 3 — Introduce a warehouse Tote object

The tote becomes a warehouse-side simulation object that *uses* the generic route follower.

```java
package online.davisfamily.warehouse.tote;

import online.davisfamily.threedee.ObjectTransformation;
import online.davisfamily.threedee.routing.RouteFollower;
import online.davisfamily.threedee.routing.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationObject;

public class Tote implements SimulationObject {
    private final String id;
    private final ObjectTransformation transformation;
    private final RouteFollower routeFollower;

    private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;

    public Tote(String id, ObjectTransformation transformation, RouteFollower routeFollower) {
        this.id = id;
        this.transformation = transformation;
        this.routeFollower = routeFollower;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        RouteFollowerSnapshot snapshot =
                routeFollower.advance(dtSeconds, isMotionBlocked());

        applySnapshot(snapshot);
        evaluateWarehouseInteractions(context, snapshot);
    }

    private boolean isMotionBlocked() {
        return interactionMode != ToteInteractionMode.FREE;
    }

    private void applySnapshot(RouteFollowerSnapshot snapshot) {
        transformation.position.set(snapshot.position());
        // Derive rotation from snapshot.forward()/snapshot.up()
    }

    private void evaluateWarehouseInteractions(SimulationContext context, RouteFollowerSnapshot snapshot) {
        // warehouse-only logic
    }
}
```

### Important point
The tote depends on framework classes.

The framework classes do not depend on `Tote`.

That is the correct direction.

---

## 7. Step 4 — Keep GraphFollowerBehaviour temporarily as a bridge

Do not delete `GraphFollowerBehaviour` immediately.

For an incremental refactor, reduce it to a thin adapter.

### Transitional version
```java
public class GraphFollowerBehaviour implements Behaviour {
    private final Tote tote;
    private final SimulationContext simulationContext;

    @Override
    public void update(RenderableObject ro, float dtSeconds) {
        tote.update(simulationContext, dtSeconds);
    }
}
```

This is useful because:
- your engine update loop may still be renderable-driven
- you can move logic out without changing all scheduling at once

### Long-term direction
Eventually you may want a simulation update loop separate from render behaviours, but that does not have to happen first.

---

## 8. Step 5 — Move transfer-specific detection out of RouteFollower

In the current design, transfer-zone detection may be embedded inside `GraphFollowerBehaviour`.

That logic should move to warehouse-side code.

### Example target
Inside `Tote.update(...)`:

```java
private void evaluateWarehouseInteractions(SimulationContext context, RouteFollowerSnapshot snapshot) {
    // inspect current segment
    // inspect distanceAlongSegment
    // detect approach/entry thresholds
    // publish warehouse events if needed
}
```

or, preferably, through external warehouse sensors/controllers:

- tote publishes or exposes route state
- transfer approach sensor observes tote + route snapshot
- controller reacts

### Which is better?
For long-term design, external sensors/controllers are better.

For incremental migration, tote-local checks may be a practical stepping stone.

---

## 9. Step 6 — Replace internal transfer flags with ToteInteractionMode

`GraphFollowerBehaviour` likely has a set of booleans and transient state related to:
- in transfer
- transfer active
- freeze yaw
- pending redirect
- blocked
- committed to zone

These should not remain as ad hoc flags.

Introduce a warehouse-side state model such as:

```java
package online.davisfamily.warehouse.tote;

public enum ToteInteractionMode {
    FREE,
    RESERVED_FOR_TRANSFER,
    TRANSFERRING,
    HELD_AT_STATION,
    BLOCKED_BY_INTERLOCK
}
```

### Why this helps
It centralizes warehouse interaction state in the warehouse object instead of scattering it through a movement behaviour.

---

## 10. Step 7 — Move transfer-zone runtime state into a machine

Your existing `TransferZone` sounds like a definition/config object.

Keep that role, but create a separate runtime machine:

```java
package online.davisfamily.warehouse.transfer;

public class TransferZoneMachine {
    private final String id;
    private final TransferZoneDefinition definition;

    private TransferZoneState state = TransferZoneState.IDLE;
    private Tote reservedTote;
    private TransferDirection direction;
    private double timeInStateSeconds;

    public void update(double dtSeconds) {
        timeInStateSeconds += dtSeconds;
    }

    public void transitionTo(TransferZoneState newState) {
        this.state = newState;
        this.timeInStateSeconds = 0.0;
    }
}
```

### Why this matters
The runtime state currently risks being spread across:
- moving tote logic
- behaviour flags
- strategy state
- renderable state

This gives it a proper home.

---

## 11. Step 8 — Add a TransferZoneController

The controller reacts to events or state observations and updates the machine/tote state.

```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.warehouse.tote.Tote;
import online.davisfamily.warehouse.tote.ToteInteractionMode;

public class TransferZoneController {
    private final TransferZoneMachine machine;
    private final TransferDecisionStrategy decisionStrategy;

    public TransferZoneController(TransferZoneMachine machine,
                                  TransferDecisionStrategy decisionStrategy) {
        this.machine = machine;
        this.decisionStrategy = decisionStrategy;
    }

    public void onToteApproaching(Tote tote) {
        if (machine.getState() != TransferZoneState.IDLE) {
            return;
        }

        Optional<TransferDirection> decision = decisionStrategy.decide(tote, machine);
        if (decision.isEmpty()) {
            return;
        }

        machine.setReservedTote(tote);
        machine.setDirection(decision.get());
        machine.transitionTo(decision.get() == TransferDirection.LEFT
                ? TransferZoneState.READY_LEFT
                : TransferZoneState.READY_RIGHT);

        tote.setInteractionMode(ToteInteractionMode.RESERVED_FOR_TRANSFER);
    }

    public void onToteEnteredZone(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.TRANSFERRING);
        machine.transitionTo(TransferZoneState.TRANSFERRING);
    }
}
```

### Important point
This controller belongs in the warehouse layer, not in the framework routing layer.

---

## 12. Step 9 — Keep actual transfer motion separate from transfer decision

There are really two transfer concerns:

### A. Control logic
- should this tote transfer?
- left or right?
- reserve this tote
- when does transfer begin/end?

### B. Motion/animation execution
- lateral shift
- yaw freeze
- interpolation
- roller visual indication

Do not keep these mixed forever.

### Recommended split
- controller / machine handles A
- motion executor or actuator handles B

For example:
- `TransferZoneController` decides when a transfer begins
- `TransferMotionExecutor` applies lateral displacement over time
- `RollerIndicatorBehaviour` animates rollers based on machine state

---

## 13. Where the old GraphFollowerBehaviour code likely lands

This section gives a mapping rather than exact code.

### Responsibility: move forward along route
**New home:** `RouteFollower`

### Responsibility: update current segment/distance
**New home:** `RouteFollower`

### Responsibility: compute position and orientation
**New home:** `RouteFollower.buildSnapshot()`

### Responsibility: write pose into transformation
**New home:** warehouse `Tote.applySnapshot(...)`

### Responsibility: decide if transfer should occur
**New home:** `TransferDecisionStrategy` + `TransferZoneController`

### Responsibility: remember active transfer
**New home:** `ToteInteractionMode` + `TransferZoneMachine`

### Responsibility: perform lateral transfer motion
**New home:** warehouse transfer motion executor / actuator

### Responsibility: freeze yaw during transfer
**New home:** transfer motion executor, or tote-side motion application policy

### Responsibility: stop at station / wait for downstream / similar future logic
**New home:** future warehouse controllers and station machines

This is the core redistribution.

---

## 14. Recommended migration sequence

This order reduces risk.

### Phase 1 — Extract framework movement without changing behaviour externally
- create `RouteFollower`
- create `RouteFollowerSnapshot`
- move route advancement code into it
- have `GraphFollowerBehaviour` call into `RouteFollower`

At this phase, the class still exists, but movement logic is no longer trapped inside it.

### Phase 2 — Introduce warehouse Tote
- create `Tote`
- move warehouse interaction flags/state into `Tote`
- `GraphFollowerBehaviour` becomes a bridge to `Tote.update(...)`

### Phase 3 — Extract transfer runtime state
- create `TransferZoneMachine`
- move active transfer state into it
- reduce direct transfer state stored on moving object/behaviour

### Phase 4 — Add controller layer
- create `TransferZoneController`
- move decision invocation and state transitions there

### Phase 5 — Move transfer motion into dedicated executor
- isolate interpolation/lateral transfer logic
- leave `RouteFollower` purely route-based

### Phase 6 — Generalize for stations/conveyors
- once transfer logic is clean, apply the same pattern elsewhere

---

## 15. A practical transitional target

A good intermediate state would be:

### Framework
- `RouteFollower`
- `RouteFollowerSnapshot`

### Warehouse
- `Tote`
- `ToteInteractionMode`
- `TransferZoneMachine`
- `TransferZoneController`

### Bridge
- `GraphFollowerBehaviour` delegates to `Tote.update(...)`

That is already a major improvement even before sensors/events are fully introduced.

---

## 16. Optional enhancement — publish generic movement observations

You may later want a neutral event/observation model for segment transitions.

For example:

```java
public record RouteSegmentChangedObservation(
        String objectId,
        RouteSegment previousSegment,
        RouteSegment currentSegment
) {
}
```

This can still remain framework-level if it stays movement-generic.

Then warehouse sensors/controllers can build on top of it.

This is optional, but it can help if you want looser coupling later.

---

## 17. What not to over-engineer yet

Avoid introducing all of these immediately:
- full global event bus
- complex generic state machine library
- reservation framework for everything
- formal ECS-style architecture

Those may be useful later, but they are not necessary to split `GraphFollowerBehaviour` cleanly.

The high-value move right now is simply:
- framework movement result via snapshot
- warehouse state and control extracted upward

---

## 18. Final recommendation

The safest and cleanest refactor path is:

1. extract `RouteFollower`
2. define `RouteFollowerSnapshot`
3. let a warehouse `Tote` consume it
4. turn `GraphFollowerBehaviour` into a bridge
5. move transfer decision/runtime state into warehouse machines/controllers
6. later apply the same pattern to stations, tippers, conveyors

This preserves your framework/solution boundary and stops `GraphFollowerBehaviour` from becoming the home for every future warehouse rule.

---

## Final distilled rule

**Use `RouteFollowerSnapshot` as the seam.**

Below that seam:
- generic framework routing/movement

Above that seam:
- warehouse objects, transfer logic, station logic, interlocks, controllers

That gives you a stable base for both the engine and the warehouse solution to grow independently.
