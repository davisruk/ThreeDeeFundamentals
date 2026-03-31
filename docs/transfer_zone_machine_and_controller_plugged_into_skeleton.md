# TransferZoneMachine + Controller (Plugged into the New Skeleton)

This document builds the next layer on top of the framework/solution split that is already working for route following.

It focuses on:

- `TransferZoneMachine`
- `TransferZoneController`
- updated `TransferDecisionStrategy`
- how this plugs into `Tote` + `RouteFollowerSnapshot`
- how to handle transfer motion without pushing transfer logic back into `RouteFollower`

The goal here is not to solve every detail in one jump. The goal is to give transfer zones a proper runtime home so their state is no longer trapped inside movement behaviour.

---

## 1. Design goals

A transfer zone needs to support at least these concerns:

- it is defined against a route segment and interval
- it can decide whether an approaching tote should transfer
- it can reserve a tote before the tote reaches the transfer point
- it may prepare left or right
- it transitions through states
- it starts and completes a transfer
- it may drive roller indication and transfer motion
- it may later need interlocks, blocking, occupancy, and downstream readiness

That makes it a good candidate for a dedicated runtime machine.

---

## 2. Keep the existing static TransferZone as a definition

You already have a `TransferZone` concept. Keep that as a definition/config/topology object.

For example, conceptually it already represents things like:

- source segment
- start distance
- end distance
- target segment
- opening side(s)
- decision strategy
- transfer geometry information

That is useful and should remain.

What is missing is the runtime object.

---

## 3. Add TransferZoneMachine as the runtime state owner

This object owns the changing state.

```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.warehouse.tote.Tote;
import online.davisfamily.threedee.routing.TransferZone;

public class TransferZoneMachine {

    private final String id;
    private final TransferZone definition;

    private TransferZoneState state = TransferZoneState.IDLE;
    private Tote reservedTote;
    private TransferDirection activeDirection;
    private double timeInStateSeconds;

    public TransferZoneMachine(String id, TransferZone definition) {
        this.id = id;
        this.definition = definition;
    }

    public void update(double dtSeconds) {
        timeInStateSeconds += dtSeconds;
    }

    public void transitionTo(TransferZoneState newState) {
        this.state = newState;
        this.timeInStateSeconds = 0.0;
    }

    public void clearActiveTransfer() {
        this.reservedTote = null;
        this.activeDirection = null;
        transitionTo(TransferZoneState.IDLE);
    }

    public String getId() {
        return id;
    }

    public TransferZone getDefinition() {
        return definition;
    }

    public TransferZoneState getState() {
        return state;
    }

    public Tote getReservedTote() {
        return reservedTote;
    }

    public void setReservedTote(Tote reservedTote) {
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

---

## 4. Suggested states

Do not overcomplicate the first version. Start with a small explicit state model.

```java
package online.davisfamily.warehouse.transfer;

public enum TransferZoneState {
    IDLE,
    READY_LEFT,
    READY_RIGHT,
    TRANSFERRING,
    RESETTING,
    BLOCKED
}
```

This is enough to start.

### Why not add more states now?
You probably could, but there is more value in making the first runtime split work than in defining a perfect state machine immediately.

You can add:
- `ARMING`
- `WAITING_FOR_DOWNSTREAM`
- `OCCUPIED`
- `FAULTED`

later if needed.

---

## 5. TransferDirection

```java
package online.davisfamily.warehouse.transfer;

public enum TransferDirection {
    LEFT,
    RIGHT
}
```

This becomes the machine's explicit runtime direction.

---

## 6. Update TransferDecisionStrategy to use Tote + Machine

Your earlier observation still applies here:

- framework should not know warehouse types
- warehouse decision logic can use warehouse types

So the transfer decision strategy belongs in the warehouse layer and should use:
- `Tote`
- `TransferZoneMachine`

Recommended shape:

```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.warehouse.tote.Tote;

