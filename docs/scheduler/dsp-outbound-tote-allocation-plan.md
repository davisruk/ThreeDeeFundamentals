# DSP Outbound Tote Allocation Plan

Branch: `feature/dsp-outbound-tote-allocation`

Status: ready for implementation.

## Purpose

Allocate completed planned bags to independently supplied outbound physical totes without reusing inbound P2P totes.

This branch must:

- model one logical empty-tote reservoir per P2P line without reservoir geometry;
- maintain at most one open receiving outbound tote per P2P line;
- enforce P2P-line ownership, service-centre purity, pharmacy purity, and configurable bag-count capacity;
- preserve completed bag order and use patient affinity only as a best-effort consequence of that order;
- retain immutable bag and pack source provenance;
- allocate deterministic generated output sheets when one source sheet would otherwise be active on two outbound totes;
- register outbound physical totes and assignment history in the existing lifecycle ledger;
- bridge completed runtime bags from the existing generic bag receiver into DSP allocation state;
- expose immutable allocation snapshots suitable for later scheduler admission, line quiescence, inspection, and Exceptions work.

This branch does not implement OSR inventory, service-centre supply, P2P line leasing, Exception outcomes, NS bags, 32R output, reservoir renderables, or full warehouse wiring.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`
4. `docs/scheduler/dsp-bag-planning-provenance-plan.md`
5. `docs/scheduler/dsp-inbound-tote-lifecycle-plan.md`

Inspect these existing classes before each affected step:

- `BagKey`
- `PlannedBag`
- `PlannedPackTrace`
- `BagPlanningResult`
- `PhysicalToteLifecycleLedger`
- `PhysicalToteLifecycleSnapshot`
- `PhysicalToteRecord`
- `PhysicalToteAssignment`
- `PhysicalToteAssignmentStage`
- `PhysicalToteAssignmentEndReason`
- `BagReceiver`
- `StoredBagReceiver`
- `BagReservation`
- `Bag`
- `BaggingMachine`
- `BaggingSectionInstaller`

## Fixed Decisions

Do not revisit these during implementation:

- Inbound FULL_PACK, ASSOCIATED, and AV02/PRE_P2P physical totes terminate at P2P. They are never renamed, converted, or reused as outbound totes.
- New outbound physical totes use `PhysicalToteRole.OUTBOUND_BAG` and begin in `OUTBOUND_BAG_TOTE` state.
- One P2P line has zero or one open receiving tote. A tote is supplied lazily when the next bag needs one; no unused tote must exist while a line has no work.
- Empty-tote supply is logically unlimited in this phase. ID supply is behind an interface so a finite reservoir can replace it later.
- Tote capacity is maximum completed bag count, not physical pack count or volume.
- The first bag assigns a new tote's service centre and pharmacy.
- A tote never mixes pharmacies or service centres.
- A full tote closes immediately after accepting the bag that reaches capacity.
- A pharmacy or service-centre mismatch closes the current tote before a new tote is opened for the candidate bag.
- Applicable-work completion, service-centre line change, and hard cutoff are explicit close operations. Do not infer them from temporary bagger idleness.
- Closed totes are never reopened.
- Patient affinity does not reorder bags, bypass purity, exceed capacity, or reopen a tote. Bags for the same patient remain together only when normal arrival order and remaining capacity permit it.
- An all-missing logical prescription still creates no fake physical bag. Allocation for a future empty NS bag remains part of Exception work because no terminal line-outcome/NS model exists yet.
- Existing `PlannedBag` and `PlannedPackTrace` records remain immutable and unchanged.
- Existing runtime `Bag`, `CompletedBag`, `BaggingMachine`, and generic bag handoff contracts remain generic.
- Do not parse arbitrary correlations. Resolve runtime bag correlations through `BagPlanningResult.findBagByCorrelationId(...)`.
- The existing `StoredBagReceiver` remains the bagger discharge boundary. A DSP simulation controller drains completed bags from it into the outbound allocator after receipt.
- Allocation and lifecycle mutation are simulation-thread-owned. Add no locks, worker threads, or concurrent collections.
- Simulation time for lifecycle records comes from `SimulationContext.getSimulationTimeSeconds()` at controller application time, converted deterministically to `Duration` using rounded nanoseconds.
- One outbound tote may have active assignments for several logical sheets.
- One logical sheet may have only one active physical assignment. This existing ledger invariant must remain enforced.
- When an outbound tote closes, each `OUTBOUND_BAG` assignment advances to `OUTBOUND` on that same tote before the tote transitions to terminal `OUTBOUND`. Those active output assignments remain until later dispatch/32R work terminates them.
- A later tote requiring work from a sheet that is still actively assigned to another outbound tote receives a generated sheet under the same order ID.
- Generated sheet numbers are allocated as `max(all known and already generated sheet numbers for that orderId) + 1`.
- The generated-sheet allocator must be initialized with all known dataset `OrderSheetKey`s, not only sheets in the current bag-planning request.
- Generated output ownership is recorded separately from immutable source and fulfilment provenance. Do not mutate source provenance or fabricate a `NotionalToteOrder`.
- The same source sheet and target outbound tote always resolve to the same output sheet.
- Preserve planned bag order. Do not sort by hash iteration, patient, pharmacy, bag key, or tote ID.
- No renderables or visual changes are required in this branch.

## Package And Vocabulary

Create outbound allocation types under:

```text
online.davisfamily.warehouse.sim.dsp.outbound
```

Use these names:

- `P2pLineId`: typed P2P instance identity.
- `OutboundToteConfig`: maximum bag-count configuration.
- `OutboundToteClosureReason`: why an outbound tote closed.
- `OutputSheetAllocation`: source owning sheet to output sheet mapping for one bag/tote allocation.
- `AllocatedOutboundBag`: one planned bag, its outbound physical tote, and its output-sheet allocations.
- `OutboundToteSnapshot`: immutable open/closed tote state and ordered bag contents.
- `OutboundAllocationSnapshot`: immutable per-line and per-bag lookup state.
- `OutboundToteIdSource`: empty-reservoir identity boundary.
- `DeterministicOutboundToteIdSource`: Phase 1 ID implementation.
- `OutputSheetAllocator`: deterministic source-to-output sheet allocation.
- `OutboundToteAllocator`: simulation-thread-owned multi-line allocation service.
- `OutboundToteAllocationController`: bridge from a line's `StoredBagReceiver` to the allocator.

## Step 1: Add Outbound Allocation Value Types

Create `P2pLineId` as:

```java
public record P2pLineId(String value)
```

Trim and reject blank values.

Create `OutboundToteConfig` as:

```java
public record OutboundToteConfig(int maximumBagCount)
```

Reject values below one.

Create `OutboundToteClosureReason` with exactly:

```text
CAPACITY_REACHED
PHARMACY_CHANGED
SERVICE_CENTRE_CHANGED
APPLICABLE_WORK_COMPLETE
HARD_CUTOFF
```

Create `OutputSheetAllocation` with exactly:

```java
OrderSheetKey sourceOwningSheetKey
OrderSheetKey outputSheetKey
```

Expose `generated()` as `!sourceOwningSheetKey.equals(outputSheetKey)`.

Create `AllocatedOutboundBag` with exactly:

```java
PlannedBag plannedBag
PhysicalToteId outboundPhysicalToteId
List<OutputSheetAllocation> outputSheetAllocations
```

Rules:

- defensively copy allocations;
- allocation source keys must equal the planned bag's owning keys in the same order;
- reject duplicate source or output keys within one bag;
- expose bag identity through `plannedBag.bagKey()` rather than duplicating it.

Required tests in `OutboundAllocationDomainTest`:

- `shouldValidateP2pLineAndOutboundCapacity()`
- `shouldRepresentOriginalAndGeneratedOutputSheetOwnership()`
- `shouldRejectAllocationThatDoesNotCoverPlannedBagOwnership()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationDomainTest
```

## Step 2: Add Deterministic Outbound Tote Identity Supply

Create:

```java
public interface OutboundToteIdSource {
    PhysicalToteId nextId(P2pLineId lineId);
}
```

Create `DeterministicOutboundToteIdSource`:

- maintain a one-based counter independently for each line;
- emit `outbound-<lineId>-<ordinal>`;
- preserve first-seen line order internally;
- reject null line IDs;
- remain simulation-thread-owned and unsynchronized.

Examples:

```text
p2p-1 -> outbound-p2p-1-1
p2p-1 -> outbound-p2p-1-2
p2p-2 -> outbound-p2p-2-1
```

The lifecycle ledger remains authoritative for global physical-ID uniqueness. A source that returns an existing ID must fail when registration occurs; do not silently request another ID.

Required tests in `DeterministicOutboundToteIdSourceTest`:

- `shouldAllocateStableOneBasedIdsPerP2pLine()`
- `shouldKeepLineSequencesIndependent()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSourceTest
```

## Step 3: Add Deterministic Output Sheet Allocation

Create `OutputSheetAllocator` initialized with a non-null collection of every known dataset `OrderSheetKey`.

It must track:

- the highest known/generated sheet number per order ID;
- the output sheet previously selected for each source-sheet/outbound-tote pair;
- generated-sheet provenance back to the source owning sheet.

Expose an operation equivalent to:

```java
List<OutputSheetAllocation> resolve(
        List<OrderSheetKey> sourceOwningSheetKeys,
        PhysicalToteId targetOutboundToteId,
        PhysicalToteLifecycleSnapshot lifecycleSnapshot)
