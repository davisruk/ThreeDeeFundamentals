# DSP Rate-Limited Service-Centre Supply Plan

Branch: `feature/dsp-rate-limited-service-centre-supply`

Status: plan ready; implementation not started.

## Purpose

Model the upstream Cencora/AHDL supply boundary that keeps the DSP OSR replenished. The feature must authorize later service centres in retained 12N priority order when OSR occupancy reaches a configurable low-water mark, then admit their physical inbound tote manifests at a deterministic configurable rate without exceeding OSR capacity.

This branch must:

- retain 12N `orderPriority` through the loaded logical-order model;
- derive a deterministic service-centre supply plan from `LoadedDspData`;
- treat Letchworth `104` and Swansea `108` as the already-preloaded startup service centres;
- authorize one later service centre at a time when the OSR is at or below its configured low-water mark;
- preserve ADAPTED-first physical supply within each service centre;
- rate-limit physical manifest admission using simulation time, not wall-clock time or update count;
- pause without losing order when the OSR is full and resume without a catch-up burst;
- authorize EMPTY sheets logically without consuming OSR capacity;
- expose immutable supply snapshots suitable for later scheduler, inspection, and metrics work;
- keep all live inventory mutations on the simulation thread.

This branch does not release individual totes from OSR into DSP, activate inbound tote lifecycle state, change scheduler candidate ranking, allocate P2P lines, implement trunker deadlines, add renderables, wire the fixed-step driver into `SoftwareRenderer`, or run a complete production day.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-osr-physical-inventory-plan.md`
4. `docs/scheduler/dsp-operational-simulation-clock-plan.md`
5. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
6. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `TwelveNMessageJson`
- `TwelveNLineMappingSupport`
- `TwelveNOrderMapper`
- `MappedTwelveNOrder`
- `DspDatasetAssembler`
- `LoadedDspData`
- `DspDatasetLoadReport`
- `NotionalToteOrder`
- `InboundToteManifest`
- `OsrInventoryConfig`
- `OsrInventoryBootstrapFactory`
- `OsrBootstrapState`
- `OsrPhysicalInventory`
- `OsrInventorySnapshot`
- `DspOperationalClockSnapshot`
- `DspOperationalClockController`
- `SimulationController`

## Fixed Decisions

Do not revisit these during implementation:

- Service-centre supply authorization and individual OSR processing release are different boundaries. This branch implements only supply authorization and physical admission into OSR.
- Physical supply operates per `InboundToteManifest` and `PhysicalToteId`. Never collapse several physical manifests into one logical `OrderSheetKey` state.
- `orderPriority` is retained on `NotionalToteOrder` because it is currently parsed by Jackson but discarded by `TwelveNOrderMapper`.
- A priority of `999` ranks before `998`. Priority orders service centres, not orders within one service centre.
- Existing hand-built fixtures may use an explicit unspecified-priority sentinel of `0` through a compatibility constructor. Production 12N mapping must require a positive integer, and supply-plan construction must reject a service centre whose retained orders have only unspecified priority.
- When one service centre contains inconsistent positive priorities, retain the priority from its earliest source-sequence order, report the inconsistency, and continue deterministically. Do not silently choose the maximum or minimum.
- If service centres share an effective priority, report the tie and order them by earliest source sequence, then service-centre ID.
- Start-of-day preload service centres come from `OsrInventoryConfig.preloadServiceCentreIds()`. Their physical manifests are already stored by `OsrInventoryBootstrapFactory`; the supply coordinator must never admit them again.
- Preloaded EMPTY sheets remain sourced from `OsrBootstrapState.authorizedEmptyOrderSheetKeys()` and consume no physical inventory slots.
- Later service centres are considered for authorization in the deterministic supply-plan order after excluding configured preload service centres.
- Authorize at most one service centre during one coordinator advance.
- Do not authorize another later service centre while the currently authorized service centre still has physical manifests upstream. Once that service centre's complete physical batch has entered OSR, a later advance may authorize the next service centre if occupancy is still at or below the low-water mark.
- This rule does not require the previous service centre's totes to leave OSR or finish DSP. The OSR may contain several authorized service centres.
- Within one service centre, all ADAPTED manifests are supplied before any FULL_PACK or ASSOCIATED manifest. FULL_PACK and ASSOCIATED retain their combined source-sequence order; neither type receives a new precedence over the other.
- EMPTY sheets are authorized immediately when their service centre is authorized. They do not join the physical arrival schedule and do not call `OsrPhysicalInventory.store(...)`.
- The low-water comparison is inclusive: `occupancy <= lowWaterMark`.
- The production low-water value is unknown. It is required configuration and must not be invented as a hardcoded production default.
- Fixed-rate baselines are one tote every 3 simulation seconds for peak supply and one every 9 simulation seconds for a representative busy hour.
- The first physical tote for a newly authorized service centre becomes due one complete configured interval after authorization. Authorization itself does not admit a tote.
- Admission at exactly the due simulation time is allowed.
- When updates skip several due times and capacity remains available, admit all due manifests in order so the simulated fixed rate is preserved.
- When a due tote is blocked because OSR is full, retain that tote and its position. After capacity becomes available, admit exactly that blocked tote at the current simulation time and schedule the following tote one full interval later. Do not discharge accumulated blocked arrivals as a burst.
- `OsrPhysicalInventory.store(...)` is the only physical-admission mutation. Do not replace inventory state or bypass its duplicate/capacity validation.
- A tote admitted by this branch is not automatically lifecycle-registered, activated, selected, or released. Those are separate transitions.
- Supply arithmetic uses `DspOperationalClockSnapshot.elapsedSimulationTime()`. Do not use `dtSeconds`, update counts, sleeps, wall-clock APIs, or independently accumulated business time.
- The hard-cutoff flag remains observational in this branch. Do not stop supply or terminalize work at 22:00 or midnight.
- Mutable coordinator state and `OsrPhysicalInventory` remain simulation-thread owned. Snapshots are immutable; add no worker thread, lock, or concurrent collection.
- Reset remains reconstruction-based. Do not add reset methods or a global reset registry.

## Package And Vocabulary

Create supply types under:

```text
online.davisfamily.warehouse.sim.dsp.supply
```

Use these names:

- `ServiceCentreSupplyIssueType`: `INCONSISTENT_PRIORITIES` or `DUPLICATE_PRIORITY`.
- `ServiceCentreSupplyIssue`: immutable description of a priority inconsistency or tie.
- `ServiceCentreSupplyBatch`: one service centre's priority, ordered physical manifests, EMPTY sheets, and source-order tie breaker.
- `DspServiceCentreSupplyPlan`: immutable priority-ordered batches and reported issues.
- `DspServiceCentreSupplyPlanFactory`: derives the plan from loaded data and OSR preload configuration.
- `InboundToteArrivalPolicy`: replaceable policy that supplies an ID and the interval before a specified next physical manifest.
- `FixedIntervalInboundToteArrivalPolicy`: deterministic fixed-interval implementation with peak and representative-busy factories.
- `ServiceCentreSupplyConfig`: configured low-water mark.
- `ServiceCentreAuthorizationState`: `PRELOADED`, `HELD_UPSTREAM`, `AUTHORIZED`, or `SUPPLY_COMPLETE`.
- `PhysicalToteSupplyState`: `PRELOADED_IN_OSR`, `HELD_UPSTREAM`, `AUTHORIZED_WAITING`, `BLOCKED_BY_OSR_CAPACITY`, `STORED_IN_OSR`, or `DEPARTED_FROM_OSR`.
- `PhysicalToteSupplySnapshot`: immutable per-manifest supply state.
- `ServiceCentreSupplySnapshot`: immutable per-service-centre counters and authorization state.
- `DspSupplySnapshot`: immutable complete supply view.
- `DspServiceCentreSupplyCoordinator`: simulation-thread-owned authorization and physical-admission state machine.
- `DspServiceCentreSupplyController`: `SimulationController` adapter that reads the latest clock snapshot and advances the coordinator.

Do not put these types in `dsp.scheduler`: upstream supply is an input boundary, not OSR candidate scheduling. Do not put policy state inside `OsrPhysicalInventory`; inventory remains a generic physical capacity aggregate.

## Step 1: Retain 12N Order Priority

Extend the canonical `NotionalToteOrder` record with `int orderPriority` immediately before `long sequenceNumber`.

Rules:

- canonical construction accepts `orderPriority >= 0`;
- `0` means unspecified and exists only to preserve hand-built legacy/debug fixtures;
- add a deprecated seven-argument compatibility constructor with the existing signature that delegates with priority `0`;
- do not infer priority from service-centre ID or source sequence;
- update production and modified DSP tests to use the canonical constructor when priority matters.

Update `TwelveNLineMappingSupport.validateMessage(...)` to require a non-null `message.orderPriority()`. Update `TwelveNOrderMapper` to parse `orderPriority.payload` with `parsePositiveInt(...)` and pass it to `NotionalToteOrder`.

Update `DspDatasetAssembler` so:

- `withItems(...)` preserves priority;
- `LogicalOrderGroup.add(...)` rejects conflicting priorities among contributions to the same `OrderSheetKey`;
- `LogicalOrderGroup.toOrder()` preserves the first contribution's priority.

Do not add priority to `InboundToteManifest`; a manifest retains physical identity and links to its logical sheet, while service-centre priority is derived from logical orders.

Update focused tests in:

- the existing `DspOrderModelTest`;
- `TwelveNOrderMapperTest`;
- `DspDatasetAssemblerTest`.

Required test behavior:

- mapped 12N priority is retained on the logical order;
- missing, blank, zero, or nonnumeric production priority is rejected;
- filtered-line and merged-sheet paths preserve priority;
- conflicting priorities for contributions to the same sheet are rejected;
- the legacy constructor produces priority `0` without changing existing fixtures.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.* --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapperTest --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest
```

