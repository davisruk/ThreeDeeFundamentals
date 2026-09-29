# DSP Adapting Sheet-Owned Bin Groups Plan

Status: proposed for user review. No implementation step is authorized by creating this plan.

## Purpose

An ADAPTED source tote can prepare lines for more than one ASSOCIATED order/sheet. The
existing `PreparedLineKey(targetOrderId, lineReference)` correctly gates and retrieves
each fulfilment sheet's lines, but `AdaptingStorageLayout` currently places lines in
pharmacy-sequential bins. A physical bin can therefore mix lines for different
fulfilment sheets. Change the full-day Adapting storage composition so each occupied
bin belongs to exactly one fulfilment `OrderSheetKey`. If that sheet's current bin
reaches `AdaptingStorageConfig.linesPerBin()`, allocate a new physical bin linked to
the same sheet. This is a storage/inspection correction, not a scheduling or routing
policy change.

“All packs for a sheet” means all *prepared ADAPTED packs stored at Adapting* for
that sheet. Direct packs already in an ASSOCIATED tote, or picked by Third Party
for that tote, never occupy these bins. The current prepared-line model represents
one planned physical pack per ADAPTED line, so the existing `linesPerBin` limit is
the applicable pack-count limit; this plan does not add dimensional/volume packing.

## Required Reading Before Each Step

Follow `AGENTS.md` and the complete mandatory reading order in
`docs/codex-instructions.md` first. Then read this whole plan and, for the selected
step, the relevant current source and tests named below. In particular read:

- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`, sections 3.4,
  5.1, 8.1, 8.3, and the outbound/inbound sheet distinction;
- `docs/scheduler/dsp-operational-scheduling-requirements.md`, section 7;
- `docs/scheduler/dsp-recoverable-input-rejection-plan.md`, fixed decisions 5–6;
- `AdaptingStorageLayout`, `AdaptedLineStore`, `AdaptedLineRecord`,
  `AdaptingStorageLocation`, `AdaptingStorageConfig`, `AdaptingBench`,
  `AdaptingAreaController`, `AdaptingVisitFactory`, and their named tests;
- `DspFullDayInputPreflight`, `DspFullDayBagPlanningRequestFactory`,
  `BagPlanningResult`, `PlannedPackSlot`, `DspFullDayAnalysisRuntimeFactory`,
  and `DspFullDayAnalysisRuntime` for Steps 1 and 3.

Record `git status --short` before editing for each step and preserve existing
changes. Use `apply_patch` for every edit. Stop if a named file/test is absent,
the inspected code contradicts a contract below, or an implementation-significant
choice remains unresolved. Do not implement another step without user initiation.

## Current Facts And Fixed Contracts

1. Full-day input preflight runs once before bag planning. It rejects missing,
   duplicate, or mismatched source/fulfilment correlation groups and removes those
   lines from executable input. Do not add a per-fixed-step correlation scan or
   reinterpret `referenceSheetNumber` as the target sheet.
2. `PreparedLineKey` remains target order ID plus globally distinct line reference.
   It is not changed to include a sheet. A valid planned pack slot already has the
   exact `fulfilmentOrderSheetKey`; use this pre-runtime fact to map each executable
   ADAPTED source line to its target ASSOCIATED or EMPTY sheet.
3. The ADAPTED STORE visit stages prepared records; only completed STORE publishes
   prepared-line keys. ASSOCIATED/EMPTY release still waits for its own keys.
   COLLECT requests exact keys and atomically refuses a missing batch. Do not change
   OSR release, scheduler ranking, station route, tote lifecycle, bag planning,
   provenance, or P2P allocation.
4. Fulfilment orders are pharmacy-pure under `DspOrderValidator`. A sheet-owned bin
   group therefore has one pharmacy. An ADAPTED source tote can serve many groups
   and pharmacies. A group is identified by the *fulfilment* `OrderSheetKey`, never
   by the ADAPTED source sheet or physical tote ID.
5. In the full-day runtime, a bin has one target sheet and one pharmacy. Bins for
   the same sheet form an ordered group with one-based ordinals; the next bin is
   allocated only when the previous one has accepted `linesPerBin` records. Distinct
   sheets never share a bin, even if one bin has unused space. Physical coordinates
   retain the existing pharmacy-preferred bench and rack/shelf/bin progression.
   Bin allocation is deterministic in STORE arrival/line order. Coordinates are
   monotonically allocated per pharmacy and are not recycled during this run.
6. A bin's link is the next location in its sheet group. An empty predecessor can
   remain in an active group's inspection chain after individual removal; once
   the group's last staged record is collected, remove the active group. The
   compact existing `AdaptedLineStoreSnapshot` continues to count *occupied* bins,
   not empty historical positions. No renderables or clickable UI are added now.
7. Preserve source provenance in `AdaptedLineRecord` and collection order. Do not
   substitute the fulfilment sheet for its source sheet. The target sheet belongs
   to the storage group and inspection snapshot, not to `PackSourceProvenance`.
8. Build the immutable mapping once at full-day runtime construction, after
   executable-input projection and bag planning. Mutate bin groups only on the
   simulation thread at STORE/COLLECT, never on the scheduler evaluation worker.
   Detailed bin snapshots are constructed only on explicit inspection request,
   not in the fixed-step metrics/progress/scheduler snapshot paths.
9. Existing standalone Adapting fixtures and `AdaptingDebugRig` do not carry a
   full-day bag plan. The debug rig currently displays moving totes and selectable
   bench information, including a staged-line count; it does not render storage
   bins or their staged pack contents. Its current two-argument
   `AdaptingStorageLayout` constructor and pharmacy-sequential legacy behaviour
   remain compatible, so the rig continues to work and look as it does now. The
   new three-argument constructor enables strict sheet-owned storage in the
   full-day composition. Do not fabricate a target sheet from the ADAPTED line's
   `referenceSheetNumber` for legacy callers. Wiring a future visual debug rig to
   sheet-owned storage is separate work requiring its own target-sheet catalog.

### Fixed efficiency contract

The current observed full-day pace is about 10 wall-clock seconds per simulated
minute; the feature must not add work proportional to fixed-step frequency or
to unrelated stored work. Build and validate the target-sheet catalog once at
runtime construction, then retain it. STORE and COLLECT are the only mutation
boundaries for grouped bins. In strict mode keep direct indexes from prepared
key to record/bin, from target sheet to bin group, and an active-record count
on each group. `stageAll` validates and applies only that visit's lines, in
O(k) work for k supplied lines. `take` is O(1) by key; `takeAll` validates and
removes only its k requested keys in O(k). Empty-group detection uses its
maintained count, not a scan of all staged records or groups. Appending an
overflow bin touches only that group and its pharmacy coordinate cursor.

Never scan all catalog entries, staged records, bins, or other sheets during
one STORE or COLLECT, and never rebuild those indexes on a simulation tick.
The fixed-step path may perform the necessary O(k) work when a STORE/COLLECT
event actually occurs, but must not add unconditional per-tick traversal or
allocation. No bin/catalog snapshot or newly derived whole-store map may be
built by scheduler evaluation, admission, completion evaluation, metrics
collection, or routine progress reporting.
`binSnapshots()` is an explicit inspection call. Cache its immutable result and
return the same instance on repeated calls without mutation; invalidate it only
after a successful STORE or COLLECT mutation. This does not authorize changing
the existing compact `AdaptedLineStoreSnapshot` or unrelated hot paths. Review
the actual call graph and indexed operations, not only their nominal APIs.

## Step 1: Publish The Exact Prepared-Line Target Sheet Catalog

### Change surface and contract

- Create `adapting/AdaptingTargetSheetCatalog.java`: an immutable defensive copy of
  `Map<PreparedLineKey, OrderSheetKey>`. Its constructor rejects null entries
  and a target sheet whose order ID
  differs from `PreparedLineKey.targetOrderId()`. Expose
  `OrderSheetKey requireTargetSheet(PreparedLineKey key)`; a missing key throws
  `IllegalStateException` with the key. It does not infer from
  `referenceSheetNumber`.
- Create `analysis/runtime/DspFullDayAdaptingTargetSheetCatalogFactory.java` with
  `create(LoadedDspData executableData, BagPlanningResult bagPlan)`. Iterate
  executable ADAPTED orders and their lines in source order. For each line, require
  exactly its `PlannedPackSlotKey(sourceOrderSheetKey, lineReference, 1)` from the
  bag plan. Require `initialPhysicalToteId` to be empty, require source provenance
  to match the source line and the target order ID to match its
  `referenceOrderId`, and require the slot's fulfilment
  sheet to identify an executable ASSOCIATED or EMPTY order whose ADAPTED alias
  contains the same `PreparedLineKey`. Reject duplicate prepared keys. Publish one
  catalog after all validation succeeds. Do not persist a second mutable mapping.
- This is a pre-runtime projection of already validated executable work, not a
  replacement for `DspFullDayInputPreflight` or bag planning. If the bag plan and
  executable input disagree, fail runtime construction before creating station
  work.

### Decision-complete tests

- Create `AdaptingTargetSheetCatalogTest`: two different line keys with the same
  target order ID map to sheets 001 and 002 respectively; missing key throws;
  invalid target order ID and null entries fail; caller map mutation after
  construction cannot change the catalog.
- Create `DspFullDayAdaptingTargetSheetCatalogFactoryTest`: construct valid
  executable input/bag-plan fixtures through existing planning helpers for one
  ADAPTED source sheet feeding two ASSOCIATED sheets with distinct line refs;
  assert exact mapping. Include an EMPTY target fixture (same mapping rule).
  Test a missing/mismatched planned slot and a duplicate prepared key, asserting
  no catalog is returned. Use the existing preflight fixture conventions rather
  than changing production correlation rules.

### Expected output

Exact target-sheet identity is available as an immutable, startup-owned lookup;
no station or scheduler behaviour changes in this step.

### Implementation verification

Run only:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalogTest --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAdaptingTargetSheetCatalogFactoryTest
```

