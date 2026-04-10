# GraphFollowerBehaviour Split Plan (Framework vs Solution Aware)

This document revises the earlier sketch to preserve a clean separation between:

- the **generic 3D/simulation framework**
- the **warehouse solution built on top of that framework**

You were right to call this out: `RouteFollower` should not know about `SimTote`, `TransferZone`, or any other warehouse-specific concept.

---

## 1. Core principle

The framework should provide reusable primitives such as:

- scene objects
- behaviours
- transforms
- path/route following
- generic simulation objects
- generic event/state support

The warehouse layer should provide domain-specific types such as:

- tote
- pack
- transfer zone
- lid station
- tipper
- conveyor section
- warehouse controllers
- warehouse events

So the dependency direction should be:

**framework -> no knowledge of warehouse**
**warehouse solution -> depends on framework**

Never the other way round.

---

## 2. Revised layering

## Framework layer
Examples:
- `RenderableObject`
- `Mesh`
- `ObjectTransformation`
- `Behaviour`
- `PathSegment3`
- `RouteSegment`
- `RouteConnection`
- `RouteFollower`
- `SimulationObject`
- `StatefulObject<S>`
- generic event support
- generic sensor support
- generic state machine support

## Warehouse solution layer
Examples:
- `Tote`
- `Pack`
- `TransferZoneMachine`
- `LidOpeningStation`
- `TipperStation`
- `PackConveyor`
- `WarehouseController`
- warehouse-specific events and strategies

This is the right split.

---

## 3. Yes, your observation makes sense

This part is exactly right:

> RouteFollower seems to me a generic 3D framework class, whereas SimTote is a solution class that uses the framework

Agreed.

And this follows naturally:

> it should not be referenced by RouteFollower

Agreed again.

That means the earlier sketch should be corrected so that `RouteFollower` is generic.

---

## 4. Revised framework abstraction

Instead of this:

```java
public class RouteFollower {
    public void update(SimulationContext context, double dtSeconds, SimTote tote) {
        ...
    }
}
```

prefer something like this:

```java
public class RouteFollower {
    public void update(SimulationContext context, double dtSeconds, MobileSimObject object) {
        ...
    }
}
```

or even:

```java
public class RouteFollower {
    public void update(SimulationContext context, double dtSeconds, RouteFollowHost host) {
        ...
    }
}
```

where `RouteFollowHost` is a small framework interface.

That preserves reuse.

---

## 5. Recommended framework interfaces

I would introduce a few very small framework-side interfaces so the route/path system can remain generic.

### Generic simulation object

```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationObject {
    String getId();
    void update(SimulationContext context, double dtSeconds);
}
```

### Optional stateful marker

```java
package online.davisfamily.threedee.sim.framework;

public interface StatefulObject<S extends Enum<S>> extends SimulationObject {
    S getState();
}
```

### Generic transform host
If you want route following to drive an object's pose:

```java
package online.davisfamily.threedee.sim.framework;

import online.davisfamily.threedee.ObjectTransformation;

public interface TransformHost {
    ObjectTransformation getTransformation();
}
```

### Generic route-follow host
This is the most useful option.

```java
package online.davisfamily.threedee.sim.framework;

public interface RouteFollowHost extends SimulationObject, TransformHost {
    boolean isRouteMotionBlocked();
    void onRouteFollowerUpdated(RouteFollowerSnapshot snapshot);
}
```

This lets `RouteFollower` remain generic while still giving the host object a hook.

---

## 6. RouteFollower should depend on abstractions, not solution types

### Good
```java
public class RouteFollower {
    public void update(SimulationContext context, double dtSeconds, RouteFollowHost host) {
        ...
    }
}
```

### Not ideal
```java
public void update(..., SimTote tote)
```

### Also reasonable
If you want even less coupling, `RouteFollower` can return a value rather than push changes into the host:

```java
public class RouteFollower {
    public RouteFollowerSnapshot update(SimulationContext context, double dtSeconds, boolean motionBlocked) {
        ...
    }
}
```

Then the caller applies the result.

This is often even cleaner.

---

## 7. A strong alternative: RouteFollower as pure state + result producer

You may prefer this shape:

```java
package online.davisfamily.threedee.routing;

public class RouteFollower {
    private RouteSegment currentSegment;
    private double distanceAlongSegment;
    private double speedUnitsPerSecond;

    public RouteFollowerSnapshot advance(double dtSeconds, boolean blocked) {
        // Move internal state if not blocked
        // Compute world pose/orientation
        // Return a generic snapshot
    }
}
```

with:

```java
package online.davisfamily.threedee.routing;

import online.davisfamily.threedee.Vec3;

public record RouteFollowerSnapshot(
        RouteSegment currentSegment,
        double distanceAlongSegment,
        Vec3 position,
        Vec3 forward,
        Vec3 up
) {
}
```

Then a warehouse `Tote` can do:

```java
RouteFollowerSnapshot snapshot = routeFollower.advance(dtSeconds, isMotionBlocked());
applySnapshotToTransformation(snapshot);
```

This keeps `RouteFollower` completely free of warehouse concepts.

I think this is probably the cleanest design.

---

## 8. Where events belong

The framework can provide **generic event infrastructure**, but warehouse event types belong in the warehouse layer.

### Framework event infrastructure
```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationEvent {
    String getSourceId();
    double getSimulationTimeSeconds();
}
```

```java
package online.davisfamily.threedee.sim.framework;

public interface EventPublisher {
    void publish(SimulationEvent event);
}
```

### Warehouse events
```java
package online.davisfamily.threedee.sim.warehouse.events;

public record ToteApproachingTransferZoneEvent(...) implements SimulationEvent { }
```

That way:
- framework provides the mechanism
- solution provides the vocabulary

This is the correct split.

---

## 9. Where sensors belong

Same idea.

### Framework can provide
- generic sensor base interface
- generic region/threshold/occupancy helpers

### Warehouse layer can provide
- transfer approach sensor
- station arrival sensor
- pack count sensor
- downstream-ready sensor

For example:

### Framework
```java
package online.davisfamily.threedee.sim.framework;

public interface Sensor extends SimulationObject {
}
```

### Warehouse
```java
package online.davisfamily.threedee.sim.warehouse.transfer;

public class TransferApproachSensor implements Sensor {
    ...
}
```

That separation is healthy.

---

## 10. How to treat Tote

A tote is not a framework object in the domain sense, but it can still implement framework interfaces.

For example:

```java
package online.davisfamily.threedee.sim.warehouse.tote;

import online.davisfamily.threedee.ObjectTransformation;
import online.davisfamily.threedee.routing.RouteFollower;
import online.davisfamily.threedee.routing.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.RouteFollowHost;
import online.davisfamily.threedee.sim.framework.SimulationContext;

public class Tote implements RouteFollowHost {
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
        routeFollower.update(context, dtSeconds, this);
    }

    @Override
    public ObjectTransformation getTransformation() {
        return transformation;
    }

    @Override
    public boolean isRouteMotionBlocked() {
        return interactionMode != ToteInteractionMode.FREE;
    }

    @Override
    public void onRouteFollowerUpdated(RouteFollowerSnapshot snapshot) {
        // apply position/orientation to transformation
    }
}
```

This is fine because:
- `Tote` depends on framework
- `RouteFollower` still depends only on framework abstractions

The dependency direction remains correct.

---

## 11. Even cleaner: let Tote use RouteFollower without RouteFollower knowing Tote at all

This may be preferable:

```java
package online.davisfamily.threedee.sim.warehouse.tote;

public class Tote implements SimulationObject, TransformHost {
    private final RouteFollower routeFollower;

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        RouteFollowerSnapshot snapshot =
                routeFollower.advance(dtSeconds, isRouteMotionBlocked());
        applySnapshot(snapshot);
    }
}
```

This is my preferred option because it reduces framework coupling even further.

### Why this is strong
- `RouteFollower` becomes a pure reusable service
- the warehouse object decides how to use the route result
- easier to unit test
- easier to reuse for non-warehouse objects

---

## 12. Revised view of GraphFollowerBehaviour

Right now `GraphFollowerBehaviour` blends:
- generic route motion
- transfer-specific warehouse rules
- interaction state
- pose/orientation control

That should split into:

### Framework side
- `RouteFollower`
- `RouteFollowerSnapshot`
- perhaps `RouteMotionPolicy` or `MovementConstraint` if needed

