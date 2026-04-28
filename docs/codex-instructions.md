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
- the demo manifest currently uses 10 bag correlations (`bag-a` through `bag-j`) and therefore proves that the current planner/controller can use more than 3 PRLs without hard-coded lane assumptions
- `ToteToBagAssignmentPlannerTest` now proves assignment from a batch/order plan
- `ToteToBagFlowControllerTest.shouldKeepPrlAssignedUntilBatchPlanPackCountIsSatisfied` proves a PRL can remain assigned while only part of the batch-level expected pack count has arrived
- pharmaceutical pack dimensions are now treated as realistic enough for this automated path:
  - small: about 7.0 x 4.5 x 3.5 cm
  - medium: about 8.0 x 5.0 x 4.0 cm
  - long: about 9.0 x 5.5 x 4.0 cm
- larger packs are expected to be routed to a future manual station and should not complicate the current automated PRL/bagger path
- the bagger intake visuals have been corrected so each pack uses a pack-size-aware terminal intake distance and the final pack no longer remains partly outside the intake mouth

Recent commits on `feature/tote-track-tipper-rig`:

- `3f6a745 Document tote injector handoff state`
- `be0c0b4 Scale tote bag rig and stabilize PCR handoff`
- `9e6fb5e Smooth bagger intake pack visuals`
- `a05264b Add fifteen PRL debug layout profile`
- `369ace0 Exercise multiple PRLs in debug manifest`
- `59b95cc Finish bagger intake pack travel`

Known local state at handoff:

- `gradle.properties` is dirty and unrelated; leave it untouched unless the user explicitly asks otherwise.
- The latest uncommitted code/doc changes implement the batch/order-scoped PRL assignment prerequisite:
  - `app/src/main/java/online/davisfamily/warehouse/sim/totebag/plan/ToteToBagBatchPlan.java`
  - `app/src/main/java/online/davisfamily/warehouse/sim/totebag/assignment/ToteToBagAssignmentPlanner.java`
  - `app/src/main/java/online/davisfamily/warehouse/sim/totebag/control/ToteToBagFlowController.java`
  - `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
  - matching planner/controller tests
- The focused test set used recently was:
  `.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagAssignmentPlannerTest --tests online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayoutTest --tests online.davisfamily.warehouse.sim.totebag.PcrConveyorTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.BaggingMachineTest --tests online.davisfamily.warehouse.testing.DebugBagReceiverAutoEmptyControllerTest`
  It passed after the batch/order-scoped PRL assignment changes.

## Next Slice

The next intended slice is the tote injector / multi-tote feed. Do not create multiple independent `ToteToBagFlowController` instances or reinitialise the existing controller per tote.

The important architectural point is that PRL/bag assignments can span tote boundaries:

- packs for `bag-a` may appear in tote A and tote B
- the PRL assigned to `bag-a` must remain assigned until the expected pack count for that bag is complete
- tote boundaries must not clear or recreate PRL assignment state
- once a PRL releases a completed group, it can be reassigned to another incomplete bag correlation

Recommended implementation sequence for the next session:

1. Review the new `ToteToBagBatchPlan` path and the controller test proving partial arrival against a larger expected count.
2. Add a tote injector/feed owner that owns a queue/list of totes and feeds the next tote only when the tipper reports it can accept one.
3. Keep the tipper responsible only for local readiness / active-tote state; it should not know the full batch/order plan.
4. Add multi-tote debug fixtures/manifests where at least one bag correlation spans totes.
5. Use the 15-PRL profile to prove the spanning correlation completes only after packs from multiple totes arrive.
