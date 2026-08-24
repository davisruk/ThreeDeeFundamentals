# DSP P2P Sticky Line Leases Plan

Branch: `feature/dsp-p2p-sticky-line-leases`

Status: implementation complete, verified, and merged to `master`.

## Purpose

Introduce authoritative, sticky service-centre ownership for each P2P line and pin every physical
tote requiring P2P processing to one exact line before it leaves the OSR.

The required decision and ownership sequence is:

```text
simulation thread builds immutable operational + P2P snapshots
  -> scheduler worker selects candidate and exact P2P assignment
  -> simulation thread revalidates first route target and lease assignment
  -> accepted OSR release commits the lease and pinned physical-tote assignment
  -> assignment travels with the tote through any earlier stations and warehouse transport
  -> selected P2P arrival policy revalidates the same assignment and lease
  -> existing P2P arrival consumer and tipper-input path process the tote
  -> line drains, applicable work completes, open outbound tote closes
  -> fully quiescent line releases its service-centre lease
```

This branch must:

- model five configurable P2P lines without requiring five rendered assemblies;
- maintain zero or one service-centre lease per line;
- pin each released physical tote whose route requires P2P to exactly one line;
- keep the pinned P2P assignment separate from the tote's first route-entry target;
- prefer compatible leased lines and active-pharmacy affinity deterministically;
- acquire only an unleased, fully quiescent line when no compatible lease exists;
- reject cross-service-centre arrival and assignment attempts;
- keep leases while any known P2P work for the owner remains, including dependency-blocked work;
- close a prior owner's open output tote before releasing its lease;
- define quiescence from the complete P2P processing path, not one machine's idle state;
- keep all mutation on the simulation thread and all worker inputs immutable;
- preserve existing OSR, transport, station-arrival, tipper-input, bagging, and outbound-tote
  ownership boundaries.

This branch does not implement deadline-aware demand estimation, dynamically decide how many lines
one service centre deserves, reclaim surplus lines from a nonterminal service centre, pre-empt active
work, add full production geometry for five P2P lines, implement Adapting/Third Party consumers,
implement Exception handling, calibrate station timings, or run a complete production day. Those
remain later slices.

## Verified Implementation Result

- Five ordered P2P line definitions are composed without requiring five rendered assemblies.
- Every P2P-required physical tote is assigned once to an exact line and destination independently
  of its first route-entry target. Exact retries are idempotent and conflicting reassignment fails.
- Operational selection prefers compatible owner/pharmacy affinity deterministically, while direct
  P2P admission observes exact target capacity and earlier-station routes remain independently
  backpressured at their eventual P2P arrival boundary.
- Successful simulation-thread command application is the only lease-acquisition and assignment
  commit boundary. Deferred, rejected, and stale releases mutate neither lease nor OSR ownership.
- Warehouse transport carries the pinned assignment unchanged. Sticky local arrival admission
  revalidates exact tote, line, destination, service centre, and current lease ownership without
  mutating the lease.
- Complete detached activity snapshots cover arrival, tipper input, active tote/discharge, pack
  path, expected groups, bagging, receiver, and outbound tote state. Temporary machine idleness is
  not treated as line quiescence.
- Owner work remains authoritative until all known P2P manifests are consumed. Once processing is
  drained, any open outbound tote closes on one update and the lease releases only on a later fully
  quiescent update.
- Runtime inspection exposes ordered ownership, active pharmacy, blockers, assignments, closure,
  and transition details. The warehouse transport scene uses deterministic non-rendered line/output
  placeholders; its final accepted tote intentionally remains in tipper input because that scene has
  no downstream P2P assembly.
- Focused tests, the full Gradle suite, warehouse transport and tote-to-bag visual checks, and
  `ALT+R` reconstruction are green.

## Required Reading

