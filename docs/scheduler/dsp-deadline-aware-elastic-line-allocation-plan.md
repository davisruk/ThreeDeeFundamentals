# DSP Deadline-Aware Elastic P2P Line Allocation Plan

Branch: `feature/dsp-deadline-aware-elastic-line-allocation`

Status: implementation complete and verified on the feature branch; pending merge to `master`.

## Purpose

Replace the baseline unlimited sticky-line acquisition policy with the first explicit experimental
DSP P2P allocation profile:

```text
DEADLINE_AWARE_ELASTIC_STICKY_LEASES
```

The profile uses immutable operational time, configured trunker deadlines, authorized
service-centre order, remaining normalized work, current line leases, and full line activity to:

- estimate how many of the five P2P lines each active service centre currently needs;
- let the oldest authorized nonterminal service centre claim its required capacity first;
- let one later service centre use remaining capacity when configured overlap permits;
- stop feeding a line that has become surplus without moving any tote already assigned to it;
- close output and release a surplus lease only after pinned work and physical processing drain;
- report infeasible demand without bypassing any physical or ownership invariant.

This is an uncalibrated experimental policy. It must identify itself in immutable snapshots and
inspection and must not be described as a confirmed production algorithm.

This branch does not implement calibrated machine timings, predict actual completion times, change
service-centre supply authorization, pre-empt active work, move committed physical assignments,
increase the five-line production baseline, add five rendered P2P assemblies, implement EMPTY/AV02,
implement Exceptions, flush all output at hard cutoff, compute final dispatch outcomes, or run a
complete production day. Those remain later features.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-p2p-sticky-line-leases-plan.md`
4. `docs/scheduler/dsp-operational-simulation-clock-plan.md`
5. `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`
6. `docs/scheduler/dsp-dependency-ready-operational-release-plan.md`
7. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `OperationalDayTime`
- `DspOperationalClockSnapshot`
- `DspSupplySnapshot`
- `ServiceCentreSupplySnapshot`
- `P2pServiceCentreWorkSnapshot`
- `P2pServiceCentreWorkSnapshotFactory`
- `BagPlanningResult`
- `PlannedBag`
- `OutboundAllocationSnapshot`
- `P2pLineLeaseCatalogSnapshot`
- `P2pLineLeaseSnapshot`
- `P2pLineAllocationRequest`
- `StickyP2pLineAllocationPolicy`
- `P2pLeaseReleaseController`
- `DspP2pStickyLeaseRuntimeFactory`
- `DspOperationalReleaseSnapshot`
- `DspOperationalReleaseSnapshotFactory`
- `DspOperationalReleaseScheduler`
- `DspOperationalReleaseRuntimeFactory`

## Fixed Decisions

Do not revisit these decisions during implementation.

### Time and timetable

- Trunker configuration is independent of 12N `departureTime`; never use that source field as a
  DSP deadline.
- Add structured service-centre timetable values containing normalized service-centre ID, display
  name, positive 12N priority, and `OperationalDayTime trunkerDepartureTime`.
- The timetable must contain distinct service-centre IDs. Priority ties are permitted and are
  resolved by normalized service-centre ID; a mismatch between timetable priority and loaded supply
  priority is an invariant failure.
- Use the operational clock's operating date, normal end, and hard cutoff. Preserve explicit day
  offsets, including Preston at 05:00 on day +1.
- Use configurable positive downstream handling duration, with one hour as the baseline:

```text
trunkerReadyDeadline = trunkerDeparture - downstreamHandlingDuration
targetCompletion = min(trunkerReadyDeadline, normalOperatingEnd)
latestAllowedCompletion = min(trunkerReadyDeadline, hardProcessingCutoff)
availableTime = max(0, latestAllowedCompletion - currentBusinessTime)
```

- The production baseline timetable is the ten agreed service centres and times in the operational
  requirements. Keep it in a structured configuration factory, not inside allocation logic.
- Deadline calculations are pure `LocalDateTime`/`Duration` operations. Tests use deterministic
  simulation time and never sleep.

### Initial normalized workload estimate

- Add a replaceable workload estimator. The initial estimate is deliberately normalized and
  uncalibrated:

```text
singleLineWork =
    remainingInboundToteCount * toteHandlingCost
  + remainingUnallocatedPackCount * packProcessingCost
  + remainingUnallocatedBagCount * baggingCost