public interface TransferDecisionStrategy {
    Optional<TransferDirection> decide(Tote tote, TransferZoneMachine machine);
}
```

### Why Optional?
Because a transfer zone often needs three outcomes:
- transfer left
- transfer right
- do not transfer

`Optional<TransferDirection>` expresses that cleanly.

---

## 7. TransferZoneController

This is the class that moves the machine through its states.

```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.warehouse.tote.Tote;
import online.davisfamily.warehouse.tote.ToteInteractionMode;
import online.davisfamily.threedee.routing.RouteFollowerSnapshot;

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

        tote.setInteractionMode(ToteInteractionMode.RESERVED_FOR_TRANSFER);
        tote.setControllingTransferZone(machine);

        machine.transitionTo(decision.get() == TransferDirection.LEFT
                ? TransferZoneState.READY_LEFT
                : TransferZoneState.READY_RIGHT);
    }

    public void onToteEnteredTransferWindow(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.TRANSFERRING);
        machine.transitionTo(TransferZoneState.TRANSFERRING);
    }

    public void onTransferComplete(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.FREE);
        tote.setControllingTransferZone(null);

        machine.transitionTo(TransferZoneState.RESETTING);
    }

    public TransferZoneMachine getMachine() {
        return machine;
    }
}
```

---

## 8. Why the controller needs RouteFollowerSnapshot on approach

For now the controller may not use the snapshot much, but keeping it in the method signature is useful.

It gives you room later for:
- current segment validation
- precise distance checks
- predictive triggering
- debugging/logging

That is better than forcing the controller to reach back into the tote for everything.

---

## 9. Extend Tote minimally

The tote needs just enough state to participate in transfer ownership.

```java
package online.davisfamily.warehouse.tote;

import online.davisfamily.warehouse.transfer.TransferZoneMachine;

public enum ToteInteractionMode {
    FREE,
    RESERVED_FOR_TRANSFER,
    TRANSFERRING,
    HELD_AT_STATION,
    BLOCKED
}
```

Then tote gets one more field:

```java
private TransferZoneMachine controllingTransferZone;
```

with accessors:

```java
public TransferZoneMachine getControllingTransferZone() {
    return controllingTransferZone;
}

public void setControllingTransferZone(TransferZoneMachine controllingTransferZone) {
    this.controllingTransferZone = controllingTransferZone;
}
```

That gives transfer logic a clean owner link without reintroducing framework coupling.

---

## 10. How Tote plugs into the controller

For a first working version, tote can perform a local hand-off.

Inside `Tote.update(...)` you already do something like:

```java
RouteFollowerSnapshot snapshot = routeFollower.advance(dtSeconds, isBlocked());
applySnapshot(snapshot);
evaluateInteractions(context, snapshot);
```

For the transfer-specific stage, `evaluateInteractions(...)` can call a transfer coordinator.

### Transitional shape
```java
private final List<TransferZoneController> transferControllers;
```

Then:

```java
private void evaluateInteractions(SimulationContext context,
                                  RouteFollowerSnapshot snapshot) {

    for (TransferZoneController controller : transferControllers) {
        TransferZoneMachine machine = controller.getMachine();

        if (!isOnSourceSegment(machine, snapshot)) {
            continue;
        }

        double distance = snapshot.distanceAlongSegment();

        if (isInApproachWindow(machine, distance)) {
            controller.onToteApproaching(this, snapshot);
        }

        if (isInTransferWindow(machine, distance)) {
            controller.onToteEnteredTransferWindow(this);
        }
    }
}
```

This is still tote-local, which is not the final ideal, but it is a very practical next step because it keeps the moving parts small while you validate the split.

Later you can move these checks into dedicated sensors.

---

## 11. Suggested helper methods in Tote

```java
private boolean isOnSourceSegment(TransferZoneMachine machine,
                                  RouteFollowerSnapshot snapshot) {
    return snapshot.currentSegment() == machine.getDefinition().getSourceSegment();
}

private boolean isInApproachWindow(TransferZoneMachine machine,
                                   double distanceAlongSegment) {
    double start = machine.getDefinition().getStartDistance();
    double triggerDistance = 20.0; // example only; make configurable
    return distanceAlongSegment >= start - triggerDistance
            && distanceAlongSegment < start;
}

