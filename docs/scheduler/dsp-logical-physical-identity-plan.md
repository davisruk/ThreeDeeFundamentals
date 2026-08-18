# DSP Logical And Physical Identity Plan

Branch: `feature/dsp-logical-physical-identity`

Status: ready for implementation.

## Purpose

Establish the domain foundation that distinguishes logical DSP order sheets from physical totes.

This branch introduces:

- a typed logical `OrderSheetKey`;
- a typed `PhysicalToteId`;
- explicit physical tote roles and lifecycle states;
- validated physical tote lifecycle transitions;
- logical-sheet-to-physical-tote assignment records;
- a simulation-thread-owned assignment ledger;
- immutable lifecycle snapshots suitable for later scheduler inspection.

This branch is domain-only. It must not migrate 12N loading, station visits, tote load plans, scheduler commands, debug rigs, renderables, P2P handoffs, or bagging behavior. Those integrations are deliberately split into later feature branches.

## Required Reading

Read these before changing code:

1. `docs/codex-context.md`
2. `docs/scheduler/dsp-logical-physical-lifecycle-requirements.md`
3. `docs/scheduler/dsp-operational-scheduling-requirements.md`
4. `docs/scheduler/dsp-scheduler-implementation-plan.md`
5. `docs/scheduler/dsp-scheduler-domain-plan.md`

The lifecycle requirements control whenever older scheduler documents use `notionalToteId` as though it were a physical tote identifier.

## Fixed Decisions

Do not revisit these during implementation:

- `OrderSheetKey` is `orderId + sheetNumber`.
- `PhysicalToteId` represents a physical `transportContainer` or a later simulator-allocated physical tote ID.
- Logical and physical IDs are different types. Do not add convenience factories that convert an order ID into a physical tote ID.
- Keep `NotionalToteOrder` for compatibility in this branch. Add a typed logical-sheet accessor, but do not rename the record or remove `notionalToteId` yet.
- Do not populate physical tote identity in `TwelveNOrderMapper`; that is the next inbound-lifecycle branch.
- Remove the unused `ToteType` enum. Its values `ASSOCIATED`, `FULL_PACK`, and `MANUAL_FLOW` describe message/order categories rather than physical tote roles.
- Replace it with an explicitly physical role enum rather than reusing `OrderType`.
- Use `java.time.Duration` for monotonic simulation-relative assignment times. The later operational-clock branch may map these durations to business date/time.
- The mutable lifecycle ledger is owned by the simulation thread. Worker threads receive immutable snapshots only.
- History is retained in deterministic sequence order. Ending an assignment replaces its immutable active record with its immutable terminated form in the same sequence position; records are never deleted or reordered.
- One logical sheet may have at most one active physical tote assignment.
- One inbound/pre-P2P physical tote may have at most one active logical-sheet assignment.
- One outbound physical tote may have active assignments from multiple logical sheets.
- This branch does not allocate generated output sheets. It only enforces the active-assignment rule that the later output-splitting branch will use.

## Package And Naming Decisions

Use these exact production packages:

- identity value objects remain in `online.davisfamily.warehouse.sim.dsp.model`;
- physical lifecycle types live in `online.davisfamily.warehouse.sim.dsp.lifecycle`.

Use these exact names unless a compile-time conflict is discovered:

- `OrderSheetKey`
- `PhysicalToteId`
- `PhysicalToteRole`
- `PhysicalToteLifecycleState`
- `PhysicalToteRecord`
- `PhysicalToteAssignmentStage`
- `PhysicalToteAssignmentEndReason`
- `PhysicalToteAssignment`
- `PhysicalToteLifecycleLedger`
- `PhysicalToteLifecycleSnapshot`

Do not name a physical tote `NotionalTote`, `OrderTote`, `AssociatedTote`, or `FullPackTote`.

## Explicit Non-Goals