Read before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-p2p-arrival-consumer-plan.md`
4. `docs/scheduler/dsp-warehouse-transport-routing-plan.md`
5. `docs/scheduler/dsp-operational-route-target-integration-plan.md`
6. `docs/scheduler/dsp-outbound-tote-allocation-plan.md`
7. `docs/scheduler/dsp-scheduler-implementation-plan.md`
8. `docs/tote-to-bag-requirements.txt`
9. `docs/bagging_machine_requirements.txt`

Inspect these classes before each affected step:

- `DspOperationalReleaseScheduler`
- `DspOperationalReleaseSnapshot`
- `DspOperationalReleaseSnapshotFactory`
- `OperationalReleaseSelection`
- `PharmacyGroupedSourceSequenceRankingPolicy`
- `OperationalRouteEntrySelector`
- `ReleasePhysicalToteFromOsrCommand`
- `OsrProcessingReleaseCommandHandler`
- `OsrProcessingReleaseRequest`
- `OsrOutboundRouteLaunchRequest`
- `RoutedPhysicalTote`
- `P2pArrivalAdmissionRequest`
- `P2pArrivalAdmissionPolicy`
- `P2pArrivalConsumerController`
- `TipperInputQueue`
- `ToteTrackTipperFlowController`
- `ToteToBagFlowController`
- `PdcConveyor`
- `PrlConveyor`
- `PcrConveyor`
- `BaggingMachine`
- `StoredBagReceiver`
- `OutboundToteAllocator`
- `OutboundAllocationSnapshot`

## Fixed Decisions

Do not revisit these decisions during implementation.

### Identity and routing

- `ReleasePhysicalToteFromOsrCommand.releaseTargetId` remains the first physical route-entry
  station. It must not be overloaded with the eventual P2P target.
- A tote that visits Third Party or Adapting before P2P has two distinct destinations: its current
  first route target and its pinned eventual P2P target.
- Add an immutable `P2pPhysicalToteAssignment` containing physical tote ID, service-centre ID,
  `P2pLineId`, and exact P2P `OperationalRouteDestination`.
- Only candidates whose `RouteRequirements.requiresP2p()` is true receive a P2P assignment.
  ADAPTED preparation/STORE totes that terminate at Adapting do not acquire a P2P line.
- The assignment is created once, committed with successful OSR release, and never moved to another
  line. Retry of the exact assignment is idempotent; a conflicting assignment is rejected.
- Direct-P2P releases require `releaseTargetId` to equal the assigned P2P destination target ID.
  Multi-station releases require them to differ whenever the first station is not P2P.
- Preserve compatibility constructors for legacy/debug tests where practical. The production
  operational path must reject a missing assignment for a P2P-required candidate.

### Baseline line selection

- Configured P2P line order is authoritative and deterministic. Never depend on `HashMap` order.
- For a candidate requiring P2P, rank line options in this order:
  1. a line already leased to the candidate service centre whose open outbound tote pharmacy is in
     the candidate's ordered pharmacy IDs;
  2. another line already leased to that service centre;
  3. an unleased, fully quiescent line;
  4. no assignment.
- Break ties by configured line order.
- For a direct-P2P first route, the selected line's route-entry queue must also have capacity. If a
  preferred matching line is full, evaluate the next compatible line before blocking the candidate.
- For a tote visiting an earlier station first, current P2P queue capacity must not gate OSR release.
  It is still pinned to a compatible line; that line's local arrival boundary applies backpressure
  when the tote eventually arrives.
- A line leased to another service centre is never a candidate, even when its machines appear idle.
- This branch does not reclaim a line from another service centre with nonterminal work. The later
  deadline-aware elastic-allocation branch may reclaim only a fully quiescent surplus line and may
  never pre-empt active work.

### Candidate ranking

- Preserve highest-priority eligible service-centre selection and stable source ordering.
- Within the selected service-centre cohort, active-line pharmacy affinity precedes the existing
  static pharmacy-group/source-sequence comparator.
- Affinity is derived only from the line's authoritative open outbound tote snapshot. Do not add a
  mutable global current-pharmacy field.
- Non-P2P candidates have no P2P affinity and retain existing ranking behavior.
- Line selection and candidate ranking must be pure functions of one immutable snapshot. The worker
  cannot read lease registries, queues, machines, or outbound allocators directly.

### Lease lifecycle

- A lease is acquired on the simulation thread only after the first route target accepts the OSR
  release request. A deferred/rejected/stale command acquires nothing.
- Validation happens before target mutation. Because command application is serialized on the
  simulation thread, a validated lease commit must be guaranteed to succeed; do not add rollback
  code around a queue that has already accepted a request.
- Legacy `DspOrderStatus` is not authoritative for physical completion: operational physical
  release deliberately does not mutate it. Do not use `WAITING`, `BLOCKED`, `RELEASED`, or
  `COMPLETED` alone to decide that owner work is exhausted.
- A service centre has remaining P2P work while any known physical manifest belonging to a logical
  route with `requiresP2p()` has not reached `PhysicalToteLifecycleState.CONSUMED_AT_P2P`.
  `InboundToteManifestCatalog` contains the complete loaded run, so unsupplied, OSR-stored,
  dependency-blocked, released, in-transit, and active manifests all retain the owner's leases.
- A P2P-required non-EMPTY logical order with no physical manifest is an invariant failure. EMPTY
  remains excluded from this branch's physical-work count until AV02 allocation creates its
  physical tote; expose the excluded EMPTY count diagnostically rather than holding leases forever.
- A lease cannot be released while the owner has remaining P2P work.
- Once no owner work remains, first wait for processing to drain. If an open outbound tote remains,
  close it through `OutboundToteAllocator.closeForApplicableWorkCompletion(...)` using the current
  authoritative simulation time.
- Perform at most one close-or-release transition per controller update, in configured line order.
  Closing and releasing on separate updates makes the state transition observable and deterministic.
- Release the lease only on a later update when the full line snapshot is quiescent and no open
  output tote exists.

### Quiescence

- Add one immutable `P2pLineActivitySnapshot` that explicitly records every relevant occupancy or
  active-work count. Do not collapse the data to only an `idle` boolean.
- `quiescent()` is true only when all of these are clear:
  - station-arrival queue;
  - tipper-input queue;
  - active/captured tote and active tipper discharges;
  - sorter input/output and pending sorter outfeed;
  - packs on PDC and active PDC transfers;
  - every PRL assignment/lane and active PRL-to-PCR transfer;
  - PCR lane/travelling/released group state;
  - bagger current/reserved group, reservation, pending discharge, and active discharge;
  - receiver reservation/receiving/completed-but-unallocated bag state;
  - outstanding expected bag groups/correlations;
  - open outbound tote for the line.
- A temporary `IDLE` state on the tipper, PRL, PCR, or bagger is insufficient.
- Add narrowly scoped read-only queries where existing machine APIs cannot supply these values.
  Do not expose mutable collections or move DSP lease logic into generic machines.
- Build activity snapshots on the simulation thread through a `P2pLineActivityProbe`; only detached
  snapshots cross into scheduling, inspection, or tests.

### Local arrival enforcement

- Replace `AllowAllP2pArrivalAdmissionPolicy` only in sticky-aware production composition. Keep it
  available for isolated legacy/debug rigs.
- `StickyP2pArrivalAdmissionPolicy` permits only when the physical tote has an exact committed
  assignment for that destination and its service-centre ID matches the current line owner.
- Missing assignment, destination mismatch, line mismatch, and service-centre mismatch are stable
  deferrals; they must leave the station-arrival FIFO head unchanged.
- Arrival admission never acquires, changes, or releases a lease. It only revalidates the committed
  decision after physical travel.

### Threading, reset, and compatibility

- Lease registry, assignment commit, output closure, and lease release are simulation-thread
  mutations.
- Scheduler workers receive immutable line/assignment/work snapshots and emit immutable commands.
- Reset remains full scene/runtime reconstruction. Do not add mutable reset methods.
- Do not couple OSR directly to P2P. Existing route-entry, warehouse transport, terminal arrival,
  station queue, P2P arrival consumer, and tipper-input boundaries remain mandatory.
- The user runs Gradle. After every coding step, ask the user to run the focused command, propose
  the listed commit message if green, and wait for feedback.

## Package And Vocabulary

Create lease types under:

```text
online.davisfamily.warehouse.sim.dsp.p2p.lease
```

Use these names unless an existing source type requires a minor adjustment:

- `P2pLineDefinition`
- `P2pPhysicalToteAssignment`
- `P2pLineActivitySnapshot`
- `P2pLineLeaseSnapshot`
- `P2pLineLeaseCatalogSnapshot`
- `P2pLineLeaseRegistry`
- `P2pLineActivityProbe`
- `ToteToBagP2pLineActivityProbe`
- `P2pServiceCentreWorkSnapshot`
- `P2pServiceCentreWorkSnapshotFactory`
- `TipperToteCompletedListener`
- `InboundLifecycleP2pToteCompletedListener`
- `P2pLineAllocationRequest`
- `P2pLineAllocationDecision`
- `P2pLineAllocationPolicy`
- `StickyP2pLineAllocationPolicy`
- `P2pReleaseRequirementResolver`
- `P2pReleaseAssignmentCommitter`
- `P2pLeaseReleaseController`
- `StickyP2pArrivalAdmissionPolicy`
- `DspP2pStickyLeaseRuntime`
- `DspP2pStickyLeaseRuntimeFactory`

## Step 1: Define Immutable Lease And Assignment Values

Scope:

- Add `P2pLineDefinition` pairing one `P2pLineId` with one exact P2P
  `OperationalRouteDestination`; validate station type and distinct IDs in ordered collections.
- Add `P2pPhysicalToteAssignment` with the identity fields fixed above.
- Add `P2pLineActivitySnapshot` with explicit nonnegative counts/flags and derived
  `processingDrained()` and `quiescent()` methods. `processingDrained()` excludes only the open
  outbound tote; `quiescent()` additionally requires no open outbound tote.
- Add `P2pLineLeaseSnapshot` and ordered `P2pLineLeaseCatalogSnapshot`, including optional owner,
  optional active pharmacy, activity, and immutable physical assignments.
- Validate that active pharmacy is present only with a lease owner and comes from an open outbound
  tote owned by the same service centre.
- Add focused tests for immutability, invalid combinations, deterministic ordering, lookup helpers,
  and every individual quiescence blocker.

Do not add mutable registry behavior or machine dependencies in this step.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseDomainTest
```

