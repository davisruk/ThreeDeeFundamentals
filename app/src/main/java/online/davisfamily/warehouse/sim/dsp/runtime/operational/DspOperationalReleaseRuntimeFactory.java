package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandler;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactory;
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
}
