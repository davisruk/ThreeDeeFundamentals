# DSP Bag Planning And Provenance Plan

Branch: `feature/dsp-bag-planning-provenance`

Status: implementation complete and verified; pending merge to `master`.

Verification completed:

- focused Step 1-8 tests are green;
- focused branch regression coverage and the complete test suite are green;
- the Adapting, Third Party, and integrated tote-to-bag/P2P scenes passed visual checks;
- `ALT+R` reset remains correct in each checked scene.

The deprecated eight-argument `DspOrderItem` constructor is intentionally retained as a transitional fixture bridge. It creates line-specific placeholder patient and prescription identities so legacy fixtures cannot accidentally combine unrelated lines into one bag. Modified DSP production paths use the full constructor with real 12N identity.

## Purpose

Retain patient and prescription identity from 12N, create deterministic pack-count-based bag plans, and make every planned physical pack traceable to its source logical line.

This branch must:

- retain trimmed `patientId` and `prescriptionId` on every mapped simulated order line;
- introduce typed `BagKey` identity using prescription plus deterministic ordinal;
- preserve immutable source-line provenance independently from machine-local pack state;
- retain the source ADAPTED order sheet while lines are stored and later collected;
- register provenance whenever current DSP station code creates a physical `PackPlan`;
- group actual physical packs by prescription with configurable pack-count capacity;
- emit P2P-compatible tote load plans whose existing correlation strings represent planned `BagKey` values;
- preserve existing tipper, sorter, PDC, PRL, PCR, bagger, scheduler-thread, and renderable behavior.

