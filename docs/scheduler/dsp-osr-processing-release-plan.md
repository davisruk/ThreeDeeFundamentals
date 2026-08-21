# DSP OSR Processing Release Plan

Branch: `feature/dsp-osr-processing-release`

Status: implementation complete and verified; pending merge to `master`.

## Purpose

Implement the simulation-thread boundary at which one physical inbound tote is accepted by a downstream DSP route, leaves physical OSR inventory, and receives its active inbound lifecycle assignment.

This branch must:

- expose immutable per-physical-tote release candidates derived from current OSR inventory and lifecycle state;
- preserve `PhysicalToteId` and `OrderSheetKey` as distinct identities;
- retain every stored physical manifest when several manifests belong to one logical sheet;
- block later physical manifests for a sheet while another physical assignment for that sheet is active;
- introduce a typed physical OSR release command without weakening or overloading the existing order-centric debug command;
- revalidate worker-produced commands against live simulation-thread state;
- obtain downstream acceptance before committing inventory departure;
- activate the accepted inbound physical tote at the same authoritative simulation time as its departure;
- leave rejected, deferred, stale, or failed releases without local inventory or lifecycle mutation;
- free OSR capacity so the completed rate-limited supply feature can continue replenishment.

This branch does not choose which candidate should be released, implement pharmacy-grouped ranking, replace the current `DspReleaseScheduler`, add physical candidates to `WarehouseSchedulerSnapshot`, implement sticky P2P line leases, allocate EMPTY totes at AV02, add deadline-aware line allocation, create renderables, or run a complete production day.

## Required Reading