```

- `remainingInboundToteCount` comes from `P2pServiceCentreWorkSnapshot`; it includes unsupplied,
  OSR-stored, dependency-blocked, released, in-transit, and active manifests until
  `CONSUMED_AT_P2P`.
- Remaining packs and bags come from immutable `BagPlanningResult` compared with
  `OutboundAllocationSnapshot`: a planned bag is remaining until its `BagKey` appears in allocated
  output history, and its physical pack IDs contribute remaining pack count for the same period.
- This additive estimate intentionally represents remaining input, pack-path, and bagging stages;
  it is not a count of distinct objects and double-stage weighting is expected.
- Validate that planned bag service-centre identity is consistent and that allocated bag keys refer
  to the same planned bag. Do not infer service centre from pharmacy or line assignment.
- EMPTY remains an explicit unsupported/unallocated diagnostic until AV02 creates physical work.
  It does not fabricate workload or hold all lines forever in this branch.
- Costs are nonnegative `Duration` values with at least one positive cost. Use overflow-safe integer
  nanosecond arithmetic; do not use floating-point time calculations.

### Demand calculation

- Configuration contains: P2P line count (baseline five), maximum concurrent service centres
  (baseline two), minimum lines retained for the earliest nonterminal centre (baseline one), safety
  factor in permille, parallel-efficiency factor in permille, downstream handling duration, and
  workload costs.
- Baseline safety and parallel efficiency are both `1000` permille until timing is calibrated.
- Calculate adjusted work and raw required lines with ceiling division:

```text
adjustedWork = ceil(singleLineWork * safetyPermille / parallelEfficiencyPermille)
rawRequiredLines =
    remainingWork == 0       ? 0
  : availableTime == 0      ? p2pLineCount
  : ceil(adjustedWork / availableTime), with a minimum of 1
