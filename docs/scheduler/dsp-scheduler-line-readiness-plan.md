# DSP Scheduler Line Readiness Plan

Status: drafted. Implement on `feature/dsp-scheduler-line-readiness`.

## Summary

This branch corrects the scheduler domain before OSR integration so it matches the 12N message shape.

The current scheduler domain treats adapted/manual readiness as notional-tote-level state. The 12N examples show that this is too coarse:

- `pharmacyId` is line-level.
- `ASSOCIATED` and `FULL_PACK` dispatch orders are pharmacy-pure.
- `MANUAL` tote order examples are pharmacy-pure.
- `ADAPTED` tote orders are not pharmacy-pure and may contain lines for many pharmacies.
- Prepared lines reference their target dispatch order through `referenceOrderId` and `referenceSheetNumber`.

This branch moves readiness dependencies from notional-tote-level sets to prepared line readiness keys. It does not add JSON loading, OSR release integration, scheduler threading, live P2P admission, or controller changes.

Branch strategy:

```powershell
git switch master
git pull
git switch -c feature/dsp-scheduler-line-readiness
```

## Key Decisions

- `serviceCentreId` remains order-level and controls scheduler release windows.
- `pharmacyId` is line-level and controls dispatch purity.
- `NotionalToteOrder` remains the scheduler's release unit.
- `ADAPTED` orders are preparation batches, not dispatch totes, and may contain multiple pharmacies.
- `ASSOCIATED`, `EMPTY`, and `FULL_PACK` orders must be pharmacy-pure.
- Manual 12N tote orders are pharmacy-pure, but `MANUAL_FLOW` remains a preparation flow rather than an `OrderType`.
- Prepared adapted/manual work unlocks dispatch orders by target order id, target sheet, order line number, and line type.

## Step 1: Add Line-Level 12N Metadata

Allowed files:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderItem.java`
- Create `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderLineType.java`
- Update `app/src/test/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderModelTest.java`
- Update existing DSP scheduler/routing test helpers only where compilation requires it.

Create:

- `DspOrderLineType.java`
  - enum values: `MANUAL`, `ADAPTED`, `FULL_PACK`
  - field: 12N code string
  - method: `public String code()`
  - method: `public static DspOrderLineType fromCode(String code)`
  - map `01 -> MANUAL`, `02 -> ADAPTED`, `05 -> FULL_PACK`
  - reject unknown, blank, or null codes with `IllegalArgumentException`

Update `DspOrderItem` canonical record fields to:

```java
public record DspOrderItem(
        String itemId,
        String productId,
        int quantity,
        String pharmacyId,
        DspOrderLineType lineType,
        String referenceOrderId,
        int referenceSheetNumber,
        int numberOfPacksPicked)
```

Validation rules:

- `itemId`, `productId`, `pharmacyId`, and `referenceOrderId` must not be blank.
- `quantity` must be positive.
- `lineType` must not be null.
- `referenceSheetNumber` must be `>= 1`.
- `numberOfPacksPicked` must be `>= 0`.
- Trim fixed-width 12N string fields before storing.

Compatibility rule:

- Add a secondary constructor matching the old shape:

```java
public DspOrderItem(String itemId, String productId, int quantity)
```

- The secondary constructor should use safe debug defaults:
  - `pharmacyId = "UNKNOWN"`
  - `lineType = DspOrderLineType.FULL_PACK`
  - `referenceOrderId = itemId`
  - `referenceSheetNumber = 1`
  - `numberOfPacksPicked = 0`
- Existing tests may keep using the old constructor unless they are testing pharmacy purity or line readiness.

Test methods:

- `shouldParseKnownDspOrderLineTypeCodes()`
- `shouldRejectUnknownDspOrderLineTypeCode()`
- `shouldStoreTrimmedLineLevelMetadata()`
- `shouldRejectBlankPharmacyAndReferenceOrderIds()`
- `shouldKeepLegacyDspOrderItemConstructorForDebugData()`

Expected output:

- DSP order lines can represent 12N line-level destination and reference metadata.
- Existing scheduler/routing tests compile with minimal helper updates.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderModelTest
```

## Step 2: Add Dispatch Purity Validation

Allowed files:

- Create `app/src/main/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderValidator.java`
- Create `app/src/test/java/online/davisfamily/warehouse/sim/dsp/model/DspOrderValidatorTest.java`

Create:

- `DspOrderValidator.java`
  - method: `public void validateForScheduler(NotionalToteOrder order)`
  - method: `public boolean isPharmacyPure(NotionalToteOrder order)`
  - method: `public Set<String> pharmacyIds(NotionalToteOrder order)`

Rules:

- `ASSOCIATED`, `EMPTY`, and `FULL_PACK` must contain exactly one pharmacy id.
- `ADAPTED` may contain one or more pharmacy ids.
- Do not add `MANUAL` to `OrderType`.
- Manual 12N tote orders should be represented later as manual preparation input, not scheduler dispatch orders.
- The validator must not derive product routing and must not call product master data.

Test methods:

- `shouldAcceptPharmacyPureAssociatedEmptyAndFullPackOrders()`
- `shouldRejectMixedPharmacyAssociatedEmptyAndFullPackOrders()`
- `shouldAllowMixedPharmacyAdaptedOrders()`
- `shouldExposePharmacyIdsForDiagnostics()`

Expected output:

- The domain can explicitly enforce the dispatch-store purity rule before release scheduling.
- Adapted preparation batches remain allowed to span multiple pharmacies.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderValidatorTest
```

## Step 3: Add Prepared Line Readiness Keys

Allowed files:

- Create `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/PreparedLineKey.java`
- Update `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/WarehouseSchedulerSnapshot.java`
- Update `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerStateTest.java`

Create:

```java
public record PreparedLineKey(
        String targetOrderId,
        int targetSheetNumber,
        String orderLineNumber,
        DspOrderLineType lineType)
```

Validation rules:

- `targetOrderId` and `orderLineNumber` must not be blank.
- `targetSheetNumber` must be `>= 1`.
- `lineType` must not be null.
- Trim string fields before storing.

Add factories:

```java
public static PreparedLineKey forPreparedLine(DspOrderItem preparedLine)
public static PreparedLineKey forDispatchLine(NotionalToteOrder dispatchOrder, DspOrderItem dispatchLine)
```

Factory rules:

- `forPreparedLine(...)` uses the line's `referenceOrderId`, `referenceSheetNumber`, `itemId`, and `lineType`.
- `forDispatchLine(...)` uses the dispatch order's `orderId`, dispatch order's `sheetNumber`, line `itemId`, and line `lineType`.

Update `WarehouseSchedulerSnapshot`:

- Replace:
  - `Set<String> completedAdaptedNotionalToteIds`
  - `Set<String> manualReadyNotionalToteIds`
- With:
  - `Set<PreparedLineKey> preparedLineKeys`

Rules:

- Copy `preparedLineKeys` defensively.
- Keep `orderStates`, `stationAdmissions`, and `activeServiceCentreId` unchanged.
- Remove old notional-tote readiness accessors and update callers in this branch.

Test methods:

- `shouldCreatePreparedLineKeyFromPreparedLineReferenceFields()`
- `shouldCreatePreparedLineKeyFromDispatchOrderAndLine()`
- `shouldRejectInvalidPreparedLineKeyFields()`
- `shouldDefensivelyCopyPreparedLineKeysInSnapshot()`

Expected output:

- Scheduler snapshots represent prepared adapted/manual readiness at line level.
- The old notional-tote readiness sets are removed from the scheduler snapshot.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerStateTest
```

## Step 4: Update Dependency Evaluation To Use Prepared Lines

Allowed files:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspDependencyEvaluator.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspDependencyEvaluatorTest.java`

Update behavior:

- `ASSOCIATED` and `EMPTY` dependencies are checked per dispatch line.
- A dispatch line with `lineType == ADAPTED` is blocked until `snapshot.preparedLineKeys()` contains `PreparedLineKey.forDispatchLine(candidate.order(), line)`.
- A dispatch line with `lineType == MANUAL` is blocked until `snapshot.preparedLineKeys()` contains `PreparedLineKey.forDispatchLine(candidate.order(), line)`.
- Dispatch lines with `lineType == FULL_PACK` do not require prepared line readiness.
- `FULL_PACK` orders do not require prepared line readiness.
- `ADAPTED` orders do not require prepared line readiness.
- Keep sheet sequencing behavior unchanged.

Dependency block rules:

- Missing adapted lines should produce `DependencyType.ADAPTED_COMPLETION`.
- Missing manual lines should produce `DependencyType.MANUAL_READY`.
- Block reason text should include the target order id, sheet number, and at least one missing line id.
- If multiple adapted/manual lines are missing, one block per dependency type is enough. Do not emit one block per line unless the existing tests make that easier.

Test methods:

- Replace notional-tote readiness tests with:
  - `shouldBlockAssociatedUntilRequiredAdaptedLinesAreReady()`
  - `shouldBlockEmptyUntilRequiredAdaptedLinesAreReady()`
  - `shouldBlockAssociatedUntilRequiredManualLinesAreReady()`
  - `shouldNotBlockWhenAllPreparedLinesAreReady()`
  - `shouldNotRequirePreparedLinesForFullPackDispatchLines()`
  - `shouldNotBlockAdaptedOrFullPackOrdersOnPreparedLineReadiness()`
- Keep existing sheet sequence tests and update snapshot helper arguments.

Expected output:

- Dispatch orders are released only when their required prepared adapted/manual lines are ready.
- Adapted preparation batches can be mixed-pharmacy without falsely marking a whole notional tote complete.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluatorTest
```