Proposed commit message: `Define sticky P2P lease domain`

## Step 2: Add The Simulation-Owned Lease Registry

Scope:

- Add `P2pLineLeaseRegistry` initialized with ordered, distinct line definitions.
- Support acquiring an unleased quiescent line for one normalized service-centre ID.
- Treat reacquiring the same owner as idempotent; reject a different owner.
- Commit one `P2pPhysicalToteAssignment` only when line definition, destination, service centre,
  and lease owner all match.
- Treat the exact repeated assignment as idempotent; reject any attempt to move the same physical
  tote or alter its service centre/destination.
- Release only the expected owner and only against a supplied quiescent activity snapshot with no
  open outbound tote.
- Expose detached snapshots in configured line order and assignment lookup by physical tote ID.
- Preserve assignment history after arrival for audit/idempotence; do not treat retained history as
  active line work or renderable lifecycle state.
- Test acquisition, exact idempotence, all conflict cases, release guards, ordering, and snapshot
  detachment.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistryTest
```

Proposed commit message: `Add simulation-owned P2P lease registry`

## Step 3: Produce Complete P2P Line Activity Snapshots

Scope:

- Add `P2pLineActivityProbe` and `ToteToBagP2pLineActivityProbe` as simulation-side read adapters.
- Compose one line snapshot from the exact station-arrival queue, `TipperInputQueue`, tipper flow,
  sorter/downstream flow, PDC, PRLs, PCR, bagger, `StoredBagReceiver`, expected-group state, and
  `OutboundAllocationSnapshot` for its `P2pLineId`.
- Add only the minimal read-only machine queries needed to count hidden work. Return immutable
  values/copies; do not expose internal queues or add lease concepts to tote-to-bag classes.
- Require an open outbound tote, if present, to match the line ID. Surface its service-centre and
  pharmacy ownership for lease validation and affinity.
- Prove every listed component independently prevents quiescence and that a genuinely empty line
  is quiescent.
- Prove completed-but-not-yet-allocated receiver bags and pending bag discharges prevent release.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.ToteToBagP2pLineActivityProbeTest --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.sim.totebag.assembly.*
```

