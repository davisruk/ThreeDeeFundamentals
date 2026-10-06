# DSP PRL Mixed-Pack Intake Remediation Plan

Status: proposed; no implementation is authorized by this document. The user starts each
implementation step, architecture review, and documentation closure separately.

## Purpose and evidence

Repair the local PDC-to-PRL progress defect exposed by mixed pack lengths, while preserving
machine ownership, bag correlation, local capacity blocking, and scheduler behavior.

The diagnostic run inspected on 2026-10-06, through PT38M, established:

- At PT15M P2P line 1 retained active tipper tote `93043164`, with 20 PDC packs and two pending
  sorter-outfeed packs. From PT16M onward it had 27 idle PRLs, two assigned PRLs, two accumulating
  PRLs, two received PRL packs, no ready/releasing PRLs, and no PCR/bagging work. These values
  remained unchanged through PT38M. Remaining physical totes stopped at 4,346 after PT19M.
- Tote `93043164` is FULL_PACK order `TOTE0008814067`, sheet `001`. Its first two executable
  packs belong to prescription `20001190000779387`: product `39929`, length 0.030 m, followed
  by product `14021`, length 0.174 m. Both are ordinary product-master dimensioned packs.
- The headless profile supplies a 0.100 m fixed PRL index and a 0.015 m minimum gap. After the
  first pack and one index, its front is approximately 0.130 m and its rear is 0.100 m. The next
  pack needs its front at 0.174 m, with the existing pack's rear at least 0.189 m. No existing
  operation requests the additional 0.089 m of PRL travel when that pack is rejected.
- `LinearConveyorLane.canAcceptAtFrontDistance(...)` and `clampSpacingFromFront()` also add
  the following/incoming pack's length to the ahead pack's rear-distance bound. That addition
  is incorrect for the lane's front-distance coordinate convention and can permit overlap.

The code and input demonstrate a specific failure mechanism. The aggregate diagnostic log
does not capture individual blocked PDC pack IDs; attribution of the observed runtime head
to that exact pack remains an inference until the regression and user run verify it. Do not
declare every daily-run blockage solved by this feature.

The line-demand issue is separate. The existing deadline-aware policy initially requests one
line, later requests two, and prefers compatible owned lines to unleased lines. This plan does
not change that policy or promise multi-line utilization.

External evidence, for reference only (never a committed test dependency):

- `C:/misc/cpas-test/scheduler-testing/progress.log`
- `C:/misc/cpas-test/2026-12N/170926-FullRun/pretty/12NMessages20260916_234536_840.json`
- product master `app/md/product_automation.csv`

## Required reading and execution rules

Read `AGENTS.md`, then `docs/codex-instructions.md` completely and follow its mandatory document
reading order. Then read this complete plan and these domain references completely:

- `docs/tote-to-bag-requirements.txt`
- `docs/bagging_machine_requirements.txt`
- `docs/tipper-route-mounted-machine-architecture.md`

Read the shared contracts and Step 2 in
`docs/scheduler/dsp-full-day-blockage-progress-diagnostics-plan.md` for the observation boundary.
The mandatory performance-plan reading supplies the efficiency and fixed-step constraints;
this feature does not reopen that performance programme or its measurements.

Before editing, record `git status --short`. Preserve all existing changes, including the
uncommitted blockage-diagnostic implementation. Use `apply_patch` for every edit. Execute
directly: no subagents unless the user explicitly opts in for this task. Do not commit.

Read each selected step's named production and test files before editing. A file explicitly
listed as **create** is expected not to exist; stop if it already exists with conflicting
ownership. For a file listed as **modify/read**, stop if it is absent. Stop and report any
implementation-significant contradiction or missing decision. Do not revise this plan while
implementing, invent a replacement API, or weaken tests to get a green result.

Only run the selected step's exact implementation-verification command. No full suite,
`installDist`, visual run, daily run, performance test, or JFR analysis by the implementation
agent. After verification, review the complete selected-step diff against every shared and
step-specific contract, run `git diff --check`, and report final `git status --short`.

## Shared fixed contracts

### Scope and ownership

1. Production changes are limited to `LinearConveyorLane`, `PrlConveyor`, and the two existing
   PDC intake/retry loops in `ToteToBagFlowController`. No new production type, configuration,
   controller, thread, scheduler input, global cache, or inspection/report field.
2. The calling simulation thread owns lane entries, PRL travel requests, assignments, and
   transfer mutations. No render-thread or worker access to live lane internals.
