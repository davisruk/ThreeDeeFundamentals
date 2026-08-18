# DSP Inbound Tote Lifecycle Plan

Branch: `feature/dsp-inbound-tote-lifecycle`

Status: ready for implementation.

## Purpose

Connect logical DSP order sheets to their physical inbound totes without conflating `orderId` and `transportContainer`.

This branch must:

- retain 12N `transportContainer` as a typed `PhysicalToteId`;
- represent one physical 12N carrier as an `InboundToteManifest`;
- allow a logical `OrderSheetKey` to have several source manifests while preserving the one-active-assignment invariant;
- keep EMPTY logical and manifest-free before AV02;
- register and advance inbound physical totes through the lifecycle ledger;
- provide logical admission profiles separately from physical station visits;
- ensure actual Adapting, Third Party, and tote-load-plan work uses explicit physical tote identity;
- preserve all existing local machine-state and scheduler-thread boundaries.

This branch does not implement outbound bags/totes or OSR replenishment policy.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`
4. `docs/scheduler/dsp-logical-physical-identity-plan.md`
5. `docs/machines/adapting-station-phase-1-plan.md`
6. `docs/machines/third-party-station-phase-1-plan.md`

Use the message examples under `docs/message-examples` when verifying 12N field semantics.

## Current Problem

`TwelveNMessageJson` already contains `transportContainer`, but `TwelveNOrderMapper` discards it and copies `orderId` into `NotionalToteOrder.notionalToteId`.

That copied value currently reaches some station fixtures and tote-load plans. It incorrectly suggests that the logical order ID is the physical carrier ID.

The corrected model is:

```text
12N message
  -> logical order sheet (`OrderSheetKey`)
  -> optional physical inbound manifest (`PhysicalToteId`)

