# Persistent Yaw Override Across Transfer-Link Traversal

This note refines the yaw policy for transfers involving linking segments.

## Goal

Preserve the tote's starting yaw:

- during the lateral transfer onto the linking segment
- for the entire traversal along the linking segment
- during the lateral transfer off the linking segment

Then return yaw control to normal route-following **only after** the tote has completed the exit transfer onto the next non-link segment.

This is a stronger rule than:
- preserve yaw during transfer only
- or preserve yaw for a short time after handoff

and it matches the behavior you described.

---

## 1. The correct control split

Separate:

- **position control**
- **yaw control**

### Position
Position can still be driven by:
- `RouteFollower` during normal movement
- transfer motion interpolation during lateral transfer

### Yaw
Yaw can be driven by:
- `RouteFollower` normally
- a persistent override while the tote is on a linking segment or within linked transfer phases

This is the important architectural adjustment.

---

## 2. The required tote state

Add explicit yaw override state to the tote.

### Suggested fields
```java
private boolean yawOverrideActive;
private float overriddenYawRadians;
```

You may also want a slightly richer model later, but these two are enough for now.

### Suggested helper methods
```java
public void enableYawOverride(float yawRadians) {
    this.yawOverrideActive = true;
    this.overriddenYawRadians = yawRadians;
}

public void disableYawOverride() {
    this.yawOverrideActive = false;
}

public boolean isYawOverrideActive() {
    return yawOverrideActive;
}

public float getOverriddenYawRadians() {
    return overriddenYawRadians;
}
```

---

## 3. How yaw should now work

### Case A — normal track traversal
- `RouteFollower` controls position
- `RouteFollower` also controls yaw/orientation

### Case B — lateral transfer onto linking segment
- transfer motion controls position
- yaw override is active and preserves the source yaw

### Case C — normal movement along linking segment
- `RouteFollower` controls position
- yaw override remains active
- follower yaw is ignored

### Case D — lateral transfer off linking segment
- transfer motion controls position
- yaw override remains active

### Case E — after handoff to the next non-link segment
- position returns to `RouteFollower`
- yaw override is cleared
- follower yaw controls orientation again

That is the desired rule.

---

## 4. Where yaw override should be enabled

Enable it when transfer onto a linking segment begins.

### Recommended place
Inside `Tote.beginTransfer(...)`, when the controller starts a transfer whose **target** is a linking segment.

Pseudo-rule:

```java
if (targetSegment.isLinkingSegment()) {
    enableYawOverride(currentYaw);
}
```

The current yaw should be captured from the tote at the moment the transfer begins.

This becomes the yaw used:
- during lateral entry
- across the linking segment
- during lateral exit

---

## 5. Where yaw override should be cleared

Clear it only when a transfer **off** the linking segment to the next non-link/main segment completes.

### Recommended place
In `completeTransfer(...)`, after the tote is handed onto the target segment:

```java
if (!targetSegment.isLinkingSegment()) {
    disableYawOverride();
}
```

This means:

- entering a linking segment -> yaw override stays on
- traversing linking segment -> yaw override stays on
- exiting to main/bottom track -> yaw override turns off on completion

That matches the desired behavior.

---

## 6. How to know whether a segment is a linking segment

You need a generic or route-level way to ask whether a segment is of this special type.

### Possible options
- a flag on `RouteSegment`
- a segment type enum
- metadata attached to the segment
- a helper predicate in the warehouse layer

### Suggested simple option
```java
public enum RouteSegmentType {
    NORMAL,
    LINK
}
```

and on `RouteSegment`:

```java
private RouteSegmentType type = RouteSegmentType.NORMAL;

public RouteSegmentType getType() {
    return type;
}

public boolean isLinkingSegment() {
    return type == RouteSegmentType.LINK;
}
```

If you already have a way to identify link segments, use that instead.

The important thing is that the tote/controller can tell whether the transfer target is a linking segment.

---

## 7. `applySnapshot(...)` becomes the yaw decision point

This is the cleanest place to apply the policy.

### Before
You likely do:
- apply position from snapshot
- apply yaw/orientation from snapshot forward/up