private boolean isInTransferWindow(TransferZoneMachine machine,
                                   double distanceAlongSegment) {
    double start = machine.getDefinition().getStartDistance();
    double end = machine.getDefinition().getEndDistance();
    return distanceAlongSegment >= start && distanceAlongSegment <= end;
}
```

### Important note
You will almost certainly want debounce/edge-trigger behaviour, otherwise the tote may repeatedly call `onToteApproaching(...)` or `onToteEnteredTransferWindow(...)`.

The simplest first solution is to rely on machine state:
- approach only matters when machine is `IDLE`
- entry only matters when tote is the reserved tote and state is ready

That may be enough initially.

---

## 12. Transfer motion should be separate from transfer decision/state

You said the transfer zones are complicated. That is exactly why this split matters.

There are two separate concerns:

### A. Control/state concern
- should the tote transfer?
- left or right?
- reserve tote
- zone becomes ready
- transfer begins
- transfer completes

### B. Motion concern
- how the tote moves laterally
- when yaw freezes
- whether orientation follows source or target geometry
- how interpolation behaves
- any visual representation of rollers

Do not put B back inside `RouteFollower`.

### Recommended first arrangement
- `TransferZoneMachine` + `TransferZoneController` own A
- tote-side temporary transfer motion code owns B

That gives you a safe migration path.

Later, B can move into something like:
- `TransferMotionExecutor`
- or `TransferMotionState`

---

## 13. First practical motion hand-off

A very workable first version is:

### When tote is FREE
Use:
```java
RouteFollowerSnapshot snapshot = routeFollower.advance(dtSeconds, false);
applySnapshot(snapshot);
```

### When tote is TRANSFERRING
Do not advance the route follower normally. Instead:
- keep the last route-following snapshot as the baseline
- apply transfer interpolation from that baseline
- once transfer completes:
  - set follower to target segment / entry position as needed
  - return tote to `FREE`
  - tell controller `onTransferComplete(...)`

This keeps route following generic, while letting transfer motion temporarily override normal route motion.

---

## 14. Minimal transfer motion state inside Tote

You may need a small temporary structure like:

```java
private TransferMotionState transferMotionState;
```

For example:

```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.threedee.Vec3;
import online.davisfamily.threedee.routing.RouteSegment;

public class TransferMotionState {
    private final TransferZoneMachine machine;
    private final Vec3 startPosition;
    private final RouteSegment targetSegment;
    private double elapsedSeconds;

    public TransferMotionState(TransferZoneMachine machine,
                               Vec3 startPosition,
                               RouteSegment targetSegment) {
        this.machine = machine;
        this.startPosition = startPosition;
        this.targetSegment = targetSegment;
    }

    public void update(double dtSeconds) {
        elapsedSeconds += dtSeconds;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public TransferZoneMachine getMachine() {
        return machine;
    }

    public Vec3 getStartPosition() {
        return startPosition;
    }

    public RouteSegment getTargetSegment() {
        return targetSegment;
    }
}
```

That is not mandatory for the first draft, but it gives the complicated transfer interpolation somewhere sensible to live.

---

## 15. Recommended minimal integration order

Because transfer zones are complicated, do this in layers.

### Phase 1 — runtime control only
Add:
- `TransferZoneMachine`
- `TransferZoneController`
- updated `TransferDecisionStrategy`

But for now do not fully migrate transfer motion.

Just prove that:
- tote can approach
- machine can reserve tote
- machine can become ready
- tote can enter transfering state

This alone is already valuable.

### Phase 2 — motion hook
Add minimal transfer motion override:
- if tote is `TRANSFERRING`, use transfer interpolation instead of route follower
- on completion, release back to normal motion

### Phase 3 — visuals
Drive roller visuals or deck visuals from machine state:
- `READY_LEFT`
- `READY_RIGHT`
- `TRANSFERRING`

That is where your existing renderable behaviours can still shine.

---

## 16. Suggested minimal class set for this stage

### Framework
- `RouteFollower`
- `RouteFollowerSnapshot`

### Warehouse
- `Tote`
- `ToteInteractionMode`
- `TransferDirection`
- `TransferZoneState`
- `TransferDecisionStrategy`
- `TransferZoneMachine`
- `TransferZoneController`

That is enough to make transfer zones feel like first-class runtime objects.

---

## 17. Example skeletons

### TransferZoneState
```java
package online.davisfamily.warehouse.transfer;

public enum TransferZoneState {
    IDLE,
    READY_LEFT,
    READY_RIGHT,
    TRANSFERRING,
    RESETTING,
    BLOCKED
}
```

### TransferDirection
```java
package online.davisfamily.warehouse.transfer;

public enum TransferDirection {
    LEFT,
    RIGHT
}
```

### TransferDecisionStrategy
```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.warehouse.tote.Tote;

public interface TransferDecisionStrategy {
    Optional<TransferDirection> decide(Tote tote, TransferZoneMachine machine);
}
```

### TransferZoneMachine
```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.threedee.routing.TransferZone;
import online.davisfamily.warehouse.tote.Tote;

public class TransferZoneMachine {