Proposed commit message: `Expose complete P2P line activity snapshots`

## Step 4: Track Physical P2P Completion And Retain Leases Until Drain

Scope:

- Add a generic, optional `TipperToteCompletedListener` callback to
  `ToteTrackTipperFlowController`. Invoke it exactly once when a captured tote has emptied, tipper
  discharge/downstream occupancy has cleared, and the controller releases that tote. Existing
  constructors use a no-op listener.
- Add `InboundLifecycleP2pToteCompletedListener` as the DSP adapter. For the exact physical tote it
  advances `INBOUND_PACK_TOTE` to `ACTIVE_PRE_P2P` when necessary, then calls
  `InboundToteLifecycleController.consumeAtP2p(...)` at the current simulation time. Reject invalid
  roles/order types/states rather than fabricating completion.
- Add `P2pServiceCentreWorkSnapshot` and factory from immutable order definitions/routes, the full
  `InboundToteManifestCatalog`, and `PhysicalToteLifecycleSnapshot`.
- Group every physical manifest whose logical route requires P2P by service centre and count it as
  remaining until its lifecycle state is `CONSUMED_AT_P2P`. This deliberately includes manifests
  that are unsupplied, stored in OSR, dependency blocked, released, or in transit.
- Reject a P2P-required non-EMPTY order with no catalog manifest. Report P2P-required EMPTY orders
  separately as unsupported/unallocated diagnostics and do not count them as blockers in this slice.
