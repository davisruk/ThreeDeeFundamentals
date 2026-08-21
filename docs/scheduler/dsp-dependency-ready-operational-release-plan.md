# DSP Dependency-Ready Operational Release Plan

Branch: `feature/dsp-dependency-ready-operational-release`

Status: plan ready; implementation not started.

## Purpose

Introduce the first operational scheduler path that consumes immutable physical OSR candidates, joins them to logical order/dependency state, chooses a deterministic dependency-ready candidate, emits `ReleasePhysicalToteFromOsrCommand`, and applies that command through the completed simulation-thread physical release handler.

This branch must:

- keep every stored physical manifest distinct, including several manifests for one `OrderSheetKey`;
- join physical candidates to exactly one logical sheet without using `orderId` as physical identity;
- make ADAPTED and FULL_PACK independently eligible;
- block ASSOCIATED only on its own incomplete ADAPTED dependencies;
- replace the legacy ASSOCIATED/EMPTY-before-FULL_PACK assumption in the operational path;
- rank fully eligible work by service-centre cohort, stable pharmacy grouping, and deterministic physical source order;
- select and gate only the first station at which the tote enters its route;
- emit the typed physical OSR command with the selected route-entry target;
- preserve immutable snapshot evaluation on a scheduler worker and command application on the simulation thread;
- route applied decisions through `OsrProcessingReleaseCommandHandler` without calling `DspSchedulerRuntimeState.markReleased(...)`;
- retain the existing order-centric `DspReleaseScheduler` and visual debug rigs as compatibility paths.

This branch does not implement EMPTY/AV02 physical allocation, sticky P2P service-centre leases, active-P2P-line pharmacy affinity, deadline-aware elastic line allocation, complete production station adapters, renderable creation, Exception behavior, calibrated timings, or a full-day run.

## Required Reading

Read these documents before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
4. `docs/scheduler/dsp-osr-processing-release-plan.md`
5. `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`
6. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `OsrProcessingReleaseCandidate`
- `OsrProcessingReleaseSnapshot`
- `OsrProcessingReleaseSnapshotFactory`
- `ReleasePhysicalToteFromOsrCommand`
- `OsrProcessingReleaseCommandHandler`
- `InboundToteManifest`
- `InboundToteManifestCatalog`
- `DspSchedulerOrderState`
- `WarehouseSchedulerSnapshot`
- `DspDependencyEvaluator`
- `DspReleaseScheduler`
- `StationAdmissionSnapshot`
- `StationAdmissionResolver`
- `RouteRequirements`
- `DspSchedulerRuntimeState`
- `SchedulerEvaluationSource`
- `ThreadedSchedulerEvaluationSource`
- `ScheduledDebugToteInjectorController`

## Fixed Decisions

Do not revisit these decisions during implementation:

