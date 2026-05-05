# DSP Scheduler Implementation Plan

## Summary

Implement the scheduler in a new branch from `master`, starting with a pure domain scheduler that can be tested without visuals, renderables, live P2P integration, or real JSON imports.

The first branch establishes the order model, routing derivation, dependency checks, service-centre windowing, station capacity checks, and release decisions. Later branches can adapt this into OSR/AV02/debug tote injection and eventually product/12N JSON loading.

Branch strategy:

```powershell
git switch master
git switch -c feature/dsp-scheduler-domain
```

Assumption: `master` contains the latest tote-to-bag/P2P work.

## Key Decisions

- Package root: `online.davisfamily.warehouse.sim.dsp`.
- V1 data source: in-memory fixtures only.
- V1 service-centre rule: service centres are processed as whole release windows.
- V1 blocked service-centre rule: if the active service centre is blocked, release nothing from later service centres until the block clears.
- V1 P2P integration: model as a station capacity/admission concept only; do not call live `ToteToBagFlowController.canAdmit(...)` until a later integration branch.
- V1 JSON loading: out of scope until sample product master and 12N schemas are supplied.

## Step 1: Domain Enums And Value Objects

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderModelTest.java`

Add:

- `OrderType`: `ADAPTED`, `EMPTY`, `ASSOCIATED`, `FULL_PACK`
- `ToteType`: `ASSOCIATED`, `FULL_PACK`, `MANUAL_FLOW`
- `ProductCategory`: `AUTOMATED`, `SORTABLE`, `MANUAL`
- `StartLocation`: `OSR`, `AV02`
- `StationType`: `OSR`, `AV02`, `THIRD_PARTY`, `ADAPTING`, `MANUAL`, `P2P`, `MANUAL_MERGE`, `DISPATCH`
- `DependencyType`: `ADAPTED_COMPLETION`, `SHEET_SEQUENCE`, `SERVICE_CENTRE_ORDER`, `MANUAL_READY`
- `ProductMasterRecord(productId, ProductCategory category, boolean thirdParty)`
- `DspOrderItem(itemId, productId, quantity)`
- `NotionalToteOrder(orderId, notionalToteId, serviceCentreId, sheetNumber, OrderType orderType, List<DspOrderItem> items, long sequenceNumber)`

Validation:

- IDs must be non-null and non-blank.
- Item lists must be non-null and non-empty.
- `quantity` must be positive.
- `sheetNumber` must be `>= 1`.
- `sequenceNumber` must be `>= 0`.

Expected output:

- Domain model compiles.
- Tests prove invalid IDs/items/quantities/sheet numbers are rejected.
- No existing tote-to-bag files are changed.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderModelTest
```

## Step 2: Route Derivation

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/routing/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/routing/DspRouteDeriverTest.java`

Add:

- `ProductMasterRepository`
  - `Optional<ProductMasterRecord> findByProductId(String productId)`
- `InMemoryProductMasterRepository`
- `RouteRequirements`
  - `requiresThirdParty`
  - `requiresSortable`
  - `requiresManual`
  - `requiresP2p`
  - `requiresManualMerge`
  - `StartLocation startLocation`
- `DspRouteDeriver`

Rules:

- `EMPTY` starts at `AV02`; all other order types start at `OSR`.
- `ASSOCIATED` and `EMPTY` require `P2P`.
- `FULL_PACK` does not require `P2P`.
- Third-party product sets `requiresThirdParty`.
- `SORTABLE` product sets `requiresSortable`.
- `MANUAL` product sets `requiresManual`.
- Manual items on `ASSOCIATED`/`EMPTY` set `requiresManualMerge`.
- Missing product master data throws `IllegalArgumentException`.

Expected output:

- Tests cover automated, sortable, manual, third-party, empty-order start location, and missing product master.
- Still no visual or controller integration.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriverTest
```

## Step 3: Scheduler State And Capacity Model

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerStateTest.java`

Add:

- `StationCapacity(int maxInProgress, int queueLimit)`
- `StationSnapshot(StationType stationType, int inProgress, int queued)`
- `DspOrderStatus`: `WAITING`, `RELEASED`, `COMPLETED`, `BLOCKED`
- `DspSchedulerOrderState`
  - wraps `NotionalToteOrder`
  - stores `RouteRequirements`
  - stores status
- `DspSchedulerSnapshot`
  - order states
  - station snapshots
  - completed adapted notional tote IDs
  - manual-ready notional tote IDs

Capacity rules:

- A station accepts if `inProgress < maxInProgress` or `queued < queueLimit`.
- If both processing and queue are full, upstream release is blocked.
- Invalid negative capacities or counts throw.

Expected output:

- Tests prove capacity acceptance/full behavior.
- This step does not decide release order yet.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerStateTest
```

