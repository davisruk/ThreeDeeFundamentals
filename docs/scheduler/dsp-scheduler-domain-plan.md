# DSP Scheduler Domain Plan

Status: complete and merged. Historical note: the original notional-tote-level adapted/manual readiness fields in this plan were superseded by `docs/scheduler/dsp-scheduler-line-readiness-plan.md`, which replaced them with `PreparedLineKey` line-level readiness in `WarehouseSchedulerSnapshot`.

## Summary

This is the detailed implementation plan for `feature/dsp-scheduler-domain`.

Status: complete and green.

The branch establishes the pure domain scheduler foundation without visuals, renderables, live P2P integration, real JSON imports, or a separate scheduler thread. It introduces the order model, route derivation, scheduler snapshots, dependency checks, service-centre windowing, release decisions, scheduler commands, and a snapshot-based P2P admission contract.

Branch strategy used:

```powershell
git switch master
git switch -c feature/dsp-scheduler-domain
```

## Key Decisions

- Package root: `online.davisfamily.warehouse.sim.dsp`.
- V1 data source: in-memory fixtures only.
- V1 service-centre rule: service centres are processed as whole release windows.
- V1 blocked service-centre rule: if the active service centre is blocked, release nothing from later service centres until the block clears.
- V1 P2P integration: model as a station capacity/admission concept only; do not call live `ToteToBagFlowController.canAdmit(...)` until a later integration branch.
- V1 JSON loading: out of scope until sample product master and 12N schemas are supplied.
- V1 threading: run synchronously in tests, but use immutable input snapshots and explicit output commands so the scheduler can later move to a separate thread.
- Scheduler boundary: the scheduler must not directly mutate machine/controller state, create renderables, inject totes, or call live mutable controller APIs.

## Thread-Ready Boundary

The scheduler is designed around this boundary:

```text
WarehouseSchedulerSnapshot -> DspReleaseScheduler -> SchedulerCommand / BlockedDecision
```

Rules:

- Machine/controllers own live mutable simulation state on the simulation thread.
- The scheduler reads immutable snapshots only.
- The scheduler emits commands/decisions only.
- The simulation thread applies commands at safe points.
- V1 may build snapshots directly in tests, but production integration should later gather snapshots from controllers before invoking the scheduler.
- Availability exposed to the scheduler should be snapshot data, not direct live methods such as `machine.isAvailable()` where that would read mutable controller state.

## Step 1: Domain Enums And Value Objects

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderModelTest.java`

Create exactly:

- `OrderType.java`: enum values `ADAPTED`, `EMPTY`, `ASSOCIATED`, `FULL_PACK`
- `ToteType.java`: enum values `ASSOCIATED`, `FULL_PACK`, `MANUAL_FLOW`
- `ProductCategory.java`: enum values `AUTOMATED`, `SORTABLE`, `MANUAL`
- `StartLocation.java`: enum values `OSR`, `AV02`
- `StationType.java`: enum values `OSR`, `AV02`, `THIRD_PARTY`, `ADAPTING`, `MANUAL`, `P2P`, `MANUAL_MERGE`, `DISPATCH`
- `DependencyType.java`: enum values `ADAPTED_COMPLETION`, `SHEET_SEQUENCE`, `SERVICE_CENTRE_ORDER`, `MANUAL_READY`
- `ProductMasterRecord.java`: `public record ProductMasterRecord(String productId, ProductCategory category, boolean thirdParty)`
- `DspOrderItem.java`: `public record DspOrderItem(String itemId, String productId, int quantity)`
- `NotionalToteOrder.java`: `public record NotionalToteOrder(String orderId, String notionalToteId, String serviceCentreId, int sheetNumber, OrderType orderType, List<DspOrderItem> items, long sequenceNumber)`

Validation:

- IDs must be non-null and non-blank.
- Item lists must be non-null and non-empty.
- `quantity` must be positive.
- `sheetNumber` must be `>= 1`.
- `sequenceNumber` must be `>= 0`.
- Record constructors must use `List.copyOf(items)` for defensive copying.
- Do not introduce Lombok.
- Do not add dependencies.

Test methods:

- `shouldRejectBlankIdentifiers()`
- `shouldRejectNullCategoryAndOrderType()`
- `shouldRejectEmptyOrderItems()`
- `shouldRejectInvalidQuantitySheetAndSequence()`
- `shouldDefensivelyCopyOrderItems()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderModelTest
```

## Step 2: Route Derivation

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/routing/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/routing/DspRouteDeriverTest.java`

