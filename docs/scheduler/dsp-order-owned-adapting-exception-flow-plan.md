# Order-Owned Adapting Bins and P2P Exception Handoff Plan

Branch: `feature/dsp-full-day-analysis-metrics-inspection` (or a new feature branch
based on its committed tip). Step 1 is committed at `f2f50ad`; Step 2 is
committed and verified at `ede909f`. Step 3 is committed at `60bd361`; the
user reports its tests green. Step 4 is committed at `1fbaf14` and user
verification is green. Step 5 has uncommitted implementation work; its focused
verification is not yet green (see the Step 5 resumption instructions). The
user starts each step separately.

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

Create `totebag/control/PdcPackDispositionPolicy` with exactly these methods:

```java
boolean bypassPrl(String packId);
int effectivePackCount(String correlationId, int plannedCount);
boolean allowEmptyTote(String toteId);
void collectedAtPdcOutfeed(String packId);
boolean deferInitialPrlAssignments();
long classificationEpoch();
```

Provide one reusable static `noOp()` singleton: it never
bypasses a pack or admits an empty tote, returns the original planned count,
does nothing on the outfeed callback, returns `false` for deferral, and returns
zero for the epoch. Only `collectedAtPdcOutfeed` may mutate policy-owned state.
Create `dsp/analysis/runtime/DspFullDayPdcPackDispositionPolicy` as a thin
adapter over the Step 2 `DspPreparedPackExceptionLedger`; that ledger already
indexes `BagPlanningResult`, so do not build a second bag/pack index. It
delegates the first four methods to the ledger, returns `true` for deferral,
and returns `ledger.snapshot().firstCollectedSheetByOrderId().size()` for the
epoch. This is an O(1), monotonic first-COLLECT count: later COLLECT visits
and PDC collection must not advance it. Do not put DSP imports in the generic
machine package.

Add one policy-bearing overload to the canonical live-input
`ToteToBagFlowController` constructor; all existing constructors delegate to
the no-op singleton. Add a final policy argument to a new
`DspHeadlessP2pLineConfig` constructor and keep its existing constructor as a
delegate supplying the no-op singleton. `DspHeadlessP2pLineRuntimeFactory`
passes the config policy to the new controller overload. Full-day composition
constructs one adapter over its one ledger and supplies that same adapter to
all headless lines; isolated/legacy factory callers retain the no-op policy.

In `ToteToBagFlowController.initializeIfNeeded`, keep the existing
`ToteToBagAssignmentPlanner.createPlans` path unchanged when
`deferInitialPrlAssignments()` is false. When true, mark initialization done
without seeding any PRL, even if assigned correlations are already known.
The existing `synchronizeExpectedCorrelations` retains original positive
planned counts in `knownExpectedPackCountsByCorrelationId`; do not replace
those counts with effective counts or create another full-work-plan scan.
Keep a controller-local set of terminal zero-pack correlation IDs, a map of
effective counts fixed at first claimable-pack assignment, and the last
observed classification epoch. During the existing synchronization traversal,
check effective counts only for newly seen assigned correlations or when the
classification epoch advances. If the effective count is zero, add that
correlation to the terminal zero-pack set and remove it from
`outstandingExpectedCorrelationIds`; every later synchronization must skip
re-adding it. This is how an all-missing bag becomes terminal even though it
will never present a first claimable pack. Do not add it to
`completedCorrelationIds`: no physical bag completed. A later nonzero count
for a terminal zero-pack correlation is an invariant failure. On an epoch
change, a count differing from one already fixed for a PRL assignment is
also an invariant failure, not a silent PRL expected-count rewrite. A
PDC-only snapshot version change must not trigger this classification work.
The line's existing `ToteToBagWorkPlanProvider` limits the traversal to
correlations assigned to that line; do not scan all planned bags or query
another line's assignments. No new set, map, stream, or immutable snapshot is
built per fixed step by these checks.

For a nonempty candidate tote, `canAdmit` uses its existing distinct-
correlation set but excludes packs for which `bypassPrl(packId)` is true from
PRL capacity demand. A physically nonempty tote containing only bypassed
packs is admissible without an idle PRL. For an empty load plan, return true
only when `allowEmptyTote` names its exact physical tote ID; otherwise retain
the legacy rejection. Do not classify a bypassed pack by correlation alone:
another pack of the same bag may be claimable. At PDC, in the existing
`requestPdcDiversions` lane-entry traversal, test `bypassPrl` before
`findOrAssignPrlForCorrelation` and skip PRL assignment/diversion for that
pack. Do not add a second whole-lane traversal or call `getLaneEntries()`
again for exception handling. For a first claimable pack in deferred mode,
`findOrAssignPrlForCorrelation` reads the current `effectivePackCount`, requires
`0 < effective <= original planned count`, assigns its PRL with that fixed
effective count, and retains the count for later epoch validation. Existing
PRL assignments reuse their fixed count. A supposedly claimable pack whose
effective count is zero is an invariant failure. Keep the no-op policy's
existing eager assignment and count behavior unchanged.