ADAPTED / ASSOCIATED / FULL_PACK -> manifest required
EMPTY                            -> no inbound manifest
```

Admission evaluation may inspect logical work before a physical tote is selected. Actual station visits and tote load plans must use the selected physical ID. Do not create fake physical IDs merely to reuse a station-visit type during admission.

## Fixed Decisions

Do not revisit these during implementation:

- Keep `NotionalToteOrder` as the current logical order-sheet class in this branch. A broad class rename is not part of inbound lifecycle behavior.
- Treat `notionalToteId` as transitional compatibility data only. New or modified production code must not use it as a physical tote ID.
- `InboundToteManifest` is separate from `NotionalToteOrder`; do not add `PhysicalToteId` as a component of the logical order record.
- ADAPTED, ASSOCIATED, and FULL_PACK 12Ns require a nonblank `transportContainer.payload`.
- EMPTY always maps without an inbound manifest. Do not interpret any EMPTY `transportContainer` placeholder as a physical tote.
- MANUAL messages remain excluded from active simulation and create no retained manifest.
- A manifest contains only retained, simulated lines from its physical source tote.
- Multiple manifests may reference one `OrderSheetKey`, but the lifecycle ledger continues to allow at most one active assignment for that key.
- The dataset loader may retain several manifests for one logical sheet. It must combine their logical lines into one scheduler order-sheet record in stable source order.
- Duplicate physical tote IDs are invalid within one loaded dataset.
- Duplicate line references within one combined logical sheet are invalid.
- OSR state decides which retained manifest is physically stored/released later. This branch does not mark every loaded manifest active at startup.
- The inbound lifecycle controller registers manifests but activates a physical tote only when explicitly commanded on the simulation thread.
- AV02 allocates a new `PRE_P2P` physical tote for EMPTY; it does not create an inbound manifest.
- Underlying generic routing/render classes may still carry the physical ID as a string. Conversion from `PhysicalToteId` to `value()` is allowed only at such an existing generic boundary.
- Scheduler workers continue to receive immutable state and never mutate the lifecycle ledger, station areas, load plans, or renderables.
- Use `Duration` for simulation-relative lifecycle times, matching the completed identity branch.

## Explicit Non-Goals

- OSR capacity, preload, low-water authorization, or rate-limited supply;
- choosing which of several stored manifests the operational scheduler releases;
- changing the current scheduler profile or service-centre policy;
- prescription/patient mapping and bag planning;
- completed bag records or pack-to-bag provenance;
- outbound tote reservoirs, allocation, purity, closure, or generated output sheets;
- detailed AV02 geometry or empty-tote reservoir animation;
- P2P outbound substitution and Exception Station behavior;
- 32R generation;
- a database, persistence, or another thread;
- renaming `NotionalToteOrder` across the whole codebase.

## Implementation Vocabulary

Use these names:

- `InboundToteManifest`: immutable physical contents and logical-sheet association from one 12N.
- `InboundToteManifestCatalog`: immutable deterministic lookup by physical tote and logical sheet.
- `MappedTwelveNOrder`: one mapped logical order-sheet contribution plus its optional inbound manifest.
- `InboundToteLifecycleController`: simulation-thread facade over the lifecycle ledger for source totes.
- `PhysicalToteIdAllocator`: injected physical-ID source for AV02.
- `Av02ToteLifecycleController`: allocates and assigns a PRE_P2P tote to eligible EMPTY work.
- `AdaptingVisitProfile`: logical work needed for admission/bench selection before physical visit creation.
- `ThirdPartyVisitPlan`: logical Third Party work needed for admission before physical visit creation.

## Step 1: Add Inbound Manifest Domain Types

Create under `online.davisfamily.warehouse.sim.dsp.lifecycle`:

- `InboundToteManifest.java`
- `InboundToteManifestCatalog.java`

`InboundToteManifest` must be an immutable record containing:

```java
PhysicalToteId physicalToteId
OrderSheetKey orderSheetKey
OrderType orderType
String serviceCentreId
List<DspOrderItem> items
long sourceSequenceNumber
```

Validation:

- reject null identity/type/list values;
- trim and reject blank `serviceCentreId`;
- reject `OrderType.EMPTY` because EMPTY has no inbound manifest;
- reject an empty item list;
- reject null items;
- reject duplicate `lineReference` values inside the manifest;
- reject negative `sourceSequenceNumber`;
- defensively copy items.

Provide:

```java
public InboundToteManifest withItems(List<DspOrderItem> retainedItems)
```

It retains all identity/metadata fields and revalidates the replacement items.

`InboundToteManifestCatalog` must:

- accept a list and retain insertion order;
- reject null manifests and duplicate `PhysicalToteId`s;
- allow several manifests for one `OrderSheetKey`;
- expose immutable methods:

```java
public List<InboundToteManifest> manifests()
public Optional<InboundToteManifest> findByPhysicalToteId(PhysicalToteId toteId)
public List<InboundToteManifest> manifestsFor(OrderSheetKey orderSheetKey)
```

Create `InboundToteManifestTest`.

Required tests:

- `shouldRetainPhysicalToteAndLogicalSheetAsDifferentIdentities()`
- `shouldRejectEmptyManifestAndDuplicateLineReferences()`
- `shouldRejectEmptyOrderTypeManifest()`
- `shouldReplaceItemsWithoutChangingManifestIdentity()`
- `shouldCatalogSeveralPhysicalTotesForOneLogicalSheet()`
- `shouldRejectDuplicatePhysicalToteIds()`
- `shouldDefensivelyCopyManifestCollections()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestTest
```

## Step 2: Retain Transport Container During 12N Mapping

Create `MappedTwelveNOrder` under `online.davisfamily.warehouse.sim.dsp.io`:

```java
public record MappedTwelveNOrder(
        NotionalToteOrder order,
        Optional<InboundToteManifest> inboundToteManifest)
```

Validation:

- reject null order/optional;
- EMPTY requires an empty optional;
- ADAPTED, ASSOCIATED, and FULL_PACK require a manifest;
- manifest `orderSheetKey`, `orderType`, `serviceCentreId`, `items`, and source sequence must match the logical order contribution.

Replace `TwelveNOrderMapper.toOrder(...)` with:

```java
public MappedTwelveNOrder map(TwelveNMessageJson message, long sourceSequenceNumber)
```

Do not retain a production mapper method that silently returns only `NotionalToteOrder` and discards physical identity.

Mapping rules:

- continue mapping logical order fields and lines as today;
- the transitional `notionalToteId` component remains `orderId` only for compatibility and must not be described as physical;
- for ADAPTED, ASSOCIATED, and FULL_PACK:
  - require `message.transportContainer()`;
  - require and trim `transportContainer.payload`;
  - create `PhysicalToteId` from that payload;
  - create a matching `InboundToteManifest`;
- for EMPTY:
  - do not require `transportContainer`;
  - return `Optional.empty()` even if a placeholder field is supplied;
- MANUAL continues to throw if passed directly to this mapper;
- never derive a physical ID from `orderId`, sheet number, tote type, or sequence number.

Update `TwelveNOrderMapperTest` fixtures so physical order types include explicit `transportContainer` data.

Required tests:

- `shouldMapFullPackLogicalOrderAndPhysicalTransportContainer()`
- `shouldMapAssociatedAndAdaptedPhysicalTransportContainers()`
- `shouldKeepOrderIdDistinctFromTransportContainer()`
- `shouldMapEmptyWithoutInboundManifest()`
- `shouldIgnoreEmptyTransportContainerPlaceholder()`
- `shouldRejectMissingTransportContainerForPhysicalInboundOrder()`
- preserve existing line mapping, validation, and unknown-type tests.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapperTest
```

