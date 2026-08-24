package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.DspP2pArrivalConsumerRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalConsumerBinding;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public final class DspP2pStickyLeaseRuntimeFactory {
    public static final int DSP_P2P_LINE_COUNT = 5;

    public DspP2pStickyLeaseRuntime create(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings) {
        return create(
                simulationWorld,
                lineDefinitions,
                activityProbes,
                schedulerSnapshotSupplier,
                manifestCatalog,
                lifecycleSnapshotSupplier,
                outboundToteAllocator,
                arrivalBindings,
                new CompletionOnlyP2pLeaseRetentionPolicy());
    }

    public DspP2pStickyLeaseRuntime create(
            SimulationWorld simulationWorld,
            List<P2pLineDefinition> lineDefinitions,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            OutboundToteAllocator outboundToteAllocator,
            List<P2pStickyArrivalBinding> arrivalBindings,
            P2pLeaseRetentionPolicy retentionPolicy) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(lineDefinitions, "lineDefinitions");
        requireNonNull(activityProbes, "activityProbes");
        requireNonNull(schedulerSnapshotSupplier, "schedulerSnapshotSupplier");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier");
        requireNonNull(outboundToteAllocator, "outboundToteAllocator");
        requireNonNull(arrivalBindings, "arrivalBindings");
        requireNonNull(retentionPolicy, "retentionPolicy");

        if (lineDefinitions.stream().anyMatch(definition -> definition == null)) {
            throw new IllegalArgumentException("lineDefinitions must not contain null");
        }
        List<P2pLineDefinition> definitions = List.copyOf(lineDefinitions);
        if (definitions.size() != DSP_P2P_LINE_COUNT) {
            throw new IllegalArgumentException("Exactly five P2P lines must be configured");
        }
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(definitions);
        Map<P2pLineId, P2pLineActivityProbe> probes = validateProbes(
                definitions, activityProbes);
        List<P2pStickyArrivalBinding> stickyBindings = validateArrivalBindings(
                definitions, arrivalBindings);

        Supplier<P2pLineLeaseCatalogSnapshot> leaseSnapshotSupplier = () ->
                registry.snapshot(activitySnapshots(definitions, probes));
        StrictP2pReleaseAssignmentCommitter committer =
                new StrictP2pReleaseAssignmentCommitter(
                        registry,
                        new WarehouseSnapshotP2pReleaseRequirementResolver(
                                schedulerSnapshotSupplier),
                        probes);
        List<P2pArrivalConsumerBinding> consumerBindings = new ArrayList<>();
        Map<P2pLineId, P2pLineDefinition> definitionsById = indexDefinitions(definitions);
        for (P2pStickyArrivalBinding binding : stickyBindings) {
            P2pLineDefinition definition = definitionsById.get(binding.lineId());
            consumerBindings.add(new P2pArrivalConsumerBinding(
                    binding.sourceQueue(),
                    new StickyP2pArrivalAdmissionPolicy(
                            definition, leaseSnapshotSupplier),
                    binding.routeBinding(),
                    binding.payloadFactory(),
                    binding.target()));
        }

        DspP2pArrivalConsumerRuntime arrivalRuntime =
                new DspP2pArrivalConsumerRuntimeFactory().create(
                        simulationWorld, consumerBindings);
        P2pServiceCentreWorkSnapshotFactory workSnapshotFactory =
                new P2pServiceCentreWorkSnapshotFactory();
        P2pLeaseReleaseController releaseController = new P2pLeaseReleaseController(
                () -> {
                    WarehouseSchedulerSnapshot schedulerSnapshot =
                            schedulerSnapshotSupplier.get();
                    if (schedulerSnapshot == null) {
                        throw new IllegalStateException(
                                "schedulerSnapshotSupplier returned null");
                    }
                    PhysicalToteLifecycleSnapshot lifecycleSnapshot =
                            lifecycleSnapshotSupplier.get();
                    if (lifecycleSnapshot == null) {
                        throw new IllegalStateException(
                                "lifecycleSnapshotSupplier returned null");
                    }
                    return workSnapshotFactory.create(
                            schedulerSnapshot.orderStates(),
                            manifestCatalog,
                            lifecycleSnapshot);
                },
                definitions.stream().map(definition -> probes.get(definition.lineId())).toList(),
                registry,
                outboundToteAllocator,
                retentionPolicy);
        simulationWorld.addController(releaseController);

        return new DspP2pStickyLeaseRuntime(
                definitions,
                probes,
                registry,
                committer,
                outboundToteAllocator,
                arrivalRuntime);
    }

    private static Map<P2pLineId, P2pLineActivityProbe> validateProbes(
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> source) {
        Map<P2pLineId, P2pLineActivityProbe> probes = new LinkedHashMap<>();
        source.forEach((lineId, probe) -> {
            if (lineId == null || probe == null) {
                throw new IllegalArgumentException("activityProbes must not contain null");
            }
            probes.put(lineId, probe);
        });
        Set<P2pLineId> expected = definitions.stream()
                .map(P2pLineDefinition::lineId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!probes.keySet().equals(expected)) {
            throw new IllegalArgumentException(
                    "activityProbes must contain exactly every configured P2P line");
        }
        return Map.copyOf(probes);
    }

    private static List<P2pStickyArrivalBinding> validateArrivalBindings(
            List<P2pLineDefinition> definitions,
            List<P2pStickyArrivalBinding> source) {
        if (source.size() != definitions.size()) {
            throw new IllegalArgumentException(
                    "arrivalBindings must contain exactly every configured P2P line");
        }
        Map<P2pLineId, P2pLineDefinition> definitionsById = indexDefinitions(definitions);
        Set<P2pLineId> seen = new LinkedHashSet<>();
        List<P2pStickyArrivalBinding> bindings = new ArrayList<>();
        for (P2pStickyArrivalBinding binding : source) {
            if (binding == null) {
                throw new IllegalArgumentException("arrivalBindings must not contain null");
            }
            P2pLineDefinition definition = definitionsById.get(binding.lineId());
            if (definition == null || !seen.add(binding.lineId())) {
                throw new IllegalArgumentException(
                        "arrival bindings must identify distinct configured P2P lines");
            }
            if (!binding.sourceQueue().destination().equals(definition.destination())
                    || !binding.target().destination().equals(definition.destination())) {
                throw new IllegalArgumentException(
                        "arrival binding destination must match its P2P line definition");
            }
            bindings.add(binding);
        }
        return List.copyOf(bindings);
    }

    private static Map<P2pLineId, P2pLineDefinition> indexDefinitions(
            List<P2pLineDefinition> definitions) {
        Map<P2pLineId, P2pLineDefinition> result = new LinkedHashMap<>();
        for (P2pLineDefinition definition : definitions) {
            result.put(definition.lineId(), definition);
        }
        return result;
    }

    private static Map<P2pLineId, P2pLineActivitySnapshot> activitySnapshots(
            List<P2pLineDefinition> definitions,
            Map<P2pLineId, P2pLineActivityProbe> probes) {
        Map<P2pLineId, P2pLineActivitySnapshot> result = new LinkedHashMap<>();
        for (P2pLineDefinition definition : definitions) {
            P2pLineActivitySnapshot activity = probes.get(definition.lineId()).snapshot();
            if (activity == null) {
                throw new IllegalStateException("P2P line activity probe returned null");
            }
            result.put(definition.lineId(), activity);
        }
        return result;
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