This branch does not allocate outbound physical totes or generated output sheets.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`
4. `docs/scheduler/dsp-inbound-tote-lifecycle-plan.md`
5. `docs/machines/adapting-station-phase-1-plan.md`
6. `docs/machines/third-party-station-phase-1-plan.md`

Inspect these existing classes before each affected step:

- `DspOrderItem`
- `TwelveNLineMappingSupport`
- `AdaptedLineRecord`
- `AdaptingVisitProfile`
- `DefaultCollectedPackPlanFactory`
- `ThirdPartyLineWork`
- `ProductMasterThirdPartyPackPlanFactory`
- `PackPlan`
- `ToteLoadPlan`
- `ToteToBagBatchPlan`

## Current Problem

The 12N JSON model contains patient and prescription fields, but `DspOrderItem` and `TwelveNLineMappingSupport` discard them. Existing `PackPlan.correlationId` values are fixture- or station-specific strings such as line references, order IDs, and arbitrary bag IDs. They do not represent a stable bag-planning contract.

Physical pack IDs survive machine movement, but there is no authoritative record connecting a pack ID to:

- its source order sheet;
- line reference and product;
- pharmacy, patient, and prescription;
- its input physical tote;
- its planned bag.

Do not solve this by putting DSP-specific mutable ownership fields into generic render or machine objects. Preserve source provenance in a DSP registry keyed by stable physical pack ID, and publish immutable planned traces when bag planning occurs.

## Fixed Decisions

Do not revisit these during implementation:

- `prescriptionId` is the bag-grouping identity.
- `patientId` is retained for later best-effort outbound-tote affinity; it does not affect bag capacity in this branch.
- `BagKey` is prescription ID plus a one-based bag ordinal.
- Bag ordinals restart at one for each prescription and are deterministic.
- Phase 1 bag capacity is maximum physical pack count, behind a replaceable policy interface.
- Planning uses actual `PackPlan` entries. It must not create physical packs from requested line quantity.
- A missing or incomplete logical line is not represented by a fake `PackPlan`.
- Short-pick outcomes, NS indicators, empty NS bags, and Exception routing remain deferred.
- Source provenance is immutable. Current input tote and planned bag are recorded in an immutable planning result, not by mutating the source record.
- `PackPlan`, `Pack`, and existing machine-state objects remain generic. Do not add patient, prescription, logical sheet, or mutable container fields to them.
- Existing three-argument `PackPlan` construction remains valid.
- Existing P2P grouping still uses the string `correlationId` boundary. Planned DSP load plans use `BagKey.correlationId()` at that boundary.
- Do not parse arbitrary existing correlation strings as bag keys. The planning result/catalog is the authoritative correlation-to-`BagKey` lookup.
- One prescription must resolve to one pharmacy, patient, and service centre within one planning request. Reject conflicting data clearly.
- Packs for one prescription may belong to several logical sheets and input physical totes.
- Preserve request tote order and pack order. Do not sort by hash-map iteration, pack ID, or dimensions.
- No outbound `PhysicalToteId`, `OUTBOUND_BAG` record, sheet allocation, bag receiver replacement, or P2P-line reservoir is created here.
- No new thread, database, renderable, or scheduler profile is introduced.

## Implementation Vocabulary

Create the new bag-planning types under:

```text
online.davisfamily.warehouse.sim.dsp.bagging
```

Use these names:

- `BagKey`: typed prescription plus ordinal identity.
- `PackSourceProvenance`: immutable source logical-line facts for one physical pack.
- `PackProvenanceRegistry`: simulation-thread-owned source lookup keyed by physical pack ID.
- `PackProvenanceSnapshot`: immutable lookup snapshot.
- `BagPlanningTote`: one fulfilment sheet and one physical input tote load.
- `BagPlanningRequest`: deterministic ordered planning input.
- `BagCapacityPolicy`: replaceable capacity decision.
- `MaximumPackCountBagCapacityPolicy`: Phase 1 implementation.
- `PlannedBag`: immutable planned bag and contained physical pack IDs.
- `PlannedPackTrace`: joined source, input tote, fulfilment sheet, and bag identity.
- `BagPlanningResult`: planned bags, rewritten P2P tote loads, and traces.
- `DeterministicBagPlanner`: pure bag-planning service.

## Step 1: Retain Patient And Prescription Identity

Change `DspOrderItem` to add these record components after `pharmacyId`:

```java
String patientId
String prescriptionId
```

Rules:

- trim and reject null/blank values in the compact constructor;
- update the primary constructor order consistently across production callers;
- retain the current eight-argument constructor as a transitional fixture bridge;
- the fixture bridge must derive line-specific placeholder values so unrelated fixture lines never collapse into one bag;
- mark that bridge deprecated and do not use it in modified DSP production code;
- retain the existing three-argument generic fixture constructor through the bridge;
- update `TwelveNLineMappingSupport.toItems(...)` to require and map `patientId` and `prescriptionId` from each 12N line;
- keep MANUAL message exclusion unchanged;
- update mapper/assembler tests and the four message-example expectations.

Do not add patient or prescription to `NotionalToteOrder`; both are line-level.

Required tests:

- `shouldRetainPatientAndPrescriptionIdentityFromTwelveN()`
- `shouldTrimPatientAndPrescriptionIdentity()`
- `shouldRejectMissingPatientOrPrescriptionIdentity()`
- `shouldKeepDifferentPrescriptionsDistinctWithinOneLogicalSheet()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.* --tests online.davisfamily.warehouse.sim.dsp.io.*
```

## Step 2: Add Typed Bag And Source-Provenance Domain

Create `BagKey` as:

```java
public record BagKey(String prescriptionId, int bagOrdinal)
```

Rules:

- trim and reject blank prescription IDs;
- reject ordinals below one;
- expose `correlationId()` using one documented stable format;
- use `prescriptionId + "/bag-" + bagOrdinal` as that format;
- do not add a parser for arbitrary legacy correlations.

Create `PackSourceProvenance` with exactly:

```java
OrderSheetKey sourceOrderSheetKey
String lineReference
String productId
String serviceCentreId
String pharmacyId
String patientId
String prescriptionId
```

Validate and trim every string. This record describes source facts only; it does not contain a current tote or bag.

Create `PackProvenanceRegistry`:

- key internally by physical `packId` string;
- `register(String packId, PackSourceProvenance provenance)` rejects blank IDs and conflicting duplicate registration;
- identical repeat registration is idempotent to support exactly-once station replay safeguards;
- `find(String packId)` returns `Optional<PackSourceProvenance>`;
- `snapshot()` returns an immutable `PackProvenanceSnapshot`;
- registry mutation is simulation-thread-owned; add no synchronization.

`PackProvenanceSnapshot` must defensively copy its map and expose deterministic read-only lookup/iteration.

Required tests:

- `shouldCreateDeterministicBagCorrelationFromPrescriptionAndOrdinal()`
- `shouldRejectInvalidBagIdentity()`
- `shouldRegisterAndSnapshotPackSourceProvenance()`
- `shouldAllowIdenticalRegistrationButRejectConflictingPackProvenance()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.BagKeyTest --tests online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistryTest
```

## Step 3: Preserve ADAPTED Source Sheet Through Storage

ADAPTED lines move from a preparation order into a later ASSOCIATED order. Their source sheet must survive that movement.

Add `OrderSheetKey orderSheetKey` and `String serviceCentreId` to `AdaptingVisitProfile`. `AdaptingVisitFactory.profileFor(order)` supplies both from the logical order. Admission still uses the profile and no physical tote ID.

Change `AdaptedLineRecord` to include:

```java
OrderSheetKey sourceOrderSheetKey
String sourceServiceCentreId
```

Populate both fields from the active STORE `AdaptingVisitProfile`. Require a non-null source sheet key and a non-blank, trimmed source service-centre ID when constructing the record.

Rules:

- STORE completion stages records using the active visit profile's source sheet;
- COLLECT completion returns those unchanged records;
- `PreparedLineKey` remains target order ID plus line reference;
- do not use `referenceSheetNumber` as source or destination identity;
- source sheet and prepared-line target are deliberately different concepts;
- compatibility test helpers may accept an explicit source key, but must not fabricate it from `referenceOrderId`;
- storage location/capacity behavior remains unchanged.

Required tests:

- `shouldRetainAdaptedSourceOrderSheetWhileLineIsStored()`
- `shouldReturnOriginalSourceSheetWhenAdaptedLineIsCollected()`
- `shouldKeepPreparedTargetKeySeparateFromSourceSheetIdentity()`
- existing Adapting area, storage, scheduler admission, and debug tests remain green.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.testing.AdaptingBenchStopControllerTest
```

