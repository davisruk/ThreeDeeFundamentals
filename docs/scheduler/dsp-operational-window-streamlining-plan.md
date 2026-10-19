# DSP Operational-Window Streamlining: Assessment And Correction Plan

Status: assessment and planning only, 2026-10-16. No implementation authorized.
Baseline: clean `7d28bd0ed814e9dd6bd53ce13c4573c7e0fe4b87`.
Execute directly; no agents, commits, matrix runs, JFR or real-data runs unless
the user separately requests them. The user initiates each execution slice.

### Branch And Merge Gate (User Confirmed)

This correction depends on the unmerged scheduler work on
`feature/dsp-full-day-analysis-metrics-inspection`. Do **not** branch from
`master`, merge the scheduler into `master` to obtain a baseline, or cherry-pick
selected prerequisites. When separately authorized to create a branch, create
`feature/dsp-operational-window-streamlining` from the current scheduler HEAD
(`7d28bd0` at planning), carrying the three planning-document changes with it.
If HEAD has advanced, inspect the intervening diff and confirm these contracts
still apply; do not reset to the planning revision. Branch creation, switching,
staging, commits and merges are not authorized by plan revision or a step request.

Neither structural tests nor documentation closure authorize a merge to
`master`. A successful user-run full-day simulation, and explicit user approval,
are required before that merge. Full-day success remains an outstanding programme
gate, distinct from this correction's bounded-state proof. The scheduler branch
need not be merged anywhere to implement or test this correction. Keep
`7d28bd0` as the feature-diff comparison baseline.

## 1. Decision And Scope

The user has explicitly corrected the earlier retention requirements:

- DSP needs incoming work and the current operational window, not a permanent
  record of completed physical work.
- When a physical lifecycle ends, account for it once in aggregate counts and
  remove its operational records, references, indexes and snapshot entries.
- Do not test a candidate against every barcode ever allocated. Trusted scanned
  physical identities do not require a historical uniqueness/security service.
- There is no audit requirement. Any future audit consumer must be separate
  from operational decisions; no audit store, tombstone set, event bus,
  asynchronous audit handoff or audit thread is introduced here.

This decision supersedes *retention* requirements in lifecycle requirements
Sections 6, 16, 17.15, 18 and 19, the sticky/elastic plans' historical-assignment
contracts, and corresponding context text. It does not undo logical/physical
identity separation, current ownership, FIFO, downstream acceptance, bag
membership, pharmacy purity, dependency readiness or simulation-thread ownership.
Those remain physical simulation correctness requirements, not audit features.
Historical implementation plans remain evidence of the old implementation, not
authority to restore history after this correction.

Scope is the shared DSP domain state and its full-day composition under **both**
existing scheduler policies. Small engine changes are limited to removing exact
retired sim/trackable objects. Do not redesign scheduling, machines, transport,
render threading, timing, watermark values or service-centre policy. Exact
inspection-target unregistering for retired visuals is also permitted. Keep the
current 50 ms profile and configured watermark unchanged.

The subsequent user clarification also authorizes correcting physical inbound
supply ordering: ascending input filename is the actual warehouse feed.
Preload and ongoing admission must consume one FIFO in that order. This
supersedes this draft's preservation of ADAPTED-first/centre-priority supply
and its per-batch cursor design. It does not change OSR release scheduling:
station capacity/load, dependencies and the existing release policy still
select departures independently. Eligible FULL_PACK can proceed alongside
ADAPTED; ASSOCIATED retains its preparation dependencies. The user has confirmed
keeping low-water requests for successive service centres and the configured
overnight startup centres. Section 3.9 specifies the gated FIFO and preload
contracts; Steps 5A-5D replace the withdrawn per-batch-cursor design.

### 1.1 Lifetime vocabulary

| Category | Retain | Retirement boundary |
| --- | --- | --- |
| Incoming manifests, logical EMPTY work, future bags | Enough data to execute remaining work | Remove from remaining-work indexes when its obligation finishes |
| Released inbound tote and P2P assignment | Current identity, route, load and ownership | Successful STORE consumption or actual tipper completion |
| Pending station disposition | Exact FIFO handoff and its payload | Downstream acknowledgement, including final CONSUME presentation |
| Packs in storage/PDC/PRL/PCR/bagger | Current physical processing/containment | Successful ownership handoff; never merely source-tote completion |
| Completed bag in receiver | Exact bag until allocation succeeds | Successful allocation and receiver removal |
| Open outbound tote and its bags | Current contents and output-sheet mapping | Outbound closure; OUTBOUND is already terminal in PhysicalToteRecord |
| Missing/zero-pack/collection exception work | Unresolved current obligations and needed identities | Only when its actual obligation ends; this plan does not invent Exceptions completion |
| Aggregate counts and durations | Scalars by configured centre, line and reporting category | Run lifetime; no lists of completed physical identities |
| Loaded immutable input/planning catalogs | Original input and direct-lookup planning values | May remain input-owned for the run; never use them to reconstruct completed runtime history each step |

An inbound tote can be retired while its packs and bags remain live. Closing an
outbound tote ends this simulator's current physical scope, not a claim that
Exceptions, truck loading or 32R has been implemented. Hard cutoff preserves all
unfinished work; it is not a blanket cleanup event.

## 2. Assessment Of The Inspected Implementation

Paths below use `P = app/src/main/java/online/davisfamily/warehouse/sim` and
`T = app/src/test/java/online/davisfamily/warehouse/sim`. Relative module paths
are exact under those roots. Engine paths are written explicitly when needed.

### 2.1 Findings And Consumers

| Owner | Current retention/repeated work | Consumers that must move before removal |
| --- | --- | --- |
| `dsp/lifecycle/PhysicalToteLifecycleLedger` and Snapshot | Every tote plus ended assignment stages; live queries scan assignment history; snapshot rebuild indexes all assignments | Inbound/AV02 controllers, physical release, P2P work, output sheets, completion, metrics, tests |
| `dsp/p2p/lease/P2pLineLeaseRegistry` and CatalogSnapshot | All committed physical assignments survive consumption/handover; each append copies a line's old list; catalog validates all IDs; snapshot lookup flattens all lists | Request creation, live arrival admission, lease retention, full-day completion, line metrics and debug inspection |
| `dsp/osr/OsrPhysicalInventory` and InventorySnapshot | Seen-ID set and complete departed-manifest list, copied on departure/admission | Supply next-pending selection, bootstrap checks, stale release classification, metrics and JSON |
| `dsp/av02/Av02PhysicalToteInventory` and InventorySnapshot | Seen IDs/sheets and departed allocations forever | Allocation suppression, live source identity at P2P, work snapshots, stale release classification, metrics/debug/JSON |
| `dsp/supply/DspServiceCentreSupplyCoordinator` | Next manifest repeatedly searches batch and asks whether each ID is stored/departed; snapshots project every already-admitted manifest | Supply timing/order, authorization, completion and capacity-block reporting |
| `dsp/scheduler/policy/WholeServiceCentreReleaseLedger` | Released obligations and completed tipping obligations remain keyed by IDs | Current release cursor, watermark, completion callback validation; aggregate committed counts must survive |
| `dsp/station/processing/StationProcessingCoordinator` | Terminal consumed-ID set and per-tote last-completion times survive forever | Claim/reclaim guard, continuation timing, exact disposition acknowledgement |
| `dsp/transport/routing/SimulationWorldWarehouseTransportPublisher` | Published-object map, renderable list and world/trackable registration retain consumed totes | Exact-object continuation publication; final CONSUME currently hides but does not unregister |
| `dsp/adapting/MapBackedToteLoadPlanRegistry` | Seeded/updated tote plans never removed | Hydration, station completion and continuation; remove only after last payload consumer |
| `dsp/thirdparty/ThirdPartyAreaController` | Per-tote completions and completed-line IDs remain after visit acknowledgement | ThirdPartyStationProcessingController waits for exact completion before CONTINUE |
| `totebag/control/ToteToBagFlowController` | Observed Pack objects, released groups, completed correlations and expected-count maps accumulate; expected correlations are revisited | P2P activity, work-plan provider and rig visuals; receiver handoff must remain safe |
| `totebag/control/ToteTrackTipperFlowController` and pack visuals | A second observed-Pack map and visual source/renderable maps retain old inputs | Current-tote emission checks and visual containment must stop using historical observation |
| `totebag/machine/BaggingMachine` | Completed correlations, CompletedBag values and runtime Bag/PackPlan contents retained after receiver accepts | Full-day completion copies/scans all completed correlations each step; headless snapshot and rig inspection |
| `dsp/p2p/bag/P2pBagCorrelationAssignmentRegistry` | Append-only correlation ownership snapshots | AssignedLineWorkPlanProvider feeds all past correlations back to machine synchronization |
| `dsp/outbound/OutboundToteAllocator` and AllocationSnapshot | Closed totes, allocated bags, allocated IDs and their containment indexes forever | Remaining bag/pack workload, completion, line/global rates, marked-tote counts and JSON |
| `dsp/outbound/OutputSheetAllocator` | Source/tote and reverse mappings forever; each resolve copies all maps | Live output ownership and deterministic per-source ordinal; ordinal cannot reset while source has future bags |
| `dsp/analysis/runtime/DspFullDayCompletionProjectionCache` | Builds projections over all manifest/planned-bag/allocated-bag history when owners change | CompletionSnapshotSource and cutoff flushing |
| `dsp/analysis/metrics/DspFullDayMetricsCollector` | Derives cumulative totals from historical list sizes and historical owner joins | Progress, final reports and rate deltas must switch to aggregate facts |
| `dsp/analysis/runtime/DspPreparedPackExceptionLedger` | Visited tote IDs and first-COLLECT markers survive completed orders; missing IDs also serve real outstanding work | Empty-tote admission, missing membership, PDC collection, exception-aware completion |
| `dsp/adapting/AdaptingStorageLayout`, scheduler readiness | Collected-group markers and prepared keys remain beyond their last consumer | Later sheets must not repeat first-COLLECT; remove only after order's remaining consumers finish |

`WarehouseTransportInFlightRegistry` already removes entries on successful
arrival. `DspHeadlessWarehouseRouteSensorController` already prunes scheduled
IDs when transport relinquishes them. `StoredBagReceiver` already removes an
allocated bag. Preserve these patterns rather than replacing them.

The engine `SimulationWorld` currently updates every registered SimObject and
`SimulationContext` retains every tracked object. Hiding a tote does not remove
that update/sensor cost. This is a separate source-supported risk, not a measured
attribution of the six-minute pause.

### 2.2 Evidence Strength And Expected Effort

The corrected diagnostic matrix at the baseline used `run-1`,
`run-2-corrected`, `run-3-corrected` beneath
`app/build/reports/dsp-p2p-history-scaling`. Each has 280 samples/40 summaries,
correct run index/order/checksums and complete status. Existing focused XML has
17 passing fixture/probe tests. At 400 pending candidates, request creation rose
from approximately 0.24-0.27 microseconds/candidate with no historical assignments
to 19.6/25.0/20.5 microseconds with 5,000; catalog construction rose from about
0.6-0.7 to 122/143/137 microseconds and from 2,864 to 268,000 bytes/construction.
These measurements justify removing the historical dependence. They do not
measure the other findings, a whole scheduler speedup or the long-run pause.

This is a substantial cross-boundary correction, not a two-file indexing patch.
Most risk is migration of completion/accounting consumers, not collection
removal. The implementation below deliberately moves readers first, then
retires owners, then proves integrated boundedness. Each slice is separately
authorized and focused-verified. Do not give a wall-clock speedup or completion
date estimate from source inspection alone.

### 2.3 Reading And Execution Rules

Follow `docs/codex-instructions.md` and its mandatory order, then read this entire
plan. Additional requirements are
`dsp-logical-physical-lifecycle-requirements.md` and
`dsp-operational-scheduling-requirements.md`. Read the completed sticky/elastic
plans when changing their lease boundaries, and the diagnostic plan for its
measurement limits. The explicit correction in Section 1 takes precedence over
their historical retention clauses. Section 3.9 also supersedes the old
rate-limited supply plan's reordered physical arrival rules; it does not reopen
OSR release ranking or P2P scheduling algorithms.

Older requirements also contain unrelated planned divergences (order-wide
preparation, ranking and output details). Preserve the inspected implemented
behaviour except the explicitly corrected physical inbound feed order; this
plan does not fix every historical documentation inconsistency. The user's
no-audit decision resolves the
retention contradiction explicitly. Report any new contradiction affecting
the selected step rather than guessing a further domain change.

Before an implementation step read its named production owners, their immediate
callers and specified tests, then record `git status --short`. Preserve unrelated
changes and use apply_patch for every edit. New test files are explicitly marked
new. If a named existing file/test is absent, an interface cannot be migrated
within its specified scope, or a new architectural choice appears, stop and
report it. Do not build a guessed compatibility/history layer.

Run only the selected step's stated focused command. At most two mechanical
correction/reverification cycles, then stop. Review the complete step diff,
`git diff --check`, final status, verification and unproven criteria. Do not
invoke matrix/full-day/full-suite/JFR commands as extra model verification.

## 3. Shared Implemented-Target Contracts

### 3.1 One Remaining-Work Authority, Not Another History Store

Create `P/dsp/runtime/operational/DspOperationalWorkLedger.java` and
`DspOperationalWorkSnapshot.java`. This is the smallest explicit replacement for
inferring remaining work from terminal records. It owns **remaining obligations
and aggregate totals only**, not machine state, route selection, capacity,
whole-centre sequencing, current P2P assignments or lifecycle transitions.

The constructor is:

```java
DspOperationalWorkLedger(
    List<DspSchedulerOrderState> orders,
    InboundToteManifestCatalog manifests,
    List<PlannedBag> plannedBags,
    List<P2pLineDefinition> lines)
```

Initialize once before controllers run, in canonical input order. Store references
to immutable plan values, not copies of historical physical journeys. Required
state is: remaining manifested inbound work by PhysicalToteId; unallocated EMPTY
orders by OrderSheetKey; current allocated AV02 identities by PhysicalToteId;
remaining planned bags by BagKey; per-source remaining bag counts; per-order
remaining inbound/EMPTY consumers; aggregate centre/line totals; one cached
immutable snapshot. LinkedHashMap order preserves deterministic source order.
Ended IDs are removed, never moved into another collection. No completed-ID set.

Use these public mutation boundaries, each with a matching read-only
`validate...` method taking identical arguments:

```java
void recordAv02Allocated(Av02AllocatedTote tote);
void recordInboundConsumed(PhysicalToteId id, StationType station,
                          Optional<P2pLineId> line);
void recordBagAllocated(P2pLineId line, PlannedBag bag, Set<String> missingIds);
void recordPendingEmptyBag(BagKey bag);
DspOperationalWorkSnapshot snapshot();
```

Validation names are exactly `validateAv02Allocated`, `validateInboundConsumed`,
`validateBagAllocated`, `validatePendingEmptyBag` and `validateMissingPacks`.
They return void, throw before mutation on invalid current work, and do not
create validation receipts, locks or a transaction framework.

P2P consumption requires a present configured line; Adapting STORE requires an
absent line. Bag accounting removes one remaining bag and its effective pack
count. Missing-pack classification must be applied before allocation; fully
missing bags leave executable remaining work through `recordPendingEmptyBag`,
but remain unresolved exception obligations in the existing exception owner.
Do not count them as allocated physical bags. Prevalidate all affected keys,
counts and checked arithmetic before mutating any map/counter. Missing current
work rejects the operation without mutation; this requires no historical IDs.

