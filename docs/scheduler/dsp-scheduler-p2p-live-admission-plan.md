# DSP Scheduler P2P Live Admission Plan

Status: complete and green.

## Summary

Detailed implementation plan for `feature/dsp-scheduler-p2p-live-admission`.

This branch replaces the debug scheduler's static/open P2P station admission with candidate-specific admission derived from the existing tote-to-bag local admission boundary:

```text
DspReleaseScheduler candidate -> P2P admission resolver -> ToteToBagFlowController.canAdmit(ToteLoadPlan)
```

Scheduler evaluation remains synchronous in this branch. Do not add a scheduler thread. Live mutable machine state must only be read on the simulation thread.

## Key Decisions

- P2P admission is order-specific, not just station-specific.
- `WarehouseSchedulerSnapshot.stationAdmissions()` remains useful for non-order-specific station capacity and for default behavior.
- `DspReleaseScheduler` needs a candidate-aware station admission resolver so later candidates can be considered even if an earlier candidate cannot enter P2P.
- `ToteToBagFlowController.canAdmit(ToteLoadPlan)` remains the authoritative local P2P/tote-to-bag admission check.
- The scheduler must not directly depend on `ToteToBagFlowController`; keep that dependency in adapter code.
- The existing tipper admission predicate must remain in place as the final local guard.
- Do not remove the bootstrap primary tote special case in this branch.
- Do not add JSON loading, scheduler threading, multiple P2P cells, or real OSR/AV02 routing in this branch.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/dsp-scheduler-p2p-live-admission
```

## Step 1: Candidate-Aware Station Admission Resolver

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Update `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspReleaseScheduler.java`
- Create or update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/`

Create exactly:

- `StationAdmissionResolver.java`
  - `StationAdmissionSnapshot admissionFor(StationType stationType, DspSchedulerOrderState candidate, WarehouseSchedulerSnapshot snapshot)`
- `SnapshotStationAdmissionResolver.java`
  - reads `snapshot.stationAdmissions().get(stationType)`
  - returns `null` when no snapshot exists, preserving current missing-admission behavior

Update `DspReleaseScheduler`:

- Keep the existing constructor:
  - `public DspReleaseScheduler(ServiceCentreWindowPolicy windowPolicy, DspDependencyEvaluator dependencyEvaluator)`
  - it must delegate to the new constructor with `new SnapshotStationAdmissionResolver()`
- Add constructor:
  - `public DspReleaseScheduler(ServiceCentreWindowPolicy windowPolicy, DspDependencyEvaluator dependencyEvaluator, StationAdmissionResolver stationAdmissionResolver)`
- Store the resolver in a final field.
- In capacity block evaluation, replace direct `snapshot.stationAdmissions().get(stationType)` lookup with:
  - `stationAdmissionResolver.admissionFor(stationType, candidate, snapshot)`
- Keep the existing missing station message:
  - `"Missing station admission snapshot for " + stationType`

Rules:

- Do not change scheduler ordering rules.
- Do not change dependency evaluation.
- Do not change `WarehouseSchedulerSnapshot`.
- Existing tests using the old constructor must still pass.

Test methods:

- Add `shouldUseCandidateAwareStationAdmissionResolver()` to `DspReleaseSchedulerTest`.
- The test should create two waiting P2P candidates in the same service centre.
- The resolver should return closed P2P admission for the first candidate and open P2P admission for the second.
- Expected scheduler result: release decision for the second candidate, not a blocked decision for the first.
- Keep at least one existing test path using the old constructor to prove default snapshot behavior remains intact.

Expected output:

- Scheduler can evaluate candidate-specific station admission without coupling to P2P or tote-to-bag classes.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseSchedulerTest
```

## Step 2: Generic P2P Station Admission Resolver

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/p2p/`
- Create or update tests under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/p2p/`

Create exactly:

- `P2pStationAdmissionResolver.java`
  - implements `StationAdmissionResolver`
  - constructor:
    `public P2pStationAdmissionResolver(StationAdmissionResolver fallbackResolver, P2pAdmission p2pAdmission, Supplier<P2pAdmissionSnapshot> p2pSnapshotSupplier, StationCapacity p2pCapacity, Supplier<StationSnapshot> p2pStationSnapshotSupplier)`

Behavior:

- For station types other than `StationType.P2P`, delegate to `fallbackResolver`.
- For `StationType.P2P`:
  - get the latest `P2pAdmissionSnapshot` from `p2pSnapshotSupplier`
  - get the latest `StationSnapshot` from `p2pStationSnapshotSupplier`
  - create a `P2pCapacityStationAdapter`
  - return `adapter.admissionFor(candidate.order())`

Rules:

- Reject null constructor inputs.
- Reject non-P2P station snapshots through the existing `P2pCapacityStationAdapter` validation.
- Do not call `ToteToBagFlowController` here.
- Do not cache supplier values across calls; this resolver must read current simulation-thread state each evaluation.

Test methods:

- `shouldResolveP2pAdmissionForCandidateOrder()`
- `shouldDelegateNonP2pStationsToFallbackResolver()`
- `shouldUseLatestSupplierValuesForEachCall()`
- `shouldRejectNullInputs()`

Expected output:

- Generic scheduler/P2P bridge exists, but still does not know about debug releases or tote load plans.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionAdapterTest --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolverTest
```

