# DSP Complete Bag Demand Planning Remediation Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection`

Status: checkpoint implementation committed as `c755dac`; numeric-field semantics superseded by
the correction plan referenced below.

> **Supersession notice:** The checkpoint implementation of this plan was committed as `c755dac`.
> Production data subsequently disproved this plan's use of `numberOfPacks` as a slot multiplier
> and `numberOfPacksPicked` as physical/station-pending evidence. The correction is specified in
> `docs/scheduler/dsp-one-retained-line-one-pack-correction-plan.md`. That plan supersedes every
> quantity, picked-count, outstanding-quantity, and per-line multi-ordinal decision below while
> preserving this plan's bag ownership, lifecycle, ordering, correlation, P2P, immutable-index,
> efficiency, and Exception-deferral decisions. Do not implement or restore the superseded
> decisions from this historical checkpoint plan.

> **Later malformed-input boundary:** Production-day verification subsequently found an ADAPTED
> fulfilment alias with no source preparation line. Its treatment is decision-complete in
> `docs/scheduler/dsp-recoverable-input-rejection-plan.md`: retain the line for reporting, exclude
> its dependency-closed group before planning, and keep this plan's bag planner checks strict as
> invariant backstops. Do not invent a slot, pack, bag, station outcome, or Exception outcome for
> rejected input.

## Purpose

Correct the full-day bag plan so it is derived from complete executable prescription demand,
including packs that will be created later by Third Party or Adapting, rather than only from
physical packs present in inbound totes at load time.

The immediate reproduced failure is Third Party line `000243688425`. Its source ADAPTED order is
`TOTE0007174962/008`, its fulfilment ASSOCIATED order is `TOTE0007175140/001`, and its prescription
is `20002460000226956`. The source line correctly carries `numberOfPacksPicked = 0`; the current bag
planner consequently omits it, and the Third Party pack factory cannot find a planned correlation
when it later creates the pack. The input is consistent with the 12N contract. The planning model is
incomplete.

This remediation must:

- plan every known-product pack position from line quantity before runtime starts;
- distinguish an immutable logical pack slot from a physical pack already in a tote;
- assign every slot to one immutable bag and retain ordered bag `x of y` identity;
- resolve later Third Party and Adapting physical packs to their exact preplanned slots;
- retain existing physical-tote load plans and provenance for packs that already exist;
- preserve a bag plan when no physical pack for that bag has yet been realised;
- preserve the same bag, ordinal, total, and line-to-bag assignment if a station-pending slot later
  fails;
- leave physical empty-bag creation, Not Supplied state, and Exception routing to the Exception
  Station work.

This is a functional prerequisite of Step 35 in
`docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`. It is not another performance
optimization step.

For this remediation, the complete-demand decisions below supersede only the statements in
`docs/scheduler/dsp-bag-planning-provenance-plan.md` that planning uses actual `PackPlan` entries
only and that missing logical lines create no bag. All other established provenance, generic
machine-boundary, ordering, and compatibility decisions remain in force.

## Required Reading

Read these documents completely, in this order, before implementing any step:

1. `AGENTS.md`
2. `docs/codex-instructions.md`
3. `docs/codex-context.md`
4. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
5. `docs/scheduler/dsp-bag-planning-provenance-plan.md`
6. `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
7. `docs/machines/adapting-station-phase-1-plan.md`
8. `docs/machines/third-party-station-requirements.md`
9. `docs/machines/third-party-station-phase-1-plan.md`
10. `docs/machines/exceptions-station-requirements.md`
11. `docs/scheduler/dsp-station-route-continuation-plan.md`
12. `docs/scheduler/dsp-full-day-analysis-metrics-inspection-plan.md`

Before each step, inspect every production class and test class named by that step and record
`git status --short`. If the current APIs or repository state contradict a fixed decision below,
stop and report the contradiction instead of choosing another ownership or lifecycle model.

## 12N Meaning Used By This Plan

"Preston warehouse" in the source specification means the wholesaler operating on the floor below
the DSP. Orders are sent to that wholesaler and picked into totes. The upstream system receives OSU
and ASN messages describing fulfilment, and constructs the DSP-facing 12N primarily from the ASN.

The source specification's statement that an ordinary short pick is transmitted with
`numberOfPacksPicked = 0` does not describe the actual DSP feed. In the observed integration:

- an ordinary line that the wholesaler did not pick is omitted when the 12N is constructed;
- a retained ordinary line therefore has a positive `numberOfPacksPicked` and represents packs
  already physical in the received tote;
- `numberOfPacksPicked = 0` is used for Third Party demand because the wholesaler did not pick that
  line and the DSP must create it through Third Party processing;
- the picked count on the matching ADAPTED line in an ASSOCIATED order is ignored because the WCS
  uses its pharmacy, prescription, and patient reference to collect the prepared result.

Therefore:

- `quantity` creates logical pack demand;
- a positive `numberOfPacksPicked` identifies ordinary packs already physical in an inbound tote;
- a zero value identifies Third Party/associated adapted demand that must receive station-pending
  slots; it must not be interpreted as an ordinary short-pick outcome;
- an ordinary zero-picked line contradicts the observed DSP 12N contract and must be rejected with
  its order sheet and line reference rather than converted into a no-pack slot;
- an ordinary line omitted upstream is not present in the DSP input and cannot be reconstructed by
  this planner. "Complete demand" in this plan means complete executable demand represented by the
  retained 12N lines; consuming OSU/ASN directly is outside the current simulator boundary;
- ADAPTED source orders describe preparation work and provenance, not a second copy of fulfilment
  demand;
- the matching ADAPTED line in the ASSOCIATED or EMPTY order owns the fulfilment position.

## Fixed Decisions

Do not revisit these during implementation.

### Bag identity and ordering

- The business grouping identity is the trimmed tuple `(pharmacyId, patientId, prescriptionId)`.
- A group must resolve to exactly one service centre in a planning request. Reject conflicts.
- Preserve the existing public `BagKey(prescriptionId, bagOrdinal)` and correlation format
  `prescriptionId + "/bag-" + bagOrdinal`; do not introduce a new correlation string or parser.
- Because `BagKey` contains only prescription ID, reject a request in which one prescription ID is
  associated with more than one pharmacy, patient, or service centre. Do not silently merge it.
- Preserve encounter order: logical order source sequence, order item order, then one-based pack
  ordinal. Do not sort by a map, ID, product, or dimensions.
- Capacity assignment consumes the complete ordered logical slot sequence, not the subset already
  physical.
- Bag ordinals are one-based, contiguous, and immutable within each prescription plan.
- `BagSequencePosition(bagOrdinal, totalBagCount)` is the authoritative `x of y` value. Both values
  are positive, `bagOrdinal <= totalBagCount`, and the ordinal must equal the `BagKey` ordinal.
- A slot is assigned to exactly one `BagKey` during planning. Runtime success, rejection, short
  pick, or later Exception processing must never migrate the slot or compact/renumber the bag set.

### Logical slots and physical packs

- Add `PlannedPackSlotKey` with exactly:

  ```java
  OrderSheetKey sourceOrderSheetKey
  String lineReference
  int packOrdinal
  ```

- `packOrdinal` is one-based within the complete source line quantity, not within outstanding
  quantity and not within a station visit.
- Add `BagPackDemand` with exactly:

  ```java
  PlannedPackSlotKey slotKey
  String reservedPhysicalPackId
  PackDimensions dimensions
  PackSourceProvenance sourceProvenance
  OrderSheetKey fulfilmentOrderSheetKey
  Optional<PhysicalToteId> initialPhysicalToteId
  ```

  It is immutable planning input. It is not a `PackPlan` and must not be registered as a physical
  pack.
- Add `PlannedPackSlot` with the same fields as `BagPackDemand` plus its assigned `BagKey`.
- `initialPhysicalToteId` is present only when the physical pack already exists in an inbound tote
  during planning. Its absence means the reserved pack is station-pending and will be created by
  Third Party or Adapting, even though the station visit later has a work tote.
- Keep `PlannedPackTrace` unchanged. It continues to mean that a physical pack existed in the named
  input tote at planning time.
- `PlannedBag.physicalPackIds` remains the ordered list of reserved physical identities for all
  slots in the bag. Membership does not assert that every reserved ID is already instantiated.
- A planned bag must contain at least one logical slot and therefore at least one reserved physical
  pack ID. Keep the current non-empty validation.
- A planned ID for a station-pending slot reserves identity only. Do not create a `PackPlan`, `Pack`,
  provenance-registry entry, load-plan entry, tote inventory entry, or label for it before the
  owning station successfully creates the physical pack.

### Demand construction

- `DspFullDayInputLoader` remains the authoritative owner of full-day pre-runtime bag-plan
  construction. Extract private construction logic into `DspFullDayBagPlanningRequestFactory` in
  the `analysis` package; do not add another runtime planner or mutable bag-plan owner.
- Construct demand from executable `LoadedDspData.orders()` after unknown-product projection.
- FULL_PACK, ASSOCIATED, and EMPTY orders are fulfilment orders and contribute demand.
- ADAPTED orders never contribute a second demand. Index their source lines by
  `PreparedLineKey.forPreparedLine(line)` while retaining the source `OrderSheetKey` and service
  centre.
- A non-ADAPTED fulfilment line uses itself and its fulfilment sheet as source provenance.
- An ADAPTED fulfilment line resolves exactly one source ADAPTED line using
  `PreparedLineKey.forDispatchLine(fulfilmentOrder, line)`. The source line supplies source sheet,
  product, and source provenance; the ASSOCIATED or EMPTY order supplies the fulfilment sheet.
- Source and fulfilment representations must match line reference, product, quantity, pharmacy,
  patient, prescription, and service centre. Reject missing, duplicate, or conflicting matches.
- Create one `BagPackDemand` for every ordinal `1..quantity`.
- Index initially physical observations from every inbound manifest by exact fulfilment
  `(OrderSheetKey, lineReference, packOrdinal)`. More than one manifest may legitimately belong to
  one fulfilment sheet; preserve manifest encounter order and allow that case when their slot keys
  are distinct. For each fulfilment line, ordinals `1..numberOfPacksPicked` must have exactly one
  matching observation and use the existing ID format
  `pack-<physicalToteId>-<lineReference>-<ordinal>`.
- A picked count must be between zero and quantity. Duplicate initial observations for one slot,
  duplicate demand keys, duplicate reserved pack IDs, an unclaimed manifest pack, or a picked slot
  without an initial observation is rejected rather than guessed.
- For a retained ordinary non-Third-Party fulfilment line, require
  `numberOfPacksPicked == quantity`; every demanded slot is already physical. A positive partial
  count has no producer for the remaining slots in the current DSP model and must be rejected with
  the exact order sheet and line reference rather than misclassified as station-pending. If real
  input contains that shape, stop implementation and obtain its upstream ASN-to-12N semantics
  before changing this decision.
- Every station-pending slot uses the established station-created ID format
  `pack-<lineReference>-<packOrdinal>`. Validate global uniqueness against all existing and pending
  IDs before planning. Do not replace that format in this remediation.
- A Third Party source line must have `numberOfPacksPicked == 0`. Reject a non-zero value because
  it contradicts the observed ASN-to-12N contract and the present Third Party APIs enumerate
  outstanding ordinals from one. Do not impose this rule on a non-Third-Party ADAPTED source line,
  and do not use the picked count on its ASSOCIATED/EMPTY alias to decide bag demand or physical
  realisation; that alias is a collection reference.
- Reject an ordinary non-Third-Party line with `numberOfPacksPicked == 0`; the upstream ASN-to-12N
  integration should have omitted it. Do not infer a short pick, Not Supplied state, or
  Exception outcome from a value that is not expected in the DSP feed.
- Unknown-product lines remain in the existing load report and outside executable data; this plan
  does not reclassify them or invent dimensions. Extending immutable bag demand across projected
  unknown products belongs to the Exception Station plan because a capacity decision cannot be
  made from the current executable model.

### Planning API and validation

- Change `BagPlanningRequest` to contain ordered `List<BagPackDemand> packDemands` and ordered
  `List<BagPlanningTote> planningTotes`. Permit an empty tote list when demands exist. Reject a
  request only when both lists are empty.
- Remove `PackProvenanceSnapshot` from `DeterministicBagPlanner`. The request's demands are its one
  authoritative ordered input.
- Change `BagCapacityPolicy` and `MaximumPackCountBagCapacityPolicy` to evaluate
  `BagPackDemand` values, never temporary `PackPlan` values. Use exactly
  `canAdd(int currentPackCount, BagPackDemand candidate)` so the maximum-count policy is constant
  time and the planner does not rebuild or rescan the current bag for each candidate. A future
  dimensional policy may replace this contract with an explicit bounded accumulator in a separate
  plan; do not pre-emptively add that machinery here.
- The planner groups demands by the fixed identity rules, partitions each group with the capacity
  policy, assigns `BagKey`s, and only after all bags in the group are known publishes their shared
  total through `BagSequencePosition`.
- The planner rewrites correlations only on the existing physical `PackPlan`s in
  `planningTotes`; station-pending slots do not appear in `p2pToteLoadPlans`.
- Every existing pack must match exactly one demand with a present, equal
  `initialPhysicalToteId`; every demand marked initially physical must match exactly one existing
  pack. Reject omissions, extras, mismatched dimensions, and duplicate IDs.
- Extend `BagPlanningResult` with ordered `plannedPackSlots` and `bagSequencePositions`.
- Build immutable constant-time indexes inside `BagPlanningResult` once at construction for bag key,
  correlation, physical pack ID, slot key, and bag position. Its read methods must use those indexes,
  not stream scans.
- Validate that every planned bag ID has exactly one matching slot, every slot references its bag,
  every trace is exactly the subset with a present `initialPhysicalToteId`, every P2P load-plan pack
  is traced, and all published collections/indexes are immutable.
- Do not retain a compatibility constructor that reconstructs logical demand from existing physical
  packs. Update all production and test callers so incomplete planning cannot remain available.

### Station correlation resolution

- Add `ThirdPartyPackCorrelationResolver` with:

  ```java
  String resolve(ThirdPartyVisit visit, ThirdPartyLineWork lineWork, int packOrdinal)
  ```

- `ProductMasterThirdPartyPackPlanFactory` must call it with the station ordinal before creating the
  physical pack. The full-day resolver constructs `PlannedPackSlotKey` from the visit source sheet,
  line reference, and ordinal, then requires the slot's reserved ID and source facts to equal what
  the factory will create.
- Add `CollectedPackCorrelationResolver` with:

  ```java
  String resolve(AdaptedLineRecord collectedLine, int packOrdinal)
  ```

- `DefaultCollectedPackPlanFactory` must resolve each collected ordinal through that interface and
  validate its reserved ID before creating the pack.
- Both resolvers return the assigned `BagKey.correlationId()`. Missing, duplicate, or conflicting
  slots are hard planning/composition errors; do not scan bags, fall back to a line reference, or
  create a new bag at runtime.
- An `ADAPTED_PREPARATION` Third Party pick and the later Adapting collection resolve the same slot,
  reserved pack ID, correlation, and source provenance. The later collection is the established
  transfer/publication of that prepared result into the fulfilment tote, not a second logical slot;
  identical provenance registration remains idempotent.
- Keep convenience constructors for isolated station tests only by supplying a resolver that
  preserves the current line-reference correlation. Full-day production composition must always
  inject the planned-slot resolver.
- `DspPackPlanFactory` registers provenance only when the station actually creates the pack. Do not
  pre-register station-pending slots.

### P2P and outbound boundaries

- `P2pBagCorrelationRequirementCatalogFactory` must derive expected physical pack count and
  fulfilment-sheet membership from `PlannedPackSlot`, not only `PlannedPackTrace`.
- Index a requirement by initial physical tote only for slots with a present
  `initialPhysicalToteId`. Index it by fulfilment sheet for every planned slot.
- The operational request path must union physical-tote and fulfilment-sheet requirements in stable
  encounter order, removing duplicates. This lets an OSR/associated tote carry requirements for
  packs that its Third Party or Adapting visit will create later.
- `P2pWorkloadPlanIndex` validates bag membership against planned slots. It consults the inbound
  manifest catalog only for initially physical slots; a station-pending slot does not require a
  startup manifest.
- Expected pack count is the complete planned slot count. Current successful Third Party and
  Adapting execution therefore closes the same correlation when all reserved packs arrive.
- Do not introduce mutable expected-count adjustment here. Later Exception work must represent a
  terminal no-pack outcome and make the existing planned slot satisfiable without renumbering the
  bag. Until that work exists, a genuinely failed slot remains unfinished rather than being
  reported as successfully closed.
- `OutboundToteAllocationController` keeps its exact planned-versus-actual pack-ID validation. A
  fully successful physical bag still must contain every reserved ID. No empty physical bag or NS
  label is created by this remediation.

### Efficiency and complexity

Runtime efficiency is an implementation acceptance criterion, not a later optimization exercise.

- `DspFullDayInputLoader` and `DspFullDayBagPlanningRequestFactory` remain the sole construction
  owners. Build product, ADAPTED-source, fulfilment-line, manifest-observation, and reserved-ID
  indexes once using method-local insertion-ordered maps/sets, then discard those construction
  indexes after the immutable request/result is published.
- Request construction must be linear in orders, lines, manifests, and generated pack slots. Do not
  search all orders for each line, all manifests for each demand, or all prepared lines for each
  alias.
- `DeterministicBagPlanner` must consume ordered demands in one linear traversal plus linear
  immutable-result publication. Group lookup, duplicate detection, and pack-to-bag association must
  be constant time. Do not create a new list of current bag contents for each capacity check.
- `BagPlanningResult` is the authoritative immutable lookup owner. Build its bag-key, correlation,
  reserved-pack-ID, slot-key, and bag-position indexes once in its constructor and publish detached
  immutable views. Every `find`/`require` lookup used by runtime composition must be constant time.
- `P2pBagCorrelationRequirementCatalogFactory` and `P2pWorkloadPlanIndex` must traverse planned
  bags/slots once during construction and retain immutable indexes. Runtime request lookup must not
  rescan planned bags, slots, traces, manifests, or requirements.
- Third Party and Adapting correlation resolution must perform one exact
  `PlannedPackSlotKey` lookup and constant-time validation. Do not replace the current scan with a
  different frequently executed traversal or allocation.
- Construction-only validation may traverse each input collection once. Do not perform equality or
  fingerprint scans on reads, rebuild indexes after publication, or hide equivalent work in
  snapshots, metrics, logging, or completion evaluation.
- Do not add a global/static cache, thread-local, synchronization, weak map, object pool, duplicate
  mutable runtime index, or cache invalidation lifecycle. The plan is immutable after construction,
  so no runtime invalidation mechanism is required.
- Preserve no-op, rejection, partial-failure, encounter-order, validation, and immutable-publication
  behavior. Efficiency changes must not weaken any of those contracts.
- The external full-day verification must record wall-clock time and compare it with the established
  approximately `1:42` run on comparable inputs/configuration. A regression greater than roughly
  ten percent must be investigated before acceptance; use JFR only if needed to attribute such a
  regression rather than scheduling another broad optimization phase.

## Step 1: Introduce Complete Logical Slot Planning

Inspect production classes:

- `BagPlanningRequest`
- `BagPlanningTote`
- `BagCapacityPolicy`
- `MaximumPackCountBagCapacityPolicy`
- `DeterministicBagPlanner`
- `PlannedBag`
- `PlannedPackTrace`
- `BagPlanningResult`

Inspect test classes:

- `BagPlanningDomainTest`
- `MaximumPackCountBagCapacityPolicyTest`
- `DeterministicBagPlannerTest`
- `DspBagPlanningProvenanceScenarioTest`

Create `PlannedPackSlotKey`, `BagPackDemand`, `PlannedPackSlot`, and
`BagSequencePosition`. Apply all planning API, validation, ordering, immutable-publication, and
constant-time lookup decisions above. Remove the physical-only planning route.

Required test contracts:

- complete demand, not initial physical presence, determines bag membership and capacity splits;
- a bag containing only station-pending slots is planned and has no trace or P2P load-plan pack;
- mixed initial and station-pending slots retain encounter order and one stable bag;
- multiple bags publish correct `1 of y` through `y of y` positions;
- slot failure/non-realisation cannot mutate or renumber the immutable result;
- existing packs and initially physical demand must match exactly;
- duplicate slot keys/IDs and prescription identity conflicts are rejected;
- result lookups return indexed immutable data and all component collections reject mutation.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.bagging.*
```