## Step 3: Retain Manifests And Group Logical Sheets In Loaded Data

Change `DspDatasetAssembler` to consume `MappedTwelveNOrder` and add retained manifests to loaded data.

Change `LoadedDspData` to contain:

```java
List<InboundToteManifest> inboundToteManifests
```

Add the component before `DspDatasetLoadReport report`. Update compatibility constructors explicitly; do not silently infer manifests from orders.

Assembly rules:

- MANUAL messages remain ignored before physical mapping and create no manifest;
- filter MANUAL lines from both logical order contribution and its manifest;
- if no simulated lines remain, omit both the logical contribution and its manifest;
- retain one manifest per retained physical source 12N;
- build an `InboundToteManifestCatalog` before returning so duplicate physical IDs fail during assembly;
- group logical order contributions by `OrderSheetKey` in first-seen order;
- one grouped logical sheet retains the first contribution's service centre, order type, and logical sequence number;
- later contributions for that key must have the same service centre and order type;
- concatenate retained items in manifest/source order;
- reject duplicate line references within the grouped logical sheet;
- run `DspOrderValidator` against the final grouped order rather than each partial contribution;
- retain prepared-line keys and unresolved-product reporting from all retained lines;
- `orders()` contains one logical order record per `OrderSheetKey`;
- `inboundToteManifests()` may contain several entries for that key;
- EMPTY contributes a logical order but no manifest.

Do not activate lifecycle assignments while loading data.

Update all assembler/runtime test fixtures for physical 12Ns to include transport containers.

Required tests:

- `shouldRetainInboundManifestAlongsideLogicalOrder()`
- `shouldGroupSeveralInboundManifestsIntoOneLogicalSheet()`
- `shouldPreserveManifestSpecificItemsAndCombinedLogicalItems()`
- `shouldRejectConflictingMetadataForSameLogicalSheet()`
- `shouldRejectDuplicateLineReferencesAcrossManifests()`
- `shouldRejectDuplicatePhysicalToteIds()`
- `shouldKeepEmptyOrderManifestFree()`
- `shouldRemoveIgnoredManualLinesFromManifestAndLogicalOrder()`
- preserve existing unresolved-product and prepared-line reporting tests.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest --tests online.davisfamily.warehouse.sim.dsp.io.LoadedDspSchedulerRuntimeFactoryTest
```

## Step 4: Add The Inbound Tote Lifecycle Controller

Create `InboundToteLifecycleController` under `online.davisfamily.warehouse.sim.dsp.lifecycle`.

Constructor:

```java
public InboundToteLifecycleController(
        PhysicalToteLifecycleLedger ledger,
        InboundToteManifestCatalog catalog)
```

Constructor rules:

- reject null arguments;
- register every catalog manifest as `PhysicalToteRecord.inboundPack(...)`;
- fail clearly if the supplied ledger already contains the same physical tote;
- do not create assignments merely because data is loaded.

Required methods:

```java
public PhysicalToteAssignment activate(
        PhysicalToteId toteId,
        Duration activationTime)

public PhysicalToteAssignment advanceToPreP2p(
        PhysicalToteId toteId,
        Duration transitionTime)

public PhysicalToteRecord consumeAtAdapting(
        PhysicalToteId toteId,
        Duration consumptionTime)

public PhysicalToteRecord consumeAtP2p(
        PhysicalToteId toteId,
        Duration consumptionTime)