### Now
Do:
- apply position from snapshot
- if yaw override is active:
  - apply overridden yaw
- otherwise:
  - apply follower-derived yaw/orientation

### Sketch
```java
private void applySnapshot(RouteFollowerSnapshot snapshot) {

    transformation.position.set(
            snapshot.position().x,
            snapshot.position().y + yOffset,
            snapshot.position().z
    );

    if (yawOverrideActive) {
        applyYaw(overriddenYawRadians);
    } else {
        applyYawFromSnapshot(snapshot);
    }
}
```

This is the key implementation change.

---

## 8. Transfer motion phase should also use the yaw override

During the lateral phases you are already preserving yaw.

Once the tote has explicit yaw override state, the same rule can be reused:

### During lateral transfer
Use:
```java
applyYaw(overriddenYawRadians);
```

### During normal route-following on the link
`applySnapshot(...)` will continue using the same overridden yaw

This keeps the policy consistent.

---

## 9. Controller responsibilities

The controller/machine should still decide when a transfer begins, but it does not need to micromanage yaw every frame.

It only needs to ensure the transfer starts with the correct intent.

### Example policy
When activating transfer:
- if target segment is a linking segment:
  - tote should preserve current yaw across that link traversal

In practice, you can keep the actual enable/disable logic tote-side, driven by target segment type.

That keeps the controller simpler.

---

## 10. Updated tote flow

### Entering a transfer to a linking segment
1. controller activates transfer
2. tote captures current yaw
3. tote enables yaw override
4. tote performs lateral transfer
5. transfer completes onto linking segment
6. `RouteFollower` resumes position control
7. `applySnapshot(...)` continues using overridden yaw

### Exiting the linking segment
1. next transfer begins from the link to the next segment
2. same overridden yaw is still active
3. tote performs lateral transfer off the link
4. transfer completes onto non-link segment
5. tote disables yaw override
6. `RouteFollower` regains yaw control

That is the complete lifecycle.

---

## 11. This is better than keeping the tote in transfer mode the whole time

It would be possible to keep the tote in a broader “transfer-controlled” mode all the way across the linking segment, but that is not necessary.

Better:
- use `RouteFollower` for position while on the link
- use yaw override for orientation
- use transfer motion only during the lateral entry/exit phases

That is cleaner and keeps responsibilities separated.

---

## 12. Direct parallel transfers still fit this model

For direct transfers without linking segments:

- enable yaw override at transfer start
- keep it during the lateral motion
- disable it immediately on completion if the target segment is normal

So the same mechanism still works.

The difference is simply:
- no linking traversal period in between

That is a nice sign that the model is generic enough.

---

## 13. What to test

This phase is successful if:

- tote keeps source yaw during entry onto linking segment
- tote keeps the same yaw while moving along the linking segment
- tote keeps the same yaw during exit from the linking segment
- tote resumes normal follower yaw only after landing on the next non-link segment
- the same tote can still follow the route position correctly along the linking segment
- no yaw snapping occurs at either handoff

---

## 14. What not to do

Do not:
- bake yaw override into `RouteFollower`
- treat linking traversal as one giant transfer interpolation
- clear yaw override at the moment of handoff onto the linking segment
- duplicate yaw logic in multiple places if `applySnapshot(...)` can centralize it

Keep the rule simple:
- `RouteFollower` owns position
- tote yaw override can supersede follower yaw when active

---

## 15. Recommended implementation order

### Step 1
Add:
- `yawOverrideActive`
- `overriddenYawRadians`

### Step 2
Change `applySnapshot(...)` so it uses yaw override when active

### Step 3
Enable yaw override when entering transfer to a linking segment

### Step 4
Keep yaw override active across normal movement on the linking segment

### Step 5
Clear yaw override only when transfer completion lands on a non-link segment

That is enough to implement the full policy.

---

## Final takeaway

The correct yaw rule is not:

- preserve yaw only during the lateral transfer
or
- preserve yaw briefly after handoff

It is:

**preserve yaw for the entire lifetime of traversal on the linking segment, including both entry and exit transfers, while still letting `RouteFollower` control position along the link.**
