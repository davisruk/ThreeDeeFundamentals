# Codex Instructions

## Purpose

This document is a session handoff note for follow-up work after the tipper / sorter separation cleanup.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/tote-to-bag-requirements.txt`
3. `docs/bagging_machine_requirements.txt`
4. `docs/tipper-route-mounted-machine-architecture.md`
5. If continuing the bagger/control-flow discussion, read the current transfer-zone classes before proposing architectural unification:
   - `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZone.java`
   - `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneMachine.java`
   - `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneController.java`

## Working Rule

Do not make any code or document changes unless the user explicitly agrees in the current session.

## Capacity-Safe Slice Rule

When capacity issues require using faster reasoning for implementation, use medium reasoning to define the slice first.

- Keep implementation slices small, explicit, and reversible.
- Do not broaden an implementation slice beyond the written brief.
- Do not commit incomplete or unsafe intermediate states.
- Stop and report if the brief conflicts with the code or requires a new architectural decision.
- Leave unrelated files untouched.

## Current Position

The main tipper / sorter architectural cleanup described in earlier sessions has now been completed materially.

Current code position:

- `TipperModule` is independently installed via `TipperSectionInstaller` / `TipperInstallation`
- `SortingModule` is independently installed via `SortingSectionInstaller` / `SortingInstallation`
- the old paired `TipperEntryModule` / builder shape has been removed
- `TipperToSorterSection` is now the explicit composition helper for the paired path
- route ownership is external to the mounted tipper installation through `TipperTrackSection`
- tote load-plan ownership is externalised through `ToteLoadPlanProvider`
- the tipper-side flow controller now uses a small downstream-capacity boundary (`TipperDownstreamFlow`)
- `TestScene` scene choice is now explicit through `DebugSceneOptions` / `DebugSceneKind` rather than comment-uncomment switching
- the integrated tote-to-bag harness composition now sits behind `IntegratedToteToBagDebugInstaller` / `IntegratedToteToBagDebugInstallation`
- sorter-outfeed into the tote-to-bag PDC is now represented by the named handoff target `SorterOutfeedToPdcReceiveTarget`
- the bagging machine is now installed through `BaggingModule` / `BaggingSectionInstaller` / `BaggingInstallation`
- the bagging intake side now has an explicit PCR-to-bagger readiness/reservation seam
- the bagging output side now has a generic `BagReceiver` / `BagReservation` seam rather than a tote-specific dependency
- completed bag output is now represented by first-class logical `Bag` objects, including logical pack contents
- `BagDischarge` is now wired into `BaggingMachine`; bag creation, chute discharge, and receiver completion are distinct lifecycle steps
- `ToteToBagFlowController` now depends on the generic `PackGroupReceiver` seam rather than on concrete `BaggingMachine`
- PRL assignment planning is now batch/order-scoped through `ToteToBagBatchPlan`; single-tote `ToteLoadPlan` inputs are still supported by deriving a batch plan from the tote plan
- `ToteToBagFlowController` now stores a batch plan separately from the currently loaded tote plan, so expected pack counts can outlive one tote manifest
- `StoredBagReceiver` now stores received runtime `Bag` objects and supports capacity gating
- the debug tote-to-bag harness now renders active bag discharge, uses an external stored receiver, and auto-empties that debug receiver after it has been full for a short timer
- bag visuals now use a first-pass `BagMeshFactory` paper-bag mesh rather than a simple box, but visual fidelity still needs refinement
- the branch now proves both:
  - `tipper -> sorter`
  - `tipper -> alternate debug-only receive target`

## Specific Direction To Continue From

Continue from the position described in `docs/tipper-route-mounted-machine-architecture.md`:

- machine-to-machine seams should stay explicit
- the sorter is no longer treated as part of the tipper's identity
- the current installed tipper flow should be treated as the repeatable pattern for future route-mounted machines:
  - installer plus install result
  - explicit handoff points
  - external route ownership
  - external load-plan ownership
  - explicit composition helper between adjacent machines
  - small downstream-flow boundary for local controller release / occupancy decisions
- likely follow-up work is now about applying the same pattern to future mounted machines and higher-level orchestration rather than continuing tipper / sorter untangling for its own sake
- for day-to-day running, prefer explicit scene launches via the `--scene=...` command-line switch or the matching VS Code launch profiles
- for bagging-machine follow-up, keep the PCR/PRL side coupled only to `PackGroupReceiver`; do not reintroduce direct `BaggingMachine` dependencies into the flow controller
- keep downstream receiver fullness and move-on policy outside `BaggingMachine`; the bagger should only react to whether its `BagReceiver` can reserve/receive a bag
- the current debug receiver auto-empty policy is proving equipment, not production tote move-on logic
- the integrated tote-to-bag harness now uses a 15-PRL debug profile via `ToteToBagCoreLayoutSpec.fifteenPrlIntegratedDebugDefaults()`
- the demo manifest now uses two source totes and 10 bag correlations (`bag-a` through `bag-j`) in the 15-PRL profile
- the multi-tote fixture is intentionally asymmetric so `bag-b` completes from tote 1 while `bag-a` remains incomplete until tote 2; this visually proves that later ready PRLs can release before earlier incomplete PRLs
- `ToteToBagAssignmentPlannerTest` now proves assignment from a batch/order plan
- `ToteToBagFlowControllerTest.shouldKeepPrlAssignedUntilBatchPlanPackCountIsSatisfied` proves a PRL can remain assigned while only part of the batch-level expected pack count has arrived
- `ToteToBagFlowControllerTest.shouldKeepPrlAssignedAcrossToteBoundaryUntilBatchCountIsMet` proves a spanning bag correlation completes only after packs from multiple totes arrive
- PRL release is deterministic but no longer strictly sequential by PRL id:
  - if an earlier ready PRL cannot reserve downstream, the controller scans later ready PRLs in id order and releases the first candidate that can reserve
  - when multiple ready PRLs can reserve, the lowest PRL id still wins
- pharmaceutical pack dimensions are now treated as realistic enough for this automated path:
  - small: about 7.0 x 4.5 x 3.5 cm
  - medium: about 8.0 x 5.0 x 4.0 cm
  - long: about 9.0 x 5.5 x 4.0 cm
- larger packs are expected to be routed to a future manual station and should not complicate the current automated PRL/bagger path
- the bagger intake visuals have been corrected so each pack uses a pack-size-aware terminal intake distance and the final pack no longer remains partly outside the intake mouth

Recent commits on `feature/tote-track-tipper-rig`:

- `3f6a745 Document tote injector handoff state`
- `d44aebf started multi tote per bag work`
- `cc7d635 Completed bag plans spanning totes`
- `009babc Changed PRL release to be deterministic not sequential`
- `be0c0b4 Scale tote bag rig and stabilize PCR handoff`
- `9e6fb5e Smooth bagger intake pack visuals`
- `a05264b Add fifteen PRL debug layout profile`
- `369ace0 Exercise multiple PRLs in debug manifest`
- `59b95cc Finish bagger intake pack travel`

Known local state at handoff:

- the worktree was clean after the user committed `009babc`
- the latest user-run focused tests passed:
  `.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.PcrConveyorTest --tests online.davisfamily.warehouse.sim.totebag.BaggingMachineTest`
- the user also confirmed the integrated visual check works after the asymmetric two-tote fixture

## Next Slice

The tote injector / multi-tote feed slice is now complete for the debug harness.

Current proven multi-tote state:

- `DebugToteInjectorController` owns queued debug totes and feeds the next tote only when `ToteTrackTipperFlowController.canAcceptNextTote()` is true
- `ToteTrackTipperFlowController` can release one tote and accept the next without being recreated
- one long-lived `ToteToBagFlowController` owns the PDC/PRL/PCR transport cell and does not reset PRL assignment state at tote boundaries
- `ToteToBagBatchPlan.fromToteLoadPlans(...)` aggregates expected pack counts across tote manifests
- the integrated 15-PRL harness uses a two-tote fixture with at least one spanning correlation
- the asymmetric fixture makes release order visible: `bag-b` completes from tote 1 and can release before `bag-a`, which completes from tote 2

The next architectural topic is machine architecture standardisation before scheduler work.

Important boundary:

- The controller should manage active PRL assignments, preserve them across tote boundaries, release completed groups, and eventually reuse idle PRLs for new bag correlations.
- The controller should not reorder totes or solve global batch scheduling.
- A deadlock is possible if all PRLs are reserved for incomplete bag correlations and the next tote contains only packs for new, unassigned correlations.
- That deadlock should be avoided by a future scheduler that chooses tote sequence, not by adding tote sequencing policy to PRL/PCR or tipper logic.
- Scheduler rules are expected to be large and dependent on the eventual full warehouse layout, tote release rules, buffer state, and machine state across the floor.
- Do not design or implement scheduler-facing APIs prematurely unless a local machine slice genuinely needs one.

Current roadmap:

1. Tidy the bagging-machine / receiver side so it follows the same installed-machine, local-state, explicit-seam style as the tipper/sorter/tote-to-bag work.
2. Implement the remaining warehouse machines using the same state architecture and installation approach:
   - lid opening machine
   - tote strapping machine
   - scheduler-controlled tote buffer where totes arrive and wait for release
3. Construct an entire warehouse layout with all machines installed and a few totes traversing it.
4. Write a dedicated scheduler requirements document once real machine state, layout constraints, release rules, and buffer behaviour are visible.
5. Implement tote release / scheduling and tote injection after those requirements are concrete.

Dynamic PRL reassignment remains future work, but it should stay local to tote-to-bag:

- active assignments stay pinned until completed release
- idle PRLs may be assigned to not-yet-complete unassigned correlations
- tote ordering and global deadlock avoidance remain scheduler responsibilities