### User verification

No additional user-run verification for Step 1.

## Step 2: Allocate And Inspect Sheet-Owned Overflow Bins

### Change surface and contract

- Modify `AdaptingStorageLayout` and `AdaptedLineStore`. Retain the two-argument
  layout constructor and its existing legacy allocation. Add a three-argument
  `(AdaptingStorageConfig, AdaptingStorageMap, AdaptingTargetSheetCatalog)`
  constructor for strict grouping. The strict path resolves every STORE key through
  the catalog before mutation; it must never fall back to an inferred sheet.
- Add `AdaptedLineStore.stageAll(List<DspOrderItem>, OrderSheetKey sourceSheet,
  String serviceCentreId)`. `AdaptingBench.completeActiveVisit()` uses this for
  STORE. Before the mutation boundary, validate the whole list: nonnull/nonempty,
  ADAPTED line type, duplicate keys within the visit or already staged, every
  strict catalog lookup, target-group pharmacy consistency, and the required
  preferred bench. A validation failure leaves records, bin groups, and coordinate
  cursors unchanged. After validation, stage in source-list order. Retain the
  existing one-line `stage(...)` and `stage(AdaptedLineRecord)` signatures;
  direct record staging remains legacy-only and is rejected before mutation in
  strict mode because it supplies no validated target-sheet placement.
- In strict mode maintain simulation-thread-owned indexes by prepared key and
  by target sheet. Each target-sheet group stores its pharmacy and an ordered list
  of bins. Each bin stores its immutable physical location and accepted-key count
  plus its currently staged keys. The pharmacy coordinate allocator assigns a
  fresh bin location for a new group or overflow; it never gives one coordinate
  to two active groups. Reuse the current bench preference and bin/shelf/rack
  rollover configuration. Do not change `AdaptingStorageLocation`'s identity.
