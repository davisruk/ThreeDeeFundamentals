# Transfer Phase 4: Aligned Lateral Transfer with Yaw Preservation

This document refines the transfer motion model from the earlier “simple motion override” into a more warehouse-appropriate phased model.

The goals are:

- keep the tote's current yaw during the transfer
- avoid diagonal-looking transfer starts
- begin lateral transfer only when the **tote itself** is centred over the transfer zone centre
- support:
  - source track -> linking track
  - linking track -> target track
  - direct parallel track transfer without linking segments

This phase still aims for a **clear, workable architecture**, not final animation polish.

---

## 1. Why the earlier simple interpolation is not enough

A single interpolation from:
- current tote position
to
- target segment entry position

causes two problems:

1. the tote may begin moving laterally before it is properly aligned with the transfer zone centre
2. the tote yaw may snap toward the target path direction too early

That produces:
- diagonal initial motion
- visually wrong heading changes
- the same problem when leaving the linking segment

So the transfer needs to become **phased**, not just “one lerp”.

---

## 2. The correct conceptual model

A transfer should be thought of as:

### Phase A — approach / reserve
Handled already by the current sensor/controller logic.

### Phase B — align until tote centre reaches zone centre
The tote continues moving normally along the current segment until its **own centre** is aligned with the transfer centre.

### Phase C — lateral transfer
The tote moves laterally toward the target handoff point while preserving the yaw/orientation it had at transfer start.

### Phase D — handoff
The tote is placed onto the target segment at the correct target distance and normal route following resumes.

That is the right process-driven model.

---

## 3. Important correction: transfer begins when the tote is centred, not when the route point is centred

This is the key rule for this phase.

The trigger should not be:
- route follower distance reaches transfer centre

The trigger should be:
- the **tote centre** reaches transfer centre

This only matters if your follower distance does not already represent the tote centre.

So before implementing the phase logic, answer this clearly:

### What does `distanceAlongSegment` represent?
- tote centre?
- tote front edge?
- tote rear edge?
- some arbitrary reference point?

If it already represents the tote centre, great.

If not, you need a small conversion so you can compute:

```text
toteCenterDistanceAlongSegment
```

and compare that to:

```text
transferCenterDistance
```

---

## 4. Recommended transfer data model

The transfer machine/controller still decides when a tote is reserved or activated.

But the tote should now execute a richer motion state.

### Suggested enum
```java
public enum TransferMotionPhase {
    WAITING_FOR_ALIGNMENT,
    LATERAL_TRANSFER
}
```

### Suggested state object
```java
package online.davisfamily.warehouse.transfer;

import online.davisfamily.threedee.Vec3;
import online.davisfamily.threedee.routing.RouteSegment;

public class TransferMotionState {

    private final String machineId;

    private final RouteSegment sourceSegment;
    private final RouteSegment targetSegment;

    private final float sourceTransferCenterDistance;
    private final float targetDistanceAlongSegment;

    private final Vec3 lateralStartPosition;
    private final Vec3 lateralEndPosition;

    private final float preservedYawRadians;
    private final double transferDurationSeconds;

    private TransferMotionPhase phase = TransferMotionPhase.WAITING_FOR_ALIGNMENT;
    private double elapsedSeconds;

    public TransferMotionState(String machineId,
                               RouteSegment sourceSegment,
                               RouteSegment targetSegment,
                               float sourceTransferCenterDistance,
                               float targetDistanceAlongSegment,
                               Vec3 lateralStartPosition,
                               Vec3 lateralEndPosition,
                               float preservedYawRadians,
                               double transferDurationSeconds) {
        this.machineId = machineId;
        this.sourceSegment = sourceSegment;
        this.targetSegment = targetSegment;
        this.sourceTransferCenterDistance = sourceTransferCenterDistance;
        this.targetDistanceAlongSegment = targetDistanceAlongSegment;
        this.lateralStartPosition = lateralStartPosition;
        this.lateralEndPosition = lateralEndPosition;
        this.preservedYawRadians = preservedYawRadians;
        this.transferDurationSeconds = transferDurationSeconds;
    }

    public String getMachineId() {
        return machineId;
    }

    public RouteSegment getSourceSegment() {
        return sourceSegment;
    }

    public RouteSegment getTargetSegment() {
        return targetSegment;
    }

    public float getSourceTransferCenterDistance() {
        return sourceTransferCenterDistance;
    }

    public float getTargetDistanceAlongSegment() {
        return targetDistanceAlongSegment;
    }

    public Vec3 getLateralStartPosition() {
        return lateralStartPosition;
    }

    public Vec3 getLateralEndPosition() {
        return lateralEndPosition;
    }

    public float getPreservedYawRadians() {
        return preservedYawRadians;
    }

    public double getTransferDurationSeconds() {
        return transferDurationSeconds;
    }

    public TransferMotionPhase getPhase() {
        return phase;
    }

    public void setPhase(TransferMotionPhase phase) {
        this.phase = phase;
        this.elapsedSeconds = 0.0;
    }

    public void updateElapsed(double dtSeconds) {
        elapsedSeconds += dtSeconds;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public float getProgress() {
        return (float)Math.min(1.0, elapsedSeconds / transferDurationSeconds);
    }

    public boolean isComplete() {
        return getProgress() >= 1.0f;
    }
}
```

