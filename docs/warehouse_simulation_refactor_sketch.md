# Warehouse Simulation Framework Refactor Sketch

This document proposes a generic, state/event-driven simulation layer that sits above the current renderable/behaviour model.

## Goals

- Keep `RenderableObject` primarily visual.
- Keep `Behaviour` primarily for animation / actuation.
- Move warehouse process logic into simulation/domain objects.
- Support transfer zones, lid stations, tippers, conveyors, pack aggregation, and future equipment using the same architectural style.
- Refactor incrementally, not via a full rewrite.

---

## Recommended high-level shape

### Rendering layer
- `RenderableObject`
- `Mesh`
- `ObjectTransformation`
- track/tote/station renderable factories

### Actuation / visual behaviour layer
- `TransferAnimationBehaviour`
- `RollerIndicatorBehaviour`
- `LidAnimationBehaviour`
- `TipAnimationBehaviour`
- conveyor visual motion behaviours

These behaviours should respond to simulation state rather than own workflow decisions.

### Simulation/domain layer
- `SimObject`
- `SimTote`
- `SimTransferZone`
- `SimLidStation`
- `SimTipperStation`
- `SimConveyorSection`

### Control/state layer
- `RouteFollower`
- `TransferZoneController`
- `StationController`
- `ConveyorController`
- reusable state machine / transition support

### Interaction/event layer
- `SimulationEvent`
- `SimulationEventBus` or simple event queue
- `SimulationSensor`
- `ApproachSensor`
- `OccupancySensor`
- `CountSensor`

---

## Core interfaces

```java
package online.davisfamily.threedee.sim;

public interface SimObject {
    String getId();
    void update(SimulationContext context, double dtSeconds);
}
```

```java
package online.davisfamily.threedee.sim;

public interface StatefulSimObject<S extends Enum<S>> extends SimObject {
    S getState();
}
```

```java
package online.davisfamily.threedee.sim;

public interface SimulationEvent {
    String getSourceId();
    double getSimulationTimeSeconds();
}
```

```java
package online.davisfamily.threedee.sim;

public interface SimulationSensor extends SimObject {
}
```

```java
package online.davisfamily.threedee.sim;

public interface SimulationController {
    void handleEvent(SimulationEvent event, SimulationContext context);
}
```

---

## SimulationContext

This gives you a clean place for shared runtime services.

```java
package online.davisfamily.threedee.sim;

import java.util.ArrayDeque;
import java.util.Queue;

public class SimulationContext {
    private final Queue<SimulationEvent> eventQueue = new ArrayDeque<>();
    private double simulationTimeSeconds;

    public void publish(SimulationEvent event) {
        eventQueue.add(event);
    }

    public Queue<SimulationEvent> getEventQueue() {
        return eventQueue;
    }

    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }

    public void setSimulationTimeSeconds(double simulationTimeSeconds) {
        this.simulationTimeSeconds = simulationTimeSeconds;
    }
}
```

You can expand this later with:
- registries/lookups
- reservation manager
- diagnostics/tracing
- metrics/debug tools
- time/timer helpers

---

## Tote model

This is the main inversion from the current design: the tote becomes a simulation object that owns its runtime state, while the renderable simply reflects that state.

```java
package online.davisfamily.threedee.sim.tote;

import online.davisfamily.threedee.RenderableObject;
import online.davisfamily.threedee.routing.RouteFollower;
import online.davisfamily.threedee.sim.SimObject;
import online.davisfamily.threedee.sim.SimulationContext;

public class SimTote implements SimObject {
    private final String id;
    private final RouteFollower routeFollower;
    private final RenderableObject renderable;

    private ToteInteractionMode interactionMode = ToteInteractionMode.FREE;
    private String controllingEquipmentId;

    public SimTote(String id, RouteFollower routeFollower, RenderableObject renderable) {
        this.id = id;
        this.routeFollower = routeFollower;
        this.renderable = renderable;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        routeFollower.update(context, dtSeconds, this);
        syncRenderableFromSimulation();
    }

    private void syncRenderableFromSimulation() {
        // Copy position/orientation from routeFollower into renderable transformation.
    }

    public RouteFollower getRouteFollower() {
        return routeFollower;
    }

    public RenderableObject getRenderable() {
        return renderable;
    }

    public ToteInteractionMode getInteractionMode() {
        return interactionMode;
    }

    public void setInteractionMode(ToteInteractionMode interactionMode) {
        this.interactionMode = interactionMode;
    }

    public String getControllingEquipmentId() {
        return controllingEquipmentId;
    }

    public void setControllingEquipmentId(String controllingEquipmentId) {
        this.controllingEquipmentId = controllingEquipmentId;
    }
}
```