- changing `TwelveNOrderMapper` or retaining `transportContainer` in mapped orders;
- changing `DspOrderItem` patient/prescription fields;
- replacing `notionalToteId` at station or tote-load-plan call sites;
- changing `WarehouseSchedulerSnapshot`, `ReleaseOrderCommand`, or `ReleaseDecision`;
- creating inbound tote manifests;
- creating `BagKey`, bag plans, completed bags, or pack provenance;
- introducing outbound tote reservoirs or allocation;
- allocating generated output sheet numbers;
- integrating lifecycle state with Adapting, Third Party, P2P, or Exceptions;
- creating or hiding renderables;
- adding persistence, event sourcing, a database, or a new worker thread.

## Step 1: Add Typed Logical And Physical Identities

Allowed production changes:

- create `OrderSheetKey.java` under `online.davisfamily.warehouse.sim.dsp.model`;
- create `PhysicalToteId.java` under `online.davisfamily.warehouse.sim.dsp.model`;
- add `orderSheetKey()` to `NotionalToteOrder`;
- delete the unused `ToteType.java`.

`OrderSheetKey` must be a record with exactly:

```java
public record OrderSheetKey(String orderId, int sheetNumber)
```

Rules:

- trim `orderId` in the compact constructor;
- reject null or blank `orderId`;
- reject `sheetNumber < 1`;
- do not override record equality or hash code;
- do not flatten the key into one concatenated identity string.

`PhysicalToteId` must be a record with exactly:

```java
public record PhysicalToteId(String value)
```

Rules:

- trim `value` in the compact constructor;
- reject null or blank values;
- do not accept an `OrderSheetKey` or `NotionalToteOrder` constructor argument;
- do not add `fromOrderId(...)` or equivalent.

Add this method to `NotionalToteOrder`:

```java
public OrderSheetKey orderSheetKey()
```

It returns `new OrderSheetKey(orderId, sheetNumber)`. Do not change record components or existing constructors in this step.

Create `DspIdentityTest` under the matching model test package.

Required tests:

- `shouldCreateOrderSheetKeyFromOrderIdAndSheetNumber()`
- `shouldRejectBlankOrderIdAndInvalidSheetNumber()`
- `shouldTreatDifferentSheetsAsDifferentLogicalIdentities()`
- `shouldValidateAndTrimPhysicalToteId()`
- `shouldExposeTypedOrderSheetKeyFromNotionalToteOrder()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.DspIdentityTest --tests online.davisfamily.warehouse.sim.dsp.model.DspOrderModelTest
```

## Step 2: Add The Physical Tote Role And Lifecycle Model

Create these files under `online.davisfamily.warehouse.sim.dsp.lifecycle`:

- `PhysicalToteRole.java`
- `PhysicalToteLifecycleState.java`
- `PhysicalToteRecord.java`

`PhysicalToteRole` values:

```text
INBOUND_PACK
PRE_P2P
OUTBOUND_BAG
```

Meaning:

- `INBOUND_PACK`: a Cencora-supplied physical tote carrying loose packs;
- `PRE_P2P`: a physical empty tote introduced at AV02 for logical EMPTY work;
- `OUTBOUND_BAG`: an independently supplied tote receiving completed bags after P2P.

`PhysicalToteLifecycleState` values:

```text
INBOUND_PACK_TOTE
ACTIVE_PRE_P2P
CONSUMED_AT_ADAPTING
CONSUMED_AT_P2P
OUTBOUND_BAG_TOTE
OUTBOUND
```

`PhysicalToteRecord` must be an immutable record containing:

```java
PhysicalToteId id
PhysicalToteRole role
PhysicalToteLifecycleState state
```

Provide these named factories:

```java
public static PhysicalToteRecord inboundPack(PhysicalToteId id)
public static PhysicalToteRecord preP2p(PhysicalToteId id)
public static PhysicalToteRecord outboundBag(PhysicalToteId id)
```

They create these initial combinations:

| Factory | Role | State |
| --- | --- | --- |
| `inboundPack` | `INBOUND_PACK` | `INBOUND_PACK_TOTE` |
| `preP2p` | `PRE_P2P` | `ACTIVE_PRE_P2P` |
| `outboundBag` | `OUTBOUND_BAG` | `OUTBOUND_BAG_TOTE` |