Snapshot accessors are `remainingInboundTotes()`, `unallocatedEmptyOrders()`,
`liveAv02Totes()`, `totalsByServiceCentre()` and `totalsByLine()`. The first three
are immutable, deterministic lists of the existing value types. Per-source
`remainingBagsForSource(OrderSheetKey)`,
`remainingInboundConsumersForSource(OrderSheetKey)` and per-order
`remainingConsumersForOrder(String)` are int-returning indexed **ledger**
queries for simulation-thread retirement, not copied snapshot maps.
`remainingBag(BagKey)` is an Optional-returning current ledger lookup for one
planned bag. Do not publish the complete remaining-bag list in hot snapshots.
Totals use nested immutable records: `CentreTotals(long
consumedAtAdapting, long consumedAtP2p, long allocatedBags, long allocatedPacks,
long affectedAllocatedBags)` and `LineTotals(long consumedTotes, long
allocatedBags)`. Initialize zero rows for configured centres/lines. No fields
contain retired IDs. Lookups by current ID and direct counters are indexed;
snapshots are reused unchanged until a relevant mutation.

Here centre rows mean the centres present in the supplied executable orders
or bag plan; reports use zero defaults for timetable/NS-only centres with no
executable row. Add `remainingP2pToteIdsByServiceCentre()` and
`remainingCountsByServiceCentre()` in Step 1, with immutable map/list values.
Resolve route eligibility from the supplied DspSchedulerOrderState values at
initialization, not by consulting terminal lifecycle history later.

Maintain incoming, unallocated-EMPTY and live-AV02 immutable subviews
independently: rebuild only a changed subview. A bag allocation must not copy
all remaining manifests, EMPTY orders, bag values or source-count maps.
The main work snapshot copies only small centre/line count maps and shares
unchanged subviews. Each allocated bag is removed from the mutable
remaining-bag lookup; only aggregate counts enter fixed-step workload publication.

Missing bag membership remains in `DspPreparedPackExceptionLedger` while needed;
the work snapshot exposes executable remaining effective pack/bag counts by
centre as nested `RemainingCounts(int totes, int unallocatedEmptyOrders, int
packs, int bags)`. Reconcile partial missing classifications by updating affected
remaining bags only, before building the next snapshot. Add
`recordMissingPacks(BagKey, Set<String>)` and matching validation for that purpose;
it is a set replacement for one currently remaining bag, not cumulative history.
Retain no missing-ID copy after its remaining bag is removed.

This authority is instantiated once in the full-day composition root and shared
with its producers/readers. No worker mutates it. No generic event/listener
framework is needed: pass this concrete dependency and call it at the named
successful boundaries. Do not add an observer per record type or polling diff
over old snapshots.

Step 1's exact snapshot value types are `List<InboundToteManifest>` for
remainingInboundTotes, `List<DspSchedulerOrderState>` for unallocatedEmptyOrders,
`List<Av02AllocatedTote>` for liveAv02Totes,
`Map<String, List<PhysicalToteId>>` for remainingP2pToteIdsByServiceCentre,
`Map<String, RemainingCounts>` for remainingCountsByServiceCentre,
`Map<String, CentreTotals>` for totalsByServiceCentre and
`Map<P2pLineId, LineTotals>` for totalsByLine. Use a final snapshot class with a
package-private constructor taking these seven arguments in that order; public
accessors return the frozen values, with no mutable ledger reference. No public
constructor is needed: fixtures obtain snapshots through a ledger. Matching
list/map equality alone is not sufficient evidence of reuse; assert identity.

Add `void validateCanAllocateEmpty(OrderSheetKey)` for pre-ID applicability and
count-arithmetic validation; absence rejects without consuming a generator ID.
The matching validateAv02Allocated(tote) additionally validates the actual
generated identity after ID creation and before lifecycle mutation. Keep indexed
isUnallocatedEmpty for candidate/revalidation filtering. Replacing an identical
missing set is a no-op with unchanged snapshot identity; repeated consume,
allocate or pending-empty removal rejects absent current work. No tombstone is
required to distinguish them. Validate route-derived terminal station as well
as configured line; an ADAPTED STORE-only manifest cannot be consumed at P2P.

### 3.2 Migration And API Compatibility

The composition root constructs the work ledger; nested factories never seed
one. Section 3.6 specifies a deliberate repository source-API migration for
composition methods that previously inferred remaining work from history.
Those methods require an explicit final `DspOperationalWorkLedger` argument;
do not retain a convenience factory that silently creates a disconnected ledger.
Expose `operationalWorkLedger()` on sticky and elastic runtimes to share that
exact instance with release/terminal adapters. Isolated low-level domain
controllers retain accounting-absent constructors only as specified below;
they are not an alternative operational composition path.

Do not reconstruct a partially completed runtime from loaded input by silently
reseeding finished work. There is no checkpoint/resume feature. Existing tests
that compose already-progressed state must explicitly provide the corresponding
remaining-work ledger. A default overload must not create a second ledger in a
nested runtime. Add identity assertions for this exact sharing contract.

Historical inspection methods may be removed when all repository callers are
migrated in the owning step. Do not preserve old semantics behind a legacy mode,
empty placeholder history, deprecated archive or feature flag. Keep method
signatures unrelated to retention, routing, command identity and policy stable.

### 3.3 Account, Complete, Retire: Required Ordering

At each boundary: validate the existing physical/ownership transition and the
prospective accounting first; perform the existing successful physical handoff;
apply the prevalidated accounting once; release no-longer-owned references.
Unexpected failures after mutation remain fatal, not caught/retried as another
completion. No rollback/replay framework is introduced.

- AV02 allocation: validate current pending EMPTY work before ID generation and
  lifecycle mutation. After lifecycle/inventory/load-plan acceptance, remove the
  EMPTY from unallocated work and install its live AV02 identity. Pending work,
  rather than departed history, prevents another allocation.
- Inbound terminal callback: prevalidate current claim, lifecycle, accounting,
  live P2P assignment and whole-centre outstanding obligation. Preserve the
  existing lifecycle -> station disposition -> watermark completion ordering.
  Account/remove remaining inbound work and remove the P2P assignment only
  after the entire successful callback chain. No source-tote cleanup removes
  bag pins or packs still processing.
- Final CONSUME acknowledgement: keep the exact disposition payload until
  existing held/closed/invisible presentation is satisfied. Then unregister
  the exact world/publisher/renderable objects, remove the live load plan and
  visit metadata, and acknowledge the FIFO head. Counts are not incremented
  again here. CONTINUE preserves publication, load plan and physical objects.
- Bag allocation: validate planned/actual/missing membership and remaining-work
  accounting before allocation. Preserve successful allocation then exact
  receiver removal. Account the bag, resolve/conditionally retire its correlation
  pin at its last remaining input consumer (Step 8), and release
  obsolete per-pack/per-correlation machine metadata. Open outbound contents
  still retain their required current Bag/PackPlan values until closure.
- Outbound closure: prevalidate output assignments and count increments, close
  once, record closed/marked totals, end/remove output lifecycle assignments,
  retire the output mappings and contents, then publish the compact snapshot.
  Return the closed value to the immediate caller for its same-call lease
  transition; no runtime field retains it afterwards.

The lifecycle ledger may return a terminal value to an immediate caller, but
its next snapshot contains neither that terminal record nor ended assignments.
A pending CONSUME disposition is a live handoff, not permission to retain a
second lifecycle history. A stale worker command uses current availability,
sequence and owner checks; absent work is simply no longer applicable, not an
excuse to search a departed/terminal archive.

An already-issued immutable worker snapshot may naturally outlive a retirement
until its one in-flight evaluation is applied/rejected. Release that reference
through the existing evaluation lifecycle; do not keep a snapshot archive.
Clear last-allocation/last-completion fields that hold retired entity payloads.
Fixed-size scalar diagnostics (sequence, category, counts) do not require a
completed physical ID or object. Input-owned immutable provenance is not a live
tote assignment and must never be used for a historical eligibility check.

### 3.4 Counters And Reports

OSR and AV02 own scalar admitted/departed totals; outbound allocation owns
closed/marked-closed totals by centre/line and closure reason. Add these to
their immutable snapshots. Completed-bag and inbound totals come from Section
3.1 or the machine's scalar discharge count, not list sizes. Keep open outbound
contents in snapshots; do not serialize closed-tote/allocated-bag history.

Preserve existing aggregate progress/report values, deadlines, milestone,
exception counts and cutoff semantics. Bump the JSON report schema from 1 to 2
when removing historical detail. Replace historical arrays with explicit
counts; keep current/incoming arrays under clearly named current-state fields.
Do not emit empty arrays pretending they are complete history. Version 1 old
reports stay readable as files; no report reader/migration implementation is
required. Future 32R cannot depend on retired operational records: it will need
its own separately specified boundary, not this run's history.

### 3.5 Complexity And Proof

Let U be remaining incoming work, A live physical/processing work, C configured
centres/lines, and R work already retired. Setup may traverse loaded input once.
Per-step work/snapshots may depend on U, A and C, never R. Touched-record
mutations may traverse that tote's packs/bags/output sheets. Do not copy an
entire run's maps to validate one new output-sheet pairing. Reuse immutable
state when unchanged. Avoid a cache/index for retired work entirely.

Do not assert that memory is constant while incoming work or unresolved
exception collection genuinely grows. Boundedness means fixed U/A/C and growing
R cannot grow operational collection sizes, traversal counts or snapshot payloads.
Use deterministic state/counter/identity assertions, not wall-clock unit tests,
GC timing, reflective access to private collections or a newly built metrics
framework. Package-local count views for tests are permitted on owning types;
they expose counts only and are not production history APIs.

### 3.6 Exact Composition Migration And Successful-Boundary Calls

Use the existing full-day factory's
`AtomicReference<DspP2pElasticAllocationRuntime>` and `requireElastic` pattern;
no service locator/global. Validate dependencies before controller registration.

1. Construct the work ledger immediately after full-day `schedulerState` and
   `manifestCatalog`, from `schedulerState.snapshot().orderStates()`, manifests,
   `bagPlan.plannedBags()` and `profile.p2pLineDefinitions()`. Append a required ledger
   parameter to the runtime facade's package constructor; store a final field
   and expose `operationalWorkLedger()`. Public full-day create methods remain
   unchanged: that root already owns all initial inputs.
2. In Step 3A append a required final ledger argument to **every existing public**
   `create`/`createWithoutArrivalConsumers` overload of
   `DspP2pStickyLeaseRuntimeFactory` and `DspP2pElasticAllocationRuntimeFactory`.
   Pass it through private terminal methods, runtime constructors and the nested
   elastic-to-sticky call. Preserve all preceding arguments and arrival choices.
   Remove no-ledger signatures: no re-seeding or progressed-state inference.
   Roots with no bag-processing work explicitly seed an empty bag list. Any root
   supplying bag requirements, sticky or elastic, uses its real planned bags.
   Factories read snapshots; they do not account completion.
3. Append the ledger to `createElastic`, `createElasticWithAv02`, `createSticky`
   overloads of `DspOperationalReleaseRuntimeFactory`; require reference identity
   with the passed P2P runtime's ledger before registration. Preserve generic
   non-P2P `create` overloads unchanged. Never construct a work ledger in release.
4. For `Av02AllocationController`, `AdaptingStationProcessingController` and
   `OutboundToteAllocationController`, add one overload of the most complete
   constructor with a final required ledger argument. Private delegation stores
   `Optional<DspOperationalWorkLedger>`; old low-level constructors use empty,
   never null. Add the required final argument to every
   `DspAv02AllocationRuntimeController` constructor in Step 3B; remove its old
   signatures then. Step 2B temporarily adds only the most complete overload.
   In Step 2C add a final `Optional<DspOperationalWorkLedger>` to the most complete
   `DspHeadlessP2pLineConfig` constructor; older constructors delegate with empty.
   The line factory passes a present ledger to its outbound controller's new
   overload. Full-day always supplies present. The runtime roots never accept
   an optional ledger.
5. Step 2B's outer P2P listener initially has its specified three arguments.
   Step 6B replaces them with `(P2pLineId, DspOperationalWorkLedger,
   Supplier<DspP2pElasticAllocationRuntime>, TipperToteCompletedListener)`.
   Full-day passes `() -> requireElastic(elasticReference)`; resolve at callback,
   not construction. Before the delegate validate same ledger, current assignment
   on this line and accounting. Delegate order is lifecycle, station disposition,
   whole-centre watermark when enabled. Then account and retire the assignment.
   Plain elastic uses the same outer wrapper without a whole-centre delegate.
   Do not also account in OperationalLifecycleP2pToteCompletedListener.
6. Add indexed `boolean isUnallocatedEmpty(OrderSheetKey)` in Step 1. AV02 checks
   validateCanAllocateEmpty plus current capacity/ownership before requesting an ID;
   validate generated-ID current collisions before lifecycle mutation. Account
   after lifecycle/inventory/load-plan success. For STORE save immutable source
   identity before consumption; validate accounting, perform STORE/coordinator
   completion, then account with ADAPTING/absent line.
7. Bag ordering: resolve plan; validate exact receiver membership and actual/
   missing partition; validate work accounting and current pin; allocator accepts;
   exact receiver removal; account work; resolve/conditionally retire pin;
   retire source/provenance/
   dependencies in their later owning slices. Expected failures precede allocation.
   A post-mutation invariant failure is fatal, not a retry/no-mutation promise.
   Tests distinguish rejecting delegates from post-mutation fatal failures.
8. `CollectDecision` gains immutable `affectedBagKeys()` and
   `missingPackIdsFor(BagKey)` accessors into its **proposed** snapshot. Observer
   preparation validates exception decision and every affected work change
   before bench mutation. Returned action commits exception decision, replaces
   missing sets for affected remaining bags, then removes fully missing bags
   through recordPendingEmptyBag, in bag-plan order. Never diff full snapshots.

Factory caller files inspected: full-day factory, nested elastic factory,
`T/dsp/p2p/allocation/ElasticRuntimeTestFixture.java`, sticky runtime/scenario
tests, operational release factory test, OSR route-launch scenario, operational
route-target scenario, AV02 operational runtime/allocation/runtime-controller
tests. Re-enumerate exact calls with rg before editing. Each root creates one
ledger before processing and passes it to its producers. Test a nested runtime's
identity, not only the root getter. Component tests may explicitly drive upstream
ledger mutations; end-to-end tests must use production completion adapters.
Mechanical signature migration of those callers is owned by the relevant slice;
other behavior is not. No fallback to history for standalone runtime tests.

### 3.7 Remaining Counts And Dependency Retirement Algorithm

Do not conflate source and target order IDs.
`remainingInboundConsumersForSource(sheet)` counts all unconsumed manifests of
that exact sheet, plus its one logical EMPTY obligation before allocation or
its live AV02 tote afterwards (never both).
`remainingConsumersForOrder(orderId)` counts still-unconsumed ASSOCIATED and
EMPTY fulfilment consumers for that target order, including every sheet and
physical manifest. ADAPTED source consumption at STORE does not decrement its
target order's collector count. Build both indexes from executable input once.
Decrement only at final STORE/P2P consumption; COLLECT/CONTINUE is not final
consumption. EMPTY allocation changes identity, not its outstanding count.
Remove zero-valued per-entity entries; absent count queries return zero.

`RemainingCounts.totes` counts remaining P2P-requiring manifests plus live AV02;
not STORE-only ADAPTED or unallocated EMPTY. The latter has its separate count.
Completion uses **all** remaining manifests, not this P2P-only field. Bag/pack
counts cover effective executable work. `allocatedPacks` counts actual packs;
`affectedAllocatedBags` counts allocations with a nonempty missing set. Each
distinct owning source sheet's bag count decrements once at bag allocation or
pending-empty removal. Remove that bag's missing-set copy with it. Tote
consumption never decrements downstream bags.

In Step 10A create
`P/dsp/analysis/runtime/DspOperationalDependencyRetirement.java`, a concrete
simulation-thread adapter with constructor `(DspOperationalWorkLedger,
DspSchedulerRuntimeState, AdaptedLineStore, AdaptingOrderPreparationCatalog,
DspPreparedPackExceptionLedger)` and methods
`void afterInboundConsumed(PhysicalToteId, OrderSheetKey)` and
`void afterBagResolved(PlannedBag)`. No new counters, history, policy or polling
controller. Add a final required `Supplier<DspOperationalDependencyRetirement>`
to the most complete accounting-aware producer overloads in this slice; isolated
constructors have no callback. Extend only the outer P2P listener,
AdaptingStationProcessingController and OutboundToteAllocationController's
accounting-aware terminal overloads, not Av02AllocationController (allocation
is not retirement). COLLECT captures the same supplier in its existing closure,
with no new observer interface. Full-day uses a construction-time reference like
elasticReference; initialize it before the first world update. A premature
callback fails before physical mutation, not silently skips cleanup.