- Add a separate operational scheduler under `online.davisfamily.warehouse.sim.dsp.scheduler.operational`. Do not overload or rewrite the legacy `DspReleaseScheduler`, `ReleaseDecision`, `SchedulerEvaluation`, or `WarehouseSchedulerSnapshot` contracts.
- The legacy scheduler and debug injector remain order-centric compatibility fixtures. Their hardcoded order-type comparator may remain only there. The new operational path must not copy or call that comparator.
- Operational candidate identity is `PhysicalToteId`. `OrderSheetKey` is joined logical metadata and is never the command release key.
- Build operational snapshots on the simulation thread from immutable `OsrProcessingReleaseSnapshot`, `InboundToteManifestCatalog`, and `WarehouseSchedulerSnapshot` values. Worker evaluation receives only the resulting immutable operational snapshot.
- One physical candidate must join to exactly one `DspSchedulerOrderState` by exact `OrderSheetKey`. Missing or duplicate logical state is an invariant failure during snapshot construction.
- A joined physical candidate must match its manifest and logical state on physical ID, logical sheet, order type, and service centre. Treat mismatches as invariant failures, not scheduler blocks.
- Candidate manifests may contain a subset of one logical sheet's lines. Every manifest line must match an equal logical line by globally distinct `lineReference`; do not require every logical line to be in every physical manifest.
- Operational physical candidates must have logical status `WAITING` or `BLOCKED`. A stored candidate joined to `RELEASED` or `COMPLETED` indicates incompatible legacy mutation and must fail snapshot construction.
- EMPTY has no inbound physical OSR candidate and remains out of scope until AV02 allocation. Do not synthesize an EMPTY `PhysicalToteId` or command.
- ADAPTED and FULL_PACK have no prepared-line dependency barrier. ASSOCIATED requires every ADAPTED line on its own logical sheet to have a key in the snapshot's prepared-line set. Unrelated ADAPTED work is irrelevant.
- The current prepared-line set represents terminal ready outcomes available in the implemented simulation. A richer successful/incomplete outcome model remains Exception work; do not fabricate packs for missing outcomes here.
- Do not apply the legacy sheet-sequence status rule in the operational path. Repeated inbound manifests are sequenced by physical lifecycle availability; generated outbound sheets are not OSR candidates.
- Evaluate station admission only for the first route-entry station. Downstream stations gate their own later handoffs and must not prevent a tote entering available upstream waiting space.
- First route-entry precedence is: `THIRD_PARTY`, then `ADAPTING` for `requiresSortable`, then `MANUAL`, then `P2P`, then `MANUAL_MERGE`. MANUAL paths remain absent from retained loaded data, but the selector must be total and deterministic.
- A route-entry station must have an admission snapshot that can accept and a nonblank selected target ID. Missing admission, closed/full admission, or missing target ID blocks the candidate without mutation.
- The selected route-entry target ID becomes `ReleasePhysicalToteFromOsrCommand.releaseTargetId`. Do not infer or reserve later route targets in this branch.
- Evaluate dependency and route-entry eligibility before ranking. A blocked candidate does not prevent another fully eligible candidate from being selected.
- Treat service-centre priority as an outer cohort choice, never as individual tote priority. Among service centres with at least one fully eligible candidate, choose the highest retained `orderPriority`; tie by normalized service-centre ID.
- Therefore, a higher-priority service centre containing only blocked candidates does not prevent dependency-ready work for a lower-priority supplied service centre. Sticky line ownership will later constrain which P2P line may accept that work.
- Validate that all joined logical orders for one service centre carry one consistent priority.
- Derive stable pharmacy groups per service centre from the complete inbound manifest catalog, not only currently stored candidates. Encounter manifests by source sequence, then original catalog order, then physical ID; encounter each manifest's items in line order.
- Retain every distinct pharmacy carried by a candidate. FULL_PACK and ASSOCIATED candidates must be pharmacy-pure. ADAPTED may carry several pharmacies.
- A candidate's grouping rank is the earliest configured group among all pharmacies it carries. This assigns a multi-pharmacy ADAPTED tote once without duplicating it across groups.
- Within the selected service-centre cohort, rank by pharmacy-group index, physical source sequence, logical sheet number, logical order ID, then physical tote ID. Do not add an order-type priority.
- Active P2P-line pharmacy affinity is deliberately deferred until sticky line leases provide authoritative line/pharmacy state. Do not add mutable global "current pharmacy" state as a substitute.
- The operational scheduler is pure. It returns an immutable evaluation containing at most one decision plus observable blocks for candidates it skipped.
- Add operational synchronous and threaded evaluation sources rather than generically refactoring the proven legacy source hierarchy in this branch.
- The threaded operational source uses one named platform-thread executor, one evaluation in flight, immutable values, monotonic result sequence numbers, and no simulation mutation.
- A simulation-thread operational release controller submits fresh snapshots, polls completed evaluations, and applies emitted physical commands through `OsrProcessingReleaseCommandHandler` exactly once.
- The controller never calls `DspSchedulerRuntimeState.markReleased(...)`. Inventory departure makes the physical candidate disappear; lifecycle state blocks repeated-sheet manifests until the active assignment terminates.
- Deferred, rejected, or stale command application is recorded and may be reconsidered from a newly built snapshot. It must not mutate logical scheduler status.
- Reset remains full reconstruction of inventory, lifecycle, operational snapshot supplier, evaluator/source, target registry, command handler, and controller.
- The user runs Gradle. After each implementation step, ask for the stated focused command and wait for feedback.

