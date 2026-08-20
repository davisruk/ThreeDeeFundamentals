# Codex Instructions

## Purpose

This document is the entry-point handoff for follow-up Codex sessions. The current direction is to introduce a DSP/OSR scheduler as a domain-first implementation, while preserving the local machine-state architecture already established in the tote-to-bag/P2P work.

Read these documents before starting:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-scheduler-implementation-plan.md`
3. The current physical release plan, `docs/scheduler/dsp-osr-processing-release-plan.md`
4. The completed supply plan, `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`
5. The completed operational-clock foundation plan, `docs/scheduler/dsp-operational-simulation-clock-plan.md`
6. The completed OSR inventory foundation plan, `docs/scheduler/dsp-osr-physical-inventory-plan.md`
7. The completed outbound foundation plan, `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
8. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
9. `docs/scheduler/dsp-operational-scheduling-requirements.md`
10. `docs/machines/phase-1-stations-roadmap.md`

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

The scheduler evaluation thread boundary from `feature/dsp-scheduler-thread` is complete and merged; later lifecycle branches continue to preserve that snapshot/command boundary.

The adapting station Phase 1 and simulation-reset branches are complete and merged to `master`.

Third Party Area Phase 1, logical/physical identity, and inbound physical tote lifecycle are complete and merged.

`feature/dsp-bag-planning-provenance`, `feature/dsp-outbound-tote-allocation`, `feature/dsp-osr-physical-inventory`, `feature/dsp-operational-simulation-clock`, and `feature/dsp-rate-limited-service-centre-supply` are complete, verified, and merged. `feature/dsp-osr-processing-release` is the current feature and has a decision-complete plan at `docs/scheduler/dsp-osr-processing-release-plan.md`. Exception Station behavior remains separate later work.

Completed bag-planning behavior:

- 12N mapping retains patient and prescription identity.
- `BagKey` is prescription plus deterministic one-based ordinal.
- Physical packs retain immutable source provenance through Third Party and Adapting flows.
- Deterministic pack-count planning emits P2P-compatible correlations without changing generic machine contracts.
- Planning traces join source sheet, fulfilment sheet, input physical tote, and bag.
- Missing logical lines do not create fake physical packs or bags.
- The deprecated eight-argument `DspOrderItem` constructor remains only as a transitional fixture bridge with line-specific placeholder identities.

Completed outbound-allocation behavior:

- Each P2P line has independent deterministic outbound tote identity and at most one open receiving tote.
- Allocation preserves completed receiver order and enforces service-centre purity, pharmacy purity, and configurable bag-count capacity.
- Inbound physical totes terminate at P2P and are never reused as outbound totes.
- Output sheets are allocated separately from immutable planned bag and pack provenance; deterministic generated sheets handle physical tote overflow.
- Closed tote assignments advance from `OUTBOUND_BAG` to `OUTBOUND` and remain active for later dispatch/32R work.
- `OutboundToteAllocationController` validates planned correlation and ordered pack identity before removing a completed runtime bag from `StoredBagReceiver`.
- All-missing prescriptions still produce no bag. Future Exception work must create an empty NS bag for a dedicated pharmacy-pure outbound tote rather than fabricating one in normal bag planning/allocation.

Completed OSR physical-inventory behavior:

- OSR capacity is configurable, with a production baseline of 1,200 physical totes.
- Startup preload defaults to all retained physical manifests for service centres `104` and `108`, preserving assembled dataset order.
- Multiple physical manifests for one logical sheet remain distinct.
- EMPTY startup authorization is separate from physical occupancy and consumes no slot.
- `OsrPhysicalInventory` is simulation-thread-owned; readers use immutable `OsrInventorySnapshot` values.
- Inventory admission/departure, lifecycle registration/activation, and scheduler order status remain separate.
- Future physical release must commit `recordDeparture(...)` only after downstream acceptance succeeds.
- Rate-limited supply operates per physical manifest and uses inventory admission APIs rather than replacing inventory state.

Completed rate-limited supply behavior:

- `DspServiceCentreSupplyPlanFactory` retains deterministic descending service-centre priority and ADAPTED-first physical supply order.
- `DspServiceCentreSupplyCoordinator` authorizes one later centre at the inclusive low-water boundary and admits manifests at a configurable operational-clock rate.
- EMPTY authorization is logical and consumes no OSR slot.
- Capacity blocking applies only when the head manifest is due; blocked recovery preserves order and prevents a catch-up burst.
- `DspSupplySnapshot` is the immutable handoff for later scheduler, inspection, and metrics work.
- Physical OSR processing release must preserve separate downstream acceptance, `recordDeparture(...)`, lifecycle activation, and scheduler-command transitions.

