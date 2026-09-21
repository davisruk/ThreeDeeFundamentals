# DSP One-Retained-Line/One-Pack Correction Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: planned; no correction implementation started.

## Purpose

Correct the quantity interpretation used by Third Party, Adapting, and complete bag-demand
planning. Production DSP 12N processing is line-based: every retained 12N line represents exactly
one executable pack. If a prescription needs two packs of the same product, the 12N contains two
distinct lines, not one line whose numeric fields instruct the WCS to create two packs.

The checkpoint implementation in commit `c755dac` followed
`docs/scheduler/dsp-complete-bag-demand-planning-remediation-plan.md` and correctly introduced
complete logical demand, immutable planned slots, preassigned bags, station correlation, and
constant-time runtime indexes. Its quantity interpretation is wrong. It uses `numberOfPacks` to
multiply one line into several slots and `numberOfPacksPicked` to decide whether those slots are
physical or station-pending. A production daily run exposed that a Third Party line can carry a
positive `numberOfPacksPicked`, causing the checkpoint implementation to reject valid executable
work.

This correction preserves the remediation's ownership, lifecycle, bag ordering, immutable
publication, correlation, P2P, and caching models. It changes only the meaning of one retained line
and removes behavioural dependence on `numberOfPacks` and `numberOfPacksPicked`.

Full-day production-data queries performed after the checkpoint implementation found:

- no `numberOfPacksPicked` value greater than `1`;
- no `numberOfPacks` value equal to `0` or greater than `1`, so every observed line carried
  `numberOfPacks == 1`; and
- `numberOfPacksPicked == 0` only in ADAPTED messages.

These observations are evidence for the line-based model, not runtime classification rules. The
implementation must not branch on them, validate them as semantic invariants, or use them to infer
physical presence. In particular, the observed positive picked value on a Third Party product does
not suppress Third Party or Adapting work. The authoritative rule remains that retained line
identity and routing context define one pack, while both numeric fields are ignored after
structural mapping.

This plan is the authoritative correction for the conflicting quantity decisions in:

- `docs/scheduler/dsp-complete-bag-demand-planning-remediation-plan.md`;
- `docs/scheduler/dsp-bag-planning-provenance-plan.md`;
- `docs/machines/third-party-station-requirements.md`;
- `docs/machines/third-party-station-phase-1-plan.md`; and
- `docs/scheduler/dsp_osr_scheduler_requirements.md`.

## Required Reading

Read these documents completely, in this order, before implementing any step:

1. `AGENTS.md`
2. `docs/codex-instructions.md`
3. `docs/codex-context.md`
4. `docs/scheduler/dsp-station-processing-boundary-plan.md`
5. `docs/scheduler/dsp-station-route-continuation-plan.md`
6. `docs/scheduler/dsp-operational-empty-end-to-end-proof-plan.md`
7. `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`
8. `docs/scheduler/dsp-complete-bag-demand-planning-remediation-plan.md`
9. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
10. `docs/scheduler/dsp-bag-planning-provenance-plan.md`
11. `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
12. `docs/machines/adapting-station-phase-1-plan.md`
13. `docs/machines/third-party-station-requirements.md`
14. `docs/machines/third-party-station-phase-1-plan.md`
15. `docs/scheduler/dsp_osr_scheduler_requirements.md`

Before each step, inspect every production and test class named by that step and record
`git status --short`. Preserve unrelated changes. If repository state contradicts a fixed decision
below, stop and report the contradiction rather than selecting a different API, ownership,
lifecycle, correlation, ordering, compatibility, or caching model.

## Authoritative 12N Meaning

- Every retained `DspOrderItem` represents one executable pack.
- Multiple packs for one prescription are represented by multiple distinct retained lines.
- Line presence, line type, order type, product-master classification, and prepared-line
  relationships determine what work exists and where that one pack is realised.
- Ordinary wholesaler short picks are omitted before the DSP-facing 12N is constructed. The WCS
  does not reconstruct an omitted line.
- A retained ordinary direct-fulfilment line represents one pack already present in its inbound
  physical tote.
- A retained direct `FULL_PACK` line whose product has a Third Party location represents one pack
  that Third Party must create, regardless of `numberOfPacksPicked`.
- A retained line in an ADAPTED source order represents one prepared pack. If its product has a
  Third Party location, Third Party first creates that same pack; Adapting later stores and
  transfers it. Otherwise Adapting prepares it through the existing flow.
- An `ADAPTED` line in an ASSOCIATED or EMPTY fulfilment order is an alias used to collect the one
  prepared source-line pack. It is not another demand and is never picked again at Third Party.
- `numberOfPacks` and `numberOfPacksPicked` are retained protocol metadata. Neither field changes
  demand count, physical-realisation choice, Third Party selection, Adapting output, correlation,
  bag membership, P2P workload, or completion.
- Do not require `numberOfPacks == 1`, `numberOfPacksPicked == 0`,
  `numberOfPacksPicked == numberOfPacks`, or `numberOfPacksPicked <= numberOfPacks`.
- Keep the existing JSON mapping and basic transport/domain parsing contract: `numberOfPacks` is
  still parsed as a positive integer and `numberOfPacksPicked` as a non-negative integer and both
  remain available on `DspOrderItem`. This correction does not redesign the source DTO or accept
  malformed/non-numeric protocol fields. Values such as `numberOfPacks = 3` or
  `numberOfPacksPicked = 2` are accepted by the existing model where structurally valid, but still
  represent one retained-line pack and have no behavioural effect.

## Fixed Decisions

### Slot and physical identity

- Preserve `PlannedPackSlotKey(OrderSheetKey sourceOrderSheetKey, String lineReference,
  int packOrdinal)` for compatibility with the checkpoint remediation.
- Production construction and station resolution use exactly one key per source line with
  `packOrdinal == 1`. Do not loop over either 12N numeric field.
- Preserve station-created physical identity `pack-<lineReference>-1`.
- Preserve initially physical identity
  `pack-<physicalToteId>-<lineReference>-1`.
- Do not remove the ordinal field or change physical-ID formats in this correction. The ordinal is
  a compatibility value fixed to one, not a quantity derived from 12N.
- Preserve `BagPackDemand`, `PlannedPackSlot`, `PlannedBag`, `BagSequencePosition`,
  `PlannedPackTrace`, and the authoritative immutable indexes in `BagPlanningResult`.
- Preserve demand encounter order: fulfilment order source sequence, item order, then the single
  slot for that line. Bag membership, bag ordinal, total bag count, and line-to-bag assignment
  remain immutable.

### Initially physical versus station-pending

`DspFullDayBagPlanningRequestFactory` remains the sole owner of this pre-runtime decision.

- A non-ADAPTED fulfilment line is initially physical exactly when it has a matching inbound
  manifest line, its line type is not `ADAPTED`, and its product is not Third Party.
- Such a line creates one physical observation and one `PackPlan`, irrespective of both numeric
  fields.
- A direct Third Party line is station-pending even though the 12N mapper retains that line in the
  inbound manifest. Product-master Third Party classification takes precedence over manifest line
  presence for physical realisation.
- An ADAPTED source line and its ASSOCIATED/EMPTY alias are station-pending in bag planning. The
  source line owns the slot and provenance; the fulfilment alias owns the fulfilment sheet.
- An initially physical ordinary line without a matching manifest observation is rejected because
  there is no physical source. A station-pending line with an initial observation, duplicate
  source/alias identity, duplicate slot, duplicate reserved ID, or conflicting source facts is
  rejected. None of those checks may inspect the two numeric fields.
- Remove quantity and picked-count comparison from manifest identity and ADAPTED
  source/fulfilment matching. Continue matching line reference, product, pharmacy, patient,
  prescription, service centre, and the established order/line relationship.
- FULL_PACK, ASSOCIATED, and EMPTY orders remain fulfilment owners. ADAPTED orders remain source
  preparation owners and never add a second demand.

### Third Party and Adapting

- `ThirdPartyVisitFactory` selects each qualifying line solely from order type, line type, and the
  product's Third Party location. It does not calculate outstanding quantity and does not inspect
  either numeric field.
- One qualifying line creates one `ThirdPartyLineWork`; one completed line-work record creates one
  `PackPlan` with ordinal `1`.
- Replace outstanding-quantity vocabulary in the active Third Party domain with line/pack-count
  vocabulary. `ThirdPartyLineWork` contains no quantity field. Visit pack count equals the number
  of line-work records.
- Preserve existing Third Party selection: ADAPTED source lines are
  `ADAPTED_PREPARATION`; direct `FULL_PACK` lines in FULL_PACK, ASSOCIATED, or EMPTY fulfilment
  orders are `DIRECT_FULFILMENT`; ADAPTED aliases are not selected.
- `DefaultCollectedPackPlanFactory` creates exactly one `PackPlan` per collected
  `AdaptedLineRecord`, with ordinal `1`, irrespective of both numeric fields.
- The planned-slot Third Party and Adapting resolvers require ordinal `1`, perform one constant-time
  slot-key lookup, validate reserved identity and provenance, and return the preassigned bag
  correlation.
- Third Party ADAPTED preparation and later Adapting collection continue to resolve the same slot,
  reserved ID, provenance, and bag correlation. Existing idempotent provenance registration is
  preserved.
- Preserve exactly-once completion application and line-reference completion tracking. Do not add
  short-pick outcomes, failure waivers, or Exception behaviour.

### P2P, workload, and bagging

- P2P expected pack count is the number of planned slots in a bag. Because construction emits one
  slot per retained source line, it is also the number of retained source lines assigned to that
  bag.
- Preserve `P2pBagCorrelationRequirementCatalogFactory`, `P2pWorkloadPlanIndex`, and
  `P2pLineAllocationRequestFactory` ownership and immutable-index reuse. They must not read either
  numeric field.
- Preserve stable union of physical-tote and fulfilment-sheet requirements, candidate-specific
  allocation, exact planned-versus-actual pack-ID validation, and close-only-after-all-reserved-
  packs-arrive behaviour.
- Capacity policies continue to count `BagPackDemand` entries. They therefore count retained lines,
  not either 12N numeric field.
- Do not move quantity interpretation into metrics, snapshots, reports, completion evaluation, or
  another runtime path.

### Efficiency and unchanged boundaries

- Full-day request construction remains linear in orders, retained lines, and manifests. Build
  method-local insertion-ordered indexes once. Emit exactly one demand or observation per eligible
  retained line.
- Runtime slot, correlation, pack-ID, bag-position, requirement, and station-resolution lookups
  remain constant-time through the checkpoint remediation's authoritative immutable indexes.
- Do not scan bags, slots, traces, manifests, orders, prepared lines, or requirements from runtime
  lookup paths.
- Do not add global/static caches, thread-locals, synchronization, weak maps, object pools,
  fingerprints, equality scans on reads, duplicate mutable indexes, or invalidation machinery.
- `DspOrderItem`, `TwelveNOrderLineJson`, `TwelveNLineMappingSupport`, and the checked-in message
  examples remain structurally unchanged in this correction.
- Preserve no-op, rejection, partial-failure, encounter-order, validation, exactly-once, and
  immutable-publication behaviour except for the explicitly removed numeric-field rejections.
- Exception Station outcomes, empty physical bags, NS labels, short-pick reconstruction, direct
  OSU/ASN ingestion, MANUAL work, dispatch/32R, and physical failure outcomes remain deferred.

## Step 1: Make Third Party Work Line-Based

Modify these production classes:

- `ThirdPartyLineWork`
- `ThirdPartyVisitFactory`
- `ThirdPartyVisitPlan`
- `ThirdPartyVisit`
- `ThirdPartyVisitState`
- `ThirdPartyArea`
- `ThirdPartyAreaController`
- `ProductMasterThirdPartyPackPlanFactory`
- `PlannedSlotThirdPartyPackCorrelationResolver`

Required API changes:

- Change `ThirdPartyLineWork` to exactly
  `ThirdPartyLineWork(DspOrderItem line, String binLocation, ThirdPartyWorkType workType)`.
- Replace `outstandingPackCount()` on `ThirdPartyVisitPlan` and `ThirdPartyVisit` with
  `packCount()`, returning `lineWork.size()` without a stream or derived allocation.
- Rename `ThirdPartyVisitState.outstandingPackCount` to `packCount`; retain `lineCount` because the
  snapshot's established shape distinguishes work lines from produced packs even though they are
  equal under this contract.
- Do not retain deprecated compatibility methods or constructors for outstanding quantity; update
  all callers so quantity arithmetic cannot remain reachable.
- Keep `ThirdPartyPackPlanFactory.createPackPlan(..., int packOrdinal)` and
  `ThirdPartyPackCorrelationResolver.resolve(..., int packOrdinal)` for checkpoint compatibility,
  but require exactly `packOrdinal == 1`.

`ThirdPartyVisitFactory` must select every qualifying Third Party line even when
`numberOfPacksPicked` is positive or greater than `numberOfPacks`. `ThirdPartyAreaController` calls
the pack factory exactly once per newly completed line with ordinal `1`; it performs no nested
quantity loop.

Modify the Third Party tests, including:

- `ThirdPartyVisitFactoryTest`
- `ThirdPartyAreaTest`
- `ThirdPartyPickFlowTest`
- `ThirdPartyStationAdmissionAdapterTest`
- `ThirdPartyStationProcessingControllerTest`
- `ThirdPartyAdaptedCollectIntegrationTest`
- `DspBagPlanningProvenanceScenarioTest`
- `ThirdPartyAreaStopControllerTest`
- `ThirdPartyDebugRigTest`

The test contract must prove:

- a qualifying line with `numberOfPacks = 3` and `numberOfPacksPicked = 2` still creates one line
  work item and one pack;
- a qualifying line with zero picked and one with positive picked are selected identically;
- two distinct qualifying lines produce two work items and two packs in encounter order;
- non-Third-Party and nonqualifying ADAPTED alias lines remain excluded;
- completion remains exactly once and the resolver rejects every ordinal except `1`;
- snapshots report equal line and pack counts without outstanding-quantity vocabulary.

### Expected output

Third Party work and physical production are line-count based, with no production accessor or
calculation for outstanding quantity.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.testing.ThirdParty*
```

