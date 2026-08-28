# Codex Instructions

## Purpose
This document is the entry-point handoff for Codex sessions. Prefer a fresh Codex session for each feature or feature branch rather than indefinitely resuming a long-lived session. A feature may continue within one session while its context remains useful; if the session has undergone repeated context compaction or its working context has become large/noisy, start a fresh session at a clean plan-step boundary.

Repository documentation is the persistent source of truth. Do not rely on resumed conversation history or compacted context to preserve architectural decisions, requirements, implementation state, or undocumented assumptions. Before ending a feature session, ensure information needed by a future session is recorded in the appropriate context, plan, requirements, or architecture document.

The current direction is to continue the domain-first DSP/OSR operational implementation while preserving the local machine-state architecture established in the tote-to-bag/P2P work.

Always read these documents before starting:

1. `docs/codex-context.md`
2. The active AV02 plan, `docs/scheduler/dsp-av02-operational-allocation-plan.md`

The active plan should name any prerequisite requirements, completed plans, source files, or tests that must also be read for its current step. Read those named prerequisites before implementation. Do not load every historical plan by default.

Use these completed programme documents as references only when the active work touches their boundaries:

- `docs/scheduler/dsp-scheduler-implementation-plan.md`
- `docs/scheduler/dsp-deadline-aware-elastic-line-allocation-plan.md`
- `docs/scheduler/dsp-p2p-sticky-line-leases-plan.md`
- `docs/scheduler/dsp-p2p-arrival-consumer-plan.md`
- `docs/scheduler/dsp-warehouse-transport-routing-plan.md`
- `docs/scheduler/dsp-osr-outbound-route-launch-plan.md`
- `docs/scheduler/dsp-operational-route-target-integration-plan.md`
- `docs/scheduler/dsp-dependency-ready-operational-release-plan.md`
- `docs/scheduler/dsp-osr-processing-release-plan.md`
- `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`
- `docs/scheduler/dsp-operational-simulation-clock-plan.md`
- `docs/scheduler/dsp-osr-physical-inventory-plan.md`
- `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
- `docs/scheduler/dsp-operational-scheduling-requirements.md`
- `docs/machines/phase-1-stations-roadmap.md`

Read these domain documents when touching their areas:

- `docs/tote-to-bag-requirements.txt`
- `docs/bagging_machine_requirements.txt`
- `docs/tipper-route-mounted-machine-architecture.md`

If continuing transfer-zone or mounted-machine architectural discussion, inspect the current transfer-zone classes before proposing unification:

- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZone.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneMachine.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/transfer/TransferZoneController.java`

## Working Rule

Do not make code or document changes unless the user explicitly agrees in the current session. Authorization to implement a selected plan step includes the code and test changes specified by that step. When the user explicitly requests multi-agent orchestration, that authorization also includes the non-architectural plan refinements permitted below. Formal plan revisions and architectural changes still require separate user approval.

The user normally wants architecture discussions to produce a decision-complete plan that a lower-capability implementation model can execute step by step. Plans are expected to be created or reviewed by a higher-capability planning model. The planning model owns architectural decisions and implementation-significant design choices; the implementation model should primarily inspect the named code, make the specified changes, correct mechanical compile/test failures, and verify the selected step rather than infer missing architecture.

The normal implementation workflow is direct execution of the user-selected plan step by the current agent. Multi-agent orchestration is opt-in and must not be used unless the user explicitly requests it for the current task or plan step.

Plans should:

- keep implementation slices small, explicit, and reversible
- limit each slice to the current domain's files where practical
- creation of new files is fine when it avoids awkward coupling
- avoid unnecessary refactors
- include expected output per step
- include separate `Implementation verification` and `User verification` sections per step
- stop and report if a step exposes a new architectural decision

`Implementation verification` must contain the focused Gradle compile/test command that the implementation model is authorized to run, or explicitly state that there is no model-run command for the step. `User verification` must contain any broader regression, full-suite, visual, or deliberately user-reserved check, or explicitly state that no additional user verification is required for the step. Do not leave verification ownership implicit.

### Planning model / step execution owner / implementation subagent contract

For every implementation-significant choice that can be resolved by inspecting the repository, the planning model should resolve it in the plan rather than delegate it to the implementation model. Where applicable, make explicit:

- the exact existing files/classes/interfaces/records to modify
- the exact new types to create and their packages
- the responsibility and ownership boundary of each new type
- architecturally significant fields, constructor signatures, method signatures, return values, and optionality
- the existing implementation analogue or pattern to follow, including which aspects to reuse and which domain-specific aspects must not be copied
- the exact existing API that must remain compatible and the compatibility mechanism to use, such as a delegating constructor, overload, factory, or adapter
- validation and revalidation sequence where ordering affects correctness
- mutation sequence and the point at which mutation begins where atomicity or partial failure matters
- simulation-thread/worker-thread ownership for reads, decisions, and mutations
- deterministic ordering/comparator semantics where ordering is part of the contract
- expected behavior for stale, duplicate, rejected, capacity-blocked, or otherwise invalid operations
- tests to add or modify and the behavior each test must prove
- files or architectural areas that must explicitly remain unchanged when that protects an important boundary
- the limited implementation details that remain discretionary, normally local naming, private helper decomposition, and mechanically equivalent code structure

Do not leave implementation-significant alternatives unresolved in a final plan. Avoid phrases such as `constructor or factory`, `adapter if necessary`, `where practical`, `as appropriate`, or `for example` when they leave the implementation model to choose an architecture. Inspect the current repository and select the intended approach. If the correct choice cannot be established without a new architectural decision, identify that decision explicitly and stop planning that slice rather than guessing.

When a step changes several stateful components, include an explicit application sequence when useful: first the ordered prevalidation/revalidation operations, then a clearly identified mutation boundary, then the ordered mutations. The implementation model should not have to design a transaction from prose requirements.

When introducing a type that resembles an existing implementation, name the concrete analogue and state both what should be copied/reused and what must remain different. Prefer repository-specific guidance over general architectural explanation.

For non-trivial steps, identify the expected change surface where practical: files to create, files to modify, tests to create or update, and important files that should not be changed. This is especially important when the lower-capability implementation model could otherwise broaden the refactor.

For each plan step, define a decision-complete test contract. Specify the test classes to create or modify, the behavioural scenarios to cover, the production boundary or entry point each scenario must exercise, and the significant positive, negative, state-transition, sequencing, and no-mutation assertions. Where multiple validation cases are equivalent, explicitly state which representative cases are sufficient; otherwise treat each named case as required. The implementation model must not infer the intended coverage strategy.

Before finalising a step, the planning model must verify that the specified tests would catch an implementation that satisfies the happy path but violates the step's important boundary, sequencing, lifecycle, failure-state, or no-mutation behaviour.


The implementation model must follow the plan rather than redesign it. It may resolve mechanical coding details and correct compile/test failures that do not change the specified architecture. If implementation reveals a choice that changes public APIs, ownership, lifecycle, ordering, threading, compatibility strategy, or another architectural contract not resolved by the plan, stop and report the decision instead of choosing one.

### Feature-plan and step-execution lifecycle

Use these roles distinctly even when the same model performs more than one role at different times:

- **Planning model**: creates or materially revises the complete feature plan before implementation begins. The plan contains the ordered implementation steps, decision-complete design choices, acceptance criteria, implementation verification, and user verification.
- **Implementation agent**: directly executes the user-selected existing plan step. It owns faithful execution of that step, repository-state validation, focused implementation verification, and reporting back for any required user verification.
- **Step execution owner**: executes exactly the user-selected existing plan step. It owns scope, delegation, repository-state validation, acceptance review, and the decision that implementation is ready for any required user verification. Exists only when the user explicitly requests multi-agent orchestration. The higher-capability parent then owns the complete selected step, delegation, assessment, permitted non-architectural plan refinement, and acceptance of delegated implementation.
- **Implementation subagent**: Exists only during explicitly requested multi-agent orchestration and performs bounded implementation work delegated by the step execution owner. It does not own feature planning or architecture.

The user initiates each implementation step. Do not automatically begin the next plan step. In the normal direct-execution workflow, completion of implementation and focused implementation verification means the step is ready for any required user verification. In explicitly requested multi-agent mode, a subagent reporting completion means only that implementation has returned for parent review; parent acceptance means that implementation is ready for any required user verification. The step is complete only after successful completion of every required user verification, or when the plan explicitly states that no additional user verification is required. Then stop and wait for the user to initiate the next step.

Starting a step does not reopen feature planning. Before implementing a selected step, the implementation agent must re-read the complete selected step and its named prerequisites, inspect enough current repository state to confirm the plan's assumptions and exact implementation surface, and record the existing worktree state with `git status --short`. Turning the existing step into an execution checklist is encouraged; creating a replacement feature plan is not.

In the normal direct-execution workflow, the implementation agent must not modify the selected plan step during implementation. If it identifies missing execution detail, ambiguity, or an apparent plan defect, stop and report it to the user.

During explicitly requested multi-agent execution, the step execution owner may refine the selected step when assessment reveals missing execution detail needed to implement or prove the already-approved architecture and behavior. Permitted refinements include adding or clarifying test cases, focused verification commands, user verification commands, acceptance criteria, expected change-surface guidance, and explicit unchanged boundaries. Any such addition or change must be written into the active plan step before delegation or corrective implementation continues.