public PhysicalToteLifecycleSnapshot snapshot()
```

Behavior:

- `activate` finds the manifest and assigns `INBOUND_PACK` to its `OrderSheetKey`;
- activation does not change `INBOUND_PACK_TOTE` state;
- `advanceToPreP2p` terminates the active inbound assignment with `ADVANCED_TO_NEXT_STAGE`, transitions the tote to `ACTIVE_PRE_P2P`, and assigns `PRE_P2P` at the same simulation time;
- `consumeAtAdapting` is allowed only for ADAPTED manifests with an active assignment;
- it terminates the active assignment using `CONSUMED_AT_ADAPTING` and transitions the tote to `CONSUMED_AT_ADAPTING`;
- `consumeAtP2p` is allowed only for ASSOCIATED or FULL_PACK manifests already in `ACTIVE_PRE_P2P`;
- it terminates the active assignment using `CONSUMED_AT_P2P` and transitions the tote to `CONSUMED_AT_P2P`;
- lifecycle operations are simulation-thread mutations and are not synchronized;
- if several manifests share a logical sheet, activating a later manifest while another remains active must fail through the ledger invariant;
- once the first assignment terminates, the next manifest may activate.

If a multi-operation method cannot complete, validate all preconditions before the first mutation. Do not leave the ledger half-transitioned after a predictable validation failure.

Create `InboundToteLifecycleControllerTest`.

Required tests:

- `shouldRegisterManifestsWithoutActivatingThem()`
- `shouldActivateAndAdvanceInboundToteToPreP2p()`
- `shouldConsumeAdaptedToteAtAdapting()`
- `shouldConsumeAssociatedAndFullPackTotesAtP2p()`
- `shouldRejectWrongOrderTypeConsumption()`
- `shouldPreventTwoManifestsForOneSheetBeingActiveTogether()`
- `shouldAllowNextManifestAfterEarlierManifestTerminates()`
- `shouldNotPartiallyMutateOnRejectedTransition()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleControllerTest
```

## Step 5: Add The EMPTY AV02 Physical Allocation Boundary

Create under `online.davisfamily.warehouse.sim.dsp.lifecycle`:

- `PhysicalToteIdAllocator.java`
- `Av02ToteLifecycleController.java`

`PhysicalToteIdAllocator` is a functional interface:

```java
PhysicalToteId nextPhysicalToteId();
```

`Av02ToteLifecycleController` constructor:

```java
public Av02ToteLifecycleController(
        PhysicalToteLifecycleLedger ledger,
        PhysicalToteIdAllocator idAllocator)
```

Required method:

```java
public PhysicalToteRecord allocateFor(
        NotionalToteOrder order,
        Duration allocationTime)
```

Rules:

- accept only `OrderType.EMPTY`;
- request one non-null physical ID from the allocator;
- register `PhysicalToteRecord.preP2p(id)`;
- create a `PRE_P2P` assignment to `order.orderSheetKey()`;
- return the registered physical tote record;
- reject allocation when the logical sheet already has an active assignment;
- allocator IDs must obey normal duplicate-ID ledger validation;
- do not create `InboundToteManifest`;
- do not model AV02 queues, geometry, renderables, or scheduler admission here.

Before mutating the ledger, perform these checks in order:

1. validate the order and require `OrderType.EMPTY`;
2. validate that `allocationTime` is non-null and non-negative;
3. require `ledger.activeAssignmentFor(order.orderSheetKey())` to be empty;
4. request the physical ID and reject a null result;
5. require `ledger.tote(allocatedId)` to be empty.

Only then register and assign the tote. Do not add rollback machinery.

Create `Av02ToteLifecycleControllerTest`.

Required tests:

- `shouldAllocatePreP2pPhysicalToteForEmptyOrder()`
- `shouldCreateAssignmentWithoutInboundManifest()`
- `shouldRejectNonEmptyOrderType()`
- `shouldRejectSecondActiveAllocationForSameLogicalSheet()`
- `shouldRejectNullOrDuplicateAllocatedPhysicalId()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleControllerTest
```

## Step 6: Separate Logical Admission Profiles From Physical Station Visits

Correct Adapting and Third Party so scheduler admission no longer needs a fabricated tote identity.

### Adapting

Create `AdaptingVisitProfile` as an immutable record containing exactly:

```java
AdaptingVisitType visitType
List<DspOrderItem> preparedLines
List<PreparedLineKey> requestedLineKeys
List<String> pharmacyIds
```

Move the existing STORE/COLLECT content validation from `AdaptingVisit` into this profile.

Create `AdaptingVisitFactory` with:

```java
public AdaptingVisitProfile profileFor(NotionalToteOrder order)
public AdaptingVisit create(PhysicalToteId physicalToteId, NotionalToteOrder order)
```

Rules:

- preserve existing STORE/COLLECT validation;
- FULL_PACK still never collects adapted lines;
- delete `AdaptingCollectVisitFactory` after all callers migrate; do not keep two competing logical planning paths;
- change `AdaptingVisit` to contain exactly `PhysicalToteId physicalToteId` and `AdaptingVisitProfile profile`;
- provide delegate accessors only where existing area/controller code genuinely needs `visitType`, prepared lines, requested keys, or pharmacy IDs; do not duplicate those collections as fields;
- use the profile for scheduler admission and bench selection;
- use the physical visit only when the tote is actually accepted into live station state;
- change `AdaptingArea.selectBenchFor(...)` to accept `AdaptingVisitProfile`; live enqueue paths pass `visit.profile()`;
- change `AdaptingStationAdmissionAdapter` to build a profile through `AdaptingVisitFactory.profileFor(...)` and never create an `AdaptingVisit`;
- migrate Adapting area/controller maps and completion lookup to `PhysicalToteId` where they represent physical totes.

### Third Party

Create `ThirdPartyVisitPlan` as an immutable record containing exactly:

```java
OrderSheetKey orderSheetKey
OrderType orderType
List<ThirdPartyLineWork> lineWork
```

It contains no physical tote ID and owns the existing nonempty line-work validation.

Change `ThirdPartyVisitFactory` to provide:

```java
public Optional<ThirdPartyVisitPlan> planFor(NotionalToteOrder order)
public Optional<ThirdPartyVisit> create(
        PhysicalToteId physicalToteId,
        NotionalToteOrder order)
