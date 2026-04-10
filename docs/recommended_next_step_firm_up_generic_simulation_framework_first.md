# Recommended Next Step: Firm Up the Generic Simulation Framework First

This document goes one level deeper than the previous architectural note and answers the practical question:

**What should be the next step before making more code changes?**

The recommendation is:

## Do not jump straight back into transfer-zone behavior.
Instead, first firm up the **generic simulation framework contract**.

That means the next implementation step should be to define and wire in a small but real generic core consisting of:

- `SimulationWorld`
- `SimObject`
- `StatefulSimObject`
- `Sensor`
- `SimulationController`
- `SimulationEvent`
- `SimulationContext`

and then make the current working route-following setup run through that framework, **without yet re-enabling transfer behavior**.

---

## 1. Why this should be the next step

You are now in a better place than before:

- `RouteFollower` is extracted and working
- `RouteFollowerSnapshot` exists
- baseline tote travel works when warehouse interactions are disabled
- you have identified the right architectural boundary:
  - framework concepts must stay generic
  - warehouse machinery must sit on top

What is still missing is a **real runtime backbone**.

Right now the architecture exists mostly as:
- partial classes
- design intent
- temporary bridges

Before adding back transfer logic, you need the generic backbone to be real enough that future warehouse features have a stable place to live.

That is why the next step should be:
**make the framework runtime real before reintroducing machinery behavior**

---

## 2. The concrete next step

Implement a minimal but genuine generic simulation pipeline:

```text
SimulationWorld
  -> updates SimObjects
  -> updates Sensors
  -> dispatches SimulationEvents
  -> updates SimulationControllers
```

Then hook the already-working tote travel into it.

### Important
At this stage:
- totes should move
- snapshots should update
- sensors/controllers can exist
- transfer behavior should still remain disabled or stubbed

So this is not yet “make transfer zones work again”.
It is:

**put the working motion model inside the generic simulation framework**

---

## 3. Why not go straight to transfer zones?

Because if you do that now, you are likely to repeat the same problem:
- transfer-specific concerns start shaping the framework
- temporary wiring becomes permanent
- ownership remains unclear

The framework needs one clean pass first.

Think of it as building the road before putting traffic lights and factories on it.

---

## 4. What “firming up the framework” means in practice

The next implementation step should answer these questions in code:

### 1. Who owns the simulation tick?
Answer: `SimulationWorld`

### 2. What kinds of things can be updated?
Answer: `SimObject`

### 3. What kinds of things detect conditions?
Answer: `Sensor`

### 4. What kinds of things respond to detections/events?
Answer: `SimulationController`

### 5. How do stateful runtime machines fit in?
Answer: `StatefulSimObject`

### 6. How are observations passed around?
Answer: `SimulationEvent` and a small event queue in `SimulationContext`

Once those are in place, transfer zones, lid stations, and conveyors all have a proper substrate.

---

## 5. The exact target for this phase

The goal of this phase should be:

### Working outcome
- `SimulationWorld.update(dt)` exists and is the simulation root
- one or more totes are registered with the world
- totes move correctly using `RouteFollower`
- the world updates the totes through generic `SimObject`
- there is a generic sensor/controller/event path in place, even if only lightly used
- `GraphFollowerBehaviour` becomes only a temporary world-update bridge

### Not required yet
- transfer execution
- station processing
- conveyor orchestration
- generic command framework
- perfect event architecture
- sensor hierarchy explosion

This keeps the scope under control.

---

## 6. Recommended minimal framework structure

## SimObject
```java
package online.davisfamily.threedee.sim.framework;

public interface SimObject {
    String getId();
    void update(SimulationContext context, double dtSeconds);
}
```

---

## StatefulSimObject
```java
package online.davisfamily.threedee.sim.framework;

public interface StatefulSimObject<S extends Enum<S>> extends SimObject {
    S getState();
}
```

---

## SimulationEvent
```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationEvent {
    String getSourceId();
    double getSimulationTimeSeconds();
}
```

---

## Sensor
Keep it generic and simple:

```java
package online.davisfamily.threedee.sim.framework;

public interface Sensor extends SimObject {
}
```

This is enough initially.

If you want a slightly richer version:

```java
public interface Sensor extends SimObject {
    void detect(SimulationContext context);
}
```

But do not overcomplicate it yet.

---

## SimulationController
```java
package online.davisfamily.threedee.sim.framework;

public interface SimulationController {
    void update(SimulationContext context, double dtSeconds);
    void handleEvent(SimulationEvent event, SimulationContext context);
}
```

This gives you both:
- time-driven control
- event-driven reaction

That is likely to be useful.

---

## SimulationContext
This is where shared runtime infrastructure lives.

```java
package online.davisfamily.threedee.sim.framework;

import java.util.ArrayDeque;
import java.util.Queue;

public class SimulationContext {

    private final Queue<SimulationEvent> eventQueue = new ArrayDeque<>();
    private double simulationTimeSeconds;

    public void publish(SimulationEvent event) {
        eventQueue.add(event);
    }

    public Queue<SimulationEvent> getEventQueue() {
        return eventQueue;
    }

    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }

    public void setSimulationTimeSeconds(double simulationTimeSeconds) {
        this.simulationTimeSeconds = simulationTimeSeconds;
    }
}
```

This is enough for the next step.

---

## 7. Recommended SimulationWorld shape