requiredLines = clamp(rawRequiredLines, 1, p2pLineCount)
```

- Preserve whether raw demand exceeded the configured line count and whether the latest deadline
  has passed. Clamping must not hide infeasibility.
- Only supplied/authorized service centres participate: `PRELOADED`, `AUTHORIZED`, and
  `SUPPLY_COMPLETE` are active states; `HELD_UPSTREAM` is not. An active nonterminal centre must
  have an authorization time and timetable entry.
- Order participating centres by authorization elapsed time, then descending configured priority,
  then normalized service-centre ID. This is the authoritative "oldest authorized" order.
- Consider at most `maximumConcurrentServiceCentres`. Additional authorized centres receive zero
  desired lines and an explicit concurrency-window reason.
- Allocate desired counts sequentially in that order. The earliest nonterminal centre receives at
  least `minimumReservedLinesForEarlierCentre`, then up to its required count; later centres receive
  only remaining line capacity.
- Report total unmet required lines, deadline infeasibility, demand exceeding one-centre capacity,
  and current lease owners outside the configured active window. Never solve infeasibility by
  violating sticky ownership.

### Exact feeding lines and elastic acquisition

- A desired count is a budget for new assignments, not authority to mutate current leases.
- For each owner, derive exact `feedingOwnedLineIds` in configured line order, preferring currently
  open outbound-tote lines before idle lines so a target reduction avoids unnecessary output flushes.
- Owned lines above the desired count are `drainingSurplusLineIds`. They retain their owner while
  pinned or active work drains but receive no new physical assignments.
- If desired count exceeds feeding owned lines, expose `additionalLineSlots`; these allow acquisition
  of that many currently unleased, fully quiescent lines.
- The elastic candidate policy preserves the sticky compatibility tiers within the budget:
  1. matching active pharmacy on a feeding line owned by the candidate service centre;
  2. another feeding line owned by that service centre;
  3. an unleased, fully quiescent line when `additionalLineSlots > 0`;
  4. block.
- Direct-P2P route admission still applies to each exact destination and falls through to another
  compatible line. Earlier-station routes remain independent of current P2P queue capacity.
- Add stable block reasons distinguishing no elastic budget from no physically compatible line.
- Non-P2P candidates and compatibility/legacy scheduler construction retain existing behavior.
- Allocation remains a pure worker-side function of one immutable snapshot.

### Surplus relinquishment

- Never move or rewrite a committed `P2pPhysicalToteAssignment`.
- A surplus line cannot close output or release while any assignment on that line identifies a
  physical tote still present in the owner's `remainingToteIds`. This includes unsupplied,
  dependency-blocked, in-transit, station-queued, and active totes.
- Once no pinned remaining tote targets that surplus line, still wait for
  `activity.processingDrained()`.
- If an open outbound tote remains, close it with existing
  `OutboundToteClosureReason.SERVICE_CENTRE_CHANGED` and stop after that one mutation.
- Release the lease only on a later update when the line is fully quiescent.
- A feeding line remains leased while its owner has work. A completed owner retains the existing
  completion behavior: drain, close with `APPLICABLE_WORK_COMPLETE`, then release.
- Perform at most one close-or-release transition per simulation update in configured line order.
- Recompute demand and surplus classification from fresh immutable snapshots each update. If demand
  rises before release, a line that becomes feeding again is retained.
- Existing assignment history remains immutable audit data and does not itself block release after
  the assigned physical tote reaches `CONSUMED_AT_P2P`.

### Composition and observability

- Build clock, supply, work, planning, outbound, lease, deadline, and elastic-allocation snapshots
  on the simulation thread. Scheduler workers receive detached immutable values only.
- Add an elastic runtime/profile composition without breaking the completed sticky runtime factory
  or its compatibility tests. Extract only a small package-private common assembler if required to
  avoid duplicating five-line validation and arrival binding.
- The operational snapshot carries the elastic allocation snapshot. The scheduler cannot call a
  live planner or inspect mutable registries.
- Runtime and inspection expose profile ID, current business time, each centre's priority,
  authorization order, target/latest deadline, remaining counts, estimated work, raw/required/
  desired/owned line counts, feeding and draining line IDs, unmet demand, and infeasibility reasons.
- Keep reset as full scene/runtime reconstruction. Do not add mutable reset methods.
- The user runs Gradle. After every coding step, ask the user to run the focused command, propose
  the listed commit message if green, and wait for feedback.

## Package And Vocabulary

Create timetable/deadline types under:

```text
online.davisfamily.warehouse.sim.dsp.schedule
```

Use:

- `ServiceCentreSchedule`
- `DspServiceCentreTimetable`
- `DspOperationalSchedulingBaselineFactory`
- `ServiceCentreDeadlineSnapshot`
- `ServiceCentreDeadlineSnapshotFactory`

Create elastic allocation types under:

```text
online.davisfamily.warehouse.sim.dsp.p2p.allocation
```

Use:

- `P2pWorkloadCostConfig`
- `P2pServiceCentreWorkloadSnapshot`
- `P2pWorkloadSnapshot`
- `P2pWorkloadSnapshotFactory`
- `P2pElasticAllocationConfig`
- `P2pServiceCentreLineDemandSnapshot`
- `P2pElasticAllocationSnapshot`
- `DeadlineAwareElasticP2pAllocationPlanner`
- `DeadlineAwareElasticStickyP2pLineAllocationPolicy`
- `ElasticP2pLeaseRetentionPolicy`
- `DspP2pElasticAllocationRuntime`
- `DspP2pElasticAllocationRuntimeFactory`

Minor naming adjustments are allowed only when an existing source type makes one necessary. Do not
move the completed sticky lease types between packages.

## Step 1: Add Structured Timetable And Deadline Values

Scope:

- Add the immutable schedule, timetable, deadline snapshot, and deadline factory types above.
- Add the ten-centre production baseline in `DspOperationalSchedulingBaselineFactory`, including
  Preston's day +1 departure and the exact priorities from requirements.
- Derive trunker-ready, normal target, latest allowed, available durations, and passed flags from one
  `DspOperationalClockSnapshot` and configurable downstream duration.
- Validate distinct IDs, normalized values, positive priorities, explicit day offsets, and deadline
  arithmetic. Permit priority ties but preserve deterministic timetable order.
- Prove 16:00 targets for the three 17:00 trunkers, 22:00 target/latest for Coatbridge, and Preston
  target 22:00/latest day +1 00:00 with a one-hour downstream duration.
- Prove 12N departure metadata is not an input to any deadline API.

Do not add workload or line allocation in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.schedule.*
```