- Prove changing only legacy `DspOrderStatus` cannot falsely remove physical work.
- Add `P2pLeaseReleaseController` using the work snapshot supplier, ordered activity probes,
  registry, `OutboundToteAllocator`, and current simulation time.
- If owner work remains, do nothing regardless of temporary machine idleness.
- If work is exhausted but processing is not drained, do nothing.
- If processing is drained and an owner-matching outbound tote is open, close it with
  `APPLICABLE_WORK_COMPLETE` and stop after that one mutation.
- On a later update, release the first configured eligible lease only when fully quiescent.
- Treat mismatched outbound-tote ownership as an invariant failure rather than silently closing or
  releasing it.
- Validate update arguments and snapshot supplier results consistently with existing controllers.
- Test exact once-only tipper completion, direct and pre-P2P lifecycle advancement, unsupplied and
  dependency-blocked retention, repeated manifests, legacy-status independence, processing drain,
  close-before-release, one transition per update, configured order, mismatched ownership, and
  no-op empty state.

Ask the user to run:

```powershell
  .\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.InboundLifecycleP2pToteCompletedListenerTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest --tests online.davisfamily.warehouse.sim.dsp.outbound.*
```

Proposed commit message: `Track completion and release drained P2P leases`

## Step 5: Select A Compatible Sticky P2P Line

Scope:

- Add immutable allocation request/decision/policy contracts.
- The request contains physical tote ID, service centre, ordered distinct pharmacy IDs, whether
  P2P is the first route station, immutable line catalog, and immutable per-target route admission.
- Implement `StickyP2pLineAllocationPolicy` using the fixed ranking tiers and configured line order.
- For direct P2P, skip line options whose exact route target is not currently admissible; continue
  to later compatible lines before returning blocked.
- For an earlier first station, ignore current P2P queue capacity while still requiring a compatible
  lease/acquirable quiescent line.
- Return either one exact assignment proposal plus affinity metadata or stable block reason
  `NO_COMPATIBLE_P2P_LINE`.
- Keep the policy pure; it must not mutate the registry or reserve capacity.
- Test five-line deterministic selection, same-owner reuse, active-pharmacy preference, full direct
  target fallback, multi-station capacity independence, cross-centre exclusion, and no compatible
  line.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pLineAllocationPolicyTest
```

Proposed commit message: `Select compatible sticky P2P lines`

## Step 6: Integrate Assignment And Affinity Into Operational Scheduling

Scope:

- Extend the immutable operational snapshot with the ordered lease catalog and line-target admission
  values required by Step 5. Build them on the simulation thread in
  `DspOperationalReleaseSnapshotFactory`.
- Extend `OperationalReleaseSelection` with an optional proposed P2P assignment and explicit
  active-pharmacy-affinity flag. Non-P2P candidates must have neither.
- Evaluate dependency readiness and first-route admission first, then P2P line allocation for every
  `requiresP2p()` candidate, then candidate ranking.
- Add stable operational block category/reason for no compatible P2P line.
- Compose affinity ahead of the existing pharmacy-group/source-sequence comparator without changing
  service-centre priority selection or non-P2P ordering.
- Extend `ReleasePhysicalToteFromOsrCommand` with optional P2P assignment while preserving a
  compatibility constructor for existing non-P2P/legacy fixtures.
- Require the emitted command assignment to match the selected physical tote/service centre and
  exact line decision.
- Test direct and multi-station candidates, non-P2P candidates, affinity, blocked lines, full-target
  fallback, stable ordering, and worker snapshot detachment.

Do not acquire the lease on the scheduler worker.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSchedulerTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactoryTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.PharmacyGroupedSourceSequenceRankingPolicyTest
```

