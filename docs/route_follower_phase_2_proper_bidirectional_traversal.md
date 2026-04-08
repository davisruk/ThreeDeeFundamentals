# RouteFollower Phase 2: Proper Bidirectional Traversal with CLAMP / LOOP / PING_PONG

This note defines the next corrective step for the architecture:

## Goal
Restore proper route traversal semantics inside `RouteFollower`, including:

- forward traversal
- reverse traversal
- `CLAMP`
- `LOOP`
- `PING_PONG`

This should happen before further transfer-motion refinement, because transfer behavior depends on the follower having a correct concept of travel direction.

---

## 1. Why this is the right next step

The recent yaw problems are not really just “yaw bugs”.

They are symptoms of a deeper issue:

- the old system had a real concept of traversal direction
- the new `RouteFollower` currently behaves mostly as forward-only traversal
- snapshots therefore do not consistently represent the actual direction of travel

This causes problems such as:
- follower yaw flipping unexpectedly
- reverse travel semantics being lost
- curves behaving incorrectly under attempted reverse traversal
- transfer/link handoff logic fighting the follower

So the next step is to fix the follower itself.

---

## 2. Core principle

`RouteFollower` should own **movement semantics**, not just position sampling.

That means it must explicitly know:

- current segment
- distance along segment
- current travel direction
- wrap/end behavior

and must advance correctly in both forward and reverse directions.

---

## 3. Required enums

### TravelDirection
```java
public enum TravelDirection {
    FORWARD,
    REVERSE
}
```

### WrapMode
```java
public enum WrapMode {
    CLAMP,
    LOOP,
    PING_PONG
}
```

---

## 4. What TravelDirection must mean

It must affect both:

### A. Movement
- whether `distanceAlongSegment` increases or decreases

### B. Orientation
- what `snapshot.forward()` means

So `TravelDirection` is not just a yaw correction flag.
It is a true traversal state.

---

## 5. What `distanceAlongSegment` should mean

Keep `distanceAlongSegment` as:

> distance measured from the segment’s native start

That is a good stable representation.

Then `TravelDirection` determines whether movement:

- increases that distance (`FORWARD`)
- decreases that distance (`REVERSE`)

This is better than redefining distance semantics.

---

## 6. RouteFollower responsibilities

A more complete `RouteFollower` should own:

```java
private RouteSegment currentSegment;
private float distanceAlongSegment;
private float speedUnitsPerSecond;
private TravelDirection travelDirection;
private WrapMode wrapMode;
```

### Optional later additions
- route graph / route owner
- connection selection policy
- pause/hold state
- route id

But not needed for this phase.

---

## 7. Advance model

Movement should now be based on a **signed movement amount**.

### Concept
```java
float movement = speedUnitsPerSecond * dtSeconds;
```

Then:

- if `FORWARD`, movement pushes distance toward segment end
- if `REVERSE`, movement pushes distance toward segment start

That is the key difference.

---

## 8. Forward traversal logic

### FORWARD on a segment
- if remaining movement fits within segment:
  - `distanceAlongSegment += movement`
- if it reaches/passes segment end:
  - consume distance to end
  - transition via outgoing connection or wrap behavior
  - continue with leftover distance

This is your existing conceptual model, just cleaned up.

---

## 9. Reverse traversal logic

### REVERSE on a segment
- if remaining movement fits before segment start:
  - `distanceAlongSegment -= movement`
- if it reaches/passes segment start:
  - consume distance to start
  - transition via **incoming/reverse connection** or wrap behavior
  - continue with leftover distance

This is the missing half that the architecture now needs.

---

## 10. Important architectural implication

For reverse traversal to work, the route graph needs a concept of what happens when you leave a segment at its **start**, not just at its end.

That means one of these must be true:

### Option A — each segment can answer both
- next connection(s) from the end
- previous connection(s) from the start

### Option B — the route graph can look up reverse links externally

Either is fine, but the follower must be able to find the correct reverse transition.

If your current model only supports “next from end”, reverse traversal will never be complete.

---

## 11. Suggested connection model

A clean conceptual model is:

```java
class RouteSegment {
    List<RouteConnection> outgoingConnections; // from end
    List<RouteConnection> incomingConnections; // to start
}
```

or methods such as:

```java
RouteConnection getForwardConnection();
RouteConnection getReverseConnection();
```

For Phase 2, even a single connection in each direction is enough if that matches your current graph.

---

## 12. Reverse entry distance semantics

When moving into a target segment in reverse traversal, `entryDistance` still needs to be meaningful.

The simplest rule is:

- `entryDistance` always means “distance from that segment’s native start”

So on reverse handoff, the follower should set:

```java
distanceAlongSegment = reverseConnection.getEntryDistance();
```

and continue moving in `REVERSE`.

That keeps the representation consistent.

---

## 13. Snapshot semantics

`RouteFollowerSnapshot.forward()` should mean:

> actual travel direction along the route at this point

So the follower must build it consistently.

### Example
```java
Vec3 tangent = geometry.sampleOrientationDirectionByDistance(distanceAlongSegment);

if (travelDirection == TravelDirection.REVERSE) {
    tangent = tangent.scale(-1f);
}
```

That rule must apply for:
- linear segments
- bezier segments
- any future segment type

### Important
Do not special-case only bezier segments.
That is what led to the recent mismatch.

---

## 14. About `up`

Do not flip `up` unless you have a very specific reason.

Normally:
- `forward` may reverse
- `up` should usually stay the same

Flipping `up` will often produce incorrect frames/orientation behavior.

So the correct default is:

- reverse `forward`
- do **not** reverse `up`

That is important.

---

## 15. WrapMode behavior

### CLAMP
- at forward end: stop at segment end
- at reverse start: stop at segment start

No wrap, no direction change.

### LOOP
- at forward end: wrap to route start / next loop start
- at reverse start: wrap to route end / previous loop end

Travel direction stays the same.

### PING_PONG
- at forward end: clamp to end, then switch to `REVERSE`
- at reverse start: clamp to start, then switch to `FORWARD`

No special yaw hacks needed.
The snapshot will naturally reflect the new direction.

---

## 16. Suggested advance algorithm

Conceptually:

```java
advance(dt):
    remaining = speed * dt

    while remaining > 0:
        if travelDirection == FORWARD:
            distanceToBoundary = currentSegment.length() - distanceAlongSegment
        else:
            distanceToBoundary = distanceAlongSegment

        if remaining < distanceToBoundary:
            apply movement within current segment
            remaining = 0
        else:
            move to boundary
            remaining -= distanceToBoundary
            transitionAcrossBoundaryOrApplyWrapMode()
```

The key is:
- forward boundary = segment end
- reverse boundary = segment start

---

## 17. Segment transitions in reverse

This is the biggest conceptual gap in the current architecture and must be addressed explicitly.

When travelling in reverse and you hit the segment start, you need:

- a reverse connection
- or wrap logic
- or clamp
- or ping-pong reversal

If the graph does not yet support reverse connections, you should add that now before trying to make reverse traversal “work”.

Otherwise all fixes will remain partial.

---

## 18. Transfer-link problem revisited

Once proper reverse traversal exists, the tote’s orientation on the final segment should no longer need ad hoc flip logic if the actual travel direction on that segment is genuinely `REVERSE`.

That is the important point.

So Phase 2 is not just restoring an old feature.
It is also the clean fix for the recent link-exit yaw problems.

---

## 19. Tote/applySnapshot after this phase

Once `RouteFollowerSnapshot.forward()` always reflects the true direction of travel, `Tote.applySnapshot(...)` can stay simple:

```java
float yaw;
if (yawOverrideActive) {
    yaw = overriddenYawRadians;
} else {
    yaw = Vec3.yawFromDirection(snapshot.forward()) + yawOffsetRadians;
}
transformation.setAxisRotation(Axis.Y, yaw);
```

That is exactly what you want:
- follower handles true directional semantics
- tote only applies policy overrides when necessary

---

## 20. Recommended implementation order

### Step 1
Restore `TravelDirection` as a real `RouteFollower` field

### Step 2
Make `advance(...)` use signed movement:
- forward increases distance
- reverse decreases distance

### Step 3
Make `buildSnapshot()` flip `forward` for reverse traversal on **all segment types**

### Step 4
Do not flip `up`

### Step 5
Add reverse segment transition support:
- reverse connections or equivalent graph lookup

### Step 6
Restore `WrapMode` behavior:
- `CLAMP`
- `LOOP`
- `PING_PONG`

### Step 7
Retest:
- linear forward
- linear reverse
- bezier forward
- bezier reverse
- loop and ping-pong
- transfer entry/exit with links

That order should keep the work manageable.

---

## 21. What not to do

Do not:
- patch reverse traversal by adding `π` to yaw in `Tote`
- special-case bezier only
- flip `up` by default
- keep transfer logic compensating for follower direction bugs

Those are workarounds.
The follower needs to become directionally correct again.

---

## 22. Success criteria

This phase is successful when:

- follower can move forward and reverse on linear segments
- follower can move forward and reverse on bezier segments
- snapshot forward matches actual travel direction
- ping-pong works without extra yaw hacks
- loop works in both directions
- tote yaw behaves consistently from snapshot forward
- transfer/link exit no longer requires ad hoc 180° correction

---

## Final takeaway

The next correct step is to restore **proper bidirectional traversal semantics inside `RouteFollower`**.

That means:
- reverse is a real traversal mode
- not just a yaw correction
- and wrap behaviors (`CLAMP`, `LOOP`, `PING_PONG`) must be reintroduced there as well

Once that is done, the tote and transfer logic can become much simpler again.