### User verification

No additional user verification is required for this step.

## Step 2: Construct One Planned Slot And One Collected Pack Per Source Line

Modify these production classes:

- `DspFullDayBagPlanningRequestFactory`
- `DefaultCollectedPackPlanFactory`
- `PlannedSlotCollectedPackCorrelationResolver`

Do not change `DspFullDayInputLoader`, `DeterministicBagPlanner`, `BagPlanningResult`, the bag
capacity API, or the P2P requirement/workload owners unless a compile-only signature migration from
Step 1 requires a mechanical caller update.

Implement the fixed physical-versus-pending matrix above. Remove `validatePickedCount`,
`thirdPartyPickedCountError`, `ordinaryPickedCountError`, all quantity/picked loops, and all
quantity comparisons from demand, manifest, and source/alias matching. Use the existing indexed
single-pass construction with ordinal `1`.

`DefaultCollectedPackPlanFactory` creates one pack per collected line and calls its resolver once
with ordinal `1`. `PlannedSlotCollectedPackCorrelationResolver` accepts only ordinal `1` and keeps
the existing exact source-fact and reserved-ID checks.

Modify these tests:

- `DspFullDayAnalysisScenarioTest`
- `DspFullDayInputLoaderTest`
- `DspBagPlanningProvenanceScenarioTest`
- `AdaptingCollectFlowTest`
- `AdaptingStationProcessingControllerTest`
- `ThirdPartyAdaptedCollectIntegrationTest`

