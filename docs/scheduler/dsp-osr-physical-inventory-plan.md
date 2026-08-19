# DSP OSR Physical Inventory Plan

Branch: `feature/dsp-osr-physical-inventory`

Status: ready for implementation.

## Purpose

Model the DSP OSR as a configurable, simulation-thread-owned inventory of physical inbound totes and create the deterministic 06:00 preload for Letchworth and Swansea.

This branch must:

- count physical inbound tote manifests rather than logical orders or packs;
- enforce configurable OSR capacity with `1200` as the production baseline;
- preload every retained ADAPTED, FULL_PACK, and ASSOCIATED physical tote for service centres `104` and `108`;
- keep EMPTY orders out of physical occupancy while identifying preload-service-centre EMPTY sheets as logically authorized at startup;
- preserve loaded manifest order and support several physical totes for one logical sheet;
- expose immutable inventory snapshots for later supply, scheduler, inspection, and metrics work;
- record physical OSR departures separately from lifecycle activation and downstream processing;
- coexist with the existing inbound physical tote catalog and lifecycle ledger without duplicating either.

This branch does not implement low-water authorization, rate-limited inbound supply, the operational clock, scheduler candidate filtering, physical tote release commands, station routing, renderables, metrics history, or full-day execution.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
4. `docs/scheduler/dsp-inbound-tote-lifecycle-plan.md`
5. `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
6. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `LoadedDspData`
- `DspDatasetAssembler`
- `InboundToteManifest`
- `InboundToteManifestCatalog`
- `InboundToteLifecycleController`
- `PhysicalToteLifecycleLedger`
- `PhysicalToteLifecycleSnapshot`
- `NotionalToteOrder`
- `OrderSheetKey`
- `OrderType`
- `PhysicalToteId`
- `LoadedDspSchedulerRuntimeFactory`
- `WarehouseSchedulerSnapshot`

## Fixed Decisions

Do not revisit these during implementation:

- OSR occupancy counts `InboundToteManifest` instances, one unit per physical tote.
- Logical orders, order sheets, packs, outbound totes, and EMPTY orders do not directly consume OSR capacity.
- A logical `OrderSheetKey` may own several physical inbound manifests. Inventory membership and departure are therefore tracked by `PhysicalToteId`, not by logical sheet.
- Do not add one mutable sheet-level supply-state enum in this branch. A sheet can become physically mixed when one of several manifests has departed while others remain stored. The later service-centre supply branch must model per-manifest state and derive logical summaries without losing that distinction.
- Startup EMPTY authorization is represented only as an immutable set of `OrderSheetKey`s in the bootstrap result. It creates no physical tote, manifest, lifecycle record, or occupancy.
- The production baseline configuration is capacity `1200` and preload service centres in this order: `104`, then `108`.
- Configuration remains replaceable. Tests may use smaller capacity and different preload service-centre IDs.
- Initial preload selects all retained manifests whose `serviceCentreId` is configured for preload. `InboundToteManifest` already excludes EMPTY.
- Preserve `LoadedDspData.inboundToteManifests()` order. Do not sort by order type, service centre, sheet, physical tote ID, or hash iteration.
- ADAPTED-first ordering applies to later upstream service-centre supply. Do not reorder the already-present startup preload.
- Validate the complete preload against capacity and duplicate identity before publishing or mutating inventory. Never partially preload and then fail.
- An empty configured preload service centre is valid for partial datasets and tests. Do not require every configured service centre to appear.
- A physical tote can enter one OSR inventory only once. After departure it cannot be re-admitted.
- Inventory departure means the tote has physically left OSR. Merely selecting a candidate, evaluating on the scheduler worker, or creating a command must not mutate occupancy.
- `OsrPhysicalInventory.recordDeparture(...)` is the simulation-thread commit operation. A later command-application branch must call it only after downstream release acceptance succeeds.
- Inventory departure does not activate or transition `PhysicalToteLifecycleLedger` automatically. The command application path will coordinate departure with `InboundToteLifecycleController.activate(...)` at the real release boundary.
- The existing lifecycle controller may register every loaded physical inbound tote before it enters OSR. Lifecycle registration means the physical identity is known; it does not mean the tote occupies OSR.
- No locks, worker threads, concurrent collections, wall-clock timestamps, or renderables are added.
- Do not modify `ReleaseOrderCommand`, `DspReleaseScheduler`, `WarehouseSchedulerSnapshot`, or `LoadedDspSchedulerRuntimeFactory` in this branch. Their order-centric release semantics are replaced or extended only when physical OSR release integration is planned.
- Do not use `DspOrderStatus.WAITING` as a substitute for physical OSR membership.
- All returned lists, sets, and maps are immutable and deterministic.

## Package And Vocabulary

Create OSR inventory types under:

```text
online.davisfamily.warehouse.sim.dsp.osr
```

Use these names:

- `OsrInventoryConfig`: physical capacity and startup preload service-centre IDs.
- `OsrInventorySnapshot`: immutable current stored inventory plus ordered departure history.
- `OsrPhysicalInventory`: simulation-thread-owned physical inventory.
- `OsrBootstrapState`: initialized inventory plus logically authorized startup EMPTY sheets.
- `OsrInventoryBootstrapFactory`: creates the all-or-nothing startup state from `LoadedDspData`.

Do not introduce `OsrOrder`, `OsrTote`, or another manifest wrapper. `InboundToteManifest` is already the authoritative physical content record.

## Step 1: Add OSR Inventory Configuration

Create:

```java
public record OsrInventoryConfig(
        int capacity,
        List<String> preloadServiceCentreIds)