```

Rules:

- preserve the existing line-selection matrix exactly;
- change `ThirdPartyVisit` to contain exactly `PhysicalToteId physicalToteId` and `ThirdPartyVisitPlan plan`;
- provide delegate accessors only for values existing area/controller code genuinely consumes;
- scheduler admission uses plan presence and area capacity, not a fake visit;
- change `ThirdPartyStationAdmissionAdapter.admissionFor(...)` to accept `Optional<ThirdPartyVisitPlan>`;
- change `ThirdPartyStationAdmissionResolver` to call `visitFactory.planFor(candidate.order())`;
- live area/controller state and completion lookup use physical tote identity;
- retain logical `OrderSheetKey` in the plan/visit for inspection and future provenance.

Update debug fixtures to create explicit `PhysicalToteId` values. A fixture may keep the same visible text as before, but it must instantiate that text as a physical ID explicitly rather than obtaining it from `orderId` or `notionalToteId`.

Required tests:

- `shouldCreateAdaptingAdmissionProfileWithoutPhysicalTote()`
- `shouldCreateAdaptingVisitWithExplicitPhysicalTote()`
- `shouldCreateThirdPartyPlanWithoutPhysicalTote()`
- `shouldCreateThirdPartyVisitWithExplicitPhysicalTote()`
- `shouldKeepLogicalOrderAndPhysicalToteDistinctInStationCompletion()`
- existing Adapting and Third Party area, scheduler-admission, and integration tests remain green.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.*
```

## Step 7: Make Tote Load Plans Explicitly Physical

Change `ToteLoadPlan` to store:

```java
PhysicalToteId physicalToteId
```

Provide the primary constructor:

```java
public ToteLoadPlan(PhysicalToteId physicalToteId, List<PackPlan> packPlans)
```

Retain a transitional string constructor only for generic/non-DSP fixtures:

```java
public ToteLoadPlan(String toteId, List<PackPlan> packPlans)
```

It must delegate immediately to `new PhysicalToteId(toteId)`. Do not retain a second string field.

Provide:

```java
public PhysicalToteId physicalToteId()
```

Keep `getToteId()` temporarily as a generic compatibility bridge returning `physicalToteId.value()`. Mark it deprecated for later migration; do not remove it while route/tipper APIs still require strings.

Rules:

- `withAdditionalPackPlans(...)` preserves the typed physical ID;
- change `MapBackedToteLoadPlanRegistry` to key internally by `PhysicalToteId`;
- add `ToteLoadPlan getLoadPlanFor(PhysicalToteId physicalToteId)` to `MutableToteLoadPlanRegistry` while retaining the current `ToteLoadPlanProvider` string bridge;
- the string bridge creates `new PhysicalToteId(toteId)` and delegates to the typed lookup;
- `MapBackedToteLoadPlanRegistry.putLoadPlan(...)` keys by `toteLoadPlan.physicalToteId()`;
- update `ScheduledTipperToteRelease.createPayload()` to create the payload once, compare `payload.getTote().getId()` with `toteLoadPlan.physicalToteId().value()`, and throw a clear `IllegalStateException` on mismatch before returning the payload;
- do not invoke the payload factory during `ScheduledTipperToteRelease` construction or catalog construction;
- actual Adapting/Third Party completion updates must look up load plans using visit `PhysicalToteId`;
- no load plan may be created from `order.orderId()` or `order.notionalToteId()` in modified DSP production code;
- fixture code must declare separate logical order and physical tote constants even when their sample text happens to match.