Proposed commit message: `Integrate sticky line selection into operational release`

## Step 7: Commit And Carry The Pinned Assignment

Scope:

- Extend `OsrProcessingReleaseRequest` with the optional P2P assignment and a compatibility
  constructor yielding empty assignment.
- Extend `OsrOutboundRouteLaunchRequest` and `RoutedPhysicalTote` with read-only assignment access;
  preserve it unchanged through route-entry queue, launch, hydration, transport, and station arrival.
- Add `P2pReleaseRequirementResolver` returning the immutable current `RouteRequirements` for an
  `OrderSheetKey`. The production adapter resolves from the simulation-thread order snapshot;
  missing or ambiguous sheets are rejected.
- Add `P2pReleaseAssignmentCommitter` used by `OsrProcessingReleaseCommandHandler`. Supply a no-op
  compatibility implementation through the existing constructor and require the strict committer
  in operational sticky-lease runtime composition.
- Before invoking the first route target, the strict committer revalidates command/manifest
  identity, `requiresP2p()` from the resolver, line definition, destination, owner compatibility,
  and direct-P2P target equality. A non-P2P route must have no assignment; a P2P-required route must
  have exactly one.
- Invoke the existing target. If it defers/rejects, leave registry, OSR inventory, lifecycle, and
  target ownership exactly as existing behavior requires.
- After an applied target result, commit the lease/assignment before OSR departure and lifecycle
  activation. Under the serialized simulation-thread contract, commit must be guaranteed after
  prevalidation; an unexpected failure is an invariant error.
- Reject a missing assignment for production P2P-required releases and an unexpected assignment for
  non-P2P releases. Keep explicitly configured compatibility behavior only for legacy tests/rigs.
- Test accepted commit, exact retry/idempotence, stale command, target deferral, target rejection,
  direct/multi-station destination rules, assignment propagation, and no partial lease mutation.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseCommitTest
```

Proposed commit message: `Commit and propagate pinned P2P assignments`

## Step 8: Enforce Sticky Ownership At Physical P2P Arrival

Scope:

- Add `StickyP2pArrivalAdmissionPolicy` over an immutable/current simulation-thread lease snapshot
  supplier or registry read boundary.
- Require an exact assignment for the arriving physical tote, exact destination and line match,
  matching request/assignment service-centre IDs, and matching current lease owner.
- Return stable deferral reasons for missing assignment, destination mismatch, service-centre
  mismatch, and inactive/mismatched lease.
- Do not mutate leases or assignments from arrival admission.
- Wire sticky admission into sticky-aware P2P consumer bindings while leaving allow-all available for
  isolated arrival-consumer and machine rigs.
- Prove a blocked FIFO head remains in station-arrival ownership and cannot enter tipper input;
  restoring the exact lease allows deterministic retry.
- Prove two independently leased lines accept only their own service centres.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.StickyP2pArrivalAdmissionPolicyTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalConsumerControllerTest --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerScenarioTest
```

Proposed commit message: `Enforce sticky leases at P2P arrival`

## Step 9: Compose And Prove The Sticky Lease Runtime

Scope:

- Add `DspP2pStickyLeaseRuntimeFactory` and runtime for ordered line definitions, registry, activity
  probes, release controller, scheduler snapshot supplier, release commit participant, and sticky
  arrival policies.
- Validate all five configured line IDs/destinations/probes/arrival bindings before registering any
  controller. Reject duplicate IDs, duplicate destinations, mismatched line mappings, and missing
  components.
- Register the lease release controller after bag allocation and activity-producing controllers so
  it observes current simulation state.