Provide:

```java
public PhysicalToteRecord transitionTo(PhysicalToteLifecycleState nextState)
public boolean terminal()
```

Allowed transitions:

```text
INBOUND_PACK_TOTE -> ACTIVE_PRE_P2P
INBOUND_PACK_TOTE -> CONSUMED_AT_ADAPTING
ACTIVE_PRE_P2P    -> CONSUMED_AT_ADAPTING
ACTIVE_PRE_P2P    -> CONSUMED_AT_P2P
OUTBOUND_BAG_TOTE -> OUTBOUND
```

Rules:

- reject null components and null next states;
- reject a state incompatible with the tote role, including direct record construction;
- reject same-state transitions;
- reject transitions out of `CONSUMED_AT_ADAPTING`, `CONSUMED_AT_P2P`, or `OUTBOUND`;
- `terminal()` is true only for those three terminal states;
- do not mutate the current record.

Create `PhysicalToteRecordTest`.

Required tests:

- `shouldCreateEachPhysicalToteRoleInItsInitialState()`
- `shouldAdvanceInboundToteToPreP2pAndConsumedAtP2p()`
- `shouldAllowAdaptedInboundToteToBeConsumedAtAdapting()`
- `shouldAdvanceOutboundBagToteToOutbound()`
- `shouldRejectRoleStateMismatchAndIllegalTransition()`
- `shouldRejectTransitionFromTerminalState()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecordTest
```

## Step 3: Add Immutable Assignment Records

Create under `online.davisfamily.warehouse.sim.dsp.lifecycle`:

- `PhysicalToteAssignmentStage.java`
- `PhysicalToteAssignmentEndReason.java`
- `PhysicalToteAssignment.java`

`PhysicalToteAssignmentStage` values:

```text
INBOUND_PACK
PREPARATION
PRE_P2P
OUTBOUND_BAG
OUTBOUND
```

`PhysicalToteAssignmentEndReason` values:

```text
ADVANCED_TO_NEXT_STAGE
CONSUMED_AT_ADAPTING
CONSUMED_AT_P2P
OUTBOUND_TOTE_CLOSED
REALLOCATED_TO_GENERATED_SHEET
```

`PhysicalToteAssignment` must be an immutable record containing:

```java
long sequenceNumber
OrderSheetKey orderSheetKey
PhysicalToteId physicalToteId
PhysicalToteAssignmentStage stage
Duration activatedAt
Optional<Duration> terminatedAt
Optional<PhysicalToteAssignmentEndReason> endReason
```

Provide:

```java
public static PhysicalToteAssignment active(
        long sequenceNumber,
        OrderSheetKey orderSheetKey,
        PhysicalToteId physicalToteId,
        PhysicalToteAssignmentStage stage,
        Duration activatedAt)

public boolean active()

public PhysicalToteAssignment terminate(
        Duration terminationTime,
        PhysicalToteAssignmentEndReason reason)
```

Validation rules:

- `sequenceNumber >= 0`;
- all required values are non-null;
- activation and termination durations are non-negative;
- termination must not precede activation;
- active records have both optionals empty;
- terminated records have both optionals populated;
- reject one populated optional without the other;
- reject terminating an already terminated assignment;
- `terminate(...)` returns a new record with the same sequence number and identity fields.

Do not add mutable setters or store wall-clock `Instant` values.

Create `PhysicalToteAssignmentTest`.

Required tests:

- `shouldCreateActiveAssignment()`
- `shouldTerminateAssignmentWithoutChangingItsIdentityOrSequence()`
- `shouldRejectNegativeOrReversedSimulationTimes()`
- `shouldRejectPartialTerminationData()`
- `shouldRejectTerminatingAssignmentTwice()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentTest
```

## Step 4: Add The Simulation-Thread Lifecycle Ledger