After `startPdcTransfersFromActuatingDevices` in the existing update order,
add one outfeed-head drain: repeatedly use
`PdcConveyor.peekLeadingPackAtOutfeed`, stop on an absent or non-bypassed
head, and for a bypassed head require the exact same pack from
`pollLeadingPackAtOutfeed` before calling `collectedAtPdcOutfeed(packId)`
once. A callback failure after the physical poll is fatal; do not retry or
count it twice. This path must never remove a normal claimable pack. Do not
change PDC speed, diversion timings, PRL/PCR/bagger sequencing, sticky line
ownership, or `TippingMachine`; the existing tipper may process an admitted
empty plan and complete its physical tote lifecycle without emitting a pack.

Replace the ledger's per-outfeed full-set copy: `collectedAtPdc` becomes a
simulation-thread-owned mutable `LinkedHashSet` used only for O(1) duplicate
checks and inserts. Preserve no-mutation failure semantics by validating the
misplaced pack and duplicate first, then preparing the next immutable
projection, then inserting the pack ID and publishing that projection.
`P2pMissingPackSnapshot` is currently a record whose public constructor
deep-copies every missing-pack map/set on each call; do not call it for every
PDC pack. Convert it to a final immutable class while preserving its public
six-argument constructor, six accessor names and return types, `empty()`
singleton, and value equality/hash/toString behavior. Its public constructor
still validates and defensively copies every caller-supplied collection.
Add `withPdcCollectedPack(String serviceCentreId)`: it increments version
and only that centre's PDC count, copies and freezes the small per-centre PDC
count map, and shares by reference the already validated immutable missing-
pack map/sets, pending-empty set, missing-count map, and first-COLLECT map
through a private trusted constructor. Never share mutable ledger maps.
`confirmPdcCollection` uses that method, so one outfeed event is O(1) set
work plus O(number of service centres), not O(total missing or already
collected packs). `snapshot()` still returns the cached immutable instance
until a real COLLECT or outfeed mutation occurs; neither fixed-step polling
nor policy queries rebuild it.