3. `accepts(Pack)` and every lane capacity query remain read-only. Movement is requested through
   a separate explicitly mutating PRL method; only `PrlConveyor.update(...)` advances PRL packs.
   Do not move a pack, receive it, increment counts, or start a device while merely requesting
   space. No teleporting, forced acceptance, dimension changes, or disabled gap checks.
4. Preserve every existing constructor and public method signature. Add only the two APIs
   explicitly specified below. Retain fixed indexing after actual acceptance as the baseline;
   supplement it only when a waiting pack needs more intake room. Do not increase the profile's
   fixed index or change PRL length, speed, or diversion positions.
5. Preserve exact pack objects/IDs/correlations, assignment expected/effective counts,
   arrival-driven assignment, assignment persistence across totes, release ordering,
   PRL-to-PCR transfer, single-group PCR gating, and `PackGroupReceiver` coupling.
6. Bypassed exception packs must not request PRL space or acquire an assignment. Missing-pack
   classification, zero-pack groups, exception marking, source/fulfilment identities, and
   physical output allocation are unchanged.
7. Genuine lack of lane capacity still blocks. Do not compact/rearrange packs, spill them into
   another PRL, shorten a group, release an incomplete group, or manufacture an exception.
   This feature is not a policy for oversized bags or packs. Existing extra gaps caused by
   fixed indexing are not repacked; therefore a sum-of-pack-lengths fit is not a promise of
   fit in the currently arranged lane.

### Coordinate and numeric contract

Distances and lengths are metres. A lane entry occupies `[front - packLength, front]`, and
entries are ordered downstream-to-upstream by descending front distance. For adjacent
`ahead` and `behind`, enforce:

```text
behind.front <= ahead.front - ahead.packLength - minimumGap
```

Use one private `LinearConveyorLane` constant `POSITION_EPSILON = 0.000001f` (one micrometre)
only for neighbor-gap comparisons and the additional-travel calculation. This is a float
roundoff allowance, not a physical capacity allowance. Do not relax the individual pack-size
or front-position bounds: an incoming length greater than lane length remains invalid, and
front must remain between that pack's length and lane length. Tests compare geometry with the
same 0.000001 m tolerance. Do not add configurable tolerance or use centimetre-scale epsilon.

### Efficiency contract

- New intake queries and PRL requests must be O(1), using `entries.getFirst()` and
  `entries.getLast()` internally. No `getPacks()`, entry snapshots, optional snapshots, streams,
  maps, lists, full-lane scans, or bag/tote-plan scans in those new paths.
- Use one primitive float travel deficit and the PRL's existing
  `remainingControlledTravelDistance`; no queued request objects, candidate caches, or per-pack
  state/indexes. Repeated requests must coalesce, never add another copy of the deficit.
- Keep the existing single PDC-entry traversal and existing transfer traversal per update.
  Add constant-time work only inside their existing relevant branches. Do not enumerate idle
  PRLs again, add another PDC snapshot, or inspect every transfer to locate one candidate.
- Lane advancement must use one ordered in-place pass, no per-update sorting or snapshots.
  Existing generic insertion sorting may remain at actual insertion. Do not optimize unrelated
  existing snapshot getters, motion-state loops, or assignment streams in this feature.
- Prove new-query bounded work and repeated-request behavior deterministically, not with
  wall-clock thresholds. No performance benchmark or JFR is required for implementation.

## Step 1: Correct lane geometry and add coalesced PRL intake recovery

Deliver these three production edits together. A geometry-only intermediate implementation
would tighten capacity checks without providing intake recovery; do not hand off that state.

### 1.1 Exact change surface and reading

Paths below are relative to the repository root. Production source prefix:
`app/src/main/java/online/davisfamily/warehouse/sim/totebag/`.

Modify:

- `conveyor/LinearConveyorLane.java`
- `conveyor/PrlConveyor.java`
- `control/ToteToBagFlowController.java`

Also read, but do not modify:

- `conveyor/PdcConveyor.java`, `conveyor/PcrConveyor.java`, `conveyor/ConveyorOccupancyModel.java`
- `assignment/PrlAssignment.java`, `assignment/PrlState.java`
- `device/PdcDiversionDevice.java`, `transfer/PdcTransfer.java`
- `control/PdcPackDispositionPolicy.java`, `control/SorterTipperDownstreamFlow.java`

Test prefix: `app/src/test/java/online/davisfamily/warehouse/sim/`.

Create (currently absent):