---

## 5. How to compute the transfer centre

For a transfer zone with:

- `startDistance`
- `endDistance`

a good default is:

```java
float transferCenter = (startDistance + endDistance) * 0.5f;
```

This gives a canonical centre for the transfer zone.

Later, if needed, this could become explicitly configurable on the zone itself.

---

## 6. How to decide whether the tote is centred

### Case A — follower distance already represents tote centre
Then the trigger is simply:

```java
snapshot.distanceAlongSegment() >= transferCenterDistance
```

### Case B — follower distance is not the tote centre
Then compute:

```java
float toteCenterDistance = snapshot.distanceAlongSegment() + centreOffset;
```

where `centreOffset` depends on what the follower distance means and the tote length/depth.

For example, if follower distance represents the tote rear and you want the centre:

```java
centreOffset = toteLengthAlongTravel * 0.5f;
```

The important rule is:
- lateral transfer begins only when the tote centre is aligned with the transfer centre.

---

## 7. Tote.beginTransfer(...) should no longer start lateral motion immediately

In the previous simple version, `beginTransfer(...)` could immediately build start/end positions and begin interpolation.

Now it should instead create a `TransferMotionState` in:

```java
WAITING_FOR_ALIGNMENT
```

### Suggested tote API
```java
public void beginTransfer(String machineId,
                          RouteSegment sourceSegment,
                          RouteSegment targetSegment,
                          float sourceTransferCenterDistance,
                          float targetDistanceAlongSegment,
                          double transferDurationSeconds)
```

### Example tote-side setup
```java
public void beginTransfer(String machineId,
                          RouteSegment sourceSegment,
                          RouteSegment targetSegment,
                          float sourceTransferCenterDistance,
                          float targetDistanceAlongSegment,
                          double transferDurationSeconds) {

    if (!machineId.equals(reservedByMachineId)) {
        return;
    }

    RouteFollowerSnapshot snap = getLastRouteSnapshot();
    if (snap == null) {
        return;
    }

    float preservedYaw = getCurrentYawRadians();

    Vec3 initialPosition = new Vec3(
            transformation.position.x,
            transformation.position.y,
            transformation.position.z
    );

    Vec3 targetPosition = targetSegment.samplePosition(targetDistanceAlongSegment);

    this.controllingMachineId = machineId;
    this.motionState = ToteMotionState.TRANSFERRING;

    this.transferMotionState = new TransferMotionState(
            machineId,
            sourceSegment,
            targetSegment,
            sourceTransferCenterDistance,
            targetDistanceAlongSegment,
            initialPosition,
            targetPosition,
            preservedYaw,
            transferDurationSeconds
    );
}
```

### Note
At this moment, motion state becomes transfer-controlled, but the transfer phase is still:
- `WAITING_FOR_ALIGNMENT`

So route-following may continue until the centre condition is met.

---

## 8. Tote.update(...) becomes phase-aware

