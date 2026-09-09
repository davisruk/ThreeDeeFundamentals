package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCompletionEvaluator;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayCutoffController;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsCollector;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.DspAv02AllocationRuntimeController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.continuation.DspStationRouteContinuationRuntime;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntime;
import online.davisfamily.warehouse.sim.dsp.supply.DspServiceCentreSupplyController;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockController;
import online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntime;

/** One source-neutral, headless operational-day simulation composition. */
public final class DspFullDayAnalysisRuntime implements AutoCloseable {
    private final SimulationWorld simulationWorld;
    private final DspOperationalClockController clockController;
    private final DspSchedulerRuntimeState schedulerRuntimeState;
    private final DspServiceCentreSupplyController supplyController;
    private final InboundToteManifestCatalog manifestCatalog;
    private final MutableToteLoadPlanRegistry loadPlanRegistry;
    private final PhysicalToteLifecycleLedger lifecycleLedger;
    private final OsrPhysicalInventory osrInventory;
    private final Av02PhysicalToteInventory av02Inventory;
    private final OutboundToteAllocator outboundToteAllocator;
    private final Supplier<OsrInventorySnapshot> osrInventorySnapshot;
    private final Supplier<Av02InventorySnapshot> av02InventorySnapshot;
    private final Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshot;
    private final DspAv02AllocationRuntimeController av02AllocationController;
    private final DspP2pElasticAllocationRuntime elasticRuntime;
    private final DspOperationalReleaseRuntime operationalReleaseRuntime;
    private final DspWarehouseTransportRuntime transportRuntime;
    private final DspStationProcessingRuntime stationProcessingRuntime;
    private final DspStationRouteContinuationRuntime continuationRuntime;
    private final List<DspHeadlessP2pLineRuntime> lineRuntimes;
    private final DspFullDayCutoffController cutoffController;
    private final DspFullDayCompletionEvaluator completionEvaluator;
    private final Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotSupplier;
    private final DspFullDayMetricsCollector metricsCollector;
    private final java.util.concurrent.atomic.AtomicReference<DspFullDayRuntimeState> state;
    private boolean closed;

    DspFullDayAnalysisRuntime(
            SimulationWorld simulationWorld,
            DspOperationalClockController clockController,
            DspSchedulerRuntimeState schedulerRuntimeState,
            DspServiceCentreSupplyController supplyController,
            InboundToteManifestCatalog manifestCatalog,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            PhysicalToteLifecycleLedger lifecycleLedger,
            OsrPhysicalInventory osrInventory,
            Av02PhysicalToteInventory av02Inventory,
            OutboundToteAllocator outboundToteAllocator,
            Supplier<OsrInventorySnapshot> osrInventorySnapshot,
            Supplier<Av02InventorySnapshot> av02InventorySnapshot,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshot,
            DspAv02AllocationRuntimeController av02AllocationController,
            DspP2pElasticAllocationRuntime elasticRuntime,
            DspOperationalReleaseRuntime operationalReleaseRuntime,
            DspWarehouseTransportRuntime transportRuntime,
            DspStationProcessingRuntime stationProcessingRuntime,
            DspStationRouteContinuationRuntime continuationRuntime,
            List<DspHeadlessP2pLineRuntime> lineRuntimes,
            DspFullDayCutoffController cutoffController,
            DspFullDayCompletionEvaluator completionEvaluator,
            Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotSupplier,
            DspFullDayMetricsCollector metricsCollector,
            java.util.concurrent.atomic.AtomicReference<DspFullDayRuntimeState> state) {
        this.simulationWorld = Objects.requireNonNull(simulationWorld);
        this.clockController = Objects.requireNonNull(clockController);
        this.schedulerRuntimeState = Objects.requireNonNull(schedulerRuntimeState);
        this.supplyController = Objects.requireNonNull(supplyController);
        this.manifestCatalog = Objects.requireNonNull(manifestCatalog);
        this.loadPlanRegistry = Objects.requireNonNull(loadPlanRegistry);
        this.lifecycleLedger = Objects.requireNonNull(lifecycleLedger);
        this.osrInventory = Objects.requireNonNull(osrInventory);
        this.av02Inventory = Objects.requireNonNull(av02Inventory);
        this.outboundToteAllocator = Objects.requireNonNull(outboundToteAllocator);
        this.osrInventorySnapshot = Objects.requireNonNull(osrInventorySnapshot);
        this.av02InventorySnapshot = Objects.requireNonNull(av02InventorySnapshot);
        this.lifecycleSnapshot = Objects.requireNonNull(lifecycleSnapshot);
        this.av02AllocationController = Objects.requireNonNull(av02AllocationController);
        this.elasticRuntime = Objects.requireNonNull(elasticRuntime);
        this.operationalReleaseRuntime = Objects.requireNonNull(operationalReleaseRuntime);
        this.transportRuntime = Objects.requireNonNull(transportRuntime);
        this.stationProcessingRuntime = Objects.requireNonNull(stationProcessingRuntime);
        this.continuationRuntime = Objects.requireNonNull(continuationRuntime);
        this.lineRuntimes = List.copyOf(lineRuntimes);
        this.cutoffController = Objects.requireNonNull(cutoffController);
        this.completionEvaluator = Objects.requireNonNull(completionEvaluator);
        this.completionSnapshotSupplier = Objects.requireNonNull(completionSnapshotSupplier);
        this.metricsCollector = Objects.requireNonNull(metricsCollector);
        this.state = Objects.requireNonNull(state);
    }