```

Resolution algorithm for each source key in input order:

1. Return the existing mapping when this source key was already resolved for the same target tote.
2. Find every output sheet previously allocated from that source key that still has an active lifecycle assignment.
3. If one is actively assigned to the target tote, reuse it.
4. If no output sheet for the source is active, use the source key itself.
5. If an output sheet for the source is active on another tote, allocate the next sheet number for that order ID.
6. Record the source/target mapping and return it.

Rules:

- consider existing known sheet numbers before allocating a generated number;
- generated sheets retain the same `orderId` as the source;
- several source sheets from different orders may resolve to one target tote;
- resolution is deterministic and preserves source-key order;
- return immutable lists;
- reject duplicate source keys in one call;
- reject a lifecycle snapshot containing an active non-outbound assignment for a selected output key rather than hiding an unconsumed inbound tote;
- do not mutate the lifecycle ledger in this class.

Required tests in `OutputSheetAllocatorTest`:

- `shouldRetainOriginalSheetForFirstOutboundAssignment()`
- `shouldReuseOutputSheetForSameSourceAndTargetTote()`
- `shouldGenerateNextAvailableSheetForConcurrentOutboundAssignment()`
- `shouldConsiderExistingHigherSheetNumbersWhenGenerating()`
- `shouldAllocateSeveralOrdersIntoOneTargetToteDeterministically()`
- `shouldRejectSourceSheetStillAssignedToNonOutboundStage()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocatorTest
```

## Step 4: Add Immutable Outbound Tote And Allocation Snapshots

Create `OutboundToteSnapshot` with exactly:

```java
PhysicalToteId physicalToteId
P2pLineId p2pLineId
Optional<String> serviceCentreId
Optional<String> pharmacyId
int maximumBagCount
List<AllocatedOutboundBag> allocatedBags
Optional<OutboundToteClosureReason> closureReason
```

Derived behavior:

- `open()` when closure reason is empty;
- `assigned()` when service centre and pharmacy are present;
- `bagCount()` and `remainingBagCapacity()`;
- `containsPatient(String patientId)` for inspection/best-effort affinity evidence.

Validation:

- service centre and pharmacy are either both absent or both present;
- an assigned tote contains only matching service-centre/pharmacy bags;
- allocated bag tote IDs match the snapshot tote ID;
- bag count does not exceed capacity;
- an unassigned tote has no bags;
- lists are immutable.

Create `OutboundAllocationSnapshot` containing ordered:

```java
Map<P2pLineId, OutboundToteSnapshot> openTotesByLine
List<OutboundToteSnapshot> closedTotes
List<AllocatedOutboundBag> allocatedBags
```

Provide immutable lookup by line, physical tote ID, and `BagKey`. Reject duplicate tote IDs and bag keys.

Required tests in `OutboundAllocationSnapshotTest`:

- `shouldRepresentAssignedOpenAndClosedOutboundTotes()`
- `shouldExposeRemainingCapacityAndPatientPresence()`
- `shouldProvideImmutableLineToteAndBagLookups()`
- `shouldRejectPurityCapacityOrDuplicateIdentityViolations()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshotTest
```

## Step 5: Implement Lifecycle-Backed Outbound Tote Allocation

Create `OutboundToteAllocator` with:

```java
PhysicalToteLifecycleLedger lifecycleLedger
OutboundToteIdSource toteIdSource
OutputSheetAllocator outputSheetAllocator
OutboundToteConfig config
```

It owns mutable per-line open tote state, ordered closed tote history, and allocated bag history. It must expose:

```java
AllocatedOutboundBag allocate(P2pLineId lineId, PlannedBag bag, Duration allocationTime)
Optional<OutboundToteSnapshot> closeForApplicableWorkCompletion(P2pLineId lineId, Duration time)
Optional<OutboundToteSnapshot> closeForServiceCentreChange(P2pLineId lineId, Duration time)
Optional<OutboundToteSnapshot> closeForHardCutoff(P2pLineId lineId, Duration time)
OutboundAllocationSnapshot snapshot()
```

Allocation algorithm:

1. Validate arguments, monotonic/nonnegative time against mutations on that line, and that the bag key has not already been allocated.
2. If an open tote exists with another service centre, close it as `SERVICE_CENTRE_CHANGED`.
3. Else if an open tote exists with another pharmacy, close it as `PHARMACY_CHANGED`.
4. If no open tote remains, request an ID, register `PhysicalToteRecord.outboundBag(id)`, and create one unassigned open tote for the line.
5. Resolve output sheets for all planned bag owning sheets against a fresh lifecycle snapshot.
6. For each resolved output sheet not already actively assigned to this tote, create an `OUTBOUND_BAG` assignment at the allocation time.
7. Add the bag, assigning tote service centre/pharmacy from the first bag.
8. Publish one `AllocatedOutboundBag` in planned owning-sheet order.
9. If capacity is now reached, close immediately as `CAPACITY_REACHED`.

Closing algorithm:

1. Return empty when the line has no open tote; explicit close operations are idempotent for an idle line.
2. Reject closing an unassigned/empty tote if such state is ever exposed by a failed operation.
3. For each active `OUTBOUND_BAG` assignment on that tote, in assignment sequence order:
   - terminate it with `OUTBOUND_TOTE_CLOSED`;
   - assign the same output sheet to the same tote at stage `OUTBOUND` and the same close time.
4. Transition the physical tote from `OUTBOUND_BAG_TOTE` to `OUTBOUND`.
5. Record the immutable closed snapshot and remove it from the line's open slot.

Rules:

- validate the complete candidate before mutating state where practical;
- never leave two open totes for one line;
- never alter a closed snapshot;
- no bag may be allocated twice, including after closure;
- multiple logical/output sheets may be active on one outbound tote;
- the ledger's one-active-tote-per-sheet invariant remains authoritative;
- do not terminate `OUTBOUND` assignments in this branch;
- do not create an empty tote until a bag requires it;
- patient ID does not participate in acceptance or closure.

Required tests in `OutboundToteAllocatorTest`:

- `shouldOpenOutboundToteAndAssignFirstBagIdentity()`
- `shouldAggregateSeveralLogicalSheetsIntoOnePureOutboundTote()`
- `shouldCloseAtConfiguredBagCapacityAndOpenAnotherForLaterBag()`
- `shouldCloseBeforeAcceptingDifferentPharmacyOrServiceCentre()`
- `shouldNeverReuseInboundPhysicalToteAsOutboundTote()`
- `shouldAdvanceClosedToteAndAssignmentsToOutboundLifecycle()`
- `shouldKeepSamePatientTogetherOnlyWhileCapacityAllows()`
- `shouldRejectDuplicateBagAllocation()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest
```

## Step 6: Complete Generated-Sheet Overflow Integration

Add focused allocator scenarios proving output-sheet behavior through the real lifecycle ledger.

Rules:

- the first tote for a consumed source sheet uses its existing `OrderSheetKey`;
- closing that tote advances the sheet assignment to active `OUTBOUND`;
- a later tote containing another bag owned by that source sheet receives a generated sheet;
- later bags from that source placed in the same second tote reuse the same generated sheet;
- output from another source order may share either tote without changing its own order ID;
- generated output ownership changes neither `PlannedBag.owningOrderSheetKeys` nor `PlannedPackTrace.sourceProvenance`;
- generated sheets are allocation records only in this branch, not synthetic scheduler orders.

Required tests in `OutboundGeneratedSheetIntegrationTest`:

- `shouldGenerateOverflowSheetWhenOriginalRemainsAssignedToClosedOutboundTote()`
- `shouldReuseGeneratedSheetForLaterBagInSameOutboundTote()`
- `shouldKeepOriginalSourceAndFulfilmentProvenanceAfterOutputSplit()`
- `shouldAvoidSheetNumberCollisionsAcrossSeveralOrders()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundGeneratedSheetIntegrationTest
```

## Step 7: Bridge The Existing Bag Receiver To Outbound Allocation

Create `OutboundToteAllocationController` implementing `SimulationController`.

Constructor inputs:

```java
P2pLineId p2pLineId
StoredBagReceiver completedBagReceiver
BagPlanningResult bagPlanningResult
OutboundToteAllocator outboundToteAllocator
```

On each `update(context, dtSeconds)`:

1. Copy the receiver's current received-bag list in receipt order.
2. For each runtime `Bag`, resolve its correlation through `bagPlanningResult.findBagByCorrelationId(...)`.
3. Fail clearly and leave the runtime bag in the receiver if no planned bag exists.
4. Verify runtime physical pack IDs and order equal `PlannedBag.physicalPackIds()`; fail clearly and leave it in the receiver on mismatch.
5. Convert `context.getSimulationTimeSeconds()` to nonnegative `Duration` using `Duration.ofNanos(Math.round(seconds * 1_000_000_000d))`.
6. Allocate the planned bag to the configured line.
7. Remove the runtime bag from `StoredBagReceiver` only after successful allocation.

Rules:

- the controller does not create, animate, or retain a second runtime `Bag`;
- receiver order is allocation order;
- successful removal provides exactly-once application together with allocator duplicate protection;
- no arbitrary correlation parsing;
- do not modify `BaggingMachine`, `BagReceiver`, `BagReservation`, or `BaggingSectionInstaller`;
- the generic receiver remains usable by existing debug rigs and tests.

Required tests in `OutboundToteAllocationControllerTest`:

- `shouldAllocateReceivedRuntimeBagUsingPlannedBagCorrelation()`
- `shouldPreserveReceiverOrderAcrossSeveralCompletedBags()`
- `shouldRemoveBagOnlyAfterSuccessfulAllocation()`
- `shouldRejectUnknownCorrelationOrPackMismatch()`
- `shouldApplyEachReceivedBagExactlyOnce()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationControllerTest
```

## Step 8: Prove Independent Multi-Line Reservoir State

Use one `OutboundToteAllocator` with at least two `P2pLineId`s.

Verify:

- tote ID sequences and open slots are independent per line;
- each line has at most one open tote;
- closing or filling one line does not affect the other;
- the same pharmacy/service centre may be active on several lines without sharing a physical tote;
- assigning output from one source sheet concurrently on two lines produces a generated sheet on the second line;
- snapshots retain deterministic line first-occurrence order;
- this is allocation state only and does not yet assign scheduler P2P leases.

Required tests in `MultiLineOutboundToteAllocationTest`:

- `shouldMaintainIndependentOpenOutboundTotePerP2pLine()`
- `shouldNotCloseOtherLineWhenOneLineReachesCapacity()`
- `shouldGenerateSheetForConcurrentSameOrderOutputAcrossLines()`
- `shouldPreserveDeterministicLineAndToteHistoryOrder()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.MultiLineOutboundToteAllocationTest
```

## Step 9: Add End-To-End Bag-To-Outbound-Tote Scenario

Create `DspOutboundToteAllocationScenarioTest` under the outbound test package.

Build a deterministic scenario containing:

- a `BagPlanningResult` with at least two prescriptions and one capacity-split prescription;
- at least two source/fulfilment logical sheets;
- consumed inbound/PRE_P2P physical tote assignments before outbound allocation;
- completed runtime bags delivered through `StoredBagReceiver`;
- one P2P line with outbound bag capacity two;
- one later pharmacy change;
- enough bags from one source sheet to require a generated output sheet after the first tote closes.

Verify:

1. no inbound physical tote ID appears as an outbound tote ID;
2. runtime bag correlations resolve to authoritative `BagKey`s;
3. planned bag order is preserved through receiver and allocation;
4. every allocated bag identifies its outbound physical tote and output sheets;
5. every output sheet retains its original source owning sheet mapping;
6. closed totes are pharmacy- and service-centre-pure and within capacity;
7. the first source sheet is retained where valid and deterministic overflow sheets are generated where required;
8. lifecycle history shows consumed inbound totes separately from outbound bag totes;
9. pack/source/fulfilment provenance remains unchanged;
10. no Exception visit, NS bag, 32R, renderable, scheduler lease, or OSR mutation is created.

Required test methods:

- `shouldAllocatePlannedBagsToIndependentOutboundPhysicalTotes()`
- `shouldPreservePurityCapacityAndDeterministicOutputSheets()`
- `shouldRetainBagPackAndLogicalProvenanceAcrossP2pBoundary()`
- `shouldKeepInboundAndOutboundPhysicalToteLifecyclesSeparate()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.DspOutboundToteAllocationScenarioTest
```

## Step 10: Regression And Visual Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.totebag.machine.* --tests online.davisfamily.warehouse.sim.totebag.handoff.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify existing pack/tote/bag motion remains unchanged;
- verify `ALT+R` still resets each scene;
- no outbound tote renderable is expected in this branch.

Before branch closure:

- update this plan status to implementation complete and verified;
- update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and `docs/codex-instructions.md`;
- record any deliberately deferred all-missing/NS allocation behavior.

## Completion Criteria

- Outbound physical tote IDs are independently supplied per P2P line.
- Inbound physical totes are never reused as outbound totes.
- Each line has at most one open receiving outbound tote.
- Outbound totes enforce service-centre purity, pharmacy purity, and configurable bag-count capacity.
- Closed totes are not reopened.
- Explicit flush, service-centre-change, and hard-cutoff closure are supported.
- Patient affinity remains best effort without reordering or violating hard constraints.
- Planned bags are traceable to outbound physical totes and output logical sheets.
- Deterministic generated sheets prevent concurrent physical assignment of one logical sheet.
- Source and fulfilment provenance remain immutable across output splitting.
- Lifecycle records distinguish inbound consumption, outbound bag receipt, and outbound closure.
- Existing generic bagger and bag receiver contracts remain unchanged.
- No Exception behavior, NS bag, 32R, OSR inventory, P2P lease, database, renderable, or new thread is introduced.
- Focused tests, complete tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, create the detailed plan for:

```text
feature/dsp-osr-physical-inventory
```

That branch will model physical OSR occupancy, the 06:00 Letchworth/Swansea preload, authorized service-centre supply into OSR, and the separation between upstream service-centre authorization and individual tote release.
