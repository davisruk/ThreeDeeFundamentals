# Order-Owned Adapting Bins and P2P Exception Handoff Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection` (or a new feature branch
based on its committed tip). Step 1 is committed at `f2f50ad`. Step 2 was
revised after the pre-drain validation and sheet-order discussion; its
implementation has not started. The user starts each step separately.

## Purpose and authority

Implement the newly confirmed production behavior in
`dsp-logical-physical-lifecycle-requirements.md`,
`dsp-operational-scheduling-requirements.md`, and
`docs/machines/exceptions-station-requirements.md`. This plan supersedes the
*physical bin ownership and collection* contract of the completed
`dsp-adapting-sheet-owned-bin-groups-plan.md`; its target-sheet catalog and
pack provenance remain valuable. Do not rewrite that completed historical plan.

The separate `dsp-full-day-blockage-progress-diagnostics-plan.md` Step 1
allocated-bag progress line is already committed. Its Step 2 is deferred,
not a prerequisite here. This plan is not a performance/stall fix, new
service-centre policy, 32R generator, or Exceptions Station implementation.
The 2-ASN/1-12N unmatched-tote scenario remains represented only as its
upstream-reconciled single 12N tote; do not synthesize the unannounced tote.

## Required reading and repository gate

Before **each** step, read `AGENTS.md`, `docs/codex-instructions.md` and its
mandatory document order, this entire plan, the three requirements above,
the completed bin plan's final contract, and the named files/tests in that
step. Record `git status --short` before editing; preserve all prior changes.
If a named file/test is absent, the current source differs materially from
the control flow described here, or an implementation-significant decision
remains, stop and report. Use `apply_patch` for every edit. Run only the
step's implementation-verification command, review its complete diff against
the shared and step contracts, run `git diff --check`, report final status,
and do not commit. Do not advance to another step without the user.

## Shared fixed contracts

1. Keep `PreparedLineKey = (referenceOrderId, lineReference)` and the immutable
   validated target ASSOCIATED/EMPTY sheet catalog. The ADAPTED source sheet
   is provenance, never bin ownership. One physical bin group is
   `(storeId, referenceOrderId)` with one-based linked overflow bins. A line
   still knows its *intended* fulfilment `(orderId, sheetNumber)` through the
   planned pack slot. Never sort physical bin contents by that sheet.
2. All executable ADAPTED source work for a referenced order must finish
   STORE before the first ASSOCIATED/EMPTY COLLECT for that order. This is
   order-wide, not just the collecting sheet's aliases. ADAPTED never waits
   for ASSOCIATED; unrelated orders do not block. Under constrained release
   capacity, eligible ADAPTED candidates precede FULL_PACK and
   ASSOCIATED/EMPTY within the already selected service-centre cohort.
   FULL_PACK and eligible ASSOCIATED/EMPTY may still run concurrently with
   ADAPTED; there is no global preparation phase. Preserve the existing
   service-centre priority and sticky P2P rules.
3. The lowest-numbered executable ASSOCIATED/EMPTY sheet with prepared work
   for an order is its designated first COLLECT sheet; it need not be 001 when
   lower sheets were excluded from executable input. Later sheets of that
   order remain at their operational release boundary until the designated
   COLLECT has committed. All sheets still visit Adapting as in production;
   do not route later sheets directly to P2P. The designated COLLECT drains
   *all* staged bins into its physical tote, regardless of intended incoming
   sheet. Later COLLECT visits receive no already-drained packs and complete
   normally. A late STORE after first drain is an invariant failure.
   No two stores/orders mix. Full-day bin inspection and occupied-bin counts
   reflect the physical order-owned groups; no per-tick bin snapshots or
   new renderables. Legacy debug storage/rig keeps its existing behavior.
4. The full-day P2P implementation must preserve the exact physical pack ID,
   intended incoming sheet, planned bag correlation, and physical tote that
   received it at Adapting. A pack in the wrong incoming sheet is physically
   tipped, travels the sorter/PDC, bypasses every PRL, and is removed only at
   the PDC outfeed into an accounted collection-tote ledger. Do not park it
   on a PRL, assign its bag to that line because it passed the PDC, or move it
   to the other P2P line. The generic debug tote-to-bag path remains unchanged
   through a default no-op disposition policy.
5. For a planned bag with some available packs, the PRL/bagger handles the
   available pack count and produces a physical, labeled bag with exact actual
   pack IDs. Outbound allocation retains the original `PlannedBag`, records
   exact actual and missing pack IDs, and marks every outbound tote containing
   an affected bag. No physical bag is created when **zero** packs are
   available; retain its logical bag key as pending Exceptions work, with no
   P2P outbound-tote assignment. A future Exceptions Station creates/places
   the empty bag. Missing physical packs, affected physical bags, affected
   outbound totes, and pending empty logical bags are distinct counts.