Read these documents before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-operational-scheduling-requirements.md`
3. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
4. `docs/scheduler/dsp-osr-physical-inventory-plan.md`
5. `docs/scheduler/dsp-inbound-tote-lifecycle-plan.md`
6. `docs/scheduler/dsp-rate-limited-service-centre-supply-plan.md`
7. `docs/scheduler/dsp-scheduler-implementation-plan.md`

Inspect these classes before each affected step:

- `InboundToteManifest`
- `InboundToteManifestCatalog`
- `InboundToteLifecycleController`
- `PhysicalToteLifecycleLedger`
- `PhysicalToteLifecycleSnapshot`
- `PhysicalToteAssignment`
- `PhysicalToteLifecycleState`
- `OsrPhysicalInventory`
- `OsrInventorySnapshot`
- `DspServiceCentreSupplyCoordinator`
- `DspSupplySnapshot`
- `DspOperationalClockSnapshot`
- `SchedulerCommand`
- `SchedulerCommandHandler`
- `SchedulerCommandApplicationResult`
- `ReleaseOrderCommand`
- `DspReleaseScheduler`
- `WarehouseSchedulerSnapshot`
- `SelectedStationTargets`

## Fixed Decisions

Do not revisit these decisions during implementation:

- Service-centre supply authorization, admission into OSR, processing release from OSR, lifecycle activation, and downstream machine processing are separate transitions.
- A processing-release candidate is one currently stored `InboundToteManifest`, identified by `PhysicalToteId`.
- `OrderSheetKey` remains logical identity. It is metadata on the physical candidate and command, not the release key.
- Several stored candidates may share one `OrderSheetKey`. Do not collapse, deduplicate, or summarize them into one candidate.
- The lifecycle invariant remains one active physical assignment per logical sheet. If one manifest for a sheet is active, every other stored manifest for that sheet remains visible but is blocked by that assignment.
- Inventory membership is the authority for whether a physical inbound tote is currently releasable from OSR. Loaded-data presence, supply authorization, or `DspOrderStatus.WAITING` is not enough.
- A stored OSR manifest must have a registered lifecycle tote in `INBOUND_PACK_TOTE` state and must not already have an active assignment on its own physical ID. Treat violations as invariant failures, not ordinary scheduler blocks.
- `EMPTY` has no inbound manifest and cannot be an OSR processing-release candidate. EMPTY remains assigned at AV02 in a later operational-release feature.
- Add `ReleasePhysicalToteFromOsrCommand`; do not add optional physical identity to `ReleaseOrderCommand` and do not make `orderId` masquerade as a tote ID.
- Keep `ReleaseOrderCommand`, `ReleaseDecision`, `DspReleaseScheduler`, `WarehouseSchedulerSnapshot`, `DspSchedulerRuntimeState`, and existing visual debug injectors behaviorally unchanged in this branch.
- The existing order-centric debug path remains a compatibility rig. The following dependency-ready operational-release branch will make the scheduler emit the physical command and consume the new candidate snapshot.
- A command carries enough immutable identity to detect stale or mismatched work: physical tote ID, logical sheet key, service-centre ID, and downstream release-target ID.
- The command type itself implies `StartLocation.OSR`; do not add a redundant start-location field.
- Target IDs are opaque, trimmed, nonblank strings. This branch does not infer a first machine from `RouteRequirements` or `SelectedStationTargets`.
- A downstream target returns `SchedulerCommandApplicationResult`: applied, deferred, or rejected. Only an applied target result permits local departure and activation commits.
- Target deferral is normal capacity backpressure and leaves the command retryable. Target rejection is a terminal application failure for that command and also leaves local state unchanged.
- An unknown target, identity mismatch, unknown physical tote, already-departed tote, or lifecycle invariant mismatch is rejected before invoking downstream code.
- An active assignment for the same logical sheet on a different physical tote is deferred because the command may become valid after that assignment terminates.
- A repeated command for a successfully released physical tote is rejected as stale and must not invoke the target again.
- Obtain and validate one authoritative `DspOperationalClockSnapshot` per command application. Use `elapsedSimulationTime()` as both inventory-release and lifecycle-activation time; do not use frame delta or wall-clock time.
- Validate every predictable inventory, identity, lifecycle, target-resolution, and timing precondition before invoking the downstream target.
- The downstream target must not mutate `OsrPhysicalInventory` or `InboundToteLifecycleController`. Those mutations belong exclusively to the command handler.
- If downstream target application defers, rejects, or throws, do not call `recordDeparture(...)` or `activate(...)`.
- After downstream application succeeds, call `OsrPhysicalInventory.recordDeparture(physicalToteId)` and then `InboundToteLifecycleController.activate(physicalToteId, releaseTime)` in that order.
- The post-acceptance commits rely on simulation-thread ownership and complete prevalidation. Do not add rollback, locks, transactions, or concurrent collections.
- Snapshot construction, command construction, and scheduler-worker evaluation are read-only. Only the command handler mutates inventory or lifecycle state.
- Preserve deterministic inventory order in candidate snapshots and immutable collection components.
- Reset remains reconstruction. Do not add `reset()` methods that reverse inventory departure or lifecycle history.
- The user runs Gradle. After each implementation step, ask for the stated focused command and wait for feedback.

## Package And Vocabulary

Create physical processing-release types under:

```text
online.davisfamily.warehouse.sim.dsp.osr.release
```

Use these names:

- `OsrProcessingReleaseAvailability`: `AVAILABLE` or `BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT`.
- `OsrProcessingReleaseCandidate`: immutable physical candidate identity and lifecycle availability.
- `OsrProcessingReleaseSnapshot`: immutable ordered candidate view.
- `OsrProcessingReleaseSnapshotFactory`: joins immutable inventory and lifecycle snapshots.
- `ReleasePhysicalToteFromOsrCommand`: typed scheduler command for one physical tote and one target.
- `OsrProcessingReleaseRequest`: exact live manifest and release time passed to a target.
- `OsrProcessingReleaseTarget`: downstream acceptance boundary.
- `OsrProcessingReleaseTargetRegistry`: deterministic target-ID lookup.
- `OsrProcessingReleaseCommandHandler`: simulation-thread command validator and commit coordinator.

Do not create another physical manifest wrapper, another inventory, or a parallel lifecycle ledger.

## Step 1: Define Immutable Physical Release Candidate State

Create:

```java
public enum OsrProcessingReleaseAvailability {
    AVAILABLE,
    BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT
}

public record OsrProcessingReleaseCandidate(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        String serviceCentreId,
        long sourceSequenceNumber,
        OsrProcessingReleaseAvailability availability,
        Optional<PhysicalToteId> blockingPhysicalToteId) {}

public record OsrProcessingReleaseSnapshot(
        List<OsrProcessingReleaseCandidate> candidates) {}