## Package And Vocabulary

Create operational scheduler domain types under:

```text
online.davisfamily.warehouse.sim.dsp.scheduler.operational
```

Create operational evaluation-source/controller types under:

```text
online.davisfamily.warehouse.sim.dsp.runtime.operational
```

Use these names:

- `ServiceCentrePharmacyGroup`
- `DspOperationalReleaseCandidate`
- `DspOperationalReleaseSnapshot`
- `DspOperationalReleaseSnapshotFactory`
- `OperationalDependencyReadinessPolicy`
- `OperationalRouteEntry`
- `OperationalRouteEntrySelector`
- `OperationalStationAdmissionResolver`
- `SnapshotOperationalStationAdmissionResolver`
- `OperationalRouteEntryEvaluation`
- `OperationalRouteEntryAdmissionPolicy`
- `OperationalReleaseSelection`
- `OperationalCandidateRankingPolicy`
- `PharmacyGroupedSourceSequenceRankingPolicy`
- `OperationalReleaseBlockType`
- `OperationalReleaseBlock`
- `OperationalBlockedCandidate`
- `DspOperationalReleaseDecision`
- `DspOperationalReleaseEvaluation`
- `DspOperationalReleaseScheduler`
- `OperationalReleaseEvaluationSource`
- `OperationalReleaseEvaluationResult`
- `SynchronousOperationalReleaseEvaluationSource`
- `ThreadedOperationalReleaseEvaluationSource`
- `DspOperationalReleaseController`
- `DspOperationalReleaseControllerSnapshot`

Do not create another inventory, lifecycle ledger, logical runtime state, physical command, or downstream target registry.

## Step 1: Define The Joined Operational Snapshot Domain

Create immutable records equivalent to:

```java
public record ServiceCentrePharmacyGroup(
        String serviceCentreId,
        String pharmacyId,
        int groupIndex,
        long firstSourceSequenceNumber) {}

public record DspOperationalReleaseCandidate(
        OsrProcessingReleaseCandidate physicalCandidate,
        DspSchedulerOrderState logicalOrderState,
        List<String> pharmacyIds) {}

public record DspOperationalReleaseSnapshot(
        List<DspOperationalReleaseCandidate> candidates,
        List<ServiceCentrePharmacyGroup> pharmacyGroups,
        Map<StationType, StationAdmissionSnapshot> stationAdmissions,
        Set<PreparedLineKey> preparedLineKeys) {}
```

Validation and API rules:

- reject null collections, records, keys, values, and collection elements;
- trim and require nonblank service-centre/pharmacy IDs;
- require nonnegative group index and source sequence;
- require group indices to be unique and contiguous from zero within each service centre;
- require one group per service-centre/pharmacy pair;
- require candidate physical/logical sheet, order type, and service-centre identity to agree;
- require candidate `RouteRequirements.startLocation()` to be `OSR`;
- require distinct, ordered, nonblank candidate pharmacy IDs;
- require exactly one pharmacy for FULL_PACK and ASSOCIATED; permit several for ADAPTED;
- reject duplicate candidate physical IDs;
- require every candidate pharmacy to have a configured group for its service centre;
- preserve candidate, group, and pharmacy order with defensive immutable copies;
- expose lookup by `PhysicalToteId`, groups for service centre, and group index for a candidate;
- do not expose mutable maps or recompute grouping from current candidates.

Create `DspOperationalReleaseSnapshotTest` with:

- `shouldRetainDistinctPhysicalCandidatesForOneLogicalSheet()`
- `shouldPreserveMultiPharmacyAdaptedCandidateWithoutDuplication()`
- `shouldRequirePharmacyPureFulfilmentCandidates()`
- `shouldRequireContiguousServiceCentrePharmacyGroups()`
- `shouldRejectCandidateWithoutConfiguredPharmacyGroup()`
- `shouldReturnDefensiveImmutableCollections()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotTest
```

## Step 2: Join Physical Candidates To Logical State And Stable Pharmacy Groups

Create:

```java
public final class DspOperationalReleaseSnapshotFactory {
    public DspOperationalReleaseSnapshot create(
            OsrProcessingReleaseSnapshot physicalSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            WarehouseSchedulerSnapshot logicalSnapshot)
}
```