After accounting, afterInboundConsumed removes that tote's visit marker, then
checks only its saved source sheet and that sheet's order ID. ASSOCIATED/EMPTY
order IDs already are the target IDs. When target collector count is zero,
require no prepared storage and no live COLLECT visit for that target; remove
first-COLLECT/storage-collected markers and the exact prepared keys from
`orderPreparationCatalog.requiredKeysFor(targetId)`. Add
`boolean hasPreparedOrder(String)` and `void retireOrder(String)` on
AdaptedLineStore, delegating to layout's current group index. Layout retirement
rejects live groups and removes only collected markers for that reference order
across configured stores. Do not delete stored packs to make the check pass.
Add `boolean hasLiveVisitForOrder(String)` to the exception ledger using current
visit counts maintained with visit insertion/removal, not a full visit scan.

For each touched sheet, `retireOrderSheet(OrderSheetKey)` removes its scheduler
row only when source-consumer and remaining-bag counts both equal zero.
afterBagResolved checks each distinct owning source sheet with the same rule;
it does not retire target readiness just because a bag allocated. Fully missing
COLLECT removal also calls afterBagResolved. No per-update global sweep. Absent
scheduler rows/collected markers are harmless in cleanup; physical accounting
still rejects absent work. Test bag-first/inbound-first finalization and shared
prepared keys. Add `removePreparedLineKeys(Set<PreparedLineKey>)` to scheduler
state; it invalidates its cached snapshot only if a key was actually removed.

In Step 10B add exception `retireVisit(PhysicalToteId)` (idempotent),
`retireOrder(String)` (reject live visits) and `retireResolvedBag(BagKey)`.
The last requires no pending-empty obligation and all that bag's misplaced
packs confirmed at PDC. Invoke after bag accounting; after PDC confirmation
recheck only that pack's bag if `work.remainingBag(key).isEmpty()`. These hooks
handle either event order without an allocated-bag tombstone. In Step 10A
retireVisit/retireOrder are added without missing-detail pruning; Step 10B
extends cleanup, not duplicate methods. Keep physical collection pack IDs as
genuinely unresolved work in the existing owner. Remove MisplacedPack's unused
`receivingTote` record component entirely: retain pack/intended-sheet/correlation/
bag/centre/pharmacy. The live visit map alone supplies empty-tote admission.
Missing membership can leave the snapshot only when these conditions pass;
cumulative missing/PDC totals never decrease. Pending-empty membership stays.

Add independent `long classificationVersion()` in Step 2C, **before** Step 8
needs it. Keep the six-argument P2pMissingPackSnapshot constructor as a fixture
convenience deriving classificationVersion from its supplied version; append
the explicit value in a new seven-argument constructor. Increment general
version on changes; increment classificationVersion only for missing/effective/
empty-admission changes and pruning, not PDC count-only updates. Preserve shared
subview identity on count-only updates. Never derive epochs from shrinking map
sizes. PDC policy uses the new version. Step 3A removes the workload factory's
obsolete missing-classification cache because effective counts now come from
the work snapshot; do not maintain two competing epochs.

### 3.8 Schema 2 Exact Delta And Scalar Diagnostics

Keep existing JSON names, order, numeric types, duration objects and null rules
except these explicit changes. Schema 1 has no serialized lifecycle-history
array; do not invent one. The Java lifecycle snapshot becomes current-only.

| JSON path | Schema 2 contract |
| --- | --- |
| `schemaVersion` | Integer 2 |
| `termination.hardCutoffClosedTotes` | Remove including nested bag/pack payloads |
| `termination.hardCutoffClosedToteCount` | Add nonnegative long counting only this cutoff action |
| `current.osr.departedToteCount`, `current.av02.departedToteCount` | Preserve names; use cumulative counters |
| `current.supply.serviceCentres[].physicalTotes` | Rename `currentAndIncomingPhysicalTotes`; same row fields, never DEPARTED |
| `current.scheduler.orderStates` | Rename `remainingOrderStates`; unfinished rows after Step 10A |
| `current.operationalRelease.lastPhysicalToteId`, `.releaseDecision` | Remove; preserve evaluation sequence, blocked count and applied/deferred/reason scalars |
| `current.p2pLines[].completedBagCorrelationCount` | Rename `completedBagCount`; cumulative machine scalar |
| `current.p2pLines[].outboundAllocation.closedTotes` | Remove |
| `current.p2pLines[].outboundAllocation.closedToteCount` | Add cumulative per-line long, not repeated global total |
| `current.p2pLines[].outboundAllocation.allocatedBagCount` | Preserve name; use per-line counter |
| `current.p2pLines[].outboundAllocation.openTotes` | Preserve shape; filter to this line; nested allocatedBags are live contents |
| `current.operationalWork` | Add object specified below |

operationalWork has exactly remainingInboundTotes, unallocatedEmptyOrders,
liveAv02Totes, remainingCountsByServiceCentre, totalsByServiceCentre,
totalsByLine. Inbound rows contain physicalToteId/serviceCentreId/orderId/
sheetNumber; EMPTY rows serviceCentreId/orderId/sheetNumber; AV02 rows reuse
av02ToteNode. Count objects use the exact nested-record component names in
Section 3.1. Line-map keys are lineId.value(); order is input/configured order,
not hash iteration. Do not serialize remaining planned bag/pack arrays.
Unfinished identities are a deduplicated canonical-order union of remaining
incoming and current owners. Input catalogs supply labels by direct lookup,
not a scan of past IDs.

In Step 4 change `P/dsp/analysis/DspFullDayCutoffController.java` and
`DspFullDayCutoffSnapshot.java` to retain long hardCutoffClosedToteCount, never
closed OutboundToteSnapshot values. Increment immediately from each present
same-call close result and forget it. Replace the snapshot's fourth record
component with that long; migrate callers, no dummy list constructor. Create
`T/dsp/analysis/DspOperationalWindowCutoffTest.java`: two closures, already-closed
no-result, unchanged subsequent updates and nonzero unfinished work. Step 9
must not leave closed payloads in this separate cutoff owner.

Append required `Supplier<DspOperationalWorkSnapshot>` as the last component
of DspFullDayMetricsCollector.SnapshotSuppliers and required
`DspOperationalWorkSnapshot operationalWork` immediately before `closed` in
DspFullDayAnalysisRuntimeSnapshot. Migrate callers directly; no fabricated empty
snapshot fallback. Step 2A adds per-centre departed long counters to OSR because
Step 5C supply needs them.

Intermediate migration is explicit: Step 4's report writer filters departed
supply rows before Step 5C removes them from the supply snapshot. Its renamed
completedBagCount JSON field initially reads the existing headless snapshot
list's size; Step 8B replaces that Java component with `long completedBagCount`
from the bagger scalar and removes the list. This bridge exists only before
owner removal, not as a final history fallback. Migrate headless snapshot
constructors/callers in 8B. Step 4's full-day snapshot assembly filters scheduler
order rows by the work ledger's indexed source-consumer/bag counts; it retains
other WarehouseSchedulerSnapshot components unchanged. Step 10A later removes
those completed rows from the scheduler owner itself. Do not infer unfinished
rows from legacy logical status. These projections are report boundaries, not
new scheduler mutation or per-bag full-input scans.

Step 5D clears AV02 controller/runtime lastAllocatedTote payloads when the ID
leaves work.findLiveAv02; snapshots/accessor then return Optional.empty().
Step 6A also owns DspOperationalReleaseController and Snapshot for diagnostics:
after an evaluation is applied/rejected retain scalar sequence/result/category/
block count, not an old physical ID or saved command tree. One actual in-flight
immutable evaluation may naturally outlive retirement until apply/reject; no
diagnostic copy/archive. Migrate directly affected formatters/tests.

### 3.9 FIFO Feed, Correlation And Presentation Details

#### Authoritative physical feed (user confirmed)

Ascending input filename order is actual warehouse-to-OSR feed order. Normalize
the full-day input path list once in DspFullDayInputLoader.load, on a copy,
using the same ascending path.getFileName().toString() lexical comparator as
DspFullDayAnalysisCommandParser.expandOrderDirectory. Directory mode already
sorts this way; explicitly listed files must produce the same feed when their
filenames differ. Use stable ordering for equal names, retaining supplied
encounter order; no invented numeric/timestamp sort or new name-security checks.
Keep generic TwelveNDatasetLoader APIs unchanged for other callers.

Preserve encounter order through TwelveNDatasetLoader, DspDatasetAssembler
and DspFullDayInputPreflight into executable physical manifests. Do not flatten
grouped logical orders to rebuild the feed. Repeated physical manifests for
one sheet remain distinct. EMPTY is logical AV02 work, not a physical inbound
queue entry; existing rejected/unsupported-input exclusion remains unchanged.

Use one simulation-thread-owned ArrayDeque of pending physical manifests,
not a queue/cursor per service centre or a search from the beginning. Bootstrap
consumes a prefix and subsequent supply consumes the untouched suffix. The
queue retains only not-yet-admitted manifests, with peekFirst/pollFirst; do not
use ArrayList.remove(0) or move dequeued IDs into history. Immutable input
catalogs may remain input-owned but are not a queue of completed runtime work.

Only inspect the head for due time, current admission gates and capacity.
Validate expected failures, store successfully, then remove exactly that head
and update counts. A blocked/not-due head stays in place; no eligible tote
behind it may pass. OSR departures never advance or repopulate upstream supply.
Arrival timing and no catch-up burst after capacity recovery remain required.
Release decisions remain independent of this arrival FIFO.

DspServiceCentreSupplyPlanFactory must not reorder physical arrival by tote
type or centre priority. Centre groupings/priorities may remain metadata for
reporting/release, not independent physical queues. A1, B1, A2 must remain in
that order even when centre A recurs. This explicitly supersedes the completed
rate-limited supply plan's whole-batch/ADAPTED-first arrival-order requirements.

#### Confirmed overnight preload and low-water requests

The user describes operatives requesting service centres from the wholesaler
in the known feed order. The configured initial centres (currently 104/108)
represent the previous night's releases; their totes are held in OSR when
daytime processing starts. Keep both existing configuration mechanisms; do not
replace startup with an arbitrary capacity-wide selection or remove low-water.
The suggestion that OSR is nearly full overnight is context, not a new calibrated
occupancy requirement, preload target, timing model or hardcoded count of centres.

Preload consumes only the uninterrupted configured-startup-centre prefix of
the filename-ordered physical feed, stopping at capacity or the first other
centre. It never jumps that head to preload a configured centre further back.
Capacity overflow stays at the front of the same pending FIFO. The list of
configured startup IDs authorizes those centres; it does not sort their totes.
If inspection reveals that configured startup centres are not the expected
leading centres for the real feed, report that input/configuration mismatch;
do not silently regroup, fill from other centres or invent a different feed.
An empty startup configuration deliberately preloads nothing. EMPTY authorization
for configured startup centres remains logical and occupies no physical slots.

Keep inclusive low-water gating, occupancy <= config.lowWaterMark(), for
requesting each not-yet-authorized centre. Request at most one new centre per
advance, in first message-encounter order, never by descending priority. A
request authorizes that centre's EMPTY sheets as before and records its time;
the first physical head for that request is due one full configured interval
later. Requesting a centre never admits its first tote in the same advance.
Once requested, that centre's queued totes continue arriving at the configured
rate subject to capacity; every individual tote does not need a new low-water
crossing. Preauthorized startup overflow needs no additional request/low-water
crossing; its first head is due one interval after elapsed zero.

Only the physical FIFO head may arrive. A new, unrequested head waits for the
low-water request. No request for a still later centre may bypass it. The old
"finish all future manifests of the active centre before requesting another"
rule is replaced at an interleaved head: A1/B1/A2 stays A1/B1/A2; A2 cannot be
moved forward, nor prevent B's low-water request. A later return to already
authorized A needs no second request. Centre metadata and per-centre totals
are not independently consumable queues.

Order request metadata by each centre's first logical-message encounter,
including EMPTY-only centres; preserve the existing priority value/issues only
as diagnostics. When the physical head needs a request, authorize the earliest
still-HELD centre in that metadata order, one per advance. An earlier EMPTY-only
centre can therefore get its request without a physical queue entry; the head
still waits until its own request. When the physical queue is empty, remaining
EMPTY-only centres can still be requested one per eligible low-water advance.
Do not fabricate physical manifests or starve logical EMPTY work.

#### Exact FIFO ownership and migration (Steps 5A-5D)

Use a single ArrayDeque<InboundToteManifest>, created by
OsrInventoryBootstrapFactory from data.inboundToteManifests() before preloading.
After each successful bootstrap inventory.store(head), remove that same head.
The returned OsrBootstrapState and supply coordinator retain the same remaining
deque instance. No second feed is reconstructed from membership/history lists.
Only bootstrap, then the simulation-thread coordinator, mutate it; never pass
it to a worker or publish it in an immutable snapshot.

Step 5B appends required Deque<InboundToteManifest> pendingPhysicalTotes to
OsrBootstrapState's existing three record components. The compact constructor
validates non-null deque/elements and retains that exact mutable instance,
like its existing mutable inventory dependency. Remove the old no-deque
constructors and migrate all callers explicitly. Do not seed missing future
work from inventory/departure history. At this transitional boundary keep the
existing startupOverflowPhysicalToteIds input value, derived once from the
remaining deque's configured-startup entries, solely for the old coordinator
initializer until 5C. It must never determine admission order.

Step 5C removes that transitional overflow-ID component/accessor. Final record:
OsrBootstrapState(OsrPhysicalInventory inventory,
Set<OrderSheetKey> authorizedEmptyOrderSheetKeys,
Deque<InboundToteManifest> pendingPhysicalTotes). No constructors silently
invent an empty queue. Pure downstream fixtures with no future supply pass an
explicit empty ArrayDeque; supply fixtures pass their actual remaining feed.
Retain inventorySnapshot(). Re-enumerate/migrate constructor callers with rg;
inspected callers are bootstrap factory, OsrBootstrapStateTest,
DspOsrProcessingReleaseScenarioTest and supply authorization/controller/flow/
rate/bootstrap tests. No new queue wrapper, event framework or history index.

In 5A DspServiceCentreSupplyPlanFactory preserves manifest encounter order
within each centre and orders metadata batches by firstSourceSequenceNumber,
then centre ID for a deterministic tie; remove ADAPTED-first/priority sorting
as a source of requests/arrivals. Keep effective priority and existing issue
classification, immutable metadata and EMPTY-sheet separation. Logical order
grouping never determines the physical deque. All existing factory signatures
remain unchanged. Full-day loader normalizes filenames as specified above;
generic TwelveNDatasetLoader/assembler/preflight contracts remain unchanged.

#### Coordinator application sequence (Step 5C)

Initialize counts once from current bootstrap inventory plus remaining deque;
configured startup centres are already authorized at elapsed zero. Keep one
authorization cursor over centre metadata (fixed C), upstream counts per centre,
preloaded/admitted counts, next head due time and a head capacity-block flag.
There is no physical-tote cursor or deque per batch. Remove physicalToteStates;
future row categories derive from centre authorization plus the current head's
blocked flag when a changed snapshot is actually built. Do not initialize a
runtime map with every already-admitted ID. Keep only currentlyStoredPreloadedIds
for still-stored preload rows; initialize once from inventory and prune against
the current stored index on inventory-snapshot changes. Cumulative preload
counts are separate scalars and never shrink with this current-owner set.