## Step 4: Dependency Evaluation

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspDependencyEvaluatorTest.java`

Add:

- `DependencyBlock(DependencyType type, String reason)`
- `DspDependencyEvaluator`

Rules:

- `ASSOCIATED` and `EMPTY` are blocked by `ADAPTED_COMPLETION` until their `notionalToteId` is in completed adapted IDs.
- Sheet `n > 1` is blocked by `SHEET_SEQUENCE` until sheet `n - 1` for the same notional tote is completed or released according to scheduler state.
- Orders requiring manual merge are blocked by `MANUAL_READY` until their `notionalToteId` is manual-ready.
- `ADAPTED` and `FULL_PACK` do not require adapted completion.

Expected output:

- Tests prove adapted-before-associated, sheet ordering, manual-ready, and unblocked full-pack behavior.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 5: Service-Centre Window Policy

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/ServiceCentreWindowPolicyTest.java`

Add:

- `ServiceCentrePriority`
  - configured ordered list of service centre IDs
- `ServiceCentreWindowPolicy`
  - tracks active service centre
  - selects next service centre by configured priority order
  - does not interleave service centres

Rules:

- If no service centre is active, choose the first service centre in priority order that has unreleased work.
- Once active, continue releasing only that service centre.
- The active service centre is complete only when it has no unreleased orders left.
- If the active service centre has unreleased work but all candidates are blocked, return no release decision.
- Do not skip to another service centre because of temporary dependency or capacity blocks.

Expected output:

- Tests prove no interleaving.
- Tests prove blocked active service centre holds the window.
- Tests prove transition from the last order of one service centre to the first order of the next.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicyTest
```

## Step 6: Release Scheduler

Allowed files:

- Extend files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspReleaseSchedulerTest.java`

Add:

- `ReleaseDecision`
  - selected order ID
  - service centre ID
  - start location
  - required route
- `BlockedDecision`
  - active service centre ID
  - candidate order IDs
  - dependency/capacity block reasons
- `DspReleaseScheduler`

Release priority inside the active service-centre window:

1. `ADAPTED`
2. `ASSOCIATED` / `EMPTY`
3. `FULL_PACK`

Tie-breakers:

1. `sheetNumber`
2. `sequenceNumber`
3. `orderId`

Rules:

- Only consider orders from the active service-centre window.
- Exclude released/completed orders.
- Exclude dependency-blocked orders.
- Exclude capacity-blocked orders.
- Return a release decision for the first eligible order.
- If the active service centre has work but none is eligible, return blocked decision.
- Marking an order released is explicit; the scheduler should not mutate state unless called through a clear method such as `markReleased(orderId)`.

Expected output:

- Tests prove priority order, sheet order, FIFO tie-break, capacity blocking, dependency blocking, and service-centre window holding.
- This is the main green point for the domain scheduler branch.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseSchedulerTest
```

## Step 7: P2P Admission Adapter Contract

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/dsp/p2p/`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/p2p/P2pAdmissionAdapterTest.java`

Add:

- `P2pAdmission`
  - `boolean canAdmit(NotionalToteOrder order)`
- `StaticP2pAdmission`
  - test implementation returning configured true/false
- `P2pCapacityStationAdapter`
  - exposes P2P as a capacity/admission check for the scheduler

Do not wire to `ToteToBagFlowController` yet.

Expected output:

- Tests prove P2P can block release independently of generic station capacity.
- Scheduler can depend on a small interface rather than tote-to-bag internals.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionAdapterTest
```

## Step 8: Full Domain Regression

Allowed files:

- No new production files unless fixing issues found by tests.
- Add `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerScenarioTest.java`

Scenario test:

- Two service centres: `SC-A`, `SC-B`
- `SC-A` has:
  - adapted order
  - associated order depending on adapted
  - two sheets for the same notional tote
  - one manual-merge requirement
- `SC-B` has ready work

Verify:

- `SC-B` does not release while `SC-A` has unreleased blocked work.
- Completing adapted unlocks associated/empty.
- Manual-ready unlocks manual merge.
- Sheet 2 does not release before sheet 1.
- After `SC-A` fully releases, `SC-B` can release.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerScenarioTest
```

Then ask user to run the broader focused suite:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```

## Later Branches

After `feature/dsp-scheduler-domain` is green and committed:

1. `feature/dsp-scheduler-osr-integration`
   - Add scheduler-driven OSR/AV02 release sources.
   - Replace or wrap `DebugToteInjectorController` with scheduler-selected releases.
   - Create tote renderables only at release time.

2. `feature/dsp-scheduler-p2p-live-admission`
   - Implement `P2pAdmission` using existing `ToteToBagFlowController.canAdmit(ToteLoadPlan)`.
   - Add an adapter from scheduler order/tote data to `ToteLoadPlan`.

3. `feature/dsp-scheduler-json-loading`
   - Add product master and 12N loaders after sample JSON schemas are provided.
   - Revisit whether a database is useful after measuring in-memory loading and query shape.

4. `feature/renderable-visibility-lifecycle`
   - Add cheap visibility/skipping support to `RenderableObject`.
   - Apply it to totes, contained packs, free packs, and bags.

## Assumptions

- `master` is the correct base branch.
- The first scheduler branch is domain-only and fixture-driven.
- Service centres must not be mixed during release.
- A blocked active service centre blocks later service centres.
- JSON import, live visual injection, live P2P admission, database decisions, deadlock override timers, and command-button/manual exception handling are later branches.