6. The run-wide observation boundary remains provisional P2P output closure.
   A service centre with all actual P2P work drained and output totes closed
   may complete *at that boundary* with a per-centre state
   `P2P_OUTPUT_CLOSED_WITH_EXCEPTION` if known Exceptions work remains.
   This is **not** final DSP/Exceptions completion. Zero-pack logical bags
   count as known pending Exceptions work, not allocated/completed bags or
   unexplained remaining P2P bags. Ordinary exception-free centres retain
   `P2P_OUTPUT_CLOSED`. Deadline outcome remains separate from this state.
7. All mutable bin, missing-pack, PDC-collection, PRL, outbound, and
   completion state is simulation-thread-owned. Precompute stable order/pack
   indexes once from executable input. Use mutation-versioned immutable
   diagnostic projections; do not rescan all orders/packs or construct
   equivalent snapshots each fixed step. Preserve the existing route,
   station-arrival, scheduler evaluation/command, and outbound purity
   boundaries. Do not implement exception resolution, collection-tote
   transport, NS labels, or bag creation at Exceptions in this feature.
8. Full-day COLLECT has a read-only preparation phase before the bin drain:
   validate the entire order group, prospective pack plans/provenance,
   observer decision, replacement tote load plan, and generic continuation.
   Provenance registration is a commit action, never a side effect of pack-plan
   preparation. Only after those checks pass may the simulation thread drain
   bins and publish the replacement plan and provenance, complete the station
   continuation, then publish the ledger decision and first-COLLECT marker.
   An unexpected post-drain failure is fatal, not a recoverable partial
   success; it must not publish first-COLLECT readiness.

## Step 1 — Order-wide preparation eligibility and ADAPTED preference

Create `dsp/adapting/AdaptingOrderPreparationCatalog`: immutable, built once
from executable `LoadedDspData.orders()` plus the already validated
`AdaptingTargetSheetCatalog`. It indexes each referenced order ID to its
distinct prepared-line keys in source encounter order, its one store ID, and
target sheet per key. Reject conflicting store/order aliases and missing
catalog targets before constructing the full-day runtime. A referenced order
with no prepared work has an empty key set; do not fabricate dependencies.

Add an overload to `OperationalDependencyReadinessPolicy` accepting that
catalog; keep the zero-argument constructor's existing behavior for legacy
compositions. For ASSOCIATED/EMPTY candidates in the catalog-enabled mode,
after the active-sheet check, require every key for the *candidate's order
ID* in `snapshot.preparedLineKeys()`. Emit the existing
`ADAPTED_DEPENDENCY` block type naming the missing order/key; do not add
candidate-specific scans of all input lines or change FULL_PACK/ADAPTED
eligibility. Revalidation still uses a fresh operational snapshot.

Create `AdaptedFirstPharmacyGroupedSourceSequenceRankingPolicy` in
`dsp/scheduler/operational`. Follow
`PharmacyGroupedSourceSequenceRankingPolicy`: select the same highest-priority
eligible service-centre cohort, then sort **eligible** ADAPTED candidates
ahead of the other types; within each tier keep existing affinity, group,
source sequence, sheet, order ID, and physical tote ID ties. A blocked ADAPTED
candidate is not a barrier to eligible FULL_PACK work. Use this policy and
catalog only in `DspFullDayAnalysisRuntimeFactory`; keep the generic scheduler
defaults and debug profile unchanged. Change the full-day profile's
eligibility/ranking policy IDs to identify the actual new contracts; do not
claim that the old policy is still running.

Files: create the catalog and ranking policy; modify
`OperationalDependencyReadinessPolicy`,
`DspFullDayAnalysisRuntimeFactory`, and
`DspUncalibratedFullDayProfile`. Tests: create
`AdaptingOrderPreparationCatalogTest`; extend
`OperationalDependencyReadinessPolicyTest`,
`DspOperationalReleaseSchedulerTest`, and
`DspFullDayAnalysisRuntimeFactoryTest`. Prove two ASSOCIATED sheets sharing
one order are both blocked until both sheets' ADAPTED keys are published;
ADAPTED can release first; unrelated order keys do not block; store mismatch
fails at initialization; eligible ADAPTED wins within one centre; blocked
ADAPTED permits FULL_PACK; higher-priority centre selection remains intact.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingOrderPreparationCatalogTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