On advance: validate monotonic clock; refresh centre states from current counts;
if a physical head is unrequested (or the queue is empty with unrequested
EMPTY-only work), evaluate low-water, authorize at most the next HELD centre,
schedule the head if it just became authorized, then return. If the head was
already authorized, process due head admissions. Prevalidate counts/arithmetic,
head identity, current inventory admission/capacity; store; pollFirst and assert
the returned reference is the validated head; decrement that centre's upstream
count, increment scalar admissions, clear the old head's blocked flag and
invalidate projections.
Expected rejection/full/not-due changes no queue/accounting. A post-store
invariant failure is fatal, never a retry that dequeues a second tote.

After admission, if the next head is already authorized, schedule it from the
previous due time plus its existing arrival-policy interval and retain normal
due catch-up behavior. If it is unrequested, clear active/due fields and return;
its request occurs on a later advance. When a due head is blocked by capacity,
retain it and its blocked flag; on recovery admit at most that one tote in the
advance, scheduling the following authorized head from current elapsed time
plus a fresh full interval, so no recovery burst occurs. A not-yet-due head
must not be reported capacity-blocked merely because OSR currently is full.

activeInboundServiceCentreId means the authorized physical head's centre, not
an independently selected whole batch; it is absent when no head or a head is
waiting for authorization. nextPhysicalAdmissionElapsedTime follows that head.
HELD_UPSTREAM means never requested; AUTHORIZED means requested with queued
physical work; a non-startup centre becomes SUPPLY_COMPLETE when upstream count
is zero, even if OSR/DSP work remains. Configured startup centres with no upstream
work are PRELOADED while current centre occupancy is positive, then
SUPPLY_COMPLETE at zero; an EMPTY-only authorized centre can be SUPPLY_COMPLETE
without any physical admission. None of these states claims DSP processing done.
Snapshots use current occupancyByServiceCentre and producer counters, not past
manifests. Build physical rows by traversing current stored inventory followed
by the remaining deque, grouping for display in first-encounter centre order;
do not traverse the original per-centre manifest lists to recover old rows.
Cache unchanged immutable views; invalidate on deque/counter/state
changes, not deque identity (the mutable deque instance stays the same).

Track cumulative preloaded/admitted counts per centre, initialized at setup and
incremented only on successful store. Project future rows from the remaining
FIFO and current stored rows from inventory; report grouping cannot reorder
the actual feed. Never traverse dequeued prefixes or original completed input
to rebuild live state. Cache unchanged snapshots. Retain the total-count
invariant preloaded + admittedAfterStartup + upstreamWaiting = physicalManifestCount.
Append `long departedCount` to ServiceCentreSupplySnapshot. Replace its current
list-size-equals-total check with currentAndIncomingRowCount = total - departed;
reject departed rows, duplicate/current-and-future overlap or inconsistent counts.
The existing physicalTotes Java accessor remains but has current/incoming semantics;
JSON uses Section 3.8's new name plus centre `departedCount`. Mechanical fixture
construction migration is owned by 5C. Derive supply completion from actual
remaining queue counts/current occupancy under this policy, not history.
In 5D remove history APIs only after no supply caller uses them.

Correlation pins may outlive physical bag allocation **only if a remaining input
consumer still needs that correlation's routing pin**. Static requirement catalogs
include later split manifests/missing-only fulfilment sheets. Removing the pin
immediately and then re-committing from that catalog would resurrect expected
machine work. Retain only line/centre/correlation plus current transfer/resolution
flags for this live dependency, never the completed bag or tote. Final pin removal
requires executable bag resolved **and** every owning sheet's inbound-consumer
count zero. This is bounded by current/future work, not retired bags; no completed
pin remains when neither condition needs it.

Step 8B's registry stores line/centre directly; existing assignment-taking
constructors extract those values and discard the assignment. Remove
p2pAssignment()/assignment()/physicalToteId() accessors and migrate direct callers;
the line-only constructor has absent centre as in the baseline. Add
`markExecutableResolved(String)` and use work.remainingBag(key) plus owning-sheet
consumer counts to decide retire(String). Snapshot untransferred publication
excludes transferred **or resolved** pins. Existing BagCoherentOperationalP2pReleaseAssignmentCommitter
gains a required final work-ledger argument on all constructors; factory/test
callers migrate. It still validates/commits current tote and pin atomically in
the same order. If it creates a pin for a bag already absent from executable
work, mark that pin resolved in the same prepared commit, before publication;
it must not appear as new expected PRL work. This also covers fully missing
classification before first physical release. Later manifests keep the same
pin and cannot resurrect transferred/resolved groups. After inbound accounting,
check only correlations for that completing manifest/sheet from the immutable
requirement catalog; retire resolved pins whose owner counts are now zero.
At bag resolution perform the same check for that bag. No global correlation sweep.
Add these split/missing-only scenarios to the committer and registry tests.

Make the resolved-flag commit atomic at the existing registry publication seam:
add `prepareCommit(Collection<P2pBagCorrelationRequirement>,
P2pPhysicalToteAssignment, Set<String> executableResolvedCorrelationIds)`.
The bag-coherent committer calculates the third argument by direct remainingBag
queries for its touched requirements before returning its commit. Validate the
set is a subset of those requirements. The returned action installs new pins
with their resolved flags and publishes once, without an intermediate expected-
work snapshot. Keep the registry's existing two-argument convenience delegating
with an empty set for isolated registry tests. Existing transferred/resolved
flags can become true, never reset to false by a later tote commit.

In 8B create the concrete adapter
`P/dsp/p2p/bag/DspOperationalCorrelationRetirement.java` with constructor
`(DspOperationalWorkLedger, BagPlanningResult,
P2pBagCorrelationRequirementCatalog, P2pBagCorrelationAssignmentRegistry)` and
void methods `afterBagResolved(PlannedBag)` and
`afterInboundConsumed(PhysicalToteId, OrderSheetKey)`. It owns only final dependency
references, not another work index/counter. The first marks an existing pin
executable-resolved and checks owning-sheet counts; the second gets only that
source's requirements, finds their immutable bags by correlation ID and retires
eligible resolved pins. Unknown/unpinned correlations are harmless; foreign-line
transfer/physical accounting still rejects. Registry markTransferred and
markExecutableResolved return void; retire returns void and requires an existing
pin. Check find before retirement, not a retired-ID set. Test this adapter in
P2pBagCorrelationAssignmentRegistryTest, including one later missing-only
consumer and subsequent final pin removal. No polling lifecycle manager.

Full-day constructs this adapter immediately after the correlation registry,
before lines. In 8B append it to the outer P2P listener constructor; call
afterInboundConsumed after work accounting and assignment retirement. Step 10A
later appends its dependency-retirement supplier after this adapter. These
domains do not duplicate counters. Save source sheet before the delegate.

Step 8B appends `Optional<P2pBagCorrelationAssignmentRegistry>` then
`Optional<DspOperationalCorrelationRetirement>` after the ledger argument in
the most complete headless config, with empty delegation from isolated
constructors. Full-day supplies both present. Reject only-one-present configs
before registration. The factory wraps the bagger's PackGroupReceiver seam with
CorrelationTrackingPackGroupReceiver `(P2pLineId,
P2pBagCorrelationAssignmentRegistry, PackGroupReceiver)`; every method delegates,
completeIncomingTransfer prevalidates line/pin, delegates, then markTransferred.
Outbound controller adds the most-complete overload with final work ledger then
correlation-retirement adapter; call afterBagResolved after accounting. The line
factory passes both, not a new registry. No direct bagger dependency in flow.

Step 8A SelectionInspectionRegistry.unregister(RenderableObject) removes only
the exact **self-target** registration: null rejects; if getSelectionTarget()
is not the supplied object, return false without removing a shared parent target.
For a self-target remove that identity key and return whether it existed.
Dynamic pack/bag visuals must register as self-targets before they become
retirement-managed. Test a child sharing its surviving parent's target explicitly.
No reference-counted general inspection service is needed.

Presentation callback is registered once, rejects null and a second registration,
and runs after successful domain completion but **before** clearing current-source
observation. When detaching an attached live pack preserve its world pose using
the existing parenting/transform pattern in TipperToSorterPackVisuals; do not
introduce another transform system. retireToteSource never deletes a still-live
downstream pack. retainLivePackVisuals takes the union specified in Step 8 plus
queued/unemitted source-plan IDs; remove a visual only if absent from that union.
Do not clear the observation map in 8A; 8C does so after visual consumers are ready.

### 3.10 Release Diagnostic Projection (Step 6A)

Do not replace lastEvaluation with an empty placeholder and lose block metrics.
Create `P/dsp/runtime/operational/DspOperationalReleaseEvaluationSummary.java`
as record `(int blockedCandidateCount,
Map<String, Map<OperationalReleaseBlockType, Long>> blockCountsByServiceCentre)`.
It contains no candidate/command/entity identity. Before discarding a completed
evaluation, count each block type at most once per blocked candidate, then add
by centre in input centre/enum order. Preserve the collector's current type-to-
metric-category mapping, including types it currently does not classify; do
not invent a new category. Count zero rows need not be stored.

Append immutable `Map<OrderSheetKey, String> serviceCentreBySheet` to the
DspOperationalReleaseController constructor. Release factory builds it once
from initial scheduler input before registering the controller; migrate direct
test callers. It is input-owned label lookup, never an eligibility/history map.
Replace snapshot component `Optional<DspOperationalReleaseEvaluation>
lastEvaluation` with `Optional<DspOperationalReleaseEvaluationSummary>
lastEvaluationSummary`; remove lastPhysicalToteId. Keep the first three
components and Optional<SchedulerCommandApplicationResult>. Initial summary
and result are empty. A completed no-command evaluation clears the prior
application result. Record only generic DEFERRED/REJECTED reason categories
after application, not an entity-bearing exception/diagnostic string.

MetricsCollector reads summary type counts directly (same deduplication and
numeric totals as before), with generic per-type reason text rather than
retained per-candidate reasons. Report/inspection use blockedCandidateCount and
sequence, never saved decisions. The controller still applies the actual local
completed result exactly once before letting it go; no evaluation-source/thread
protocol change. Test multiple blocks of the same type on one candidate, two
centres, applied/deferred/no-command evaluations, and a later scheduler-row
retirement that cannot erase already summarized centre counts. Update the
metrics test in this slice, not only the controller test.

## 4. Ordered Implementation Steps

Steps and their exact verification contracts follow. Intermediate commits are
not requested; the temporary migration state is not the final acceptance state.

### 4.0 Execution Slices: Authorization, Ownership And Verification

The numbered chapters below retain the design contracts; the following leaves
are the **execution steps**. Execute in table order, one leaf per user request.
Do not execute a whole multi-leaf chapter at once. A request for "Step 2" means
2A only; after it report that 2B needs a new request. The same rule applies to
other split chapters. Always report the exact leaf completed and next leaf.
Each leaf inherits its chapter's prerequisites, unchanged boundaries, expected
output and applicable test cases; its allowed files are only the named owners
needed for that leaf, plus their immediate mechanical signature callers/tests
listed in Section 3.6. No whole-chapter cleanup ahead of schedule.

Every retention-removing leaf must include its own repeated-retirement proof:
drive 1,000 small sequential ownership transitions through that owner's public
boundary, keep one unrelated live sentinel, and assert that the sentinel and
current cardinalities survive while only scalar totals increase. Also assert
unchanged-update snapshot identity and rejected-transition no-mutation. These
are ordinary deterministic unit tests, not synthetic history injection or
wall-clock benchmarks. Add them to the existing leaf test class; don't defer
all lifetime proof to Step 11. Count views, if required, are package-private on
the owning type and exercised by its same-package tests. Cross-package tests
use existing current-state snapshots, not reflection or widened debug APIs.

All implementation commands below run from repository root after
`$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'`. Each command is the complete authorized
Gradle invocation for its leaf; no aggregate chapter run or additional test
selection. Step 1, 4 and 11 keep their commands in their chapters. Steps 12/13
have no Gradle command. Two mechanical correction cycles maximum still apply.

| Leaf | Exact responsibility and stop boundary | User verification |
| --- | --- | --- |
| 1 | Section 3.1 values/counts/validators only; no wiring | Chapter 1 result review only |
| 2A | OSR/AV02 departed counters and outbound allocated/closure counters + immutable snapshots; keep all history and existing readers | None |
| 2B | Full-day shared ledger construction/facade; AV02 and STORE accounting; outer P2P callback; preserve existing readers and assignments | None |
| 2C | COLLECT proposed affected-key accounting, classificationVersion/PDC epoch; outbound controller accounting and headless config/factory dependency path | None |
| 3A | P2P work/workload projections, scalar workload value, sticky/elastic/release factory migration in 3.6; no AV02 candidate or completion change | None |
| 3B | AV02 pending candidates/fresh revalidation/runtime constructor migration only | None |
| 3C | CompletionSnapshotSource, closeApplicableOutputs and obsolete projection cache migration only; keep evaluator predicates | None |
| 4 | Metrics, exact schema 2, cutoff scalar and diagnostic serialization; no other owner purge | Review schema examples |
| 5A | Normalize full-day filenames and preserve encounter order in supply metadata; no bootstrap/coordinator change | None |
| 5B | Bootstrap consumes configured FIFO prefix and hands off the same remaining deque; explicit constructor-caller migration only | None |
| 5C | Low-water requests in feed order, head-only admission/timing and current supply projection; remove transitional overflow IDs; retain inventory history until 5D | None |
| 5D | Remove OSR/AV02 history/seen IDs and migrate stale handlers/source/last-value diagnostics; no FIFO redesign | None |
| 6A | Current-only lifecycle/activation, OSR preregistration removal, outbound terminal-stage compatibility and release diagnostics | None |
| 6B | Current-only P2P assignment indexes, request guard removal, outer callback retirement/supplier, catalog reuse and probe correctness assertions | None |
| 6C | Whole-centre pending/tipping obligation retirement and scalar transition diagnostics only | None |
| 7A | Engine exact-instance retirement APIs/phase guard and publisher contract/implementation/test doubles; not yet invoke from CONSUME | None |
| 7B | Wire final CONSUME cleanup; current station/Third Party metadata and load-plan retirement | Visual check deferred to 11 |
| 8A | Presentation callback, tote-source/pack/bag visual reconciliation and exact inspection unregister; preserve current observation lifetimes | Visual check deferred to 11 |
| 8B | Correlation value/pin split, transfer adapter/work-plan publication and bagger scalar; headless completion getters/rig adaptation | None |
| 8C | Prune machine observation/released/count metadata, use 8B untransferred view and 2C classification epoch; zero-pack pin cleanup | Visual check deferred to 11 |
| 9A | OutputSheetAllocator touched-key atomic resolve and explicit retirement APIs; no allocator purge yet | None |
| 9B | Wire source/tote mapping retirement, purge closed output/allocated bags, remove latestClosed and migrate diagnostics/report callers | None |
| 10A | Exact remaining-consumer cleanup adapter, visited/order/prepared/scheduler-row retirement; no missing-detail/provenance pruning yet | None |
| 10B | Two-event missing-detail retirement, remove receiving-tote provenance, retire mutable pack provenance; keep unresolved collection/empty work | None |
| 11 | Integrated real-flow scenarios and empty/non-empty fixed-window proof below; tests only | Full suite, rigs, schema review |
| 12 | Complete feature-diff architecture review | User findings review |
| 13 | Bounded documentation closure | User handoff review |

Focused implementation verification for split leaves:

2A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventoryTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshotTest
```

2B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.OperationalWindowP2pToteCompletedListenerTest
```

2C:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocationControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspPreparedPackExceptionLedgerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pMissingPackSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest
```

3A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactoryTest
```

3B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeControllerTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationControllerTest
```

3C:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayCompletionProjectionCacheTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspOperationalWindowCompletionTest
```

5A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoaderTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyPlanFactoryTest
```

5B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryBootstrapFactoryTest --tests online.davisfamily.warehouse.sim.dsp.osr.OsrBootstrapStateTest
```