Suggested shape:

```java
@Override
public void update(SimulationContext context, double dtSeconds) {

    if (motionState == ToteMotionState.TRANSFERRING && transferMotionState != null) {
        updateTransferControlledMotion(context, dtSeconds);
        return;
    }

    RouteFollowerSnapshot snapshot =
            routeFollower.advance(dtSeconds, isMotionBlocked());

    lastRouteSnapshot = snapshot;
    applySnapshot(snapshot);
}
```

The important difference now is that “transfering” does not necessarily mean “already moving laterally”.
It may still be waiting for alignment.

---

## 9. updateTransferControlledMotion(...)

This method should branch on the transfer phase.

### Shape
```java
private void updateTransferControlledMotion(SimulationContext context, double dtSeconds) {

    if (transferMotionState.getPhase() == TransferMotionPhase.WAITING_FOR_ALIGNMENT) {
        updateAlignmentPhase(context, dtSeconds);
        return;
    }

    if (transferMotionState.getPhase() == TransferMotionPhase.LATERAL_TRANSFER) {
        updateLateralTransferPhase(context, dtSeconds);
    }
}
```

---

## 10. Alignment phase

In this phase:
- tote continues advancing normally along the **source segment**
- yaw/orientation still comes from current normal movement or can be frozen if preferred
- when tote centre reaches transfer centre:
  - route follower stops advancing
  - lateral start position is fixed
  - phase switches to `LATERAL_TRANSFER`

### Suggested implementation sketch
```java
private void updateAlignmentPhase(SimulationContext context, double dtSeconds) {

    RouteFollowerSnapshot snapshot =
            routeFollower.advance(dtSeconds, false);

    lastRouteSnapshot = snapshot;
    applySnapshot(snapshot);

    float toteCenterDistance = getToteCenterDistanceAlongSegment(snapshot);

    if (toteCenterDistance >= transferMotionState.getSourceTransferCenterDistance()) {

        Vec3 alignedStart = new Vec3(
                transformation.position.x,
                transformation.position.y,
                transformation.position.z
        );

        Vec3 targetPosition =
                transferMotionState.getTargetSegment()
                        .samplePosition(transferMotionState.getTargetDistanceAlongSegment());

        transferMotionState = new TransferMotionState(
                transferMotionState.getMachineId(),
                transferMotionState.getSourceSegment(),
                transferMotionState.getTargetSegment(),
                transferMotionState.getSourceTransferCenterDistance(),
                transferMotionState.getTargetDistanceAlongSegment(),
                alignedStart,
                targetPosition,
                transferMotionState.getPreservedYawRadians(),
                transferMotionState.getTransferDurationSeconds()
        );

        transferMotionState.setPhase(TransferMotionPhase.LATERAL_TRANSFER);
    }
}
```

### Note
You may wish to avoid reconstructing the whole state object and instead make `lateralStartPosition` mutable.
Either is fine; the architectural point is the same.

---

## 11. Lateral transfer phase

Now:
- `RouteFollower` does not advance
- tote position interpolates from aligned start -> target handoff point
- yaw remains preserved from transfer start

### Suggested implementation
```java
private void updateLateralTransferPhase(SimulationContext context, double dtSeconds) {

    transferMotionState.updateElapsed(dtSeconds);

    float t = smoothstep(transferMotionState.getProgress());

    Vec3 start = transferMotionState.getLateralStartPosition();
    Vec3 end = transferMotionState.getLateralEndPosition();

    float x = lerp(start.x, end.x, t);
    float y = lerp(start.y, end.y, t);
    float z = lerp(start.z, end.z, t);

    transformation.position.set(x, y, z);

    applyPreservedYaw(transferMotionState.getPreservedYawRadians());

    if (transferMotionState.isComplete()) {
        completeTransfer(context);
    }
}
```

---

## 12. Yaw preservation

This should be a machine/controller policy eventually, but for now keep it simple:

### Rule
Preserve the yaw that the tote had when transfer began.

That means:
- no yaw snap toward linking segment direction
- no yaw snap toward bottom track direction
- no diagonal heading change during lateral motion