User verification: no additional check for this step.

## Step 2 — Physical order-owned bins and first-COLLECT transfer

In strict full-day storage only, replace `AdaptingBinId`'s sheet key with
`referenceOrderId`; preserve `storeId` and one-based ordinal. Keep
`AdaptingTargetSheetCatalog` unchanged for intended-sheet provenance. Change
`AdaptingStorageLayout`'s group index to `(storeId, referenceOrderId)` and
retain key-to-bin and staged-line indexes. Validate every STORE visit before
mutation, including group store, unique keys, catalog target, and the catalog
of expected keys. Allocate overflow bins only when the current bin reaches
`linesPerBin`. A late STORE after a completed group drain fails before any
mutation. Cache bin snapshots by mutation version as now. In strict mode,
direct `take`/`takeAll` must reject partial removal; the two-argument legacy
layout retains `stage`, `take`, `takeAll`, and its debug coordinates unchanged.

Use a new four-argument strict `AdaptingStorageLayout(config, storageMap,
targetSheetCatalog, orderPreparationCatalog)` constructor in full-day
composition and strict test fixtures. Remove the old three-argument strict
constructor, which cannot prove order-wide completeness without the catalog;
this is an intentional replacement of the superseded internal API. Keep the
two-argument legacy constructor unchanged. Do not add an all-order scan to
`AdaptingTargetSheetCatalog` or change its lookup API. Extend the already
committed `AdaptingOrderPreparationCatalog` with
`Optional<OrderSheetKey> firstCollectSheetFor(String orderId)`, computed once
as the lowest target sheet among that order's executable prepared keys.
Orders with no prepared keys return empty. For this feature, every executable
ASSOCIATED/EMPTY sheet requiring Adapting has ADAPTED aliases; a FULL_PACK
tote is not a collecting sheet. If sheet 001 is not executable, the lowest
remaining executable sheet (for example 002) is the designated collector.

Add an immutable `AdaptingPreparedOrderGroup` in `dsp/adapting` containing
`storeId`, `referenceOrderId`, the storage mutation version, the ordered
preview records, and whether this is the first collection. In strict mode,
`AdaptingStorageLayout.prepareOrderGroup(storeId, referenceOrderId)` validates
the catalog's complete expected-key set against staged records and returns
that decision without removing a record, bin, or count. The store validates
the visit's sole `profile().pharmacyIds().getFirst()` store, rejects mixed
stores, and checks the order ID. `commitOrderGroup(decision)` rechecks the
exact mutation version and group state, then drains all records in staging
order, updates direct indexes/occupied-bin counts, removes the group, and
marks it collected. It returns the same immutable record list. After that,
preparation and commit for a later visit return an empty immutable list
without reopening the group; a stale decision or missing expected key fails
before drain. Keep `takeOrderGroup(storeId, referenceOrderId)` as a
prepare/commit convenience through `AdaptedLineStore` for direct strict
callers, but full-day completion uses the split preparation and commit APIs.

Extend `AdaptingBenchCompletion` with an optional prepared-order-group
decision and retain its existing two-argument constructor as a delegating
legacy constructor. `AdaptingBench.completeActiveVisit` still stages STORE
as now. For strict COLLECT it stores a read-only preview completion and
enters COMPLETED **without draining**; for legacy COLLECT it keeps the
existing key-specific `takeAll` behavior. `AdaptingVisitFactory` continues
to create COLLECT only when that sheet has ADAPTED aliases. Every later
ASSOCIATED/EMPTY sheet still visits Adapting and receives an empty preview
after the designated drain; do not change route derivation or send it
directly to P2P.

Add an order-local release gate in the existing catalog-enabled
`OperationalDependencyReadinessPolicy`, after its active-sheet and
order-wide STORE-key checks. A candidate for the designated first sheet is
not held by this gate. A later ASSOCIATED/EMPTY sheet is blocked with new
`FIRST_COLLECT_PENDING` `OperationalReleaseBlockType` until the immutable
operational snapshot records that this order's designated sheet completed
COLLECT. Do not hold ADAPTED, FULL_PACK, unrelated orders, or the designated
sheet. Gate OSR and AV02 candidates identically; do not rely on source
sequence, release order, travel times, bench queue order, or a route change
to guarantee who collects first. If the designated collector never commits,
later sheets remain blocked through cutoff, with an observable block reason.
Repeated physical manifests of the same sheet retain their existing active-
sheet assignment rule. The committed-first marker is monotonic, so a command
evaluated against a marker cannot become invalid solely because of this
gate; live inventory/target revalidation remains unchanged.

