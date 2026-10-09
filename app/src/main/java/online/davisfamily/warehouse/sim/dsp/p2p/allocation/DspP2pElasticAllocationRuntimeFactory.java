package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
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
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pStickyArrivalBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedger;
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
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings,
            P2pElasticAllocationConfig config) {
        return create(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                clockSnapshotSupplier,
                supplySnapshotSupplier,
                timetable,
                bagPlanningResultSupplier,
                outboundToteAllocator,
                arrivalBindings,
                config,
                P2pBagCorrelationRequirementCatalog.empty(),
                new P2pBagCorrelationAssignmentRegistry());
    }

    public DspP2pElasticAllocationRuntime create(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings,
            P2pElasticAllocationConfig config,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignmentRegistry) {
        return createInternal(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                clockSnapshotSupplier,
                supplySnapshotSupplier,
                timetable,
                bagPlanningResultSupplier,
                outboundToteAllocator,
                arrivalBindings,
                config,
                requirementCatalog,
                correlationAssignmentRegistry,
                P2pMissingPackSnapshot::empty,
                true,
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                Optional.empty());
    }

    /** Creates the elastic runtime while leaving station-arrival ownership to the caller. */
    public DspP2pElasticAllocationRuntime createWithoutArrivalConsumers(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            P2pElasticAllocationConfig config,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignmentRegistry) {
        return createWithoutArrivalConsumers(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                clockSnapshotSupplier,
                supplySnapshotSupplier,
                timetable,
                bagPlanningResultSupplier,
                outboundToteAllocator,
                config,
                requirementCatalog,
                correlationAssignmentRegistry,
                P2pMissingPackSnapshot::empty);
    }

    /** Creates the caller-owned-arrival runtime with immutable exception publication. */
    public DspP2pElasticAllocationRuntime createWithoutArrivalConsumers(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            P2pElasticAllocationConfig config,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignmentRegistry,
            Supplier<P2pMissingPackSnapshot> missingPackSnapshotSupplier) {
        return createWithoutArrivalConsumers(simulationWorld, lineDefinitions, activityProbes,
                schedulerSnapshotSupplier, manifestCatalog, lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier, clockSnapshotSupplier, supplySnapshotSupplier,
                timetable, bagPlanningResultSupplier, outboundToteAllocator, config,
                requirementCatalog, correlationAssignmentRegistry, missingPackSnapshotSupplier,
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES, Optional.empty());
    }

    public DspP2pElasticAllocationRuntime createWithoutArrivalConsumers(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            P2pElasticAllocationConfig config,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignmentRegistry,
            Supplier<P2pMissingPackSnapshot> missingPackSnapshotSupplier,
            DspSchedulerPolicy policy,
            Optional<WholeServiceCentreReleaseLedger> releaseLedger) {
        return createInternal(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                clockSnapshotSupplier,
                supplySnapshotSupplier,
                timetable,
                bagPlanningResultSupplier,
                outboundToteAllocator,
                List.of(),
                config,
                requirementCatalog,
                correlationAssignmentRegistry,
                missingPackSnapshotSupplier,
                false, policy, releaseLedger);
    }

    private DspP2pElasticAllocationRuntime createInternal(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02InventorySnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            DspServiceCentreTimetable timetable,
            Supplier<BagPlanningResult> bagPlanningResultSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings,
            P2pElasticAllocationConfig config,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry correlationAssignmentRegistry,
            Supplier<P2pMissingPackSnapshot> missingPackSnapshotSupplier,
            boolean registerArrivalConsumers,
            DspSchedulerPolicy policy,
            Optional<WholeServiceCentreReleaseLedger> releaseLedger) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(lineDefinitions, "lineDefinitions");
        requireNonNull(activityProbes, "activityProbes");
        requireNonNull(schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        requireNonNull(av02InventorySnapshotSupplier, "av02InventorySnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(supplySnapshotSupplier, "supplySnapshotSupplier");
        requireNonNull(timetable, "timetable");
        requireNonNull(bagPlanningResultSupplier, "bagPlanningResultSupplier");
        requireNonNull(outboundToteAllocator, "outboundToteAllocator");
        requireNonNull(arrivalBindings, "arrivalBindings");
        requireNonNull(config, "config");
        requireNonNull(requirementCatalog, "requirementCatalog");
        requireNonNull(correlationAssignmentRegistry, "correlationAssignmentRegistry");
        requireNonNull(missingPackSnapshotSupplier, "missingPackSnapshotSupplier");
        requireNonNull(policy, "policy");
        requireNonNull(releaseLedger, "releaseLedger");
        boolean wholeCentre = policy == DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER;
        if (wholeCentre != releaseLedger.isPresent()) {
            throw new IllegalArgumentException("Whole-centre policy requires its ledger; legacy policy forbids it");
        }
        if (lineDefinitions.size() != DspP2pStickyLeaseRuntimeFactory.DSP_P2P_LINE_COUNT
                || config.p2pLineCount()
                        != DspP2pStickyLeaseRuntimeFactory.DSP_P2P_LINE_COUNT) {
            throw new IllegalArgumentException("Elastic DSP composition requires exactly five lines");
        }

        WarehouseSchedulerSnapshot initialScheduler = requireSupplied(
                schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        PhysicalToteLifecycleSnapshot initialLifecycle = requireSupplied(
                lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        Av02InventorySnapshot initialAv02Inventory = requireSupplied(
                av02InventorySnapshotSupplier, "av02InventorySnapshotSupplier");
        requireSupplied(clockSnapshotSupplier, "clockSnapshotSupplier");
        DspSupplySnapshot initialSupply = requireSupplied(
                supplySnapshotSupplier, "supplySnapshotSupplier");
        requireSupplied(bagPlanningResultSupplier, "bagPlanningResultSupplier");
        requireSupplied(missingPackSnapshotSupplier, "missingPackSnapshotSupplier");

        P2pServiceCentreWorkSnapshotFactory workFactory =
                new P2pServiceCentreWorkSnapshotFactory();
        P2pWorkloadSnapshotFactory workloadFactory = new P2pWorkloadSnapshotFactory();
        DeadlineAwareElasticP2pAllocationPlanner planner = wholeCentre ? null
                : new DeadlineAwareElasticP2pAllocationPlanner();
        WholeServiceCentreP2pAllocationPlanner wholePlanner = wholeCentre
                ? new WholeServiceCentreP2pAllocationPlanner() : null;
        Function<P2pLineLeaseCatalogSnapshot, P2pElasticAllocationSnapshot> allocationFactory =
                leases -> {
                    WarehouseSchedulerSnapshot scheduler = requireSupplied(
                            schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
                    PhysicalToteLifecycleSnapshot lifecycle = requireSupplied(
                            lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
                    Av02InventorySnapshot av02Inventory = requireSupplied(
                            av02InventorySnapshotSupplier, "av02InventorySnapshotSupplier");
                    DspSupplySnapshot supply = requireSupplied(
                            supplySnapshotSupplier, "supplySnapshotSupplier");
                    P2pServiceCentreWorkSnapshot work = workFactory.create(
                            scheduler.orderStates(),
                            manifestCatalog,
                            av02Inventory,
                            lifecycle,
                            supply.authorizedEmptyOrderSheetKeys());
                    P2pWorkloadSnapshot workload = workloadFactory.create(
                            work,
                            manifestCatalog,
                            requireSupplied(
                                    bagPlanningResultSupplier,
                                    "bagPlanningResultSupplier"),
                            outboundToteAllocator.snapshot(),
                            config.workloadCostConfig(),
                            av02Inventory,
                            lifecycle,
                            requireSupplied(
                                    missingPackSnapshotSupplier,
                                    "missingPackSnapshotSupplier"));
                    DspOperationalClockSnapshot clock = requireSupplied(
                            clockSnapshotSupplier, "clockSnapshotSupplier");
                    if (wholeCentre) {
                        return wholePlanner.create(clock, supply, workload, timetable, leases,
                                releaseLedger.orElseThrow().snapshot(), config.downstreamHandlingDuration());
                    }
                    return planner.create(
                            clock,
                            supply,
                            workload,
                            timetable,
                            leases,
                            config);
                };

        P2pLineLeaseCatalogSnapshot initialLeases = initialLeaseSnapshot(
                lineDefinitions, activityProbes);
        P2pServiceCentreWorkSnapshot initialWork = workFactory.create(
                initialScheduler.orderStates(),
                manifestCatalog,
                initialAv02Inventory,
                initialLifecycle,
                initialSupply.authorizedEmptyOrderSheetKeys());
        if (initialWork == null || allocationFactory.apply(initialLeases) == null) {
            throw new IllegalStateException("elastic runtime prevalidation returned null");
        }

        P2pLeaseRetentionPolicy retention = wholeCentre
                ? new WholeServiceCentreP2pLeaseRetentionPolicy(releaseLedger.orElseThrow()::snapshot)
                : new ElasticP2pLeaseRetentionPolicy(allocationFactory);
        DspP2pStickyLeaseRuntime leaseRuntime = registerArrivalConsumers
                ? new DspP2pStickyLeaseRuntimeFactory().create(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                () -> requireSupplied(
                        supplySnapshotSupplier,
                        "supplySnapshotSupplier").authorizedEmptyOrderSheetKeys(),
                outboundToteAllocator,
                arrivalBindings,
                retention,
                requirementCatalog,
                correlationAssignmentRegistry)
                : new DspP2pStickyLeaseRuntimeFactory().createWithoutArrivalConsumers(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                av02InventorySnapshotSupplier,
                () -> requireSupplied(
                        supplySnapshotSupplier,
                        "supplySnapshotSupplier").authorizedEmptyOrderSheetKeys(),
                outboundToteAllocator,
                retention,
                requirementCatalog,
                correlationAssignmentRegistry);
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