- `totebag/LinearConveyorLaneTest.java`, package `online.davisfamily.warehouse.sim.totebag`
- `totebag/PrlConveyorTest.java`, same package

Modify:

- `totebag/ToteToBagFlowControllerTest.java`

Read and run unchanged coverage:

- `totebag/PcrConveyorTest.java`
- `dsp/p2p/lease/ToteToBagP2pLineActivityProbeTest.java`

No other production or test file is authorized in Step 1. A failure outside this change surface
must be reported for a plan revision, not corrected speculatively.

### 1.2 LinearConveyorLane: exact geometry and primitive query

Implement the following operations; retain existing ownership, getters, removals, and outfeed
semantics.

**A. `canAcceptAtInfeed(Pack pack)`**

Replace delegation to the general insertion scan with a direct O(1) check:

1. Null returns false. Read `incomingLength = pack.getDimensions().length()` once.
2. If incoming length is greater than usable length, return false.
3. If empty, return true.
4. Otherwise return whether `incomingLength <= lastEntry.rearDistance() - minimumGap +
   POSITION_EPSILON`.

Do not call snapshot getters to obtain the last entry. Keep `acceptAtInfeed(...)` delegating to
the existing general insertion method; its work occurs only on actual admission.

**B. `canAcceptAtFrontDistance(Pack pack, float candidateFrontDistance)`**

Retain the existing strict position bounds and existing neighbor-location scan. Change only
the ahead-space formula and floating comparison:

```text
ahead upper bound = ahead.rearDistance() - minimumGap
reject if candidateFrontDistance > upperBound + POSITION_EPSILON

behind lower bound = behind.frontDistance + minimumGap + incomingLength
reject if candidateFrontDistance < lowerBound - POSITION_EPSILON
```

The behind lower-bound formula is already geometrically correct: do not remove its incoming
length. Remove the incoming length only from the ahead upper bound. Preserve rejection without
mutation and deterministic insertion order.

**C. Add `public float additionalTravelRequiredForInfeed(Pack pack)`**

This is a pure query. Return primitive `Float.POSITIVE_INFINITY` for impossible geometry, not
an `Optional`, record, exception, or allocation. Its exact calculation is:

```text
null or incomingLength > usableLength -> POSITIVE_INFINITY
empty -> 0
needed = max(0, incomingLength + minimumGap - lastEntry.rearDistance())
needed <= POSITION_EPSILON -> 0
available = max(0, usableLength - firstEntry.frontDistance)
needed > available + POSITION_EPSILON -> POSITIVE_INFINITY
otherwise -> min(needed, available)
```

Read incoming length once and use direct first/last entries. Do not call the general insertion
scan or build a snapshot to answer this query. It describes uniform forward travel of the
current arrangement; it does not promise travel at zero belt speed or rearrange existing gaps.

**D. `advanceDistance(float requestedDistance)`**

Retain the zero/empty checks and applied-distance cap from the leading pack's front to usable
length. Replace the separate add-all pass, spacing-clamp pass, and sorting with one ordered
in-place pass:

```text
previous = null
for each entry in the existing downstream-to-upstream order:
    entry.front = min(entry.front + appliedDistance, usableLength)
    if previous exists:
        entry.front = min(entry.front, previous.rearDistance() - minimumGap)
    previous = entry
return appliedDistance
```

The following pack's length is not added to the spacing bound. Delete the now-unused private
`clampSpacingFromFront()` method. Do not sort after advancement: validated insertion and the
strict neighbor clamp preserve order. No new object is created by the movement pass. Keep
sorting in generic insertion/removal as existing event-bound work; do not refactor it here.

### 1.3 PrlConveyor: explicit movement request

Add exactly `public boolean requestInfeedSpaceFor(Pack pack)`.

The boolean means **this PRL already has, or can request, enough intake space for this pack**;
true does not mean the pack has been admitted or movement has completed. Callers still retry
the read-only `accepts(pack)` on a subsequent update.

Prevalidation and mutation sequence:

1. Null, mismatched/unassigned correlation, or any assignment state other than `ASSIGNED` or
   `ACCUMULATING` returns false without mutation. This includes `IDLE`, `READY_TO_RELEASE`, and
   `RELEASING`; do not reopen or advance them through this API.
2. Read `required = lane.additionalTravelRequiredForInfeed(pack)`.
3. Non-finite required distance returns false without mutation.
4. If required is zero, return true without changing travel or motion state.
5. If the lane speed is zero, return false without mutation: geometry alone cannot make it move.
6. **Mutation begins only here:** set
   `remainingControlledTravelDistance = Math.max(remainingControlledTravelDistance, required)`
   and return true.