Use the Step 2 `P2pMissingPackSnapshot` as the immutable gate publication:
add `firstCollectedSheetByOrderId` (order ID to exact `OrderSheetKey`) and
validate that each value has the same order ID as its key. The ledger updates
this map only when the designated first COLLECT's complete commit succeeds.
`DspOperationalReleaseSnapshot` stores a reference to that immutable value;
existing constructors and `fromValidatedCandidateState` delegate to its
empty singleton. Add a final `DspOperationalReleaseSnapshotFactory.create`
overload for the elastic-with-AV02 path accepting this snapshot, and a
`DspOperationalReleaseRuntimeFactory.createElasticWithAv02` overload accepting
`Supplier<P2pMissingPackSnapshot>`; existing signatures delegate with the
empty singleton. Full-day composition alone supplies the ledger's cached
snapshot. The supplier is read on the simulation thread while building the
operational snapshot; the scheduler worker reads only that frozen reference.
Do not deep-copy the cached exception snapshot per candidate or fixed step.

Create simulation-thread-owned `DspPreparedPackExceptionLedger` in
`dsp/analysis/runtime`, initialized once from `BagPlanningResult`, the target
sheet catalog, and `AdaptingOrderPreparationCatalog`. On the designated
first COLLECT, classify each collected physical pack ID against the visit's
incoming `OrderSheetKey`. For a different sheet of the **same** referenced
order, record the physical pack ID, intended sheet, actual receiving tote ID,
planned bag correlation, service centre, and store; derive affected bag/
zero-pack counts from this indexed state. Reject unknown pack IDs, different-
order transfers, duplicate recording, inconsistent bag identity, and an
attempted first drain by a non-designated sheet before ledger or storage
mutation. Later COLLECT does not record another deficit, but records the
visiting physical tote ID against its own sheet so a physically empty tote
with all its planned bag packs missing is explicitly authorized for P2P. The
ledger exposes O(1) pack/bag lookups and a mutation-versioned immutable
summary; it does not construct snapshots during fixed-step polling. Its
committed-first marker is distinct from STORE-key readiness and a bench's
read-only COMPLETED preview.

Create the immutable `P2pMissingPackSnapshot` in `dsp/p2p/allocation` as the
cross-boundary read model: `version`, missing physical pack IDs by `BagKey`,
pending zero-pack bag keys, missing-pack counts by service centre,
PDC-collected-pack counts by service centre, and exact first-collected sheet
by order ID. The ledger caches this snapshot until a COLLECT or PDC outfeed
changes state. It is the only exception-work and first-COLLECT value read by
scheduler/workload evaluation; the mutable ledger never crosses
the worker/snapshot boundary. Affected *allocated* bag and outbound-tote
counts instead come from immutable outbound allocation snapshots, not
duplicated ledger state. Deep-copy and validate its maps/sets once on
construction, and supply a reusable empty singleton at version zero.

The ledger API is `prepareCollect(OrderSheetKey collectingSheet,
PhysicalToteId receivingTote, List<PackPlan> collectedPacks)` returning an
immutable validated decision, followed by `commitCollect(decision)`, plus
`isMisplaced(String packId)`, `missingPackIdsFor(String correlationId)`,
`effectivePackCount(String correlationId, int plannedCount)`,
`allowEmptyTote(String physicalToteId)`,
`confirmPdcCollection(String packId)`, `pendingEmptyBagKeys()`, and a cached
`snapshot()`. `prepareCollect` validates that a nonempty first preview belongs
to the designated sheet and that a later empty visit is for an already
committed order. Its immutable decision carries the ledger version and the
complete prospective ledger change, including the next immutable snapshot;
`commitCollect` rejects a stale decision before mutation, then installs that
change in one simulation-thread commit. Publish the first-collected sheet
only in `commitCollect`, after the replacement load plan, provenance, and
station continuation have been committed; no later work in that completion
may throw after the marker is visible.

Keep `AdaptingAreaController` independent of analysis runtime: add an
`AdaptingCollectObserver` interface in `dsp/adapting` whose
`prepare(OrderSheetKey, PhysicalToteId, List<PackPlan>)` returns a
prevalidated `Runnable` commit action. Existing controller constructors use
a shared no-op observer; only full-day composition supplies an adapter that
calls the ledger's prepare/commit methods. Add immutable
`PreparedCollectedPackPlans` in `dsp/adapting`, containing ordered `PackPlan`
values and exact pack-ID-to-`PackSourceProvenance` entries. Add
`DefaultCollectedPackPlanFactory.preparePackPlans(List<AdaptedLineRecord>)`:
use the existing planned-slot correlation resolver and source facts, but
construct prospective `PackPlan`/provenance values **without** calling
`DspPackPlanFactory.createPackPlan`, which currently registers provenance.
Keep `createPackPlans` and `DspPackPlanFactory` unchanged for legacy callers.
Add read-only `PackProvenanceRegistry.validateBatch(map)` and a corresponding
`registerBatch(map)` that validates before registering; reject duplicate
pack IDs within the prepared batch and conflicting existing registrations.
The full-day commit uses the same registry instance already constructed in
`DspFullDayAnalysisRuntimeFactory`; do not pre-register prepared packs at
runtime construction.