## Step 5: Update Scheduler Scenario And Release Tests

Allowed files:

- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspReleaseSchedulerTest.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/DspSchedulerScenarioTest.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/scheduler/ServiceCentreWindowPolicyTest.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/p2p/P2pAdmissionAdapterTest.java`
- Other DSP test helpers only if compilation requires it.

Update tests:

- Replace old snapshot construction with `preparedLineKeys`.
- Update helper methods to create `DspOrderItem` with explicit pharmacy and line type when the test depends on readiness.
- Keep service-centre windowing assertions unchanged.
- Keep release priority unchanged: `ADAPTED`, then `ASSOCIATED`/`EMPTY`, then `FULL_PACK`.
- Add or update one scenario test showing:
  - an `ADAPTED` order contains lines for two pharmacies
  - only one target associated order's prepared line key is ready
  - scheduler releases only the associated order whose required prepared lines are ready
  - a different service centre is still not mixed while the active service centre has waiting work

Expected output:

- Existing scheduler behavior remains intact except readiness is now line-level.
- The service-centre rule and sheet sequencing still work after the readiness model change.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionAdapterTest
```

## Step 6: Update Routing Tests If Needed

Allowed files:

- `app/src/main/java/online/davisfamily/warehouse/sim/dsp/routing/DspRouteDeriver.java`
- `app/src/test/java/online/davisfamily/warehouse/sim/dsp/routing/DspRouteDeriverTest.java`

Expected implementation:

- No production routing behavior should need to change.
- `DspRouteDeriver` should still derive route requirements from product master records and order type.
- Update test helper item construction to use either the old 3-argument constructor or explicit line metadata.

Expected output:

- Product classification remains sourced from product master data, not 12N line type.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriverTest
```

## Step 7: Documentation And Roadmap Update

Allowed files:

- `docs/scheduler/dsp_osr_scheduler_requirements.md`
- `docs/scheduler/dsp-scheduler-implementation-plan.md`
- `docs/scheduler/dsp-scheduler-osr-integration-plan.md`
- `docs/scheduler/dsp-scheduler-line-readiness-plan.md`

Update docs:

- Requirements must state that `pharmacyId` is line-level.
- Requirements must state that `ASSOCIATED`, `EMPTY`, and `FULL_PACK` dispatch orders must be pharmacy-pure.
- Requirements must state that manual tote order examples are pharmacy-pure.
- Requirements must state that `ADAPTED` orders are preparation batches and may contain multiple pharmacies.
- Requirements must state that adapted/manual readiness is line-level and target-order referenced, not notional-tote-level.
- Roadmap must show this branch before OSR integration.
- OSR integration plan must be updated so any runtime snapshot setup uses `preparedLineKeys`, not old notional-tote readiness sets.

Expected output:

- The written architecture matches the corrected scheduler domain.
- OSR integration can proceed after this branch without carrying the old readiness assumption forward.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 8: Branch Closure

Ask user to run the focused DSP suite:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.*
```

Completion criteria:

- DSP scheduler/model/routing/P2P tests pass.
- No tote-to-bag controller, OSR integration, visual fixture, or machine state code has been changed.
- `WarehouseSchedulerSnapshot` no longer exposes notional-tote-level adapted/manual readiness sets.
- Dispatch purity is validated by pharmacy id at line level.
- Mixed-pharmacy adapted preparation batches are explicitly supported.