Do not call additive `queueControlledTravel(required)` for these requests. Do not set running,
advance the lane, add a pack, increment received counts, or change assignment state here.
Retain `acceptPack(...)`'s existing fixed indexing after actual acceptance and its complete-group
travel-to-release behavior. Retain `update(...)` as the sole movement executor, with the existing
belt-speed and elapsed-time budget. Do not add fields or change PRL constructors.

Repeated polling is intentionally cheap: recalculation reads two entries and coalesces one
float. No versioned cache is needed because those coordinates genuinely change while moving.

### 1.4 ToteToBagFlowController: both existing retry points

Modify only these two private loops. Keep `update(...)` ordering, `canAdmit(...)`, assignment
selection, release selection, devices, and disposition callbacks unchanged.

**A. `requestPdcDiversions()`**

Keep the existing single `pdcConveyor.getLaneEntries()` traversal. Preserve the bypass check
before PRL lookup. After existing PRL lookup and diversion-distance calculation:

```text
if entry.frontDistance < diversionFrontDistance:
    continue                         // no premature intake request
if !prl.accepts(pack):
    prl.requestInfeedSpaceFor(pack)
    continue                         // do not request/start a device yet
lookup existing device and call device.requestDiversion(pack) as before
```

Do not treat a true movement-request result as permission to divert. Leave the pack on the
PDC until the normal device/transfer path can accept it. Do not inspect or reassign another PRL.

**B. `updatePdcTransfers(double dtSeconds)`**

Retain its iterator, transfer progress, completion guard, and target validation. A pack can
be accepted by the PRL when diversion begins but find the intake occupied when its transfer
finishes. Therefore replace the existing rejection branch with:

```text
if !prl.accepts(transfer.getPack()):
    prl.requestInfeedSpaceFor(transfer.getPack())
    continue
prl.acceptPack(transfer.getPack())
iterator.remove()
```

The waiting transfer keeps the same object, pack, target, and ownership. Do not cancel it,
return it to PDC, consume it twice, or add a second transfer traversal/reservation model.
Retain the existing iterator order and acceptance rules; this feature does not redesign
transfer arbitration. A false request result leaves the existing blocked item intact.

### 1.5 Decision-complete unit and controller tests

Use JUnit 5 and the existing `Pack`, `PackDimensions`, occupancy, assignment, and world types.
No external files, mocks requiring new libraries, sleeps, reflection, or production test hooks.
Use event predicates with explicit iteration bounds. State checks at a deliberate capacity or
request boundary are required; avoid guessed transient update counts.

**New `LinearConveyorLaneTest`**

1. `shouldEnforceWholePackGapAtInfeedAndAtArbitraryInsertion`: lane length 1 m, gap 0.015 m.
   Ahead pack length 0.100 m, front 0.300 m gives rear 0.200 m. A length-0.080 pack at front
   0.220 must be rejected, at 0.185 accepted (gap boundary). Test an insertion between two
   residents separately: residents `[0.400,0.500]` and `[0.200,0.300]`; a length-0.070 pack
   at front 0.385 fits both 0.015 gaps, at 0.386 fails ahead space, at 0.390 fails ahead
   space, and at 0.380 fails behind space. Rejected admission must leave entries and identities
   unchanged. Also prove the fast infeed check rejects an 0.080 pack while a resident's rear is
   0.090 (gap requires 0.095), then accepts after adequate movement. Null query is false;
   an individually oversized pack is rejected even in an empty lane.
2. `shouldComputeAdditionalInfeedTravelWithoutMutation`: 0.030 pack at front 0.130, next
   length 0.174, gap 0.015: deficit 0.089. Repeated reads leave coordinates unchanged;
   advance that deficit, then assert acceptance and a zero deficit. Empty fitting lane gives
   zero; null/oversized gives positive infinity. A 0.200 lane with that resident cannot fit
   the next pack and gives infinity. A smaller candidate already fitting gives zero.
3. `shouldPreserveOrderBoundsAndGapsThroughAdvanceAndRemoval`: use a 1 m lane at 1 m/s,
   gap 0.015, with lengths/fronts `(0.030,0.300)`, `(0.174,0.255)`, `(0.050,0.066)`;
   these are valid initial gaps and bounds. Advance in 0.05-second steps to the outfeed
   (maximum 100 steps), validate all
   front/rear bounds, object order, and every adjacent gap each time. At outfeed, further
   advancement returns zero until the leading pack is removed; after removal, the remaining
   exact objects advance normally. Never substitute pack IDs for object-identity assertions.
