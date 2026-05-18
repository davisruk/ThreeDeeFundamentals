# Codex Instructions

## Purpose

This document is the entry-point handoff for follow-up Codex sessions. The current direction is to introduce a DSP/OSR scheduler as a domain-first implementation, while preserving the local machine-state architecture already established in the tote-to-bag/P2P work.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp_osr_scheduler_requirements.md`
3. `docs/scheduler/dsp-scheduler-implementation-plan.md`
4. `docs/machines/phase-1-stations-roadmap.md`
5. The branch-specific plan referenced by the active roadmap

Read these domain documents when touching their areas:

- `docs/tote-to-bag-requirements.txt`
- `docs/bagging_machine_requirements.txt`
- `docs/tipper-route-mounted-machine-architecture.md`

If continuing transfer-zone or mounted-machine architectural discussion, inspect the current transfer-zone classes before proposing unification:

- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZone.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneMachine.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneController.java`

## Working Rule

Do not make code or document changes unless the user explicitly agrees in the current session.

The user normally wants architecture discussions to produce a plan that a weaker model can execute step by step. Plans should be decision-complete for the selected slice:

- keep implementation slices small, explicit, and reversible
- limit each slice to the current domain's files where practical
- creation of new files is fine when it avoids awkward coupling
- avoid unnecessary refactors
- include expected output per step
- include the focused Gradle command for the user to run
- do not run Gradle tasks yourself unless the user explicitly asks
- stop and report if a step exposes a new architectural decision

## Current Position

The tote-to-bag/P2P feature work is materially complete and should be treated as the current machine-state pattern to preserve.

Established tote-to-bag/P2P behavior:

- `ToteToBagFlowController` is long-lived across totes and owns the PDC/PRL/PCR transport cell.
- `ToteLoadPlanProvider` externalizes tote load-plan lookup by tote id.
- `ToteToBagBatchPlan` owns batch/order-level expected pack counts independently of one tote manifest.
- PRL assignment is arrival-driven:
  - active assignments stay pinned until completed release
  - idle PRLs are assigned to new batch correlations when the first pack for that correlation arrives
  - if no idle PRL exists, the controller fails clearly; upstream scheduling/admission must prevent that case
- Tote admission gating exists:
  - `ToteToBagFlowController.canAdmit(ToteLoadPlan)` checks whether a tote can be safely tipped into the current PRL assignment/idle state
  - `ToteTrackTipperFlowController` can hold a tote using an admission predicate
- PRL release is local-state driven:
  - PRL release into PCR is gated by PCR availability/current PCR work-in-flight
  - PCR-to-bagger handoff is gated separately through `PackGroupReceiver`
  - the controller should not solve global tote ordering
- `BaggingMachine` tracks intake/bagging separately from output discharge:
  - a later group can begin intake while an earlier bag is still discharging, when the intake side is clear
  - completed bag output uses `BagReceiver` / `BagReservation`
- The debug tote-to-bag harness has exercised a 15-PRL / 40-pack visual profile with local admission gating.

Architectural boundaries to maintain:

- Keep PRL/PCR coupled to downstream only through `PackGroupReceiver`.
- Do not reintroduce direct `BaggingMachine` dependencies into `ToteToBagFlowController`.
- Do not reinitialize `ToteToBagFlowController` per tote.
- Do not make PCR multi-bag aware yet; the current one-released-group-in-flight policy is the conservative baseline.
- Do not solve global scheduling inside the tipper, PRL, PCR, or bagger controllers.

## Current Direction

The latest completed scheduler-adjacent branch is `feature/dsp-scheduler-thread`, merged back to `master`.

The next larger direction is Phase 1 station implementation. Before making code changes, create or confirm the branch-specific station plan.

Completed scheduler work:

- `feature/dsp-scheduler-domain`
- `feature/dsp-scheduler-line-readiness`
- `feature/dsp-scheduler-osr-integration`
- `feature/dsp-scheduler-p2p-live-admission`
- `feature/dsp-scheduler-debug-observability`
- `feature/dsp-scheduler-json-loading`
- `feature/renderable-visibility-lifecycle`
- `feature/machine-wait-queues`
- `feature/dsp-scheduler-thread`

Current scheduler decisions:

- Branch from `master` unless the user says otherwise.
- Treat product master data as the source of product classification.
- Keep `OrderType` and `ToteType` distinct:
  - `OrderType` controls start location, dependencies, routing intent, and lifecycle
  - `ToteType` controls physical carrier role/capability
- Process service centres as whole release windows:
  - do not mix totes from different service centres, except naturally at the last/first boundary
  - if the active service centre is blocked, hold the window rather than skipping ahead
- P2P admission is candidate-specific because tote processing depends on the candidate tote load plan.
- Scheduler evaluation now runs through an evaluation source boundary:
  - `SynchronousSchedulerEvaluationSource` remains available as the fallback path
  - `ThreadedSchedulerEvaluationSource` uses one named platform-thread executor
  - the integrated debug scene uses the threaded source
  - scheduler worker code receives immutable snapshots and returns evaluations; simulation-thread code still applies commands and mutates runtime state
- Scheduler decisions are observable in the existing selection inspection overlay. The current integrated debug target is `tipper_slide`; longer term, composite machine selection should route child hits back to the root renderable.
- Product master and 12N JSON loading now produce existing DSP domain/runtime objects without creating renderables or changing scheduling behavior.
- Renderable visibility/lifecycle support is complete. Hidden renderables are skipped in update/draw/pick, and pack visuals use visibility instead of off-screen translation in current debug paths.
- Machine wait queues are now the scheduler release boundary for the integrated debug P2P path:
  - scheduler release admission answers whether a tote can enter station waiting space
  - machine processing admission remains local to the machine/controller
  - for P2P, queue capacity gates scheduler release, while `ToteToBagFlowController.canAdmit(...)` remains the local tipper processing gate
  - this architecture correction was inserted mid DSP scheduler work before adding further scheduler behaviour
- The integrated debug rig uses a rig-only lid controller so inbound source tote lids open after actual motion starts. This supports visual verification that contained pack renderables stay hidden while lids are closed.

Use `docs/machines/phase-1-stations-roadmap.md` as the active machine roadmap. The generic transfer-machine capability is complete and merged; the next planned branch is `feature/adapting-station-phase-1`.

## Deferred Direction

Current larger direction:

1. Pause deeper scheduler behavior work while Phase 1 stations are introduced.
2. Implement Phase 1 stations with state-complete, visually cheap placeholders.
3. Defer station visual polish to separate Phase 2 visualisation plans.

Known Phase 1 machine/station work:

- adapting station
- third-party station
- manual station
- exception station
- lid opening machine
- lid closing machine
- scheduler-controlled tote buffer
- full warehouse layout with multiple P2P instances

Production layout context:

- A real P2P/tote-to-bag area has five P2P instances.
- Each instance has its own tipper and bagger and around 31 PRLs.
- The current separation between tipper, sorter, PDC/PRL/PCR, and bagger remains preferred despite that physical repetition.

## Testing Practice

The user runs Gradle tasks. When ready for verification, ask the user to run the focused command and wait for feedback.

Prefer stable event/contract assertions over transient state assertions after arbitrary update counts, especially in PRL/PCR/bagger tests.