Proposed commit message: `Add DSP service-centre deadline timetable`

## Step 2: Build Immutable Remaining Workload Estimates

Scope:

- Add workload cost configuration and immutable per-centre/catalog snapshots.
- Build them from `P2pServiceCentreWorkSnapshot`, `InboundToteManifestCatalog`,
  `BagPlanningResult`, and `OutboundAllocationSnapshot` without mutating any source.
- Count remaining tote IDs, planned physical packs in unallocated bags, unallocated planned bags,
  and unsupported EMPTY diagnostics by service centre.
- Calculate overflow-safe normalized single-line duration using configured costs.
- Validate manifest/planning/allocation identity and reject unknown allocated bag keys or
  service-centre conflicts.
- Test mixed states, dependency-blocked/unsupplied tote retention, partially allocated bags,
  completion, EMPTY diagnostics, deterministic ordering, immutability, and overflow.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest
```

Proposed commit message: `Estimate remaining P2P workload`

## Step 3: Calculate Deadline-Aware Service-Centre Demand

Scope:

- Add and validate `P2pElasticAllocationConfig` using the fixed integer/permille model.
- Add immutable per-centre demand and whole-allocation snapshots with profile ID
  `DEADLINE_AWARE_ELASTIC_STICKY_LEASES`.
- Implement pure demand calculation from clock deadlines, supply authorization, workload, and the
  ordered five-line lease catalog.
- Enforce authorization ordering, maximum two-centre window, earliest-centre minimum reservation,
  sequential desired allocation, clamping, and explicit unmet/infeasible reporting.
- Derive feeding owned lines, draining surplus lines, and additional slots deterministically,
  preferring owner lines with open output before other owned lines and then configured line order.
- Test one/two/three centres, priority tie, authorization-time precedence, no work, zero slack,
  normal and post-deadline demand, safety/efficiency factors, demand over five, current owners,
  shrinking/rising demand, and uncalibrated profile identity.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticP2pAllocationPlannerTest
```

Proposed commit message: `Calculate elastic P2P line demand`

## Step 4: Enforce Elastic Assignment Budgets

Scope:

- Extend `P2pLineAllocationRequest` with an optional elastic allocation snapshot while preserving a
  compatibility constructor for the completed sticky policy.
- Add the deadline-aware elastic sticky policy implementing existing `P2pLineAllocationPolicy`.
- Permit same-owner assignment only to feeding lines and new lease acquisition only while the
  centre has an additional slot. Never feed a draining surplus line.
- Preserve active-pharmacy preference, configured line order, direct-target admission fallback,
  multi-station capacity independence, cross-centre exclusion, and exact immutable assignment.
- Add stable `NO_ELASTIC_LINE_BUDGET` separately from `NO_COMPATIBLE_P2P_LINE`.
- Test all ranking tiers, desired zero, desired growth/shrink, draining lines, physical capacity,
  direct fallback, and unchanged baseline sticky behavior.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pLineAllocationPolicyTest