## Step 4: Register Provenance At DSP Pack-Creation Points

Create `DspPackPlanFactory` under the bagging package. It owns a `PackProvenanceRegistry` and provides one method that accepts:

```java
String packId
String initialCorrelationId
PackDimensions dimensions
PackSourceProvenance provenance
```

It registers provenance first, then returns the existing generic three-field `PackPlan`. It does not change `PackPlan`.

Use this factory in current DSP physical-pack creation paths:

1. Adapting collection through `DefaultCollectedPackPlanFactory`.
2. Third Party direct fulfilment and ADAPTED preparation through `ProductMasterThirdPartyPackPlanFactory`.

For Adapting collection:

- source sheet comes from `AdaptedLineRecord.sourceOrderSheetKey`;
- line/product/pharmacy/patient/prescription come from its `DspOrderItem`;
- service centre comes from `AdaptedLineRecord.sourceServiceCentreId()` and must not be inferred from the collecting order;
- preserve deterministic pack IDs `pack-<lineReference>-<ordinal>`.

For Third Party:

- change `ThirdPartyLineWork` to retain the complete `DspOrderItem line` rather than duplicating only line reference/product ID;
- retain delegate accessors only where existing code genuinely needs them;
- source sheet and service centre come from `ThirdPartyVisitPlan`;
- preserve existing outstanding-quantity and pack-ID rules.

Update `ThirdPartyVisitPlan` to retain `String serviceCentreId` alongside its logical key/type/work. Admission remains logical and physical visits remain typed.

Every production controller/debug rig that creates these factories must receive one shared registry for the scene/runtime. Do not create a fresh registry per completed visit.

Required tests:

