# RouteFollowerSnapshot Design

This document defines a clean, framework-level `RouteFollowerSnapshot` that keeps
the routing system fully generic and independent of any warehouse-specific concepts.

---

## 1. Purpose

`RouteFollowerSnapshot` represents the **result of advancing along a route** for a single update step.

It should:
- contain all data needed to position/orient an object
- be completely independent of solution/domain concepts (no Tote, no TransferZone)
- be lightweight and immutable
- be easy to extend if needed later

---

## 2. Design principles

### 1. Pure data carrier
No logic. Just state produced by `RouteFollower`.

### 2. Framework-only types
Only depends on:
- `RouteSegment`
- vector/matrix types (`Vec3`, etc.)

### 3. No behaviour or callbacks
The snapshot should not “do” anything — the caller decides how to use it.

---

## 3. Proposed implementation

```java
package online.davisfamily.threedee.routing;

import online.davisfamily.threedee.Vec3;

public record RouteFollowerSnapshot(

        // Where we are in the route
        RouteSegment currentSegment,
        double distanceAlongSegment,

        // World-space transform data
        Vec3 position,
        Vec3 forward,
        Vec3 up,

        // Optional: derived values (can help avoid recomputation)
        double segmentLength,
        double remainingDistanceOnSegment

) {
}
```

---

## 4. Notes on fields

### currentSegment
- Needed by solution layer to detect:
  - transfer zones
  - stations
  - segment transitions
- Keeps routing context available

---

### distanceAlongSegment
- Core state of movement
- Used for:
  - sensor triggering
  - interaction thresholds
  - debugging

---

### position / forward / up

These define a full orientation basis:

- `position` → world location
- `forward` → direction of travel
- `up` → orientation reference (important for banking / slopes later)

You can derive:
- right vector via cross product if needed

---

### segmentLength
Optional but useful:
- avoids repeated calls into segment
- useful for UI/debug

---

### remainingDistanceOnSegment
Useful for:
- predictive logic (approach sensors)
- avoiding repeated subtraction everywhere

---

## 5. What is intentionally NOT included

### No velocity
Velocity is a property of the follower, not the snapshot.

### No timestamps
Time belongs in the simulation context, not per snapshot.

### No domain data
No:
- Tote
- TransferZone
- Station
- Behaviour references

---

## 6. RouteFollower API

Recommended shape:

```java
public class RouteFollower {

    public RouteFollowerSnapshot advance(double dtSeconds, boolean blocked) {
        if (!blocked) {
            advanceDistance(dtSeconds);
        }

        return buildSnapshot();
    }

    private void advanceDistance(double dtSeconds) {
        // move along segment
        // handle segment transitions
    }

    private RouteFollowerSnapshot buildSnapshot() {
        // compute position + orientation from segment
    }
}
```

---

## 7. Usage from solution layer (Tote example)

```java
RouteFollowerSnapshot snapshot =
        routeFollower.advance(dtSeconds, isBlocked());

applySnapshot(snapshot);
```

```java
private void applySnapshot(RouteFollowerSnapshot snapshot) {
    transformation.setPosition(snapshot.position());

    // derive rotation from forward/up
}
```

---

## 8. Where interaction logic lives

Important separation:

### RouteFollowerSnapshot → framework
- purely describes motion result

### Tote / simulation object → solution
- decides:
  - isBlocked()
  - when to stop
  - when to transfer
  - when to publish events

---

## 9. Extensibility

If you later need more data, extend safely:

Examples:
- tangent curvature
- previous segment reference
- normalized progress (0..1)
- path ID / route ID

Because it's a record, extension is straightforward.

---

## 10. Debugging advantages

This design makes debugging much easier:

You can log:
- segment
- distance
- position
- direction

without needing access to domain objects.

---

## 11. Summary

`RouteFollowerSnapshot` becomes the clean contract between:

- framework (movement system)
- solution (warehouse logic)

It ensures:
- no coupling from framework → warehouse
- simple, testable movement logic
- flexible higher-level behaviours

---

## Final takeaway

**The snapshot is the boundary.**

Everything below it is framework.
Everything above it is solution.

That keeps the architecture clean as complexity grows.
