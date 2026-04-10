# Generic Simulation Framework vs Warehouse-Specific Components

This note corrects the direction of the recent sketches.

The important rule is:

**generic concepts belong in the framework**
**warehouse concepts belong on top of the framework**

That means sensors, controllers, events, stateful simulation objects, and world scheduling should all exist first as generic simulation concepts. Transfer zones, lid stations, tippers, conveyors, and totes should then be built on top of them.

---

## 1. Yes, that is the intended plan

The correct long-term plan is to make use of the generic simulation abstractions we discussed earlier:

- `SimObject`
- `StatefulSimObject`
- `SimulationController`
- `SimulationEvent`

Those should not be bypassed by making everything transfer-zone-specific.

So your correction is right:

- sensors should be generic
- controllers should be generic at the framework level
- specific warehouse machinery should implement or use those generic abstractions

The recent transfer-zone-focused sketches were intended as incremental steps, but they should not become the architectural center of gravity.

---

## 2. Correct layering

## Framework layer
Generic, reusable, no warehouse semantics:

- `SimObject`
- `StatefulSimObject<S>`
- `SimulationController`
- `SimulationEvent`
- `SimulationWorld` / `SimulationScheduler`
- generic `Sensor`
- generic motion / path-following / traversal
- generic state transition helpers
- generic detection regions / thresholds
- generic command or action concepts if introduced later

## Warehouse layer
Built on top of framework:

- `Tote`
- `Pack`
- `TransferZoneMachine`
- `LidOpeningStationMachine`
- `TipperMachine`
- `ConveyorMachine`
- `TransferZoneController`
- `LidStationController`
- warehouse-specific events
- warehouse-specific sensor configurations / sensor handlers

That is the intended architecture.

---

## 3. Generic framework concepts that should exist

### SimObject
Anything that participates in the simulation tick.

```java
package online.davisfamily.threedee.sim.framework;

public interface SimObject {
    String getId();
    void update(SimulationContext context, double dtSeconds);
}
```

This can be used by:
- totes
- machines
- sensors
- controllers
- world helpers if needed

---

### StatefulSimObject
Anything with explicit state.

```java
package online.davisfamily.threedee.sim.framework;

public interface StatefulSimObject<S extends Enum<S>> extends SimObject {
    S getState();
}
```

This is generic and reusable for:
- transfer machines
- stations
- conveyors
- even generic traffic/interlock objects later

---

### SimulationController
Generic decision/orchestration abstraction.

```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationController {
    void update(SimulationContext context, double dtSeconds);
}
```

You may later add methods like:
- `handleEvent(...)`
- `register(...)`
- `attachSensor(...)`

but the concept should remain generic.

---

### SimulationEvent
Generic event abstraction.

```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationEvent {
    String getSourceId();
    double getSimulationTimeSeconds();
}
```

The framework provides the concept.
The warehouse solution provides domain-specific event types, for example:
- tote detected
- tote entered station
- pack dropped
- downstream ready

---

## 4. Generic sensors should be part of the framework

This is the main correction.

A sensor should not be inherently “a transfer-zone sensor”.
It should first be a generic simulation concept.

### Generic framework sensor
```java
package online.davisfamily.threedee.sim.framework;

public interface Sensor extends SimObject {
}
```

That is intentionally minimal.

You may later refine it into something like:

```java
public interface Sensor<T> extends SimObject {
    boolean detects(T object, SimulationContext context);
}
```

or:

```java
public interface Sensor extends SimObject {
    void scan(SimulationContext context);
}
```

But the important point is:
**sensor is generic**

---

## 5. Warehouse-specific sensors should be compositions of generic sensors

The warehouse layer can then use generic sensors in specific ways.

Examples:

- a proximity/region sensor configured near a transfer zone
- an occupancy sensor configured over a station entry
- a count sensor configured for a conveyor intake
- a downstream-clear sensor configured at a junction

So instead of “transfer-zone sensor” being a special framework type, think in terms of:

- **generic sensor mechanism**
- **warehouse-specific deployment/configuration and reaction**

That is a much healthier model.

---

## 6. Generic movement / traversal

This is the same reasoning you already applied to `RouteFollower`.

### Framework side
- `RouteFollower`
- `RouteFollowerSnapshot`
- path/segment traversal
- position/orientation sampling

### Warehouse side
- tote uses `RouteFollower`
- transfer machine may temporarily override movement
- stations may hold/release a tote

So traversal remains framework, while machinery effects remain warehouse-specific.

