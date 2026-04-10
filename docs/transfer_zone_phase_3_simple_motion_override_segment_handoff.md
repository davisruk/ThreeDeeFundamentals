# Transfer Zone Phase 3: Simple Motion Override + Segment Handoff

This document defines the next thin vertical slice for transfer zones in the new architecture.

## Goal
Implement:
- simple transfer motion override
- controlled tote motion while machine is ACTIVE
- handoff to target segment on completion

Do **not** aim yet for:
- polished transfer geometry
- perfect orientation blending
- fancy interpolation curves
- generalized motion-command framework

This phase is about proving that the architecture can now **take control of a moving tote, override route-following temporarily, and return it to normal route-following on a different segment**.

---

## 1. What should already be true before this phase

From the previous phases, the following should already work:

- tote moves normally with `RouteFollower`
- approach/outer window sensor reserves the tote
- inner window sensor activates the machine
- tote records reservation / controlling machine
- machine and tote state are reset cleanly when using the current placeholder logic

That means the remaining missing piece is actual motion handoff.

---

## 2. The core idea

There are now two motion modes for the tote:

### Normal mode
- tote movement is driven by `RouteFollower`
- snapshot is applied to tote transform

### Transfer mode
- tote movement is temporarily driven by transfer logic
- `RouteFollower` is **not advanced**
- transform is updated from transfer interpolation instead
- when complete, the tote is placed onto the target segment
- normal route-following resumes

This is the critical architectural step.

---

## 3. Keep the motion override in the Tote, not the machine

Even though the machine is the active controller, the tote should still own:
- its own transform
- its own route follower
- its own current motion mode
- its own temporary transfer motion state

That means the controller should **tell** the tote to begin transfer, but the tote should execute the actual movement override.

This preserves the separation:

- machine/controller decides
- tote executes

That is the right balance.

---

## 4. Suggested tote state additions

For this phase, the tote needs:

```java
private String controllingMachineId;
private TransferMotionState transferMotionState;
private ToteMotionState motionState;
```

Suggested motion state enum:

```java
public enum ToteMotionState {
    MOVING,
    TRANSFERRING,
    HELD,
    BLOCKED
}
```

This is better than overloading reservation state with motion meaning.

---

## 5. TransferMotionState

Keep it deliberately simple.

```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.threedee.Vec3;
import online.davisfamily.threedee.routing.RouteSegment;

public class TransferMotionState {

    private final String machineId;
    private final Vec3 startPosition;
    private final Vec3 endPosition;
    private final RouteSegment targetSegment;
    private final float targetDistanceAlongSegment;

    private double elapsedSeconds;
    private final double durationSeconds;

    public TransferMotionState(String machineId,
                               Vec3 startPosition,
                               Vec3 endPosition,
                               RouteSegment targetSegment,
                               float targetDistanceAlongSegment,
                               double durationSeconds) {
        this.machineId = machineId;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.targetSegment = targetSegment;
        this.targetDistanceAlongSegment = targetDistanceAlongSegment;
        this.durationSeconds = durationSeconds;
    }

    public void update(double dtSeconds) {
        elapsedSeconds += dtSeconds;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public float getProgress() {
        return (float)Math.min(1.0, elapsedSeconds / durationSeconds);
    }

    public boolean isComplete() {
        return getProgress() >= 1.0f;
    }

    public String getMachineId() {
        return machineId;
    }

    public Vec3 getStartPosition() {
        return startPosition;
    }

    public Vec3 getEndPosition() {
        return endPosition;
    }

    public RouteSegment getTargetSegment() {
        return targetSegment;
    }

    public float getTargetDistanceAlongSegment() {
        return targetDistanceAlongSegment;
    }
}
```

---

## 6. What the end position should be

For the first version, keep this simple.

### Recommended first target
Use the start of the target segment:

```java
Vec3 end = targetSegment.samplePosition(0f);
```

or, if your transfer should land slightly into the target segment:

```java
float targetDistance = 0f; // or a small positive entry distance
Vec3 end = targetSegment.samplePosition(targetDistance);
```

This is enough to prove the handoff.

You can refine the exact landing point later.

---

## 7. Tote.beginTransfer(...)