Files: create `PdcPackDispositionPolicy` and
`DspFullDayPdcPackDispositionPolicy`; modify `ToteToBagFlowController`,
`DspHeadlessP2pLineConfig`, `DspHeadlessP2pLineRuntimeFactory`,
`DspFullDayAnalysisRuntimeFactory`, `DspPreparedPackExceptionLedger`, and
`P2pMissingPackSnapshot`. Tests: extend `ToteToBagFlowControllerTest`,
`DspHeadlessP2pLineRuntimeFactoryTest`, `DspPreparedPackExceptionLedgerTest`,
and `P2pMissingPackSnapshotTest`;
create `DspFullDayPdcPackDispositionTest`. Cover same-line and different-line
ASSOCIATED sheets, wrong-pack PDC outfeed exactly once with no PRL claim,
partially available bag release, a previously published zero-pack
correlation becoming terminal after first COLLECT without any arriving pack
or physical bag, admitted empty later tote, unchanged debug eager assignment
and empty-tote rejection, and no premature lease quiescence while a bypassed
pack is on PDC. Use a counting policy to prove repeated fixed steps at one
classification epoch do not re-query effective counts or rebuild snapshots;
a PDC-only count change publishes one cheap new snapshot but does not
re-query effective counts, and a newly assigned correlation is checked once.
The partial-bag test uses an isolated `ToteToBagFlowController`/bagger fixture
without a registered `OutboundToteAllocationController` and stops at its
`StoredBagReceiver` output. The headless factory test verifies policy wiring,
not partial-bag allocation through the complete registered controller set.
Step 4, not this step, changes outbound allocation's exact planned-pack
validation. Do not weaken that validation or modify outbound allocation now.
Prove a late effective-count change after PRL assignment fails, and that
successive outfeed snapshots share the immutable classification maps while
old counts stay unchanged and duplicate collection leaves both ledger and
snapshot unchanged. Use state/identity/counter assertions, not wall-clock
thresholds.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspPreparedPackExceptionLedgerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pMissingPackSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest
```

User verification: no additional check for this step.

## Step 4 — Actual bag contents and marked outbound totes

This is the outbound handoff only. Implement the following subsections in
order, then their tests. Steps 1–3 remain authoritative and unchanged. Do not
change PRL/PDC classification, first-COLLECT behavior, scheduling, completion
criteria, workload demand, progress output, labels, or the debug rig. The
Step 5 exception closure state and reporting are **not** part of this step.

### 4.1 Exact immutable allocated-bag partition

Keep `AllocatedOutboundBag` a record. Append these two components, in this
order, after its existing three components:

```java
List<String> actualPhysicalPackIds,
List<String> missingPhysicalPackIds
```

Its five-argument canonical constructor retains all existing planned-bag,
physical-tote, and output-sheet validation. Both new lists must be non-null;
actual must be nonempty. Both lists must contain exact planned IDs, in their
relative `PlannedBag.physicalPackIds()` order. They must be disjoint and
together cover that original list exactly. Reject null/blank/foreign IDs,
duplicates, overlap, reordered IDs, and incomplete partitions with
`IllegalArgumentException`; do not trim or repair caller-supplied pack IDs.

Validate the partition with two integer cursors in one traversal of the
original planned IDs: for each planned ID, consume the next actual ID if it
equals that ID, otherwise consume the next missing ID if it equals that ID,
otherwise fail. At the end both input lists must be fully consumed. Use
`plannedId.equals(candidate)` so null entries fail validation safely. The
planned list already contains unique IDs; this algorithm proves order,
coverage, uniqueness and disjointness without another set or nested
`List.contains` scans. Freeze both lists with `List.copyOf` only after
validation. Retain the original `PlannedBag` reference; never construct a
smaller substitute bag or change its owning sheets, key, or pack list.

Keep the existing three-argument constructor, delegating with the original
planned pack list and `List.of()` for missing IDs. Use a private null-checking
helper when reading that list in the delegating constructor, so a null
planned bag still raises `IllegalArgumentException`. The all-present path
can reuse the already immutable planned list. Add no mutable exception state
to this record; an affected bag is identified by a nonempty missing list.

### 4.2 Allocator preparation before mutation

Add exactly this public overload and have the existing three-argument
`allocate` delegate to it with `Set.of()`:

```java
AllocatedOutboundBag allocate(
        P2pLineId lineId,
        PlannedBag bag,
        Duration allocationTime,
        Set<String> missingPhysicalPackIds)