That is exactly the pattern to follow for sensors and control too.

---

## 7. Generic world/simulation scheduler

The world should also stay generic.

### Framework
```java
package online.davisfamily.threedee.sim.framework;

public class SimulationWorld {
    public void update(double dtSeconds) {
        // schedule generic sim objects
    }
}
```

or

```java
public class SimulationScheduler {
    public void update(double dtSeconds) {
        // update phases
    }
}
```

Then the warehouse solution can register:
- totes
- sensors
- controllers
- machines

This means the scheduler is not warehouse-specific even if one concrete instance is used for the warehouse solution.

---

## 8. Better generic model for sensing

A more reusable pattern would be:

### Framework concepts
- `Sensor`
- `Detection`
- `SimulationEvent`
- `SimulationController`

### Warehouse-specific usage
- tote enters a configured detection region
- sensor emits a detection event
- transfer controller handles it
- machine updates its state
- tote is commanded or reserved

This gives much better reuse than encoding transfer-specific detection rules inside the sensor abstraction itself.

---

## 9. A more general sensor/event picture

A strong generic model would look like this:

```text
SimulationWorld
  -> updates SimObjects
  -> updates Sensors
  -> collects SimulationEvents
  -> updates SimulationControllers
```

with generic framework concepts:

- `Sensor` detects something
- `SimulationEvent` represents what was detected
- `SimulationController` responds
- `StatefulSimObject` stores runtime state

Then the warehouse layer defines meaning on top of that.

This is much closer to the original intention of the architecture.

---

## 10. Example of generic vs specific

## Generic framework
```java
public interface Sensor extends SimObject {
    void detect(SimulationContext context);
}
```

```java
public interface SimulationController {
    void handleEvent(SimulationEvent event, SimulationContext context);
}
```

```java
public interface SimulationEvent {
    String getSourceId();
    double getSimulationTimeSeconds();
}
```

## Warehouse-specific
```java
public record ToteDetectedEvent(
        String sourceId,
        double simulationTimeSeconds,
        Tote tote,
        String regionId
) implements SimulationEvent {
}
```

```java
public class TransferZoneController implements SimulationController {
    @Override
    public void handleEvent(SimulationEvent event, SimulationContext context) {
        if (event instanceof ToteDetectedEvent detected) {
            // transfer-zone-specific logic here
        }
    }
}
```

This is the right split:
- framework defines the pattern
- warehouse defines the domain meaning

---

## 11. How this applies to the current codebase

In the current codebase, the shortest path forward is still incremental, but the target should be corrected to:

### Framework target
Introduce or keep generic:
- `SimObject`
- `StatefulSimObject`
- `SimulationController`
- `SimulationEvent`
- `Sensor`
- `SimulationWorld`
- `RouteFollower`

### Warehouse target
Build on top:
- `Tote implements SimObject`
- `TransferZoneMachine implements StatefulSimObject<TransferZoneState>`
- `TransferZoneController implements SimulationController`
- a configured sensor or handler that detects totes in a specific zone region

This is better than letting transfer-zone-specific classes define the entire simulation shape.

---

## 12. Practical recommendation

For the next stage, the architecture should be framed like this:

### Framework
Create or firm up:
- `Sensor`
- `SimulationWorld`
- maybe an event queue/dispatcher
- keep `RouteFollower` generic

### Warehouse
Use those abstractions for:
- tote detection near transfer zone
- tote detection at station entry
- pack detection on conveyors

So yes:
**the generic framework abstractions should eventually be used**
and the warehouse components should be layered on top of them.

That should remain the guiding rule.

---

## 13. A good mental model

Think of the framework as providing reusable simulation grammar:

- object
- state
- event
- controller
- sensor
- traversal
- scheduler

Then the warehouse solution writes sentences in that grammar:

- tote approaches transfer zone
- station detects tote
- tipper empties tote
- conveyor counts packs

That is the cleanest way to preserve separation.

---

## 14. Final recommendation

The recent transfer-zone-specific designs should be treated as **examples of how the warehouse solution may use the framework**, not as replacements for the framework abstractions.

So the corrected plan is:

1. keep framework abstractions generic
2. make warehouse machinery implement/use them
3. avoid making sensors or controllers inherently transfer-specific at the framework level
4. let specificity live only in warehouse-layer classes

---

## Final takeaway

**Yes — the generic framework abstractions are still the plan, and warehouse-specific machinery should sit on top of them rather than redefining them.**

That should remain the architectural rule going forward.