When the inner window sensor causes the machine/controller to activate, the controller should call a high-level tote method, not mutate tote internals directly.

Suggested tote API:

```java
public void beginTransfer(String machineId,
                          RouteSegment targetSegment,
                          float targetDistanceAlongSegment,
                          double durationSeconds)
```

### Example implementation sketch

```java
public void beginTransfer(String machineId,
                          RouteSegment targetSegment,
                          float targetDistanceAlongSegment,
                          double durationSeconds) {

    if (!machineId.equals(reservedByMachineId)) {
        return;
    }

    RouteFollowerSnapshot snap = getLastRouteSnapshot();
    if (snap == null) {
        return;
    }

    Vec3 start = snap.position();
    Vec3 end = targetSegment.samplePosition(targetDistanceAlongSegment);

    this.controllingMachineId = machineId;
    this.motionState = ToteMotionState.TRANSFERRING;

    this.transferMotionState = new TransferMotionState(
            machineId,
            new Vec3(start.x, start.y, start.z),
            new Vec3(end.x, end.y, end.z),
            targetSegment,
            targetDistanceAlongSegment,
            durationSeconds
    );
}
```

### Important
No `RouteFollower` change happens yet. That only happens when transfer completes.

---

## 8. Tote.update(...) in this phase

The tote update now needs a motion-mode branch.

### Shape

```java
@Override
public void update(SimulationContext context, double dtSeconds) {

    if (motionState == ToteMotionState.TRANSFERRING && transferMotionState != null) {
        updateTransferMotion(context, dtSeconds);
        return;
    }

    RouteFollowerSnapshot snapshot =
            routeFollower.advance(dtSeconds, isMotionBlocked());

    lastRouteSnapshot = snapshot;
    applySnapshot(snapshot);
}
```

### Important
`isMotionBlocked()` should not block reservation.
It should block only true motion overrides such as TRANSFERRING / HELD / BLOCKED.

---

## 9. updateTransferMotion(...)

Keep this simple and explicit.

```java
private void updateTransferMotion(SimulationContext context, double dtSeconds) {

    transferMotionState.update(dtSeconds);

    float t = smoothstep(transferMotionState.getProgress());

    Vec3 start = transferMotionState.getStartPosition();
    Vec3 end = transferMotionState.getEndPosition();

    float x = lerp(start.x, end.x, t);
    float y = lerp(start.y, end.y, t);
    float z = lerp(start.z, end.z, t);

    transformation.position.set(x, y, z);

    // Keep orientation simple for now
    applyTransferOrientation();

    if (transferMotionState.isComplete()) {
        completeTransfer(context);
    }
}
```

Helpers:

```java
private float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

private float smoothstep(float t) {
    return t * t * (3f - 2f * t);
}
```

---

## 10. Orientation during transfer

Do not overcomplicate this yet.

### Recommended first version
Freeze orientation using the last normal route-following snapshot.

That means:
- do not try to blend forward vectors yet
- do not try to derive target orientation yet

If your tote currently has a reliable way of applying the normal route orientation, simply keep whatever orientation it had at transfer start.

This is enough for proving the motion override.

---

## 11. completeTransfer(...)

This is the key handoff step.

When transfer motion completes:

1. move the `RouteFollower` onto the target segment and distance
2. clear transfer motion state
3. restore normal tote motion state
4. clear reservation/control
5. notify the machine/controller that transfer is complete

### Tote-side implementation sketch

```java
private void completeTransfer(SimulationContext context) {

    RouteSegment targetSegment = transferMotionState.getTargetSegment();
    float targetDistance = transferMotionState.getTargetDistanceAlongSegment();
    String machineId = transferMotionState.getMachineId();

    routeFollower.setCurrentSegment(targetSegment);
    routeFollower.setDistanceAlongSegment(targetDistance);

    reservedByMachineId = null;
    controllingMachineId = null;
    motionState = ToteMotionState.MOVING;
    transferMotionState = null;

    // optional: refresh snapshot immediately so next normal update is consistent
    RouteFollowerSnapshot snapshot = routeFollower.buildSnapshot();
    lastRouteSnapshot = snapshot;
    applySnapshot(snapshot);

    context.publish(new TransferCompletedEvent(
            machineId,
            context.getSimulationTimeSeconds(),
            getId()
    ));
}
```