5C:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyCoordinatorBootstrapTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyAuthorizationTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspRateLimitedInboundSupplyTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyFlowTest --tests online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

5D:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventoryTest --tests online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalLifecycleP2pToteCompletedListenerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest
```

6A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedgerTest --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundPhysicalToteLifecycleScenarioTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandlerTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollectorTest
```

6B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequestFactoryTest --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.OperationalWindowP2pToteCompletedListenerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingFixturesTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pHistoryScalingProbeTest
```

6C:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedgerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshotTest
```

7A:
```powershell
.\gradlew test --tests online.davisfamily.threedee.sim.framework.SimulationWorldRetirementTest --tests online.davisfamily.warehouse.sim.dsp.transport.routing.SimulationWorldWarehouseTransportPublisherTest
```

7B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinatorTest --tests online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerTest --tests online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationProcessingControllerTest
```

8A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterPackVisualsTest --tests online.davisfamily.threedee.debug.SelectionInspectionRegistryTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest
```

8B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.BaggingMachineTest --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.AssignedLineWorkPlanProviderTest --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.CorrelationTrackingPackGroupReceiverTest --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.BagCoherentOperationalP2pReleaseAssignmentCommitterTest
```

8C:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.ToteToBagFlowControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistryTest
```

9A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocatorTest
```

9B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundGeneratedSheetIntegrationTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest
```

10A:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspOperationalDependencyRetirementTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayConcurrentAdaptingScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspPreparedPackExceptionLedgerTest
```

10B:
```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspPreparedPackExceptionLedgerTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayPdcPackDispositionTest --tests online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistryTest
```

Leaf review must explicitly try to falsify its contract: could code pass the
happy path while retaining ended IDs, clearing live downstream work, counting
twice, or scanning retired input? Identify the test assertion that rejects each
applicable failure. Report anything lacking evidence as UNPROVEN, not PASS from
the presence of a method name. Local helper naming/decomposition is discretionary;
ownership, APIs, mutation order, retirement and cache invalidation are not.

### Step 1 - Remaining Work And Aggregate Value Types

Own only the two new Section 3.1 types, their new
`T/dsp/runtime/operational/DspOperationalWorkLedgerTest.java`, and this execution
record. No runtime wiring or changes to existing owners yet. Read existing
`PhysicalToteLifecycleLedger`, `WholeServiceCentreReleaseLedger`,
`P2pServiceCentreWorkSnapshotFactory`, `P2pWorkloadSnapshotFactory` and
`DspFullDayCompletionProjectionCache` to understand the data being replaced.

Implement Section 3.1, including `recordMissingPacks` and its validation. Expose
only immutable value snapshots. On the ledger, `remainingBagsForSource`,
`remainingInboundConsumersForSource` and `remainingConsumersForOrder` return int
counts; on the snapshot, `remainingCountsByServiceCentre`
returns `Map<String, RemainingCounts>`. Add `findLiveAv02(PhysicalToteId)` and
`findLiveAv02(OrderSheetKey)`, returning Optional of the exact Av02AllocatedTote.
Live AV02 remains from allocation until inbound consumption, including departure.
No waiting/in-flight distinction is duplicated in this ledger.

Test contract (all required):

- Initialize mixed ADAPTED, FULL_PACK, ASSOCIATED and EMPTY work across two
  centres; repeated manifests for one sheet remain separate physical work.
- AV02 allocation removes precisely one unallocated EMPTY and keeps its live
  identity; consumption removes that identity without losing unrelated work.
- STORE and P2P consumption increment the correct centre/line totals; no bag
  or downstream work is removed merely because the source tote finishes.
- Partial missing classification changes remaining effective packs only for
  its bag; allocation removes that bag and counts actual packs/affected bags.
  Fully missing classification uses pending-empty accounting, not allocation.
- Invalid current IDs, wrong terminal station/line, negative/invalid effective
  counts, duplicate current mutations and invalid missing membership leave
  the complete snapshot unchanged. No ended-ID collection is introduced.
- Snapshot immutability, stable identity without mutation, replacement on
  mutation, deterministic order, zero-work centres, per-source/per-order counts.
- Process 1,000 distinct small obligations; completed collections end empty
  while aggregate totals are correct. Do not create a list of processed IDs
  inside the production ledger or timing thresholds inside the test.

Expected output: a tested future/live-work authority, not yet used by controllers.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkLedgerTest
```

User verification: none beyond reviewing the step result. Stop after Step 1.

### Step 2 - Connect Successful Boundaries And Producer Counters

Read the Step 1 types and current full-day composition, AV02 allocation,
Adapting STORE/COLLECT, P2P completion wrappers, outbound allocation/controller
and OSR/AV02 snapshot types. This step adds accounting before any retention is
removed. Do not replace readers yet.

Own `dsp/analysis/runtime/DspFullDayAnalysisRuntimeFactory` and runtime facade,
`dsp/av02/Av02AllocationController`, `DspAv02AllocationRuntimeController`,
`dsp/adapting/AdaptingStationProcessingController`,
`dsp/outbound/OutboundToteAllocator`, `OutboundToteAllocationController`,
`OutboundAllocationSnapshot`, `dsp/osr/OsrPhysicalInventory`,
`OsrInventorySnapshot`, `dsp/av02/Av02PhysicalToteInventory`,
`Av02InventorySnapshot`, and the Step 1 types for mechanical integration only.
Also own `dsp/analysis/runtime/DspPreparedPackExceptionLedger` only for the
affected-key decision accessor/accounting integration below; no pruning yet.
Create `P/dsp/p2p/allocation/OperationalWindowP2pToteCompletedListener.java`.
Constructor: `(P2pLineId, DspOperationalWorkLedger, TipperToteCompletedListener)`.
It prevalidates work consumption, invokes the existing delegate chain, then
records the consumption. In full-day, this wrapper is outside the existing
whole-centre watermark wrapper so no accounting changes if its delegate fails.
Actual P2P assignment retirement is added in Step 6, not prematurely here.

Construct one ledger from executable scheduler orders, manifests, planned bags
and configured lines before controller registration. Expose it through the
runtime facade as `operationalWorkLedger()`. Extend constructor chains with
overloads per Section 3.2; old
isolated constructors delegate to accounting-absent operation, not history
emulation. The operational full-day path must always pass its shared instance.

Wire `recordAv02Allocated` after successful current AV02 allocation,
`recordInboundConsumed` after successful STORE/coordinator completion, and
`recordBagAllocated` after successful allocator acceptance and receiver removal.
Keep the outer P2P wrapper's accounting exactly once. Extend the existing
COLLECT observer: add `affectedBagKeys()` to CollectDecision as an immutable
set of keys touched by that decision; prevalidate missing/empty work accounting
alongside exception validation, then commit both through its existing returned
commit action. Never diff entire historical snapshots to find affected bags.

Add scalar monotonic long `departedToteCount()` to OSR/AV02 snapshots, incremented
only on successful departure. Add to OutboundAllocationSnapshot immutable maps
`closedToteCountsByLine()`, `closedToteCountsByServiceCentre()`,
`markedClosedToteCountsByServiceCentre()` and
`closureCountsByReason()`. The allocator updates them once during closure,
including closure inside allocate. Add total `allocatedBagCount()` and per-line
`allocatedBagCountsByLine()` counters to outbound snapshots for standalone
allocator consumers. No callbacks or second work authority inside the allocator.
Add constructor overloads for the new counters; existing snapshot constructors
derive their initial counters from the explicitly supplied fixture values once.
Keep historical lists temporarily until their readers migrate.

Tests: extend `DspFullDayAnalysisRuntimeFactoryTest`,
`Av02AllocationControllerTest`, `OutboundToteAllocationControllerTest`, and create
`T/dsp/p2p/allocation/OperationalWindowP2pToteCompletedListenerTest.java`.
Prove the same ledger is shared; actual completion counts once; queued/waiting
and failed delegate calls do not consume work; STORE does not remove collected
packs; failed bag membership/allocation changes no accounting; both explicit
and capacity-triggered closes increment counts once. The whole-centre wrapper
must finish before its outer work-accounting callback. Test two pending EMPTY
orders so failure cannot accidentally consume the next obligation.

Expected output: accounting facts agree with old retained state at successful
boundaries, with no purge yet.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: none. Full-day throughput remains unproven.

### Step 3 - Move Workload, Allocation Eligibility And Completion Off History

Own `dsp/p2p/lease/P2pServiceCentreWorkSnapshotFactory`,
`dsp/p2p/allocation/P2pWorkloadSnapshotFactory`, sticky/elastic runtime factories
and facades, `dsp/runtime/operational/DspOperationalReleaseRuntimeFactory`,
`dsp/av02/Av02AllocationSnapshotFactory` and allocation runtime controller,
`dsp/analysis/runtime/DspFullDayAnalysisRuntimeFactory` and
`DspFullDayCompletionProjectionCache`. Read their current callers and
`DspFullDayCompletionEvaluator` before editing. Do not change planner ranking,
desired line calculations, deadlines or completion evaluator predicates.

Add overload `P2pServiceCentreWorkSnapshotFactory.create(
DspOperationalWorkSnapshot, Set<OrderSheetKey> authorizedEmptyKeys)`.
Its remaining P2P IDs come only from remaining manifests whose current route
requires P2P plus live AV02, not from terminal/nonterminal historical queries.
Resolve route requirements once at ledger initialization from the order states;
expose `remainingP2pToteIdsByServiceCentre()` on the work snapshot. Authorization
filters only the unallocated EMPTY diagnostics. Reuse the projection when
incoming-P2P-ID, live-AV02, unallocated-EMPTY and authorization subview identities
are unchanged. Do not use the main work snapshot identity as that cache key:
unrelated bag-count mutations must not rebuild all incoming tote lists.

Add `P2pWorkloadSnapshotFactory.create(DspOperationalWorkSnapshot,
P2pWorkloadCostConfig)`. Preserve canonical centre/tote/bag order and existing
cost formula, but use remaining effective counts, IDs and unallocated EMPTY
work. No scan of allocated history or the full bag plan on mutation. The full
plan index remains startup/direct lookup only. Keep unchanged centre workload
values reusable. Share the work authority in core factories per Section 3.2;
composition roots construct it once per Section 3.6, never per evaluation.
After migrating callers, remove the factories' old history-dependent create
overloads rather than retaining an operational fallback to terminal records.
Mechanical caller/test-fixture migration to the new work-snapshot signatures is
owned by this step; no unrelated behavioural test changes are permitted.

Also own `dsp/p2p/allocation/P2pServiceCentreWorkloadSnapshot`. Replace its three
large identity lists with scalar `remainingInboundToteCount`,
`remainingUnallocatedBagCount` and `unallocatedEmptyOrderCount`, preserving the
existing pack-count, service-centre and estimated-duration values. Its canonical
record is `(String serviceCentreId, int remainingInboundToteCount, int
remainingUnallocatedPackCount, int remainingUnallocatedBagCount, int
unallocatedEmptyOrderCount, Duration estimatedSingleLineWork)`. Keep the old
list-taking constructor as a count-deriving convenience without retaining the
lists; migrate list-getter callers/tests to counts. Planner decisions already
use those count accessors, so their arithmetic/ranking is unchanged. Do not
manufacture dummy IDs to satisfy the old shape. Remaining incoming P2P IDs used
for live pin intersection stay in the separate work view, not copied per bag.

AV02 candidates must be drawn from `unallocatedEmptyOrders()`, then use the
existing authorization, dependency, capacity, current assignment and whole-centre
sequence checks. A completed EMPTY cannot reappear after departed history is
removed. Do not infer it from the absence of a lifecycle record. Revalidation
must confirm the sheet is still pending before generating a physical ID.

Replace CompletionSnapshotSource's lifecycle/outbound historical projections
with remaining-work and live ownership. Keep live station claims, dispositions,
queues, tipper activity and processing-drained checks unchanged. Nonterminal
manifested count is remaining manifested work; remaining physical tote count
adds live AV02 and open output totes, without double-counting manifested work.
Use current assigned-tote counts from live leases (after Step 6 these contain
no terminal entries). While migrating, intersect assignments with remaining
P2P IDs; never treat a missing retired lifecycle record as new work. Completed
but unallocated bags come from current StoredBagReceiver contents, not all
bagging completion IDs. Applicable output closure tests remaining executable
bag count zero plus existing machine drain/open-output conditions. Do not
flush unfinished work or fabricate zero-pack bags.

Remove only obsolete projection builders/caches when their callers are gone;
do not add a second cache over old terminal records. Keep the evaluator's
first-completion time, unsupported-work/NS handling and cutoff outcome intact.

Tests: update `P2pServiceCentreWorkSnapshotTest`, `P2pWorkloadSnapshotTest`,
`DspP2pElasticAllocationRuntimeTest`, `Av02AllocationSnapshotFactoryTest`,
`DspFullDayCompletionProjectionCacheTest` and new
`T/dsp/analysis/runtime/DspOperationalWindowCompletionTest.java`.
Cover incoming but unsupplied manifested work, allocated/departed EMPTY, consumed
inbound with active bag work, current receiver bags, partial/fully missing bags,
open output preventing completion, no-work/NS-only centre, pending disposition,
and cutoff with future work. Compare old/new numerical completion/workload
facts for the same mixed fixture during this migration step, then assert the
new path works without any historical list. No post-removal fallback is allowed.

Expected output: operational decisions and completion no longer require history.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: none. Keep source-retention removal for subsequent steps.

### Step 4 - Aggregate Metrics And Current-State Report Schema

Own `dsp/analysis/metrics/DspFullDayMetricsCollector`,
`dsp/analysis/runtime/DspFullDayAnalysisRuntime`, runtime snapshot and factory,
`dsp/analysis/report/DspFullDayReportFactory`, `DspFullDayReportJsonWriter`,
`dsp/analysis/report/DspFullDayProgressFormatter` and
`dsp/analysis/DspFullDayInspectionFormatter`. Read their tests and full snapshot
assembly. Do not change metric sample intervals, rate definitions or category
classification. SnapshotSuppliers gets a required shared work snapshot supplier
through Section 3.8's constructor migration. Also own its cutoff types/new test.

Replace historical list-size totals and owner-map joins with Section 3 counters
and live/incoming owners. Global and per-line consumed/allocated/closed totals
must be cumulative across centre handover even when no completed objects exist.
Marked totals equal marked-closed counters plus currently marked open totes;
do not count the same closure twice. Missing/collection/NS counts retain their
distinct definitions. Remaining sheet diagnostics use remaining obligations,
not legacy logical statuses that physical release intentionally leaves unchanged.

Include `operationalWork` in full-day runtime snapshots; it contains remaining
work and aggregate totals only. Final-report unfinished physical IDs include
remaining incoming IDs and current live identities, not just the now-smaller
lifecycle map. Report schema 2 per Section 3.4: remove historical lifecycle
assignment, closed-output and past allocated-bag detail; serialize live records
and explicit aggregate counters instead. Remove latest closed-tote identity
inspection from sticky snapshots when Step 9 removes that field; preserve
counts and last transition *category/sequence*, not a hidden completed payload.
Occupancy samples and per-centre completion facts may remain bounded analysis
results; do not keep per-entity terminal records in these samples.

Tests: `DspFullDayMetricsCollectorTest`, `DspFullDayReportFactoryTest`,
`DspFullDayReportJsonWriterTest`, `DspFullDayProgressFormatterTest`.
Required: zero history with nonzero correct totals; interval rates after
retirement; two centres share a line; open/closed marked counts; cutoff lists
incoming unfinished work; stable schema-2 field names, no historical arrays;
same aggregate numbers as a literal known fixture, not values calculated by the
production code under test. Old tests asserting retained history must assert
counts/current values instead, never be simply deleted.