Create exactly:

- `ProductMasterRepository.java`
  - `Optional<ProductMasterRecord> findByProductId(String productId)`
- `InMemoryProductMasterRepository.java`
  - constructor: `public InMemoryProductMasterRepository(List<ProductMasterRecord> products)`
  - store records by `productId` in insertion order
  - copy inputs defensively
- `RouteRequirements.java`
  - `public record RouteRequirements(boolean requiresThirdParty, boolean requiresSortable, boolean requiresManual, boolean requiresP2p, boolean requiresManualMerge, StartLocation startLocation)`
- `DspRouteDeriver.java`
  - constructor: `public DspRouteDeriver(ProductMasterRepository productMasterRepository)`
  - method: `public RouteRequirements derive(NotionalToteOrder order)`

Rules:

- `EMPTY` starts at `AV02`; all other order types start at `OSR`.
- `ASSOCIATED` and `EMPTY` require `P2P`.
- `FULL_PACK` does not require `P2P`.
- Third-party product sets `requiresThirdParty`.
- `SORTABLE` product sets `requiresSortable`.
- `MANUAL` product sets `requiresManual`.
- Manual items on `ASSOCIATED`/`EMPTY` set `requiresManualMerge`.
- Missing product master data throws `IllegalArgumentException`.
- `ADAPTED` may require third-party/sortable/manual stations according to item data, but does not require P2P.
- Do not create station path lists yet; only return the booleans in `RouteRequirements`.

Test methods:

- `shouldStartEmptyOrdersAtAv02AndOthersAtOsr()`
- `shouldRequireP2pForAssociatedAndEmptyOrdersOnly()`
- `shouldDeriveThirdPartySortableAndManualRequirementsFromProductMaster()`
- `shouldRequireManualMergeForAssociatedOrEmptyOrdersWithManualItems()`
- `shouldRejectMissingProductMasterData()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriverTest
```

## Step 3: Scheduler State And Capacity Model

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerStateTest.java`

Create exactly:

- `StationCapacity.java`
  - `public record StationCapacity(int maxInProgress, int queueLimit)`
  - method: `public boolean canAccept(StationSnapshot snapshot)`
- `StationSnapshot.java`
  - `public record StationSnapshot(StationType stationType, int inProgress, int queued)`
- `StationAdmissionSnapshot.java`
  - `public record StationAdmissionSnapshot(StationType stationType, StationCapacity capacity, StationSnapshot snapshot, boolean admissionOpen, String blockedReason)`
  - method: `public boolean canAccept()`
- `DspOrderStatus.java`
  - enum values `WAITING`, `RELEASED`, `COMPLETED`, `BLOCKED`
- `DspSchedulerOrderState.java`
  - `public record DspSchedulerOrderState(NotionalToteOrder order, RouteRequirements routeRequirements, DspOrderStatus status)`
  - method: `public DspSchedulerOrderState withStatus(DspOrderStatus status)`
- `WarehouseSchedulerSnapshot.java`
  - `public record WarehouseSchedulerSnapshot(List<DspSchedulerOrderState> orderStates, Map<StationType, StationAdmissionSnapshot> stationAdmissions, Set<String> completedAdaptedNotionalToteIds, Set<String> manualReadyNotionalToteIds, Optional<String> activeServiceCentreId)`

Rules:

- A station accepts if `inProgress < maxInProgress` or `queued < queueLimit`.
- If both processing and queue are full, upstream release is blocked.
- Invalid negative capacities or counts throw.
- `StationAdmissionSnapshot.canAccept()` returns false when `admissionOpen` is false, even if capacity exists.
- `blockedReason` may be blank only when `admissionOpen` is true.
- `WarehouseSchedulerSnapshot` must defensively copy all collections using `List.copyOf`, `Map.copyOf`, and `Set.copyOf`.
- Do not store references to controllers, renderables, simulation objects, or mutable command queues in scheduler snapshots.
- Do not add thread classes in this step.

Test methods:

- `shouldAcceptWhenProcessingOrQueueCapacityExists()`
- `shouldRejectWhenProcessingAndQueueAreFull()`
- `shouldRejectWhenStationAdmissionIsClosed()`
- `shouldRejectInvalidCapacityAndSnapshotCounts()`
- `shouldDefensivelyCopySchedulerSnapshotCollections()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerStateTest
```

## Step 4: Dependency Evaluation

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspDependencyEvaluatorTest.java`

Create exactly:

- `DependencyBlock.java`
  - `public record DependencyBlock(DependencyType type, String reason)`
- `DspDependencyEvaluator.java`
  - constructor: no args
  - method: `public List<DependencyBlock> findBlocks(DspSchedulerOrderState candidate, WarehouseSchedulerSnapshot snapshot)`

Rules:

- `ASSOCIATED` and `EMPTY` are blocked by `ADAPTED_COMPLETION` until their `notionalToteId` is in completed adapted IDs.
- Sheet `n > 1` is blocked by `SHEET_SEQUENCE` until sheet `n - 1` for the same notional tote is completed or released according to scheduler state.
- Orders requiring manual merge are blocked by `MANUAL_READY` until their `notionalToteId` is manual-ready.
- `ADAPTED` and `FULL_PACK` do not require adapted completion.
- Return an empty list when unblocked.
- Sheet sequence check should look at orders with the same `notionalToteId` and `sheetNumber == candidate.order().sheetNumber() - 1`.
- Treat previous sheet as satisfied when that previous sheet state is `RELEASED` or `COMPLETED`.
- Do not mutate the snapshot or candidate.

Test methods:

- `shouldBlockAssociatedAndEmptyUntilAdaptedComplete()`
- `shouldNotBlockAdaptedOrFullPackOnAdaptedCompletion()`
- `shouldBlockLaterSheetUntilPreviousSheetReleasedOrCompleted()`
- `shouldBlockManualMergeUntilManualReady()`
- `shouldReturnAllApplicableDependencyBlocks()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 5: Service-Centre Window Policy

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/ServiceCentreWindowPolicyTest.java`

Create exactly:

- `ServiceCentrePriority.java`
  - `public record ServiceCentrePriority(List<String> serviceCentreIds)`
  - constructor must reject null/blank ids and duplicate ids
  - use `List.copyOf`
- `ServiceCentreWindowPolicy.java`
  - constructor: `public ServiceCentreWindowPolicy(ServiceCentrePriority priority)`
  - method: `public Optional<String> activeWindowFor(WarehouseSchedulerSnapshot snapshot)`
  - method: `public boolean isOrderInActiveWindow(DspSchedulerOrderState orderState, WarehouseSchedulerSnapshot snapshot)`

Rules:

- If no service centre is active, choose the first service centre in priority order that has unreleased work.
- Once active, continue releasing only that service centre.
- The active service centre is complete only when it has no unreleased orders left.
- Do not skip to another service centre because of temporary dependency or capacity blocks.
- The policy must be stateless; active service centre is supplied by `WarehouseSchedulerSnapshot.activeServiceCentreId()`.
- Unreleased means status `WAITING` or `BLOCKED`.
- Ignore service centres not present in `ServiceCentrePriority`.

Test methods:

- `shouldChooseFirstPriorityServiceCentreWithUnreleasedWork()`
- `shouldKeepActiveServiceCentreUntilItsWorkIsReleased()`
- `shouldMoveToNextServiceCentreAfterActiveWindowCompletes()`
- `shouldNotSkipBlockedActiveServiceCentre()`
- `shouldRejectDuplicateOrBlankServiceCentreIds()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicyTest
```