```

Proposed commit message: `Enforce elastic P2P assignment budgets`

## Step 5: Carry Elastic Demand Through Operational Evaluation

Scope:

- Extend `DspOperationalReleaseSnapshot` with optional elastic allocation data and compatibility
  constructors yielding no elastic profile.
- Extend snapshot validation so all configured lines match the lease catalog and all candidate
  service centres that require P2P have a deterministic budget or typed exclusion reason.
- Pass the immutable allocation snapshot into `P2pLineAllocationRequest` during worker evaluation.
- Keep the existing default scheduler on baseline sticky allocation. Tests for the elastic profile
  explicitly inject `DeadlineAwareElasticStickyP2pLineAllocationPolicy`.
- Map budget exhaustion and physical incompatibility to stable operational block detail without
  changing dependency, route-entry, service-centre cohort, or pharmacy/source ranking.
- Prove non-P2P work is unchanged and a budget-blocked higher-priority candidate still follows the
  existing eligible-cohort semantics.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest
```

Proposed commit message: `Integrate elastic demand into operational release`

## Step 6: Relinquish Only Fully Drained Surplus Leases

Scope:

- Add a small lease-retention decision boundary. Existing sticky composition uses completion-only
  retention; elastic composition uses `ElasticP2pLeaseRetentionPolicy`.
- For each draining surplus line, intersect committed assignment IDs with the owner's remaining tote
  IDs. Any match blocks closure/release even when current machine activity is idle.
- Once pinned work clears, require processing drain, close open output with
  `SERVICE_CENTRE_CHANGED`, and release only on a later quiescent update.
- Preserve completion closure as `APPLICABLE_WORK_COMPLETE` when the owner has no remaining work.
- Re-evaluate fresh demand before each transition and perform at most one transition in configured
  line order per update.
- Test in-transit/dependency-blocked assignments, activity blockers, rising demand cancellation,
  surplus output closure, later release, complete-owner behavior, assignment history, and no
  pre-emption.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.ElasticP2pLeaseRetentionPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseControllerTest --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

Proposed commit message: `Release fully drained surplus P2P leases`

## Step 7: Compose The Elastic Runtime Profile

Scope:

- Add elastic runtime/factory composition over clock, supply, timetable, work, planning, outbound,
  leases, activity probes, assignment committer, and sticky arrivals.
- Reuse the completed five-line validation and controller ordering; extract one package-private
  assembler only if needed to avoid duplicating composition.
- Keep `DspP2pStickyLeaseRuntimeFactory.create(...)` behavior and signatures working for existing
  callers. Elastic composition installs elastic retention and exposes current allocation snapshot.
- Add `DspOperationalReleaseRuntimeFactory.createElastic(...)` so operational snapshots receive the
  same allocation snapshot used by retention. Require an evaluation source configured with the
  elastic policy and reject mismatched/missing profile composition.
- Validate timetable/supply/work service-centre identity, exactly five lines, all probes/bindings,
  and nonnull detached supplier results before controller registration.
- Test reset-by-reconstruction, simulation-thread mutation, worker detachment, profile mismatch,
  and compatibility sticky composition.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeTest
```

Proposed commit message: `Compose deadline-aware elastic P2P runtime`

## Step 8: Add Elastic Allocation Inspection

Scope:

- Add compact immutable inspection formatting for profile/time/calibration and every active
  service-centre demand field fixed above.
- Add deterministic elastic allocation diagnostics to `dsp-warehouse-transport` without adding
  more rendered P2P machinery. Reuse the selectable `warehouse_transport_state` marker.
- Clearly label fixture-only non-rendered activity/output placeholders. Do not imply that the scene
  executes a calibrated production day.
- Keep selected-object overlay wrapping, existing sticky lease details, transport behavior, and
  `ALT+R` reconstruction.
- Test that inspection includes profile ID, deadline/slack, workload counts, required/desired/owned,
  feeding/draining lines, unmet demand, and infeasibility.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationInspectionTest
```

Proposed commit message: `Expose elastic P2P allocation diagnostics`

## Step 9: Prove Elastic Reallocation End To End

Scope:

- Add a deterministic simulation-time scenario with five logical lines and at least three configured
  service centres, of which two are concurrently active.
- Start with the earlier centre requiring most lines and the later centre using surplus capacity.
- Include direct P2P and earlier-station-before-P2P totes, dependency-blocked work, active-pharmacy
  affinity, queue backpressure, and an in-transit pinned assignment.
