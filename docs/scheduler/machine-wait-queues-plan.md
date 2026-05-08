# Machine Wait Queues Plan

Status: drafted. Implement next on `feature/machine-wait-queues`.

## Summary

This branch is an architecture correction inserted mid DSP scheduler work.

The renderable visibility branch exposed that current scheduler/P2P admission is asking the wrong question for released totes. It currently treats a downstream machine's ability to process a tote as equivalent to the physical ability to release a tote into available track/buffer space. In a warehouse those are separate concerns:

```text
Can enter station input queue? != Can be processed by the machine now?
```

This branch introduces explicit machine input wait queues with manually configured capacity. The first application should be the P2P/tipper debug path, so scheduler release can be based on queue space while the existing local machine admission remains the processing gate.

Do not add scheduler threading, database storage, JSON-to-renderable loading, manual exception buttons, lid opener/strapper machines, or full warehouse layout in this branch.

## Key Decisions

- Each machine/station can expose an input wait queue capacity as a simple count, configured by layout/domain code.
- Queue capacity is not calculated from tote dimensions and track length in this branch.
- Queue admission answers: "can this station accept another tote into its waiting space?"
- Machine processing admission answers: "can this machine process this specific tote now?"
- `ToteToBagFlowController.canAdmit(ToteLoadPlan)` remains the processing/tipping gate for P2P, not the scheduler release gate.
- Scheduler station admission for P2P should use queue availability once this branch is wired.
- The tipper/tote-to-bag controller should not become the global scheduler.
- Keep the implementation small and debug-P2P-first; generalise only where it prevents immediate duplicate queue logic.

## Step 1: Add Generic Machine Wait Queue Domain

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/machine/queue/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/sim/machine/queue/`

Create:

- `MachineWaitQueue.java`
  - constructor taking `String id` and `int capacity`
  - `boolean canAccept()`
  - `void enqueue(String toteId)`
  - `String peek()`
  - `String dequeue()`
  - `int size()`
  - `int capacity()`
  - `List<String> toteIds()`

Implementation:

- Reject null/blank ids.
- Reject capacity less than zero.
- Reject null/blank tote ids.
- Reject enqueue when full.
- Preserve FIFO order.
- Capacity zero is valid and means no waiting space.

Expected output:

- A small reusable queue primitive exists without route/render dependencies.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueTest
```

## Step 2: Add Queue Snapshot/Inspectable Surface

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/machine/queue/`
- Create tests under `app/src/test/java/online/davisfamily/warehouse/sim/machine/queue/`

Create:

- `MachineWaitQueueSnapshot.java`
  - immutable id, capacity, queued tote ids
  - `boolean canAccept()`

Update `MachineWaitQueue`:

- Add `MachineWaitQueueSnapshot snapshot()`

Rules:

- Snapshot must copy queued tote ids.
- Do not expose mutable queue internals.

Expected output:

- Scheduler/debug code can inspect queue state without mutating it.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.machine.queue.*
```

## Step 3: Add Tipper Input Queue Wrapper

Allowed files:

- Create files under `app/src/main/java/online/davisfamily/warehouse/sim/totebag/assembly/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/sim/totebag/assembly/`

Create:

- `TipperInputQueue.java`
  - wraps `MachineWaitQueue`
  - stores `TipperTotePayload` by tote id
  - `boolean canAccept()`
  - `void enqueue(TipperTotePayload payload)`
  - `TipperTotePayload peekPayload()`
  - `TipperTotePayload dequeuePayload()`
  - `MachineWaitQueueSnapshot snapshot()`

Implementation:

- Reject null payloads.
- Use `payload.getTote().getId()` as the queue key.
- Keep payload ownership local to this queue.
- Preserve FIFO order.

Expected output:

- The tipper/P2P debug path has a typed input queue without changing scheduler domain objects.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueTest
```

## Step 4: Add Queue-Aware Tipper Release Target

Allowed files:

- Update/create files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Create/update tests under `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/`

Create or update:

- Add a queue-backed release target, for example `QueuedTipperFlowScheduledToteReleaseTarget`

Behavior:

- `canAcceptRelease()` returns `tipperInputQueue.canAccept()`.
- `release(payload)` enqueues the payload and adds its renderable/trackable object to the scene if not already present.
- It must not call `ToteTrackTipperFlowController.acceptNextTote(...)` directly.
- It should return deferred when the queue is full.

Rules:

- Do not bypass scheduler runtime command application.
- Do not change `ScheduledDebugToteInjectorController` scheduling logic in this step.
- Do not call `ToteToBagFlowController.canAdmit(...)` from this release target.

Expected output:

- Scheduler release can place a tote into visible local waiting space without requiring immediate machine processing.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.QueuedTipperFlowScheduledToteReleaseTargetTest
```

## Step 5: Drain Queue Into Existing Tipper Flow

Allowed files:

- Update/create files under `app/src/main/java/online/davisfamily/warehouse/sim/totebag/assembly/`
- Update/create tests under `app/src/test/java/online/davisfamily/warehouse/sim/totebag/assembly/`

Create:

- `TipperInputQueueController.java`
  - implements `SimulationController`
  - dependencies: `TipperInputQueue`, `ToteTrackTipperFlowController`

Behavior:

- On update, if queue has a payload and `tipperFlowController.canAcceptNextTote()` is true:
  - dequeue the next payload
  - call `tipperFlowController.acceptNextTote(payload.getTote())`
- If the tipper flow cannot accept, leave the queued payload in FIFO order.

Rules:

- The existing `ToteTrackTipperFlowController` admission predicate remains the final processing gate after the tote reaches the tipper stop point.
- Do not reintroduce a two-slot special case inside `ToteTrackTipperFlowController`.
- Do not make the queue controller inspect PRL/PCR/bagger internals.

Expected output:

- Waiting totes are released into scene/track space early, then handed to the existing tipper flow only when that flow is ready.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueueControllerTest --tests online.davisfamily.warehouse.sim.totebag.ToteTrackTipperFlowControllerTest
```

## Step 6: Change Debug P2P Scheduler Admission To Queue Capacity

Allowed files:

- Update files under `app/src/main/java/online/davisfamily/warehouse/testing/scheduler/`
- Update/create tests under `app/src/test/java/online/davisfamily/warehouse/testing/scheduler/`

Implementation:

- Replace debug scheduler P2P release admission based on `ToteToBagFlowController.canAdmit(...)` with queue-capacity admission.
- Keep `ToteToBagFlowController.canAdmit(...)` wired as the local tipper processing predicate.
- The scheduler should release into the input queue when queue capacity exists.
- The tipper should only load/tip the tote when the local predicate later permits it.

Rules:

- Do not delete `ScheduledReleaseP2pAdmission` if other tests still cover candidate-specific processing admission; either replace it in installer wiring or add a new queue-specific adapter.
- Keep candidate-specific scheduler mechanics intact.
- Preserve service-centre window behavior.

Expected output:

- Scheduler release means "enter P2P waiting queue", not "tip immediately".
- Local machine processing remains guarded by existing P2P/tote-to-bag state.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

## Step 7: Wire Integrated Debug Installer

Allowed files:

- Update `app/src/main/java/online/davisfamily/warehouse/testing/IntegratedToteToBagDebugInstaller.java`
- Add/update focused installer tests only if an existing stable pattern exists.

Implementation:

- Create a `TipperInputQueue` with a small explicit capacity, initially `1`.
- Wire scheduler release target to enqueue into that queue.
- Add `TipperInputQueueController` to the simulation.
- Keep the bootstrap/primary tote path unchanged unless this branch explicitly expands it.
- Keep scheduler debug overlay registered on `tipper_slide`.
- Extend scheduler/debug inspection lines to show queue capacity and queued tote ids if practical through existing inspection mechanisms.

Expected output:

- The second scheduler-controlled tote can appear in the scene before the tipper can process it, provided queue capacity exists.
- If the queue is full, scheduler release blocks/defer visibly through existing debug state.

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.totebag.*
```

## Step 8: Visual Check

Allowed files:

- No code changes unless visual verification exposes a concrete issue.

Visual expectations:

- The first/bootstrap tote behaves as before.
- A scheduler-controlled queued tote appears once P2P input queue space exists, even if P2P/tote-to-bag cannot process it yet.
- The queued tote waits in a stable visible position/track state until the tipper flow can accept it.
- The tipper still does not tip a tote until `ToteToBagFlowController.canAdmit(...)` allows it.
- Closed-lid contained packs remain hidden; active/free/downstream packs remain visible.
- Scheduler overlay still works from `tipper_slide` and should make queue blocking easier to explain.

## Step 9: Branch Closure

Ask user to run:

```powershell
.\gradlew test --tests online.davisfamily.warehouse.sim.machine.queue.* --tests online.davisfamily.warehouse.sim.totebag.* --tests online.davisfamily.warehouse.testing.scheduler.* --tests online.davisfamily.warehouse.sim.dsp.*
```

Then ask the user to run their trusted broader suite and visual pass.

Completion criteria:

- Machine wait queue primitive exists and is tested.
- Tipper/P2P debug release uses queue capacity as the scheduler release gate.
- Existing P2P processing admission remains the local machine gate.
- Scheduler remains synchronous.
- No scheduler thread, database, JSON renderable loading, or full warehouse layout is introduced.

## Deferred Work

- Apply wait queues to lid opener, tote strapper, manual station, OSR buffer, and other future machines.
- Replace debug-only P2P queue admission with production multi-P2P queue snapshots.
- Model queue positions from real track geometry.
- Add command-panel controls for manual exception/override workflows.
- Revisit whether `StationAdmissionSnapshot` should carry queue-specific fields once more machines use queues.