Expected output: reporting remains informative without terminal object retention.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollectorTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayProgressFormatterTest --tests online.davisfamily.warehouse.sim.dsp.analysis.DspOperationalWindowCutoffTest
```

User verification: review schema-2 aggregate/current-state examples from tests;
no real-data run is required yet.

### Step 5 - FIFO Physical Supply And Inventory History Removal

Own `dsp/osr/OsrPhysicalInventory`, `OsrInventorySnapshot`,
`dsp/supply/DspServiceCentreSupplyCoordinator`, `DspSupplySnapshot`,
`ServiceCentreSupplySnapshot`, `dsp/av02/Av02PhysicalToteInventory`,
`Av02InventorySnapshot`, allocation controller/factory,
`dsp/av02/Av02OperationalCommandHandler`,
`dsp/osr/release/OsrProcessingReleaseCommandHandler`,
`dsp/p2p/lease/OperationalLifecycleP2pToteCompletedListener`, and
`warehouse/testing/DspWarehouseTransportDebugRig` (outside P; path is
`app/src/main/java/online/davisfamily/warehouse/testing/...`).

OSR retains currently stored manifests plus aggregate departure counts, not a
seen-ID set or departed list. Batch validation may check current occupants,
current batch uniqueness and capacity; no historical barcode search.
AV02 inventory retains waiting allocations only; its in-flight source identity
comes from the shared work ledger, not a renamed departed list. Update P2P
source resolution to indexed `findLiveAv02(id)`, retaining current-source and
role checks but removing whole-history scans. Stale release handlers classify
an absent/non-head current tote as no longer applicable; do not recreate
ALREADY_DEPARTED certainty using another ID set.

Execute 5A, 5B, 5C and 5D separately, in that order. The temporary API/reader
migration is not the final acceptance state. Exact shared contracts are in
Section 3.9; no old per-batch cursor/ADAPTED-first supply fallback is permitted.

5A owns DspFullDayInputLoader and DspServiceCentreSupplyPlanFactory, their
existing DspFullDayInputLoaderTest and DspServiceCentreSupplyPlanFactoryTest;
metadata type comments can change but existing factory/value signatures stay.
Read DspFullDayAnalysisCommandParser, TwelveNDatasetLoader,
DspDatasetAssembler and DspFullDayInputPreflight as order-preservation
prerequisites; do not change their generic APIs. Test reversed explicit paths
against directory order, lexical filename ordering (10 before 2 without zero
padding), source encounter indices, filtered nonphysical/rejected messages,
repeated manifests of one logical sheet and per-centre metadata encounter order.
Replace old ADAPTED-first/descending-priority request expectations with literal
feed-order assertions, preserving priority issue reporting and other validation.
A higher numeric centre priority must not move physical input ahead of its feed
position. No bootstrap mutation or new scheduling policy in this leaf.

5B owns OsrInventoryBootstrapFactory, OsrBootstrapState, their named tests and
the mechanical constructor callers in Section 3.9. Use the required transitional
four-argument state constructor; add no silent-empty-queue compatibility path.
Bootstrap creates one deque, peeks the head and stops at capacity/non-startup
centre; only after store succeeds does it remove that head. Return that same
remaining instance. Keep configured-startup EMPTY authorization separate.
Test capacity truncation within the first/second centre; input prefix ending
before capacity; an intervening non-startup head that prevents later configured
totes being preloaded; no startup configuration; zero physical/EMPTY-only
startup; exact stored-prefix/remaining-suffix order and identity. State
constructors retain the supplied deque identity, reject null deque/elements,
and do not copy it or synthesize future input. This leaf makes
the FIFO handoff available; coordinator admission is connected in 5C. Do not
claim integrated arrival-order correctness before 5C or change its old loop here.

5C owns DspServiceCentreSupplyCoordinator, DspSupplySnapshot,
ServiceCentreSupplySnapshot and the transitional OsrBootstrapState removal
plus direct construction callers/tests. It consumes the exact bootstrap deque.
Implement Section 3.9's application sequence and first-encounter requests;
remove nextPendingManifest's inventory/departed scans, whole-batch admission
selection, startup-overflow queues/ID lists and permanent physicalToteStates.
Current stored preload labels alone use a bounded current-ID set as specified.
Keep physical arrival-policy values and OsrInventoryConfig/
ServiceCentreSupplyConfig signatures/defaults unchanged.

In coordinator/rate/controller/bootstrap tests assert:

- At occupancy one above low-water no later request occurs; at equality one
  next centre is authorized, in encounter order even against numeric priority.
  Authorization itself stores no tote; first admission is one full interval later.
- A requested centre can continue supply while occupancy rises above low-water,
  within capacity. Startup overflow is already requested at zero; it is still
  rate/capacity limited. No repeated request for a centre encountered again.
- A1/B1/A2 stays in that order; an unauthorized B head blocks later authorized A.
  Earlier EMPTY-only centres get one request per eligible advance, consume no
  OSR slots, cannot starve, and do not allow physical head bypass.
- Full/not-due heads retain identity/counts. Expected store rejection leaves
  queue/counters unchanged. Successful store precedes dequeue. Due capacity
  recovery admits only one tote then schedules a fresh interval; normal due
  catch-up retains existing semantics. No early capacity-block classification.
- Scalar centre counts/current-only rows remain correct after departures;
  startup PRELOADED and subsequent SUPPLY_COMPLETE follow the specified meanings.
  No source manifest rescan is used to infer admission/departure. Repeated
  unchanged polling reuses snapshot/queue-derived views.
- Drive 1,000 small admissions/departures while retaining one future sentinel;
  queue length/current-owner counts do not grow with admitted history. Unit test
  observers may capture accepted order; no production audit list/test hook.
- Extend DspFullDayAnalysisRuntimeFactoryTest with a small mixed-type feed:
  actual preload plus supplied suffix have literal input order. Do not assert
  that OSR release order must equal arrival order. Independent eligible
  FULL_PACK/ADAPTED and ASSOCIATED preparation behavior is preserved.

5D owns the inventory/source/stale-handler/AV02 diagnostic changes named at
this chapter's beginning. Run its separate command after 5C; supply must already
work without departed lists. OSR and AV02 retain current inventory plus counters,
not seen IDs/departure records. Waiting AV02 leaves inventory at departure;
shared work ledger retains its live source until actual P2P consumption.
Test cumulative counts with no ended entries, capacity failure/no mutation,
stale commands against current availability, live AV02 lookup after departure,
completed EMPTY not reallocated, and actual runtime composition with no history.
Mechanical debug-rig/current-snapshot caller migration is permitted; no new GUI
feature or queue policy. Do not use the earlier history APIs to repair a test.

Expected output: configured overnight preload and low-water centre requests,
one filename-ordered physical FIFO, current-only inventory with cumulative
counts, and independent OSR release scheduling.

Implementation verification: run only the selected leaf command in Section
4.0. No combined chapter verification is authorized.

User verification: none beyond the focused leaf results. Broader integration,
visual and full-suite checks remain Step 11; no full-day run by the model.

### Step 6 - Retire Lifecycle, P2P Assignments And Completed Watermark Obligations

Own `dsp/lifecycle/PhysicalToteLifecycleLedger`, Snapshot, inbound/AV02 lifecycle
controllers, `dsp/p2p/lease/P2pLineLeaseRegistry`, CatalogSnapshot, allocation
Request and RequestFactory, sticky/elastic runtime facades, the Step 2 outer
listener, `dsp/scheduler/policy/WholeServiceCentreReleaseLedger`, and affected
whole/elastic lease-retention policies. Read their current committer/arrival
callers and tests. Do not remove live arrival assignment matching.
Also own `dsp/osr/release/OsrProcessingReleaseSnapshotFactory`, its command
handler, and `dsp/outbound/OutboundToteAllocator` for the terminal-stage migration
below. Update existing diagnostic fixture/probe correctness assertions that
still demand candidate-time assigned-ID rejection; their timing runner/settings
and saved artifacts remain unchanged.

Lifecycle stores current records and active assignments only, indexed by
OrderSheetKey and PhysicalToteId. Terminating a stage removes that assignment;
return its ended value to the immediate caller without retaining it. Terminal
transition removes the tote after validating no active assignments remain.
Inbound lifecycle construction no longer registers the whole manifest catalog;
register an OSR tote on accepted activation. Admission validation may consult
the immutable manifest and current inventory but not require a future tote to
have an existing lifecycle record. AV02 registers at allocation as before.
Update OsrProcessingReleaseSnapshotFactory and CommandHandler together: current
stored inventory establishes an incoming tote; an absent lifecycle record is
normal until accepted release. A present incompatible/live record still blocks
the operation. Preserve same-sheet current-assignment deferral, target/P2P
prevalidation and downstream-first acceptance. Only after acceptance/departure
does activate register the current record and assignment. Failed target
acceptance must not register a tote. Do not change only validateActivation while
leaving the snapshot/handler's mandatory pre-registration checks in place.
Also own Section 3.10's summary/controller/metrics migration and its new record;
no general metrics rewrite.

At this step also change outbound closure to terminate OUTBOUND_BAG assignments
without creating permanent OUTBOUND assignments, then transition/remove its
terminal tote. Otherwise the new no-active-assignment terminal check would
break closure before Step 9. Historical closed snapshots remain temporarily
until Step 9, but no longer appear in the current lifecycle ledger.
Remove assignmentHistoryFor APIs and adapt their tests to current state plus
immediate returned transition values. Never infer missing identity means future
work; Step 3's work snapshot is authoritative for future obligations.

Add `P2pLineLeaseRegistry.validateCanRetireAssignment(PhysicalToteId,
P2pLineId)` and `retireAssignment` with the same arguments. Remove exactly one
live assignment from both current indexes; publish a new immutable per-line
list only for the affected line. Expose delegation methods with these names
on sticky/elastic runtimes. Extend the outer listener constructor to take the
shared elastic runtime supplier per Section 3.6: prevalidate assignment/accounting before the
delegate, then account and retire after the full existing completion chain.
Earlier station visits and blocked input must retain the assignment.

Delete the `findAssignment` rejection in candidate request creation (both the
public Request path and factory path). Retain current-map consistency in
commit/arrival, not ever-assigned checks. Catalog validation may inspect its
bounded live assignments; cache/reuse assignment-derived structures when
their identity has not changed instead of revalidating stable data per activity
poll. No history lookup index is added.
Last lease transitions contain category and sequence, not a completed physical
ID embedded in a details string. Live payload identity remains on current
assignments, not last-transition audit text.

Whole-centre tipping completion removes the obligation, rather than setting
completed=true forever. Released OSR/EMPTY obligations leave pending maps at
successful release; keep centre counts/cursor, not released objects or
committedAv02ToteIds. Pending/current state validates future commands.
Outstanding watermark remains assignment-to-actual-tipper-completion, with
unchanged configurable default and no PRL/bag completion extension.

Tests: `PhysicalToteLifecycleLedgerTest`, `PhysicalToteLifecycleSnapshotTest`,
`InboundPhysicalToteLifecycleScenarioTest`, `P2pLineLeaseRegistryTest`,
`P2pLineAllocationRequestFactoryTest`, `WholeServiceCentreReleaseLedgerTest`,
`OperationalWindowP2pToteCompletedListenerTest`.
Also update and run `OsrProcessingReleaseSnapshotFactoryTest`,
`OsrProcessingReleaseCommandHandlerTest`, `OutboundToteAllocatorTest`,
`P2pHistoryScalingFixturesTest` and `P2pHistoryScalingProbeTest`: these are
directly affected correctness tests, not permission to run matrix mode.
Prove ended records disappear; active-stage indexes are correct; future work
still exists without registered lifecycle objects; same-sheet sequential totes
remain possible; actual completion retires both P2P indexes and counts once;
wrong current claim/delegate failure changes nothing; downstream bag work still
prevents lease handover; no candidate-time historical-ID probe; thousands of
retirements leave only current obligations. Update old terminal/audit assertions,
but retain unrelated live ownership and no-mutation assertions.

Expected output: ended inbound lifecycles do not remain in operational snapshots.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: none. Physical object retirement follows separately.

### Step 7 - Final Station Handoff And Exact World Object Retirement

Own engine `app/src/main/java/online/davisfamily/threedee/sim/framework/`:
`SimulationWorld.java`, `SimulationContext.java`; plus
`dsp/transport/routing/WarehouseTransportPublisher`,
`SimulationWorldWarehouseTransportPublisher`,
`dsp/station/processing/StationProcessingCoordinator`,
`dsp/station/continuation/StationRouteContinuationController`, its runtime factory,
`dsp/adapting/MutableToteLoadPlanRegistry`, `MapBackedToteLoadPlanRegistry`,
Third Party area/station controllers and the full-day factory. No other engine
or rendering refactor.

Add exact-instance `removeSimObject(SimObject)` and
`removeTrackableObject(TrackableObject)` to SimulationWorld, and
`removeTrackedObject(TrackableObject)` to SimulationContext. Return boolean,
preserve survivor order, never remove an equal-but-different object. These
operations run from controller-phase CONSUME acknowledgement, after sim-object
and sensor iteration. Calls during those earlier iteration phases must be
rejected before mutation; use a private update-phase guard, not deferred queues
or a new event system. Removal outside update is allowed for teardown/tests.

Step 7A also adds public read-only `validateCanRemoveSimObject(SimObject)` and
`validateCanRemoveTrackableObject(TrackableObject)` so publisher validation can
fail before changing its own maps. Unknown exact instances are allowed/no-op;
half-registered trackables are invariant failures before mutation. A trackable
passed to removeSimObject delegates to removeTrackableObject, removing both
registrations. Preserve survivor identity/order. World phase is IDLE,
SIM_OBJECTS, SENSORS, EVENTS or CONTROLLERS; only IDLE/CONTROLLERS permit removal.
Set/restore phase in try/finally. Context has a package-private retirement-allowed
flag controlled by world; removeTrackedObject rejects while disabled. It must
not allow a sensor to bypass the world's guard by mutating context directly.
Add package-private world simObjectCount()/context trackedObjectCount() for
same-package tests and Step 11's test-only assertion helper.

Add abstract `validateCanRetire(RoutedPhysicalTote)` and
`retire(RoutedPhysicalTote)` to WarehouseTransportPublisher. The production
publisher checks exact published tote/renderable membership before mutation,
then removes the map entry, world/trackable registration and exact renderable.
Remove whole-renderable-list validation scans during publish; current map and
exact bounded active membership suffice. Do not keep retired publication IDs.
Add `removeLoadPlan(PhysicalToteId)` returning Optional<ToteLoadPlan>.

Publisher prevalidation checks world/context phase and exact instance membership
as well as its map/renderable owner. Retirement then removes world/context
registration, exact renderable list entry and publication entry. A wrong instance
or forbidden phase must not partially remove any owner. Repeated retirement of
an already removed published payload rejects; station FIFO acknowledgement
ensures it is never retried as a new completion. Do not use an ID-only delete.

Inject the live load-plan registry into the existing consume path; it already
owns the exact disposition payload. Prevalidate disposition, presentation,
transport absence and retirement. Then retire exact publication, remove its
plan and visit metadata, acknowledge head. No additional count increment.
CONTINUE must still retain the same world/tote/renderable/follower objects and
replace only the immutable routing envelope.

Remove StationProcessingCoordinator's terminalConsumedPhysicalToteIds.
lastCompletedAtByPhysicalToteId is allowed only for still-live CONTINUE journeys;
remove on final consume acknowledgement. Keep active/unacknowledged ownership
checks and exact FIFO semantics. Scalar last diagnostic fields must not hold
payload objects; remove terminal physical-ID fields rather than treating a
single retained retired ID as an archive requirement.

ThirdPartyAreaController gains `acknowledgeCompletion(PhysicalToteId)` which
removes the completion and that visit's completed-line bookkeeping after the
station controller has successfully created its CONTINUE disposition. Do not
forget the visit while the station is still awaiting its completion. Current
claim/time checks replace terminal-ID exclusion; no anti-replay archive.

Tests: create the new files at these exact paths:
`app/src/test/java/online/davisfamily/threedee/sim/framework/SimulationWorldRetirementTest.java`
and `T/dsp/transport/routing/SimulationWorldWarehouseTransportPublisherTest.java`.
Update `StationProcessingCoordinatorTest`, `StationRouteContinuationControllerTest`
and `ThirdPartyStationProcessingControllerTest`. Update all publisher test doubles
in `WarehouseTransportIngressControllerTest`,
`DspWarehouseTransportRoutingScenarioTest`,
`DspStationRouteContinuationRuntimeFactoryTest` to implement the new exact
retirement contract; they must not silently no-op it.

Required proofs: retired object never updates/is scanned as trackable again;
survivor order unchanged; equal ID/different instance is not removed; forbidden
iteration-phase removal leaves state unchanged; blocked CONSUME keeps payload;
ack removes all owners; CONTINUE re-entry preserves exact objects; active packs
survive source-tote removal; repeated updates do not recount completion.

Expected output: completed inbound tote objects, not merely their visibility,
leave the world and all current DSP owners.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: standalone warehouse transport rig visual check is reserved
until Step 11; no GUI run by the model.

### Step 8 - Retire Machine Metadata And Completed Correlation Ownership

Read BaggingMachine, ToteToBagFlowController, ToteTrackTipperFlowController, StoredBagReceiver,
AssignedLineWorkPlanProvider, P2pBagCorrelationAssignmentRegistry, activity probe,
headless line snapshot, and their immediate debug/render consumers.

Own those types, `dsp/analysis/runtime/DspHeadlessP2pLineRuntime` and Snapshot,
`dsp/outbound/OutboundToteAllocationController`, plus
`app/src/main/java/online/davisfamily/warehouse/testing/ToteToBagDebugRig.java` and
`totebag/assembly/BaggingSectionInstaller` only for inspection adaptation.
Also own Section 3.9's bag-coherent committer/headless-config integration and
its existing `BagCoherentOperationalP2pReleaseAssignmentCommitterTest`.
Also own `dsp/p2p/bag/P2pBagCorrelationAssignment` and its snapshot to remove
the retired tote payload from live correlation pins.
Visual consumers directly affected by changed observation lifetimes are also
owned here: `totebag/assembly/TipperToSorterPackVisuals`,
`TipperToSorterSection`, and engine
`app/src/main/java/online/davisfamily/threedee/debug/SelectionInspectionRegistry.java`.
Changes are exact retirement/adaptation only, not rendering redesign.

BaggingMachine no longer owns completed correlation/CompletedBag/runtime Bag
lists after discharge. Add scalar `completedBagCount()`; do not replace the
lists with a last-completed payload. Replace historical getters and callers. Receiver
contents are the authority for discharged but unallocated bags. In the
no-receiver standalone machine case, count/discard after discharge; do not
create a fallback history queue. Tests capture completion at its boundary.

On successful PCR-to-bagger transfer remove the releasedGroups entry for that
correlation. Observed Pack references remain only while that Pack is live;
remove CONSUMED packs after bag intake and remove bypassed packs after PDC
collection handoff. Preserve physical contents owned by the bagger/collection
owner; this does not delete live packs. ToteTrackTipperFlowController clears
its per-source observed-Pack map only after its successful completion callback
and last discharge, not while later packs of that current tote still tip. Remove
completedCorrelationIds and obsolete known/fixed count entries once that group
has transferred. To prevent re-registration, expose only currently untransferred
line correlations to the controller. Add
`P2pBagCorrelationAssignmentRegistry.markTransferred(String)` to remove it from
the work-plan publication while retaining its current line pin.
`markExecutableResolved(String)` marks successful allocation or a fully missing
bag; `retire(String)` removes the pin once its last input consumer also ends.
Snapshot adds
`untransferredCorrelationIdsFor(P2pLineId)`; AssignedLineWorkPlanProvider uses
that, not all pinned correlations. Keep correlation ID -> current line metadata
independent of a completed source tote's P2pPhysicalToteAssignment; store line
and service-centre values directly, not an old tote assignment object.

Thread the correlation registry as an explicit full-day handoff dependency,
using the existing PackGroupReceiver seam: new
`P/dsp/p2p/bag/CorrelationTrackingPackGroupReceiver.java` implements/delegates
PackGroupReceiver, prevalidates the current pin, and invokes markTransferred
only after its delegate accepts completeIncomingTransfer. It must not add a
direct BaggingMachine dependency to ToteToBagFlowController. Outbound allocation
resolves/conditionally retires the pin after membership/allocation/receiver
success per Section 3.9. All constructors keep their existing
local receiver seams and isolated defaults; full-day composes the adapter.

Synchronization caches the untransferred correlation-set identity plus missing
classification version. Recheck only on those genuine changes, not per-candidate
or fixed step. Fully missing zero-pack correlations leave executable expected
work and are counted by the existing exception owner; no fabricated pack/group.
For a zero-pack correlation, mark executable resolution and use its
PlannedBag.owningOrderSheetKeys and
remainingInboundConsumersForSource for every owning sheet: retire the pin
after all those counts become zero. Check only the completing tote's affected
correlations and newly classified zero-pack keys, not all past bags. A single
source tote finishing is not that proof when another manifest remains.

Before changing observation semantics, adapt visuals in this same step. Add
one optional presentation callback to ToteTrackTipperFlowController via
`setToteCompletionPresentationListener(TipperToteCompletedListener)`, initially
NO_OP; reject a second registration. This is a single synchronous presentation
hook after the successful existing domain callback, not an audit/event bus.
TipperToSorterSection registers its visual source-retirement callback there.
`TipperToSorterPackVisuals.retireToteSource(String)` detaches any still-attached
live pack renderables with preserved pose before removing the ToteSource;
sorter/downstream visuals still own those packs. It must never infer that
absence from a cleared observation map means an old pack is back inside a tote.

Add `retainLivePackVisuals(Set<String>)` to the section/visuals and call it from
ToteToBagDebugRig with current tipper discharge, sorter queue/outfeed,
ToteToBagFlowController live observed packs, transfers, lanes and bagger group
IDs. The visual owner also includes not-yet-emitted plans for registered current
or queued source totes. Remove absent pack renderables from parent/scene/map and
inspection registration. SelectionInspectionRegistry gets exact-target
`unregister(RenderableObject)`, returning boolean. Receiver bag renderable maps
are likewise reconciled to current receiver/active-discharge bags, not all
completed correlations. No permanent visual seen-ID set or payload archive.

Tests: `BaggingMachineTest`, `ToteToBagFlowControllerTest`,
`ToteTrackTipperFlowControllerTest`,
`P2pBagCorrelationAssignmentRegistryTest`, `AssignedLineWorkPlanProviderTest`,
new `T/dsp/p2p/bag/CorrelationTrackingPackGroupReceiverTest.java`.
Also update existing `TipperToSorterPackVisualsTest` and
`SelectionInspectionRegistryTest`: clearing source observation must not
resurrect packs, live sorter/downstream packs remain visible after source
retirement, retired targets unregister without affecting survivors, and
completed bag/pack renderables do not accumulate after receiver removal.
Cover receiver backpressure, no-receiver discharge, transfer versus allocation
lifetimes, no repeated expected-work resurrection, partial missing count
stability, zero-pack case, actual packs still owned downstream, and 1,000
completed groups with unchanged current collection counts. Rig display must
read current receiver bags/scalar counts, not reconstruct a bag archive.

Expected output: completed packs/correlations/bags do not accumulate in machines.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: machine/transport visual compatibility reserved for Step 11.

### Step 9 - Retire Closed Output And Bound Output-Sheet Resolution

Own `dsp/outbound/OutboundToteAllocator`, AllocationSnapshot, OutputSheetAllocator,
`dsp/p2p/lease/DspP2pStickyLeaseRuntime`, StickyLineSnapshot, full-day snapshot
and JSON assembly, and their named tests. Use Step 4's counters, not a new
statistics service.

Remove allocator closedTotes/allocatedBags/allocatedBagKeys history. Current
allocated bags exist only as contents of open totes, with direct current keys
if needed. `findTote`, `findAllocatedBag` and current ownership lookup refer to
open contents only; retire historical list APIs and latestClosed snapshot field.
Remove the ever-allocated bag rejection; current receiver/remaining-work state
controls applicability. Keep actual pack membership and current tote purity.

OutputSheetAllocator gains `retireTote(PhysicalToteId)` and
`retireSource(OrderSheetKey)`. Keep source/tote output mappings only while that
output tote is open. Keep one ordinal integer per source until that source has
no remaining bags **and** no open output pairing. On close remove mappings,
reverse ownership and current lifecycle assignments; when the last pairing
closes, retire the source ordinal if its remaining count is zero. Never reset
an ordinal while another future bag from that source can arrive. Formula,
range/collision rules and source versus derived ownership remain unchanged.

`retireSource` means mark the source exhausted after work accounting reaches
zero; if it still has an open pairing, keep that flag with its current source
ordinal until the last pairing closes. Otherwise remove the ordinal immediately.
Call it from OutboundToteAllocationController after recordBagAllocated for each
affected source that now has zero remaining bags. This ordering also handles
capacity closure inside allocate, which occurs before that last bag's work
accounting. `retireTote` removes only that tote's current pairings and then
clears exhausted sources whose last pairing disappeared. Do not leave an
ordinal forever because capacity closure preceded the last count decrement.

Replace resolve's full-map copies with touched-key staging: validate every
source in the request and calculate all proposed ordinals/mappings in local
collections, then apply only those additions after all expected failures have
passed. Collision checks are direct against immutable known incoming keys and
current output owners; no persistent set of all previously generated output
IDs. Deterministic ordinals prevent reusing closed output keys for a still-live
source. This is not a reason to scan retired physical tote IDs.

During closure remove OUTBOUND_BAG assignments and transition to terminal
OUTBOUND as an immediate value, without assigning permanent OUTBOUND records.
Count closure/reason/exception flags before forgetting contents. Open outputs
on hard cutoff close only through the existing cutoff rule; unfinished inbound
and unallocated bags remain reported. Do not introduce dispatch/Exceptions work.

Tests: `OutboundToteAllocatorTest`, `OutputSheetAllocatorTest`,
`OutboundAllocationSnapshotTest`, `OutboundGeneratedSheetIntegrationTest`,
`DspFullDayReportJsonWriterTest`. Required: two output totes for one still-live
source have advancing ordinals after first closure; same open pairing reuses
its derived sheet; source ordinal retires after its true last pairing closes;
later invalid source in a multi-source resolve leaves all maps/ordinals equal;
capacity/pharmacy/centre/cutoff closes retain correct totals with no closed
objects; marked count not lost; current contents remain valid; source inbound
retirement neither ends output nor erases output numbering prematurely.

Expected output: closed output contents and sheet mappings no longer accumulate.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: none beyond reviewing focused results/schema changes.

### Step 10 - Retire Finished Logical Dependencies And Exception Visit Metadata

Own `dsp/analysis/runtime/DspPreparedPackExceptionLedger`,
`DspFullDayPdcPackDispositionPolicy`, `dsp/p2p/allocation/P2pMissingPackSnapshot`,
`dsp/adapting/AdaptingStorageLayout`, `AdaptedLineStore`,
`dsp/runtime/DspSchedulerRuntimeState`, work ledger/facade and final retirement
composition. Read current order-wide STORE/first-COLLECT contracts and tests.
The exact new adapter, signatures and touched-key invocation algorithm are in
Section 3.7; do not design another cleanup coordinator.
Also own `dsp/bagging/PackProvenanceRegistry` and its current creation/retirement
callers for keyed provenance removal. Keep immutable plan provenance separate.

At final inbound retirement remove visitedSheetByToteId for that tote.
Retain first-COLLECT/order collectedGroups/readiness markers only while any
incoming or live collecting sheet/tote of that referenced order remains.
Use remainingConsumersForOrder plus reverse references built from input once;
do not scan consumed source totes. Add `retireOrder(String)` to exception and
storage owners, and `removePreparedLineKeys(Set<PreparedLineKey>)` to scheduler
runtime state. Invoke only when remaining consumers are zero and no prepared
storage/active claim still owns the work. Remove finished logical scheduler
rows through `retireOrderSheet(OrderSheetKey)` only once all its inbound/EMPTY
work is consumed and remaining bag count is zero. Static order/route catalogs
may remain direct-lookup planning input, not scheduler candidate rows.

These reference counts include later sheets and multiple manifests; consuming
an ADAPTED STORE tote does not retire its future fulfilment order/readiness or
stored packs. A prepared key stays available for every pending consumer, not
only the first physical tote. If a bag is still live downstream, its required
provenance stays in its own immutable contents, independent of that tote row.

Use classificationVersion already introduced in Step 2C; add its pruning
increments here. Do not postpone epoch introduction until this step or restore
the removed workload-factory classification cache. Preserve unchanged identity
when no mutation. PDC collection counts remain separate from that
version and from missing totals.

Retire missing-ID detail for an allocated bag after membership accounting and
any required PDC routing consumer finish; keep aggregate missing/affected
counts. Pending empty bags and actual PDC collection packs are **unresolved**,
not completed history: retain their required BagKey/pack identity with the
existing owner. Do not erase known exception work to achieve a size bound or
pretend Exceptions completed. Remove receivingTote provenance from settled
classification records once its admission/routing role ends; retain only the
live pack/bag/target-order facts. Immutable input provenance may remain input-owned.

Add `PackProvenanceRegistry.retirePacks(Set<String>)` and remove mutable runtime
entries after successful bag membership/allocation for its actual pack IDs;
retain necessary provenance for unresolved collection packs with their current
exception owner. Do not keep a mutable duplicate of input provenance forever
after its current consumer ends. Planned immutable traces remain direct-lookup
input, not a history checked during allocation. Test retirement does not remove
the provenance of future/current packs or unresolved exception work.

Tests: `DspPreparedPackExceptionLedgerTest`, `DspFullDayPdcPackDispositionTest`,
`DspFullDayConcurrentAdaptingScenarioTest`, and new
`T/dsp/analysis/runtime/DspOperationalDependencyRetirementTest.java`.
Also update existing `PackProvenanceRegistryTest` for the keyed removal contract.
Prove two sheets/multiple manifests preserve first-COLLECT readiness until the
last consumer, then remove order markers and finished scheduler rows; source
STORE retirement preserves prepared packs; classification versions never
regress when maps shrink; allocated missing-bag detail can retire without
changing aggregate counts; unresolved empty-bag and PDC collection work remains
visible and counted. No new exception recovery is implemented.

Expected output: completed logical consumers do not keep physical histories
alive; genuinely unfinished dependencies/exception work remain.

Implementation verification: run only the selected leaf's command in Section
4.0. No combined chapter verification is authorized.

User verification: none. Do not close the previous watermark review gates.

### Step 11 - Integrated Window-Boundedness And Behaviour Proof

Own only the new world-count assertion helper named below, new tests
`T/dsp/analysis/runtime/DspOperationalWindowScenarioTest.java` and
`DspOperationalWindowScalingTest.java`, plus existing scenario assertions that
still expect historical records. No production optimization or benchmark task.
Read `DspFullDayWholeServiceCentreScenarioTest`,
`DspFullDayP2pOutstandingToteWatermarkScenarioTest`,
`DspAv02OperationalAllocationScenarioTest`,
`DspStationRouteContinuationScenarioTest` and their current fixture helpers.
Use the actual full-day production composition and machine/transport callbacks,
not a test-only sequence of ledger mutations masquerading as an end-to-end run.
Also prove literal filename-ordered physical arrival through preload and supply,
without asserting that OSR departures must follow that order. Independent
FULL_PACK/ADAPTED eligibility and ASSOCIATED dependencies remain unchanged.

Scenario test covers both scheduler policies, with direct P2P, Third Party ->
P2P and Adapting STORE/COLLECT paths, two centres, a split source sheet, partial
missing work and a fully missing bag. Preserve actual fixed-step execution,
one command/application, exact route identities, FIFO and full drain/close
before handover. Compare deterministic aggregate totals and business completion
times across repeated runs of the same fixture. No speed assertions.

Scaling test drives short repeated bounded batches through real composition,
not long simulated idle days. Use three fresh fixtures with 1, 10 and 100 small
batches and the same centre/line configuration. Compare their fully drained
end checkpoints (U=0, A=0 and the same C). At these equivalent checkpoints
assert identical operational snapshot
cardinalities and no completed IDs in lifecycle, leases, inventory, publication,
station metadata, load plans, machines, bag pins or output maps. Counters alone
increase. Include 20 unchanged updates and prove snapshot/set identity reuse
and no new expected-correlation work via a counting test provider. Verify a
retired world object gets no further updates/trackable traversal. Preloaded
immutable plan data is excluded from the active-state size assertion but no
hot-path consumer may traverse completed plan entries to reconstruct results.

The drained comparison alone is insufficient. Add a second, **non-empty**
checkpoint matrix for prefix sizes 1, 10 and 100 under each existing policy:

- Reuse the existing scenario's input/profile builders as the concrete pattern
  (copy private helper logic into this new test, do not change production config).
  Prefix orders are distinct one-pack FULL_PACK work in centre 104; append one
  fixed one-pack sentinel FULL_PACK bag in centre 108. Keep the same two centres,
  five lines, queue/capacity/timing settings and default watermark across cases.
  For these synthetic small fixtures only use P2pPlaceholderDurations
  `(1d, .1d, .1d, .1d, .05d, .05d, .05d, .05d, .05d, .05d, .05d)`;
  preserve production defaults/configuration files unchanged. This keeps the
  bounded loop a small correctness test, not a slow real-day replay.
  Construct with `create(world, input, profile)`; future prefix input may differ,
  but remaining U/A/C at the comparison checkpoint must not.
- Define nested test-only `GatedAllocationWorld extends SimulationWorld` in
  DspOperationalWindowScalingTest. Override addController: wrap only actual
  OutboundToteAllocationController instances in a controller that normally
  delegates unchanged. After the runtime is constructed, bind its reference;
  find the owning line by exact controller identity from lineRuntimes(). The
  wrapper withholds that controller only when its receiver contains the sentinel
  bag. Assert no prefix bag shares that receiver at that moment (one bagger
  discharge per update in this fixture). Other line/controller updates continue.
  This is test-only execution staging of the real allocator, not a production
  policy hook, fabricated ledger completion or replacement machine. No mutation
  of domain collections to install the checkpoint.
- Continue until every prefix input, bag, output and disposition has genuinely
  retired, all pipelines are drained except the sentinel's receiver, and the
  sentinel source's CONSUME acknowledgement has completed. Assert U=0 incoming
  totes/EMPTYs, exactly one remaining executable bag/pack, exactly one receiver
  bag and pin, zero open outputs, no current inbound lifecycle/assignments,
  and RUNNING state. The sentinel physical bag is live A>0; its packs must still
  be in its immutable physical contents. Only prefix scalar totals differ.
  Bound the loop to 50,000 updates of 50 ms and fail with compact current-state
  diagnostics if this exact checkpoint is not reached; never clear extra work.
- Compare current collection cardinalities, including the work snapshot,
  inventories, lifecycle, leases, station ownership, current machine/receiver,
  pin/output maps and world registrations. Ignore monotonic versions, scalar
  totals, timestamps and which otherwise equivalent line owns the sentinel;
  do not normalize away an extra entity or unresolved exception. Public current
  snapshots provide cross-component evidence; private caches are covered by
  their owning leaf's repeated-retirement tests and architecture review.
- Add new test helper
  `app/src/test/java/online/davisfamily/threedee/sim/framework/OperationalWindowWorldAssertions.java`
  exposing only static registration counts for this test, using package-private
  `simObjectCount()` on world and `trackedObjectCount()` on context added in 7A.
  No reflection/public debug API or metrics subsystem. Existing world retirement
  unit tests separately prove retired objects never receive another update/scan.
- With allocation still withheld, perform 20 `runtime.update(0d)` calls (supported
  by the current runtime). Assert unchanged work/projection/subview identities,
  cardinalities and counts; don't require identity of the entire clock-bearing
  runtime snapshot. Expected-provider invocation counts are proven at the actual
  flow/provider seam in 8C, not by substituting a fake full-day completion path.
- Release the test gate and run the actual final allocation/closure to completion;
  assert prefix+1 totals and empty ordinary operational state. This final action
  proves the sentinel was preserved as usable physical work, not an inert row.

This matrix must fail both an implementation retaining each completed prefix and
one that clears everything to pass the end-drain test. It is a state/ownership
proof, not a timing benchmark or a guarantee about the six-minute pause.

For exceptions, assert a separate bound: genuine unresolved collection grows
only with known outstanding exception work; ordinary completed work does not
remain mixed into it. Hard-cutoff scenario preserves incoming/active/bag work
and its aggregate diagnostics instead of clearing everything to look bounded.

Implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspOperationalWindowScenarioTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspOperationalWindowScalingTest
```