```java
package online.davisfamily.threedee.sim.tote;

public enum ToteInteractionMode {
    FREE,
    RESERVED,
    STOPPED,
    TRANSFERRING,
    STATION_HELD
}
```

---

## Extract RouteFollower from GraphFollowerBehaviour

`GraphFollowerBehaviour` currently does too much. The refactor target is for route motion to become a domain/simulation service rather than a renderable-attached behaviour.

```java
package online.davisfamily.threedee.routing;

import online.davisfamily.threedee.sim.SimulationContext;
import online.davisfamily.threedee.sim.tote.SimTote;

public class RouteFollower {
    private RouteSegment currentSegment;
    private double distanceAlongSegment;
    private double speedUnitsPerSecond;

    public RouteFollower(RouteSegment currentSegment, double speedUnitsPerSecond) {
        this.currentSegment = currentSegment;
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }

    public void update(SimulationContext context, double dtSeconds, SimTote tote) {
        // Advance along segment if the tote is not blocked/station-held/transferring.
        // Publish events when passing logical thresholds if appropriate.
    }

    public RouteSegment getCurrentSegment() {
        return currentSegment;
    }

    public double getDistanceAlongSegment() {
        return distanceAlongSegment;
    }

    public void setDistanceAlongSegment(double distanceAlongSegment) {
        this.distanceAlongSegment = distanceAlongSegment;
    }

    public double getSpeedUnitsPerSecond() {
        return speedUnitsPerSecond;
    }

    public void setSpeedUnitsPerSecond(double speedUnitsPerSecond) {
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }
}
```

### What moves out of `GraphFollowerBehaviour`
Move these responsibilities out first:
- transfer-zone reservation/commitment
- decision strategy invocation
- station stop/release logic
- equipment-specific workflow timing

### What may stay inside RouteFollower
- advancing along the route
- segment transitions
- sampling tangent/orientation
- distance bookkeeping

---

## Transfer zone as stateful simulation equipment

Your current `TransferZone` looks like a definition/config object. Keep that idea, but introduce a stateful runtime counterpart.

```java
package online.davisfamily.threedee.sim.transfer;

public enum TransferZoneState {
    IDLE,
    ARMING,
    READY_LEFT,
    READY_RIGHT,
    TRANSFERRING,
    RESETTING,
    BLOCKED
}
```

```java
package online.davisfamily.threedee.sim.transfer;

import online.davisfamily.threedee.routing.TransferZone;
import online.davisfamily.threedee.sim.StatefulSimObject;
import online.davisfamily.threedee.sim.SimulationContext;
import online.davisfamily.threedee.sim.tote.SimTote;

public class SimTransferZone implements StatefulSimObject<TransferZoneState> {
    private final String id;
    private final TransferZone definition;

    private TransferZoneState state = TransferZoneState.IDLE;
    private SimTote reservedTote;
    private TransferDirection activeDirection;
    private double timeInStateSeconds;

    public SimTransferZone(String id, TransferZone definition) {
        this.id = id;
        this.definition = definition;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        timeInStateSeconds += dtSeconds;
    }

    @Override
    public TransferZoneState getState() {
        return state;
    }

    public void transitionTo(TransferZoneState newState) {
        state = newState;
        timeInStateSeconds = 0.0;
    }

    public TransferZone getDefinition() {
        return definition;
    }

    public SimTote getReservedTote() {
        return reservedTote;
    }

    public void setReservedTote(SimTote reservedTote) {
        this.reservedTote = reservedTote;
    }

    public TransferDirection getActiveDirection() {
        return activeDirection;
    }

    public void setActiveDirection(TransferDirection activeDirection) {
        this.activeDirection = activeDirection;
    }

    public double getTimeInStateSeconds() {
        return timeInStateSeconds;
    }
}
```

```java
package online.davisfamily.threedee.sim.transfer;

public enum TransferDirection {
    LEFT,
    RIGHT
}
```

---

## Transfer events

```java
package online.davisfamily.threedee.sim.transfer.events;

import online.davisfamily.threedee.sim.SimulationEvent;

public record ToteApproachingTransferZoneEvent(
        String sourceId,
        double simulationTimeSeconds,
        String toteId,
        String transferZoneId,
        double distanceToZone
) implements SimulationEvent {
    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }
}
```