The test contract must prove:

- an ordinary direct line with a manifest creates one initially physical slot even when picked is
  zero or differs from `numberOfPacks`;
- a direct Third Party line creates one pending slot even when picked is positive;
- values greater than one in either field never create a second slot, observation, demand, pack,
  trace, requirement, or workload unit;
- an ADAPTED source and alias whose numeric fields differ still resolve one source-owned pending
  slot when all authoritative identity fields match;
- one collected adapted line creates one pack and resolves ordinal `1`;
- duplicate manifests, missing ordinary observations, source-fact conflicts, duplicate slots, and
  duplicate reserved IDs still fail before immutable publication;
- encounter order and bag assignment remain unchanged for equivalent line sequences.

### Expected output

Full-day demand and Adapting collection implement one source line, one logical slot, and one
eventual physical pack without reading either numeric field.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisScenarioTest --tests online.davisfamily.warehouse.sim.dsp.bagging.DspBagPlanningProvenanceScenarioTest --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAdaptedCollectIntegrationTest
```

### User verification

No additional user verification is required for this step.

## Step 3: Prove Downstream Bag, P2P, And Workload Semantics

Update synthetic fixtures that currently represent several packs by setting one line's quantity
above one. Represent those packs as distinct line references with one demand each. Retain explicit
non-unit numeric values only in the tests whose purpose is to prove that those values are ignored.

Inspect and update as required:

- `BagPlanningDomainTest`
- `MaximumPackCountBagCapacityPolicyTest`
- `DeterministicBagPlannerTest`
- `BagPlanningResultTestFixtures`
- `P2pBagCorrelationRequirementCatalogFactoryTest`
- `P2pWorkloadPlanIndexTest`
- `P2pWorkloadSnapshotTest`
- `P2pLineAllocationRequestFactoryTest`
- `DspHeadlessP2pLineRuntimeFactoryTest`
- `DspHeadlessP2pLineRuntimeTest`
- `DspAv02OperationalAllocationScenarioTest`
- `DspOutboundToteAllocationScenarioTest`
- `OutboundGeneratedSheetIntegrationTest`
- `OutboundToteAllocationControllerTest`

Production P2P, outbound, planner, and result classes are expected to require no semantic change.
If a failing test reveals that one of them reads either numeric field or infers several slots from
one source line, stop and amend this plan before changing its ownership or API.

The test contract must prove:

- bag capacity splits by the number of distinct retained-line demands;
- requirement expected counts and workload counts equal planned slot counts;
- one line cannot contribute more than one reserved physical ID;
- stable requirement union, sticky line ownership, exact pack-ID validation, and immutable index
  reuse remain unchanged;
- tests needing three packs use three line references and preserve encounter order.

### Expected output

All downstream behaviour consumes the corrected one-slot-per-line plan without a second quantity
interpretation.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.dsp.av02.*
```