## Step 2: Build The Immutable Service-Centre Supply Plan

Create:

```java
public enum ServiceCentreSupplyIssueType {
    INCONSISTENT_PRIORITIES,
    DUPLICATE_PRIORITY
}
```

Create immutable records with defensive copies and null/blank validation:

```java
public record ServiceCentreSupplyIssue(
        ServiceCentreSupplyIssueType type,
        int priority,
        List<String> serviceCentreIds,
        List<Integer> observedPriorities) {}

public record ServiceCentreSupplyBatch(
        String serviceCentreId,
        int priority,
        long firstSourceSequenceNumber,
        boolean preloadedAtStart,
        List<InboundToteManifest> physicalManifests,
        Set<OrderSheetKey> emptyOrderSheetKeys) {}

public record DspServiceCentreSupplyPlan(
        List<ServiceCentreSupplyBatch> batches,
        List<ServiceCentreSupplyIssue> issues) {}
```

Expose lookup helpers on `DspServiceCentreSupplyPlan`:

- `Optional<ServiceCentreSupplyBatch> findBatch(String serviceCentreId)`;
- `List<ServiceCentreSupplyBatch> postStartupBatches()`.

Create `DspServiceCentreSupplyPlanFactory.create(LoadedDspData, OsrInventoryConfig)`.