Factory algorithm, in this exact order:

1. Reject null inputs.
2. Index `logicalSnapshot.orderStates()` by exact `OrderSheetKey`; reject duplicate keys.
3. Validate consistent `orderPriority` for every service centre represented by those order states.
4. Build service-centre pharmacy groups from every catalog manifest, including departed/upstream manifests, using the fixed encounter order. Do not derive groups only from current OSR occupancy.
5. Iterate `physicalSnapshot.candidates()` in existing inventory order.
6. Require the exact physical manifest in the catalog.
7. Require exactly one logical order state for the candidate's sheet.
8. Require candidate, manifest, and logical state to agree on physical/logical identity, order type, and service centre.
9. Require logical status `WAITING` or `BLOCKED`.
10. Match every manifest item to an equal logical item by `lineReference`; reject missing or contradictory line data.
11. Derive ordered distinct candidate pharmacy IDs from manifest item order.
12. Construct one joined candidate without changing physical availability.
13. Copy station admissions and prepared-line keys from the logical snapshot.

Ignore `logicalSnapshot.activeServiceCentreId()` because it belongs to the legacy global-window scheduler. Do not mutate either source snapshot or the catalog.

Create `DspOperationalReleaseSnapshotFactoryTest` with:

- `shouldJoinPhysicalManifestToExactLogicalSheet()`
- `shouldKeepSeveralPhysicalManifestsForOneLogicalSheet()`
- `shouldDeriveStablePharmacyGroupsFromCompleteCatalog()`
- `shouldRetainAllPharmaciesForAdaptedManifest()`
- `shouldRejectMissingOrDuplicateLogicalSheetState()`
- `shouldRejectManifestLogicalIdentityOrLineMismatch()`
- `shouldRejectReleasedOrCompletedLogicalStateForStoredCandidate()`
- `shouldRejectInconsistentServiceCentrePriority()`
- `shouldIgnoreLegacyActiveServiceCentreWindow()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest
```

## Step 3: Implement Dependency-Ready Operational Eligibility

Create `OperationalDependencyReadinessPolicy` with:

```java
public List<OperationalReleaseBlock> findBlocks(
        DspOperationalReleaseCandidate candidate,
        DspOperationalReleaseSnapshot snapshot)
```

Add `OperationalReleaseBlockType` values needed in this branch:

```text
ACTIVE_SHEET_ASSIGNMENT
ADAPTED_DEPENDENCY
ROUTE_ENTRY
STATION_ADMISSION
TARGET_SELECTION
```

`OperationalReleaseBlock` must retain block type and a nonblank reason. Physical identity belongs to the containing `OperationalBlockedCandidate`, not a free-form reason alone.

Dependency algorithm:

1. Convert `BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT` into an `ACTIVE_SHEET_ASSIGNMENT` block naming the blocking physical tote.
2. For ADAPTED and FULL_PACK, add no prepared-line dependency block.
3. For ASSOCIATED, inspect only logical items with line type `ADAPTED`.
4. For each such item, require `PreparedLineKey.forDispatchLine(logicalOrder, line)` in `snapshot.preparedLineKeys()`.
5. Add deterministic blocks in logical item order for missing keys.
6. Reject EMPTY defensively as an invariant violation because it cannot have a physical candidate.
7. Do not call the legacy `DspDependencyEvaluator`; its sheet-status and manual compatibility rules are not the operational physical policy.

Create `OperationalDependencyReadinessPolicyTest` with:

- `shouldAllowAdaptedAndFullPackWithoutGlobalPreparationBarrier()`
- `shouldBlockAssociatedOnlyForItsOwnMissingAdaptedLines()`
- `shouldAllowAssociatedWhenEveryOwnDependencyIsReady()`
- `shouldIgnorePreparedLinesForUnrelatedOrders()`
- `shouldExposeActivePhysicalSheetBlocker()`
- `shouldPreserveDeterministicDependencyBlockOrder()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicyTest
```

## Step 4: Select And Gate The First Route-Entry Target

Create:

