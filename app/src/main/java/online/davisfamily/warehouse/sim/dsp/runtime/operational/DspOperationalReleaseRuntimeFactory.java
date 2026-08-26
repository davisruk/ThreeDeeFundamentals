package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalCommandHandler;
import online.davisfamily.warehouse.sim.dsp.av02.Av02PhysicalToteInventory;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandler;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.DspP2pStickyLeaseRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntrySelector;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class DspOperationalReleaseRuntimeFactory {

    public DspOperationalReleaseRuntime createElastic(
            OperationalReleaseEvaluationSource evaluationSource,
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog,
            DspP2pElasticAllocationRuntime elasticRuntime) {
        requireNonNull(elasticRuntime, "elasticRuntime");
        requireNonNull(evaluationSource, "evaluationSource");
        requireNonNull(inventory, "inventory");
        requireNonNull(lifecycleController, "lifecycleController");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(logicalSnapshotSupplier, "logicalSnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(stationAdmissionResolver, "stationAdmissionResolver");
        requireNonNull(routeTargetAdmissionCatalog, "routeTargetAdmissionCatalog");
        if (!evaluationSource.p2pAllocationProfileId()
                .filter(P2pElasticAllocationSnapshot
                        .DEADLINE_AWARE_ELASTIC_STICKY_LEASES::equals)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Elastic runtime requires an elastic P2P evaluation source");
        }
        validateP2pTargets(routeTargetAdmissionCatalog, elasticRuntime.lineDefinitions());

        OsrProcessingReleaseSnapshotFactory physicalSnapshotFactory =
                new OsrProcessingReleaseSnapshotFactory();
        DspOperationalReleaseSnapshotFactory operationalSnapshotFactory =
                new DspOperationalReleaseSnapshotFactory();
        OperationalCandidateRouteAdmissionFactory routeAdmissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        stationAdmissionResolver,
                        routeTargetAdmissionCatalog);

        Supplier<DspOperationalReleaseSnapshot> operationalSnapshotSupplier = () -> {
            WarehouseSchedulerSnapshot logicalSnapshot = logicalSnapshotSupplier.get();
            if (logicalSnapshot == null) {
                throw new IllegalStateException("logicalSnapshotSupplier returned null");
            }
            OsrProcessingReleaseSnapshot physicalSnapshot = physicalSnapshotFactory.create(
                    inventory.snapshot(), lifecycleController.snapshot());
            var elasticSnapshot = elasticRuntime.operationalSnapshot();
            return operationalSnapshotFactory.create(
                    physicalSnapshot,
                    manifestCatalog,
                    logicalSnapshot,
                    routeAdmissionFactory,
                    elasticSnapshot.leases(),
                    routeTargetAdmissionCatalog.snapshotAdmissions().stream()
                            .filter(admission -> admission.stationType() == StationType.P2P)
                            .toList(),
                    elasticSnapshot.allocation());
        };

        OsrProcessingReleaseCommandHandler commandHandler =
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycleController,
                        clockSnapshotSupplier,
                        routeTargetAdmissionCatalog.processingReleaseTargetRegistry(),
                        elasticRuntime.releaseAssignmentCommitter());
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                evaluationSource,
                operationalSnapshotSupplier,
                commandHandler);
        return new DspOperationalReleaseRuntime(controller, routeTargetAdmissionCatalog);
    }

    public DspOperationalReleaseRuntime createElasticWithAv02(
            OperationalReleaseEvaluationSource evaluationSource,
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            StationAdmissionResolver stationAdmissionResolver,
            OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry,
            Av02PhysicalToteInventory av02Inventory,
            PhysicalToteLifecycleLedger lifecycleLedger,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            DspP2pElasticAllocationRuntime elasticRuntime) {
        requireNonNull(elasticRuntime, "elasticRuntime");
        requireNonNull(evaluationSource, "evaluationSource");
        requireNonNull(inventory, "inventory");
        requireNonNull(lifecycleController, "lifecycleController");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(logicalSnapshotSupplier, "logicalSnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(stationAdmissionResolver, "stationAdmissionResolver");
        requireNonNull(routeTargetRegistry, "routeTargetRegistry");
        requireNonNull(av02Inventory, "av02Inventory");
        requireNonNull(lifecycleLedger, "lifecycleLedger");
        requireNonNull(loadPlanRegistry, "loadPlanRegistry");
        if (!evaluationSource.p2pAllocationProfileId()
                .filter(P2pElasticAllocationSnapshot
                        .DEADLINE_AWARE_ELASTIC_STICKY_LEASES::equals)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Elastic runtime requires an elastic P2P evaluation source");
        }
        validateP2pTargets(routeTargetRegistry, elasticRuntime.lineDefinitions());
        validateReleaseTargets(routeTargetRegistry);

        OsrProcessingReleaseSnapshotFactory physicalSnapshotFactory =
                new OsrProcessingReleaseSnapshotFactory();
        DspOperationalReleaseSnapshotFactory operationalSnapshotFactory =
                new DspOperationalReleaseSnapshotFactory();
        OperationalCandidateRouteAdmissionFactory routeAdmissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        stationAdmissionResolver,
                        routeTargetRegistry);

        Supplier<DspOperationalReleaseSnapshot> operationalSnapshotSupplier = () -> {
            WarehouseSchedulerSnapshot logicalSnapshot = logicalSnapshotSupplier.get();
            if (logicalSnapshot == null) {
                throw new IllegalStateException("logicalSnapshotSupplier returned null");
            }
            OsrProcessingReleaseSnapshot physicalSnapshot = physicalSnapshotFactory.create(
                    inventory.snapshot(), lifecycleController.snapshot());
            Av02InventorySnapshot av02InventorySnapshot = av02Inventory.snapshot();
            var elasticSnapshot = elasticRuntime.operationalSnapshot();
            return operationalSnapshotFactory.create(
                    physicalSnapshot,
                    manifestCatalog,
                    av02InventorySnapshot,
                    logicalSnapshot,
                    routeAdmissionFactory,
                    elasticSnapshot.leases(),
                    routeTargetRegistry.snapshotAdmissions().stream()
                            .filter(admission -> admission.stationType() == StationType.P2P)
                            .toList(),
                    elasticSnapshot.allocation());
        };

        OsrProcessingReleaseCommandHandler osrHandler =
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycleController,
                        clockSnapshotSupplier,
                        routeTargetRegistry.processingReleaseTargetRegistry(),
                        elasticRuntime.releaseAssignmentCommitter());
        Av02OperationalCommandHandler av02Handler = new Av02OperationalCommandHandler(
                av02Inventory,
                lifecycleLedger,
                loadPlanRegistry,
                clockSnapshotSupplier,
                routeTargetRegistry.operationalPhysicalToteReleaseTargetRegistry(),
                elasticRuntime.operationalReleaseAssignmentCommitter());
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                evaluationSource,
                operationalSnapshotSupplier,
                new CompositeOperationalCommandHandler(osrHandler, av02Handler));
        return new DspOperationalReleaseRuntime(controller, routeTargetRegistry);
    }

    public DspOperationalReleaseRuntime createSticky(
            OperationalReleaseEvaluationSource evaluationSource,
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog,
            DspP2pStickyLeaseRuntime stickyLeaseRuntime) {
        requireNonNull(stickyLeaseRuntime, "stickyLeaseRuntime");
        requireNonNull(evaluationSource, "evaluationSource");
        requireNonNull(inventory, "inventory");
        requireNonNull(lifecycleController, "lifecycleController");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(logicalSnapshotSupplier, "logicalSnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(stationAdmissionResolver, "stationAdmissionResolver");
        requireNonNull(routeTargetAdmissionCatalog, "routeTargetAdmissionCatalog");
        validateP2pTargets(routeTargetAdmissionCatalog, stickyLeaseRuntime.lineDefinitions());

        OsrProcessingReleaseSnapshotFactory physicalSnapshotFactory =
                new OsrProcessingReleaseSnapshotFactory();
        DspOperationalReleaseSnapshotFactory operationalSnapshotFactory =
                new DspOperationalReleaseSnapshotFactory();
        OperationalCandidateRouteAdmissionFactory routeAdmissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        stationAdmissionResolver,
                        routeTargetAdmissionCatalog);

        Supplier<DspOperationalReleaseSnapshot> operationalSnapshotSupplier = () -> {
            WarehouseSchedulerSnapshot logicalSnapshot = logicalSnapshotSupplier.get();
            if (logicalSnapshot == null) {
                throw new IllegalStateException("logicalSnapshotSupplier returned null");
            }
            OsrProcessingReleaseSnapshot physicalSnapshot = physicalSnapshotFactory.create(
                    inventory.snapshot(), lifecycleController.snapshot());
            return operationalSnapshotFactory.create(
                    physicalSnapshot,
                    manifestCatalog,
                    logicalSnapshot,
                    routeAdmissionFactory,
                    stickyLeaseRuntime.leaseSnapshot(),
                    routeTargetAdmissionCatalog.snapshotAdmissions().stream()
                            .filter(admission -> admission.stationType() == StationType.P2P)
                            .toList());
        };

        OsrProcessingReleaseCommandHandler commandHandler =
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycleController,
                        clockSnapshotSupplier,
                        routeTargetAdmissionCatalog.processingReleaseTargetRegistry(),
                        stickyLeaseRuntime.releaseAssignmentCommitter());
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                evaluationSource,
                operationalSnapshotSupplier,
                commandHandler);
        return new DspOperationalReleaseRuntime(controller, routeTargetAdmissionCatalog);
    }

    public DspOperationalReleaseRuntime create(
            OperationalReleaseEvaluationSource evaluationSource,
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetRegistry routeTargetRegistry) {
        requireNonNull(routeTargetRegistry, "routeTargetRegistry");
        return create(
                evaluationSource,
                inventory,
                lifecycleController,
                manifestCatalog,
                logicalSnapshotSupplier,
                clockSnapshotSupplier,
                stationAdmissionResolver,
                (OperationalRouteTargetAdmissionCatalog) routeTargetRegistry);
    }

    public DspOperationalReleaseRuntime create(
            OperationalReleaseEvaluationSource evaluationSource,
            OsrPhysicalInventory inventory,
            InboundToteLifecycleController lifecycleController,
            InboundToteManifestCatalog manifestCatalog,
            Supplier<WarehouseSchedulerSnapshot> logicalSnapshotSupplier,
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog) {
        requireNonNull(evaluationSource, "evaluationSource");
        requireNonNull(inventory, "inventory");
        requireNonNull(lifecycleController, "lifecycleController");
        requireNonNull(manifestCatalog, "manifestCatalog");
        requireNonNull(logicalSnapshotSupplier, "logicalSnapshotSupplier");
        requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier");
        requireNonNull(stationAdmissionResolver, "stationAdmissionResolver");
        requireNonNull(routeTargetAdmissionCatalog, "routeTargetAdmissionCatalog");

        OsrProcessingReleaseSnapshotFactory physicalSnapshotFactory =
                new OsrProcessingReleaseSnapshotFactory();
        DspOperationalReleaseSnapshotFactory operationalSnapshotFactory =
                new DspOperationalReleaseSnapshotFactory();
        OperationalCandidateRouteAdmissionFactory routeAdmissionFactory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        stationAdmissionResolver,
                        routeTargetAdmissionCatalog);

        Supplier<DspOperationalReleaseSnapshot> operationalSnapshotSupplier = () -> {
            WarehouseSchedulerSnapshot logicalSnapshot = logicalSnapshotSupplier.get();
            if (logicalSnapshot == null) {
                throw new IllegalStateException("logicalSnapshotSupplier returned null");
            }
            OsrProcessingReleaseSnapshot physicalSnapshot = physicalSnapshotFactory.create(
                    inventory.snapshot(), lifecycleController.snapshot());
            return operationalSnapshotFactory.create(
                    physicalSnapshot,
                    manifestCatalog,
                    logicalSnapshot,
                    routeAdmissionFactory);
        };

        OsrProcessingReleaseCommandHandler commandHandler =
                new OsrProcessingReleaseCommandHandler(
                        inventory,
                        lifecycleController,
                        clockSnapshotSupplier,
                        routeTargetAdmissionCatalog.processingReleaseTargetRegistry());
        DspOperationalReleaseController controller = new DspOperationalReleaseController(
                evaluationSource,
                operationalSnapshotSupplier,
                commandHandler);
        return new DspOperationalReleaseRuntime(controller, routeTargetAdmissionCatalog);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private static void validateP2pTargets(
            OperationalRouteTargetAdmissionCatalog admissionCatalog,
            List<P2pLineDefinition> lineDefinitions) {
        List<OperationalRouteDestination> configuredDestinations = lineDefinitions.stream()
                .map(definition -> definition.destination())
                .toList();
        Set<OperationalRouteDestination> admittedDestinations = new LinkedHashSet<>();
        admissionCatalog.snapshotAdmissions().stream()
                .filter(admission -> admission.stationType() == StationType.P2P)
                .map(admission -> new OperationalRouteDestination(
                        admission.stationType(), admission.targetId()))
                .forEach(destination -> {
                    if (!admittedDestinations.add(destination)) {
                        throw new IllegalArgumentException(
                            "Duplicate P2P route target admission: " + destination);
                    }
                });
        if (!admittedDestinations.equals(new LinkedHashSet<>(configuredDestinations))) {
            throw new IllegalArgumentException(
                    "P2P route admissions must match every configured line destination");
        }
    }

    private static void validateReleaseTargets(
            OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry) {
        Set<OperationalRouteDestination> configuredDestinations =
                new LinkedHashSet<>(routeTargetRegistry.destinations());
        Set<OperationalRouteDestination> osrDestinations = routeTargetRegistry.targets().stream()
                .map(target -> target.destination())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<OperationalRouteDestination> av02Destinations =
                routeTargetRegistry.av02Targets().stream()
                        .map(target -> target.destination())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!osrDestinations.equals(configuredDestinations)
                || !av02Destinations.equals(configuredDestinations)) {
            throw new IllegalArgumentException(
                    "Every configured route destination must have OSR and AV02 release targets");
        }
    }
}