```

Keep `requireLineAndTime`, null-bag and duplicate-bag checks. Before looking
up/closing an open tote or invoking the tote ID source, output-sheet
allocator, or lifecycle mutation, require a non-null missing set and prepare
the exact ordered partition. Use one private `PackPartition` record holding
the two lists and one private preparation helper. For a nonempty missing
set, traverse this bag's planned IDs once, append each ID to actual or
missing according to set membership, and count matched missing IDs. That
count must equal the supplied set size, rejecting foreign/null/noncanonical
IDs, and actual must be nonempty. With an empty set, use the original planned
list and `List.of()` directly. Do not retain the caller's set or build an
index over other bags. The prepared lists are temporary, bag-local values;
`AllocatedOutboundBag` supplies their defensive immutable boundary.

Pass this partition through both existing allocation branches to
`completeAllocation`. Construct the five-argument `AllocatedOutboundBag`
before the lifecycle assignment loop in that method, then retain the
existing assignment/add/history/capacity-close sequence. This ensures the
new bag-content validation cannot fail after assigning output sheets. Do
not redesign `OutputSheetAllocator` or add a transaction/rollback layer.

Invalid missing IDs and all-missing input must leave allocator and lifecycle
state, cached snapshot identity, tote ID sequence, and output-sheet ordinals
unchanged, including when the incoming bag would otherwise cause a pharmacy
or centre change. This guarantee concerns the new prevalidation failures,
not all possible allocator failures. Existing tests deliberately preserve
published partial creation/allocation/closure when a later injected ID or
lifecycle operation fails; preserve those semantics and cache invalidation
points. Such post-mutation failures remain fatal, not recoverable retries.

Keep maximum capacity measured in physical bags, per-line open-tote
ownership, pharmacy/centre purity, and original-sheet output numbering.
Partial contents do not create another bag or change an output ordinal.
Two bags for one prescription may still occupy different outbound totes.
A pending zero-pack logical bag is never allocated; the all-missing check is
an invariant guard, not a way to fabricate an empty physical bag.

### 4.3 Stored tote exception flag and existing snapshot cache

Keep `OutboundToteSnapshot` a record and append
`boolean requiresExceptionProcessing` after `closureReason`. In its
existing contained-bag validation loop, accumulate whether any bag has a
nonempty missing list and reject a supplied flag that differs from that
derived value with `IllegalArgumentException`. The generated accessor is
therefore O(1), and an empty/unassigned or all-normal tote must be unmarked.
Do not implement the accessor as a stream or repeated contents scan.

Preserve the seven-argument constructor as a delegate that derives the flag
from its supplied contents with a small private loop. Ordinary legacy
contents produce `false`. Partial contents must produce `true`, not be
silently cleared by that compatibility constructor; this clarifies the old
"all-normal" wording. Reject a null list or null bag with
`IllegalArgumentException` in the helper. The public canonical constructor
still validates the complete snapshot. Do not convert this record to a
mutable class or change any existing accessor/closure/purity contract.

Add one boolean to allocator-owned `MutableOutboundTote`, initially false.
After successful `add`, OR it with that bag's nonempty missing list; normal
bags cannot clear it. Pass it to the new eight-argument snapshot constructor
on both open publication and close. New physical totes start unmarked;
another line is unaffected. The flag is a contents-derived cached aggregate,
not a second exception-work ledger.

Retain `OutboundToteAllocator.cachedSnapshot`: allocation and actual close
invalidate it at the existing mutation points; idle reads, rejected
prevalidation, and closing an already idle line do not. Old open/closed
snapshots and bag lists remain immutable after later additions/closure.
`OutboundAllocationSnapshot` needs no change: its existing immutable bags,
totes and indexes already carry the new record values. Do not rebuild it
inside `update` to count exceptions. Step 5 will derive affected allocated
bag and distinct marked-tote counts from that snapshot; do not add those
owners or counters to `DspPreparedPackExceptionLedger` now.

### 4.4 Narrow missing-ID lookup and exact runtime validation

Add a fifth, final constructor argument to a new
`OutboundToteAllocationController` overload:

```java
Function<String, Set<String>> missingPackIdsProvider
```

Validate the provider as non-null. Keep its current four-argument constructor
as a delegate using one static reusable function returning `Set.of()`.
Provider keys are original bag correlation IDs; results are non-null,
stable read-only sets for the simulation-thread allocation call. Do not
import the analysis runtime or mutable ledger into the outbound package,
extend `PdcPackDispositionPolicy`, or create another correlation/pack index.

Capture `completedBagReceiver.getReceivedBags()` once in the controller
constructor as a private final live read-only list view. The current receiver
returns an unmodifiable view over its backing list, not a frozen copy; it
tracks later receives/removals. Do not change `StoredBagReceiver` or cache
`List.copyOf` of that view at construction. In `update`, validate the context,
then return immediately when the view is empty: no time conversion, provider
query, stream, collection copy, or allocator/snapshot call on that path.
For nonempty work, take one `List.copyOf` of the received-bag view, preserve
its encounter order, and calculate allocation time once using the existing
simulation-time conversion. That event-local copy permits safe removal
from the live receiver during the loop.

For each received bag:

1. Use existing indexed `bagPlanningResult.findBagByCorrelationId` to obtain
   its original planned bag. Unknown correlation remains an
   `IllegalStateException` before allocation/removal. Query the provider
   **once** for that correlation and reject a null result as an invariant
   `IllegalStateException`.
2. Compare `runtimeBag.getPackContents()` directly with the planned list
   minus that missing set. Traverse planned IDs once, count/skip registered
   missing IDs, and otherwise require the next runtime pack's `packId()` to
   equal that exact planned ID. Require all runtime packs consumed, matched
   missing count equal to set size, and a positive expected actual count.
   Fail with `IllegalStateException` on a mismatch. Do not construct a
   runtime-ID list, filtered expected list, stream, or temporary set for
   this comparison. This rejects reordered, duplicate, foreign,
   unregistered-absent, and registered-missing-but-present runtime packs,
   as well as an invalid provider set; there is no general subset acceptance.
3. Call the new allocator overload with that same set and original bag,
   then remove that exact runtime bag from the receiver as now. Keep the
   existing fatal check if removal unexpectedly fails after allocation.

Receiver/content failures occur before this bag's allocator mutation or
receiver removal. Earlier valid bags in the same update stay committed; do
not turn the entire batch into an atomic transaction. Do not swallow a
provider/allocator failure or remove the failed bag for a later silent retry.

### 4.5 Headless and full-day composition

In `DspHeadlessP2pLineConfig`, add a final
`Function<String, Set<String>> missingPackIdsProvider` field and accessor of
the same name, and a fifteen-argument constructor appending that provider
after `packDispositionPolicy`. Preserve both current constructors: the
thirteen-argument one continues to use the no-op disposition policy, and
the fourteen-argument one delegates with one static reusable empty-missing
function. The fifteen-argument constructor retains current validation and
rejects a null provider. Isolated/legacy callers still use strict full-pack
outbound validation, even when they explicitly supply a disposition policy.

`DspHeadlessP2pLineRuntimeFactory.build` passes
`config.missingPackIdsProvider()` as the fifth outbound-controller argument;
keep all machinery, registered-controller order, and caller-owned allocator
identity unchanged. Immediately after constructing the existing full-day
exception ledger/adapter, create one typed bound function
`exceptionLedger::missingPackIdsFor` and pass the **same reference** to every
headless line config. The ledger already provides indexed immutable sets;
this call needs no snapshot construction or copied classification map.
Only the simulation-thread outbound controller calls it, never the scheduler
worker. Do not construct a per-line ledger, a function per tick, or query the
ledger while polling an empty receiver. Leave full-day output completion
logic unchanged until Step 5, even though its remaining-work criteria cannot
yet finish an exception-bearing day correctly.

### 4.6 Bounded deterministic proof and change surface

Modify only `AllocatedOutboundBag`, `OutboundToteSnapshot`,
`OutboundToteAllocator`, `OutboundToteAllocationController`,
`DspHeadlessP2pLineConfig`, `DspHeadlessP2pLineRuntimeFactory`, and
`DspFullDayAnalysisRuntimeFactory`, plus the following four existing tests.
Read also `OutboundAllocationSnapshot`, `PlannedBag`, `BagPlanningResult`,
`StoredBagReceiver`, `Bag`, and `DspPreparedPackExceptionLedger` to verify
the reused indexes/views/ownership; do not edit them. Do not create another
test class or broaden the specified verification command.

- `OutboundToteAllocatorTest`: use planned IDs `[p1, p2, p3]` with missing
  `{p2}` to assert the original planned-bag reference, actual `[p1, p3]`,
  missing `[p2]`, immutable lists, and unchanged source/output identity.
  Exercise both allocated-bag constructors, rejecting duplicate/overlapping/
  foreign/null/reordered/incomplete partitions and empty actual contents.
  Test both tote constructors and reject a flag inconsistent with contents.
  Prove a partial bag marks an open tote, normal additions retain the mark,
  and explicit and capacity closure retain it. Old snapshots remain unchanged;
  repeated reads reuse identity. The next physical tote starts unmarked.
  Reject foreign/null/all-missing sets before mutation, also against an
  existing tote of another pharmacy/centre; assert unchanged allocator and
  lifecycle snapshots, and use counting ID-source assertions plus the next
  valid output ordinal to prove no ID/ordinal consumption. Retain existing
  injected post-mutation failure tests without weakening their assertions.
- `OutboundToteAllocationControllerTest`: extend the fixture with an explicit
  provider overload, retaining its current no-provider path. Receive `[p1,
  p3]` for planned `[p1, p2, p3]` and provider `{p2}`; assert the exact
  partition/marked tote and removal only after allocation. For wrong order,
  duplicate/foreign IDs, unregistered absence, registered-missing presence,
  null/foreign provider results, and unknown correlation, assert the failed
  bag remains and cached allocator/lifecycle snapshots are unchanged. Keep
  duplicate-allocation and receiver-order tests. A counting provider is
  called once per received bag and zero times on repeated empty updates;
  a counting receiver getter proves its live view is captured once and later
  receives are still processed. Assert no new allocation on idle updates.
- `MultiLineOutboundToteAllocationTest`: prove a marked tote on one line
  does not mark the other line's normal tote or alter line history. Allocate
  two bags of one prescription and one incoming sheet with capacity one;
  make one bag partial and one normal. They occupy distinct outbound totes
  with sheets 101 and 102, only the partial bag's tote marked, and no
  prescription-wide hold or incoming-sheet change.
- `DspHeadlessP2pLineRuntimeFactoryTest`: retain the Step 3 bypass/zero-bag
  and controller-order tests. Assert old config constructors supply an
  empty-missing provider and the new constructor rejects null/preserves the
  supplied provider reference. Build a bounded headless partial-bag fixture
  with planned `[p1, p2, p3]`, a matching work-plan count of three, a disposition
  policy returning effective count two for that correlation, and a counting
  missing provider returning `{p2}`. Set that policy's deferral to true and
  epoch to a constant one; bypass and empty-tote authorization return false,
  and an outfeed-collection callback fails the test if invoked. Without
  deferral the original eager assignment would incorrectly wait for three.
  The work provider publishes only this correlation and count three. Use
  `BagPlanningResultTestFixtures.complete` with all three original pack traces,
  one original owning sheet `(order-1, 1)`, and an allocator configured with
  that known sheet and capacity four; do not reuse the current empty fixture's
  bag plan or empty output-sheet catalog. Supply that helper with one original
  `ToteLoadPlan` containing all three `PackPlan` values, each with the same
  bag correlation and dimensions `0.02f, 0.01f, 0.008f`; all three traces name
  that plan's same physical input tote ID. The helper derives matching slot
  dimensions from those plans. Use those dimensions for physical packs and
  the existing default placeholder durations. Deliver claimable packs `p1` and `p3`
  through `runtime.sortingMachine().receive`, then advance the existing world
  by `0.05d` steps, at most 2,000 iterations, stopping at the first outbound
  allocation and asserting it occurred. Keep the full registered controller
  set. Assert exact allocated contents, a marked open tote, an empty receiver,
  and one provider call; explicitly close the tote at the world's current
  simulation time using the existing seconds-to-duration conversion and
  assert the mark remains. This must not manually place the partial bag
  straight into the receiver. Keep the zero-effective fixture producing no
  physical bag/tote, not an empty `Bag` (which its constructor rejects).

These are state, identity, and call-count tests, not timing benchmarks. The
normal and partial per-bag preparation/validation work is O(that bag's pack
count), never O(all planned bags/packs). Small defensive lists and the private
partition value are permitted **on allocation events**, not idle ticks.
The canonical tote validation uses its existing bounded bag traversal;
exception-flag reads and mutation-time aggregation are O(1). Snapshot history
rebuilds remain behind the existing mutation cache, not a new fixed-step
operation. Review the full-day bound-function wiring statically in this step;
the real two-sheet COLLECT-to-output and lease/completion proof stays in
Step 5. Do not claim this step alone fixes the stall or proves new run speed.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.outbound.MultiLineOutboundToteAllocationTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeFactoryTest
```