Create `PhysicalToteLifecycleLedger` under `online.davisfamily.warehouse.sim.dsp.lifecycle`.

The ledger owns:

- physical tote records keyed by `PhysicalToteId` in deterministic insertion order;
- assignment records in deterministic sequence order;
- the next assignment sequence number.

Required public API:

```java
public void register(PhysicalToteRecord tote)
public PhysicalToteRecord transitionTote(
        PhysicalToteId toteId,
        PhysicalToteLifecycleState nextState)
public PhysicalToteAssignment assign(
        OrderSheetKey orderSheetKey,
        PhysicalToteId toteId,
        PhysicalToteAssignmentStage stage,
        Duration activationTime)
public PhysicalToteAssignment terminateActiveAssignment(
        OrderSheetKey orderSheetKey,
        Duration terminationTime,
        PhysicalToteAssignmentEndReason reason)
public Optional<PhysicalToteRecord> tote(PhysicalToteId toteId)
public Optional<PhysicalToteAssignment> activeAssignmentFor(OrderSheetKey orderSheetKey)
public List<PhysicalToteAssignment> activeAssignmentsFor(PhysicalToteId toteId)
public List<PhysicalToteAssignment> assignmentHistoryFor(OrderSheetKey orderSheetKey)
public PhysicalToteLifecycleSnapshot snapshot()
```

Registration rules:

- reject null totes;
- reject duplicate physical tote IDs;
- transition only registered totes;
- delegate transition validation to `PhysicalToteRecord.transitionTo(...)`.

Assignment rules:

- reject assignment to an unregistered tote;
- reject a new assignment when the logical sheet already has an active assignment;
- `INBOUND_PACK`, `PREPARATION`, and `PRE_P2P` stages are exclusive on the physical tote: reject assignment of another logical sheet while one is active;
- `OUTBOUND_BAG` and `OUTBOUND` stages may assign several logical sheets to the same physical tote;
- stage must be compatible with physical tote role:

| Physical role | Allowed stages |
| --- | --- |
| `INBOUND_PACK` | `INBOUND_PACK`, `PREPARATION`, `PRE_P2P` |
| `PRE_P2P` | `PRE_P2P` |
| `OUTBOUND_BAG` | `OUTBOUND_BAG`, `OUTBOUND` |

- assigning to a terminal physical tote is forbidden;
- assign deterministic sequence numbers beginning at zero;
- termination looks up by logical sheet key and rejects missing active assignments;
- ending an assignment retains it in history at its original sequence position;
- queries return immutable lists and never expose mutable collections.

The ledger must not be synchronized and must not create an executor. It is simulation-thread state. Thread-safe publication is provided by the immutable snapshot in Step 5.

Create `PhysicalToteLifecycleLedgerTest`.

Required tests:

- `shouldRegisterAndTransitionPhysicalTote()`
- `shouldRejectDuplicatePhysicalToteIdentity()`
- `shouldRejectAssignmentToUnknownOrTerminalTote()`
- `shouldAllowOnlyOneActivePhysicalTotePerLogicalSheet()`
- `shouldAllowOnlyOneLogicalSheetOnInboundTote()`
- `shouldAllowMultipleLogicalSheetsOnOutboundTote()`
- `shouldPermitSequentialAssignmentsAfterTermination()`
- `shouldRetainAssignmentHistoryInSequenceOrder()`
- `shouldRejectStageIncompatibleWithPhysicalToteRole()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedgerTest
```

## Step 5: Add Immutable Lifecycle Snapshots

Create `PhysicalToteLifecycleSnapshot` under `online.davisfamily.warehouse.sim.dsp.lifecycle`.

It must be an immutable record containing:

```java
Map<PhysicalToteId, PhysicalToteRecord> totes
List<PhysicalToteAssignment> assignments
```

Rules:

- reject null collections and null elements;
- defensively copy both collections;
- preserve deterministic tote insertion order in the snapshot rather than relying on unspecified `Map.copyOf(...)` iteration order;
- assignment list order must match ledger sequence order;
- expose query helpers equivalent to the ledger's read-only queries:
  - `activeAssignmentFor(OrderSheetKey)`;
  - `activeAssignmentsFor(PhysicalToteId)`;
  - `assignmentHistoryFor(OrderSheetKey)`;
- query results are immutable;
- the snapshot contains no ledger reference and no mutable simulation/controller object.

`PhysicalToteLifecycleLedger.snapshot()` must return a fresh snapshot representing one consistent simulation-thread state.

Create `PhysicalToteLifecycleSnapshotTest`.

Required tests:

- `shouldDefensivelyCopyTotesAndAssignments()`
- `shouldPreserveDeterministicToteAndAssignmentOrder()`
- `shouldExposeActiveAssignmentsAndHistory()`
- `shouldRemainUnchangedAfterLedgerAdvances()`
- `shouldRejectNullSnapshotContent()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshotTest
```

## Step 6: Add The Identity And Assignment Scenario Regression

Create `LogicalPhysicalIdentityScenarioTest` under the lifecycle test package.

Build one domain-only scenario with:

- logical order `ORDER-A`, Sheet 001;
- an inbound physical tote `INBOUND-100`;
- an independently supplied outbound tote `OUTBOUND-900`;
- logical order `ORDER-B`, Sheet 001 sharing the outbound tote;
- logical order `ORDER-A`, Sheet 002 used as a separate concurrent output allocation fixture.

The test must prove:

1. `ORDER-A/001` is assigned to `INBOUND-100` at `INBOUND_PACK`;
2. ending that assignment and consuming the inbound tote does not remove the logical key or assignment history;
3. `ORDER-A/001` can then be assigned to `OUTBOUND-900` at `OUTBOUND_BAG`;
4. `ORDER-B/001` can simultaneously be assigned to the same outbound tote;
5. `ORDER-A/001` cannot simultaneously be assigned to another tote;
6. `ORDER-A/002` can be assigned independently because it is a distinct logical sheet;
7. the immutable snapshot explains all retained assignments using distinct logical and physical IDs.

Required test methods:

- `shouldRetainIdentityAcrossInboundConsumptionAndOutboundSubstitution()`
- `shouldAggregateDifferentLogicalSheetsIntoOneOutboundTote()`
- `shouldRequireDifferentSheetForConcurrentOutputAssignment()`

Ask the user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.lifecycle.LogicalPhysicalIdentityScenarioTest
.\gradlew test --tests online.davisfamily.warehouse.sim.dsp.model.* --tests online.davisfamily.warehouse.sim.dsp.lifecycle.*
```

After the focused tests pass, ask the user to run the complete Gradle test suite before closing the branch. No visual test is required because this branch creates no runtime or renderable integration.

## Completion Criteria

- Logical sheet and physical tote identities are represented by different Java types.
- `NotionalToteOrder` exposes its typed `OrderSheetKey` without breaking current consumers.
- The unused and misleading logical-valued `ToteType` enum is removed.
- Physical tote role is distinct from `OrderType`.
- Valid physical tote lifecycle transitions are explicit and tested.
- Logical-to-physical assignments retain activation, termination, stage, reason, and deterministic history order.
- One logical sheet cannot have two active physical tote assignments.
- An inbound/pre-P2P tote cannot represent two current logical sheets.
- An outbound tote can aggregate several logical sheets.
- Lifecycle snapshots are immutable and safe to publish to scheduler inspection later.
- Existing 12N, scheduler, station, machine, debug, and rendering behavior remains unchanged.
- Bag planning, outbound allocation, 12N physical mapping, and live lifecycle integration remain deferred to their named branches.

## Follow-On Branch

After this branch is green and merged to `master`, create the detailed plan for:

```text
feature/dsp-inbound-tote-lifecycle
```

That branch will retain 12N `transportContainer`, create inbound physical tote manifests, migrate physical station/load-plan identity away from `notionalToteId`, and represent EMPTY as logical-only before AV02.
