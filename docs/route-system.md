# Path / Route / Track / Follower Architecture Guide

## Overview

This guide explains how to construct and use the routing system for moving objects (e.g. totes) across tracks.

The system is composed of:

- **PathSegment3** → geometry
- **RouteSegment** → graph structure
- **TransferZone** → optional lateral movement between segments
- **TrackRouteFactory** → visual track generation
- **GraphFollowerBehaviour** → movement logic

---

# 1. Build Geometry (`PathSegment3`)

Create the physical shape of the route.

## Common types

- `LinearSegment3` → straight
- `BezierSegment3` → curve

## Example

```java
PathSegment3 top = new LinearSegment3(
    new Vec3(0f, 0f, 0f),
    new Vec3(8f, 0f, 0f)
);

PathSegment3 curve = new BezierSegment3(
    new Vec3(8f, 0f, 0f),
    new Vec3(10f, 0f, 0f),
    new Vec3(10f, 0f, -3f),
    new Vec3(8f, 0f, -3f)
);
```

---

# 2. Wrap Geometry in `RouteSegment`

Each geometry segment must be wrapped in a `RouteSegment`.

## Responsibilities

- Connectivity (graph)
- Transfer zones
- Debug identity

## Example

```java
RouteSegment topRoute = new RouteSegment("top", top);
RouteSegment curveRoute = new RouteSegment("curve", curve);
```

---

# 3. Connect Segments

Define how movement flows between segments.

## Standard connection (start of next segment)

```java
topRoute.connectTo(curveRoute);
```

## Connection with entry distance (mid-segment entry)

```java
linkRoute.connectTo(bottomRoute, 3.0f);
```

Use this when:
- joining into the middle of a segment
- linking parallel tracks
- transfer-fed entry points

---

# 4. Add Transfer Zones

A `TransferZone` allows movement from one segment to another.

## Key properties

- Start distance (on source segment)
- Length (zone span)
- Target segment
- Target entry distance
- Decision strategy

## Example

```java
TransferZone zone = new TransferZone(
    4.5f,
    1.0f,
    linkRoute,
    0.0f,
    new ToggleTransferStrategy(true)
);

topRoute.getTransferZones().add(zone);
```

## Rules

- Transfer zones belong to the **source segment**
- Multiple zones per segment are supported
- Each zone has its **own strategy**

---

# 5. Build Track Renderables

Once the route is defined, generate the visible track.

```java
List<RenderableObject> trackObjects =
    RouteTrackFactory.createRenderableTracks(
        triangleRenderer,
        List.of(topRoute, curveRoute, linkRoute),
        trackSpec,
        trackAppearance
    );
```

---

# 6. Create Moving Object

```java
RenderableObject tote = RenderableObject.create(
    triangleRenderer,
    toteMesh,
    new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f),
    colourStrategy
);
```

---

# 7. Attach `GraphFollowerBehaviour`

This drives movement across the route.

```java
GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
    topRoute,
    null,
    1.0f,
    WrapMode.LOOP,
    EnumSet.of(OrientationMode.YAW),
    0f
);

tote.behaviours.add(follower);
```

## For debugging movement only

```java
EnumSet.of(OrientationMode.NONE)
```

---

# 8. Add to Scene

```java
sceneObjects.addAll(trackObjects);
sceneObjects.add(tote);
```

---

# Recommended Build Order

1. Create `PathSegment3` geometry  
2. Wrap in `RouteSegment`  
3. Connect segments  
4. Add `TransferZone`s  
5. Build track renderables  
6. Create moving object  
7. Attach follower  
8. Add to scene  

---

# Mental Model

## PathSegment3
Defines shape only.

## RouteSegment
Defines connectivity + behaviour zones.

## TransferZone
Defines optional lateral movement.

## GraphFollowerBehaviour
Executes traversal logic.

## TrackRouteFactory
Creates visual representation.

---

# Orientation Behaviour

| Segment Type    | Yaw Behaviour              |
|----------------|----------------------------|
| Linear         | Constant (entry yaw)       |
| Bezier         | Follows curve              |
| Transfer Zone  | Frozen during transfer     |

---

# Rules of Thumb

### Use `connectTo(next)`
When segments meet at the start.

### Use `connectTo(next, entryDistance)`
When entering mid-segment.

### Transfer zones
- Always attach to **source**
- Keep zones non-overlapping

### Debugging
- Disable orientation first
- Verify movement
- Then enable yaw

---

# Common Mistakes

### Missing connection
→ follower resets or stops

### Wrong entry distance
→ object jumps to wrong position

### Transfer zone on target segment
→ transfer never triggers

### Overlapping zones
→ unpredictable behaviour

### Debugging yaw before movement
→ misleading results

---

# Minimal Example

```java
// Geometry
PathSegment3 top = new LinearSegment3(...);
PathSegment3 link = new LinearSegment3(...);
PathSegment3 bottom = new LinearSegment3(...);

// Route segments
RouteSegment topRoute = new RouteSegment("top", top);
RouteSegment linkRoute = new RouteSegment("link", link);
RouteSegment bottomRoute = new RouteSegment("bottom", bottom);

// Connections
topRoute.connectTo(linkRoute);
linkRoute.connectTo(bottomRoute, 3.0f);

// Transfer zone
topRoute.getTransferZones().add(new TransferZone(
    4.5f,
    1.0f,
    linkRoute,
    0.0f,
    new ToggleTransferStrategy(true)
));

// Track
List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(...);

// Tote
RenderableObject tote = RenderableObject.create(...);

// Follower
tote.behaviours.add(new GraphFollowerBehaviour(
    topRoute,
    null,
    1.0f,
    WrapMode.LOOP,
    EnumSet.of(OrientationMode.NONE),
    0f
));

// Scene
sceneObjects.addAll(tracks);
sceneObjects.add(tote);
```

---

# Summary

This architecture separates concerns cleanly:

- **Geometry** → PathSegment3  
- **Topology** → RouteSegment  
- **Dynamic routing** → TransferZone  
- **Movement** → GraphFollowerBehaviour  
- **Rendering** → TrackRouteFactory  

Once you follow the build order, the system becomes predictable and easy to extend.