```

Validation rules:

- reject null typed identities, order type, availability, optional, or candidate collection;
- trim and require a nonblank service-centre ID;
- require nonnegative source sequence;
- `AVAILABLE` requires an empty `blockingPhysicalToteId`;
- `BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT` requires a present blocker different from the candidate physical ID;
- reject null candidate elements and duplicate candidate physical IDs;
- preserve candidate order through `List.copyOf(...)`;
- expose `availableCandidates()` preserving order;
- expose `findByPhysicalToteId(PhysicalToteId)` returning `Optional`;
- do not expose mutable maps or lists.

Create `OsrProcessingReleaseSnapshotTest` with:

- `shouldPreserveDistinctPhysicalCandidatesForOneLogicalSheet()`
- `shouldExposeAvailableCandidatesInInventoryOrder()`
- `shouldRequireConsistentBlockingIdentity()`
- `shouldRejectDuplicatePhysicalCandidateIdentity()`
- `shouldReturnDefensiveImmutableCandidateCollections()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotTest
```

## Step 2: Derive Candidates From Inventory And Lifecycle Snapshots

Create:

```java
public final class OsrProcessingReleaseSnapshotFactory {
    public OsrProcessingReleaseSnapshot create(
            OsrInventorySnapshot inventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot)
}
```

Factory algorithm, in this exact order:

1. Reject null snapshots.
2. Iterate `inventorySnapshot.storedTotes()` in its existing order. Do not sort.
3. Require a lifecycle record for each stored physical tote.
4. Require that record to be `INBOUND_PACK_TOTE` with role `INBOUND_PACK`.
5. Require `lifecycleSnapshot.activeAssignmentsFor(candidatePhysicalId)` to be empty. A currently stored tote cannot already be physically active.
6. Read `lifecycleSnapshot.activeAssignmentFor(manifest.orderSheetKey())`.
7. If no sheet assignment is active, publish `AVAILABLE` with no blocker.
8. If an assignment is active on another physical tote, publish `BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT` with that physical tote as blocker.
9. If an assignment reports the candidate physical ID despite step 5, fail as an inconsistent lifecycle snapshot.
10. Copy physical and logical identity directly from the manifest.

Do not include departed manifests, upstream manifests not yet stored, or EMPTY sheets. Do not use `DspSupplySnapshot` as a second membership authority; current inventory membership already proves physical admission.

Create `OsrProcessingReleaseSnapshotFactoryTest` with:

- `shouldExposeOnlyCurrentlyStoredPhysicalManifests()`
- `shouldPreserveInventoryOrderAcrossServiceCentresAndOrderTypes()`
- `shouldBlockLaterManifestWhileSameSheetAssignmentIsActive()`
- `shouldMakeLaterManifestAvailableAfterEarlierAssignmentTerminates()`
- `shouldRejectStoredManifestMissingFromLifecycleLedger()`
- `shouldRejectStoredManifestWithActiveOrTerminalPhysicalState()`
- `shouldNeverCreateCandidateForEmptyAuthorization()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactoryTest
```

## Step 3: Add Non-Mutating Lifecycle Activation Validation

Add to `InboundToteLifecycleController`:

```java
public void validateActivation(
        PhysicalToteId toteId,
        Duration activationTime)
```

Refactor `activate(...)` to call this method before `ledger.assign(...)` so validation logic has one owner.

`validateActivation(...)` must:

- reject null/unknown physical IDs and null, negative times using the same exception semantics as `activate(...)`;
- require a catalog manifest;
- require lifecycle state `INBOUND_PACK_TOTE`;
- require no active assignment for the manifest's `OrderSheetKey`;
- require no active assignment for the physical tote;
- perform no mutation to tote state, assignments, sequence counters, or history.

The existing ledger remains the final invariant authority when `activate(...)` commits. Do not expose the ledger from the controller.

Extend `InboundToteLifecycleControllerTest` with:

- `shouldValidateActivationWithoutCreatingAssignment()`
- `shouldShareValidationBetweenValidateAndActivate()`
- `shouldRejectValidationWhileAnotherManifestForSheetIsActive()`
- `shouldLeaveLifecycleSnapshotUnchangedAfterRejectedValidation()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleControllerTest
```

## Step 4: Define The Typed Command And Downstream Target Boundary

Create:

```java
public record ReleasePhysicalToteFromOsrCommand(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        String serviceCentreId,
        String releaseTargetId) implements SchedulerCommand {}

public record OsrProcessingReleaseRequest(
        InboundToteManifest manifest,
        Duration releaseTime) {}

public interface OsrProcessingReleaseTarget {
    String targetId();