- Advance immutable clock/work inputs so demand first shrinks and later rises.
- Prove surplus lines stop accepting new assignments, pinned totes are never moved, active/pinned
  lines are not pre-empted, output closes before release, and a released line is later acquired by
  the eligible centre.
- Prove a third authorized centre remains outside the two-centre window, infeasible demand is
  observable, no outbound tote mixes ownership, and no OSR-to-P2P teleport is introduced.
- Use simulation updates and explicit state transitions only; no wall-clock waits or timing races.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspDeadlineAwareElasticLineAllocationScenarioTest
```

Proposed commit message: `Prove elastic P2P line reallocation`

## Step 10: Regression, Visual Check, And Branch Closure

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.schedule.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Then ask the user to run:

```powershell
.\gradlew test
```

Visual checks:

```powershell
.\gradlew run --args="--scene=dsp-warehouse-transport"
.\gradlew run --args="--scene=tote-to-bag"
```

Verify:

- elastic profile identity and uncalibrated status are visible;
- timetable deadline, remaining work, required/desired/owned counts, and infeasibility are readable;
- line owner/pharmacy/assignment/quiescence diagnostics remain correct;
- direct P2P backpressure and earlier-station physical transport remain intact;
- tote-to-bag processing and pack/bag visuals are unchanged;
- no assigned or active tote changes P2P line;
- `ALT+R` reconstructs both scenes deterministically.

Architecture verification:

- 12N `departureTime` is not used as a deadline;
- all worker inputs are immutable and all lease mutations remain on the simulation thread;
- desired counts limit only future feeding/acquisition;
- outstanding pinned assignments and complete physical activity prevent surplus release;
- close and release remain separate observable updates;
- no calibrated prediction claim, pre-emption, mutable reset, second route engine, or rendering
  thread was introduced.

Before branch closure:

- [x] mark this plan implementation complete and verified;
- [x] record final timetable, workload, demand, feeding, draining, closure, and release contracts;
- [x] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [x] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [x] confirm focused/full tests and visual/reset checks are green;
- [x] reassess whether the next branch is full-day metrics/execution, EMPTY/AV02, or Exception
  Station Phase 1 based on the remaining operational critical path.

Implementation record:

- Timetable deadlines are configured independently of 12N `departureTime`, including explicit
  next-day operation where required.
- Remaining tote, pack, bag, and unsupported EMPTY data produce immutable, normalized,
  uncalibrated workload snapshots.
- Ordered demand reserves capacity for the oldest authorized centre, permits at most one later
  centre in the baseline concurrency window, and exposes unmet or infeasible demand.
- Feeding lines alone accept new assignments. Draining lines retain immutable pinned assignments
  and ownership until work and complete physical activity drain.
- An open outbound tote closes on one simulation update before its fully quiescent lease may
  release on a later update. A released line may then be acquired through the normal elastic
  assignment policy.
- Focused tests, the full Gradle suite, `dsp-warehouse-transport`, `tote-to-bag`, and `ALT+R`
  reconstruction checks are green.
- The likely next planning slice is full-day execution and metrics so the uncalibrated profile can
  be exercised against loaded 12N volumes. EMPTY/AV02 and Exception Station Phase 1 remain explicit
  fidelity gaps and must be selected first if the next full-day scenario requires their physical
  outcomes rather than diagnostics.

Proposed commit message: `Complete deadline-aware elastic P2P allocation`

## Expected Final Contract

- The first experimental operational profile derives deterministic line demand from configured
  deadlines and normalized uncalibrated remaining work.
- At most two authorized service centres receive desired line budgets; the oldest nonterminal centre
  receives capacity first and retains at least one line.
- New tote assignments use only feeding owner lines or available acquisition slots. Surplus lines
  drain without accepting more work.
- Existing physical assignments never move. A surplus lease remains until all totes pinned to it,
  complete machine activity, expected output, and open outbound tote state have drained.
- Output closes before a surplus or completed-owner lease releases, and another centre acquires only
  a later fully quiescent line.
- Deadline infeasibility is measured and reported rather than hidden by clamping or solved by
  breaking physical ownership invariants.