- On `take` and `takeAll`, remove the requested keys from both indexes. Prevalidate
  an entire `takeAll` batch for missing **or duplicate** keys before mutation,
  retain caller-requested result order, and leave every record/bin unchanged on
  failure. Use the maintained active-record count for constant-time empty-group
  detection; do not rescan other bins or sheets. Delete a group only after its
  final staged key is removed. Do not
  recycle its physical coordinates. Prepared-line readiness is not retracted by
  collection.
- Create `adapting/AdaptingBinSnapshot.java` with immutable
  `AdaptingStorageLocation location`, `OrderSheetKey targetOrderSheetKey`,
  one-based `int ordinal`, `Optional<AdaptingStorageLocation> nextLocation`, and
  `List<AdaptedLineRecord> stagedRecords` (defensively copied). Expose
  `List<AdaptingBinSnapshot> AdaptedLineStore.binSnapshots()` through the layout.
  Return active groups in first-bin allocation order, each group's bins in
  ordinal order. The next link points only within the same group; the tail has
  no next link. This is an explicit on-demand immutable inspection API, not a
  change to `AdaptedLineStoreSnapshot` or its hot-path consumers. Cache the list
  by storage mutation version so two calls without mutation return the same
  list object. Neither the list nor any contained snapshot is built during a
  fixed-step update unless an external inspector explicitly requests it.

### Decision-complete tests

- Extend `AdaptedLineStoreTest`: interleave two keys for target sheet 001 and two
  for sheet 002 at the same pharmacy with `linesPerBin=2`. Assert two distinct
  locations, each bin contains only its sheet, and collection by one sheet leaves
  the other intact. Use the catalog constructor; retain the existing legacy
  location assertions for the two-argument constructor.
- With `linesPerBin=2`, stage at least five keys for one target sheet, interleaved
  with a second sheet. Assert three ordered bins for the first sheet, occupancy
  2/2/1, correct next links, and rack/shelf/bin rollover. Assert a later source
  tote can append to the current non-full tail of the same sheet without moving
  previously staged records.
- Assert whole-visit validation: a missing catalog entry, duplicate key, and
  cross-pharmacy group attempt each fail without changing records, groups,
  links, or next physical location. Assert duplicate or missing `takeAll` keys
  similarly cause no partial removal; successful removal preserves requested
  order, drops only its own group, and leaves no active bin snapshots for a
  fully collected group. Mutating a returned snapshot's lists must not affect
  the store.
- Assert `binSnapshots()` returns the identical list object on two consecutive
  calls without mutation, changes identity after successful STORE/COLLECT, and
  retains identity after rejected STORE/COLLECT. Verify interleaved large
  unrelated sheet groups are unchanged by one sheet's collection; inspect the
  direct key/group indexes in review to rule out whole-store scans. Do not use
  wall-clock thresholds as unit assertions.
- Extend `AdaptingBenchTest` to exercise `stageAll` through a STORE completion:
  all lines become staged together before the completion is consumed; a bad
  line/key blocks the visit without staging the earlier valid line. Preserve the
  existing COLLECT blocked-state behaviour.

### Expected output

Strict grouped storage is available for wiring; existing legacy Adapting callers
and compact storage snapshots remain compatible.

### Implementation verification