Add a full-day-only `AdaptingAreaController` constructor accepting the
existing area, scheduler state, load-plan registry, concrete
`DefaultCollectedPackPlanFactory`, `PackProvenanceRegistry`, and
`AdaptingCollectObserver`. Retain the existing two- and four-argument
constructors and their legacy completion behavior. Add
`prepareBenchCollect(AdaptingBenchId, ToteLoadPlan)` returning an immutable
`PreparedAdaptingCollect` decision and
`commitBenchCollect(PreparedAdaptingCollect)` returning the prevalidated
observer commit action. Preparation reads the bench's
exact pending strict preview without consuming it, checks visit/store and
registry identity, prepares pack plans, validates the entire provenance
batch, calls the observer's read-only `prepare`, and constructs the
replacement `ToteLoadPlan` from the current plan plus prepared plans.
`ToteLoadPlan` itself rejects duplicate physical pack IDs. No bin, bench,
provenance-registry, load-plan-registry, or ledger mutation occurs in this
phase. A rejected preparation leaves the pending completion and all those
owners unchanged. The existing `applyBenchCompletion` delegates to this
prepare/commit pair for direct strict COLLECT callers, running the returned
observer action after its domain commit; it retains its old STORE and legacy
COLLECT behavior. The station controller uses the split API so its generic
continuation can finish before observer publication.

In `AdaptingStationProcessingController.completeCollect`, keep its existing
claim/current-load-plan identity checks. For a strict preview, call
`prepareBenchCollect`, then `coordinator.validateCanComplete` with the
*prospective replacement plan* before invoking `commitBenchCollect`; keep
the current-plan validation too. Legacy completions retain the existing
path. At strict commit, recheck the exact bench completion, current
registered load-plan identity, and storage version; drain the prepared order
group, register the prevalidated provenance batch, publish the exact
prospective replacement load plan, and consume the bench completion. Return
the prevalidated observer action without running it. The station controller
calls `coordinator.complete` with that replacement plan, then runs the
observer action as the **last** operation of the COLLECT completion. This
ordered sequence is one simulation-thread completion; after the drain, any
unexpected registry, load-plan publication, coordinator, or ledger failure
is fatal rather than recoverable, and no success is returned. The marker is
published last so later sheets cannot release against a partial COLLECT. The
first COLLECT's physical tote ID, not the planned target sheet, is the
receiver recorded for a misplaced pack.

Files: modify `AdaptingBinId`, `AdaptingStorageLayout`, `AdaptedLineStore`,
`AdaptingBenchCompletion`, `AdaptingBench`, `AdaptingAreaController`,
`AdaptingStationProcessingController`, `AdaptingOrderPreparationCatalog`,
`DefaultCollectedPackPlanFactory`, `PackProvenanceRegistry`,
`OperationalDependencyReadinessPolicy`, `OperationalReleaseBlockType`,
`DspOperationalReleaseSnapshot`, `DspOperationalReleaseSnapshotFactory`,
`DspOperationalReleaseRuntimeFactory`, and
`DspFullDayAnalysisRuntimeFactory`; create `AdaptingPreparedOrderGroup`,
`PreparedCollectedPackPlans`, `PreparedAdaptingCollect`,
`AdaptingCollectObserver`, `DspPreparedPackExceptionLedger`, and
`P2pMissingPackSnapshot`. Keep `AdaptingTargetSheetCatalog`, planned slots,
`DspPackPlanFactory`, generic route derivation, and debug rig unchanged.
Tests: update `AdaptingBinIdTest`, `AdaptedLineStoreTest`,
`AdaptingBenchTest`, `AdaptingCollectFlowTest`,
`AdaptingStationProcessingControllerTest`,
`AdaptingOrderPreparationCatalogTest`, `PackProvenanceRegistryTest`,
`OperationalDependencyReadinessPolicyTest`,
`DspOperationalReleaseSchedulerTest`,
`DspOperationalReleaseSnapshotFactoryTest`,
`DspOperationalReleaseRuntimeFactoryTest`, and
`DspFullDayAnalysisRuntimeFactoryTest`; create
`DspPreparedPackExceptionLedgerTest` and `P2pMissingPackSnapshotTest`.
Prove two incoming sheets share one bin chain, the lowest executable sheet
(including 002 when 001 is absent) drains all staged lines, and later sheets
remain blocked through the bench's read-only preview and release only after
the full first-COLLECT commit. The later sheets still visit Adapting and
collect none; overflow links/counts remain correct. Prove that a late STORE,
missing expected key, wrong collecting sheet, duplicate/conflicting
provenance, prospective load-plan failure, and observer-preparation failure
all fail **before drain** without changing bin, provenance, load-plan,
ledger, or pending bench completion. Prove exact wrong-sheet pack, bag, and
receiving-tote identity; a versioned immutable marker reaches the worker
only through the operational snapshot. Preserve key-specific legacy debug
COLLECT and ordinary scheduler behavior outside the full-day catalog mode.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBinIdTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingControllerTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingOrderPreparationCatalogTest --tests online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicyTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspPreparedPackExceptionLedgerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pMissingPackSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