## Step 6: Release Scheduler

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspReleaseSchedulerTest.java`

Create exactly:

- `SchedulerCommand.java`
  - marker interface: `public interface SchedulerCommand {}`
- `ReleaseOrderCommand.java`
  - `public record ReleaseOrderCommand(String orderId, String serviceCentreId, StartLocation startLocation) implements SchedulerCommand`
- `ReleaseDecision.java`
  - `public record ReleaseDecision(String orderId, String serviceCentreId, StartLocation startLocation, RouteRequirements routeRequirements, ReleaseOrderCommand command)`
- `BlockedDecision.java`
  - `public record BlockedDecision(String activeServiceCentreId, List<String> candidateOrderIds, List<String> blockReasons)`
- `SchedulerEvaluation.java`
  - `public record SchedulerEvaluation(Optional<ReleaseDecision> releaseDecision, Optional<BlockedDecision> blockedDecision)`
  - static factories: `release(ReleaseDecision decision)`, `blocked(BlockedDecision decision)`, `nothingToRelease()`
- `DspReleaseScheduler.java`
  - constructor: `public DspReleaseScheduler(ServiceCentreWindowPolicy windowPolicy, DspDependencyEvaluator dependencyEvaluator)`
  - method: `public SchedulerEvaluation evaluate(WarehouseSchedulerSnapshot snapshot)`

Rules:

- Only consider orders from the active service-centre window.
- Exclude released/completed orders.
- Exclude dependency-blocked orders.
- Exclude capacity-blocked orders.
- Release priority: `ADAPTED`, then `ASSOCIATED`/`EMPTY`, then `FULL_PACK`.
- Tie-breakers: `sheetNumber`, then `sequenceNumber`, then `orderId`.
- Return a release decision and matching `ReleaseOrderCommand` for the first eligible order.
- If the active service centre has work but none is eligible, return blocked decision.
- The scheduler must not directly mark live machine/controller state.
- Capacity blocking should inspect required station admissions from `RouteRequirements`.
- Missing required station admission snapshots block release with a clear reason.

Test methods:

- `shouldReleaseHighestPriorityEligibleOrderInActiveServiceCentre()`
- `shouldUseSheetSequenceThenSequenceNumberThenOrderIdAsTieBreakers()`
- `shouldReturnBlockedDecisionWhenActiveServiceCentreHasOnlyBlockedWork()`
- `shouldReturnNothingWhenNoServiceCentreHasUnreleasedWork()`
- `shouldBlockWhenRequiredStationHasNoAdmissionSnapshot()`
- `shouldEmitReleaseCommandWithoutMutatingSnapshotState()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseSchedulerTest
```

## Step 7: P2P Admission Adapter Contract

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/p2p/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/p2p/P2pAdmissionAdapterTest.java`

Create exactly:

- `P2pAdmission.java`
  - `P2pAdmissionResult canAdmit(NotionalToteOrder order, P2pAdmissionSnapshot snapshot)`
- `P2pAdmissionSnapshot.java`
  - `public record P2pAdmissionSnapshot(String p2pCellId, int idlePrlCount, Set<String> activeBagCorrelations, Set<String> admissibleKnownCorrelations, boolean pcrAvailableForNewRelease)`
- `P2pAdmissionResult.java`
  - `public record P2pAdmissionResult(boolean accepted, String rejectionReason)`
  - factories should avoid colliding with the record accessor name
- `StaticP2pAdmission.java`
  - constructor: `public StaticP2pAdmission(P2pAdmissionResult result)`
- `P2pCapacityStationAdapter.java`
  - constructor: `public P2pCapacityStationAdapter(P2pAdmission p2pAdmission, P2pAdmissionSnapshot p2pSnapshot, StationCapacity capacity, StationSnapshot stationSnapshot)`
  - method: `public StationAdmissionSnapshot admissionFor(NotionalToteOrder order)`

Rules:

- Do not wire to `ToteToBagFlowController`.
- `P2pCapacityStationAdapter.admissionFor(...)` returns a `StationAdmissionSnapshot` for `StationType.P2P`.
- P2P admission is open only when both capacity can accept and `P2pAdmissionResult.accepted()` is true.
- The adapter must not store or call live tote-to-bag controllers.

Test methods:

- `shouldOpenP2pAdmissionWhenCapacityAndP2pAdmissionAllow()`
- `shouldCloseP2pAdmissionWhenCapacityIsFull()`
- `shouldCloseP2pAdmissionWhenP2pRejectsOrder()`
- `shouldDefensivelyCopyP2pAdmissionSnapshotSets()`
- `shouldRejectInvalidP2pSnapshotAndResultInputs()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionAdapterTest
```

## Step 8: Full Domain Regression

Allowed files:

- Add `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerScenarioTest.java`

Scenario:

- Two service centres: `SC-A`, `SC-B`.
- `SC-A` has adapted work, associated work depending on adapted, two sheets for the same notional tote, and one manual-merge requirement.
- `SC-B` has ready work.

Verify:

- `SC-B` does not release while `SC-A` has unreleased blocked work.
- Completing adapted unlocks associated/empty.
- Manual-ready unlocks manual merge.
- Sheet 2 does not release before sheet 1.
- After `SC-A` fully releases, `SC-B` can release.
- Scheduler decisions are generated from immutable snapshots.
- Release decisions produce commands; command application is left to the caller/simulation thread.

Test methods:

- `shouldHoldLaterServiceCentreWhileActiveServiceCentreIsBlocked()`
- `shouldReleaseActiveServiceCentreInDependencyAndSheetOrder()`
- `shouldMoveToNextServiceCentreAfterActiveServiceCentreCompletes()`
- `shouldProduceCommandsWithoutApplyingSimulationSideEffects()`

Verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerScenarioTest
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```