4. `shouldUseOnlyLaneEndpointsForRepeatedIntakeQueries`: define test-local `CountingPack`
   overriding `getDimensions()` and a test-local lane subclass whose snapshot/getPacks/
   optional-entry getters throw if used during queries. Populate 20 spaced packs in a 10 m
   lane: each has length 0.030 and front `0.200 * ordinal` for ordinals 1 through 20,
   gap 0.015. Reset dimension counters after setup, and run each query 100 times with a
   length-0.174 incoming candidate (infeed false, required travel 0.019). No resident except the last may have its
   dimensions read; each query reads candidate and last dimensions at most once. No movement
   or identity/count change. This guards O(1) work without runtime instrumentation or timing.

**New `PrlConveyorTest`**

1. `shouldRequestOnlyTheMissingTravelForThirtyThenOneHundredSeventyFourMillimetres`: PRL
   length 1.8, gap 0.015, index 0.100, speed 1.8 m/s, assignment of two packs. Accept the
   exact 0.030 first pack and finish its fixed index; front is 0.130. Repeated `accepts(long)`
   calls are false and do not move/change counts. Request space 100 times without updating:
   positions/count/state remain unchanged. Update until `accepts(long)` is true (at most
   100 updates of 0.05 s); total indexing distance is approximately 0.189, not 100 copies
   of 0.089. Stop pending travel with one additional update before checking settled distance.
   Accept the exact long pack once; assert ordered received IDs, count two, READY_TO_RELEASE,
   and physical gap. Preserve normal release/empty/clear lifecycle to IDLE in a bounded loop.
2. `shouldSupplementEqualPackIndexingWithoutReplacingFixedIndex`: two length-0.200 packs,
   gap 0.050, fixed index 0.150. After fixed movement the next cannot fit; explicit request
   and update supplies the additional 0.100. Separately, after a 0.030 pack has completed
   its fixed 0.100 index with gap 0.015, requesting a 0.020 candidate already fitting
   gives true and no extra travel. Also use a fresh 0.030 resident with its 0.100 fixed
   index still pending: repeatedly request space for a 0.020 candidate before updating;
   total completed index remains 0.100, not 0.100 plus repeated deficits. Constructors/
   index settings do not change.
3. `shouldKeepImpossibleIntakeAndInvalidRequestsNonMutating`: 0.200 m lane with 0.030 pack
   already indexed by 0.100 and waiting 0.174: false request, unchanged received count,
   assignment, coordinates, and settled indexed distance across repeated requests/updates.
   Separately test null, wrong correlation, idle, ready, releasing, individually oversized,
   and zero-speed positive-deficit requests. Each returns false and cannot receive/clear/
   release anything. A zero-speed request for space already available returns true without
   mutation. Do not assert capacity-blocked work becomes quiescent or completed.

**Extend `ToteToBagFlowControllerTest`**

Use the existing constructor whose first parameters are `(ToteLoadPlan, ToteToBagBatchPlan,
PdcConveyor, PcrConveyor, PackGroupReceiver, ToteToBagAssignmentPlanner, List<PrlConveyor>,
List<PdcDiversionDevice>, ...)`, with explicit transfer-duration and entry-distance providers.
Use a current tote plan containing the scenario's pack plans and a batch derived from that
plan; this constructor deliberately has no tipper/sorter. Reuse the existing
`UnavailablePackGroupReceiver`. For the three new fixtures, PDC length 2, gap 0.015,
speed 1 m/s; PCR length 2, gap 0.015, safety margin 0, travel duration 1 s. Device arm/
actuation/reset are 0/0.01/0.01 s, PRL-to-PCR transfer duration zero, and PCR entry equals
incoming pack length. Use one PRL, `prl-1`, with the stated scenario length/index and speed
1.8 m/s. Feed retained pack objects directly only where the scenario explicitly says so.