User verification: no additional check for this step. The debug rig must
remain unchanged; broad regression is reserved for Step 5.

## Step 3 — PDC collection and partial/zero physical bag thresholds

Create a generic `PdcPackDispositionPolicy` in `totebag/control` with a
default no-op implementation. Its read-only methods identify an unclaimable
physical pack ID, supply the effective positive pack count for a planned bag
correlation after known missing IDs are removed, and explicitly authorize a
known zero-load tote; its outfeed callback records a collected physical pack
exactly once. Implement the full-day policy as a thin adapter over the Step 2
ledger and bag plan; do not put DSP imports in the generic machine package.
Use the exact interface methods `bypassPrl(String packId)`,
`effectivePackCount(String correlationId, int plannedCount)`,
`allowEmptyTote(String toteId)`, and
`collectedAtPdcOutfeed(String packId)`. Only the last method mutates state.
Add one policy-bearing overload to the canonical live-input
`ToteToBagFlowController` constructor; all existing constructors delegate to
the no-op policy. Add a delegating compatible constructor to
`DspHeadlessP2pLineConfig`; only full-day composition supplies the ledger
adapter to `DspHeadlessP2pLineRuntimeFactory`.

`canAdmit` ignores wrong-sheet pack correlations for PRL capacity, but still
admits a physically nonempty tote containing only wrong-sheet packs; it
admits an empty load plan **only** when the policy names that exact physical
tote as zero-pack exception work. At PDC, skip PRL assignment/diversion for
marked pack IDs. After diversion activity, poll marked leading packs at the
PDC outfeed into the ledger's collection-tote count. Never remove a normal
claimable pack as an exception. Do not change PDC speed, PRL diversion
timings, PCR, or bagger sequencing.

For full-day lines, do not eagerly assign PRLs merely because a bag
correlation was published by a line assignment. The effective count is fixed
when its first **claimable** pack is considered for PDC diversion; at that point the order's
first COLLECT has already classified every missing adapted pack. Validate
`0 < effective <= original planned count`; an effective-zero correlation
enters a terminal logical pending-Exceptions set with **no** PRL/bagger
group, and is excluded from `outstandingExpectedCorrelationIds` even if it
was published before the first COLLECT. Recheck zero correlations only when
the ledger mutation version changes, not by rescanning all planned bags on
every fixed step. Keep
generic debug eager assignment behavior. A late missing-pack adjustment
after the first pack/PRL assignment for that bag is an invariant failure,
never a silent expected-count rewrite. Preserve stable sticky P2P
correlation-to-line assignment; no cross-line pack transfer or holding PRL.
The standard `TippingMachine` may process an admitted empty plan and complete
the physical tote lifecycle without emitting a pack.