    SchedulerCommandApplicationResult accept(
            OsrProcessingReleaseRequest request);
}
```

Create `OsrProcessingReleaseTargetRegistry` with a constructor accepting `List<OsrProcessingReleaseTarget>` and:

```java
public Optional<OsrProcessingReleaseTarget> find(String targetId)
```

Rules:

- command IDs and target IDs are trimmed and nonblank;
- request manifest and release time are non-null, and release time is nonnegative;
- target registry rejects null targets, blank normalized IDs, and duplicate normalized IDs before publishing itself;
- registry preserves configured target order internally and returns immutable behavior;
- `find(...)` validates and normalizes its argument;
- target code may mutate its own downstream queue only when returning `appliedResult()`;
- target code must not mutate OSR inventory or lifecycle state;
- do not add `PhysicalToteId` fields to `ReleaseOrderCommand`;
- do not change `SchedulerCommandApplicationResult` in this step.

Create:

- `ReleasePhysicalToteFromOsrCommandTest`
- `OsrProcessingReleaseTargetRegistryTest`

Required methods:

- `shouldRetainDistinctPhysicalAndLogicalCommandIdentity()`
- `shouldRejectInvalidPhysicalReleaseCommandFields()`
- `shouldRetainManifestAndSimulationTimeInReleaseRequest()`
- `shouldResolveTargetsByNormalizedUniqueId()`
- `shouldRejectDuplicateOrInvalidTargets()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommandTest --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistryTest
```

## Step 5: Implement Simulation-Thread Command Revalidation And Commit

Create `OsrProcessingReleaseCommandHandler implements SchedulerCommandHandler` with:

```java
public OsrProcessingReleaseCommandHandler(
        OsrPhysicalInventory inventory,
        InboundToteLifecycleController lifecycleController,
        Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
        OsrProcessingReleaseTargetRegistry targetRegistry)
```

Constructor rules:

- reject null dependencies;
- retain live mutable collaborators, not snapshots;
- add no synchronization or worker thread.

`apply(SchedulerCommand)` algorithm, in this exact order:

1. Reject a null command argument with `IllegalArgumentException`.
2. Return `rejectedResult("Unsupported scheduler command: ...")` for command types other than `ReleasePhysicalToteFromOsrCommand`. Do not cast blindly.
3. Read exactly one clock snapshot. Throw `IllegalStateException` if the supplier returns null.
4. Read one fresh inventory snapshot.
5. Find the command's physical ID in current stored inventory.
6. If it is in departure history, return a rejected stale result. If it is unknown to both stored and departed inventory, return a rejected unknown result.
7. Compare the live manifest's `OrderSheetKey` and service-centre ID with the command. Reject any mismatch before resolving or invoking a target.
8. Read one lifecycle snapshot and require the physical tote to be registered as `INBOUND_PACK_TOTE` with role `INBOUND_PACK`.
9. If the same physical tote already has an active assignment, reject the inconsistent/stale command.
10. If another physical tote has an active assignment for the command's logical sheet, return a deferred result naming the blocking physical ID.
11. Call `lifecycleController.validateActivation(physicalToteId, elapsedSimulationTime)` to prove the later commit can succeed.
12. Resolve the command's target ID. Reject an unknown target without invoking any target.
13. Create one `OsrProcessingReleaseRequest` from the live manifest and clock elapsed time.
14. Invoke the target exactly once.
15. Throw `IllegalStateException` if the target returns null.
16. If the target result is deferred or rejected, return it unchanged and perform no local mutation.
17. If the target throws, allow the exception to propagate and perform no local inventory/lifecycle mutation.
18. If the target applies, call `inventory.recordDeparture(physicalToteId)`.
19. Verify the returned departed manifest equals the previously validated live manifest; throw if the inventory violates that invariant.
20. Call `lifecycleController.activate(physicalToteId, elapsedSimulationTime)`.
21. Return `SchedulerCommandApplicationResult.appliedResult()`.

Predictable failures in steps 5 through 12 return rejected or deferred results as stated; they must not escape as inventory/lifecycle exceptions. If `validateActivation(...)` rejects after the explicit snapshot checks, translate it to a rejected result and do not invoke the target. Null clock snapshots, null target results, target exceptions, or impossible post-acceptance invariant failures remain exceptions because they indicate broken runtime collaborators rather than an ordinary stale command.

The target must never be called when validation fails. The handler must not mark a logical scheduler order released; one logical sheet may still have other stored physical manifests.

Create `OsrProcessingReleaseCommandHandlerTest` with focused fake targets and these methods:

- `shouldAcceptDownstreamBeforeCommittingDepartureAndActivation()`
- `shouldUseOneAuthoritativeClockSnapshotForReleaseTime()`
- `shouldDeferWithoutMutationWhenTargetHasNoCapacity()`
- `shouldRejectWithoutMutationWhenTargetRejects()`
- `shouldLeaveLocalStateUnchangedWhenTargetThrows()`
- `shouldRejectUnknownTargetBeforeInvokingDownstream()`
- `shouldRejectCommandIdentityMismatchBeforeInvokingDownstream()`
- `shouldDeferWhileAnotherPhysicalToteOwnsTheLogicalSheet()`
- `shouldRejectRepeatedStaleCommandWithoutInvokingTargetAgain()`
- `shouldRejectUnsupportedSchedulerCommandWithoutMutation()`
- `shouldRejectInvalidLifecycleOrInventoryStateBeforeDownstreamMutation()`

Tests must assert inventory occupancy/history, lifecycle assignments, target call count, received manifest, and exact release time after every applied and non-applied path.

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandlerTest
```

