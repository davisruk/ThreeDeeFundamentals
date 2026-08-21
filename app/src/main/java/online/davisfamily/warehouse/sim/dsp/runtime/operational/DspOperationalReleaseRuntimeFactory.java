package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCommandHandler;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRouteAdmissionFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntrySelector;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class DspOperationalReleaseRuntimeFactory {

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
}