```

Rules:

- capacity must be at least one;
- preload service-centre IDs must be non-null, trimmed, nonblank, distinct, and immutable;
- preserve configured ID order;
- an empty preload list is valid;
- expose `public static OsrInventoryConfig productionBaseline()` returning capacity `1200` and `List.of("104", "108")`.

Do not add low-water mark or inbound-rate settings here. They belong to later branches.

Required tests in `OsrInventoryConfigTest`:

- `shouldProvideProductionCapacityAndPreloadServiceCentres()`
- `shouldNormalizeAndPreserveConfiguredServiceCentreOrder()`
- `shouldRejectInvalidCapacityBlankOrDuplicateServiceCentres()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfigTest
```

## Step 2: Add The Immutable Inventory Snapshot

Create `OsrInventorySnapshot` with exactly:

```java
int capacity
List<InboundToteManifest> storedTotes
List<InboundToteManifest> departedTotes
```

Validation:

- capacity is at least one;
- lists are non-null, contain no nulls, and are immutable;
- physical tote IDs are unique across both lists;
- stored tote count does not exceed capacity.

Derived behavior:

```java
int occupancy()
int remainingCapacity()
boolean full()
boolean contains(PhysicalToteId physicalToteId)
boolean hasDeparted(PhysicalToteId physicalToteId)
Optional<InboundToteManifest> findStored(PhysicalToteId physicalToteId)
List<InboundToteManifest> storedTotesFor(OrderSheetKey orderSheetKey)
List<InboundToteManifest> storedTotesForServiceCentre(String serviceCentreId)
Map<String, Integer> occupancyByServiceCentre()
Map<OrderType, Integer> occupancyByOrderType()
```

Rules:

- preserve stored admission order and departure commit order;
- service-centre and order-type maps preserve first occurrence order;
- normalize lookup service-centre IDs and reject null/blank lookup inputs;
- `contains(...)` means currently stored, not historically seen;
- do not expose mutable backing collections.

Required tests in `OsrInventorySnapshotTest`:

- `shouldExposeOccupancyCapacityAndPhysicalToteLookups()`
- `shouldGroupStoredTotesByLogicalSheetServiceCentreAndOrderType()`
- `shouldDistinguishStoredAndDepartedPhysicalTotes()`
- `shouldRejectCapacityDuplicateIdentityOrNullContentViolations()`
- `shouldExposeImmutableDeterministicCollections()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshotTest
```

## Step 3: Implement The Simulation-Owned Physical Inventory

Create `OsrPhysicalInventory` constructed with `OsrInventoryConfig`.

It owns:

- a `LinkedHashMap<PhysicalToteId, InboundToteManifest>` for currently stored totes;
- a `LinkedHashSet<PhysicalToteId>` for every identity ever admitted;
- an ordered list of departed manifests.

Expose:

```java
void store(InboundToteManifest manifest)
void storeAll(List<InboundToteManifest> manifests)
InboundToteManifest recordDeparture(PhysicalToteId physicalToteId)
OsrInventorySnapshot snapshot()
```

Store rules:

- reject null manifests;
- reject any physical ID already stored or previously departed;
- reject capacity overflow;
- preserve input order;
- `storeAll(...)` validates all candidates, duplicates, previously seen IDs, and final capacity before any mutation;
- a failed store operation leaves inventory unchanged.

Departure rules:

- reject null or unknown/not-currently-stored IDs;
- remove exactly one stored manifest;
- append it to departure history;
- decrement occupancy only at this commit operation;
- never modify the manifest or lifecycle ledger;
- a departed identity cannot be stored again.

Required tests in `OsrPhysicalInventoryTest`:

- `shouldStorePhysicalTotesAndPreserveAdmissionOrder()`
- `shouldStoreBatchAtomicallyWithinCapacity()`
- `shouldLeaveInventoryUnchangedWhenBatchWouldOverflowOrDuplicate()`
- `shouldRecordPhysicalDepartureAndFreeOneCapacitySlot()`
- `shouldRejectUnknownDepartureOrReadmissionAfterDeparture()`
- `shouldReturnImmutablePointInTimeSnapshots()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventoryTest
```

## Step 4: Add The Startup Bootstrap Result

Create `OsrBootstrapState` with exactly:

```java
OsrPhysicalInventory inventory
Set<OrderSheetKey> authorizedEmptyOrderSheetKeys
```

Rules:

- reject null inputs;
- defensively copy the EMPTY key set while preserving deterministic insertion order;
- expose `inventorySnapshot()` as a convenience delegating to `inventory.snapshot()`;
- the inventory remains simulation-thread-owned mutable state;
- the EMPTY key set is immutable startup authorization evidence only.

Do not put physical manifest IDs in the EMPTY set and do not create a fake EMPTY manifest.

Required tests in `OsrBootstrapStateTest`:

- `shouldExposeInventoryAndImmutableAuthorizedEmptySheets()`
- `shouldKeepEmptyAuthorizationSeparateFromPhysicalOccupancy()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapStateTest
```

## Step 5: Build The Deterministic 06:00 Preload

Create `OsrInventoryBootstrapFactory` exposing:

```java
OsrBootstrapState create(LoadedDspData data, OsrInventoryConfig config)
```

Algorithm:

1. Validate non-null inputs.
2. Build a membership set from `config.preloadServiceCentreIds()` without changing its order.
3. Select every `data.inboundToteManifests()` entry whose service centre is configured, preserving dataset manifest order.
4. Select every `data.orders()` entry whose service centre is configured and whose `OrderType` is `EMPTY`, preserving logical order order and collecting its `OrderSheetKey`.
5. Validate that the complete physical selection fits configured capacity before creating published state.
6. Create one `OsrPhysicalInventory`, call its atomic `storeAll(...)`, and return `OsrBootstrapState`.

Rules:

- include ADAPTED, FULL_PACK, and ASSOCIATED manifests;
- include all physical manifests for a logical sheet, not only the first;
- do not infer manifests from logical orders;
- do not include nonconfigured service centres;
- do not create lifecycle assignments;
- do not modify `LoadedDspData`;
- capacity overflow fails clearly with selected count and capacity in the message;
- no partially initialized state escapes on failure.

Required tests in `OsrInventoryBootstrapFactoryTest`:

- `shouldPreloadAllPhysicalTotesForLetchworthAndSwansea()`
- `shouldPreserveDatasetManifestOrderAcrossPreloadedServiceCentres()`
- `shouldIncludeSeveralPhysicalTotesForOneLogicalSheet()`
- `shouldAuthorizeEmptyOrdersWithoutIncreasingOccupancy()`
- `shouldLeaveLaterServiceCentresOutsideInitialInventory()`
- `shouldFailClearlyWhenInitialPreloadExceedsCapacity()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryBootstrapFactoryTest
```

## Step 6: Prove Multi-Manifest Logical Sheet Semantics

Add focused scenarios using one logical ASSOCIATED sheet represented by at least two physical inbound manifests.

Verify:

- both physical IDs occupy separate OSR capacity units;
- lookup by logical sheet returns both manifests in source order;
- departing the first physical tote decrements occupancy by one only;
- the second physical tote remains stored and discoverable under the same logical sheet;
- the logical sheet is not treated as wholly departed because one physical tote left;
- departure history identifies only the departed physical manifest;
- no generated logical sheet is created.

Required tests in `OsrMultiManifestInventoryTest`:

- `shouldCountEachPhysicalManifestForOneLogicalSheet()`
- `shouldKeepRemainingManifestStoredAfterSiblingDeparts()`
- `shouldNeverUseLogicalSheetIdentityAsPhysicalInventoryIdentity()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrMultiManifestInventoryTest
```

## Step 7: Prove Inventory And Lifecycle Separation

Create an integration scenario using one `LoadedDspData`, `InboundToteManifestCatalog`, `PhysicalToteLifecycleLedger`, `InboundToteLifecycleController`, and bootstrapped OSR inventory.

Verify:

1. constructing `InboundToteLifecycleController` registers every loaded physical inbound identity, including later service centres outside startup OSR;
2. lifecycle registration creates no active assignment and does not change OSR occupancy;
3. the bootstrap inventory contains only configured preload service-centre manifests;
4. reading inventory or selecting a stored manifest does not alter occupancy;
5. `recordDeparture(...)` removes the physical tote from OSR but does not itself create a lifecycle assignment;
6. calling `InboundToteLifecycleController.activate(...)` at the release boundary creates the expected `INBOUND_PACK` assignment for that same physical tote;
7. another manifest for the same sheet cannot activate concurrently under the existing ledger invariant;
8. EMPTY startup authorization creates neither lifecycle tote nor assignment.

Do not add a combined release controller in this branch. The later physical release-command plan must define downstream acceptance and commit ordering before coordinating inventory departure with lifecycle activation.

Required tests in `OsrLifecycleIntegrationTest`:

- `shouldRegisterLoadedTotesWithoutTreatingAllAsStoredInOsr()`
- `shouldKeepCandidateInspectionFreeOfInventoryMutation()`
- `shouldActivateExactPhysicalToteAfterCommittedOsrDeparture()`
- `shouldPreserveOneActivePhysicalManifestPerLogicalSheet()`
- `shouldKeepAuthorizedEmptyOrderManifestFree()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrLifecycleIntegrationTest
```

## Step 8: Add The Dataset-To-OSR Scenario

Create `DspOsrPhysicalInventoryScenarioTest` under the OSR test package.

Build a deterministic assembled dataset containing:

- ADAPTED, FULL_PACK, ASSOCIATED, and EMPTY work for service centre `104`;
- at least one physical manifest and one EMPTY order for `108`;
- at least one physical manifest and one EMPTY order for a later service centre such as `116`;
- two ASSOCIATED physical manifests sharing one logical sheet;
- enough physical preload manifests to exercise remaining capacity without filling it.

Use `OsrInventoryConfig.productionBaseline()` for the primary scenario and a small-capacity config for overflow validation.

Required assertions:

1. only physical `104` and `108` manifests occupy startup OSR;
2. every selected physical tote consumes exactly one slot;
3. EMPTY orders for `104` and `108` appear only in authorized EMPTY keys;
4. later service-centre manifests and EMPTY sheets remain outside startup state;
5. source manifest order and physical IDs are preserved;
6. logical sheets with multiple physical manifests consume multiple slots;
7. snapshot grouping by service centre and order type is deterministic;
8. no scheduler order status, release command, lifecycle assignment, renderable, or outbound tote is created;
9. a failed undersized preload publishes no usable partial bootstrap state.

Required test methods:

- `shouldBootstrapConfiguredServiceCentresFromAssembledDataset()`
- `shouldCountPhysicalManifestsWithoutCountingEmptyOrdersOrPacks()`
- `shouldPreserveLogicalAndPhysicalIdentityAcrossInitialInventory()`
- `shouldRejectAnOverCapacityInitialDatasetAtomically()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.DspOsrPhysicalInventoryScenarioTest
```

## Step 9: Regression And Branch Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.io.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify existing pack/tote/bag behavior remains unchanged;
- verify `ALT+R` still resets each scene;
- no OSR tote or inventory renderable is expected in this branch.

Before branch closure:

- update this plan status to implementation complete and verified;
- update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and `docs/codex-instructions.md`;
- record any implementation detail that the later supply or physical-release plans must preserve.

## Completion Criteria

- OSR capacity is configurable and the production baseline is `1200` physical totes.
- Startup preload service centres default to Letchworth `104` and Swansea `108`.
- Every retained physical manifest for configured preload service centres occupies one slot.
- EMPTY orders consume no physical slot and their startup authorization is represented separately.
- Several physical manifests for one logical sheet remain distinct inventory entries.
- Initial preload is deterministic, immutable when observed, and atomic on failure.
- Current occupancy, remaining capacity, full state, service-centre counts, order-type counts, and physical lookup are available through an immutable snapshot.
- Occupancy decreases only through explicit simulation-thread departure commit.
- Departed physical identities cannot be admitted again.
- Inventory state remains separate from lifecycle registration/assignment and scheduler order status.
- No low-water authorization, rate-limited supply, operational clock, release-command redesign, scheduler policy, renderable, metric history, database, or new thread is introduced.
- Focused tests, complete tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, create the detailed plan for:

```text
feature/dsp-operational-simulation-clock
```

That branch will add the configurable 06:00 operating clock, accelerated deterministic time mapping, explicit day offsets, normal target and hard-cutoff representation, and clock snapshots without yet implementing rate-limited service-centre supply.