Such refinements must not change architectural decisions, domain behavior, feature scope, public APIs/contracts, ownership boundaries, lifecycle semantics, ordering rules, threading boundaries, compatibility strategy, or another implementation-significant decision established by the feature plan. A refinement makes the existing step more complete or precise; it does not redesign it.

An internal execution checklist may decompose and track requirements already written in the active plan, but it must not introduce or alter acceptance criteria, test obligations, verification ownership, or scope. Do not keep a corrected requirement only in the step execution owner's reasoning or an ephemeral checklist: subsequent subagent tasks and final review must use the persistently refined active plan step.

A formal plan revision is required when execution discovers that the approved architecture or intended behavior is materially wrong, incomplete, implementation-significantly ambiguous, or inconsistent with the current repository. Stop and report the precise issue rather than silently changing the architecture during implementation.

### Multi-agent step ownership and delegation
Where the user has explicitly asked for the multi agent implementation approach read `./docs/codex-multi-agent-instructions.md`.


### Verification during implementation

The implementation model may run focused Gradle compile/test tasks named by the plan while implementing a step so that compiler and test feedback can be used to correct mechanical errors before proceeding. A focused verification command may run one test class or a set of tests, provided all tests are directly part of the current implementation step. Keep these checks bounded to the current slice and use normal Gradle output; do not enable `--info`, `--debug`, or other verbose logging unless the failure cannot otherwise be diagnosed.

If a focused compile/test command fails with a small, directly actionable compiler or test error, the implementation model may diagnose the failure, make a corrective change, and rerun the focused command.  After the initial failed verification, the implementation model may perform at most two edit-and-rerun correction cycles for that checkpoint. If the command is still failing after two corrective attempts, if the failure becomes broad or noisy, or if fixing it would require an architectural decision not resolved by the plan, stop and report the failure rather than continuing speculative changes.

The implementation model must not run full regression suites, the complete Gradle test suite, or other broad verification outside the current step. When broader verification is required, ask the user to execute it and provide the exact command in full. The same rule applies whenever a verification command is intentionally reserved for user execution by the plan.

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

The operational scheduler foundations through deadline-aware elastic P2P allocation are complete, verified, and merged. Eventual P2P assignment remains separate from the first route-entry destination; simulation-thread command application commits leases/assignments; arrival only revalidates; full quiescence and output closure precede release. AV02 operational allocation is complete and verified, pending merge to `master`. It introduces inbound `PRE_P2P` totes only for logical EMPTY work, while P2P outbound tote supply and generated output sheets remain independent. After AV02 is merged, generic station processing and route continuation must be implemented before the deferred operational EMPTY end-to-end proof and full-day execution. Exception Station behavior remains deferred under the current all-lines-fulfilled assumption.

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
- Physical release commits `recordDeparture(...)` only after downstream acceptance succeeds.
- Rate-limited supply operates per physical manifest and uses inventory admission APIs rather than replacing inventory state.

Completed rate-limited supply behavior:

- `DspServiceCentreSupplyPlanFactory` retains deterministic descending service-centre priority and ADAPTED-first physical supply order.
- `DspServiceCentreSupplyCoordinator` authorizes one later centre at the inclusive low-water boundary and admits manifests at a configurable operational-clock rate.
- EMPTY authorization is logical and consumes no OSR slot.
- Capacity blocking applies only when the head manifest is due; blocked recovery preserves order and prevents a catch-up burst.
- `DspSupplySnapshot` is the immutable handoff for later scheduler, inspection, and metrics work.
- Physical OSR processing release must preserve separate downstream acceptance, `recordDeparture(...)`, lifecycle activation, and scheduler-command transitions.

Completed physical OSR processing-release behavior:

- Work per currently stored `PhysicalToteId`; never infer physical release from logical `DspOrderStatus` alone.
- Preserve every physical manifest when several manifests share one `OrderSheetKey`.
- Keep the legacy `ReleaseOrderCommand` and debug scheduler unchanged while introducing the typed physical command.
- Revalidate worker-produced physical commands against live simulation-thread inventory, lifecycle, target, identity, and clock state.
- Obtain downstream acceptance before committing inventory departure and lifecycle activation.
- `OsrProcessingReleaseSnapshotFactory` publishes distinct ordered physical candidates from immutable inventory and lifecycle snapshots.
- `ReleasePhysicalToteFromOsrCommand` remains separate from the legacy order-centric debug command.
- `OsrProcessingReleaseCommandHandler` revalidates live state, accepts downstream first, then commits `recordDeparture(...)` followed by lifecycle `activate(...)` using one simulation time.
- Rejected, deferred, stale, or failed target applications leave inventory and lifecycle unchanged.
- Production route-target integration and sticky P2P leases are complete. EMPTY/AV02 allocation is complete and verified, pending merge to `master`.