Proposed commit message: `Plan complete logical bag demand`

## Step 2: Build Full-Day Demand From 12N Fulfilment Semantics

Inspect production classes:

- `DspFullDayInputLoader`
- `DspFullDayLoadedInput`
- `LoadedDspData`
- `DspDatasetAssembler`
- `DspOrderItem`
- `NotionalToteOrder`
- `InboundToteManifest`
- `PreparedLineKey`

Inspect test classes:

- `DspFullDayInputLoaderTest`
- `DspFullDayLoadScaleTest`
- `DspDatasetAssemblerTest`
- `DspBagPlanningProvenanceScenarioTest`

Create `DspFullDayBagPlanningRequestFactory` and move only full-day request construction into it.
Build demands and existing load plans using the fixed FULL_PACK/ASSOCIATED/EMPTY and ADAPTED-source
rules. Keep `DspFullDayInputLoader` responsible for invoking the factory and planner.

Required test contracts:

- a Third Party code-`03` line with picked `0000` produces one station-pending logical slot;
- its later ASSOCIATED code-`02` alias supplies the fulfilment sheet but does not duplicate demand;
- ADAPTED source provenance survives exact prepared-line matching;
- ordinary picked packs remain initially physical and retain their existing IDs;
- multiple physical manifests for one fulfilment sheet retain distinct tote ownership and do not
  duplicate the logical bag demand;