- Expose immutable ordered line, owner, active-pharmacy, activity, assignment, closure, and last
  transition details for inspection. Do not expose machine objects or mutable registries.
- Add a deterministic end-to-end scenario with at least two service centres, two pharmacies, direct
  P2P and Third-Party-before-P2P routes, queue backpressure, owner work retained while blocked, output
  tote closure, quiescent lease release, and later line reuse.
- Prove no tote teleports from OSR to P2P, no physical assignment changes after release, no outbound
  tote mixes service centres/pharmacies, and one blocked line does not stop an independent line.
- Add the lease state to `dsp-warehouse-transport` inspection without constructing five full P2P
  assemblies. Use deterministic non-rendering activity probes/placeholders where the scene lacks a
  real machine line.
- Preserve `ALT+R` full reconstruction and current transport/tote-to-bag visuals.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeTest --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLineLeaseScenarioTest --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Proposed commit message: `Compose sticky P2P lease runtime`

## Step 10: Regression, Visual Check, And Branch Closure

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.p2p.lease.* --tests online.davisfamily.warehouse.sim.dsp.p2p.arrival.* --tests online.davisfamily.warehouse.sim.dsp.scheduler.operational.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.route.* --tests online.davisfamily.warehouse.sim.dsp.osr.release.launch.* --tests online.davisfamily.warehouse.sim.dsp.transport.* --tests online.davisfamily.warehouse.sim.dsp.transport.routing.* --tests online.davisfamily.warehouse.sim.dsp.outbound.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.sim.totebag.assembly.* --tests online.davisfamily.warehouse.testing.DspWarehouseTransportDebugRigTest
```

Then ask the user to run:

```powershell
.\gradlew test
```

Visual checks:

```powershell
.\gradlew run --args="--scene=dsp-warehouse-transport"
.\gradlew run --args="--scene=tote-to-bag"
```

Verify:

- inspection shows each configured P2P line's owner, active pharmacy, quiescence blockers, and
  pinned physical tote IDs;
- a second service centre cannot enter a leased line while any work/output remains;
- a compatible service centre can continue feeding a leased line;
- direct P2P backpressure remains visible at the correct queue boundary;
- a multi-station tote retains its future P2P assignment without bypassing warehouse transport;
- close-before-release and later line reuse are visible in deterministic inspection state;
- tote-to-bag processing, pack visuals, bag allocation, and queue ownership remain unchanged;
- `ALT+R` reconstructs both scenes deterministically.

Architecture verification:

- OSR remains coupled only to the selected first route-entry target;
- P2P assignment metadata is distinct from the first route target;
- scheduler workers consume immutable snapshots and never mutate leases;
- command application is the only lease-acquisition/assignment commit boundary;
- station arrival only revalidates ownership and never selects or moves a line;
- quiescence includes all machine, queue, bag, expected-group, receiver, and output-tote state;
- no active work is pre-empted and no line is reclaimed from a nonterminal owner;
- no deadline-aware line-count policy, render thread, second route engine, or mutable reset API was
  introduced.

Before branch closure:

- [x] mark this plan implementation complete and verified;
- [x] record the final assignment, affinity, commit, arrival, quiescence, close, and release contracts;
- [x] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [x] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [x] confirm focused/full tests and visual/reset checks are green;
- [x] create the decision-complete plan for `feature/dsp-deadline-aware-elastic-line-allocation`
  only after this branch is merged.

Proposed commit message: `Complete sticky P2P line lease feature`

## Expected Final Contract

- Every physical tote requiring P2P processing is pinned once to one exact P2P line before leaving
  OSR, independently of its first route-entry station.
- One line has zero or one service-centre owner. It accepts only exact assigned totes for that owner.
- Compatible owner work reuses the line; active outbound pharmacy provides deterministic affinity.
- Another service centre cannot acquire the line until owner work is complete, all physical work has
  drained, and the prior output tote has closed.
- Full P2P quiescence is observable as detached immutable data rather than inferred from temporary
  machine idleness.
- Existing OSR release, warehouse transport, station-arrival, tipper-input, machine-state, bagging,
  and outbound allocation boundaries remain intact.
- The next branch may add deadline-aware elastic allocation by replacing the allocation policy; it
  must not weaken assignment pinning, arrival enforcement, close-before-release, or quiescence.