Factory algorithm, in this exact order:

1. Group retained logical orders by normalized `serviceCentreId`, preserving first appearance.
2. Set `firstSourceSequenceNumber` to the minimum logical-order sequence for that centre.
3. Reject a service centre if its earliest order has priority `0` or if no positive priority exists.
4. Use the earliest source-order priority as the effective priority.
5. If any other order in that centre has a different priority, add one `INCONSISTENT_PRIORITIES` issue containing the sorted distinct observed priorities.
6. Mark the batch preloaded when its ID is in `OsrInventoryConfig.preloadServiceCentreIds()`.
7. Select physical manifests for the centre. Sort all ADAPTED manifests first by `sourceSequenceNumber` then physical-tote ID. Append FULL_PACK and ASSOCIATED manifests together by `sourceSequenceNumber` then physical-tote ID.
8. Reject any unexpected physical order type rather than silently dropping it.
9. Select EMPTY sheet keys from logical orders in order sequence, preserving insertion order.
10. Sort service-centre batches by effective priority descending, then first source sequence ascending, then service-centre ID ascending.
11. For every effective priority shared by multiple centres, add one `DUPLICATE_PRIORITY` issue with IDs in final deterministic order.

Validate that every configured preload service-centre ID has a batch in loaded data. Do not check inventory contents here; bootstrap/coordinator integration owns that check.