## Step 3: Scheduled Release P2P Admission Adapter

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/`

Create exactly:

- `ScheduledReleaseP2pAdmission.java`
  - implements `P2pAdmission`
  - constructor:
    `public ScheduledReleaseP2pAdmission(ScheduledTipperToteReleaseCatalog releaseCatalog, ToteToBagFlowController flowController)`

Behavior:

- `canAdmit(order, snapshot)` looks up the scheduled release by `order.orderId()`.
- If no release exists, return `P2pAdmissionResult.rejectedResult("No scheduled P2P tote load plan for order " + order.orderId())`.
- If a release exists, call `flowController.canAdmit(release.toteLoadPlan())`.
- If `canAdmit(...)` is true, return `P2pAdmissionResult.acceptedResult()`.
- If `canAdmit(...)` is false, return `P2pAdmissionResult.rejectedResult("P2P cannot admit tote " + release.toteLoadPlan().getToteId())`.

Rules:

- Do not create payloads.
- Do not add renderables or trackables.
- Do not call `ScheduledTipperToteRelease.createPayload()`.
- Do not mutate scheduler runtime state.
- This adapter is debug/testing integration. It may depend on `ScheduledTipperToteReleaseCatalog` and `ToteToBagFlowController`.

Test methods:

- `shouldAcceptWhenFlowControllerCanAdmitScheduledToteLoadPlan()`
- `shouldRejectWhenFlowControllerCannotAdmitScheduledToteLoadPlan()`
- `shouldRejectWhenNoScheduledReleaseExistsForOrder()`
- `shouldNotCreatePayloadDuringAdmissionCheck()`

Expected output:

- Debug scheduler orders can be checked against live tote-to-bag admission through their scheduled tote load plans.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.ScheduledReleaseP2pAdmissionTest
```

## Step 4: Runtime P2P Admission Snapshot Helper

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/`

Create exactly:

- `DebugP2pAdmissionSnapshotFactory.java`
  - constructor:
    `public DebugP2pAdmissionSnapshotFactory(String p2pCellId, ToteToBagFlowController flowController)`
  - method:
    `public P2pAdmissionSnapshot snapshot()`
  - method:
    `public StationSnapshot stationSnapshot()`

Behavior:

- `snapshot()` reads `flowController.getPrlsById().values()`.
- `idlePrlCount` is the number of PRLs with assignment state `PrlState.IDLE`.
- `activeBagCorrelations` contains non-blank correlation ids from non-idle PRL assignments.
- `admissibleKnownCorrelations` can initially match `activeBagCorrelations`.
- `pcrAvailableForNewRelease` can be `true` for this branch unless a clear existing public API can compute it without expanding tote-to-bag controller surface.
- `stationSnapshot()` returns `new StationSnapshot(StationType.P2P, 0, 0)` for this branch.

Rules:

- This helper provides diagnostic/snapshot context for the P2P adapter.
- Do not add public getters to `ToteToBagFlowController` solely for PCR internals in this branch.
- Do not duplicate `ToteToBagFlowController.canAdmit(...)` logic here.

Test methods:

- `shouldReportIdlePrlCount()`
- `shouldReportActiveCorrelationsFromAssignedPrls()`
- `shouldCreateP2pStationSnapshot()`
- `shouldRejectNullOrBlankInputs()`

Expected output:

- The live P2P resolver has a current P2P snapshot supplier without expanding the tote-to-bag controller API.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.DebugP2pAdmissionSnapshotFactoryTest
```

## Step 5: Wire Live P2P Admission Into Integrated Debug Installer

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
- Add focused installer-level test only if an existing installer test pattern is already available; otherwise rely on Step 6 visual verification plus focused adapter tests.

Implementation:

- Keep the primary/bootstrap tote install path unchanged.
- Keep `tipperToSorterSection.getFlowController().setToteAdmissionPredicate(flowController::canAdmit)`.
- Build `DspSchedulerRuntimeState` and `ScheduledTipperToteReleaseCatalog` from `demoTipperFeedSet.additionalFeeds()` as in the OSR integration branch.
- Replace the current static/open P2P scheduler behavior with:
  - `ScheduledReleaseP2pAdmission`
  - `DebugP2pAdmissionSnapshotFactory`
  - `P2pStationAdmissionResolver`
  - `SnapshotStationAdmissionResolver` as fallback
- Construct `DspReleaseScheduler` with the new resolver-aware constructor.
- Initial runtime state may still contain a P2P station snapshot, but the resolver must be the source used during scheduler evaluation.
- Do not wire a scheduler thread.
- Do not remove the local tipper admission predicate.

Expected output:

- Scheduler release decisions for queued debug totes are blocked when the live tote-to-bag cell cannot admit that specific tote.
- Once the live tote-to-bag state can admit the tote, the scheduler can select it and the existing release target can hand it to the tipper flow.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 6: Visual Check

Allowed files:

- No code changes unless the visual check exposes a concrete issue.

Visual expectations:

- The first/bootstrap tote still behaves as before.
- Queued totes are still scheduler-selected.
- A queued tote whose bag mix cannot currently be admitted by the P2P/tote-to-bag cell should not be released by the scheduler.
- When the P2P/tote-to-bag cell becomes able to admit the queued tote, it should release and proceed as before.
- No renderable for a scheduler-controlled queued tote appears before release.
- The local tipper admission predicate still acts as a final guard.

Ask user to run the existing integrated visual scene using their normal run configuration.

Expected output:

- Visual behavior matches OSR integration behavior, with scheduler decisions now respecting live P2P admission instead of a static/open P2P station.

## Step 7: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.* --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest
```

Then ask user to run their trusted broader suite/visual pass.

Completion criteria:

- Focused scheduler/P2P/testing integration tests pass.
- Existing tote-to-bag focused tests still pass.
- Integrated visual check works.
- Scheduler evaluation remains synchronous.
- No scheduler thread is introduced.
- The bootstrap primary tote limitation remains unchanged.