- `shouldRegisterCollectedAdaptedPackAgainstOriginalSourceLine()`
- `shouldRegisterThirdPartyPackAgainstVisitSourceSheet()`
- `shouldPreservePatientAndPrescriptionForAdaptedThirdPartyCollection()`
- `shouldNotCreateProvenanceForMissingPhysicalPack()`
- existing exactly-once Third Party completion tests remain green.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactoryTest
```

## Step 5: Add Bag-Planning Input And Output Records

Create `BagPlanningTote` with exactly:

```java
OrderSheetKey fulfilmentOrderSheetKey
String serviceCentreId
ToteLoadPlan toteLoadPlan
```

The physical input identity is `toteLoadPlan.physicalToteId()`. Do not duplicate it as another component.

Create `BagPlanningRequest` containing an ordered nonempty `List<BagPlanningTote>`. Defensively copy it and reject:

- null entries;
- duplicate physical tote IDs;
- duplicate physical pack IDs across tote loads.

Create `PlannedBag` with exactly:

```java
BagKey bagKey
String serviceCentreId
String pharmacyId
String patientId
String prescriptionId
List<String> physicalPackIds
List<OrderSheetKey> owningOrderSheetKeys
```

`owningOrderSheetKeys` is stable first-occurrence order with duplicates removed.

Create `PlannedPackTrace` with exactly:

```java
String physicalPackId
PackSourceProvenance sourceProvenance
PhysicalToteId inputPhysicalToteId
OrderSheetKey fulfilmentOrderSheetKey
BagKey bagKey
```

Create `BagPlanningResult` containing:

```java
List<PlannedBag> plannedBags
List<ToteLoadPlan> p2pToteLoadPlans
List<PlannedPackTrace> packTraces
```

It must provide immutable lookup by `BagKey`, bag correlation ID, and physical pack ID. Reject duplicate keys/traces.

Required tests:

- `shouldValidateOrderedBagPlanningInput()`
- `shouldRepresentOneBagOwnedBySeveralLogicalSheets()`
- `shouldJoinSourceAndFulfilmentIdentityInPlannedPackTrace()`
- `shouldProvideImmutableBagAndPackLookups()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningDomainTest
```

## Step 6: Add Replaceable Capacity Policy And Deterministic Planner

Create:

```java
public interface BagCapacityPolicy {
    boolean canAdd(List<PackPlan> currentPackPlans, PackPlan candidatePackPlan);
}
```

The planner supplies an immutable current list. A policy must not mutate it.

Create `MaximumPackCountBagCapacityPolicy`:

- constructor takes positive `maximumPackCount`;
- `canAdd` returns true while adding the candidate would keep count at or below the limit;
- this is the only production policy in this branch.

Create `DeterministicBagPlanner` with injected `BagCapacityPolicy` and `PackProvenanceSnapshot`.

Algorithm:

1. Walk planning totes in request order, then each tote's packs in load-plan order.
2. Resolve source provenance for every pack; fail before returning a partial result if any pack is missing provenance.
3. Group by `prescriptionId` in first-occurrence order.
4. Validate that one prescription has one patient, pharmacy, and service centre.
5. Within each prescription preserve encountered pack order.
6. Start ordinal one and add packs while the policy accepts them.
7. When the policy rejects a candidate for a nonempty bag, close that bag and start the next ordinal.
8. If the policy rejects a candidate for an empty bag, throw a clear policy-contract exception.
9. Build `PlannedBag` and `PlannedPackTrace` records.
10. Do not create a bag for a prescription with no physical packs.

Required tests:

- `shouldGroupPacksForOnePrescriptionIntoOneBagWhenCapacityAllows()`
- `shouldSplitPrescriptionIntoDeterministicBagOrdinalsAtPackLimit()`
- `shouldPreservePrescriptionAndPackFirstOccurrenceOrder()`
- `shouldRejectConflictingPatientPharmacyOrServiceCentreForPrescription()`
- `shouldFailClearlyWhenPhysicalPackProvenanceIsMissing()`
- `shouldCreateNoPhysicalBagForLineWithoutPhysicalPacks()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicyTest --tests online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlannerTest
```

## Step 7: Emit P2P-Compatible Planned Tote Loads

Complete `DeterministicBagPlanner` result construction by producing one rewritten `ToteLoadPlan` for each input planning tote.

For every existing `PackPlan`:

- preserve `packId` exactly;
- preserve `dimensions` exactly;
- replace `correlationId` with its planned `BagKey.correlationId()`;
- preserve the original typed `PhysicalToteId` and pack order;
- do not mutate the input load plan.

Rules:

- `BagPlanningResult.p2pToteLoadPlans()` preserves input tote order, including an empty physical tote load if supplied;
- one bag correlation may occur across several physical input tote plans;
- `ToteToBagBatchPlan.fromToteLoadPlans(...)` must aggregate those packs into the expected count for that bag correlation;
- existing arbitrary generic correlations remain supported outside the DSP planner;
- do not change sorter, PRL, PCR, or bagger grouping logic;
- do not change `CompletedBag` or `Bag` identity fields in this branch.

Required tests:

- `shouldRewriteOnlyCorrelationWhenApplyingBagPlanToToteLoads()`
- `shouldAggregateOnePlannedBagAcrossSeveralInputTotes()`
- `shouldKeepSeparateBagOrdinalsAsSeparateP2pCorrelations()`
- existing tote-to-bag plan and assignment tests remain green.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.totebag.plan.* --tests online.davisfamily.warehouse.sim.totebag.ToteToBagAssignmentPlannerTest
```