Create `DspServiceCentreSupplyPlanFactoryTest`.

Required tests:

- `shouldOrderServiceCentresByDescendingRetainedPriority()`
- `shouldPreserveAdaptedFirstThenCombinedFulfilmentSourceOrder()`
- `shouldKeepEveryPhysicalManifestForARepeatedLogicalSheet()`
- `shouldKeepEmptySheetsSeparateFromPhysicalManifests()`
- `shouldMarkConfiguredStartupBatches()`
- `shouldReportInconsistentPrioritiesUsingEarliestOrderPriority()`
- `shouldReportAndDeterministicallyResolveDuplicatePriorities()`
- `shouldRejectMissingPriorityAndUnknownPreloadCentre()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyPlanFactoryTest
```

## Step 3: Add Low-Water And Arrival-Rate Configuration

Create:

```java
public record ServiceCentreSupplyConfig(int lowWaterMark) {}
```

Rules:

- require `lowWaterMark >= 0`;
- do not add a no-argument or hardcoded production low-water default;
- the coordinator later validates `lowWaterMark < inventory.capacity()` because that check needs the live inventory configuration.

Create:

```java
public interface InboundToteArrivalPolicy {
    String policyId();

    Duration intervalBeforeNextTote(
            InboundToteManifest nextManifest,
            long previouslyAdmittedToteCount);
}
```

Create `FixedIntervalInboundToteArrivalPolicy`:

- constructor takes a nonblank policy ID and a strictly positive `Duration`;
- `intervalBeforeNextTote(...)` rejects null manifests and negative counts and returns the configured interval;
- expose `interval()`;
- expose `peak()` with ID `FIXED_PEAK_1200_PER_HOUR` and `Duration.ofSeconds(3)`;
- expose `representativeBusyHour()` with ID `FIXED_BUSY_400_PER_HOUR` and `Duration.ofSeconds(9)`.

The interface intentionally receives the next manifest and admitted count so a later deterministic variable-arrival policy can be substituted without changing authorization or inventory admission code. Do not add randomness in this branch.

Create:

- `ServiceCentreSupplyConfigTest`;
- `FixedIntervalInboundToteArrivalPolicyTest`.

Required tests:

- `shouldRequireNonnegativeLowWaterMark()`
- `shouldExposePeakAndRepresentativeBusyIntervals()`
- `shouldReturnConfiguredIntervalDeterministically()`
- `shouldRejectInvalidArrivalPolicyInputs()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfigTest --tests online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicyTest
```

## Step 4: Define Immutable Supply Snapshots And Bootstrap State

Create the exact enum values listed in Package And Vocabulary.

Create `PhysicalToteSupplySnapshot` with:

```text
physicalToteId
orderSheetKey
orderType
serviceCentreId
sourceSequenceNumber
state
```

Create `ServiceCentreSupplySnapshot` with:

```text
serviceCentreId
priority
authorizationState
authorizationElapsedTime: Optional<Duration>
physicalManifestCount
preloadedCount
admittedAfterStartupCount
upstreamWaitingCount
authorizedEmptyOrderSheetKeys
physicalTotes
```

Create root `DspSupplySnapshot` with:

```text
policyId
lowWaterMark
osrCapacity
osrOccupancy
activeInboundServiceCentreId: Optional<String>
nextPhysicalAdmissionElapsedTime: Optional<Duration>
authorizedEmptyOrderSheetKeys
serviceCentres
admittedAfterStartupCount
```

All collection components must be defensive immutable copies preserving deterministic order. Validate nonnegative counts and that derived counts are internally consistent.

Create the initial `DspServiceCentreSupplyCoordinator` constructor:

```java
public DspServiceCentreSupplyCoordinator(
        DspServiceCentreSupplyPlan plan,
        ServiceCentreSupplyConfig config,
        InboundToteArrivalPolicy arrivalPolicy,
        OsrBootstrapState bootstrapState)
```

Initial-state rules:

- reject `lowWaterMark >= inventory capacity`;
- verify every manifest in each preloaded batch is currently stored in bootstrap inventory;
- verify no post-start manifest is already stored or departed;
- verify bootstrap EMPTY authorization exactly equals the union of EMPTY keys from preloaded batches;
- mark preload physical manifests `PRELOADED_IN_OSR` and preload batches `PRELOADED`;
- mark later batches and manifests held upstream;
- initialize authorized EMPTY keys from bootstrap state;
- expose `DspSupplySnapshot snapshot()`.

Do not expose an additional mutable-inventory getter from the coordinator. Composition code that performs a later explicit OSR departure already owns the `OsrBootstrapState.inventory()` reference.

Do not implement authorization or timed admission in this step.

Create `DspServiceCentreSupplyCoordinatorBootstrapTest`.

Required tests:

- `shouldRepresentPreloadedAndHeldUpstreamBatches()`
- `shouldSeedOnlyPreloadedEmptyAuthorization()`
- `shouldRepresentEveryPhysicalManifestIndependently()`
- `shouldRejectLowWaterMarkAtOrAboveCapacity()`
- `shouldRejectBootstrapInventoryThatDoesNotMatchSupplyPlan()`
- `shouldReturnDeeplyImmutableSnapshots()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyCoordinatorBootstrapTest
```

## Step 5: Implement Priority-Ordered Low-Water Authorization

Add:

```java
public void advance(DspOperationalClockSnapshot clockSnapshot)
```

Authorization algorithm:

1. Reject null clock snapshots and reject elapsed time moving backward from the previous advance.
2. Read a fresh `OsrInventorySnapshot` from the bootstrap inventory.
3. If a post-start service centre is currently active for inbound supply, do not authorize another.
4. If OSR occupancy is above the inclusive low-water mark, do nothing.
5. Select the first `HELD_UPSTREAM` post-start batch in supply-plan order.
6. Mark exactly that batch `AUTHORIZED` and record `clockSnapshot.elapsedSimulationTime()`.
7. Add all of that batch's EMPTY sheet keys to the authorized EMPTY set immediately.
8. If it has physical manifests, make it the active inbound batch and set the first due time to authorization elapsed time plus the arrival policy's interval for its first manifest.
9. If it has no physical manifests, mark it `SUPPLY_COMPLETE` immediately, but do not authorize another batch until a later `advance(...)` call.

Snapshots must show authorized physical manifests as `AUTHORIZED_WAITING`. Authorization must not change OSR occupancy, lifecycle state, scheduler state, or route state.

Create `DspServiceCentreSupplyAuthorizationTest`.

Required tests:

- `shouldNotAuthorizeWhileOccupancyIsAboveLowWaterMark()`
- `shouldAuthorizeHighestPriorityHeldCentreAtInclusiveLowWaterMark()`
- `shouldAuthorizeAtMostOneCentrePerAdvance()`
- `shouldAuthorizeEmptySheetsWithoutConsumingOsrCapacity()`
- `shouldScheduleFirstPhysicalArrivalOneIntervalAfterAuthorization()`
- `shouldNotAuthorizeAnotherCentreWhileAuthorizedPhysicalSupplyRemains()`
- `shouldRejectClockRegression()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyAuthorizationTest
```

## Step 6: Implement Deterministic Rate-Limited Physical Admission

Extend `advance(...)` to admit due physical manifests from the active batch.

Admission algorithm:

1. Run authorization first only when no batch was active at the start of the advance. A newly authorized batch may schedule its first due time but cannot admit in that same call because one full interval is required.
2. If the current elapsed time is before the next due time, do nothing.
3. At or after the due time, inspect current OSR capacity before each admission.
4. If capacity exists, call `OsrPhysicalInventory.store(nextManifest)` and increment the global and batch post-start admitted counts.
5. When the coordinator was not capacity-blocked, calculate the following due time from the previous scheduled due time. Continue admitting overdue manifests while `elapsed >= nextDue` and capacity remains.
6. When OSR becomes full while a manifest is due, mark only the head manifest `BLOCKED_BY_OSR_CAPACITY`, leave all later manifests `AUTHORIZED_WAITING`, retain the head and due time, and stop.
7. When capacity later becomes available after a block, admit exactly the blocked head manifest at the current elapsed time. Schedule the next manifest from current elapsed time plus its full interval and stop processing admissions for that advance. This prevents a catch-up burst.
8. When the final physical manifest is admitted, mark the batch `SUPPLY_COMPLETE`, clear the active batch and due time, and do not authorize another service centre until a later advance.

Derive admitted manifest state from inventory history:

- currently stored: `STORED_IN_OSR`;
- present in departure history: `DEPARTED_FROM_OSR`;
- never allow a manifest to return to an upstream or waiting state.

Create `DspRateLimitedInboundSupplyTest`.

Required tests:

- `shouldAdmitFirstManifestAtExactConfiguredDueTime()`
- `shouldPreserveConfiguredIntervalsAndAdaptedFirstOrder()`
- `shouldCatchUpDeterministicallyWhenSeveralUnblockedArrivalsAreDue()`
- `shouldStoreEveryRepeatedSheetManifestByPhysicalIdentity()`
- `shouldMarkBatchCompleteAfterItsFinalManifestIsStored()`
- `shouldNeverAdmitPreloadedOrEmptyWorkAgain()`
- `shouldDeriveStoredAndDepartedStatesFromInventory()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspRateLimitedInboundSupplyTest
```

## Step 7: Prove Capacity Blocking And Sequential Replenishment

Add `DspServiceCentreSupplyFlowTest` using at least four service centres:

- two configured preload centres;
- two later centres with descending distinct priorities;
- at least one ADAPTED, one FULL_PACK, one ASSOCIATED, and one EMPTY order in the later supply;
- a deliberately small OSR capacity and explicit low-water mark;
- a fixed test interval.

Prove this exact sequence:

1. later work starts held upstream;
2. departures lower occupancy to the inclusive threshold;
3. only the highest-priority later centre is authorized;
4. its EMPTY sheets authorize immediately;
5. physical manifests arrive at the configured simulated rate;
6. a full OSR blocks the due head manifest without reordering or throwing;
7. a physical departure creates capacity;
8. the blocked head enters first and the following arrival waits a complete interval, with no burst;
9. the next service centre remains held until the active centre's entire upstream physical batch is admitted;
10. once the first later batch is complete and occupancy again satisfies the threshold, a later advance authorizes the next priority centre even though earlier-centre totes may still remain stored in OSR;
11. no inventory capacity or physical-identity invariant is bypassed.

