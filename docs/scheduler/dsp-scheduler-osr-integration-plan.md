# DSP Scheduler OSR Integration Plan

Status: drafted. Implement on `feature/dsp-scheduler-osr-integration`.

## Summary

Detailed implementation plan for `feature/dsp-scheduler-osr-integration`.

This branch wires the completed scheduler domain into the debug OSR/tote release path by replacing FIFO queued-tote injection with scheduler-selected queued-tote release. It keeps scheduler evaluation synchronous and command-based. It does not add a scheduler thread and does not wire live P2P admission yet.

Important scope boundary:

- The current debug tipper install still has a primary/bootstrap tote because `TipperSectionInstaller`, `TipperInstallation`, `TipperModule`, and `TipperToSorterSection` currently assume an initial tote payload.
- This branch should replace/wrap `DebugToteInjectorController` for queued OSR releases after that bootstrap tote.
- Do not refactor the installed tipper to be tote-less in this branch.
- A later branch can remove the bootstrap tote special case once the scheduler-driven release path is proven.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/dsp-scheduler-osr-integration
```

## Key Decisions

- Scheduler evaluation remains synchronous inside the simulation update loop.
- Scheduler decisions are still based on immutable `WarehouseSchedulerSnapshot`.
- Scheduler output is still a `ReleaseOrderCommand`; command application stays on the simulation thread.
- The scheduler must not call `ToteToBagFlowController.canAdmit(...)` in this branch.
- P2P is represented by a static/open `StationAdmissionSnapshot` in the debug OSR integration.
- Debug queued tote release should be scheduler-selected, not FIFO-selected.
- Renderables for scheduler-controlled queued totes should be added to the scene only when the scheduler releases that tote.
- Existing `DebugToteInjectorController` should remain available for non-scheduler debug scenarios unless no references remain.

## Step 1: Runtime State And Command Application Types

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/runtime/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/runtime/DspSchedulerRuntimeStateTest.java`

Create exactly:

- `SchedulerCommandApplicationResult.java`
  - `public record SchedulerCommandApplicationResult(boolean applied, boolean deferred, String reason)`
  - static factories: `appliedResult()`, `deferredResult(String reason)`, `rejectedResult(String reason)`
  - Do not name a no-argument static factory `applied()` because that collides with the generated record accessor.
- `SchedulerCommandHandler.java`
  - `SchedulerCommandApplicationResult apply(SchedulerCommand command)`
- `DspSchedulerRuntimeState.java`
  - constructor: `public DspSchedulerRuntimeState(WarehouseSchedulerSnapshot initialSnapshot)`
  - method: `public WarehouseSchedulerSnapshot snapshot()`
  - method: `public void markReleased(String orderId)`
  - method: `public void replaceStationAdmission(StationAdmissionSnapshot stationAdmission)`
  - method: `public void markAdaptedComplete(String notionalToteId)`
  - method: `public void markManualReady(String notionalToteId)`

Rules:

- Store mutable runtime state internally, but always expose immutable `WarehouseSchedulerSnapshot`.
- `markReleased(orderId)` changes exactly one order from `WAITING` or `BLOCKED` to `RELEASED`.
- `markReleased(orderId)` must set the active service centre to the released order's service centre.
- Reject unknown order ids.
- Reject attempts to mark `COMPLETED` orders as released.
- `replaceStationAdmission(...)` replaces the station admission by `stationType`.
- Do not store controllers, renderables, totes, or command queues in this runtime state.

Test methods:

- `shouldExposeImmutableSnapshotFromRuntimeState()`
- `shouldMarkWaitingOrderReleasedAndSetActiveServiceCentre()`
- `shouldRejectUnknownOrCompletedOrderRelease()`
- `shouldReplaceStationAdmissionByStationType()`
- `shouldTrackAdaptedCompleteAndManualReadySets()`

Expected output:

- Runtime state provides the mutable simulation-thread owner for scheduler snapshots.
- No debug injector changes yet.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeStateTest
```

## Step 2: Scheduled Tipper Release Catalog

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/ScheduledTipperToteReleaseCatalogTest.java`

Create exactly:

- `TipperTotePayloadFactory.java`
  - functional interface
  - `TipperTotePayload createPayload()`
- `ScheduledTipperToteRelease.java`
  - `public record ScheduledTipperToteRelease(String orderId, ToteLoadPlan toteLoadPlan, TipperTotePayloadFactory payloadFactory)`
  - method: `public TipperTotePayload createPayload()`
- `ScheduledTipperToteReleaseCatalog.java`
  - constructor: `public ScheduledTipperToteReleaseCatalog(List<ScheduledTipperToteRelease> releases)`
  - method: `public Optional<ScheduledTipperToteRelease> findByOrderId(String orderId)`
  - method: `public List<ToteLoadPlan> toteLoadPlans()`
  - method: `public ToteLoadPlanProvider toteLoadPlanProvider()`

Rules:

- `ScheduledTipperToteReleaseCatalog` rejects duplicate order ids.
- `ScheduledTipperToteReleaseCatalog` rejects duplicate tote ids.
- `toteLoadPlanProvider()` returns by tote id across all catalog entries.
- Payload factories must be lazy; constructing the catalog must not create `TipperTotePayload`.
- This package is debug/testing integration, not core scheduler domain.

Test methods:

- `shouldFindReleaseByOrderId()`
- `shouldProvideToteLoadPlansByToteId()`
- `shouldRejectDuplicateOrderIdsAndToteIds()`
- `shouldCreatePayloadLazilyOnlyWhenReleaseIsUsed()`

Expected output:

- Scheduler order ids can be mapped to debug tote payloads and load plans without FIFO assumptions.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledTipperToteReleaseCatalogTest
```

## Step 3: Scheduler-Driven Debug Injector

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/ScheduledDebugToteInjectorControllerTest.java`

Create exactly:

- `ScheduledToteReleaseTarget.java`
  - `boolean canAcceptRelease()`
  - `SchedulerCommandApplicationResult release(TipperTotePayload payload)`
- `ScheduledDebugToteInjectorController.java`
  - implements `SimulationController`
  - constructor:
    `public ScheduledDebugToteInjectorController(DspReleaseScheduler scheduler, DspSchedulerRuntimeState runtimeState, ScheduledTipperToteReleaseCatalog releaseCatalog, ScheduledToteReleaseTarget releaseTarget)`

Update behavior:

- On each `update(...)`, return immediately if `releaseTarget.canAcceptRelease()` is false.
- Evaluate scheduler using `runtimeState.snapshot()`.
- If scheduler returns `nothingToRelease()`, do nothing.
- If scheduler returns `BlockedDecision`, do nothing.
- If scheduler returns a `ReleaseDecision`, read its `ReleaseOrderCommand`.
- Look up `ScheduledTipperToteRelease` by command order id.
- If no release exists for the command, do not mutate runtime state; throw `IllegalStateException` with the missing order id.
- Create payload through the release entry's lazy factory.
- Apply it through `releaseTarget.release(payload)`.
- If `result.applied()` is true, call `runtimeState.markReleased(command.orderId())`.
- If `result.deferred()` is true, do not mark released.
- If neither `applied()` nor `deferred()` is true, treat the result as rejected and throw `IllegalStateException` with the result reason.

Rules:

- The controller must not add renderables directly.
- The controller must not call `ToteTrackTipperFlowController` directly.
- The controller must not mutate scheduler state before command application succeeds.
- This class owns scheduler evaluation cadence for the debug integration only.

Test methods:

- `shouldReleaseSchedulerSelectedToteAndMarkOrderReleased()`
- `shouldDoNothingWhenTargetCannotAcceptRelease()`
- `shouldDoNothingWhenSchedulerHasNoReleaseDecision()`
- `shouldDoNothingWhenSchedulerReturnsBlockedDecision()`
- `shouldNotMarkReleasedWhenTargetDefersCommand()`
- `shouldRejectMissingReleaseForSchedulerCommand()`

Expected output:

- Queued release is scheduler-selected and command-applied, independent of concrete tipper machinery.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledDebugToteInjectorControllerTest
```

## Step 4: Tipper Flow Release Target

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/TipperFlowScheduledToteReleaseTargetTest.java`

Create exactly:

- `TipperFlowScheduledToteReleaseTarget.java`
  - constructor:
    `public TipperFlowScheduledToteReleaseTarget(SimulationWorld sim, List<RenderableObject> objects, ToteTrackTipperFlowController tipperFlowController)`
  - implements `ScheduledToteReleaseTarget`

Behavior:

- `canAcceptRelease()` delegates to `tipperFlowController.canAcceptNextTote()`.
- `release(payload)` returns `SchedulerCommandApplicationResult.deferredResult(...)` when `canAcceptRelease()` is false.
- `release(payload)` adds `payload.getToteRenderable()` to `objects` if absent.
- `release(payload)` calls `sim.addTrackableObject(payload.getTote())`.
- `release(payload)` calls `tipperFlowController.acceptNextTote(payload.getTote())`.
- `release(payload)` returns `SchedulerCommandApplicationResult.appliedResult()` after those operations succeed.

Rules:

- This is the only new class in the OSR integration branch that should touch renderables, `SimulationWorld`, or `ToteTrackTipperFlowController`.
- Do not duplicate the scheduler decision logic here.
- Do not inspect or mutate `ToteLoadPlan` here.

Test methods:

- `shouldDeferWhenTipperCannotAcceptRelease()`
- `shouldAddRenderableAndTrackableObjectWhenReleased()`
- `shouldNotAddRenderableTwice()`
- `shouldPassToteToTipperFlowController()`

Expected output:

- Command application to the existing tipper flow is isolated behind a small simulation-thread adapter.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.TipperFlowScheduledToteReleaseTargetTest
```

## Step 5: Debug Scheduler Fixture Adapter

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/DspDebugSchedulerFixtureAdapterTest.java`

Create exactly:

- `DspDebugSchedulerFixtureAdapter.java`
  - method:
    `public DspSchedulerRuntimeState createRuntimeState(List<TipperDemoFixtures.DemoTipperFeed> feeds, Map<StationType, StationAdmissionSnapshot> stationAdmissions, String serviceCentreId)`
  - method:
    `public ScheduledTipperToteReleaseCatalog createReleaseCatalog(List<TipperDemoFixtures.DemoTipperFeed> feeds)`

Mapping rules:

- `orderId = feed.toteLoadPlan().getToteId()`
- `notionalToteId = "notional-" + feed.toteLoadPlan().getToteId()`
- `serviceCentreId` comes from the method argument.
- `sheetNumber = 1`
- `orderType = ASSOCIATED`
- `sequenceNumber` is the feed list index.
- `RouteRequirements(false, false, false, true, false, StartLocation.OSR)`
- Since this debug slice is not modelling adapted work, initial `completedAdaptedNotionalToteIds` must include every generated notional tote id.
- Initial `manualReadyNotionalToteIds` is empty.
- Initial `activeServiceCentreId` is empty.

Catalog rules:

- The catalog should include only feeds passed to the method.
- Do not force payload creation while building the catalog.
- The existing `DemoTipperFeed` already contains a payload, so this branch can wrap it lazily with `() -> feed.totePayload()`.
- A future branch may make fixture payload creation fully lazy; do not refactor `TipperDemoFixtures` in this branch.

Test methods:

- `shouldCreateSchedulerOrderStateForEachFeed()`
- `shouldMarkAdaptedCompleteForAssociatedDebugOrders()`
- `shouldCreateP2pRouteRequirementsForDebugOrders()`
- `shouldCreateReleaseCatalogForFeedsWithoutCreatingAdditionalPayloads()`

Expected output:

- Existing demo feed sets can be converted into scheduler runtime state and release catalog inputs.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.DspDebugSchedulerFixtureAdapterTest
```

## Step 6: Wire Scheduler Into Integrated Debug Installer

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
- Add focused test if an existing installer-level test pattern exists; otherwise rely on Step 7 visual verification plus focused controller tests.

Implementation:

- Keep the existing primary feed install path unchanged.
- Build `DspSchedulerRuntimeState` and `ScheduledTipperToteReleaseCatalog` from `demoTipperFeedSet.additionalFeeds()` only.
- Use a static/open `StationAdmissionSnapshot` for `StationType.P2P`:
  - capacity can be generous for this branch, for example `new StationCapacity(1, 100)`
  - snapshot can be `new StationSnapshot(StationType.P2P, 0, 0)`
  - admission open is true
- Construct `DspReleaseScheduler` with:
  - `new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("SC-DEBUG")))`
  - `new DspDependencyEvaluator()`
- Replace the existing `new DebugToteInjectorController(...)` registration with:
  - `ScheduledDebugToteInjectorController`
  - `TipperFlowScheduledToteReleaseTarget`
- Continue setting `tipperToSorterSection.getFlowController().setToteAdmissionPredicate(flowController::canAdmit)`.
- Continue registering additional tote sources with `tipperToSorterSection.registerToteSource(...)`.
- Do not wire live `P2pAdmission` to `ToteToBagFlowController.canAdmit(...)` in this branch.

Expected output:

- The integrated debug harness uses scheduler-selected release for queued/additional totes.
- Existing local tipper admission still holds a scheduler-selected tote if the tote-to-bag cell cannot currently admit it.
- The primary/bootstrap tote remains unchanged and documented as a current limitation.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 7: Visual Check

Allowed files:

- No code changes unless the visual check exposes a concrete issue.
- If a small debug label/log is needed, ask before adding it.

Visual expectations:

- The first/bootstrap tote still appears as before.
- The second queued tote is released by `ScheduledDebugToteInjectorController`, not `DebugToteInjectorController`.
- The scheduler runtime state marks that queued order `RELEASED` only after the tote is successfully handed to the tipper release target.
- No renderable for a scheduler-controlled queued tote is added to `objects` before scheduler release.
- The tote-to-bag local admission gate can still hold the tote at the tipper if P2P cannot accept it yet.

Ask user to run the existing integrated visual scene using their normal run configuration.

Expected output:

- Visual behavior matches the current integrated tote-to-bag harness, but queued release is now scheduler-controlled.

## Step 8: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.* --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

Then ask user to run their trusted broader suite/visual pass.

Completion criteria:

- Focused scheduler/testing integration tests pass.
- Existing tote-to-bag focused tests still pass.
- Integrated visual check works.
- The branch has not introduced a scheduler thread.
- The branch has not introduced live P2P admission.
- The bootstrap primary tote limitation remains documented for a later branch.
