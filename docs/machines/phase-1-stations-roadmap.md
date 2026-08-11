# Phase 1 Station Roadmap

Status: active planning. Use this roadmap before creating branch-specific station plans.

## Summary

This roadmap pauses deeper scheduler behavior work so the remaining warehouse stations can be introduced with consistent machine state, queue, routing, and scheduler-facing availability surfaces.

Phase 1 station work should be state-complete and visually cheap. The goal is to prove tote routing, station queues, processing state, scheduler decisions, and logical pack/tote effects across a whole warehouse layout. Detailed meshes, realistic pack transfer animation, bins/racks, polished station visuals, and operator controls are deferred to Phase 2 visualisation work.

The generic transfer-machine work and Adapting Station Phase 1 are complete and merged. The short `feature/simulation-reset` runtime branch is complete and verified, pending merge. After reset is merged, create the detailed plan for Third-Party Station Phase 1 from updated `master`.

Phase 1 stations may use placeholder renderables, simple inspection overlays, and "magical" pack appearance/disappearance where needed. That is acceptable as long as domain state, machine state, and scheduler-facing state are coherent and testable.

## Phase Split

Phase 1:

- Add station state machines, wait queues, route stops, and processing timers.
- Add the logical inventory/state changes each station owns.
- Add minimal placeholder renderables only where needed to locate/select the station.
- Add inspection text for machine state, queue state, and logical inventory counts.
- Integrate scheduler release/admission against station queue and readiness state.
- Keep pack movement animation and detailed station visuals out of scope.

Phase 2:

- Add realistic visual layouts for stations.
- Add detailed pack/bin/rack/bag/tote presentation.
- Add transfer animation between totes, machines, bins, and output areas.
- Add richer operator interaction, command buttons, and exception workflows.
- Tune station geometry and route positions for production-like layouts.

## Station Sequence

### 0. Inline Transfer Targets

Purpose:

- Extend the existing transfer-zone machinery so one physical transfer window can route a tote to a selected target segment/direction.
- Support inline transfer layouts needed around the adapting benches.
- Keep the change generic so later stations can use it.

Detailed implementation doc:

- `docs/machines/inline-transfer-targets-plan.md`

Phase 1 expectations:

- Add an explicit transfer target/result model.
- Keep existing direct transfer and transfer-to-link behaviour working.
- Make target travel direction explicit for transfer completion.
- Add focused tests for one source window selecting between multiple targets.
- Do not add adapting station logic in this branch.

### 1. Adapting Station Phase 1

Status: complete and merged to `master`.

Purpose:

- Introduce the most complex merge/preparation station first.
- Support the two adapting visit reasons:
  - `STORE`: adapted/preparation totes arrive and prepared packs are removed into logical station storage.
  - `COLLECT`: collecting/dispatch totes arrive and prepared packs are added from logical station storage before travelling onward to P2P/tote-to-bag.

Phase 1 expectations:

- One adapting station state machine with operation mode/request type, not two separate station types.
- A station input wait queue with manually configured capacity.
- A logical prepared-pack store keyed by the existing prepared-line identity, or by a small closely related key if the existing `PreparedLineKey` is not sufficient.
- Loaded ADAPTED prepared lines represent work to process, not completed readiness. Adapted `PreparedLineKey`s become scheduler-ready after the station processes a `STORE` visit, except for fixtures that explicitly seed already-staged startup state.
- Placeholder stop/processing point where source packs disappear for `STORE`.
- The source `ADAPTED` tote is removed/stored after `STORE` and can disappear in Phase 1.
- Placeholder stop/processing point where prepared packs reappear in the collecting tote for `COLLECT`.
- `COLLECT` updates the collecting tote load plan so P2P can act on the newly collected packs.
- `FULL_PACK` orders never collect adapted lines.
- Scheduler readiness should eventually be able to ask whether required adapted lines for a target dispatch tote are available.
- No rendered racks/bins or animated pack transfer in Phase 1.

Implemented notes:

- Multi-bench adapting area with deterministic capacity/store-affinity selection.
- Logical `bench -> rack -> shelf -> bin` storage allocation exists without rendered racks/bins.
- STORE totes disappear after processing in the debug rig.
- COLLECT totes update their load plan and return to the main line.
- The adapting debug scene uses explicit bench stop sensors and six visible bench placeholders.

### Runtime Interlude: Simulation Reset

Status: complete and verified on `feature/simulation-reset`, pending merge to `master`.

Detailed implementation doc:

- `docs/runtime/simulation-reset-plan.md`

Purpose:

- Add an `ALT+R` command that safely rebuilds the active debug simulation.
- Add the missing debug-runtime lifecycle needed to close scheduler workers before scene replacement.
- Preserve the active scene kind, camera, and display modes while resetting simulation-owned state.
- Keep rewind/forward and simulation/render thread separation out of scope.

### 2. Third-Party Station Phase 1

Purpose:

- Add a station for third-party product handling/merge behavior.

Phase 1 expectations:

- Input wait queue and placeholder processing state.
- Logical inventory/readiness state sufficient for scheduler dependency checks.
- Minimal renderable/inspection only.
- No detailed third-party handling visuals.

### 3. Manual Station Phase 1

Purpose:

- Add manual item handling and manual merge behavior after P2P where required.

Phase 1 expectations:

- Input wait queue and placeholder processing state.
- Support manual item flow and manual merge readiness at domain/state level.
- Keep manual tote/order assumptions aligned with the DSP scheduler requirements.
- No detailed operator/manual-work visuals.

### 4. Exception Station Phase 1

Purpose:

- Add a destination/state path for exception bags/totes created by future manual override or deadlock recovery workflows.

Phase 1 expectations:

- Placeholder station and queue.
- Minimal logical exception intake/state.
- Do not add full command-panel override workflows in Phase 1 unless explicitly planned.

### 5. Tote Lid Open/Close Machines Phase 1

Purpose:

- Replace rig-only lid behavior with explicit machine-state handling.

Phase 1 expectations:

- Add simple open/close machine state and route stops.
- Support opening lids before stations that need pack visibility/access.
- Support closing lids after stations before onward travel where appropriate.
- Replace or reduce reliance on `DebugToteLidController` once real machine flow is available.
- Detailed lid opener visuals are Phase 2.

## Layout Direction

The station work should move toward a whole-warehouse debug layout where a small number of totes can travel through:

- OSR/buffer release area
- lid open/close machines
- adapting station
- third-party station
- manual station
- P2P/tote-to-bag
- exception station where needed

The first layout does not need production geometry. It must provide enough route segments, station stops, wait queues, and placeholder renderables to verify scheduler-driven tote movement and station processing.

## Scheduler Boundary

Schedulers decide when to release totes based on immutable snapshots, station queue capacity, dependency readiness, and service-centre rules.

Stations own:

- live machine state
- input wait queues
- processing timers/state
- logical inventory mutation
- local admission/processing rules

The scheduler worker must not mutate machines, queues, renderables, or totes directly. Simulation-thread controllers apply commands and mutate live state.

## Documentation Rules

Each station should get its own detailed plan before coding. Plans should:

- name the branch
- keep Phase 1 scope explicit
- list allowed files and new package locations
- break implementation into small ordered steps
- include focused Gradle commands
- state which visuals are placeholders
- state which visual/animation work is deferred to Phase 2

Suggested branch names:

- `feature/inline-transfer-targets`
- `feature/adapting-station-phase-1`
- `feature/simulation-reset`
- `feature/third-party-station-phase-1`
- `feature/manual-station-phase-1`
- `feature/exception-station-phase-1`
- `feature/tote-lid-open-close-phase-1`

## Deferred Phase 2 Visualisation

Create a separate Phase 2 visualisation roadmap after Phase 1 station behavior is proven.

Phase 2 should include:

- adapting racks and logical bin presentation
- pack transfer animation between tote and station storage
- third-party/manual station visual detail
- exception station operator-facing visuals
- polished lid opener/closer meshes and motion
- richer selection and command-panel controls

Do not spend Phase 1 implementation time on Phase 2 visual detail unless it is required to verify state correctness.
