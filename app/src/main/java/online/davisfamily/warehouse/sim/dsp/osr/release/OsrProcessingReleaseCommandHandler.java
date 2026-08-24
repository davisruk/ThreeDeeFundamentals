package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommit;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class OsrProcessingReleaseCommandHandler implements SchedulerCommandHandler {
    private final OsrPhysicalInventory inventory;
    private final InboundToteLifecycleController lifecycleController;
    private final Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier;
    private final OsrProcessingReleaseTargetRegistry targetRegistry;
    private final P2pReleaseAssignmentCommitter p2pAssignmentCommitter;

    public OsrProcessingReleaseCommandHandler(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            OsrProcessingReleaseTargetRegistry targetRegistry) {
        this(
                inventory,
                lifecycleController,
                clockSnapshotSupplier,
                targetRegistry,
                P2pReleaseAssignmentCommitter.NO_OP);
    }

    public OsrProcessingReleaseCommandHandler(
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            OsrProcessingReleaseTargetRegistry targetRegistry,
            P2pReleaseAssignmentCommitter p2pAssignmentCommitter) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        if (lifecycleController == null) {
            throw new IllegalArgumentException("lifecycleController must not be null");
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
        this.lifecycleController = lifecycleController;
        this.clockSnapshotSupplier = clockSnapshotSupplier;
        this.targetRegistry = targetRegistry;
        this.p2pAssignmentCommitter = p2pAssignmentCommitter;
    }

    @Override
    public SchedulerCommandApplicationResult apply(SchedulerCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (!(command instanceof ReleasePhysicalToteFromOsrCommand releaseCommand)) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Unsupported scheduler command: " + command.getClass().getSimpleName());
        }

        DspOperationalClockSnapshot clockSnapshot = clockSnapshotSupplier.get();
        if (clockSnapshot == null) {
            throw new IllegalStateException("clockSnapshotSupplier returned null");
        }
        Duration releaseTime = clockSnapshot.elapsedSimulationTime();
        OsrInventorySnapshot inventorySnapshot = inventory.snapshot();
        Optional<InboundToteManifest> storedManifest =
                inventorySnapshot.findStored(releaseCommand.physicalToteId());
        if (storedManifest.isEmpty()) {
            if (inventorySnapshot.hasDeparted(releaseCommand.physicalToteId())) {
                return rejected("Physical tote has already departed OSR: ", releaseCommand);
            }
            return rejected("Unknown physical tote in OSR inventory: ", releaseCommand);
        }

        InboundToteManifest manifest = storedManifest.orElseThrow();
        if (!manifest.orderSheetKey().equals(releaseCommand.orderSheetKey())) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote order-sheet identity does not match live OSR manifest");
        }
        if (!manifest.serviceCentreId().equals(releaseCommand.serviceCentreId())) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote service-centre identity does not match live OSR manifest");
        }

        PhysicalToteLifecycleSnapshot lifecycleSnapshot = lifecycleController.snapshot();
        PhysicalToteRecord tote = lifecycleSnapshot.totes().get(releaseCommand.physicalToteId());
        if (tote == null) {
            return rejected("Physical tote is missing from lifecycle state: ", releaseCommand);
        }
        if (tote.role() != PhysicalToteRole.INBOUND_PACK
                || tote.state() != PhysicalToteLifecycleState.INBOUND_PACK_TOTE) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Stored physical tote has invalid lifecycle role or state: "
                            + releaseCommand.physicalToteId().value());
        }

        List<PhysicalToteAssignment> physicalAssignments =
                lifecycleSnapshot.activeAssignmentsFor(releaseCommand.physicalToteId());
        if (!physicalAssignments.isEmpty()) {
            return rejected("Stored physical tote already has an active assignment: ", releaseCommand);
        }
        Optional<PhysicalToteAssignment> sheetAssignment =
                lifecycleSnapshot.activeAssignmentFor(releaseCommand.orderSheetKey());
        if (sheetAssignment.isPresent()) {
            PhysicalToteId blockingPhysicalToteId =
                    sheetAssignment.orElseThrow().physicalToteId();
            if (blockingPhysicalToteId.equals(releaseCommand.physicalToteId())) {
                return rejected("Stored physical tote already owns its logical sheet: ", releaseCommand);
            }
            return SchedulerCommandApplicationResult.deferredResult(
                    "Logical sheet is active on physical tote: "
                            + blockingPhysicalToteId.value());
        }

        try {
            lifecycleController.validateActivation(
                    releaseCommand.physicalToteId(), releaseTime);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote activation validation failed: " + exception.getMessage());
        }

        Optional<OsrProcessingReleaseTarget> target =
                targetRegistry.find(releaseCommand.releaseTargetId());
        if (target.isEmpty()) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Unknown OSR processing release target: "
                            + releaseCommand.releaseTargetId());
        }

        P2pReleaseAssignmentCommit pendingP2pCommit;
        try {
            pendingP2pCommit = p2pAssignmentCommitter.prepare(releaseCommand, manifest);
            if (pendingP2pCommit == null) {
                throw new IllegalStateException("P2P release assignment committer returned null");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "P2P release assignment validation failed: " + exception.getMessage());
        }

        OsrProcessingReleaseRequest request =
                new OsrProcessingReleaseRequest(
                        manifest,
                        releaseTime,
                        releaseCommand.proposedP2pAssignment());
        SchedulerCommandApplicationResult targetResult =
                target.orElseThrow().accept(request);
        if (targetResult == null) {
            throw new IllegalStateException("OSR processing release target returned null");
        }
        if (!targetResult.applied()) {
            return targetResult;
        }

        pendingP2pCommit.commit();
        InboundToteManifest departedManifest =
                inventory.recordDeparture(releaseCommand.physicalToteId());
        if (!departedManifest.equals(manifest)) {
            throw new IllegalStateException(
                    "OSR inventory departed a different manifest than was validated");
        }
        lifecycleController.activate(releaseCommand.physicalToteId(), releaseTime);
        return SchedulerCommandApplicationResult.appliedResult();
    }

    private static SchedulerCommandApplicationResult rejected(
            String reason,
            ReleasePhysicalToteFromOsrCommand command) {
        return SchedulerCommandApplicationResult.rejectedResult(
                reason + command.physicalToteId().value());
    }
}