Completed dependency-ready operational release behavior:

- Build candidates by exact physical manifest and logical sheet identity; never collapse repeated manifests.
- ADAPTED and FULL_PACK are independently eligible. ASSOCIATED requires only its own prepared ADAPTED line keys and no active same-sheet physical assignment.
- Gate OSR departure on the first route-entry station and an explicit selected target; later stations retain their own local admission gates.
- Rank the highest-priority service-centre cohort containing eligible work, then stable pharmacy group and deterministic physical source order. Do not add order-type priority.
- Rank multi-pharmacy ADAPTED work once at its earliest configured pharmacy group.
- Emit at most one typed physical command per pure evaluation and retain typed block reasons for inspection.
- Apply commands on the simulation thread through `OsrProcessingReleaseCommandHandler`; never call legacy logical `markReleased(...)`.
- Rebuild fresh snapshots after deferral or rejection. Live handler revalidation rejects stale commands without duplicate downstream mutation.

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
- `feature/dsp-operational-simulation-clock`
- `feature/dsp-rate-limited-service-centre-supply`
- `feature/dsp-osr-processing-release`
- `feature/dsp-dependency-ready-operational-release`
- `feature/dsp-operational-route-target-integration`
- `feature/dsp-osr-outbound-route-launch`

Current active branch:

- `feature/dsp-av02-operational-allocation`: complete and verified; pending merge to `master`

Current scheduler decisions:

- Branch from `master` unless the user says otherwise.
- Treat 12N line type as the source of order-specific FULL_PACK/ADAPTED/MANUAL processing intent.
- Treat the CSV product master as the source of Third Party bin location and physical dimensions.
- Do not use `referenceSheetNumber` in prepared-line identity; use target order id plus line reference.
- Exclude MANUAL messages/lines from active simulation and report them during ingestion.
- Keep `OrderType` and `ToteType` distinct:
  - `OrderType` controls start location, dependencies, routing intent, and lifecycle
  - `ToteType` controls physical carrier role/capability
- The legacy debug scheduler retains its global service-centre-window behavior for compatibility.
- Operational physical release chooses the highest-priority service-centre cohort that contains eligible work. If a higher-priority centre is wholly blocked, eligible work from a lower-priority centre may proceed.
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
- Warehouse transport ingress is the sole publication boundary after detached hydration. Transfer decisions use exact active destination metadata, and terminal sensors are the sole writers to bounded station-local routed-tote queues.
- Full station-arrival queues retain held pending totes in flight for deterministic retry. Do not bypass this boundary when adding P2P, Adapting, or Third Party consumers.
- P2P arrival consumers process one exact station FIFO head per update. Local admission or full
  tipper input retains source ownership and route-follower state; acceptance preserves exact tote,
  renderable, and load-plan identity before verified source dequeue.
- Do not call `ToteTrackTipperFlowController.acceptNextTote(...)` from a station-arrival consumer.
  `TipperInputQueueController` remains the sole queue-to-tipper boundary.
- Use supplied tote interior geometry through `ContainedPackP2pTipperPayloadFactory`; do not create
  pack renderables in an arrival-controller update.
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
- physical OSR processing release: complete, verified, and merged
- dependency-ready operational release: complete, verified, and merged
- operational route-target integration: complete, verified, and merged
- OSR outbound route launch and physical-tote hydration: complete, verified, and merged
- warehouse transport routing and station-arrival boundaries: complete, verified, and merged
- P2P-local arrival consumption: complete, verified, and merged
- sticky service-centre leases: complete, verified, and merged
- deadline-aware elastic line allocation: complete, verified, and merged; preserve exact assignment pinning, immutable snapshots, full quiescence, and close-before-release
- AV02 operational allocation: complete and verified; pending merge to `master`
- station processing boundary: next branch after AV02 merge
- station route continuation and operational EMPTY end-to-end proof: subsequent separately planned work
- full-day execution and metrics: expected after those station boundaries and the deferred proof
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

The implementation agent runs the focused compile/test command or focused set of tests specified for the current implementation step, subject to the verification rules above. In explicitly requested multi-agent mode, the registered implementer subagent performs this focused verification and the higher-capability parent reviews the complete step, normally relying on the reported green result rather than duplicating it. Full regression runs, the complete Gradle test suite, and other broad verification remain user-run checkpoints: ask the user to execute them and provide the exact command in full.

Prefer stable event/contract assertions over transient state assertions after arbitrary update counts, especially in PRL/PCR/bagger tests.