User verification: no additional check for this step.

## Step 5 — Honest provisional completion, workload and reporting

### 5.1 Resumption and unchanged boundaries

The existing Step 5 production and test edits must be preserved, not discarded
or reimplemented. The prior focused run compiled successfully but failed
`DspFullDayAnalysisRuntimeFactoryTest.shouldCompletePartialAndZeroPackExceptionsWhenAssociatedSheetsUseDifferentLines`:
both ASSOCIATED totes were assigned to `dsp-p2p-line-1`. The fixture used
`cross-prescription` and `patient-1` on both incoming sheets. That violates
the trusted upstream patient/prescription containment contract in the lifecycle
requirements and pins the same planned bag correlation to one line. The
test requirement, not that pinning, must change as specified below. Step 5
remains incomplete until the revised focused verification and user checks pass.

On resumption, inspect the existing Step 5 diff against the production contract
below, then repair the integration fixture/tests using 5.4. Do not restart the
feature or overwrite unrelated work. Keep the production implementation if it
meets this contract; only mechanical corrections within the named Step 5 files
are authorized. An unresolved production-contract conflict still requires a
stop/report, not an architectural choice by the implementation model.

Do not change `DeadlineAwareElasticStickyP2pLineAllocationPolicy`, bag-correlation
requirements/assignment/compatibility, lease selection, routes, machine
controllers, or the profile's workload cost/demand merely to make a test use
two lines. Pharmacy affinity is a selection preference, not a permanent
pharmacy-to-line prohibition. A committed bag correlation is pinned to one
line; increasing `desiredLines` does not force the next tote onto another
line. Separate valid prescriptions may use different lines when the existing
allocator permits it, but this step must neither require nor manufacture
that placement. The controlled two-line proof in 5.5 supplies that coverage.