## Step 6: Prove Repeated-Sheet Sequencing And Supply Resumption

Create `DspOsrProcessingReleaseScenarioTest` using real:

- `InboundToteManifestCatalog`;
- `PhysicalToteLifecycleLedger` and `InboundToteLifecycleController`;
- `OsrPhysicalInventory`;
- `DspServiceCentreSupplyCoordinator` where rate-limited replenishment is needed;
- `DspOperationalClock` snapshots;
- `OsrProcessingReleaseSnapshotFactory`;
- `OsrProcessingReleaseCommandHandler`;
- a deterministic in-memory accepting target.

Prove these scenarios:

1. **Physical release transaction:** one stored manifest is initially available; accepted release records exactly one inventory departure and one active `INBOUND_PACK` assignment at the command time.
2. **Repeated logical sheet:** two stored manifests share one `OrderSheetKey`; releasing the first makes the second remain stored and become `BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT`; after the first assignment terminates through the correct existing lifecycle operation, rebuilding the snapshot makes the second available and it can release.
3. **No logical collapse:** releasing the first manifest does not remove or mark the second manifest departed and does not use `DspSchedulerRuntimeState.markReleased(orderId)`.
4. **Supply resumption:** an accepted processing release lowers occupancy; the rate-limited supply coordinator observes the freed capacity on a later advance and admits the correct due/blocked upstream physical manifest without exceeding capacity or reordering it.
5. **Reset reconstruction:** rebuilding inventory, lifecycle controller, supply coordinator, target registry, and command handler from the same loaded inputs restores startup inventory and has no departed totes or active assignments.

Use small deterministic capacities and explicit elapsed times. Do not use sleeps, update-count timing, wall-clock APIs, or visual objects.

Required methods:

- `shouldReleaseStoredPhysicalToteIntoLifecycleAfterDownstreamAcceptance()`
- `shouldSequenceSeveralPhysicalTotesForOneLogicalSheet()`
- `shouldFreeCapacityForRateLimitedServiceCentreSupply()`
- `shouldReconstructExactStartupReleaseState()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.DspOsrProcessingReleaseScenarioTest
```

## Step 7: Protect Existing Scheduler And Visual Compatibility

Do not migrate the legacy debug scheduler in this branch. Add characterization assertions only where required to prove:

- `ReleaseOrderCommand` remains order-centric and unchanged;
- `DspReleaseScheduler` still emits the legacy command for existing debug snapshots;
- `WarehouseSchedulerSnapshot` retains its existing constructor and components;
- `DspSchedulerRuntimeState.markReleased(...)` remains used only by the legacy debug path;
- threaded and synchronous evaluation sources still exchange immutable `WarehouseSchedulerSnapshot` values;
- no physical inventory or lifecycle mutation occurs on a scheduler worker;
- the new command handler is invoked only on the simulation thread by future integration code.

Create `OsrProcessingReleaseCompatibilityTest` under the new release package. Prefer compiling against the existing public constructors over duplicating all existing scheduler tests.

Required methods:

- `shouldKeepLegacyOrderReleaseCommandSeparateFromPhysicalOsrCommand()`
- `shouldLeaveWarehouseSchedulerSnapshotContractUnchanged()`
- `shouldKeepPhysicalCommitOutsideSchedulerEvaluation()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCompatibilityTest --tests online.davisfamily.warehouse.sim.dsp.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.runtime.* --tests online.davisfamily.warehouse.testing.scheduler.*
```

## Step 8: Regression, Visual Verification, And Branch Closure

Run focused branch coverage first:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.osr.release.* --tests online.davisfamily.warehouse.sim.dsp.osr.* --tests online.davisfamily.warehouse.sim.dsp.supply.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.* --tests online.davisfamily.warehouse.sim.dsp.time.*
```

Then ask the user to run the complete Gradle suite.

Visual smoke tests:

- run the Adapting debug scene;
- run the Third Party debug scene;
- run the integrated tote-to-bag/P2P scene;
- verify existing release, tote, pack, and bag behavior is unchanged because the new physical handler is not yet installed in those legacy rigs;
- verify `ALT+R` still reconstructs each checked scene;
- no new OSR renderable or physical-release overlay is expected in this branch.

Before branch closure:

- [x] update this plan status to implementation complete and verified;
- [x] update `docs/scheduler/dsp-scheduler-implementation-plan.md`;
- [x] update `docs/codex-context.md` and `docs/codex-instructions.md`;
- [x] record final type names and transaction ordering for the dependency-ready operational-release plan;
- [x] identify `feature/dsp-dependency-ready-operational-release` as the likely next branch, subject to reassessment against the completed physical boundary.

## Preserved Contracts For Follow-On Work

- `OsrProcessingReleaseSnapshot` is the immutable physical candidate input that a later scheduler snapshot may compose; do not infer OSR membership from logical order state.
- `ReleasePhysicalToteFromOsrCommand` is the typed physical command. `ReleaseOrderCommand` remains a legacy debug command until a deliberate migration removes it.
- The later scheduler must emit at most one physical command per selected manifest and include exact physical, logical, service-centre, and target identity.
- Candidate ranking must operate on physical candidates while using logical dependency and pharmacy metadata. It must not collapse several manifests for one sheet.
- A candidate blocked by another active assignment for its sheet remains observable and may become available after lifecycle termination.
- Command application always revalidates live inventory, lifecycle, identity, target, and clock state because scheduler-worker snapshots may be stale.
- Downstream acceptance occurs before OSR departure and lifecycle activation commits.
- `recordDeparture(...)` followed by `activate(...)` is the simulation-thread commit sequence after successful target acceptance.
- Target deferral, target rejection, target exception, stale command, and validation failure leave inventory and lifecycle unchanged.
- Physical departure frees OSR capacity for rate-limited upstream supply; supply and processing release remain independent controllers sharing inventory as the authority.
- EMPTY bypasses this physical inbound release boundary and remains a later AV02 concern.
- Reset reconstructs runtime state rather than reversing append-only history.

## Completion Criteria

- Every currently stored inbound physical manifest appears once in deterministic candidate order.
- Departed and upstream physical manifests do not appear as current release candidates.
- Several manifests for one logical sheet remain distinct.
- One active sheet assignment blocks later physical candidates for that sheet without hiding them.
- The typed command carries physical tote, logical sheet, service centre, and target identity.
- Live command application rejects stale or mismatched commands before downstream invocation.
- Downstream deferral, rejection, or failure performs no local inventory or lifecycle mutation.
- Successful downstream acceptance commits one inventory departure and one lifecycle activation at the same simulation time.
- Repeating an applied command does not invoke downstream code or mutate state again.
- Released capacity is visible to the existing rate-limited supply coordinator.
- Existing order-centric debug scheduler behavior, worker boundary, visual scenes, and reset behavior remain unchanged.
- Focused tests, complete tests, and visual/reset smoke checks are green.

## Follow-On Branch

After this branch is green and merged, reassess and create a detailed plan for:

```text
feature/dsp-dependency-ready-operational-release
```

That branch should compose physical OSR candidates into scheduler snapshots, remove the hardcoded ASSOCIATED/EMPTY-before-FULL_PACK comparator, implement pharmacy-grouped deterministic ranking, make the scheduler emit `ReleasePhysicalToteFromOsrCommand`, and route applied commands through the handler built here. Sticky P2P service-centre leases and deadline-aware elastic allocation remain later branches.
