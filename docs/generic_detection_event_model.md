# Generic Detection Event Model (Sensor → Event → Controller)

This document defines a clean, reusable pattern for **detection events** that sits between:

- `Sensor` (framework)
- `SimulationController` (framework)

It builds on your current working implementation and refines it into something that is:

- generic (not warehouse-specific)
- reusable across sensors
- expressive enough for future machinery (transfer zones, stations, conveyors)

---

## 1. The goal

Right now you have:

- sensor detects membership change
- event is published
- controller reacts

The missing piece is a **clear, generic event model**.

We want to move from:

"something happened on sensor X"

to:

"object Y ENTERED region Z at time T"

That is the key upgrade.

---

## 2. Core concept: DetectionEvent

Introduce a generic detection event that captures:

- what was detected
- where it was detected
- what type of change occurred
- when it happened

---

## 3. Detection type enum

```java
public enum DetectionType {
    ENTER,
    EXIT,
    PRESENT
}
```

---

## 4. Generic DetectionEvent

```java
public record DetectionEvent(
        String sourceId,
        double simulationTimeSeconds,
        String sensorId,
        String objectId,
        DetectionType type
) implements SimulationEvent {
}
```

---

## 5. Updating MembershipSensor

```java
context.publish(new DetectionEvent(
        getId(),
        context.getSimulationTimeSeconds(),
        getId(),
        trackable.getFollowerId(),
        DetectionType.ENTER
));
```

---

## 6. Controller handling

```java
@Override
public void handleEvent(SimulationEvent event, SimulationContext context) {
    if (event instanceof DetectionEvent detection) {

        if (detection.type() == DetectionType.ENTER) {
            // react
        }

        if (detection.type() == DetectionType.EXIT) {
            // react
        }
    }
}
```

---

## 7. Design rule

Sensors detect.
Controllers interpret.

---

## 8. Next step

Implement this event model, then reuse it for transfer zones.

---

## Final takeaway

A single generic DetectionEvent unlocks reusable sensing across the whole simulation.