    public SimulationWorld simulationWorld() {
        return simulationWorld;
    }

    public DspOperationalClockController clockController() {
        return clockController;
    }

    public DspSchedulerRuntimeState schedulerRuntimeState() {
        return schedulerRuntimeState;
    }

    public DspServiceCentreSupplyController supplyController() {
        return supplyController;
    }

    public InboundToteManifestCatalog manifestCatalog() {
        return manifestCatalog;
    }

    public MutableToteLoadPlanRegistry loadPlanRegistry() {
        return loadPlanRegistry;
    }

    public PhysicalToteLifecycleLedger lifecycleLedger() {
        return lifecycleLedger;
    }

    public OsrPhysicalInventory osrInventory() {
        return osrInventory;
    }

    public Av02PhysicalToteInventory av02Inventory() {
        return av02Inventory;
    }

    public OutboundToteAllocator outboundToteAllocator() {
        return outboundToteAllocator;
    }

    public Supplier<OsrInventorySnapshot> osrInventorySnapshotSupplier() {
        return osrInventorySnapshot;
    }

    public Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier() {
        return av02InventorySnapshot;
    }

    public Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier() {
        return lifecycleSnapshot;
    }

    public DspAv02AllocationRuntimeController av02AllocationRuntimeController() {
        return av02AllocationController;
    }

    public DspP2pElasticAllocationRuntime elasticRuntime() {
        return elasticRuntime;
    }

    public DspOperationalReleaseRuntime operationalReleaseRuntime() {
        return operationalReleaseRuntime;
    }

    public DspWarehouseTransportRuntime transportRuntime() {
        return transportRuntime;
    }

    public DspStationProcessingRuntime stationProcessingRuntime() {
        return stationProcessingRuntime;
    }

    public DspStationRouteContinuationRuntime continuationRuntime() {
        return continuationRuntime;
    }

    public List<DspHeadlessP2pLineRuntime> lineRuntimes() {
        return lineRuntimes;
    }

    public DspFullDayCutoffController cutoffController() {
        return cutoffController;
    }

    public DspFullDayCompletionEvaluator completionEvaluator() {
        return completionEvaluator;
    }

    public DspFullDayRuntimeState state() {
        return state.get();
    }

    public List<DspServiceCentreCompletionSnapshot> completionSnapshots() {
        return List.copyOf(completionSnapshotSupplier.get());
    }

    public DspFullDayMetricsCollector metricsCollector() {
        return metricsCollector;
    }

    public DspFullDayMetricsSnapshot metricsSnapshot() {
        return metricsCollector.snapshot();
    }

    public void update(double dtSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }
        if (closed || state() != DspFullDayRuntimeState.RUNNING) {
            return;
        }
        simulationWorld.update(dtSeconds);
    }

    public DspFullDayAnalysisRuntimeSnapshot snapshot() {
        return DspFullDayAnalysisRuntimeSnapshotFactory.create(this);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        failure = closeOne(continuationRuntime, failure);
        failure = closeOne(stationProcessingRuntime, failure);
        failure = closeOne(transportRuntime, failure);
        failure = closeOne(operationalReleaseRuntime, failure);
        failure = closeOne(elasticRuntime, failure);
        for (int index = lineRuntimes.size() - 1; index >= 0; index--) {
            failure = closeOne(lineRuntimes.get(index), failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private static RuntimeException closeOne(AutoCloseable closeable, RuntimeException failure) {
        try {
            closeable.close();
        } catch (Exception exception) {
            RuntimeException wrapped = exception instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Unable to close full-day runtime component", exception);
            if (failure == null) {
                return wrapped;
            }
            failure.addSuppressed(wrapped);
        }
        return failure;
    }

    /** Internal bridge used by the snapshot factory without exposing mutable implementation state. */
    static DspFullDayAnalysisRuntimeSnapshot buildSnapshot(DspFullDayAnalysisRuntime runtime) {
        return new DspFullDayAnalysisRuntimeSnapshot(
                runtime.state(),
                runtime.clockController.snapshot(),
                runtime.schedulerRuntimeState.snapshot(),
                runtime.supplyController.snapshot(),
                runtime.osrInventorySnapshot.get(),
                runtime.av02InventorySnapshot.get(),
                runtime.lifecycleSnapshot.get(),
                runtime.av02AllocationController.snapshot(),
                runtime.operationalReleaseRuntime.controller().snapshot(),
                runtime.elasticRuntime.operationalSnapshot(),
                runtime.lineRuntimes.stream().map(DspHeadlessP2pLineRuntime::snapshot).toList(),
                runtime.stationProcessingRuntime.coordinatorSnapshot(),
                runtime.stationProcessingRuntime.claimantSnapshots(),
                runtime.transportRuntime.routeCatalogSnapshot(),
                runtime.transportRuntime.outboundTransportSnapshot(),
                runtime.transportRuntime.inFlightSnapshot(),
                runtime.transportRuntime.ingressController().snapshot(),
                runtime.transportRuntime.arrivalController().snapshot(),
                runtime.transportRuntime.stationArrivalSnapshots(),
                runtime.continuationRuntime.snapshot(),
                runtime.completionSnapshots(),
                runtime.cutoffController.snapshot(),
                runtime.metricsCollector.snapshot(),
                runtime.closed);
    }
}