```java
package online.davisfamily.threedee.sim.transfer.events;

import online.davisfamily.threedee.sim.SimulationEvent;

public record ToteEnteredTransferZoneEvent(
        String sourceId,
        double simulationTimeSeconds,
        String toteId,
        String transferZoneId
) implements SimulationEvent {
    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }
}
```

---

## Approach sensor

This can be generic later, but a transfer-specific first version is perfectly fine.

```java
package online.davisfamily.threedee.sim.transfer;

import java.util.Collection;

import online.davisfamily.threedee.routing.RouteSegment;
import online.davisfamily.threedee.sim.SimulationContext;
import online.davisfamily.threedee.sim.SimulationSensor;
import online.davisfamily.threedee.sim.tote.SimTote;
import online.davisfamily.threedee.sim.transfer.events.ToteApproachingTransferZoneEvent;

public class TransferApproachSensor implements SimulationSensor {
    private final String id;
    private final SimTransferZone transferZone;
    private final RouteSegment monitoredSegment;
    private final double triggerDistance;
    private final Collection<SimTote> totes;

    public TransferApproachSensor(
            String id,
            SimTransferZone transferZone,
            RouteSegment monitoredSegment,
            double triggerDistance,
            Collection<SimTote> totes) {
        this.id = id;
        this.transferZone = transferZone;
        this.monitoredSegment = monitoredSegment;
        this.triggerDistance = triggerDistance;
        this.totes = totes;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        for (SimTote tote : totes) {
            if (tote.getRouteFollower().getCurrentSegment() != monitoredSegment) {
                continue;
            }

            double distanceToZone = transferZone.getDefinition().getStartDistance()
                    - tote.getRouteFollower().getDistanceAlongSegment();

            if (distanceToZone >= 0 && distanceToZone <= triggerDistance) {
                context.publish(new ToteApproachingTransferZoneEvent(
                        id,
                        context.getSimulationTimeSeconds(),
                        tote.getId(),
                        transferZone.getId(),
                        distanceToZone));
            }
        }
    }
}
```

### Note
You will probably want debounce/edge-trigger logic so the same tote does not repeatedly fire the same event every frame. That can be handled by tracking which tote IDs are already inside the sensor window.

---

## Decision strategy should no longer depend on RenderableObject

Current direction to move away from:
```java
boolean shouldTransfer(RouteSegment currentSegment,
                       TransferZone transferZone,
                       RenderableObject renderableObject)
```

Recommended replacement:
```java
package online.davisfamily.threedee.sim.transfer;

import online.davisfamily.threedee.sim.tote.SimTote;

public interface TransferDecisionStrategy {
    TransferDirection chooseDirection(SimTote tote, SimTransferZone transferZone);
}
```

If some cases are yes/no rather than left/right, return:
- `LEFT`
- `RIGHT`
- or `null` / `Optional.empty()` to mean "do not transfer"

Better still:

```java
package online.davisfamily.threedee.sim.transfer;

import java.util.Optional;

import online.davisfamily.threedee.sim.tote.SimTote;

public interface TransferDecisionStrategy {
    Optional<TransferDirection> decide(SimTote tote, SimTransferZone transferZone);
}
```

---

## TransferZoneController

This is where state transitions belong.