1. `shouldRecoverMixedLengthPackWaitingOnPdcWithoutReassignment`: use one PRL (length 1.8,
   gap 0.015, index 0.100, speed 1.8), a 2 m PDC, a 2 m PCR, the existing unavailable
   downstream-receiver fixture, and a two-pack batch correlation `bag-a`. Controller has
   no tipper/sorter bootstrap. Device timing 0/0.01/0.01; PDC and PRL transfer durations
   zero; diversion front threshold 0; PCR entry at incoming pack length. Initialize through
   `controller.update(null, 0)`, accept the first pack directly into the assigned PRL and
   finish its fixed index with `prl.update(1)`, then put the long pack on PDC. After
   `controller.update(null, 0)` it must remain on PDC, no active transfer or device start,
   unchanged received count. Use a `SimulationWorld` containing PCR and controller to
   update 0.05 s until the bag-a release event (maximum 400 steps). Assert release contains
   the exact two objects once, same correlation/PRL, PDC empty, no outstanding PDC transfers.
   This test must exercise the controller branch, not manually call the new PRL request.
2. `shouldRecoverCompletedPdcTransferWaitingForPrlIntake`: same one-PRL setup, expected
   count three, three 0.080 m packs, fixed index 0, gap 0.015, transfer duration 0.50 s.
   Put the first at PDC front 1.5; `controller.update(null,0.05)` starts its transfer while
   PRL is empty. Insert the second at front 1.5 and update the world in 0.05 s steps until
   two active transfers exist (at most eight steps); device reset means the very next update
   need not start it. Assert both transfers exist before either completes. Then run the world until
   received count two (maximum 400 steps). Assert at least one completed transfer was
   observed waiting with `!prl.accepts(itsPack)` before retry; both exact IDs eventually
   received once and transfer list empty, without a manually issued space request. Expected
   count three deliberately prevents release so geometry can be inspected. Then feed the
   third normally and await one exact three-pack release. This catches an implementation
   that fixes only PDC waiting and misses the transfer retry branch.
3. `shouldRetainPdcPackWhenPrlCannotPhysicallyFitIt`: short 0.200 PRL, 0.030 first pack
   indexed by 0.100, waiting 0.174 on PDC. Across 100 controller/world updates it remains
   the same PDC object, no transfer/device start, received count one, no release/completed
   bag. Do not force fit, reroute, or alter counts. Also retain existing bypass-outfeed and
   one-PDC-traversal assertions unchanged.
4. Correct only these two existing direct-admission fixtures, which previously relied on
   overlap rather than sufficient physical space:
   `shouldSummarizePrlActivityWithoutMutatingPrlState` and
   `shouldKeepPrlAssignedAcrossToteBoundaryUntilBatchCountIsMet`. Before the second direct
   `acceptPack`, create/retain that exact second pack, assert a successful explicit space
   request, and update that PRL directly in a bounded 0.05 s loop (maximum 100 steps) until
   `accepts(secondPack)`. Assert the predicate before acceptance. Keep the original activity,
   count, correlation, and cross-tote lifecycle assertions. Do not shrink fixture packs,
   enlarge lanes/indexes, weaken gap checks, or replace event checks with larger guessed waits.

Test loops may inspect snapshots for assertions; the prohibition on new snapshots applies to
production intake/request paths. Existing controller tests for PCR gating, delayed device
actuation, bypass disposition, assignment pinning, and PRL ordering must remain meaningful.

### 1.6 Acceptance review

Confirm all three production files implement the exact above contract; geometry and recovery
are delivered together. Confirm both waiting paths request movement only after failed intake,
all read-only queries stay pure, coalescing uses max not addition, and no new full-lane/PDC scan
or snapshot/stream allocation exists in new paths. Report existing unrelated dirty files
separately, not as feature edits. Confirm unchanged scheduler/profile/report/exception files.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.LinearConveyorLaneTest --tests online.davisfamily.warehouse.sim.totebag.PrlConveyorTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.PcrConveyorTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.ToteToBagP2pLineActivityProbeTest
```

Use normal output. Follow repository limits on mechanical correction cycles. Report exact
result/test failures, `git diff --check`, changed files, and final status. Do not start Step 2.

### User verification

No additional user-run verification is required for this slice beyond acceptance of its focused
result. Full-suite, installed-line, visual, and daily-run verification belong to Step 2. This
step alone must not claim that the complete daily run is unstalled.

## Step 2: Prove production headless integration and hand off daily-run verification

### 2.1 Existing implementation is the acceptance contract, not a rebuild instruction

Read all Step 1 contracts and inspect its actual diff. Preserve the completed implementation;
do not rebuild the lane/PRL fix, change its APIs, or expand into scheduler changes. Stop if
the inspected implementation conflicts with those contracts.

Modify only:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspHeadlessP2pLineRuntimeFactoryTest.java`