Do not change PRL/PCR/bagger behavior or pack correlation semantics.

Required tests:

- `shouldStoreTypedPhysicalToteIdentityInLoadPlan()`
- `shouldPreservePhysicalIdentityWhenAddingPackPlans()`
- `shouldBridgeExistingStringProviderAtGenericBoundary()`
- `shouldUpdateAdaptingAndThirdPartyPlansByPhysicalToteId()`
- `shouldRejectScheduledReleaseWhenPayloadAndPlanToteIdsDiffer()`
- existing tote-to-bag plan, queue, scheduled release, and debug fixture tests remain green.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.plan.* --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.*
```

## Step 8: Add The Inbound Lifecycle Scenario Regression

Create `InboundPhysicalToteLifecycleScenarioTest` under the lifecycle test package.

Build a domain/data scenario containing:

- one ADAPTED physical inbound tote;
- one FULL_PACK physical inbound tote;
- one ASSOCIATED logical sheet represented by two inbound manifests with different physical IDs and nonoverlapping lines;
- one EMPTY logical order with no inbound manifest;
- explicit physical station visits/load plans for the active totes.

Verify:

1. all physical 12Ns retain their real `transportContainer` values;
2. logical `orderId/sheetNumber` values remain distinct from physical IDs;
3. the two ASSOCIATED manifests group into one logical scheduler order sheet;
4. the manifests retain their own physical lines;
5. no assignment exists merely because data was loaded;
6. the first ASSOCIATED tote can activate, advance, and be consumed at P2P;
7. the second tote cannot activate concurrently but can activate after the first terminates;
8. the ADAPTED tote terminates at Adapting;
9. EMPTY has no inbound manifest and receives a PRE_P2P physical tote only through AV02 allocation;
10. station visits and tote load plans use physical IDs, not logical order IDs;
11. lifecycle snapshot history retains the source manifest assignments after physical consumption;
12. no outbound physical tote or bag is created in this branch.

Required test methods:

- `shouldLoadLogicalSheetsAndDistinctInboundPhysicalTotes()`
- `shouldProcessSeveralInboundTotesForOneLogicalSheetSequentially()`
- `shouldConsumeAdaptedAndFulfilmentTotesAtTheirCorrectBoundaries()`
- `shouldAllocateEmptyPhysicalToteOnlyAtAv02()`
- `shouldUsePhysicalIdentityForStationVisitsAndLoadPlans()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundPhysicalToteLifecycleScenarioTest
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.io.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.*
```

After focused tests pass, ask the user to run the complete Gradle suite. Run the Adapting and Third Party debug scenes as visual smoke tests because fixture identities and load-plan lookup paths changed. Visual behavior should remain otherwise unchanged.

## Completion Criteria

- Physical inbound 12Ns retain `transportContainer` as `PhysicalToteId`.
- EMPTY loads without an inbound physical manifest.
- Logical order sheets and physical manifests are separate domain records.
- Several manifests can belong to one logical sheet without concurrent active assignment.
- Loaded logical orders are grouped by `OrderSheetKey` and physical manifest contents remain distinct.
- Loading data does not activate every physical tote.
- Inbound lifecycle activation, pre-P2P transition, Adapting consumption, and P2P consumption are explicit and recorded.
- AV02 creates PRE_P2P physical identity for EMPTY without creating an inbound manifest.
- Scheduler admission uses logical profiles rather than fabricated physical station visits.
- Live Adapting and Third Party visits use explicit `PhysicalToteId`.
- Tote load plans store explicit physical identity and retain only a generic string bridge where required.
- Modified production code no longer obtains physical station/load-plan identity from `orderId` or `notionalToteId`.
- Existing scheduler threading and local machine-state behavior remain unchanged.
- Outbound bags, outbound totes, OSR policy, generated sheets, Exceptions, and 32R remain deferred.

## Follow-On Branch

After this branch is green and merged, create the detailed plan for:

```text
feature/dsp-bag-planning-provenance
```

That branch will retain patient/prescription identity, create deterministic bag plans, and preserve pack/source-line provenance without yet allocating outbound physical totes.