- an ordinary non-Third-Party zero-picked line is rejected as an upstream 12N contract violation;
- an ordinary positive partial count is rejected rather than treated as station-created demand;
- EMPTY fulfilment demand can produce a planned bag without an initial manifest;
- the reproduced shape for line `000243688425` shares prescription
  `20002460000226956` with its eight ordinary packs and receives the deterministic bag correlation;
- mismatched or ambiguous adapted aliases, invalid ordinary picked counts, non-zero Third Party
  picked counts, missing picked manifests, and duplicate reserved IDs fail clearly;
- unresolved-product reporting and executable projection remain unchanged.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadScaleTest --tests online.davisfamily.warehouse.sim.dsp.bagging.DspBagPlanningProvenanceScenarioTest --tests online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssemblerTest
```

Proposed commit message: `Build bag plans from complete 12N demand`

## Step 3: Bind Third Party And Adapting Packs To Planned Slots

Inspect production classes:

- `ProductMasterThirdPartyPackPlanFactory`
- `ThirdPartyPackPlanFactory`
- `ThirdPartyLineWork`
- `ThirdPartyVisit`
- `DefaultCollectedPackPlanFactory`
- `CollectedPackPlanFactory`
- `AdaptedLineRecord`
- `DspPackPlanFactory`
- `DspFullDayAnalysisRuntimeFactory`

Inspect test classes:

- `ThirdPartyPickFlowTest`
- `ThirdPartyStationProcessingControllerTest`
- `ThirdPartyAdaptedCollectIntegrationTest`
- `AdaptingCollectFlowTest`
- `DspFullDayAnalysisRuntimeFactoryTest`

Create the two resolver interfaces, pass absolute line ordinal to them, and compose exact slot-key
lookups from the immutable `BagPlanningResult`. Remove the current full-day correlation scan by
physical trace. Preserve station-local convenience behavior only in explicit test-oriented
constructors.

Required test contracts:

- two ordinals on one line resolve independently and retain their planned bag assignment;
- a Third Party pack receives its reserved ID, planned correlation, and source provenance;
- an adapted collected pack receives its reserved ID, planned correlation, and retained ADAPTED
  source provenance;
- ADAPTED Third Party preparation and later collection resolve the same slot and do not create a
  second demand or identity;
- station-pending slots are not registered before pack creation and are registered exactly when
  created;
- a missing or conflicting slot fails before a physical pack is published;
- the full-day runtime composes both resolvers from one immutable bag plan.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

Proposed commit message: `Bind station packs to planned bag slots`

## Step 4: Publish Complete Correlation Requirements

Inspect production classes:

- `P2pBagCorrelationRequirement`
- `P2pBagCorrelationRequirementCatalog`
- `P2pBagCorrelationRequirementCatalogFactory`
- `P2pWorkloadPlanIndex`
- `DspOperationalReleaseSnapshotFactory`
- `OutboundToteAllocationController`

Inspect test classes:

- `P2pBagCorrelationRequirementCatalogFactoryTest`
- `P2pBagCorrelationRequirementCatalogTest`
- `P2pWorkloadPlanIndexTest`
- `DspOperationalReleaseSnapshotFactoryTest`
- `OutboundToteAllocationControllerTest`

Change correlation requirement and workload validation to use complete planned slots while retaining
physical-tote indexing only for initial packs. Union sheet and tote requirements at the operational
request boundary. Do not relax outbound completeness.

Required test contracts:

- a mixed initial/station-pending bag publishes its complete physical-expected count;
- a station-pending-only bag is discoverable by fulfilment sheet and not by a nonexistent initial
  tote;
- unioning sheet and tote requirements is deterministic and duplicate-free;
- workload publication accepts station-pending slots and still rejects invalid initial-manifest
  joins;
- outbound allocation rejects a successful-looking physical bag missing a reserved physical ID;
- no terminal no-pack waiver or mutable expected-count path is introduced.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadPlanIndexTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationControllerTest
```