Read, but do not modify:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspHeadlessP2pLineRuntimeFactory.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspHeadlessP2pLineRuntime.java`
- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspHeadlessP2pLineConfig.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/analysis/runtime/DspHeadlessP2pLineRuntimeTest.java`

### 2.2 Exact production-boundary proof

Add `shouldAllocateMixedLengthBagThroughProductionHeadlessGeometry()` in the existing factory
test. Reuse its `fixture("line-1")`, `withPolicyAndProvider(...)`, and
`shouldPassConfiguredPartialPackBagThroughTheCompleteHeadlessLine()` construction pattern.

- Owning sheet `new OrderSheetKey("mixed-order", 1)`, input tote `mixed-input`, bag key
  `new BagKey("mixed-rx", 1)`, service centre `SC-1`, pharmacy `pharmacy-1`, patient `patient-1`.
- Two pack plans `short-pack` then `long-pack`, correlation from the bag key, dimensions
  `(0.030,0.042,0.086)` and `(0.174,0.075,0.030)` metres. Planned bag's exact ordered physical
  IDs are those two; create the corresponding provenance/traces and a complete immutable bag
  result through `BagPlanningResultTestFixtures.complete(...)`, as in the partial-bag test.
- Work provider returns expected count two for only that correlation and an immutable singleton
  expected-correlation set. Disposition policy uses the existing default methods/unchanged
  planned count, bypass false, and `deferInitialPrlAssignments()` true. No missing IDs: immutable
  empty set. Do not fabricate a partial bag or flag exceptions to make the test complete.
- Construct a fresh allocator exactly as the partial-bag test does, with the owning sheet and
  outbound capacity four. Do not reuse a fixture allocator with unrelated output-sheet data.
- Create the runtime using the production factory and `RecordingWorld`. Do not override factory
  geometry: its 31 PRLs, 1.8 m PRL length, 0.015 m gap, 0.100 m index, actual diversion distance,
  device durations, belt speeds, and configured placeholder durations are the subject of this
  test. Assert the unchanged controller registration order.
- Create and retain the two exact runtime `Pack` objects and feed them, in order, through
  `runtime.sortingMachine().receive(...)`, following the existing partial-bag proof. This is a
  sorter-to-output production composition proof, not a new station/tipper test; do not claim
  it proves physical tote entry or release. Never manually accept a PRL pack or issue an intake
  movement request in this test.
- Update the existing world in 0.05 s steps, at most 2,000, until the allocator records one bag.
  While moving, inspect current PRL/PDC/PCR lane snapshots in the test and assert adjacent gaps
  and pack bounds with 0.000001 tolerance. A private test helper for these assertions is allowed.
- Assert one allocation using the same planned bag object; actual IDs exactly
  `[short-pack,long-pack]`, missing IDs empty, no exception-marked open tote, and no duplicate
  bag after 20 additional world steps. Assert PDC/PRL-transfer/PRL/PCR work empty and no
  outstanding expected group; keep the normal explicit output-tote close operation and assert
  the resulting closed tote is not exception-marked. Do not require whole-line quiescence
  before closing an open outbound receiving tote.