Also prove that repeated calls at the same elapsed time are idempotent once all work due at that time has been handled.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyFlowTest
```

## Step 8: Add The Simulation Controller And Clock Integration Scenario

Create:

```java
public final class DspServiceCentreSupplyController implements SimulationController
```

Constructor inputs:

```text
Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier
DspServiceCentreSupplyCoordinator coordinator
```

Rules:

- reject null inputs and null supplied snapshots;
- `update(SimulationContext, double)` rejects null context, obtains one immutable clock snapshot, and calls `coordinator.advance(snapshot)`;
- ignore `dtSeconds` for supply arithmetic;
- expose `DspSupplySnapshot snapshot()` by delegation;
- document that `DspOperationalClockController` must be registered before this controller.

Create `DspServiceCentreSupplyControllerTest` proving controller order, immutable snapshot delegation, and that repeated/different `dtSeconds` values do not alter absolute-time behavior.

Create `DspRateLimitedServiceCentreSupplyScenarioTest` using mapped/assembled 12N-style data rather than constructing only supply records directly. The scenario must:

- retain priority through JSON mapping and assembly;
- bootstrap preload service centres through `OsrInventoryBootstrapFactory`;
- register `DspOperationalClockController` before the supply controller in `SimulationWorld`;
- create capacity through explicit `OsrPhysicalInventory.recordDeparture(...)` calls representing later OSR release;
- authorize the next service centre at the low-water boundary;
- admit its ADAPTED manifests before mixed FULL_PACK/ASSOCIATED source order;
- authorize EMPTY without changing occupancy;
- block and resume physical supply at capacity;
- prove reconstruction returns to the exact startup preload and held-upstream state.

Required tests:

- `shouldAdvanceSupplyFromAuthoritativeOperationalClockSnapshot()`
- `shouldIgnoreFrameDeltaForSupplyTiming()`
- `shouldRunPriorityOrderedRateLimitedSupplyFromLoadedData()`
- `shouldRestartSupplyDeterministicallyWhenRuntimeIsReconstructed()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyControllerTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspRateLimitedServiceCentreSupplyScenarioTest
```

## Step 9: Regression And Branch Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.time.* --tests online.davisfamily.warehouse.sim.dsp.io.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify existing scene behavior and motion are unchanged because this branch does not install the new supply controller into a visual rig;
- verify `ALT+R` still resets each scene;
- no new supply overlay is expected in this branch.

Before branch closure:

- [ ] update this plan status to implementation complete and verified;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] record final type names or implementation details that later OSR release, scheduler, inspection, and metrics plans must preserve.

## Preserved Contracts For Follow-On Work

- `NotionalToteOrder.orderPriority()` is the retained 12N field used to derive service-centre ordering. It is not an individual scheduler-candidate rank.
- Priority anomalies are explicit supply-plan issues; deterministic ordering never depends on map iteration.
- `DspServiceCentreSupplyPlan` is immutable warehouse input. `DspServiceCentreSupplyCoordinator` owns only live authorization/admission progress.
- Supply authorization, physical inventory admission, physical inventory departure, lifecycle activation, and scheduler release remain separate transitions.
- `OsrPhysicalInventory` remains the authority for capacity, duplicate admission, stored membership, and departure history.
- EMPTY authorization is a logical set and consumes no OSR capacity before AV02.
- Later scheduler snapshots should consume `DspSupplySnapshot` rather than infer upstream authorization from logical order type or service-centre ID.
- A service centre may be supply-complete while some or all of its physical manifests remain stored in OSR or are already in DSP.
- The OSR may hold several authorized service centres even though this first inbound stream supplies one post-start centre batch at a time.
- Rate scheduling is based on absolute simulation elapsed time. The fixed-step size and render frequency do not define the arrival rate.
- Capacity-blocked supply resumes from the blocked physical manifest without reordering and without a catch-up burst.
- Runtime reset reconstructs inventory bootstrap, plan, coordinator, controller, and clock controller.
- Supply snapshots are immutable and may later cross the scheduler-worker boundary; the coordinator and inventory never do.

## Completion Criteria

- 12N order priority survives mapping, filtering, logical-sheet assembly, and loaded-data access.
- Service centres are ordered by descending retained priority with deterministic anomaly reporting.
- Start-of-day preload centres are represented but never re-admitted.
- Later service centres authorize only at the inclusive configured OSR low-water boundary.
- No second later centre authorizes while the current centre still has physical manifests upstream.
- ADAPTED manifests enter before the same service centre's combined FULL_PACK/ASSOCIATED source order.
- EMPTY sheets authorize logically without consuming physical capacity.
- Physical manifests enter individually at a configurable deterministic simulation-time rate.
- Peak 3-second and representative-busy 9-second fixed policies are available.
- Full capacity blocks rather than throws or reorders, and resumed supply does not burst accumulated arrivals.
- Multiple physical manifests for one logical sheet remain distinct.
- Immutable snapshots distinguish preloaded, held, authorized-waiting, capacity-blocked, stored, and departed physical states.
- No lifecycle activation, OSR processing release, scheduler candidate decision, P2P allocation, deadline, rendering, or new thread is introduced.
- Focused tests, complete tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, reassess the roadmap against the operational requirements. The likely next feature is the physical OSR processing-release boundary that connects stored manifest identity to lifecycle activation and scheduler commands. Create its detailed branch plan from updated `master`; do not implement it as an unplanned extension of this branch.