### User verification

No additional user verification is required for this step.

## Step 4: Full-Day Regression, Static Audit, And External Proof

Update `DspFullDayAnalysisScenarioTest` with an end-to-end mixed prescription containing distinct
ordinary, direct Third Party, and adapted source/alias lines. Give at least one Third Party line a
positive picked value and at least one line non-unit numeric metadata. Prove that each retained
source line contributes exactly one immutable slot, every station-produced pack resolves its
preassigned correlation, P2P closes at the distinct-line count, and bag sequence positions remain
stable.

Perform a static production audit with:

```powershell
rg -n "\.quantity\(\)|numberOfPacksPicked\(\)|outstandingQuantity|outstandingPackCount" app/src/main/java
```

The only permitted production matches after this correction are:

- JSON-to-domain parsing/accessor retention in `TwelveNLineMappingSupport` and `DspOrderItem`; and
- no behavioural read from analysis, bagging, Third Party, Adapting, P2P, outbound, metrics,
  snapshots, or completion code.

If another match remains, remove the behavioural dependence or stop and report why the field is
still required. Do not hide an equivalent calculation behind a renamed helper.

### Expected output

Focused repository tests prove the corrected contract, and static inspection proves there is no
remaining production quantity dependence in executable DSP behaviour.

### Implementation verification

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.* --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