The existing partial-bag/exception and bypassed-outfeed tests remain unchanged and are run in
the same class. If new production changes appear necessary, stop and request a Step 1 revision.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeTest
```

Review the complete feature diff, not only the new test. Report every shared-contract result,
focused result, diff check, final status, and criteria awaiting user verification. Stop.

### User verification

These are user-owned checks; the implementation agent provides commands but does not run them.

1. Full regression:

   ```powershell
   .\gradlew test
   ```

2. Existing tote-to-bag visual rig:

   ```powershell
   .\gradlew run --args="--scene=tote-to-bag"
   ```

   Verify visible PRL indexing/release, minimum-gap behavior, PCR/bagger flow, later tote
   admission, and reset via the existing rig controls. Do not add the real dataset or a new
   bin/machine visual to the rig. Its existing small-pack fixture is compatibility coverage;
   it does not independently reproduce the 174 mm pack.

3. Rebuild installed artifacts before the usual daily run:

   ```powershell
   .\gradlew installDist
   ```

   Use the same configuration/data/deadline policy and the user's existing installed-run
   launcher. Do not change external config or invent a different installed launcher. The
   equivalent existing Gradle entry point, if the user chooses that route, is:

   ```powershell
   .\gradlew :app:dspFullDayAnalysis --args="--config=C:/misc/cpas-test/scheduler-testing/config/scheduler_conf.json"
   ```

   Existing overwrite/output-path rules still apply; preserve/rename prior outputs or use
   the user's established output handling. Do not delete the progress log automatically.
   Continue past PT25M; compare actual remaining tote/pack/bag values and detailed line-1
   activity with the earlier 4,346/164,838/33,824 plateau. Verify tote `93043164` no longer
   remains indefinitely active with the same PDC/PRL state, and the exact earlier plateau is
   passed. If another blockage appears, retain its diagnostics and report it separately.

Do not judge success from only simulated timestamps, busy utilization, desired line count,
or per-centre closed-tote/allocated-bag totals. The separately identified shared-allocator
reporting duplication remains deferred and is not repaired here. Full-day completion and a
quantitative speedup remain unproven unless the user supplies that evidence; no JFR is needed.

## End-of-feature architecture review (separately user initiated)

After focused and required user checks are green, review the actual complete feature diff and
directly relevant live code. Report PASS, FAIL, or UNPROVEN with concrete method/control-flow
evidence for each item:

1. Front/rear coordinate bounds and neighbor gaps are correct at infeed, arbitrary insertion,
   and advancement; no addition of following length remains in the ahead upper bound.
2. Query purity, simulation-thread movement ownership, compatible constructors, and unchanged
   baseline fixed indexing/release lifecycle.
3. Required travel uses only endpoints; no new snapshots/scans/streams/maps/request objects;
   advancement is one ordered pass with no sort; repeated requests coalesce using max.
4. Both eligible PDC waiting and completed-transfer waiting recover through the explicit PRL
   request. No request before the diversion point, no request for bypass packs, no bypass of
   device/transfer acceptance, and no duplicate physical ownership/counting.
5. Impossible geometry and zero-speed positive deficits remain blocked without fabricated
   completion; wrong-correlation/idle/ready/releasing requests do not mutate.
6. Arrival-driven correlation pinning, partial/zero/missing-pack exception behavior, PRL release
   ordering, PCR/bagger contracts, machine/profile geometry, and scheduler/lease policy unchanged.
7. Tests distinguish movement requests from acceptance; prove exact mixed sizes, true capacity,
   transfer retry, endpoint bounded work, coalescing, and installed composition. The two repaired
   fixtures retain their original purposes instead of concealing overlap.
8. Existing uncommitted diagnostics are not attributed to or overwritten by this feature.
   User daily-run evidence supports only the claimed blocker removal; identify any remaining
   stall, reporting duplication, utilization limitation, or unproven full-day criterion.

No model-run verification command for architecture review. Inspect existing verification
results and user evidence. No implementation/refactor during review; escalate FAIL/UNPROVEN
architecture decisions rather than silently redesigning.

## Documentation closure (separately user initiated after green review)

Only the following document edits are authorized when closure is requested:

- This plan: record completed steps, exact focused verification results, user-provided suite/
  visual/daily-run result, architecture-review outcome, and remaining unproven criteria. Do not
  infer a complete full-day run or performance improvement from passing the earlier plateau.
- `docs/tote-to-bag-requirements.txt`: amend the current PRL-indexing description and explicit
  fixed-distance decision to state that fixed indexing remains the baseline and a matching
  waiting pack can request additional bounded, coalesced travel to clear the intake. Describe
  correct whole-pack gaps, explicit query-versus-mutation separation, both retry points, and
  capacity blocking. Reconcile any statement that fixed distance alone always provides room;
  keep historical branch/commit narratives intact. Add completed regression/current-state
  notes without changing planned manual/oversize handling or global tote scheduling.
- `docs/codex-context.md`: in the current tote-to-bag behavior section, record the implemented
  mixed-length recovery and endpoint/no-new-allocation contract; link this completed plan as a
  reference for later work on local P2P transport. Do not rewrite unrelated programme history.
- `docs/codex-instructions.md`: under established tote-to-bag behavior, add the supplemental
  intake-recovery rule and preserve the prohibition on local global-scheduling decisions. Add
  this plan to conditional references for conveyor/PRL work, not the mandatory nine-document
  reading order. No other current-direction revision is authorized by this feature.

Do not edit historical completed scheduler/exception/bin plans, the diagnostics plan, full-day
schemas, or external configuration/data. Leave alternate scheduler policy, allocation expansion,
shared-allocator reporting duplication, future exception resolution, and generalized bag-fit
handling as explicit deferrals, not newly assigned implementation steps.

No model-run build/test command for documentation closure. Review the complete documentation
diff, run `git diff --check`, and report status. No additional user verification is required
for documentation-only closure beyond user acceptance. Do not commit.