```java
public record OperationalRouteEntry(
        StationType stationType,
        String targetId) {}

public final class OperationalRouteEntrySelector {
    public Optional<StationType> firstStation(RouteRequirements routeRequirements)
}

public interface OperationalStationAdmissionResolver {
    StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot);
}
```

Add `SnapshotOperationalStationAdmissionResolver` as the default map-backed implementation.

Create:

```java
public record OperationalRouteEntryEvaluation(
        Optional<OperationalRouteEntry> routeEntry,
        List<OperationalReleaseBlock> blocks) {}

public final class OperationalRouteEntryAdmissionPolicy {
    public OperationalRouteEntryEvaluation evaluate(
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot)
}
```

The policy owns the route selector and operational admission resolver as non-null constructor dependencies. Its algorithm is:

1. obtains the first route station using the fixed precedence;
2. blocks with `ROUTE_ENTRY` when no entry station exists;
3. resolves candidate-specific admission for that one station only;
4. blocks with `STATION_ADMISSION` when admission is missing, closed, or capacity cannot accept;
5. uses the admission's explicit blocked reason when available;
6. blocks with `TARGET_SELECTION` when accepting admission has no selected target ID;
7. publishes `OperationalRouteEntry` only when all entry checks pass;
8. never checks later stations in the route.

Create `OperationalRouteEntrySelectorTest` and `OperationalStationAdmissionResolverTest` with:

- `shouldSelectThirdPartyBeforeAdaptingAndP2p()`
- `shouldSelectAdaptingBeforeP2p()`
- `shouldSelectP2pForDirectFullPack()`
- `shouldBlockRouteWithoutEntryStation()`
- `shouldCheckOnlyFirstRouteStationAdmission()`
- `shouldBlockMissingClosedOrFullEntryAdmission()`
- `shouldRequireExplicitSelectedEntryTarget()`
- `shouldSupportCandidateAwareAdmissionResolver()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntrySelectorTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalStationAdmissionResolverTest
```

## Step 5: Implement Pharmacy-Grouped Deterministic Ranking

Create:

```java
public record OperationalReleaseSelection(
        DspOperationalReleaseCandidate candidate,
        OperationalRouteEntry routeEntry) {}

public interface OperationalCandidateRankingPolicy {
    List<OperationalReleaseSelection> rank(
            List<OperationalReleaseSelection> eligibleCandidates,
            DspOperationalReleaseSnapshot snapshot);
}

public final class PharmacyGroupedSourceSequenceRankingPolicy
        implements OperationalCandidateRankingPolicy { ... }
```

Ranking algorithm:

1. Reject null inputs, null elements, duplicate physical IDs, and selections whose candidate is absent from the snapshot.
2. If empty, return an immutable empty list.
3. Group selections by service centre.
4. Retain only the service-centre cohort with the highest consistent `orderPriority` among fully eligible selections; tie by service-centre ID.
5. For each candidate, find the minimum configured pharmacy-group index among all its pharmacy IDs.
6. Sort the retained cohort by that group index.
7. Within the group, sort by physical source sequence, logical sheet number, logical order ID, then physical tote ID.
8. Return an immutable ordered list.

Do not compare `OrderType`, patient, prescription, `departureTime`, or target ID. Do not carry ranking state between evaluations.

Create `PharmacyGroupedSourceSequenceRankingPolicyTest` with:

- `shouldChooseHighestPriorityEligibleServiceCentreCohort()`
- `shouldResolveServiceCentrePriorityTieDeterministically()`
- `shouldKeepPharmacyGroupTogetherBeforeLaterGroup()`
- `shouldUseStablePhysicalSourceOrderWithinPharmacy()`
- `shouldNotPrioritizeAssociatedOverFullPack()`
- `shouldRankMultiPharmacyAdaptedCandidateOnceAtEarliestGroup()`
- `shouldReturnDefensiveImmutableRanking()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.PharmacyGroupedSourceSequenceRankingPolicyTest
```

## Step 6: Implement The Pure Operational Release Scheduler

Create:

```java
public record OperationalBlockedCandidate(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        List<OperationalReleaseBlock> blocks) {}

public record DspOperationalReleaseDecision(
        DspOperationalReleaseCandidate candidate,
        OperationalRouteEntry routeEntry,
        ReleasePhysicalToteFromOsrCommand command) {}

public record DspOperationalReleaseEvaluation(
        Optional<DspOperationalReleaseDecision> releaseDecision,
        List<OperationalBlockedCandidate> blockedCandidates) {}

public final class DspOperationalReleaseScheduler {
    public DspOperationalReleaseEvaluation evaluate(
            DspOperationalReleaseSnapshot snapshot)
}
```

Constructor dependencies are the Step 3 dependency policy, Step 4 route-entry/admission collaborators, and Step 5 ranking policy. Provide a convenience constructor using the default snapshot admission resolver and pharmacy ranking policy.

Evaluation algorithm:

1. Reject null snapshot.
2. Iterate every joined candidate once in snapshot order.
3. Collect physical/lifecycle and prepared-dependency blocks.
4. If dependency-ready, evaluate first route-entry admission and target selection.
5. Publish every blocked candidate with typed reasons; do not hide blocked repeated-sheet manifests.
6. Pass only fully eligible candidate/entry selections to the ranking policy.
7. If none are eligible, return no decision plus all blocks.
8. Select the first ranked result.
9. Create one `ReleasePhysicalToteFromOsrCommand` from exact physical ID, logical sheet, service centre, and route-entry target.
10. Return one decision plus any blocks collected for skipped candidates.
11. Perform no inventory, lifecycle, station, logical-status, or target mutation.

Create `DspOperationalReleaseSchedulerTest` with:

- `shouldEmitPhysicalCommandForDependencyReadyCandidate()`
- `shouldEmitExactSelectedRouteEntryTarget()`
- `shouldReleaseFullPackWhileAssociatedDependencyIsBlocked()`
- `shouldKeepBlockedRepeatedSheetCandidateObservable()`
- `shouldChooseLowerPriorityCentreWhenHigherCentreHasNoEligibleCandidate()`
- `shouldNotUseLegacyOrderTypePriority()`
- `shouldReturnNothingWhenSnapshotHasNoCandidates()`
- `shouldReturnTypedBlocksWhenEveryCandidateIsBlocked()`
- `shouldEvaluateWithoutMutatingSnapshotState()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest
```

## Step 7: Preserve The Worker Boundary For Operational Evaluation

Create operational equivalents of the proven evaluation-source boundary:

```java
public record OperationalReleaseEvaluationResult(
        long sequence,
        DspOperationalReleaseSnapshot snapshot,
        DspOperationalReleaseEvaluation evaluation) {}

public interface OperationalReleaseEvaluationSource {
    boolean canSubmit();
    void submit(DspOperationalReleaseSnapshot snapshot);
    Optional<OperationalReleaseEvaluationResult> pollResult();
    void close();
    default String modeLabel();
    default boolean evaluationInFlight();
}
```

Implement `SynchronousOperationalReleaseEvaluationSource` and `ThreadedOperationalReleaseEvaluationSource` by following existing source semantics exactly:

- reject null scheduler/snapshots and blank worker names;
- one pending/in-flight evaluation at a time;
- monotonic sequence starting at zero;
- named daemon platform thread, not a virtual thread;
- immutable submitted snapshot returned in the result;
- worker failures rethrown by `pollResult()`;
- `close()` stops threaded submission and is a no-op for synchronous mode;
- no command application or mutable collaborator in either source.

Do not refactor the legacy source hierarchy in this step.

Create:

- `SynchronousOperationalReleaseEvaluationSourceTest`
- `ThreadedOperationalReleaseEvaluationSourceTest`

Required methods:

- `shouldEvaluateOperationalSnapshotSynchronously()`
- `shouldPreserveSnapshotAndMonotonicSequence()`
- `shouldRejectSubmissionWhileResultIsPending()`
- `shouldEvaluateOnNamedPlatformWorker()`
- `shouldKeepOneOperationalEvaluationInFlight()`
- `shouldRethrowOperationalWorkerFailure()`
- `shouldRejectThreadedSubmissionAfterClose()`

