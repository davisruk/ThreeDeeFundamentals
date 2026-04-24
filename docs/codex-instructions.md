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
- likely next bagging-machine work is visual/receiver refinement: better bag mesh, production-shaped external tote/bin receiver, and later a real tote move-on controller