Files: create the generic policy and full-day adapter; modify
`ToteToBagFlowController`, `DspHeadlessP2pLineConfig`,
`DspHeadlessP2pLineRuntimeFactory`, full-day composition, and the ledger's
collection callback. Tests: extend `ToteToBagFlowControllerTest` and
`DspHeadlessP2pLineRuntimeFactoryTest`; create
`DspFullDayPdcPackDispositionTest`. Cover same-line and different-line
ASSOCIATED sheets, wrong-pack PDC outfeed exactly once, no PRL claim for that
pack, partially available bag release, zero-available bag with no physical
bag, admitted empty later tote, unchanged debug admission, and no premature
lease quiescence while a marked pack is still on PDC.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest
```

User verification: no additional check for this step.

## Step 4 — Actual bag contents and marked outbound totes

Extend `AllocatedOutboundBag` with immutable ordered
`actualPhysicalPackIds` and `missingPhysicalPackIds` derived from the
original `PlannedBag`. Keep its existing three-argument constructor as a
delegating all-present compatibility path. Reject duplicates, overlap,
foreign IDs, empty actual IDs, and an actual/missing partition that differs
from the planned pack IDs. Add an `OutboundToteAllocator.allocate` overload
accepting the exact missing IDs; preserve the old overload as empty-missing.
Derive `OutboundToteSnapshot.requiresExceptionProcessing` from its contained
allocated bags (also after close), with its existing constructor delegating
to an all-normal value. A zero-pack logical bag never calls this allocator.

`OutboundToteAllocationController` keeps correlation and ordered pack-ID
validation, but compares runtime bag contents with the planned IDs **minus
the ledger's exact missing IDs**. On a match it passes missing IDs to the
allocator, then removes the bag from `StoredBagReceiver` as now. No relaxed
subset acceptance: an unrelated, duplicate, or missing-but-not-registered
runtime pack fails before allocation or receiver removal. Preserve tote
service-centre/pharmacy purity, bag capacity, output sheet derivation, and
close-before-line-release behavior. Count affected allocated bags and marked
outbound tote IDs from successful immutable outbound allocation snapshots;
do not duplicate those owners in the missing-pack ledger.

Files: modify `AllocatedOutboundBag`, `OutboundToteSnapshot`,
`OutboundToteAllocator`, `OutboundToteAllocationController`, headless config/
factory wiring and full-day composition. Tests: extend
`OutboundToteAllocationControllerTest`, `OutboundToteAllocatorTest`,
`MultiLineOutboundToteAllocationTest`, and
`DspHeadlessP2pLineRuntimeFactoryTest`. Assert exact actual/missing pack
partition, normal compatibility, partial bag mark on open and closed tote,
two different outbound totes for one prescription when capacity demands,
zero bag never allocated, and failure leaves receiver/allocator unchanged.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.outbound.MultiLineOutboundToteAllocationTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest
```

User verification: no additional check for this step.

## Step 5 — Honest provisional completion, workload and reporting

Create `DspP2pOutputClosureState` in `dsp/analysis` with
`NOT_CLOSED`, `P2P_OUTPUT_CLOSED`, and
`P2P_OUTPUT_CLOSED_WITH_EXCEPTION`. Add it **per service centre** to
`DspServiceCentreCompletionSnapshot`; retain existing constructor behavior
through a delegating no-exception constructor. Do **not** repurpose the
run-wide `DspCompletionMilestone`/profile string or timetable
`DspServiceCentreCompletionOutcome`: they answer different questions.
Extend `DspFullDayCompletionEvaluator.Observation` and
`DspServiceCentreCompletionSnapshot` with `missingPackCount`,
`pdcCollectedPackCount`, `affectedAllocatedBagCount`,
`markedOutboundToteCount`, and `pendingEmptyBagCount`; all are nonnegative
per-centre counts. A centre may
reach provisional P2P completion only after the normal queues/assignments/
physical tote conditions are zero, every planned bag is either allocated or
explicitly pending as zero-pack Exceptions work, and all wrong-sheet packs
recorded at COLLECT have actually reached PDC collection. When complete,
choose the exception closure state if any of those exception counts is
nonzero; otherwise choose ordinary closure. A centre with pending Exceptions
work must never be described as finally dispatched or Exceptions-complete.

`DspFullDayCompletionProjectionCache` and the factory's
`CompletionSnapshotSource` use the ledger's mutation-versioned immutable
projection alongside `OutboundAllocationSnapshot`: subtract exact registered
missing physical IDs from remaining *P2P* pack work; exclude pending
zero-pack keys from remaining *P2P* bag work, but expose both as separate
exception counts. Subtract missing IDs only for *unallocated* bags when
computing remaining P2P packs, so allocated partial bags are not subtracted
twice. Adjust full-day `closeApplicableOutputs` to regard
allocated plus explicit zero-pack pending keys as terminal at the P2P output
boundary. Add an overload to `P2pWorkloadSnapshotFactory.create` accepting
the immutable `P2pMissingPackSnapshot`; existing overloads delegate with its
empty singleton. Do not
count confirmed missing packs/zero-pack bags as executable P2P work; include
its mutation version in existing reuse/invalidation logic. Do not repeatedly
build full bag/pack maps inside fixed-step evaluation.
Pass the cached projection through a new
`DspP2pElasticAllocationRuntimeFactory.createWithoutArrivalConsumers`
overload accepting `Supplier<P2pMissingPackSnapshot>`; its existing signature
delegates with the empty singleton supplier. Validate non-null snapshots
before evaluation; never read the mutable ledger on the scheduler worker.