```java
package online.davisfamily.threedee.sim.framework;

import java.util.ArrayList;
import java.util.List;

public class SimulationWorld {

    private final SimulationContext context = new SimulationContext();

    private final List<SimObject> simObjects = new ArrayList<>();
    private final List<Sensor> sensors = new ArrayList<>();
    private final List<SimulationController> controllers = new ArrayList<>();

    public void addSimObject(SimObject simObject) {
        simObjects.add(simObject);
    }

    public void addSensor(Sensor sensor) {
        sensors.add(sensor);
    }

    public void addController(SimulationController controller) {
        controllers.add(controller);
    }

    public SimulationContext getContext() {
        return context;
    }

    public void update(double dtSeconds) {
        context.setSimulationTimeSeconds(context.getSimulationTimeSeconds() + dtSeconds);

        updateSimObjects(dtSeconds);
        updateSensors(dtSeconds);
        dispatchEvents();
        updateControllers(dtSeconds);
    }

    private void updateSimObjects(double dtSeconds) {
        for (SimObject simObject : simObjects) {
            simObject.update(context, dtSeconds);
        }
    }

    private void updateSensors(double dtSeconds) {
        for (Sensor sensor : sensors) {
            sensor.update(context, dtSeconds);
        }
    }

    private void dispatchEvents() {
        while (!context.getEventQueue().isEmpty()) {
            SimulationEvent event = context.getEventQueue().poll();
            for (SimulationController controller : controllers) {
                controller.handleEvent(event, context);
            }
        }
    }

    private void updateControllers(double dtSeconds) {
        for (SimulationController controller : controllers) {
            controller.update(context, dtSeconds);
        }
    }
}
```

This is intentionally generic.
Nothing here knows about warehouses, totes, transfer zones, or stations.

That is exactly what you want.

---

## 8. How the current tote should fit

At this stage, make `Tote` implement `SimObject`.

That means:

- `Tote.update(...)` remains responsible only for its own movement/application of route-following
- `Tote` should not scan the world
- `Tote` should not own machine detection logic
- `Tote` should update `lastRouteSnapshot`
- `Tote` should apply its transform

So the tote becomes a clean generic participant in the simulation tick, even though it is still a warehouse-layer class.

That is a good fit.

---

## 9. What should sensors do in this phase?

Very little.

Do not rush into a full sensor framework.
Instead, prove the pattern with something tiny.

For example, create one trivial generic-friendly sensor implementation that:
- inspects some registered object(s)
- publishes a `SimulationEvent`

It does not even need to be used for transfer yet.

The point is to make sure the event path works.

### Example idea
A simple region/proximity sensor that watches a tote and publishes:
- entered region
- exited region

You can then later configure that for transfer zones, stations, conveyors, etc.

This is better than making the first sensor transfer-zone-specific.

---

## 10. What should controllers do in this phase?

Also very little.

Introduce the framework-side interface and perhaps one trivial controller or a stub warehouse controller that:
- receives events
- logs / updates simple state
- proves the event path works

Again, the goal is not to solve transfer now.
The goal is to verify the generic framework pattern.

---

## 11. Best “thin vertical slice” for this phase

This is the specific slice I would recommend implementing:

### Step A
Implement `SimulationWorld` and `SimulationContext`

### Step B
Register the current `Tote` as a `SimObject`

### Step C
Change the temporary bridge so it updates `SimulationWorld` once per frame

### Step D
Add one generic sensor that publishes one generic or lightly domain-specific event

### Step E
Add one controller that receives the event

### Step F
Do **not** enable transfer motion yet

If all of that works, you will have proven:
- world scheduling
- generic update phases
- object registration
- event publication
- controller reaction

That is a huge milestone.

---

## 12. Why this is better than going straight back to transfer work

Because once this is in place, all future warehouse subsystems will have an obvious home:

### Transfer zones
- machine = `StatefulSimObject`
- controller = `SimulationController`
- sensor = generic sensor configured for transfer region

### Lid stations
- same pattern

### Tippers
- same pattern

### Conveyors
- same pattern

Without this step, every new warehouse feature will force another architectural decision.

With this step, the framework shape becomes stable.

---

## 13. What to avoid in this phase

Avoid:
- writing a complicated sensor hierarchy
- adding lots of generics everywhere
- building a large command bus
- trying to rework every class at once
- reintroducing transfer movement now
- overfitting the framework to transfer-zone needs

This phase should be about **framework clarity**, not feature completeness.

---

## 14. Suggested phase after this one

Once the generic simulation framework is genuinely in place and tote travel is running through it, the next sensible phase would be:

## Reintroduce one warehouse behavior using the framework
Most likely:
- a transfer-zone approach/entry detection path
- but built using:
  - generic `Sensor`
  - generic `SimulationEvent`
  - `TransferZoneController implements SimulationController`
  - `TransferZoneMachine implements StatefulSimObject`

That would be the right moment to make warehouse-specific behavior active again.

---

## 15. Final recommendation

The next step should be:

**Make the generic simulation framework real and run the already-working movement through it before adding transfer behavior back in.**

That gives you:
- a stable runtime backbone
- clear ownership of update flow
- a place for sensors/controllers/events to live
- confidence that future warehouse features are being added on top of the framework rather than distorting it

---

## Final takeaway

If you want a slightly stronger next step than the previous note, this is it:

### Next implementation phase
1. implement `SimulationWorld`
2. firm up `SimulationContext`
3. use `SimObject`, `Sensor`, `SimulationController`, `SimulationEvent`, `StatefulSimObject` for real
4. run current tote movement through that framework
5. only then reintroduce warehouse-specific machinery logic

That is the most disciplined next move from where the codebase stands now.