## Step 8: Add Cross-Station Bag-Provenance Scenario

Create `DspBagPlanningProvenanceScenarioTest` under the bagging test package.

Build a scenario containing:

- one FULL_PACK logical sheet with two prescriptions for one patient;
- one prescription that fits one bag;
- one prescription that exceeds the configured pack limit and produces two bags;
- one ASSOCIATED fulfilment sheet collecting a line from an ADAPTED source sheet;
- one Third Party-created physical pack;
- at least two physical input totes;
- a logical line with no physical pack, represented only as logical data and absent from the provenance registry.

Verify:

1. 12N patient and prescription identity survives loading;
2. every physical planned pack resolves to source line provenance;
3. ADAPTED collection retains the preparation source sheet while the planned bag owns the ASSOCIATED fulfilment sheet;
4. the no-pack line creates neither a fake pack nor a physical bag;
5. bag ordinals and correlations are deterministic across repeated planning;
6. every trace identifies source sheet, fulfilment sheet, input physical tote, and bag key;
7. P2P batch counts match the planned bags;
8. no outbound physical tote, output sheet, NS flag, or Exception visit is created.

Required test methods:

- `shouldPlanBagsByPrescriptionWithDeterministicOverflow()`
- `shouldTraceAdaptedAndThirdPartyPacksToSourceLines()`
- `shouldKeepMissingLogicalLineSeparateFromPhysicalPackPlans()`
- `shouldProduceStableP2pCorrelationsFromBagKeys()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.DspBagPlanningProvenanceScenarioTest
```

## Step 9: Regression And Visual Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.* --tests online.davisfamily.warehouse.sim.dsp.io.* --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.totebag.plan.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the `adapting` debug scene;
- run the `third-party` debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify pack/tote/bag motion and grouping look unchanged;
- verify `ALT+R` still resets each tested scene;
- no new visual presentation is required.

Before branch closure:

- update this plan status to implementation complete and verified;
- update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- update `docs/codex-context.md` and `docs/codex-instructions.md` if branch position changed;
- record any intentionally retained deprecated fixture bridge.

## Completion Criteria

- Production 12N mapping retains patient and prescription IDs.
- Bag identity is typed and deterministic by prescription plus ordinal.
- Actual physical packs have immutable source-line provenance.
- ADAPTED storage/collection retains its original source sheet.
- Third Party and Adapting-created packs register provenance exactly once.
- Bag planning uses actual packs and never fabricates missing packs.
- Pack-count capacity is configurable behind a replaceable policy.
- P2P-ready tote plans use bag correlations without changing generic machine-state contracts.
- Planned traces join source sheet, fulfilment sheet, input physical tote, and bag.
- No outbound physical tote, generated output sheet, Exception behavior, NS bag, 32R, database, renderable, or thread is introduced.
- Focused tests, full tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, create the detailed plan for:

```text
feature/dsp-outbound-tote-allocation
```

That branch will allocate independently supplied outbound physical totes per P2P line, enforce pharmacy/service-centre purity and bag capacity, retain patient affinity where possible, and introduce generated output sheets only when concurrent overflow requires them.