Run `git diff --check` after the focused tests.

### User verification

The user runs:

```powershell
.\gradlew test
```

Then rerun the same external full-day dataset and configuration that exposed
`TOTE0007170945/001` line `000243554437`. The run must pass that line without a picked-count error
and must not duplicate its demand or physical pack. Continue with the external-data acceptance in
Step 35 of `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`.

## Acceptance Criteria

- Every retained source line creates exactly one logical slot and at most one eventual physical
  pack.
- Multiple packs for a prescription are represented by multiple distinct lines.
- `numberOfPacks` and `numberOfPacksPicked` have no behavioural effect after successful structural
  mapping.
- Positive picked metadata never suppresses or rejects qualifying Third Party work.
- Ordinary retained direct lines use their matching inbound manifest, not picked metadata, as
  physical evidence.
- ADAPTED source/alias correlation ignores both numeric fields and preserves exact source identity,
  reserved pack ID, provenance, bag correlation, and fulfilment ownership.
- Bag membership, bag ordinal, total count, and slot assignment remain immutable.
- P2P requirements, workload, capacity, and completion count planned retained-line slots.
- Full-day construction remains linear and runtime lookups remain constant-time through one
  authoritative immutable index owner per concern.
- No equivalent traversal or quantity interpretation is moved into snapshots, metrics, logging,
  completion evaluation, or another frequent path.
- Existing validation, no-op, rejection, partial-failure, exactly-once, encounter-order, and
  immutable-publication contracts remain unchanged except for removed numeric-field semantics.
- Exception outcomes, empty physical bags, NS labels, failure waivers, short-pick reconstruction,
  and direct OSU/ASN ingestion remain unimplemented.

## End-Of-Feature Architecture Review

After all implementation and user verification pass, review the complete correction diff and
report PASS, FAIL, or UNPROVEN with concrete class/method evidence for every acceptance criterion
above. Additionally prove:

- no production loop bound, branch, count, identity, validation, correlation, or workload result
  depends on either numeric field;
- Third Party and Adapting each create one pack per eligible line and resolve ordinal `1`;
- initially physical and station-pending decisions follow the fixed matrix;
- P2P and outbound ownership/correlation contracts from the checkpoint remediation were not
  redesigned;
- construction and runtime complexity still meet the fixed efficiency contract; and
- no deferred Exception or direct upstream-ingestion behaviour entered the diff.

No model-run command is authorized for the review. Use the verified implementation, focused test
results, user full-suite result, external-run result, and static audit as evidence.

## Documentation Closure

After implementation, architecture review, full-suite verification, and external-data verification
are green:

- mark this plan complete and verified with the commit and verification evidence;
- update `docs/codex-context.md` and `docs/codex-instructions.md` to state that executable DSP
  semantics are one retained line/one pack and that both numeric fields are metadata only;
- update the supersession notices in the plans and requirements named in this plan from planned to
  implemented;
- update Step 35 of the full-day analysis plan with the successful external-run evidence; and
- preserve the original remediation plan as the historical checkpoint record rather than
  rewriting its completed implementation steps.

If verified implementation differs from this fixed contract, documentation closure must stop and
report the inconsistency rather than reinterpret it.