User verification (model must not execute):

1. Run the complete suite, supplying the result before Step 12:

   ```powershell
   $env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
   .\gradlew test
   ```

2. Run the existing standalone warehouse transport and tote-to-bag rigs using
   the user's established launch configuration. Confirm continuation preserves
   objects, consumed totes disappear without ghosts, current receiver bags
   display correctly, and reset still works. No new UI/launcher work is part
   of this step. If that launch configuration is unavailable, report visual
   compatibility UNPROVEN rather than guessing a command.

3. Review a schema-2 small-fixture report for preserved aggregate counts and
   current unfinished work. A full-day rerun and any revised throughput probe
   are separate user-run work after correctness; neither is needed to prove
   the structural no-history contract. Do not reuse the old history-injection
   matrix as a performance acceptance test for a system that no longer retains
   that state. Preserve old diagnostic artifacts as baseline evidence. A successful
   full-day run remains mandatory before merging scheduler/correction to master;
   structural acceptance or this plan's closure must not waive that gate.

Expected output: deterministic boundedness and preserved physical behaviour,
not an unsupported claim that the six-minute pause is explained or eliminated.

### Step 12 - Architecture And Complete-Diff Review

No model-run Gradle/probe command. Requires Step 11 user results. Own this
execution record only. Review the complete feature diff from the baseline and
trace the actual relevant production flows. Report PASS/FAIL/UNPROVEN and
concrete type/method/test evidence for every item:

1. One remaining-work authority; no divergent nested ledger or restored history.
2. Incoming work stays available, terminal work leaves operational structures.
3. No historical barcode/allocated-bag/terminal-ID checks or disguised tombstones.
4. Lifecycle and live assignment retirement at the actual physical boundaries.
5. Source-tote retirement does not discard live packs, bags, pins or preparation.
6. FIFO/CONTINUE exact identity and CONSUME acknowledgement/presentation preserved.
7. World/trackable/renderable registrations retire safely and survivor order holds.
8. Filename-ordered physical FIFO across preload/admission; no type/centre-
   priority reorder or queue-jumping. Startup/authorization behavior matches
   Section 3.9's confirmed low-water/configured-preload contract, not the
   superseded batch-order policy.
9. AV02 pending-work applicability prevents accidental repeated physical creation.
10. Whole-centre watermark and both policy algorithms remain unchanged.
11. Output ordinal continuity, pharmacy/centre purity and membership unchanged.
12. Completed output/machine metadata removed; unresolved exceptions not hidden.
13. Aggregate totals, intervals, milestone/cutoff and schema-2 current-state reports.
14. Per-step work depends on U/A/C, not retired R; no growing full-map copies.
    Require the non-empty fixed-window matrix as well as the drained proof;
    inspect private owner counters/tests, not only published list sizes.
15. Immutable worker publication, simulation-thread mutation, no audit threads.
16. Complete scope/test review including standalone call sites and rig changes;
    no unnecessary refactor, weakened physical assertions or deleted coverage.

Also confirm cutoff snapshots and scalar release diagnostics contain no closed
payloads, and resolved correlation pins remain only for actual pending source
consumers. Trace the last-consumer removal and no-resurrection commit path.
Record the successful-full-day/master-merge gate as OPEN unless the user has
provided that separate evidence and approval; never mark it PASS from unit tests.

Any FAIL or architecture-significant UNPROVEN stops closure. Do not redesign
during review. Run git diff --check and report final git status --short.
Historical watermark/whole-centre review gates remain separate unless the user
explicitly authorizes their reconciliation; do not silently certify them here.

User verification: review findings and authorize documentation closure only
when review and required user checks are green.

### Step 13 - Bounded Requirements And Documentation Closure

No model-run Gradle/probe command. Requires green Step 12 and separate request.
Own this plan, `docs/codex-instructions.md`, `docs/codex-context.md`,
`docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`,
`docs/scheduler/dsp-operational-scheduling-requirements.md`,
`docs/scheduler/dsp-scheduler-implementation-plan.md`, and
`docs/machines/phase-1-stations-roadmap.md` only.

Record verified final current/incoming-only state, aggregate results, source
versus downstream lifetimes, physical object removal, schema 2 and remaining
performance uncertainty. Replace lifecycle append-only audit/history and
future-32R-retention clauses with Section 1's agreed rule; retain immutable
input provenance and live containment, not completed runtime mappings. Add
one explicit supersession note to the current instructions/context for older
plans; do not rewrite every historical execution record or relabel their
verification. Record old diagnostic measurements as evidence for the old
retaining implementation, not current runtime state or full-day attribution.

Also reconcile current arrival/supply statements in the named instructions,
context and requirements: ascending-filename physical FIFO, configured overnight
preload prefix, inclusive low-water requests in encounter order and continued
rate/capacity-limited admission for requested centres. Do not restore the old
ADAPTED-first/descending-priority physical supply from historical text. Keep
OSR release eligibility/ranking, P2P policies and their preparation contracts
separate. The completed rate-limited supply plan remains a historical record;
its supersession is stated in current handoff, not rewritten as new verification.

Do not implement or plan an audit thread, 32R, dispatch, Exceptions recovery,
PRL policy, new throughput model, renderer split or additional optimization.
Do not close watermark user/review gates by implication. Update current reading
direction to this completed correction plus the next separately authorized
work; do not create that next implementation from documentation closure.

Review complete documentation diff and named facts, run git diff --check,
report final status. User verification: review the bounded handoff; no further
run is required by documentation closure.

## 5. Planning Execution Record

2026-10-16: initial `git status --short` empty at `7d28bd0`. User authorized
assessment plus plan creation, not implementation. Inspected the actual
retention owners and direct operational/report consumers. The measured P2P
problem is one finding among several source-supported retention problems;
other findings are not newly benchmarked. No tests, matrix, JFR, full-day run,
agents, staging or commits. Implementation/review/closure await separate requests.
Planning review read the complete new document and both handoff diffs, checked
existing named owners and verification classes (new tests explicitly marked),
and traced activation, consumption, disposition, bag transfer and closure
ordering. Recorded migration hazards for future OSR records, capacity-triggered
output closure, shrinking classification maps and visual observation lifetimes.
Tracked and new-file whitespace checks found no whitespace errors; Git's
no-index check returns 1 for the new-file difference. Final change surface is
this new plan plus the two handoff documents only. No performance gain or
unmeasured bottleneck attribution is certified.

2026-10-16 hardening revision: user authorized plan/document hardening only and
explicitly rejected a master-based prerequisite merge before a successful
full-day run. Initial git status contained the two modified handoff documents
and this untracked plan; all existing changes were preserved. Inspected current
composition factories/callers, bootstrap/supply ordering, COLLECT decisions,
correlation commit/publication, cutoff/report payloads, release diagnostics,
world phases, inspection targets and existing scenario construction seams.
Resolved factory ledger ownership, callback construction order, source versus
target counters, partial-missing future-consumer pins, classification epoch
dependency, exact report delta and cutoff scalar retirement. Added separately
authorized leaves, per-owner live-sentinel tests and a real-flow non-empty
checkpoint with test-only allocation staging. No production test hook or
runtime benchmarking subsystem is authorized. Existing source verification
classes were checked by path (64 distinct command selections, 10 explicitly
new classes); no named existing command test was missing. New production/test
types are explicitly named above. No tests, full-day/matrix/JFR, agents, git
branch changes, staging or commits were run. Review/implementation and all
previous programme gates remain unperformed unless already separately recorded.

2026-10-16 FIFO-feed revision (historical pending-decision entry; resolved below):
user established ascending filename as actual
warehouse-to-OSR feed order and authorized plan revision. Initial status was
the same two modified handoff documents plus this untracked plan; preserved
all existing changes. Source inspection confirmed directory filename sorting,
path-list encounter loading, physical manifest assembly and order-preserving
executable filtering. Subsequent supply grouping/type priority is not feed
order. Replaced the per-batch-cursor target with one pending FIFO, post-store
dequeue and independent OSR release decisions, with explicit ordering tests.
Two existing policy interactions were presented for user choice: low-water
authorization versus capacity-only admission; configured-startup-centre prefix
versus capacity-wide preload. They remain OPEN, and Step 5A is planning-blocked
rather than left to an implementer. No source/config/test changes, test runs,
agents, branch operations, staging or commits.

2026-10-16 supply decisions finalized: user confirmed that DSP operatives request
successive centres from the wholesaler in the known order, first two configured
centres are supplied overnight, and low-water gating should remain. Near-full
overnight occupancy is not calibrated evidence or a new target. Retained the
configured-startup FIFO prefix, capacity/rate-limited overflow, inclusive
low-water requests and independent OSR release policy. Specified shared deque/
bootstrap constructors, head-only timing/accounting, EMPTY-only request order,
current-state reporting and staged 5A-5D scope/commands/tests. This resolves the
previous two OPEN decisions; it does not implement those steps. Initial status
was the same three planning documents, all preserved. No source/config/test
edits, tests, agents, branch operations, staging or commits in this revision.

2026-10-19 Step 1 implemented and focused-verified. The user separately authorized
creation/switching of `feature/dsp-operational-window-streamlining` from scheduler
HEAD `3cb8acb`; the sole intervening commit since `7d28bd0` contains the three
planning documents only. Initial `git status --short` was empty. Added only
DspOperationalWorkLedger, DspOperationalWorkSnapshot and their new ledger test,
plus this execution entry; no existing runtime owner or composition was changed.
All edits used apply_patch. No agents, staging, commits or later leaves.

Section 3.1's current/incoming maps, indexed source/target counts, exact AV02
lookups, prevalidation boundaries, effective missing-pack replacement and scalar
centre/line totals are implemented. Large immutable subviews are invalidated
independently; bag accounting shares them unchanged. No ended-ID/history map,
remaining-bag snapshot array, worker mutation or runtime wiring was introduced.

Implementation verification (JDK 21.0.7, repository root):
`./gradlew test --tests online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkLedgerTest`
passed: 9 tests, zero failures/errors/skips. The sandbox initially denied the
Gradle wrapper cache lock; the same command succeeded with approved cache access.
No compile/test corrective rerun was needed. Tests cover mixed/split input,
STORE/P2P terminal validation, pending versus live EMPTY identity, current ID
collision and reuse after retirement, bag/source lifetime separation, partial
and fully missing accounting, rejected-transition no-mutation, frozen snapshots,
subview identity reuse, zero rows and 1,000 small retirement cycles with a live
sentinel. Package-private entry counts verify removal from every per-entity map;
the final sentinel also completes and leaves those maps empty. Checked arithmetic
was reviewed; numeric-limit overflow branches were not forced by synthetic state.

Complete new-file diffs and this record were reviewed against Sections 3.1,
3.5, 3.7, 4.0 and Step 1: no required Step 1 contract violation or unresolved
architectural choice found. Tracked and explicit new-file whitespace checks
found no whitespace errors (Git may warn about LF-to-CRLF conversion). Final
change surface is this modified plan plus the three untracked new Java files.
Runtime integration, full-day throughput and whole-feature boundedness remain
UNPROVEN and outside this unwired slice. The successful-full-day/master-merge
gate remains OPEN. Next authorized-on-request leaf is 2A; stop after Step 1.