```java
package online.davisfamily.threedee.sim.transfer;

import java.util.Optional;

import online.davisfamily.threedee.sim.SimulationContext;
import online.davisfamily.threedee.sim.SimulationController;
import online.davisfamily.threedee.sim.SimulationEvent;
import online.davisfamily.threedee.sim.tote.SimTote;
import online.davisfamily.threedee.sim.tote.ToteInteractionMode;
import online.davisfamily.threedee.sim.transfer.events.ToteApproachingTransferZoneEvent;
import online.davisfamily.threedee.sim.transfer.events.ToteEnteredTransferZoneEvent;

public class TransferZoneController implements SimulationController {
    private final SimTransferZone zone;
    private final TransferDecisionStrategy decisionStrategy;
    private final ToteLookup toteLookup;

    public TransferZoneController(
            SimTransferZone zone,
            TransferDecisionStrategy decisionStrategy,
            ToteLookup toteLookup) {
        this.zone = zone;
        this.decisionStrategy = decisionStrategy;
        this.toteLookup = toteLookup;
    }

    @Override
    public void handleEvent(SimulationEvent event, SimulationContext context) {
        if (event instanceof ToteApproachingTransferZoneEvent approaching) {
            onApproach(approaching, context);
        } else if (event instanceof ToteEnteredTransferZoneEvent entered) {
            onEntered(entered, context);
        }
    }

    private void onApproach(ToteApproachingTransferZoneEvent event, SimulationContext context) {
        if (!zone.getId().equals(event.transferZoneId())) {
            return;
        }
        if (zone.getState() != TransferZoneState.IDLE) {
            return;
        }

        SimTote tote = toteLookup.getById(event.toteId());
        Optional<TransferDirection> decision = decisionStrategy.decide(tote, zone);
        if (decision.isEmpty()) {
            return;
        }

        zone.setReservedTote(tote);
        zone.setActiveDirection(decision.get());
        tote.setInteractionMode(ToteInteractionMode.RESERVED);
        tote.setControllingEquipmentId(zone.getId());

        zone.transitionTo(decision.get() == TransferDirection.LEFT
                ? TransferZoneState.READY_LEFT
                : TransferZoneState.READY_RIGHT);
    }

    private void onEntered(ToteEnteredTransferZoneEvent event, SimulationContext context) {
        if (!zone.getId().equals(event.transferZoneId())) {
            return;
        }

        SimTote tote = toteLookup.getById(event.toteId());
        if (zone.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.TRANSFERRING);
        zone.transitionTo(TransferZoneState.TRANSFERRING);
    }
}
```

```java
package online.davisfamily.threedee.sim.transfer;

import online.davisfamily.threedee.sim.tote.SimTote;

public interface ToteLookup {
    SimTote getById(String id);
}
```

---

## Behaviours become actuators

Your existing behaviour model is still useful, but it should increasingly read simulation state rather than decide workflow.

### Example: roller indicator behaviour

```java
package online.davisfamily.threedee.behaviour.transfer;

import online.davisfamily.threedee.Behaviour;
import online.davisfamily.threedee.RenderableObject;
import online.davisfamily.threedee.sim.transfer.SimTransferZone;
import online.davisfamily.threedee.sim.transfer.TransferDirection;
import online.davisfamily.threedee.sim.transfer.TransferZoneState;

public class RollerIndicatorBehaviour implements Behaviour {
    private final SimTransferZone zone;
    private final float idleAngle;
    private final float leftReadyAngle;
    private final float rightReadyAngle;
    private final float radiansPerSecond;

    public RollerIndicatorBehaviour(
            SimTransferZone zone,
            float idleAngle,
            float leftReadyAngle,
            float rightReadyAngle,
            float radiansPerSecond) {
        this.zone = zone;
        this.idleAngle = idleAngle;
        this.leftReadyAngle = leftReadyAngle;
        this.rightReadyAngle = rightReadyAngle;
        this.radiansPerSecond = radiansPerSecond;
    }

    @Override
    public void update(RenderableObject ro, float dtSeconds) {
        float targetAngle = idleAngle;

        if (zone.getState() == TransferZoneState.READY_LEFT
                || (zone.getState() == TransferZoneState.TRANSFERRING
                    && zone.getActiveDirection() == TransferDirection.LEFT)) {
            targetAngle = leftReadyAngle;
        } else if (zone.getState() == TransferZoneState.READY_RIGHT
                || (zone.getState() == TransferZoneState.TRANSFERRING
                    && zone.getActiveDirection() == TransferDirection.RIGHT)) {
            targetAngle = rightReadyAngle;
        }

        // Smoothly animate ro/transformation toward targetAngle using radiansPerSecond.
    }
}
```

This is exactly the kind of thing behaviours should own.

---

## Generic station pattern

Once transfer zones are refactored in this style, lid stations and tippers can follow the same shape.

### Generic station states
```java
package online.davisfamily.threedee.sim.station;

public enum StationCycleState {
    IDLE,
    CAPTURING,
    PROCESSING,
    RELEASING,
    RESETTING,
    BLOCKED
}
```

### Generic station base
```java
package online.davisfamily.threedee.sim.station;

import online.davisfamily.threedee.sim.StatefulSimObject;
import online.davisfamily.threedee.sim.SimulationContext;
import online.davisfamily.threedee.sim.tote.SimTote;

public abstract class AbstractStation implements StatefulSimObject<StationCycleState> {
    private final String id;
    private StationCycleState state = StationCycleState.IDLE;
    private SimTote currentTote;
    private double timeInStateSeconds;

    protected AbstractStation(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        timeInStateSeconds += dtSeconds;
    }

    @Override
    public StationCycleState getState() {
        return state;
    }

    public void transitionTo(StationCycleState newState) {
        state = newState;
        timeInStateSeconds = 0.0;
    }

    public SimTote getCurrentTote() {
        return currentTote;
    }

    public void setCurrentTote(SimTote currentTote) {
        this.currentTote = currentTote;
    }

    public double getTimeInStateSeconds() {
        return timeInStateSeconds;
    }
}
```

