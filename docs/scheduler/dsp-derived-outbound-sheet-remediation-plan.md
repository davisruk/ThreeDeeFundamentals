# DSP Derived Outbound Sheet And PT10M Remediation Plan

Status: proposed; no implementation step has started.

## Purpose And Evidence

The newer full-day 12N run failed at `PT10M` with `Source sheet still has an
active non-outbound assignment: TOTE0008814067/001`. The current
`OutboundToteAllocator.rejectActiveNonOutboundSourceAssignments(...)` rejects
the completed bag, while `OutputSheetAllocator` tries to reuse the incoming
`OrderSheetKey` for its first outbound assignment. This is an identity error:
the inbound physical tote and an independently supplied outbound bag tote may
exist at the same time. Removing only the guard would leave the outbound sheet
in conflict with the inbound sheet in `PhysicalToteLifecycleLedger`.

The newer dataset check parsed all 4,385 JSON messages. In the 4,262 FULL_PACK
and ASSOCIATED messages (167,282 lines), no `(incoming order ID, patient ID)`
appeared on multiple incoming sheets or physical transport-container IDs.
145 patient IDs appeared in different incoming orders, so the containment
scope is **within an incoming order**, not global by patient ID. The upstream
system guarantees this constraint; it is not a new simulator validation task.
12N has no Patient Prescription Group (PPG) identifier, so the simulator does
not infer PPG membership. A valid prescription may produce several bags and
those bags may occupy distinct outbound physical totes, but each derived
output sheet retains the same incoming order and sheet provenance.

This is a functional correction separate from the seven-step fixed-step
performance remediation. Do not append it as a performance step or claim a
measured speedup. The existing lifecycle and operational scheduling
requirements are updated with this plan's input assumption and sheet formula.
The completed `dsp-outbound-tote-allocation-plan.md` records the historical
first-sheet-reuse/max-plus-one behavior; this plan supersedes only that
behavior, not its other outbound ownership and lifecycle contracts.

## Required Reading Before Each Implementation Step

Read `docs/codex-instructions.md` and its mandatory document order, then this
entire plan, `dsp-logical-physical-lifecycle-requirements.md`,
`dsp-operational-scheduling-requirements.md`, and the completed
`dsp-outbound-tote-allocation-plan.md`. Inspect the exact production and test
files named by the selected step. Record `git status --short` before edits and
preserve existing work. The user starts each step separately. Use `apply_patch`
for every edit. Do not run a full day, performance test, or JFR during a step.

## Shared Fixed Contract

- `OrderSheetKey(orderId, sheetNumber)` remains the logical assignment key;
  `PhysicalToteId` remains the physical carrier identity. Do not merge them.
- For each incoming source key, the first **distinct outbound physical tote**
  carrying its bags has one-based outgoing-tote ordinal 1; the next distinct
  tote has ordinal 2, regardless of P2P line or closure state. The same
  source/tote pair always reuses its output key. A tote carrying bags from
  several source keys receives one derived key for each source, with an
  independent ordinal sequence per `(orderId, incoming sheetNumber)`.
- Calculate `outgoingSheetNumber = 80 + (incomingSheetNumber * 20 +
  outgoingToteNumber)`. Thus `TOTE001/001` maps to `/101`, `/102`, and
  `TOTE001/002` maps independently to `/121`, `/122`. The ordinal is *not* the
  physical transport-container ID, global outgoing tote count, order number,
  bag ordinal, or sheet number. Do not format or emit 32R here.
- Apply the derived mapping to every bag with a source `OrderSheetKey`,
  including EMPTY logical work (which has a source sheet but no OSR inbound
  physical tote). Never reuse the incoming sheet number as output ownership.
- The input source assignment may remain active in `INBOUND_PACK`,
  `PREPARATION`, or `PRE_P2P` when the completed bag is allocated. Outbound
  allocation neither rejects nor terminates that source assignment. The
  lifecycle ledger still forbids two active assignments for the **same**
  output key; `OUTBOUND_BAG` advances to `OUTBOUND` on closure as before.
- Preserve `PlannedBag`, `OutputSheetAllocation`, `AllocatedOutboundBag`,
  `PlannedPackTrace`, controller/receiver interfaces, bag receipt order,
  service-centre/pharmacy purity, bag capacity, per-line open-tote ownership,
  and immutable source/fulfilment provenance. Do not alter scheduler worker
  inputs, P2P machines, inbound lifecycle, or dispatch/32R.
