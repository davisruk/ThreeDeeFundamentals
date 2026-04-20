# Tipper Route-Mounted Machine Architecture

## Purpose

This note records the design discussion and decisions made for refactoring the current tipper-entry work toward the same architectural style as the existing transfer-zone system, without forcing the tipper into transfer-zone semantics.

The intent is that a future session can read this document and continue the refactor from the agreed direction rather than inferring it from code or chat history.

## Context

The current tote-to-bag work has a reusable `TipperEntryModule`, but that type still reflects its proving-harness origin.

In the current code:

- `TipperEntryModule` builds a short internal route section for the tipper entry
- `TipperEntryModule` creates a demo `Tote`
- `TipperEntryModule` creates a demo `ToteLoadPlan`
- `TipperEntryModule` wires runtime/controller/render concerns together
- `TipperEntryModule` still owns the cross-module tipper-to-sorter discharge seam logic

This is acceptable for the current thin slice and for isolated proving rigs, but it is not the intended production boundary for a plug-together warehouse application.

## Transfer-Zone Pattern To Align With

The transfer system currently provides the architectural style that the tipper should follow.

The current transfer-side shape is:

- `RouteSegment`
  - generic route/path topology only
- `TransferZone`
  - warehouse-owned topology/config attached to a source segment
- `TransferZoneMachine`
  - runtime/state holder
- `TransferZoneController`
  - orchestration and transfer startup logic
- `TransferZoneMechanism`
  - mounted device/mechanism state and render-driving behaviour

Important clarification:

- the tipper should not be modeled as a `TransferZone`
- the tipper should instead follow the same overall route-mounted-machine convention and API style

That means:

- generic route code remains generic
- warehouse-owned mounted-site topology is explicit
- machine runtime/state is separate from topology
- controller/orchestration is separate from runtime
- render/mechanism ownership is separate from controller logic
- installation/wiring is explicit

## Agreed Architectural Position

### High-Level Direction

The tipper should be treated as a route-mounted machine family, analogous in architectural style to transfer machines, but not identical in semantics.

The tipper differs from transfer zones because:

- the tote rides onto the machine-owned section itself
- the tipper captures, holds, tips, and releases a tote
- the tipper is the explicit boundary where tote route transport transitions into pack conveyor-local transport
- the tipper includes a machine-owned track section rather than only a side-mounted transfer device

Even so, the same boundary discipline should apply:

- site/topology object
- machine/runtime object
- controller/orchestration object
- render/mechanism/module objects
- installer/install-result API

### What Must Change From The Current `TipperEntryModule`

The reusable production-facing tipper installation should not own:

- tote creation
- demo load-plan creation
- private internal route-scene ownership

The reusable production-facing tipper installation should own:

- mounted machine assembly
- machine-local state and controller wiring
- explicit tote/machine interaction boundary
- downstream pack handoff points
- machine-local renderables and anchor math

## Decisions Made

### 1. Tote Interaction Model

Agreed direction:

- internal tipper machine/controller logic should use a direct `Tote` reference

Discussion outcome:

- if the codebase later moves away from id-based object lookup for transfer zones, the same direct-reference direction should be used there too
- the tipper should not establish a one-off permanent identity model that diverges from the eventual transfer-zone direction

Practical implication:

- `TipperMachine` should store the currently engaged tote as a direct reference
- the initial mechanism by which that tote reference is obtained may be transitional in the first slice

### 2. Load-Plan Ownership

Agreed direction:

- do not move tote pack/load metadata onto `Tote` yet
- use a provider keyed by tote id for now

Rationale:

- current `Tote` ownership is still more aligned with movement/render/traversal concerns than with warehouse business metadata
- pack/load ownership can be revisited later

Practical implication:

- introduce something like `ToteLoadPlanProvider`
- tipper controller obtains the active tote’s pack plan via that provider

### 3. Route Ownership

Agreed direction:

- the production/reusable tipper should mount onto an externally created route section

Rationale:

- this matches the transfer-zone installation style more closely
- it avoids the reusable machine module becoming a mini-scene that creates private route infrastructure internally

Practical implication:

- scene/layout code or a broader warehouse builder owns the route section
- the tipper installation attaches to that route section

### 4. Capture Semantics

Agreed direction:

- section-local controller logic is acceptable for the first implementation
- the main requirement is that the tipper controller ends up with a clean tote reference boundary

Rationale:

- the tipper behaves like a machine-owned route section rather than like a transfer zone with separate window sensors
- a simpler section-local capture rule is acceptable if the resulting machine/controller boundary remains clean

Practical implication:

- first implementation may resolve the arriving tote from tracked objects relevant to the mounted section
- once captured, the machine/controller should operate on the direct tote reference

### 5. Public API Style

Agreed direction:

- installer plus install result

Rationale:

- this reads more like mounted infrastructure installation and less like a self-contained harness object
- it better matches the transfer-style architecture goal

Practical implication:

- replace or materially reshape the current `TipperEntryModuleBuilder`
- return a compact installation result exposing runtime, modules, renderables, and handoff points

## Resolved Interpretation Of The Tote Reference Question

The key open question was not whether the target should use a direct `Tote` reference, but how that reference should be obtained.

The agreed interpretation is:

- the target design is direct tote reference inside machine/controller logic
- the first slice does not need to force an immediate global simulation-event refactor
- the tipper controller may initially acquire the tote reference through section-local resolution/capture logic
- once the tipper path is proven, the transfer-zone path can later be updated to use the same direct-reference mechanism