    private final String id;
    private final TransferZone definition;

    private TransferZoneState state = TransferZoneState.IDLE;
    private Tote reservedTote;
    private TransferDirection activeDirection;
    private double timeInStateSeconds;

    public TransferZoneMachine(String id, TransferZone definition) {
        this.id = id;
        this.definition = definition;
    }

    public void update(double dtSeconds) {
        timeInStateSeconds += dtSeconds;
    }

    public void transitionTo(TransferZoneState newState) {
        this.state = newState;
        this.timeInStateSeconds = 0.0;
    }

    public void clearActiveTransfer() {
        reservedTote = null;
        activeDirection = null;
        transitionTo(TransferZoneState.IDLE);
    }

    public String getId() {
        return id;
    }

    public TransferZone getDefinition() {
        return definition;
    }

    public TransferZoneState getState() {
        return state;
    }

    public Tote getReservedTote() {
        return reservedTote;
    }

    public void setReservedTote(Tote reservedTote) {
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

### TransferZoneController
```java
package online.davisfamily.warehouse.transfer;

import java.util.Optional;

import online.davisfamily.threedee.routing.RouteFollowerSnapshot;
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

        tote.setInteractionMode(ToteInteractionMode.RESERVED_FOR_TRANSFER);
        tote.setControllingTransferZone(machine);

        machine.transitionTo(decision.get() == TransferDirection.LEFT
                ? TransferZoneState.READY_LEFT
                : TransferZoneState.READY_RIGHT);
    }

    public void onToteEnteredTransferWindow(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.TRANSFERRING);
        machine.transitionTo(TransferZoneState.TRANSFERRING);
    }

    public void onTransferComplete(Tote tote) {
        if (machine.getReservedTote() != tote) {
            return;
        }

        tote.setInteractionMode(ToteInteractionMode.FREE);
        tote.setControllingTransferZone(null);

        machine.transitionTo(TransferZoneState.RESETTING);
    }

    public TransferZoneMachine getMachine() {
        return machine;
    }
}
```

---

## 18. Where this leaves GraphFollowerBehaviour

At this point it should still remain thin.

It should not know about:
- transfer strategy
- transfer state machine rules
- reservation logic

At most it delegates to:
- `Tote.update(...)`

That is a major improvement over the old model.

---

## 19. Next step after this file

Once this stage is in place, the most useful next file would be one of these:

### Option A
**“Transfer motion integration”**
How to migrate the actual lateral movement and yaw-freeze logic out of the old behaviour.

### Option B
**“Transfer zone sensing/debounce”**
How to move approach/entry detection out of tote-local checks into dedicated sensor classes.

Because you said transfer zones are complicated, I would probably do Option A next, since motion is where a lot of the hidden complexity usually lives.

---

## Final takeaway

The important shift here is:

- transfer zone definition remains static
- transfer zone runtime state gets its own machine
- decisions and transitions move into a controller
- tote becomes the moving warehouse object that participates in the interaction
- route following stays generic and untouched

That gives the complicated transfer logic a proper home without polluting the framework layer.
