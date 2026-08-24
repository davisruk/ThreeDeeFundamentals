package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pStickyArrivalBinding;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class DspP2pElasticAllocationRuntimeFactory {

    public DspP2pElasticAllocationRuntime create(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings,
            P2pElasticAllocationConfig config) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(lineDefinitions, "lineDefinitions");
        requireNonNull(activityProbes, "activityProbes");
        requireNonNull(schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(supplySnapshotSupplier, "supplySnapshotSupplier");
        requireNonNull(timetable, "timetable");
        requireNonNull(bagPlanningResultSupplier, "bagPlanningResultSupplier");
        requireNonNull(outboundToteAllocator, "outboundToteAllocator");
        requireNonNull(arrivalBindings, "arrivalBindings");
        requireNonNull(config, "config");
        if (lineDefinitions.size() != DspP2pStickyLeaseRuntimeFactory.DSP_P2P_LINE_COUNT
                || config.p2pLineCount()
                        != DspP2pStickyLeaseRuntimeFactory.DSP_P2P_LINE_COUNT) {
            throw new IllegalArgumentException("Elastic DSP composition requires exactly five lines");
        }

        WarehouseSchedulerSnapshot initialScheduler = requireSupplied(
                schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        PhysicalToteLifecycleSnapshot initialLifecycle = requireSupplied(
                lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        requireSupplied(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireSupplied(supplySnapshotSupplier, "supplySnapshotSupplier");
        requireSupplied(bagPlanningResultSupplier, "bagPlanningResultSupplier");

        P2pServiceCentreWorkSnapshotFactory workFactory =
                new P2pServiceCentreWorkSnapshotFactory();
        P2pWorkloadSnapshotFactory workloadFactory = new P2pWorkloadSnapshotFactory();
        DeadlineAwareElasticP2pAllocationPlanner planner =
                new DeadlineAwareElasticP2pAllocationPlanner();
        Function<P2pLineLeaseCatalogSnapshot, P2pElasticAllocationSnapshot> allocationFactory =
                leases -> {
                    WarehouseSchedulerSnapshot scheduler = requireSupplied(
                            schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
                    PhysicalToteLifecycleSnapshot lifecycle = requireSupplied(
                            lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
                    P2pServiceCentreWorkSnapshot work = workFactory.create(
                            scheduler.orderStates(), manifestCatalog, lifecycle);
                    P2pWorkloadSnapshot workload = workloadFactory.create(
                            work,
                            manifestCatalog,
                            requireSupplied(
                                    bagPlanningResultSupplier,
                                    "bagPlanningResultSupplier"),
                            outboundToteAllocator.snapshot(),
                            config.workloadCostConfig());
                    return planner.create(
                            requireSupplied(clockSnapshotSupplier, "clockSnapshotSupplier"),
                            requireSupplied(supplySnapshotSupplier, "supplySnapshotSupplier"),
                            workload,
                            timetable,
                            leases,
                            config);
                };

        P2pLineLeaseCatalogSnapshot initialLeases = initialLeaseSnapshot(
                lineDefinitions, activityProbes);
        P2pServiceCentreWorkSnapshot initialWork = workFactory.create(
                initialScheduler.orderStates(), manifestCatalog, initialLifecycle);
        if (initialWork == null || allocationFactory.apply(initialLeases) == null) {
            throw new IllegalStateException("elastic runtime prevalidation returned null");
        }

        DspP2pStickyLeaseRuntime leaseRuntime = new DspP2pStickyLeaseRuntimeFactory().create(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                outboundToteAllocator,
                arrivalBindings,
                new ElasticP2pLeaseRetentionPolicy(allocationFactory));
        return new DspP2pElasticAllocationRuntime(leaseRuntime, allocationFactory);
    }

    private static P2pLineLeaseCatalogSnapshot initialLeaseSnapshot(
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> probes) {
        if (definitions.stream().anyMatch(definition -> definition == null)) {
            throw new IllegalArgumentException("lineDefinitions must not contain null");
        }
        Set<P2pLineId> expected = definitions.stream()
                .map(P2pLineDefinition::lineId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!probes.keySet().equals(expected)
                || probes.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "activityProbes must contain exactly every configured P2P line");
        }
        List<P2pLineLeaseSnapshot> lines = definitions.stream()
                .map(definition -> {
                    var activity = probes.get(definition.lineId()).snapshot();
                    if (activity == null) {
                        throw new IllegalStateException("P2P line activity probe returned null");
                    }
                    return new P2pLineLeaseSnapshot(
                            definition, java.util.Optional.empty(), activity, List.of());
                })
                .toList();
        return new P2pLineLeaseCatalogSnapshot(lines);
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