- Preserve `OutputSheetAllocator(Collection<OrderSheetKey>)` and
  `resolve(List<OrderSheetKey>, PhysicalToteId,
  PhysicalToteLifecycleSnapshot)` signatures. The collection is the complete
  known incoming-sheet catalog in production. Preserve multi-source-list
  resolution and `PlannedBag.owningOrderSheetKeys()` compatibility; valid
  upstream FULL_PACK/ASSOCIATED data does not require multi-sheet bags.
- No patient/PPG/prescription containment scan or enforcement is added at
  loading, bag planning, allocation, or each fixed step. This is a trusted
  upstream contract, not an optimization claim for simulation-time hot paths.
- The three-digit protocol sheet range is 001..999. Reject an attempted
  per-source ordinal 20 or greater: 20 reaches the next source sheet's
  20-number block. Reject arithmetic overflow, a derived number above 999,
  a derived key matching any known incoming key, and a derived key already
  owned by a different source/tote. Do not skip numbers, wrap, fall back to
  max-plus-one, or silently reuse a colliding key. These are explicit
  unsupported-input failures; no recovery workflow is added.
- New mutable allocator state is simulation-thread-owned and per runtime.
  Preserve deterministic first-use order. No global cache, locks, background
  work, renderables, or new fixed-step scan.

## Step 1: Derive Output Keys By Source And Distinct Outbound Tote

### Required change surface

Modify `app/src/main/java/online/davisfamily/warehouse/sim/dsp/outbound/OutputSheetAllocator.java`
and `app/src/test/java/online/davisfamily/warehouse/sim/dsp/outbound/OutputSheetAllocatorTest.java`
only. Do not change lifecycle ledger, tote allocator, bag planner, runtime
factory, data loader, or any other test in this step.

### Implementation contract

Replace `highestSheetNumberByOrderId` and the original-key/active-output
candidate search with a defensive copy of known incoming keys, a mapping by
`(source OrderSheetKey, outbound PhysicalToteId)`, a per-source next ordinal,
and reverse derived-key ownership for collision detection. Retain the existing
transactional staging pattern inside one `resolve(...)`: validate all source
keys in input order and publish map/counter changes only after every requested
mapping succeeds. A repeated pair returns the same key without advancing its
counter. A new pair takes the next ordinal even if an earlier tote has closed
or its output assignment has ended. Multiple source keys in one call have
independent counters and preserve list order.

For each prospective or repeated output key, inspect the supplied immutable
lifecycle snapshot: no active assignment is allowed on a *new* derived key;
an existing mapping may be active only on its target tote in `OUTBOUND_BAG`
or `OUTBOUND` stage. Reject stale/foreign or non-outbound output assignment
before publishing changes. Ignore any active assignment on the incoming
*source* key. Keep non-null/duplicate-source validation and immutable result
list. The constructor must reject null known keys and copy the catalog. An
unknown source key remains accepted for compatibility with existing empty
test catalogs; its derived key is still collision-checked against the supplied
known catalog and already generated keys. Do not add a catalog-wide
patient/prescription pass.

Tests through `OutputSheetAllocator.resolve(...)` must prove: first `/001`
source yields `/101` despite active `PRE_P2P` source assignment; second tote
gets `/102`; source `/002` starts `/121`; same source/tote reuse, including
after closure, does not advance; two orders or source sheets in one physical
tote are independent and ordered; ordinal 20, >999, known-key collision,
and active/foreign output-key conflicts fail without publishing partial
mapping/counter state; null and duplicate input validation remains. Replace
the old original-key and max-plus-one assertions.

### Expected output