### Warehouse side
- `Tote`
- `ToteInteractionMode`
- `TransferZoneMachine`
- `TransferZoneController`
- station controllers
- warehouse events/sensors

### Optional visual side
- a tiny behaviour that mirrors solution state into a renderable, if you still want renderable-attached updates

But the important point is:
**route following becomes generic framework code**

---

## 13. Suggested framework package layout

```text
online.davisfamily.threedee
├── behaviour
├── geometry
├── rendering
├── routing
│   ├── PathSegment3.java
│   ├── RouteSegment.java
│   ├── RouteConnection.java
│   ├── RouteFollower.java
│   └── RouteFollowerSnapshot.java
└── sim
    └── framework
        ├── SimulationObject.java
        ├── StatefulObject.java
        ├── SimulationContext.java
        ├── SimulationEvent.java
        ├── EventPublisher.java
        ├── Sensor.java
        ├── TransformHost.java
        └── RouteFollowHost.java
```

## Warehouse solution package layout

```text
online.davisfamily.warehouse
├── tote
│   ├── Tote.java
│   └── ToteInteractionMode.java
├── transfer
│   ├── TransferZoneMachine.java
│   ├── TransferZoneState.java
│   ├── TransferDirection.java
│   ├── TransferZoneController.java
│   ├── TransferDecisionStrategy.java
│   └── TransferApproachSensor.java
├── station
│   ├── AbstractStation.java
│   ├── LidOpeningStation.java
│   ├── TipperStation.java
│   └── StationState.java
└── events
    ├── ToteApproachingTransferZoneEvent.java
    └── ToteEnteredStationEvent.java
```

If you prefer to keep everything under one root package, the conceptual split still matters even if the packages are adjacent.

---

## 14. Refactor direction for GraphFollowerBehaviour

Here is the revised extraction plan.

### Step 1
Extract generic movement state and math into `RouteFollower`.

Move:
- current segment
- distance along segment
- segment transition handling
- route progression
- pose/orientation sampling

Keep warehouse logic out.

### Step 2
Create `RouteFollowerSnapshot`.

This returns:
- segment
- distance
- position
- orientation basis or yaw/pitch data

### Step 3
Create warehouse `Tote`.

Tote owns:
- route follower
- interaction mode
- warehouse-specific state
- any reservation/controller links

### Step 4
Move transfer-specific logic out of the follower path.

Move:
- transfer reservation
- should-transfer decisions
- transfer state
- stop/release logic
- zone controller coordination

into warehouse controllers/machines.

### Step 5
Optionally keep a tiny bridging behaviour.

If your engine update loop still expects renderable behaviours, create something minimal like:

```java
public class SimulationObjectBehaviour implements Behaviour {
    private final SimulationObject simulationObject;

    @Override
    public void update(RenderableObject ro, float dtSeconds) {
        simulationObject.update(...);
    }
}
```

That is only a bridge, not the home of business logic.

---

## 15. What should stay framework-generic

These concepts feel framework-level:
- route/path following
- transforms
- renderables
- generic simulation object lifecycle
- generic state machine support
- generic event infrastructure
- generic sensors
- timers/time-in-state helpers

These concepts feel warehouse-specific:
- tote
- pack
- transfer zone
- lid station
- tipper
- conveyor accumulation
- warehouse-specific sensors
- warehouse-specific events
- routing/decision policies based on warehouse semantics

That is the clean boundary.

---

## 16. Recommendation

Yes, your correction is important and I would adopt it.

### Best version
Make `RouteFollower` a generic framework component that:
- owns route-following state
- advances independently of warehouse types
- exposes generic movement results via a snapshot or small host interface

Then let warehouse objects such as `Tote` consume that framework service.

If choosing between the two generic styles, I would slightly prefer this one:

```java
RouteFollowerSnapshot snapshot = routeFollower.advance(dtSeconds, blocked);
```

over this:

```java
routeFollower.update(context, dtSeconds, host);
```

because it keeps `RouteFollower` even more self-contained and reusable.

---

## 17. Final distilled rule

**Framework classes should depend only on framework abstractions.**
**Solution classes may implement or compose those abstractions, but framework code should never reference warehouse domain types.**

That is the right boundary, and it will keep the architecture much cleaner as both the engine and the warehouse solution evolve.
