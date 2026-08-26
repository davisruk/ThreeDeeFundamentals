package online.davisfamily.warehouse.sim.dsp.av02;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalP2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommit;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/**
 * Applies a physical tote release for a tote allocated at AV02.
 *
 * <p>The handler deliberately leaves the lifecycle assignment in its active
 * {@code PRE_P2P} state. AV02 departure changes ownership of the waiting
 * inventory entry; it is not an OSR activation.</p>
 */
public final class Av02OperationalCommandHandler implements SchedulerCommandHandler {
    private final Av02PhysicalToteInventory inventory;
    private final PhysicalToteLifecycleLedger lifecycleLedger;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier;
    private final OperationalPhysicalToteReleaseTargetRegistry targetRegistry;
    private final OperationalP2pReleaseAssignmentCommitter p2pAssignmentCommitter;

    public Av02OperationalCommandHandler(
            Av02PhysicalToteInventory inventory,
            PhysicalToteLifecycleLedger lifecycleLedger,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            OperationalPhysicalToteReleaseTargetRegistry targetRegistry,
            OperationalP2pReleaseAssignmentCommitter p2pAssignmentCommitter) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        if (lifecycleLedger == null) {
            throw new IllegalArgumentException("lifecycleLedger must not be null");
        }
        if (loadPlanRegistry == null) {
            throw new IllegalArgumentException("loadPlanRegistry must not be null");
        }
        if (clockSnapshotSupplier == null) {
            throw new IllegalArgumentException("clockSnapshotSupplier must not be null");
        }
        if (targetRegistry == null) {
            throw new IllegalArgumentException("targetRegistry must not be null");
        }
        if (p2pAssignmentCommitter == null) {
            throw new IllegalArgumentException("p2pAssignmentCommitter must not be null");
        }
        this.inventory = inventory;
        this.lifecycleLedger = lifecycleLedger;
        this.loadPlanRegistry = loadPlanRegistry;
        this.clockSnapshotSupplier = clockSnapshotSupplier;
        this.targetRegistry = targetRegistry;
        this.p2pAssignmentCommitter = p2pAssignmentCommitter;
    }

    @Override
    public SchedulerCommandApplicationResult apply(SchedulerCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getClass() != ReleasePhysicalToteFromAv02Command.class) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Unsupported scheduler command: " + command.getClass().getSimpleName());
        }

        ReleasePhysicalToteFromAv02Command releaseCommand =
                (ReleasePhysicalToteFromAv02Command) command;
        DspOperationalClockSnapshot clockSnapshot = clockSnapshotSupplier.get();
        if (clockSnapshot == null) {
            throw new IllegalStateException("clockSnapshotSupplier returned null");
        }
        Duration releaseTime = clockSnapshot.elapsedSimulationTime();

        Av02AllocatedTote allocatedTote;
        try {
            allocatedTote = validateInventory(releaseCommand);
            validateLifecycle(releaseCommand);
            validateLoadPlan(releaseCommand.physicalToteId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "AV02 release validation failed: " + exception.getMessage());
        }

        OperationalPhysicalToteReleaseTarget target = targetRegistry
                .find(releaseCommand.releaseTargetId())
                .orElse(null);
        if (target == null) {
            return rejected("Unknown AV02 operational release target: ", releaseCommand);
        }

        P2pReleaseAssignmentRequest assignmentRequest =
                P2pReleaseAssignmentRequest.from(releaseCommand);
        P2pReleaseAssignmentCommit pendingP2pCommit;
        try {
            pendingP2pCommit = p2pAssignmentCommitter.prepare(assignmentRequest);
            if (pendingP2pCommit == null) {
                throw new IllegalStateException(
                        "P2P release assignment committer returned null");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "P2P release assignment validation failed: " + exception.getMessage());
        }

        OperationalPhysicalToteReleaseRequest request =
                new OperationalPhysicalToteReleaseRequest(
                        allocatedTote.identity(),
                        releaseTime,
                        releaseCommand.proposedP2pAssignment());
        SchedulerCommandApplicationResult targetResult = target.accept(request);
        if (targetResult == null) {
            throw new IllegalStateException("AV02 release target returned null");
        }
        if (!targetResult.applied()) {
            return targetResult;
        }

        pendingP2pCommit.commit();
        Av02AllocatedTote departed = inventory.recordDeparture(
                releaseCommand.physicalToteId());
        if (!departed.equals(allocatedTote)) {
            throw new IllegalStateException(
                    "AV02 inventory departed a different allocated tote than was validated");
        }
        return SchedulerCommandApplicationResult.appliedResult();
    }

    private Av02AllocatedTote validateInventory(
            ReleasePhysicalToteFromAv02Command command) {
        Optional<Av02AllocatedTote> waiting = inventory.findWaiting(command.physicalToteId());
        if (waiting.isEmpty()) {
            boolean departed = inventory.snapshot().departedTotes().stream()
                    .anyMatch(tote -> tote.physicalToteId().equals(command.physicalToteId()));
            if (departed) {
                throw rejectedException(
                        "AV02 physical tote has already departed: ", command);
            }
            throw rejectedException(
                    "Unknown AV02 physical tote in inventory: ", command);
        }

        Av02AllocatedTote allocatedTote = waiting.orElseThrow();
        if (!allocatedTote.orderSheetKey().equals(command.orderSheetKey())) {
            throw rejectedException(
                    "AV02 physical tote order-sheet identity does not match inventory: ",
                    command);
        }
        if (!allocatedTote.serviceCentreId().equals(command.serviceCentreId())) {
            throw rejectedException(
                    "AV02 physical tote service-centre identity does not match inventory: ",
                    command);
        }
        return allocatedTote;
    }

    private void validateLifecycle(ReleasePhysicalToteFromAv02Command command) {
        PhysicalToteLifecycleSnapshot lifecycleSnapshot = lifecycleLedger.snapshot();
        PhysicalToteRecord tote = lifecycleSnapshot.totes().get(command.physicalToteId());
        if (tote == null) {
            throw rejectedException(
                    "AV02 physical tote is missing from lifecycle state: ", command);
        }
        if (tote.role() != PhysicalToteRole.PRE_P2P
                || tote.state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            throw rejectedException(
                    "AV02 physical tote has invalid lifecycle role or state: ", command);
        }

        List<PhysicalToteAssignment> toteAssignments =
                lifecycleSnapshot.activeAssignmentsFor(command.physicalToteId());
        if (toteAssignments.size() != 1) {
            throw rejectedException(
                    "AV02 physical tote must have exactly one active lifecycle assignment: ",
                    command);
        }
        PhysicalToteAssignment toteAssignment = toteAssignments.getFirst();
        if (toteAssignment.stage() != PhysicalToteAssignmentStage.PRE_P2P
                || !toteAssignment.orderSheetKey().equals(command.orderSheetKey())) {
            throw rejectedException(
                    "AV02 physical tote has an invalid active sheet assignment: ", command);
        }

        Optional<PhysicalToteAssignment> sheetAssignment =
                lifecycleSnapshot.activeAssignmentFor(command.orderSheetKey());
        if (sheetAssignment.isEmpty()
                || !sheetAssignment.orElseThrow().physicalToteId()
                        .equals(command.physicalToteId())) {
            throw rejectedException(
                    "AV02 logical sheet is not actively assigned to the physical tote: ",
                    command);
        }
    }

    private void validateLoadPlan(PhysicalToteId physicalToteId) {
        ToteLoadPlan loadPlan = loadPlanRegistry.getLoadPlanFor(physicalToteId);
        if (loadPlan == null) {
            throw new IllegalStateException(
                    "AV02 physical tote has no load plan: " + physicalToteId.value());
        }
        if (!physicalToteId.equals(loadPlan.physicalToteId())) {
            throw new IllegalStateException(
                    "AV02 load plan physical ID does not match the physical tote: "
                            + physicalToteId.value());
        }
    }

    private static SchedulerCommandApplicationResult rejected(
            String reason,
            ReleasePhysicalToteFromAv02Command command) {
        return SchedulerCommandApplicationResult.rejectedResult(
                reason + command.physicalToteId().value());
    }

    private static IllegalStateException rejectedException(
            String reason,
            ReleasePhysicalToteFromAv02Command command) {
        return new IllegalStateException(reason + command.physicalToteId().value());
    }
}
