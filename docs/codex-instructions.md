# Codex Instructions

## Purpose

This document is the entry-point handoff for follow-up Codex sessions. The current direction is to introduce a DSP/OSR scheduler as a domain-first implementation, while preserving the local machine-state architecture already established in the tote-to-bag/P2P work.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp_osr_scheduler_requirements.md`
3. `docs/scheduler/dsp-scheduler-implementation-plan.md`
4. The branch-specific plan referenced by the scheduler roadmap

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

The next scheduler branch is `feature/dsp-scheduler-debug-observability`.

Completed scheduler work:

- `feature/dsp-scheduler-domain`
- `feature/dsp-scheduler-line-readiness`
- `feature/dsp-scheduler-osr-integration`
- `feature/dsp-scheduler-p2p-live-admission`

Current scheduler decisions:

- Branch from `master` unless the user says otherwise.
- Treat product master data as the source of product classification.
- Keep `OrderType` and `ToteType` distinct:
  - `OrderType` controls start location, dependencies, routing intent, and lifecycle
  - `ToteType` controls physical carrier role/capability
- Process service centres as whole release windows:
  - do not mix totes from different service centres, except naturally at the last/first boundary
  - if the active service centre is blocked, hold the window rather than skipping ahead
- P2P admission is candidate-specific because `ToteToBagFlowController.canAdmit(...)` depends on the candidate tote load plan.
- Scheduler evaluation remains synchronous; do not introduce a scheduler thread yet.
- The next debug slice should make scheduler decisions observable in the existing selection inspection overlay before further scheduler expansion.

Use `docs/scheduler/dsp-scheduler-implementation-plan.md` as the scheduler branch roadmap, then follow `docs/scheduler/dsp-scheduler-debug-observability-plan.md`.

## Deferred Direction

After scheduler debug observability is proven:

1. Add product master / 12N JSON loading after schema samples are supplied.
2. Add renderable lifecycle/visibility optimization so loaded order data does not create active renderables up front.
3. Consider scheduler threading only after synchronous snapshot/command integration remains stable.

Known future machine work still exists, but is lower priority than understanding scheduler impact:

- lid opening machine
- tote strapping machine
- scheduler-controlled tote buffer
- full warehouse layout with multiple P2P instances

Production layout context:

- A real P2P/tote-to-bag area has five P2P instances.
- Each instance has its own tipper and bagger and around 31 PRLs.
- The current separation between tipper, sorter, PDC/PRL/PCR, and bagger remains preferred despite that physical repetition.

## Testing Practice

The user runs Gradle tasks. When ready for verification, ask the user to run the focused command and wait for feedback.

Prefer stable event/contract assertions over transient state assertions after arbitrary update counts, especially in PRL/PCR/bagger tests.