### 5.2 Production contract (unchanged)

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
count confirmed missing packs/zero-pack bags as executable P2P work. Use the
first-COLLECT classification epoch (the immutable first-collected-order map's
size), not the general snapshot version, to invalidate *workload* reuse:
PDC-only count changes cannot change executable pack/bag demand. Completion
and report projections must still observe the general snapshot version so
new PDC collection counts are visible. Do not repeatedly build full bag/pack
maps inside fixed-step evaluation.
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

Efficiency acceptance is unchanged: reuse cached immutable ledger and outbound
snapshots; keep workload invalidation tied to first-COLLECT classification,
and completion/report invalidation tied to actual snapshot changes. Do not add
per-tick order/pack/bag scans, snapshot construction, copied classification
maps, another bag/correlation index, shared mutable worker state, or a new
runtime inspection API to satisfy the revised test. Fixture construction and
bounded test-only assertions are not production hot paths. Retain the existing
identity/state-transition tests; do not replace them with timing assertions.

### 5.3 Change surface and retained tests

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
change, and unchanged progress prefix/console-file parity. Preserve those
existing Step 5 tests. `ElasticRuntimeTestFixture` may retain the supporting
fixture changes already present; do not broaden its production analogue.
Read `DspFullDayBagPlanningRequestFactory`, `DspOrderItem`, `PlannedPackSlot`,
`PlannedPackSlotKey`, `PlannedBag`, and the existing
`DspFullDayPdcPackDispositionTest` when repairing the full-day fixture.
The last class is a read/verification dependency, not a file to rewrite.

