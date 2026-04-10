# Minimal Java Skeleton (Framework vs Warehouse Split)

This file provides a **concrete, minimal skeleton** for the key classes discussed:

- RouteFollower (framework)
- RouteFollowerSnapshot (framework)
- Tote (warehouse)
- ToteInteractionMode (warehouse)
- GraphFollowerBehaviour (bridge)

This is intentionally minimal and designed to be dropped into your project and evolved.

---

# 1. FRAMEWORK LAYER

## RouteFollowerSnapshot

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

---

## RouteFollower

```java
package online.davisfamily.threedee.routing;

public class RouteFollower {

    private RouteSegment currentSegment;
    private double distanceAlongSegment;
    private double speedUnitsPerSecond;

    public RouteFollower(RouteSegment startSegment,
                         double startDistance,
                         double speedUnitsPerSecond) {
        this.currentSegment = startSegment;
        this.distanceAlongSegment = startDistance;
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }

    public RouteFollowerSnapshot advance(double dtSeconds, boolean blocked) {

        if (!blocked) {
            advanceDistance(dtSeconds);
        }

        return buildSnapshot();
    }

    private void advanceDistance(double dtSeconds) {
        double distanceToMove = speedUnitsPerSecond * dtSeconds;

        while (distanceToMove > 0 && currentSegment != null) {

            double remaining = currentSegment.length() - distanceAlongSegment;

            if (distanceToMove < remaining) {
                distanceAlongSegment += distanceToMove;
                break;
            }

            // move to end of segment
            distanceAlongSegment = currentSegment.length();
            distanceToMove -= remaining;

            // transition to next segment
            RouteConnection next = currentSegment.getNext(); // adapt to your model
            if (next == null) {
                break; // end of route
            }

            currentSegment = next.getTarget();
            distanceAlongSegment = 0.0;
        }
    }

    private RouteFollowerSnapshot buildSnapshot() {

        var pos = currentSegment.samplePosition(distanceAlongSegment);
        var forward = currentSegment.sampleForward(distanceAlongSegment);
        var up = currentSegment.sampleUp(distanceAlongSegment);

        double length = currentSegment.length();
        double remaining = length - distanceAlongSegment;

        return new RouteFollowerSnapshot(
                currentSegment,
                distanceAlongSegment,
                pos,
                forward,
                up,
                length,
                remaining
        );
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

---

# 2. WAREHOUSE LAYER

## ToteInteractionMode

```java
package online.davisfamily.warehouse.tote;

public enum ToteInteractionMode {
    FREE,
    RESERVED_FOR_TRANSFER,
    TRANSFERRING,
    HELD_AT_STATION,
    BLOCKED
}
```

---

## Tote

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

    public Tote(String id,
                ObjectTransformation transformation,
                RouteFollower routeFollower) {
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
                routeFollower.advance(dtSeconds, isBlocked());

        applySnapshot(snapshot);

        // TEMP: local interaction hook (can move to sensors/controllers later)
        evaluateInteractions(context, snapshot);
    }

    private boolean isBlocked() {
        return interactionMode != ToteInteractionMode.FREE;
    }

    private void applySnapshot(RouteFollowerSnapshot snapshot) {

        transformation.position.set(snapshot.position());

        // TODO: derive rotation from forward/up
        // e.g. build basis matrix or quaternion
    }

    private void evaluateInteractions(SimulationContext context,
                                      RouteFollowerSnapshot snapshot) {

        // This is a TEMPORARY place for:
        // - transfer detection
        // - station detection
        // - event publishing

        // Eventually move this to sensors/controllers.
    }

    public void setInteractionMode(ToteInteractionMode mode) {
        this.interactionMode = mode;
    }

    public ToteInteractionMode getInteractionMode() {
        return interactionMode;
    }

    public ObjectTransformation getTransformation() {
        return transformation;
    }

    public RouteFollower getRouteFollower() {
        return routeFollower;
    }
}
```

---

# 3. BRIDGE LAYER

## GraphFollowerBehaviour (Transitional)

```java
package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.Behaviour;
import online.davisfamily.threedee.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.tote.Tote;

public class GraphFollowerBehaviour implements Behaviour {

    private final Tote tote;
    private final SimulationContext context;

    public GraphFollowerBehaviour(Tote tote, SimulationContext context) {
        this.tote = tote;
        this.context = context;
    }

    @Override
    public void update(RenderableObject ro, float dtSeconds) {

        tote.update(context, dtSeconds);

        // Ensure renderable uses tote transform
        ro.transformation.set(tote.getTransformation());
    }
}
```

---

# 4. OPTIONAL: SimulationContext (Minimal)

```java
package online.davisfamily.threedee.sim.framework;

public class SimulationContext {

    private double simulationTimeSeconds;

    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }

    public void setSimulationTimeSeconds(double simulationTimeSeconds) {
        this.simulationTimeSeconds = simulationTimeSeconds;
    }
}
```

---

# 5. WHAT THIS GIVES YOU

After introducing just this skeleton:

- Route following is completely framework-owned
- Tote owns warehouse interaction state
- GraphFollowerBehaviour becomes a thin adapter
- You now have a clean insertion point for:
  - transfer controllers
  - station controllers
  - sensors/events

---

# 6. NEXT STEP

Next logical addition:

- TransferZoneMachine
- TransferZoneController
- TransferDecisionStrategy (updated to use Tote)

This can be layered in without touching RouteFollower again.

---

# Final takeaway

This skeleton establishes the most important boundary:

**RouteFollower is framework.  
Tote is solution.  
GraphFollowerBehaviour is just glue.**

Once that boundary exists, the rest of the system becomes much easier to evolve.