Use latches for worker coordination. Do not assert scheduler behavior after arbitrary simulation update counts.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.*OperationalReleaseEvaluationSourceTest
```

## Step 8: Apply Operational Decisions On The Simulation Thread

Create `DspOperationalReleaseController implements SimulationController, AutoCloseable` with constructor dependencies:

```java
public DspOperationalReleaseController(
        OperationalReleaseEvaluationSource evaluationSource,
        Supplier<DspOperationalReleaseSnapshot> snapshotSupplier,
        SchedulerCommandHandler commandHandler)
```

Create `DspOperationalReleaseControllerSnapshot` containing immutable inspection state equivalent to:

- evaluation mode;
- evaluation-in-flight flag;
- optional last completed sequence;
- optional last operational evaluation;
- optional last command application result;
- optional last physical tote ID.

Controller update algorithm:

1. Reject null construction dependencies and null supplier results.
2. Record evaluation-source mode/in-flight state.
3. If the source can submit, obtain one fresh operational snapshot and submit it.
4. Poll at most one completed result.
5. If none is ready, return without mutation.
6. Record the completed immutable evaluation and sequence.
7. If it has no release decision, return.
8. Apply the decision's `ReleasePhysicalToteFromOsrCommand` exactly once through the supplied handler on the calling simulation thread.
9. Record the application result unchanged.
10. Never call `DspSchedulerRuntimeState.markReleased(...)` or modify prepared keys/station snapshots.
11. On the next submission, rely on a newly assembled live snapshot; do not locally remove or suppress candidates.
12. `close()` delegates to the evaluation source.

The controller may record rejected/deferred outcomes without throwing. Broken collaborator exceptions from the evaluation source, snapshot supplier, or command handler still propagate.

Create `DspOperationalReleaseControllerTest` with:

- `shouldSubmitImmutableOperationalSnapshotAndApplyPhysicalCommand()`
- `shouldApplyCommandOnCallingSimulationThread()`
- `shouldNotApplyWhenEvaluationHasNoDecision()`
- `shouldRecordDeferredOrRejectedApplicationWithoutLogicalMutation()`
- `shouldRebuildSnapshotAfterAppliedPhysicalDeparture()`
- `shouldNotMarkLegacyOrderReleased()`
- `shouldPropagateBrokenCollaboratorFailure()`
- `shouldCloseOperationalEvaluationSource()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerTest
```

## Step 9: Prove Dependency-Ready Physical Release End To End

Create `DspDependencyReadyOperationalReleaseScenarioTest` using real:

- `OsrPhysicalInventory` and `OsrProcessingReleaseSnapshotFactory`;
- `InboundToteManifestCatalog`, lifecycle ledger, and controller;
- `DspSchedulerRuntimeState` only as the source of logical/prepared/station snapshots;
- `DspOperationalReleaseSnapshotFactory`;
- `DspOperationalReleaseScheduler`;
- synchronous operational evaluation source and controller;
- `OsrProcessingReleaseCommandHandler`;
- operational clock snapshots;
- deterministic in-memory route-entry targets.

Prove these scenarios without sleeps or visual objects:

1. ADAPTED and FULL_PACK from one supplied service centre are independently eligible; source/pharmacy ranking, not order type, selects them.
2. ASSOCIATED remains stored while its own ADAPTED key is missing, then becomes eligible after that key is added to a newly built logical snapshot.
3. Unrelated missing preparation does not block ready fulfilment work.
4. Two physical manifests for one sheet release sequentially through lifecycle assignment termination without logical `markReleased(...)`.
5. Stable pharmacy groups keep ready work together according to complete-catalog group order.
6. A fully blocked higher-priority service centre permits eligible lower-priority work; a ready higher-priority cohort wins when both are eligible.
7. Route-entry target deferral leaves the tote stored and allows a later fresh evaluation/retry.
8. A stale worker result is rejected by the live handler without another downstream call.
9. Reconstructing all mutable/runtime collaborators restores exact startup candidates and clears departure, assignment, evaluation, and application history.

Required methods:

- `shouldReleaseAdaptedAndFullPackWithoutOrderTypePriority()`
- `shouldReleaseAssociatedOnlyAfterOwnDependencyBecomesReady()`
- `shouldSequenceRepeatedPhysicalManifestsWithoutLogicalReleaseMutation()`
- `shouldPreserveStablePharmacyGroupingAcrossPhysicalReleases()`
- `shouldSelectHighestPriorityServiceCentreWithEligibleWork()`
- `shouldRetryAfterRouteEntryTargetDefers()`
- `shouldRejectStaleWorkerDecisionWithoutDuplicateTargetMutation()`
- `shouldReconstructExactOperationalReleaseStartupState()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspDependencyReadyOperationalReleaseScenarioTest
```

## Step 10: Regression, Visual Verification, And Branch Closure

Run focused feature coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify legacy visual release behavior is unchanged because those rigs still use `DspReleaseScheduler` and `ReleaseOrderCommand`;
- verify `ALT+R` still reconstructs each checked scene;
- no operational release renderable, AV02 tote, sticky P2P allocation, or new overlay is expected in this branch.

Before branch closure:

- [ ] update this plan status to implementation complete and verified;
- [ ] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [ ] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [ ] record final operational snapshot, ranking, command, and controller contracts;
- [ ] reassess whether the next branch is `feature/dsp-p2p-sticky-service-centre-leases` or an intervening route-target integration slice.

## Preserved Contracts For Follow-On Work

- `DspOperationalReleaseSnapshot` is the immutable worker input for physical release policy. It does not replace inventory, lifecycle, or logical runtime ownership.
- `DspOperationalReleaseCandidate` keeps physical identity primary and retains exact logical, route, priority, and pharmacy metadata.
- Pharmacy grouping is deterministic from complete catalog data. It has no mutable global active-pharmacy state.
- Active-line pharmacy affinity must later come from authoritative P2P line/lease snapshots and compose with, rather than replace, static group order.
- Operational eligibility checks only the first route-entry station. Machine-local and downstream handoffs remain separate admission boundaries.
- `DspOperationalReleaseScheduler` emits `ReleasePhysicalToteFromOsrCommand`; only the simulation-thread controller applies it.
- `OsrProcessingReleaseCommandHandler` remains the sole owner of downstream acceptance followed by `recordDeparture(...)` then lifecycle `activate(...)`.
- The operational path never marks one logical order released merely because one physical manifest departed.
- The legacy scheduler, command, evaluation sources, runtime state mutation, debug injector, and visual rigs remain compatibility paths until deliberately migrated.
- EMPTY remains logical and absent from OSR physical candidates until AV02 allocation is implemented.
- Sticky P2P service-centre isolation remains mandatory before operational release is wired broadly to all five production-style P2P instances.

## Completion Criteria

- Every stored physical candidate joins to exactly one matching logical sheet and manifest.
- Several physical manifests for one sheet remain distinct and sequence through lifecycle availability.
- ADAPTED and FULL_PACK have no unrelated preparation barrier.
- ASSOCIATED eligibility depends only on its own ADAPTED line keys.
- Operational ranking has no ASSOCIATED/EMPTY-before-FULL_PACK priority.
- Ranking selects the highest-priority service-centre cohort containing eligible work, then stable pharmacy group and physical source order.
- Multi-pharmacy ADAPTED candidates appear and rank exactly once.
- Only the first route-entry station gates OSR departure, and its explicit target ID is retained in the command.
- Worker evaluation is immutable and mutation-free.
- Physical command application occurs on the simulation thread through the completed handler.
- Applied physical departure does not call legacy logical `markReleased(...)`.
- Deferred, rejected, and stale decisions preserve inventory/lifecycle invariants and can be reevaluated from fresh snapshots.
- Legacy scheduler/runtime/debug tests, complete tests, visual scenes, and reset checks remain green.

## Follow-On Branch

After this branch is green and merged, reassess whether the next feature should be:

```text
feature/dsp-p2p-sticky-service-centre-leases
```

That feature must publish authoritative per-line lease, quiescence, and open-output-tote state; prevent cross-service-centre admission; add active-line pharmacy affinity to ranking/allocation; and preserve selected P2P identity across downstream routing. If no production route-entry targets can yet consume `OsrProcessingReleaseRequest`, create a small route-target integration branch before lease work rather than hiding adapters inside the lease policy.