`OutputSheetAllocator` always returns derived, source-traceable output keys
and never claims the incoming source key.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocatorTest
```

### User verification

No additional user verification is required for this step.

## Step 2: Allocate Completed Bags While Inbound Source Remains Active

### Required change surface

Modify only `OutboundToteAllocator.java` in the same outbound production
package, and these existing tests:

- `outbound/OutboundToteAllocatorTest.java`
- `outbound/OutboundGeneratedSheetIntegrationTest.java`
- `outbound/MultiLineOutboundToteAllocationTest.java`
- `outbound/DspOutboundToteAllocationScenarioTest.java`
- `av02/DspAv02OperationalAllocationScenarioTest.java`

All paths are under `app/src/test/java/online/davisfamily/warehouse/sim/dsp/`
unless noted as production above. Do not modify
`PhysicalToteLifecycleLedger`, `DeterministicBagPlanner`,
`OutboundToteAllocationController`, the full-day runtime factory, other
station tests, or scheduler policy.

### Implementation contract

Remove only the pre-allocation
`rejectActiveNonOutboundSourceAssignments(...)` check and its now-unused
private helper. Keep the duplicate-bag, time, tote-ID, purity, capacity,
output-key assignment validation, ledger registration/assignment, and close
sequence unchanged. `validateOutputAssignments(...)` still rejects an output
key assigned to another tote or non-outbound stage. Existing source
assignments are not examined or mutated by outbound allocation. A successful
allocation produces one `OutputSheetAllocation(source, derived output)` per
planned source key, assigns the derived output key on the outbound tote, and
leaves inbound lifecycle history intact.

Update historical numeric expectations in the named tests to `/101`, `/102`,
`/121` as their fixtures require. Add a real-ledger allocation scenario with
an actively assigned inbound `TOTE0008814067/001` source: complete bag
allocation succeeds, output `/101` is active on a different outbound tote,
source `PRE_P2P` assignment stays active and unchanged, and closing the
outbound tote advances only `/101` to `OUTBOUND`. A later bag from that source
on another physical outbound tote gets `/102` even while the first output
assignment stays active. Preserve exact bag and pack provenance. Assert
duplicate bag allocation and a genuine output-key ownership conflict still
fail without taking a runtime bag out of the receiver. The existing
`DspAv02OperationalAllocationScenarioTest` must assert EMPTY output derives
from its logical source sheet, not that it equals the source key. Do not add
an end-to-end full-day run in this step.

### Expected output

The PT10M source-assignment condition no longer rejects a completed bag, and
the outbound assignment uses a distinct, derived sheet. Existing hard
outbound ownership protections remain.

### Implementation verification

Run exactly:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocatorTest --tests online.davisfamily.warehouse.sim.dsp.outbound.OutboundGeneratedSheetIntegrationTest --tests online.davisfamily.warehouse.sim.dsp.outbound.MultiLineOutboundToteAllocationTest --tests online.davisfamily.warehouse.sim.dsp.outbound.DspOutboundToteAllocationScenarioTest --tests online.davisfamily.warehouse.sim.dsp.av02.DspAv02OperationalAllocationScenarioTest
```

### User verification

No additional user verification is required for this step.

## User-Owned Functional Gate

After both implementation steps and their focused commands are green, the
user runs the existing full-day plan's focused regression command:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.analysis.* --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.p2p.bag.* --tests online.davisfamily.warehouse.sim.dsp.p2p.allocation.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.av02.* --tests online.davisfamily.warehouse.sim.dsp.station.processing.* --tests online.davisfamily.warehouse.sim.dsp.station.continuation.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.threedee.sim.framework.time.*
```

Then run the complete suite and rebuild the installed distribution:

```powershell
.\gradlew test
.\gradlew :app:installDist
```

Retry the newer data beyond `PT10M`; confirm no source/output identity exception,
that output sheets follow the formula in allocation history, and that work
continues after the former failure point. A run past `PT10M` is evidence for
this failure mode, not proof of whole-day completion or 32R correctness.
Performance comparison to the older dataset remains unproven. No model-run
performance test or JFR is authorized by this plan.

## Architecture Review And Documentation Closure

After user verification, independently review the actual feature diff and
relevant production control flow. Report PASS/FAIL/UNPROVEN for the formula
and ordinal scope, distinct source/output keys under active inbound state,
collision/range failures without partial resolver publication, ledger's
one-active-output assignment rule, unchanged inbound/provenance/purity/capacity
behavior, EMPTY handling, compatibility of the existing multi-source API,
simulation-thread ownership, and absence of load-time or fixed-step patient
scans. Flag unnecessary production changes.

Only after green user verification and review, mark this plan complete with
the focused/user results and observed PT10M outcome. Reconcile only stale
outbound-sheet descriptions in `docs/codex-context.md` and
`docs/codex-instructions.md` and the relevant current-position entry of
`docs/scheduler/dsp-scheduler-implementation-plan.md`; do not rewrite the
historical completed outbound-allocation plan. The lifecycle and operational
requirements already contain the approved rule. Leave dispatch/32R,
performance analysis, and any change to the upstream data contract deferred.