Current physical OSR processing-release constraints:

- Work per currently stored `PhysicalToteId`; never infer physical release from logical `DspOrderStatus` alone.
- Preserve every physical manifest when several manifests share one `OrderSheetKey`.
- Keep the legacy `ReleaseOrderCommand` and debug scheduler unchanged while introducing the typed physical command.
- Revalidate worker-produced physical commands against live simulation-thread inventory, lifecycle, target, identity, and clock state.
- Obtain downstream acceptance before committing inventory departure and lifecycle activation.
- Do not implement pharmacy-grouped ranking, physical command emission, sticky P2P leases, or EMPTY/AV02 allocation in this branch.

Completed operational-clock behavior:

- `SimulationContext` elapsed seconds remain authoritative; business time is a stateless derived view.
- Production defaults map elapsed zero to day 0 `06:00`, normal end to day 0 `22:00`, and hard cutoff to day 1 `00:00`.
- `OperationalDayTime` preserves explicit day offsets for post-midnight scheduling values.
- Immutable snapshots distinguish normal operations, overtime, and hard cutoff reached.
- `DspOperationalClockController` follows absolute context time and performs no cutoff mutation.
- Generic fixed-step execution emits bounded steps, retains backlog under a work budget, and exposes immutable requested/achieved-speed state.
- Realtime, accelerated visual, and headless semantics are represented, but fixed-step/render-decimation behavior is not yet wired into `SoftwareRenderer`.
- Future supply and scheduler logic must consume immutable clock snapshots; it must not read wall-clock time or independently accumulate business time.

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
- Treat 12N line type as the source of order-specific FULL_PACK/ADAPTED/MANUAL processing intent.
- Treat the CSV product master as the source of Third Party bin location and physical dimensions.
- Do not use `referenceSheetNumber` in prepared-line identity; use target order id plus line reference.
- Exclude MANUAL messages/lines from active simulation and report them during ingestion.
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
- Third Party Phase 1 separates product-master CSV loading from 12N JSON loading; neither loading path creates renderables.
- Third Party visits use line-aware selection, candidate-specific immutable admission, configurable waiting/concurrency, and exactly-once completion application.
- Direct picks update fulfilment tote plans. ADAPTED Third Party preparation participates in the existing Adapting store/collect lifecycle and is covered by an integration test through ASSOCIATED collection.
- The `third-party` debug scene and inspection cover stopping, pass-through, downstream routing, and `ALT+R` reset. Focused tests, the complete suite, and visual verification are green.
- Renderable visibility/lifecycle support is complete. Hidden renderables are skipped in update/draw/pick, and pack visuals use visibility instead of off-screen translation in current debug paths.
- Machine wait queues are now the scheduler release boundary for the integrated debug P2P path:
  - scheduler release admission answers whether a tote can enter station waiting space
  - machine processing admission remains local to the machine/controller
  - for P2P, queue capacity gates scheduler release, while `ToteToBagFlowController.canAdmit(...)` remains the local tipper processing gate
  - this architecture correction was inserted mid DSP scheduler work before adding further scheduler behaviour
- The integrated debug rig uses a rig-only lid controller so inbound source tote lids open after actual motion starts. This supports visual verification that contained pack renderables stay hidden while lids are closed.
- Simulation and rendering still run sequentially on the same game-loop thread. Only scheduler evaluation has been moved to a worker thread. A future render-thread split remains deferred and would use published render snapshots rather than live renderable mutation.

Use `docs/machines/phase-1-stations-roadmap.md` as the machine roadmap. Third Party Area Phase 1 is merged. Outbound physical tote allocation now provides the bag/tote lifecycle foundation required by Exception Station Phase 1, but Exception implementation remains deferred to its own planned branch.

## Deferred Direction

Current larger direction:

1. Pause deeper scheduler behavior work while Phase 1 stations are introduced.
2. Implement Phase 1 stations with state-complete, visually cheap placeholders.
3. Defer station visual polish to separate Phase 2 visualisation plans.

Known Phase 1 machine/station work:

- adapting station: Phase 1 complete and merged
- Third Party Area: Phase 1 complete and merged
- bag planning/provenance: complete, verified, and merged
- outbound physical tote allocation: complete, verified, and merged
- OSR physical inventory and preload: complete, verified, and merged
- operational simulation clock: complete, verified, and merged
- rate-limited service-centre supply: complete, verified, and merged
- physical OSR processing release: detailed plan ready; current feature branch
- Exception Area: foundation complete; resume through a separate detailed plan
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