Proposed commit message: `Publish complete bag correlation demand`

## Step 5: Prove The Full-Day Third Party Remediation

Inspect test classes:

- `DspFullDayAnalysisRuntimeTest`
- `DspFullDayAnalysisRuntimeFactoryTest`
- `DspFullDayAnalysisScenarioTest`
- `DspFullDayCompletionEvaluatorTest`

Add an integrated fixture with the reproduced relationship: an ADAPTED Third Party source line with
picked `0000`, an ASSOCIATED alias, and ordinary already-picked lines for the same prescription.
Drive the real Third Party continuation into P2P. Assert the created pack uses its reserved slot and
the batch closes only after the complete planned count arrives.

Also prove the preservation boundary with a planning-level station-pending-only prescription: it
has a stable bag and `1 of 1` position before any pack is realised. Do not simulate failure, create
an empty physical bag, or mark it Not Supplied. Future incomplete outcomes must attach to these
unchanged slots and bag positions.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.* --tests online.davisfamily.warehouse.sim.dsp.bagging.* --tests online.davisfamily.warehouse.sim.dsp.thirdparty.* --tests online.davisfamily.warehouse.sim.dsp.adapting.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

User verification after the focused tests are green:

```powershell
.\gradlew test
```

Then rerun the external full-day configuration. The run must pass the former line
`000243688425` correlation failure. Any later failure is new evidence and must not be suppressed or
folded into this plan without review. No JFR capture is required for this functional remediation
unless runtime regresses materially from the established approximately `1:42` reference. Record
the comparable wall-clock duration and investigate a regression greater than roughly ten percent
before accepting the remediation.