Concrete stations can then specialize:
- `LidOpeningStation`
- `TipperStation`
- `ScanningStation`
- `WeighingStation`

---

## How this generalizes beyond transfer zones

### Lid station
- approach sensor detects tote
- station controller reserves tote
- tote interaction mode changes to `STATION_HELD`
- station transitions: `CAPTURING -> PROCESSING -> RELEASING`
- lid animation behaviour reads station state

### Tipper
- tote arrives
- tipper captures tote
- packs are emitted as events or spawned simulation objects
- receiving conveyor controller responds to pack-added events
- tipper resets and releases tote

### Conveyor
- pack-added events increment count
- controller transitions from `LOADING` to `RUNNING` when criteria satisfied
- actuator/visual behaviour animates belt or rollers while running
- downstream-ready events gate the transfer

---

## Refactor sequence

### Stage 1 - decouple decisions from renderables
- Replace `RenderableObject` in transfer decision strategy signatures.
- Introduce `SimTote`.
- Add a small `ToteInteractionMode`.

### Stage 2 - extract route motion
- Move route progression logic from `GraphFollowerBehaviour` into `RouteFollower`.
- Let `SimTote` own a `RouteFollower`.
- Renderable becomes a visual mirror of simulation state.

### Stage 3 - introduce stateful transfer zones
- Add `SimTransferZone`.
- Keep existing `TransferZone` as definition/topology/config if that still fits.
- Move reservation/active-direction/current-tote state into `SimTransferZone`.

### Stage 4 - add sensors/events/controllers
- Add `SimulationContext`.
- Add transfer approach/enter events.
- Add `TransferApproachSensor`.
- Add `TransferZoneController`.

### Stage 5 - narrow Behaviour responsibilities
- Keep behaviours for animation and motion execution.
- Remove workflow decisions from behaviours where practical.

### Stage 6 - apply the same style to a second machine
Choose one:
- lid opener
- tipper
- conveyor accumulation section

If the same framework fits both a transfer zone and a station, the abstraction is probably sound.

---

## What to leave alone for now

Do not rewrite all of this at once:
- mesh generation
- render pipeline
- track-building geometry
- route topology classes unless they actively block this refactor

The highest-value change is to stop warehouse process logic being renderable-centric.

---

## Practical guidance for the current codebase

### Keep
- `RenderableObject`
- mesh/render infrastructure
- route segment topology
- transfer geometry math
- most existing visuals

### Shrink over time
- `GraphFollowerBehaviour`
- decision logic inside behaviours
- strategy methods that depend on `RenderableObject`

### Add
- `sim` package
- `sim.tote`
- `sim.transfer`
- `sim.station`
- `sim.events`
- maybe `sim.sensor`

Suggested package sketch:

```text
online.davisfamily.threedee
├── sim
│   ├── SimObject.java
│   ├── StatefulSimObject.java
│   ├── SimulationContext.java
│   ├── SimulationEvent.java
│   ├── SimulationController.java
│   ├── tote
│   │   ├── SimTote.java
│   │   └── ToteInteractionMode.java
│   ├── transfer
│   │   ├── SimTransferZone.java
│   │   ├── TransferZoneState.java
│   │   ├── TransferDirection.java
│   │   ├── TransferDecisionStrategy.java
│   │   ├── TransferZoneController.java
│   │   ├── ToteLookup.java
│   │   ├── TransferApproachSensor.java
│   │   └── events
│   │       ├── ToteApproachingTransferZoneEvent.java
│   │       └── ToteEnteredTransferZoneEvent.java
│   └── station
│       ├── AbstractStation.java
│       └── StationCycleState.java
├── routing
│   └── RouteFollower.java
└── behaviour
    └── transfer
        └── RollerIndicatorBehaviour.java
```

---

## Final recommendation

The most important conceptual change is this:

**renderables and behaviours should stop being the owners of warehouse workflow decisions.**

Let:
- simulation objects own state,
- sensors/events report facts,
- controllers perform transitions,
- behaviours animate the result.

That gives you a generic framework style that can support transfer zones, stations, tippers, conveyors, pack handling, and later fault/interlock logic without turning everything into one large tangle.