Expose the per-centre closure state and separate exception counts in final
JSON/inspection and the routine PT1M progress block. Append a bounded
`MissingPacksByServiceCentre` segment beside the already committed
`AllocatedBagsByServiceCentre` segment without changing the
`ClosedOutboundTotesByServiceCentre:` prefix or existing segments. Do not
count a zero-pack logical bag as an allocated bag. Preserve console/file
mirroring, cutoff timing, deadline outcomes, and uncalibrated labels.
Retain the runner's existing two-argument
`closedOutboundTotesByServiceCentre` helper as a compatibility delegate;
the new three-argument overload accepts per-centre missing-pack counts.

Files: modify `DspFullDayCompletionEvaluator`,
`DspServiceCentreCompletionSnapshot`,
`DspFullDayCompletionProjectionCache`,
`DspFullDayAnalysisRuntimeFactory`,
`DspP2pElasticAllocationRuntimeFactory`,
`P2pWorkloadSnapshotFactory`,
`DspFullDayAnalysisRunner`,
`DspFullDayProgressFormatter`,
`DspFullDayInspectionFormatter`, and
`DspFullDayReportJsonWriter`; create the enum. The existing
`DspFullDayAnalysisReport`/`DspServiceCentreAnalysisResult` already carry
the per-centre completion snapshot: do not add a second mutable report store
or change the global `completionMilestone` field.
Tests: extend `DspFullDayCompletionEvaluatorTest`,
`DspFullDayCompletionProjectionCacheTest`,
`DspFullDayAnalysisRuntimeFactoryTest`,
`P2pWorkloadSnapshotTest`,
`DspP2pElasticAllocationRuntimeTest`,
`DspFullDayProgressFormatterTest`,
`DspFullDayAnalysisRunnerTest`, and
`DspFullDayReportJsonWriterTest`,
`DspFullDayReportFactoryTest`, and
`DspFullDayInspectionFormatterTest`. Cover exception-free exact compatibility,
partial and zero-pack completion, pending PDC-collection preventing closure,
deadline outcome independent of closure state, no bogus physical bag/tote,
cache identity stable without ledger mutation and invalidated once on a real
change, and unchanged progress prefix/console-file parity. In
`DspFullDayAnalysisRuntimeFactoryTest`, add a bounded full-day executable
two-sheet order fixture: one ADAPTED source feeds both sheets, the first
ASSOCIATED COLLECT receives both prepared packs, the second receives none;
run through P2P to prove the wrong-sheet pack reaches PDC collection,
partial/zero-pack outcomes are attributed to the correct bag and centre,
and P2P output closes with exception. Use two assigned P2P lines in a second
variant to prove no cross-line PRL hold or pack migration. Neither variant
uses wall-clock assertions or a production-day dataset.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluatorTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayCompletionProjectionCacheTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInspectionFormatterTest
```

User verification: run `.\gradlew test` as a separate broad regression
check, then a bounded recent-data headless run to inspect progress/JSON and
confirm whether the old stall remains. Do not treat that run as performance
calibration or evidence that the Exceptions Station ran.

## End-of-feature architecture review and documentation closure

After user verification, review the complete feature diff and trace the
production flow, not just type names. Mark PASS/FAIL/UNPROVEN for: order-wide
preparation barrier and ADAPTED preference without global blocking; bin
identity/linked overflow/first-and-later COLLECT; exact intended-sheet
provenance; wrong-sheet PDC outfeed rather than PRL capture on same/different
lines; partial and zero-pack bag semantics; marked outbound tote persistence;
lease quiescence and output close; per-centre exception closure without
final-Exceptions claim; unchanged 12N-only unmatched-tote and debug paths;
and mutation-driven hot-path efficiency. Report unnecessary changes and
unproven real-run behavior. No automatic redesign during review.

Once review and user verification are green, reconcile only verified facts
in the three requirements named above, `docs/codex-context.md`, this plan's
completion record, and the current-position/reading-order text of
`docs/codex-instructions.md`. Replace their explicit implementation-divergence
warnings only for verified implemented behavior; retain the deferred physical
Exceptions Station, 32R, CPF unmatched-tote simulation, and any still
unproven stall cause. Leave completed historical plans intact. If review
finds a materially different implementation, stop for a formal plan revision
before documentation closure.
