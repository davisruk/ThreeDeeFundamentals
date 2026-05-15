# Transfer Machine Requirements

Status: current design target for `feature/inline-transfer-targets`.

## Purpose

Transfer machines move totes between route segments when the tote cannot simply follow the next normal route connection.

The transfer model must support:

- simple source-to-target transfers between adjacent tracks
- inline junctions where one source can route to one of multiple targets
- future layouts where a transfer can receive from more than one source
- separate visual presentations for the same transfer behaviour

This document supersedes the earlier assumption that a transfer machine must be modelled as a transfer interval overlaid onto a larger source track.

## Core Concepts

### Transfer Abstraction

A transfer is a machine/control concept. It owns the decision about whether a tote continues or is routed to a selected target.

The transfer abstraction should know:

- the source window or transfer route segment where the tote is controlled
- the possible transfer targets
- the strategy/controller used to choose a target
- the motion configuration used while moving between segments
- the orientation policy to apply during or after transfer

The transfer abstraction must not require a specific renderable model.

### Transfer Renderable

The renderable transfer machine is visual only.

Examples:

- the current two-conveyor machine, where two small conveyors rotate about Y before the tote arrives
- a future track-piece machine with no guides, where the whole bed rotates about Y
- a future custom turntable or lift-transfer visual

The renderable may be attached to a transfer route segment or transfer window, but the transfer behaviour must not be baked into the renderable mesh.

### Route Segments

The preferred route topology is to represent the transfer-controlled area as a short explicit route segment or route window.

For an inline transfer:

```text
source track -> transfer window segment -> default/continue target
                           |
                           +-> alternate target
```

The source track should not continue underneath the transfer machine. The transfer-controlled region should be explicit.

Target tracks should not receive automatic guide openings just because they are transfer targets. Guide openings and guide suppression are layout/render metadata only and should be applied only where the physical layout needs them.

### Receiver Track Pieces

Some parallel transfer layouts may need a short ordinary receiving track piece opposite the transfer machine.

Example:

```text
--------   --------
-> 1 -> |T| -> 2 ->
--------   --------
--------   --------
<- 4 <-  R  <- 3 <-
-------------------
```

`T` is the transfer machine. `R` is just a normal route segment/track piece. It has no special transfer behaviour. It may have guide metadata to suppress one guide side if the physical layout requires an opening towards `T`.

Do not introduce receiver-machine behaviour for `R` unless a later requirement needs it.

## Routing Behaviour

The route follower should continue to handle ordinary route traversal.

Transfer remains an explicit machine action:

1. A tote enters the transfer-controlled segment/window.
2. The transfer controller asks its strategy for a routing decision.
3. A `CONTINUE` decision lets the tote follow the normal route connection.
4. A transfer decision reserves the tote, waits for mechanism readiness, and calls tote transfer motion to land on the selected target segment.
5. Normal route following resumes after transfer completion.

Do not turn `RouteFollower` into a general routing/scheduler decision engine in this work.

## Transfer Targets

A transfer target must explicitly include:

- target `RouteSegment`
- target entry distance
- target travel direction

Target travel direction must not be inferred from the source tote's current direction.

## Orientation Policy

Transfer pathing and tote orientation are separate.

A transfer decision or machine configuration should state how tote orientation is handled.

Required initial policies:

- `PRESERVE_TOTE_ORIENTATION`: keep the tote yaw it had before transfer. This matches the current two-conveyor machine when the conveyors rotate before arrival and do not rotate the tote.
- `ALIGN_TO_TARGET_TRAVEL`: align the tote yaw to the selected target segment/travel direction.

Useful future policy:

- `ALIGN_TO_MECHANISM`: align the tote yaw to a rotating bed or turntable mechanism.

Do not hard-code one orientation behaviour into `Tote.beginTransfer(...)`.

## Guide And Track Rendering Rules

Guide openings are not transfer decisions.

Rules:

- source guide suppression/openings are explicit layout metadata
- target guide suppression/openings are explicit layout metadata
- target guide openings are not added automatically for every transfer target
- a transfer machine renderable may be guide-less without forcing all transfer segments to be rendered as track meshes
- `suppressGuidesInTransferZones` should either be made effective or replaced by clearer metadata

## Current Problems To Fix

The current inline visual rig exposes these issues:

- `TransferZone` is still represented as an interval on a source segment.
- `WarehouseRouteBuilder.addInlineTransfer(...)` adds target guide openings automatically.
- `GuideSide.RIGHT` is local to segment direction, so opposite-direction target tracks appear to have openings on opposite physical sides.
- source track rendering can still appear under or through the transfer machine.
- visual footprint calculations are duplicated between fixture layout and machine renderable creation.

The implementation plan should remove these causes rather than continuing to tune fixture coordinates.

## Non-Goals

Do not implement in this slice:

- adapting station state
- DSP scheduler changes
- a general route graph decision engine
- multiple-source transfer routing unless it falls out naturally from the new abstraction
- phase 2 visual polish