### Note
If `RouteFollower.buildSnapshot()` is currently private, you may need:
- a public `getSnapshot()` / `snapshot()` method
or
- a public `refreshSnapshot()` method
or
- simply allow the next normal update to rebuild it

---

## 12. TransferCompletedEvent

This is a new warehouse-specific event built on the framework event pattern.

```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.threedee.sim.framework.SimulationEvent;

public record TransferCompletedEvent(
        String sourceId,
        double simulationTimeSeconds,
        String toteId
) implements SimulationEvent {
}
```

### Why publish this?
Because once motion is complete:
- the machine should reset cleanly
- that reset should happen through the event/controller path, not by the tote reaching inside the machine

This preserves the architecture.

---

## 13. Controller changes for this phase

### On inner window ENTER
The controller should now start transfer, not just mark active.

Pseudo-shape:

```java
if (machine.getState() == TransferZoneState.RESERVED
        && tote.getId().equals(machine.getReservedToteId())) {

    machine.transitionTo(TransferZoneState.TRANSFERRING);

    RouteSegment targetSegment = machine.getDefinition().getTargetSegment();
    float targetDistance = 0f; // or a configured entry distance

    tote.beginTransfer(
            machine.getId(),
            targetSegment,
            targetDistance,
            0.35
    );
}
```

### On TransferCompletedEvent
The controller should reset the machine.

```java
if (event instanceof TransferCompletedEvent completed) {
    if (!machine.getId().equals(completed.sourceId())) {
        return;
    }

    if (!completed.toteId().equals(machine.getReservedToteId())) {
        return;
    }

    machine.setReservedToteId(null);
    machine.setReservedDirection(null);
    machine.transitionTo(TransferZoneState.IDLE);
}
```

---

## 14. Machine state progression in this phase

Suggested minimal progression now:

```text
IDLE
  -> RESERVED
  -> TRANSFERRING
  -> IDLE
```

That is enough.

Do not add more states until the motion handoff is proven.

---

## 15. Important temporary change

Right now you said:
- on window exit, machine becomes idle
- tote reservation clears

That logic should now be removed or disabled for transfer windows.

Why?
Because once actual transfer motion starts, the machine should reset on:
- **transfer completion**
not
- **window exit**

This is a very important behavioral change for this phase.

---

## 16. What to test

This phase should be considered successful if:

- tote moves normally before reservation
- outer/approach detection reserves the correct tote
- inner/window detection starts transfer for the correct tote only
- normal route-following pauses while transfer motion runs
- tote moves from source position to target segment entry
- on completion, tote resumes normal route-following on the target segment
- machine resets via completion event, not window exit
- unrelated tote/window events do not interfere

If all of that works, the new architecture has successfully taken over actual transfer behavior.

---

## 17. What to keep simple for now

For this phase, keep these deliberately crude if necessary:

- transfer path = straight interpolation
- target point = target segment start
- orientation = frozen
- duration = fixed constant

That is absolutely fine.

The architecture is the important thing here, not the final motion polish.

---

## 18. What comes after this

Only after this is stable should you move to:

### Phase 4
Refine transfer motion:
- use a better path than straight lerp
- improve orientation handling
- better target landing point
- possibly blend into target segment tangent

But do not jump there yet.

---

## 19. Recommended implementation order

### Step 1
Add `ToteMotionState` if not already present

### Step 2
Add `TransferMotionState`

### Step 3
Add `Tote.beginTransfer(...)`

### Step 4
Add transfer-mode branch to `Tote.update(...)`

### Step 5
Add `TransferCompletedEvent`

### Step 6
Change controller so inner window ENTER starts transfer

### Step 7
Change controller so transfer completion resets machine

### Step 8
Remove placeholder "window exit resets machine" logic

That is the safest order.

---

## Final takeaway

The next correct step is to implement a **simple motion override plus segment handoff**, using the new architecture end-to-end:

- sensor detects
- controller activates
- tote executes transfer motion
- tote publishes completion
- controller resets machine

Keep the motion simple.
Prove the architecture.
Refine the geometry later.