Run only:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingCollectFlowTest
```

### User verification

No additional user-run verification for Step 2.

## Step 3: Wire Full-Day Storage And Prove Two-Sheet Flow

### Change surface and contract

- Modify `DspFullDayAnalysisRuntimeFactory` to build the Step 1 catalog once from
  executable input and the already-computed bag plan, then pass it to the Step 2
  strict `AdaptingStorageLayout` constructor. Construct the catalog before any
  station controller is registered. Do not add catalog creation or bin traversal
  to a fixed-step callback.
- Modify `DspFullDayAnalysisRuntime` to retain the `AdaptedLineStore` and expose
  `List<AdaptingBinSnapshot> adaptingBinSnapshots()` as a simulation-thread-only,
  on-demand immutable inspection call. The factory is the only direct runtime
  constructor caller. Do not add bins to the routine
  `DspFullDayAnalysisRuntimeSnapshot`, metrics, or progress log, and do not render
  clickable bins in this feature. Verify by source call-graph inspection that
  `adaptingBinSnapshots()` and `binSnapshots()` are absent from the fixed-step
  update, scheduler, admission, completion, metrics, and progress paths.
- Keep `AdaptingAreaController` STORE publication and COLLECT load-plan mutation,
  `OperationalDependencyReadinessPolicy`, and all upstream 12N/OSR/P2P logic
  unchanged. An ASSOCIATED tote for sheet 001 remains blocked only by sheet 001's
  prepared keys, likewise sheet 002. Each collects only its keys, even though
  both were deposited by one ADAPTED source tote.

### Decision-complete tests

- Extend `DspFullDayAnalysisRuntimeFactoryTest` using actual
  `DspFullDayInputPreflight`, bag planning, and runtime composition: one ADAPTED
  physical tote for one pharmacy has distinct prepared line refs A1 and A2
  targeted to ASSOCIATED order sheet 001, and B targeted to the same order's
  sheet 002. Set `linesPerBin=1`. Before STORE completion, both ASSOCIATED
  candidates remain dependency blocked. After STORE, inspect two sheet-pure
  groups: sheet 001 has two linked bins and sheet 002 has one separate bin.
  Let sheet 001 collect and verify its keys and
  bins disappear while sheet 002's staged keys/bin and eventual collection remain
  correct. Assert source provenance and resulting bag correlations are unchanged.
  Use state transitions/observations and a bounded update loop, not wall-clock
  timing assertions.
- Include one EMPTY collection regression using the same strict catalog path.
  Existing malformed correlation preflight tests remain authoritative; do not
  make missing or duplicate source/fulfilment data executable to satisfy the
  new storage lookup.

### Expected output

The full-day simulator uses sheet-owned linked bins while its operational
release, collected packs, provenance, and completion behaviour remain the same.

### Implementation verification

Run only:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactoryTest --tests online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflightTest --tests online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationProcessingControllerTest
```

### User verification

Run the complete suite separately after Step 3 implementation verification:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'; .\gradlew test
```

The implementation agent must not run the complete suite, a full-day performance
run, or JFR analysis as part of this plan. Await the user's result.

## Acceptance And End-Of-Feature Review

Acceptance requires all focused checks and the user-owned suite to pass, with
no changed OSR release decisions, prepared-key identity, collection ordering,
source provenance, or legacy Adapting fixture behaviour. An independent
architecture review then traces the actual Step 1–3 diff and production
composition, reporting PASS/FAIL/UNPROVEN for: exact target-sheet catalog and
preflight boundary; bin purity and overflow linkage; atomic STORE/COLLECT
failure behaviour; source-versus-target identity; immutable on-demand inspection;
simulation-thread ownership and fixed-step efficiency; legacy compatibility;
two-sheet and EMPTY scenario evidence; and absence of unrelated scheduler,
performance, visual, or dispatch changes. Stop on any FAIL or UNPROVEN item for
user direction rather than redesigning during review.

## Documentation Closure

Only after user verification and architecture review are green, update:

- `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`: describe
  sheet-owned Adapting bin groups for executable prepared work, linked overflow
  at `linesPerBin`, and preserved source provenance versus fulfilment sheet;
- `docs/scheduler/dsp-operational-scheduling-requirements.md`: clarify that
  dependency readiness/release remains per prepared line, while the storage
  layout groups those lines by fulfilment sheet without changing OSR release;
- `docs/codex-context.md`: record the verified implementation, the full-day-only
  strict composition and legacy debug compatibility, and defer clickable bin
  renderables/inspection UI;
- `docs/codex-instructions.md`: adjust the mandatory reading order/current
  direction only if this plan is then the active or newly completed feature;
- this plan: mark each step's implementation and user verification outcome and
  the architecture-review result, without rewriting its fixed contracts.

Closure is documentation reconciliation, not another storage or renderer design
step. If implementation or review differs from this plan, stop and seek a plan
revision before claiming completion.
