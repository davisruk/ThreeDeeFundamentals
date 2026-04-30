# Conveyor Drive Representation

## Decision

Conveyors are now modelled as a warehouse track drive type alongside rollers, not as a replacement for them.

- Routing remains generic and unchanged.
- Warehouse rendering owns whether a segment is roller-driven or conveyor-driven.
- Tote motion remains route-following rather than physics-driven.

## Model Layer

`TrackSpec` now carries an explicit `TrackDriveType`.

- `ROLLER` means the running surface is represented by roller geometry.
- `CONVEYOR` means the running surface is represented by a belt and end-return loop.
- `NONE` is reserved for future passive/non-driven sections.

Drive-specific dimensions remain warehouse-owned in `TrackSpec`.

- Roller sections use roller pitch/height/depth.
- Conveyor sections use belt thickness, return depth, width inset, and animated marker spacing.
- `getLoadSurfaceHeight()` abstracts the tote-supporting height so tote placement does not need to know whether the segment is a roller bed or a belt.

## Rendering Layer

Roller sections still render spinning rollers per sampled interval.

Conveyor sections render as:

- the existing structural deck
- a belt surface mesh running over the render span
- rotating end rollers at the span ends
- animated belt markers that move over the top run, around the end rollers, back along the underside, and up again at the start

This keeps the belt visually alive without adding engine-level texture scrolling or physics.

## Simulation Layer

The simulation still treats both rollers and conveyors as route-following support surfaces.

- `RouteFollower` continues to own path traversal only.
- `Tote` still advances by route speed and transfer state.
- Transfers still work between segments regardless of drive type because they depend on route geometry and transfer-zone config, not on the rendered surface implementation.

This is intentional for now: conveyors differ visually and semantically as warehouse equipment, but they do not yet introduce accumulation, slip, or zone-control physics.

## Current Behavioural Difference

At the moment the main behavioural difference is representational:

- rollers expose discrete rotating support elements
- conveyors expose a continuous driven belt loop

Future warehouse-only simulation changes can build on `TrackDriveType` if conveyor zones later need start/stop control, accumulation, or tote speed ownership.
