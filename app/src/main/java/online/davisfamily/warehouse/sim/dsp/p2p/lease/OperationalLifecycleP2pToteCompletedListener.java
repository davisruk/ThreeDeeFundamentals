package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

public final class OperationalLifecycleP2pToteCompletedListener implements TipperToteCompletedListener {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;

    private final InboundToteManifestCatalog manifestCatalog;
    private final InboundLifecycleP2pToteCompletedListener inboundListener;
    private final Av02PhysicalToteInventory av02Inventory;
    private final Av02ToteLifecycleController av02LifecycleController;

    public OperationalLifecycleP2pToteCompletedListener(
            InboundToteManifestCatalog manifestCatalog,
            InboundLifecycleP2pToteCompletedListener inboundListener,
            Av02PhysicalToteInventory av02Inventory,
            Av02ToteLifecycleController av02LifecycleController) {
        if (manifestCatalog == null) {
            throw new IllegalArgumentException("manifestCatalog must not be null");
        }
        if (inboundListener == null) {
            throw new IllegalArgumentException("inboundListener must not be null");
        }
        if (av02Inventory == null) {
            throw new IllegalArgumentException("av02Inventory must not be null");
        }
        if (av02LifecycleController == null) {
            throw new IllegalArgumentException("av02LifecycleController must not be null");
        }
        this.manifestCatalog = manifestCatalog;
        this.inboundListener = inboundListener;
        this.av02Inventory = av02Inventory;
        this.av02LifecycleController = av02LifecycleController;
    }

    @Override
    public void onToteCompleted(Tote tote, SimulationContext context) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        Duration completionTime = simulationTime(context.getSimulationTimeSeconds());
        PhysicalToteId toteId = new PhysicalToteId(tote.getId());
        Optional<InboundToteManifest> manifest = manifestCatalog.findByPhysicalToteId(toteId);
        Av02InventorySnapshot av02Snapshot = av02Inventory.snapshot();
        List<Av02AllocatedTote> av02Entries = findAv02Entries(av02Snapshot, toteId);

        if (manifest.isPresent() && !av02Entries.isEmpty()) {
            throw new IllegalStateException(
                    "Physical tote ID is owned by both an inbound manifest and AV02: "
                            + toteId.value());
        }
        if (manifest.isPresent()) {
            inboundListener.onToteCompleted(tote, context);
            return;
        }
        if (av02Entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown inbound physical tote source: " + toteId.value());
        }
        if (av02Entries.size() != 1) {
            throw new IllegalStateException(
                    "AV02 physical tote ID has multiple inventory entries: " + toteId.value());
        }

        if (av02Snapshot.waitingTotes().stream()
                .anyMatch(allocatedTote -> allocatedTote.physicalToteId().equals(toteId))) {
            throw new IllegalStateException(
                    "AV02 physical tote has not departed for P2P: " + toteId.value());
        }

        Av02AllocatedTote allocatedTote = av02Entries.getFirst();
        validateDepartedAv02Tote(allocatedTote, toteId);
        av02LifecycleController.consumeAtP2p(
                allocatedTote.orderSheetKey(),
                allocatedTote.physicalToteId(),
                completionTime);
    }

    private void validateDepartedAv02Tote(
            Av02AllocatedTote allocatedTote,
            PhysicalToteId completedToteId) {
        if (allocatedTote == null) {
            throw new IllegalStateException("AV02 inventory entry must not be null");
        }
        if (allocatedTote.identity().source() != OperationalPhysicalToteSource.AV02) {
            throw new IllegalStateException("AV02 inventory entry must have AV02 source");
        }
        if (allocatedTote.identity().orderType() != OrderType.EMPTY) {
            throw new IllegalStateException("AV02 inventory entry must represent EMPTY work");
        }
        if (allocatedTote.identity().physicalToteRole() != PhysicalToteRole.PRE_P2P
                || allocatedTote.physicalTote().role() != PhysicalToteRole.PRE_P2P) {
            throw new IllegalStateException("AV02 inventory entry must use PRE_P2P role");
        }
        if (allocatedTote.physicalTote().state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            throw new IllegalStateException(
                    "AV02 inventory entry must represent an active PRE_P2P tote");
        }
        if (!allocatedTote.identity().physicalToteId().equals(completedToteId)
                || !allocatedTote.physicalToteId().equals(completedToteId)
                || !allocatedTote.physicalTote().id().equals(completedToteId)) {
            throw new IllegalStateException(
                    "AV02 inventory entry physical tote ID does not match completed tote");
        }

    }

    private static List<Av02AllocatedTote> findAv02Entries(
            Av02InventorySnapshot snapshot,
            PhysicalToteId toteId) {
        List<Av02AllocatedTote> matches = new ArrayList<>();
        for (Av02AllocatedTote allocatedTote : snapshot.waitingTotes()) {
            if (allocatedTote.physicalToteId().equals(toteId)) {
                matches.add(allocatedTote);
            }
        }
        for (Av02AllocatedTote allocatedTote : snapshot.departedTotes()) {
            if (allocatedTote.physicalToteId().equals(toteId)) {
                matches.add(allocatedTote);
            }
        }
        return List.copyOf(matches);
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