### 5.4 Exact full-day integration fixture and assertions

Modify only the Step 5 exception fixture, its helper, and its two added tests
in `DspFullDayAnalysisRuntimeFactoryTest`; retain the other integration tests
and their helpers. Use the existing `sheetOwnedProfile()` without a cost or
line-demand override. Keep `exceptionFixtureInput(profile)`'s existing
product dimensions, preflight, deterministic bag planner, loaded-input
construction, three physical manifests, and source sequence 0/1/2.

Use these exact orders: source `(adapted-source, 1)` of type ADAPTED;
first target `(associated-target, 1)` and later target
`(associated-target, 2)`, both ASSOCIATED. Physical tote IDs remain
`tote-adapted`, `tote-associated-1`, and `tote-associated-2`. Every line has
product `product-a`, quantity one, pharmacy `pharmacy-1`, service centre
`104`, and the identity below:

| Line | Patient | Prescription | Source sheet | Intended fulfilment sheet | Physical pack origin |
| --- | --- | --- | --- | --- | --- |
| A1 | patient-first | first-prescription | adapted-source/1 | associated-target/1 | ADAPTED STORE/COLLECT |
| A2 | patient-partial | partial-prescription | adapted-source/1 | associated-target/2 | ADAPTED STORE/COLLECT |
| B | patient-zero | zero-prescription | adapted-source/1 | associated-target/2 | ADAPTED STORE/COLLECT |
| D | patient-partial | partial-prescription | associated-target/2 | associated-target/2 | Initially in tote-associated-2 |

Build the source's prepared-line list in order `[A1, A2, B]`, using the
four-argument `adaptedLine(line, referenceOrderId, prescriptionId, patientId)`
helper with reference order `associated-target` for each line. Build target
sheet 1's items as `[A1]` and target sheet 2's items as `[A2, B, D]`; their
ADAPTED aliases use that same helper with reference order `adapted-source`.
Source and alias patient/prescription values must match the table. Keep those
ADAPTED helpers' reference sheet at 1: `referenceSheetNumber` is not the target
sheet discriminator. The validated target catalog and planned slots establish
the intended sheet from the target orders. Do not add D to the prepared-line
list or prepared-key set. Construct D directly as:

```java
new DspOrderItem("D", "product-a", 1, "pharmacy-1",
        "patient-partial", "partial-prescription",
        DspOrderLineType.FULL_PACK, "associated-target", 2, 1)
```

The later order stays ASSOCIATED because A2 and B require Adapting. Its
manifest includes all three items; the existing planner creates D's initial
physical pack/load plan and leaves the ADAPTED aliases pending. Do not
manually register provenance, create a substitute planned bag, inject a
runtime bag, mark missing IDs, or confirm PDC collection in this integration
test. The real COLLECT, machine and outbound controllers must produce them.

Apply these mechanical test changes:

1. Rename `shouldCompletePartialAndZeroPackExceptionsAfterPdcCollectionOnOneLine`
   to `shouldCompletePartialAndZeroPackExceptionsWithValidIncomingSheetOwnership`.
   It uses `sheetOwnedProfile()`, the corrected `exceptionFixtureInput`, and
   the corrected completion helper. Remove the failing
   `shouldCompletePartialAndZeroPackExceptionsWhenAssociatedSheetsUseDifferentLines`
   variant and its private `twoLineExceptionProfile()` helper. Do not replace
   it with another full-day test that forces separate lines.
2. Remove `requireDifferentLines` from `assertExceptionFixtureCompletes`, its
   `observedLines`/`differentLinesObserved` tracking, the desired-lines check,
   and all same/different-line assertions. Remove only imports/helpers made
   unused by this repair. Retain helpers/imports used by other tests.
