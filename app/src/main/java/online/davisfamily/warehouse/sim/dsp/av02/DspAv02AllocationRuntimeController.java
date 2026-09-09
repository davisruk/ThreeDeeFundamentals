package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;

/**
 * Full-day bridge around the existing AV02 command controller.
 *
 * <p>The bridge owns only snapshot sequencing and diagnostics. Allocation mutation remains in
 * {@link Av02AllocationController}, which performs the fresh command revalidation and allocates
 * at most one physical EMPTY tote per simulation update.</p>
 */
public final class DspAv02AllocationRuntimeController implements SimulationController {
    private final Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier;
    private final Supplier<DspSupplySnapshot> supplySnapshotSupplier;
    private final Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier;
    private final Av02PhysicalToteInventory inventory;
    private final PhysicalToteLifecycleLedger lifecycleLedger;
    private final PhysicalToteIdAllocator idAllocator;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final Av02AllocationSnapshotFactory snapshotFactory;
    private final Av02AllocationController allocationController;
    private long nextSequence;
    private long activeSequence = -1L;
    private Av02AllocationSnapshot activeAllocationSnapshot;
    private Av02AllocationSnapshot activeRevalidationSnapshot;
    private DspAv02AllocationRuntimeSnapshot latestSnapshot;

    public DspAv02AllocationRuntimeController(
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Av02PhysicalToteInventory inventory,
            PhysicalToteLifecycleLedger lifecycleLedger,
            PhysicalToteIdAllocator idAllocator,
            MutableToteLoadPlanRegistry loadPlanRegistry) {
        this(
                schedulerSnapshotSupplier,
                supplySnapshotSupplier,
                lifecycleSnapshotSupplier,
                inventory,
                lifecycleLedger,
                idAllocator,
                loadPlanRegistry,
                new Av02AllocationSnapshotFactory());
    }

    public DspAv02AllocationRuntimeController(
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Av02PhysicalToteInventory inventory,
            PhysicalToteLifecycleLedger lifecycleLedger,
            PhysicalToteIdAllocator idAllocator,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            Av02AllocationSnapshotFactory snapshotFactory) {
        requireNonNull(schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        requireNonNull(supplySnapshotSupplier, "supplySnapshotSupplier");
        requireNonNull(lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        requireNonNull(inventory, "inventory");
        requireNonNull(lifecycleLedger, "lifecycleLedger");
        requireNonNull(idAllocator, "idAllocator");
        requireNonNull(loadPlanRegistry, "loadPlanRegistry");
        requireNonNull(snapshotFactory, "snapshotFactory");
        this.schedulerSnapshotSupplier = schedulerSnapshotSupplier;
        this.supplySnapshotSupplier = supplySnapshotSupplier;
        this.lifecycleSnapshotSupplier = lifecycleSnapshotSupplier;
        this.inventory = inventory;
        this.lifecycleLedger = lifecycleLedger;
        this.idAllocator = idAllocator;
        this.loadPlanRegistry = loadPlanRegistry;
        this.snapshotFactory = snapshotFactory;

        this.allocationController = new Av02AllocationController(
                () -> activeAllocationSnapshot == null
                        ? Optional.empty()
                        : activeAllocationSnapshot.command(),
                () -> {
                    if (activeSequence < 0L) {
                        throw new IllegalStateException(
                                "AV02 allocation revalidation requested outside an update");
                    }
                    activeRevalidationSnapshot = buildSnapshot(activeSequence);
                    return activeRevalidationSnapshot;
                },
                inventory,
                lifecycleLedger,
                idAllocator,
                loadPlanRegistry);

        Av02AllocationSnapshot initial = buildSnapshot(0L);
        this.latestSnapshot = runtimeSnapshot(
                initial,
                Optional.empty(),
                initial.command(),
                Optional.empty());
        this.nextSequence = 1L;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        long sequence = nextSequence++;
        activeSequence = sequence;
        activeAllocationSnapshot = buildSnapshot(sequence);
        activeRevalidationSnapshot = null;
        try {
            allocationController.update(context, dtSeconds);
            Optional<Av02AllocationSnapshot> revalidationSnapshot =
                    Optional.ofNullable(activeRevalidationSnapshot);
            latestSnapshot = runtimeSnapshot(
                    activeAllocationSnapshot,
                    revalidationSnapshot,
                    activeAllocationSnapshot.command(),
                    allocationController.lastAllocatedTote());
        } finally {
            activeSequence = -1L;
            activeAllocationSnapshot = null;
            activeRevalidationSnapshot = null;
        }
    }

    /** Returns the existing command controller that owns allocation mutation. */
    public Av02AllocationController allocationController() {
        if (allocationController == null) {
            throw new IllegalStateException("allocation controller has not been initialized");
        }
        return allocationController;
    }

    public Optional<Av02AllocatedTote> lastAllocatedTote() {
        return allocationController.lastAllocatedTote();
    }

    public DspAv02AllocationRuntimeSnapshot snapshot() {
        return latestSnapshot;
    }

    private Av02AllocationSnapshot buildSnapshot(long sequence) {
        WarehouseSchedulerSnapshot scheduler = requireSupplied(
                schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        DspSupplySnapshot supply = requireSupplied(
                supplySnapshotSupplier, "supplySnapshotSupplier");
        PhysicalToteLifecycleSnapshot lifecycle = requireSupplied(
                lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        return snapshotFactory.create(sequence, scheduler, supply, inventory.snapshot(), lifecycle);
    }

    private DspAv02AllocationRuntimeSnapshot runtimeSnapshot(
            Av02AllocationSnapshot allocationSnapshot,
            Optional<Av02AllocationSnapshot> revalidationSnapshot,
            Optional<AllocateEmptyToteAtAv02Command> command,
            Optional<Av02AllocatedTote> lastAllocatedTote) {
        String diagnostic = "";
        if (command.isEmpty() && !allocationSnapshot.candidates().isEmpty()) {
            diagnostic = allocationSnapshot.candidates().stream()
                    .filter(candidate -> !candidate.eligible())
                    .findFirst()
                    .map(candidate -> candidate.orderSheetKey() + ": "
                            + candidate.blockReasons().getFirst().name())
                    .orElse("");
        }
        return new DspAv02AllocationRuntimeSnapshot(
                allocationSnapshot.sequence(),
                allocationSnapshot,
                revalidationSnapshot,
                command,
                lastAllocatedTote,
                command.isEmpty() && !diagnostic.isEmpty(),
                diagnostic);
    }

    private static <T> T requireSupplied(Supplier<T> supplier, String fieldName) {
        T value = supplier.get();
        if (value == null) {
            throw new IllegalStateException(fieldName + " returned null");
        }
        return value;
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
