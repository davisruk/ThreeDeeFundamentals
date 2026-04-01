# Transfer Motion Integration (Lateral Shift, Yaw Handling, Completion)

This document shows how to add **transfer motion** on top of the current setup:

- Keep `RouteFollower` generic
- Keep `TransferZoneMachine + Controller` owning control/state
- Add a **tote-local transfer motion override** that:
  - suspends normal route-following
  - performs lateral interpolation
  - hands back to the route follower on completion

---

## 1. Core idea

Two motion modes:

NORMAL → RouteFollower  
TRANSFERRING → custom interpolation

---

## 2. Tote update change

```java
if (interactionMode == ToteInteractionMode.TRANSFERRING) {
    updateTransferMotion(dtSeconds);
    return;
}
```

---

## 3. Minimal TransferMotionState

```java
public class TransferMotionState {

    private final Vec3 startPosition;
    private final RouteSegment targetSegment;
    private double elapsed;

    public TransferMotionState(Vec3 startPosition, RouteSegment targetSegment) {
        this.startPosition = startPosition;
        this.targetSegment = targetSegment;
    }

    public void update(double dt) { elapsed += dt; }

    public double t() { return Math.min(1.0, elapsed / 0.5); }

    public boolean done() { return t() >= 1.0; }

    public Vec3 start() { return startPosition; }

    public RouteSegment target() { return targetSegment; }
}
```

---

## 4. Motion update

```java
private void updateTransferMotion(double dt) {

    transfer.update(dt);

    double t = smooth(transfer.t());

    Vec3 start = transfer.start();
    Vec3 end = transfer.target().samplePosition(0.0);

    transformation.position.set(lerp(start, end, t));

    if (transfer.done()) {
        completeTransfer();
    }
}
```

---

## 5. Helpers

```java
private double smooth(double t) {
    return t * t * (3 - 2 * t);
}

private Vec3 lerp(Vec3 a, Vec3 b, double t) {
    return new Vec3(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t,
        a.z + (b.z - a.z) * t
    );
}
```

---

## 6. Completion

```java
private void completeTransfer() {

    RouteSegment target = transfer.target();

    routeFollower.setCurrentSegment(target);
    routeFollower.setDistanceAlongSegment(0.0);

    transfer = null;
    interactionMode = ToteInteractionMode.FREE;
}
```

---

## 7. Start transfer

```java
transfer = new TransferMotionState(
    lastSnapshot.position(),
    targetSegment
);
interactionMode = ToteInteractionMode.TRANSFERRING;
```

---

## 8. Key rule

RouteFollower is paused during transfer.

---

## 9. Expected result

- Tote moves along route
- Hits transfer
- Slides sideways
- Snaps to new segment
- Continues normally

---

## Final takeaway

Keep transfer motion OUT of RouteFollower.  
Use it as a temporary override.