3. Find the three original planned bags by the table's prescription IDs. Resolve
   exact pack IDs using `requirePlannedPackSlot(new PlannedPackSlotKey(sheet,
   line, 1)).reservedPhysicalPackId()`: source sheet for A1/A2/B, later target
   sheet for D. Use these IDs in the following assertions; do not infer pack
   identities from a bag's first/second position or the global slot list.
4. Before runtime creation, assert exactly four slots and three planned bags;
   first bag IDs `[A1]`, partial bag IDs `[A2, D]`, zero-pack bag IDs `[B]`.
   Assert `owningOrderSheetKeys()` is exactly `[associated-target/1]` for the
   first bag and `[associated-target/2]` for each later bag. No patient or
   prescription in this fixture spans the two fulfilment sheets. This proves
   that physical misplacement is not being confused with logical ownership.
5. Advance the real full-day runtime by `1d` while RUNNING, at most 3,000
   iterations, as in the current helper. Do not extend that bound, use sleeps,
   measure wall-clock time, or use a production-day dataset. Assert
   `ALL_SUPPORTED_WORK_COMPLETE`, retaining the current completion/operational/
   elastic failure diagnostics. Do not mandate a particular assigned line.
6. Assert the retained first target load plan contains exactly `[A1, A2, B]`
   in that order. Assert the later load plan contains exactly `[D]`: it receives
   no prepared packs but retains its direct pack. It is **not** an empty tote
   in this scenario. A2 and B remain absent from the later physical plan;
   there is no migration of the misplaced packs back to their intended tote.
7. From the real outbound snapshot, assert exactly two allocated physical bags.
   The first keeps its original `PlannedBag` reference, actual `[A1]`, missing
   `[]`; the partial bag keeps its original reference, actual `[D]`, missing
   `[A2]`. Both retain their original owning sheets. The zero-pack logical bag
   has no outbound allocation (`findAllocatedBag(zeroPackBag.bagKey()).isEmpty()`).
   Assert `openTotesByLine().isEmpty()` and exactly one closed tote is
   exception-marked; its physical ID equals the
   partial bag's allocated outbound physical ID. Do not mandate the total
   number of closed totes, co-location of the two bags, or their line IDs.
8. For centre 104 assert `complete()` and
   `P2P_OUTPUT_CLOSED_WITH_EXCEPTION`, with `missingPackCount = 2`,
   `pdcCollectedPackCount = 2`, `affectedAllocatedBagCount = 1`,
   `markedOutboundToteCount = 1`, and `pendingEmptyBagCount = 1`.
   Together with the exact allocation assertions, these distinguish the
   physical partial bag, the logical zero-pack bag, and both physically
   collected misplaced packs. Do not count the zero-pack bag as allocated or
   claim final Exceptions completion.

### 5.5 Cross-line proof without changing the allocator

Reuse, unchanged, the committed `DspFullDayPdcPackDispositionTest` tests
`sameLineSheetsBypassWrongPacksAndBagOnlyAvailableContents()` and
`differentLinesNeverAssignThePassingWrongBagToTheFirstLine()`; include their
class in Step 5's focused command below. This is the required cross-line
proof, not a claim that the full-day allocator schedules this fixture on two
lines. The existing test deliberately supplies separate line-local work
providers: the first line owns the normal first-sheet bag, and the later
line owns the partial/zero-pack later-sheet bags. Each planned bag has one
intended incoming sheet; the partial bag has one direct later-tote pack plus
one misplaced prepared pack, just as in 5.4.

Those tests exercise real tipping, sorting, PDC bypass/outfeed and PRL/bagger
flow. They assert two misplaced packs collected, no PRL indefinitely holding
them, no cross-line pack migration, a later partial bag containing only its
direct pack, and no zero-pack physical bag. In the different-line variant,
the misplaced packs' passage must not assign or release a later-sheet bag
on the first line. In the same-line variant, the later bag may legitimately
use that line when its direct pack arrives; passage of a misplaced pack
still must not claim a PRL. Their boundary is `StoredBagReceiver`;
the corrected full-day test separately proves outbound allocation and
provisional closure. Do not force production lease assignments, add a test-only
runtime API, or weaken either proof to combine those two boundaries. If the
existing controlled test fails or the corrected bounded full-day fixture
exposes a production-contract conflict, stop/report rather than changing
scheduling, routing, or bag-correlation pinning.

Implementation verification:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluatorTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayCompletionProjectionCacheTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisRunnerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInspectionFormatterTest
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
lines (the valid full-day fixture proves real allocation/closure under the
unchanged allocator, while `DspFullDayPdcPackDispositionTest` supplies controlled
same-line/two-line machine proof; do not require forced full-day line splitting);
partial and zero-pack bag semantics; marked outbound tote persistence;
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