Later, this preserved yaw decision could be explicitly configured by the transfer machine.

For now, hard-coding this rule is acceptable.

---

## 13. Completing the transfer

When lateral transfer completes:

1. set `RouteFollower` to the target segment
2. set `distanceAlongSegment` to the configured target distance
3. restore tote motion state to `MOVING`
4. clear reservation/control
5. publish `TransferCompletedEvent`

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

    context.publish(new TransferCompletedEvent(
            machineId,
            context.getSimulationTimeSeconds(),
            getId()
    ));
}
```

### Important
As before:
- do not immediately consume more route movement in the same tick
- the next normal update will resume route-following from the handoff point

This is consistent with the corrected connection policy you just validated.

---

## 14. Controller changes for this phase

The controller still reacts to:
- approach region ENTER -> reserve
- window region ENTER -> start transfer

But starting transfer now means:
- putting tote into transfer-controlled mode
- not immediate lateral movement

### Suggested controller logic
```java
if (machine.getState() == TransferZoneState.RESERVED
        && tote.getId().equals(machine.getReservedToteId())) {

    machine.transitionTo(TransferZoneState.TRANSFERRING);

    TransferZone zone = machine.getDefinition();

    float sourceTransferCenter =
            (zone.getStartDistance() + zone.getEndDistance()) * 0.5f;

    RouteSegment targetSegment = zone.getTargetSegment();
    float targetDistance = zone.getTargetStartDistance();

    tote.beginTransfer(
            machine.getId(),
            zone.getSourceSegment(),
            targetSegment,
            sourceTransferCenter,
            targetDistance,
            0.35
    );
}
```

---

## 15. This works for direct parallel transfers too

This is one of the key benefits of the phased approach.

Because the model is:
- align on source segment
- move laterally to target segment handoff point

it does not care whether:
- the target is a linking segment
- the target is a main track
- the two tracks are directly parallel with no intermediate link

That is exactly what you wanted.

The transfer motion model is now generic enough to support all three cases.

---

## 16. What to test

This phase should be considered successful if:

- tote is reserved correctly by outer/approach window
- tote activates transfer correctly on inner/window ENTER
- tote continues along source segment until its **centre** reaches transfer centre
- lateral motion begins only at correct alignment
- tote yaw remains unchanged during lateral transfer
- tote hands off cleanly to target segment / target distance
- route following resumes normally after handoff
- same logic works for both:
  - entry onto linking segment
  - exit from linking segment
- direct parallel transfer can use the same mechanism later

---

## 17. What to keep simple for now

For this phase, keep these deliberately simple:

- lateral path = straight line from aligned start to target point
- yaw policy = preserve current yaw
- target orientation after handoff = let normal route following take over next update
- transfer centre = zone midpoint
- target distance = configured target start distance

That is enough.

The architecture and behavior correctness are more important right now than final polish.

---

## 18. What comes after this

Only once this works should you move to a later refinement phase such as:

### Phase 5
- better transfer path than straight line
- orientation blending after handoff
- machine-driven yaw policy options
- more precise tote-length-aware centre calculations if needed
- sensor/controller cleanup or factory extraction

But do not jump there yet.

---

## 19. Recommended implementation order

### Step 1
Add `TransferMotionPhase`

### Step 2
Refine `TransferMotionState` to support:
- source centre distance
- preserved yaw
- waiting-for-alignment phase

### Step 3
Update `Tote.beginTransfer(...)` so it creates a phase-based transfer state

### Step 4
Update `Tote.update(...)` to handle:
- alignment phase
- lateral phase

### Step 5
Preserve yaw during lateral phase

### Step 6
Complete transfer by setting target segment + target distance and publishing completion event

### Step 7
Disable any remaining logic that resets transfer on window exit

That is the safest sequence.

---

## Final takeaway

The right refinement now is to move from:
- “transfer begins immediately with one interpolation”

to:
- “wait until tote centre aligns with the zone centre, then perform a pure lateral transfer while preserving yaw, then hand off to the target segment”

That will address the current issues for:
- link entry
- link exit
- direct parallel transfers

without changing the overall architecture.