Proposed commit message: `Verify complete bag demand flow`

## Acceptance Criteria

- Complete known-product demand fixes bag count, bag ordinal, total bags, and line/ordinal-to-bag
  assignment before runtime.
- The exact source identity `(sourceOrderSheetKey, lineReference, packOrdinal)` resolves every
  later Third Party and Adapting pack in constant time.
- The `000243688425` pack receives the correlation planned for prescription
  `20002460000226956`; no source-sheet physical trace is required before it is picked.
- Existing inbound packs retain their physical IDs, provenance, tote load, order, and correlation
  rewrite behavior.
- Station-pending slots create no physical or provenance state until successful station creation.
- P2P expected counts include every initially physical and station-pending slot, and successful
  mixed initial/pending bags close normally.
- A station-pending-only prescription has a stable logical bag and `x of y` identity before any
  physical pack exists. If future Exception work records every slot incomplete, that same plan is
  retained for its empty Not Supplied bag; the remediation does not pretend the failed packs or
  empty bag already exist.
- No failure can compact bag ordinals, change total bags, or move another slot.
- Unknown-product exclusion/reporting, no-op/rejection behavior, deterministic ordering,
  simulation-thread ownership, and immutable publication remain unchanged except where this plan
  explicitly strengthens validation.
- No global/static cache, thread-local, synchronization, weak map, object pool, fingerprint scan, or
  additional mutable runtime index is introduced. Immutable result indexes are constructed once by
  the authoritative bag-plan owner.
- Request/planner/catalog construction is linear in its input and generated slots, capacity checks
  and retained runtime lookups are constant time, and no equivalent traversal or allocation is
  moved into another frequently executed path.
- A comparable external run does not regress by more than roughly ten percent from the established
  approximately `1:42` wall-clock reference without investigation and evidence explaining the
  difference.
- Focused verification and the complete regression suite are green, and the external run advances
  beyond the former correlation failure.

## Explicitly Deferred To Exception Station Work

- recording fulfilled, short-picked, misplaced, damaged, or otherwise terminal logical-slot
  outcomes;
- reconciling P2P expected counts when a terminal slot produces no physical pack;
- creating a physical empty bag for a completely unfulfilled prescription;
- applying or simulating a Not Supplied label;
- routing that empty bag through an Exception Station and into an outbound tote;
- extending complete capacity planning to unknown-product lines that were projected from executable
  data;
- reconstructing ordinary wholesaler-unfulfilled lines that were omitted from 12N by consuming OSU
  or ASN data directly;
- rendering or printing bag labels.

The Exception plan must consume the immutable `PlannedPackSlot` and `BagSequencePosition` contracts.
It must not reconstruct bag membership from successful packs or renumber the preserved plan.
