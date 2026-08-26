package online.davisfamily.warehouse.sim.dsp.av02;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public final class Av02AllocationController implements SimulationController {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;

    private final Supplier<Optional<AllocateEmptyToteAtAv02Command>> commandSupplier;
    private final Supplier<Av02AllocationSnapshot> freshSnapshotSupplier;
    private final Av02PhysicalToteInventory inventory;
    private final PhysicalToteLifecycleLedger lifecycleLedger;
    private final PhysicalToteIdAllocator idAllocator;
    private final Av02ToteLifecycleController lifecycleController;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private Optional<Av02AllocatedTote> lastAllocatedTote = Optional.empty();

    public Av02AllocationController(
            Supplier<Optional<AllocateEmptyToteAtAv02Command>> commandSupplier,
            Supplier<Av02AllocationSnapshot> freshSnapshotSupplier,
            Av02PhysicalToteInventory inventory,
            PhysicalToteLifecycleLedger lifecycleLedger,
            PhysicalToteIdAllocator idAllocator,
            MutableToteLoadPlanRegistry loadPlanRegistry) {
        if (commandSupplier == null || freshSnapshotSupplier == null || inventory == null
                || lifecycleLedger == null || idAllocator == null || loadPlanRegistry == null) {
            throw new IllegalArgumentException("AV02 allocation controller inputs must not be null");
        }
        this.commandSupplier = commandSupplier;
        this.freshSnapshotSupplier = freshSnapshotSupplier;
        this.inventory = inventory;
        this.lifecycleLedger = lifecycleLedger;
        this.idAllocator = idAllocator;
        this.lifecycleController = new Av02ToteLifecycleController(lifecycleLedger, idAllocator);
        this.loadPlanRegistry = loadPlanRegistry;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Optional<AllocateEmptyToteAtAv02Command> suppliedCommand = commandSupplier.get();
        if (suppliedCommand == null) {
            throw new IllegalStateException("commandSupplier returned null");
        }
        if (suppliedCommand.isEmpty()) {
            return;
        }

        AllocateEmptyToteAtAv02Command command = suppliedCommand.orElseThrow();
        Av02AllocationSnapshot freshSnapshot = freshSnapshotSupplier.get();
        if (freshSnapshot == null) {
            throw new IllegalStateException("freshSnapshotSupplier returned null");
        }
        Optional<AllocateEmptyToteAtAv02Command> currentCommand = freshSnapshot.command();
        if (freshSnapshot.sequence() != command.snapshotSequence()
                || currentCommand.isEmpty()
                || !currentCommand.orElseThrow().equals(command)
                || !freshSnapshot.inventory().equals(inventory.snapshot())) {
            return;
        }

        Av02AllocationCandidate candidate = freshSnapshot.candidates().stream()
                .filter(Av02AllocationCandidate::eligible)
                .filter(value -> value.orderSheetKey().equals(command.orderSheetKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fresh AV02 command has no matching eligible candidate"));
        allocate(candidate.order(), candidate.pharmacyId(), context.getSimulationTimeSeconds());
    }

    public Optional<Av02AllocatedTote> lastAllocatedTote() {
        return lastAllocatedTote;
    }

    private void allocate(
            NotionalToteOrder order,
            String pharmacyId,
            double simulationTimeSeconds) {
        if (inventory.full()
                || inventory.snapshot().waitingTotes().stream()
                        .anyMatch(tote -> tote.orderSheetKey().equals(order.orderSheetKey()))
                || inventory.snapshot().departedTotes().stream()
                        .anyMatch(tote -> tote.orderSheetKey().equals(order.orderSheetKey()))
                || lifecycleLedger.activeAssignmentFor(order.orderSheetKey()).isPresent()) {
            return;
        }

        PhysicalToteId allocatedId = idAllocator.nextPhysicalToteId();
        if (allocatedId == null) {
            throw new IllegalStateException("idAllocator returned null");
        }
        if (lifecycleLedger.tote(allocatedId).isPresent()
                || inventory.snapshot().waitingTotes().stream()
                        .anyMatch(tote -> tote.physicalToteId().equals(allocatedId))
                || inventory.snapshot().departedTotes().stream()
                        .anyMatch(tote -> tote.physicalToteId().equals(allocatedId))
                || loadPlanRegistry.getLoadPlanFor(allocatedId) != null) {
            throw new IllegalStateException(
                    "Allocated AV02 physical tote ID is already in use: " + allocatedId.value());
        }

        Duration allocationTime = simulationTime(simulationTimeSeconds);
        PhysicalToteRecord physicalTote = lifecycleController.allocateFor(
                order, allocationTime, allocatedId);
        Av02AllocatedTote allocatedTote = new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        allocatedId,
                        order.orderSheetKey(),
                        order.orderType(),
                        order.serviceCentreId(),
                        PhysicalToteRole.PRE_P2P,
                        order.sequenceNumber()),
                physicalTote,
                pharmacyId);
        try {
            inventory.store(allocatedTote);
            loadPlanRegistry.putLoadPlan(new ToteLoadPlan(allocatedId, List.of()));
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "AV02 allocation invariant failed after lifecycle mutation", failure);
        }
        lastAllocatedTote = Optional.of(allocatedTote);
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
