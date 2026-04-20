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

## Current Code Position After Recent Refactors

The code has moved materially closer to the intended boundary, but the current reusable shape is still one composition level too coarse.

Recent completed cleanup:

- `TipperModule` now more clearly owns tipper-side visual sync and anchor math
- `SortingModule` now more clearly owns sorter-side render/anchor ownership
- `TipperEntryPackVisuals` now owns pack-renderable lifecycle and visual placement for the mounted entry section
- `TipperToSorterDischargeSeam` now owns the visible tipper-to-sorter transfer path math
- `TipperAssemblyFactory` now owns the mounted tipper assembly render construction
- tote-to-bag integrated upstream mount policy now lives in `ToteToBagCoreLayoutSpec` / `ToteToBagCoreLayout` rather than being rebuilt ad hoc in the rig

Current remaining issue:

- `TipperEntryModule` is still effectively a reusable "tipper plus sorter" section rather than a composition helper built from two independently mountable machines

This means the current code is improved, but it does not yet fully support swapping the sorter for another downstream machine or station without treating the pair as a special-case reusable unit.

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

Additional clarified requirement from later discussion:

- the tipper and sorter must be independently reusable and independently replaceable
- a helper/composition type specifically for "tipper to sorter" is acceptable
- that helper must not become the primary architectural abstraction for the tipper itself

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

Important clarification:

- `SortingModule` should remain independently mountable
- it should not be treated as an inherent part of a reusable tipper machine package
- the same principle should apply to future machines such as lid handling, strapping, exception/manual/third-party stations, and bagging

### `TipperToSorterDischargePath`

Role:

- dedicated cross-module seam object

Owns:

- tipper-to-sorter discharge path geometry/math
- sampling of world-space positions along the visible discharge path

Reason:

- this is the main remaining seam still owned by `TipperEntryModule`
- extracting it would materially narrow `TipperEntryModule`/installer ownership

Current note:

- this seam extraction has now happened in code under `TipperToSorterDischargeSeam`
- the broader architectural lesson remains important:
  machine-to-machine transfer seams should be explicit so adjacent machines can be swapped without collapsing them into one reusable type

## Additional Architectural Clarification: Independent Machines Plus Composition

Later discussion clarified that the main remaining architecture issue is not sorter-to-PDC coupling.

The more important remaining concern is that tipper-to-sorter is still too tightly packaged from the point of view of reuse.

The agreed direction is now:

- treat each machine as independently mountable
- expose explicit handoff points
- express machine-to-machine transfer through dedicated seam/composition objects
- keep scene/build code responsible for choosing which machines are connected

This is important because future machine families are expected to include:

- lid opening / closing machine
- tote strapping machine
- bagging machine
- exceptions station
- third-party station
- manual station

Those machines will not all share identical semantics, but they will benefit from a shared composition style.

### What Should Be Reusable

The reusable unit should usually be:

- an independently mountable machine module

not:

- a fixed pairing of adjacent machines

Examples:

- `TipperModule` should be reusable without `SortingModule`
- `SortingModule` should be reusable without `TipperModule`
- a future tipper could discharge into:
  - a sorter
  - another tote route section below
  - an exception/manual handling station
  - some other downstream machine

### What Kind Of Coupling Is Acceptable

It is acceptable to keep convenience composition helpers such as:

- `TipperToSorterSection`
- `TipperSorterSectionInstaller`

if they are clearly composition helpers and not the primary machine abstraction.

That means:

- `TipperEntryModule` in its current role is too close to being treated as the reusable machine unit
- the long-term direction should be to rename/reframe that shape as a composition helper built from independent machines plus a seam

### Lightweight Shared Convention Worth Introducing

A heavy common inheritance hierarchy is not required yet.

A lightweight shared route-mounted-machine convention would likely be enough:

- machine owns its runtime and renderables
- machine exposes handoff points
- machine can be mounted from external layout/route ownership
- machine-to-machine seams are explicit
- scene/build code composes machines and seams

This shared convention is the part that will help upcoming machines, not forcing identical state machines or identical topology objects.

## Work Still Required

The following work is still required to reach the now-clarified target architecture.

### 1. Reframe `TipperEntryModule`

Required work:

- stop treating `TipperEntryModule` as the reusable machine abstraction
- either rename it or materially document/restructure it as a composition helper for:
  - `TipperModule`
  - `SortingModule`
  - `TipperToSorterDischargeSeam`
  - any section-level visual helper/controller glue

Reasoning:

- the current name/shape still implies that "tipper plus sorter" is the primary reusable unit
- that makes it harder to replace the sorter with another downstream station later

### 2. Introduce A Small Independent-Machine Surface

Required work:

- define a lightweight convention or interface for independently mountable machines
- this may be minimal and should not over-generalise

Likely capabilities:

- expose root renderables / inspection roots
- expose handoff points
- support visual sync
- support explicit install/registration from scene/build code

Reasoning:

- the upcoming machine set is broad enough that a small common composition style will help
- a disciplined shared boundary is more valuable than a premature large abstraction hierarchy

### 3. Move Scene-Level Composition Out Of The Debug Rig

Required work:

- extract the remaining scene-level wiring from `ToteToBagDebugRig`
- create a builder/installer whose job is specifically to compose:
  - tote-to-bag core
  - mounted tipper
  - mounted sorter
  - seam/handoff objects
  - controllers

Reasoning:

- the debug rig should not remain the only place where the real production composition pattern exists
- production scenes should be able to ask for a mounted machine section without copying rig logic

### 4. Make Alternate Downstream Targets First-Class

Required work:

- ensure the tipper-side machine composition can target something other than a sorter
- likely by expressing downstream dependencies through explicit handoff/receive targets or seam installers

Examples to keep in mind:

- tipper -> sorter
- tipper -> lower tote track
- tipper -> exception/manual station

Reasoning:

- this is the concrete proof that the architecture no longer treats the sorter as part of the tipper’s identity

### 5. Revisit Sorter-To-PDC Only If It Helps The Same Boundary

Required work:

- sorter-to-PDC may later be expressed more explicitly if that improves independence/composition
- this is lower priority than tipper/sorter independence

Reasoning:

- PDC is realistically much more likely to be fed by a sorter than the tipper is to always feed a sorter
- tipper/downstream independence therefore carries more architectural value right now

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

Updated interpretation after later refactors/discussion:

- seam extraction and internal cleanup have progressed materially
- the next refactor priority is now independent-machine composition rather than more internal cleanup inside the current paired section

Updated recommended next sequence:

1. Reframe the current `TipperEntryModule` shape as a composition helper rather than a primary machine abstraction
2. Introduce a small explicit independent-machine convention/surface
3. Extract reusable scene/build composition out of `ToteToBagDebugRig`
4. Prove one alternate downstream target for the tipper beyond sorter ownership, or at least shape the seam/install API so such a target can be plugged in cleanly
5. Only then decide whether sorter-to-PDC also benefits from the same abstraction layer

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
4. Treat the current code position as improved but still not fully decoupled:
   - `TipperModule` and `SortingModule` should become the primary independently mountable units
   - the current paired section should become an explicit composition helper, not the primary tipper abstraction
5. Prefer the next implementation slice to focus on independent-machine composition:
   - reframe/rename the current paired section
   - define a lightweight independent-machine convention
   - extract production-facing composition out of the debug rig
   - keep future downstream replacement scenarios in mind