This means:

- direct reference is the architectural target
- the first acquisition mechanism can be transitional
- transfer-zone identity cleanup is expected later, not ignored

## Agreed Target Class/Responsibility Shape

### `TipperSection`

Role:

- warehouse-owned mounted machine site/topology object
- analogous in role to `TransferZone`, but not identical in semantics

Owns:

- id
- externally owned mounted route segment/section
- capture distance/window
- release distance/window
- fixed machine/layout config relevant to the mounted section

Does not own:

- tote creation
- renderable creation
- controller logic
- pack runtime state

### `TipperSectionSpec`

Role:

- tipper section/site configuration record

Likely contents:

- tipped angle
- tolerances
- machine-owned local geometry/layout values
- sorter-relative mount offsets if needed

### `TipperMachine`

Role:

- runtime/state object analogous in role to `TransferZoneMachine`

Owns:

- machine state enum
- engaged tote reference
- engaged tote id for diagnostics/transitional compatibility
- time in state
- reference to the associated `TipperSection`

Possible states:

- `IDLE`
- `WAITING_FOR_TOTE`
- `CAPTURED`
- `TIPPING`
- `DISCHARGING`
- `RETURNING`
- `RELEASING`
- `BLOCKED`

### `TipperController`

Role:

- orchestration/controller analogous in role to `TransferZoneController`

Owns:

- capture logic for an arriving tote on the mounted section
- hold/tip/discharge/release sequencing
- load-plan lookup through `ToteLoadPlanProvider`
- handoff into downstream pack target(s)

Does not own:

- tote creation
- route creation
- renderable construction

### `ToteLoadPlanProvider`

Role:

- external provider for tote contents keyed by tote id

Reason:

- current decision is to keep pack/load metadata outside `Tote` for now

### `TipperModule`

Role:

- tipper-side mounted render/mechanism assembly

Current status:

- this already largely exists and should be retained

Owns:

- tipper assembly renderables
- tip-angle visual sync
- tipper-side anchor/handoff math

### `SortingModule`

Role:

- sorter-side mounted render/mechanism assembly

Current status:

- this already largely exists and should be retained

Owns:

- sorter renderable assembly
- sorter intake/outfeed handoff points
- sorter queued-pack visual placement

### `TipperToSorterDischargePath`

Role:

- dedicated cross-module seam object

Owns:

- tipper-to-sorter discharge path geometry/math
- sampling of world-space positions along the visible discharge path

Reason:

- this is the main remaining seam still owned by `TipperEntryModule`
- extracting it would materially narrow `TipperEntryModule`/installer ownership

### `TipperSectionInstaller`

Role:

- public production-facing install surface

Owns:

- creation/wiring of `TipperMachine`
- creation/wiring of `TipperController`
- creation of `TipperModule`
- creation of `SortingModule`
- creation of `TipperToSorterDischargePath`
- simulation/controller/render registration

Returns:

- a compact `TipperInstallation` result

### `TipperInstallation`

Role:

- install result / installed package

Likely contents:

- `TipperSection`
- `TipperMachine`
- `TipperController`
- handoff point provider
- `TipperModule`
- `SortingModule`
- renderables / inspection roots

## What Happens To Existing Types

### Keep And Reuse

- `TipperModule`
- `SortingModule`

These already align reasonably well with the intended render/mechanism ownership split.

### Replace Or Materially Reshape

- `TipperEntryModule`
- `TipperEntryModuleBuilder`

These are currently still too close to the original proving-harness shape.

### Extract For The Next Cleanup Slice

- `TipperToSorterDischargePath`

This is the narrowest remaining seam decision already identified in prior sessions.

### Keep As Harness-Only / Demo-Only Ownership

- demo tote creation
- demo `ToteLoadPlan` creation
- self-contained isolated route setup used only for the proving rig

Those responsibilities should not remain in the production-facing installed machine package.

## Recommended Refactor Sequence

The agreed recommendation is not to jump directly to the final production shape in one change. The safer sequence is:

1. Introduce the missing boundary types:
   - `TipperSection`
   - `TipperSectionSpec`
   - `TipperMachine`
   - `ToteLoadPlanProvider`
2. Extract `TipperToSorterDischargePath` from the current `TipperEntryModule`
3. Move tote creation and demo load-plan creation out of the reusable tipper package and into the proving rig / harness wrapper
4. Replace the current builder shape with installer plus install result
5. Mount the reusable tipper installation against externally created route infrastructure
6. After tipper behaviour is validated under the new boundary, revisit transfer-zone identity flow and align it with the direct-reference direction

## Important Non-Goals For This Refactor

The following were explicitly not the aim of the discussion:

- do not remodel the tipper as a `TransferZone`
- do not force the tipper and transfer-machine families into identical topology/runtime classes
- do not move tote load metadata onto `Tote` yet
- do not widen this slice into a full global simulation-event redesign before the tipper boundary is proven

## Next Session Starting Point

When resuming work in the next session:

1. Read this document first.
2. Treat the tipper as a route-mounted machine family that should match transfer-zone architectural style, not transfer-zone semantics.
3. Keep the goal focused on boundary cleanup:
   - external route ownership
   - external tote ownership
   - installer/install-result surface
   - direct tote reference inside tipper machine/controller logic
   - load-plan lookup through provider
4. Prefer the next implementation slice to start with the missing boundary types and the discharge-path seam extraction rather than immediately attempting the entire production integration at once.

