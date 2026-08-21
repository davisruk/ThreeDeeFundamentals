package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;

public final class DspOperationalReleaseRuntime implements AutoCloseable {
    private final DspOperationalReleaseController controller;
    private final OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog;
    private final OperationalRouteTargetRegistry legacyRouteTargetRegistry;
    private final OsrOutboundRouteLaunchTargetRegistry launchTargetRegistry;
    private boolean closed;

    DspOperationalReleaseRuntime(
            DspOperationalReleaseController controller,
            OperationalRouteTargetRegistry routeTargetRegistry) {
        this(controller, (OperationalRouteTargetAdmissionCatalog) routeTargetRegistry);
    }

    DspOperationalReleaseRuntime(
            DspOperationalReleaseController controller,
            OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog) {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        if (routeTargetAdmissionCatalog == null) {
            throw new IllegalArgumentException("routeTargetAdmissionCatalog must not be null");
        }
        this.controller = controller;
        this.routeTargetAdmissionCatalog = routeTargetAdmissionCatalog;
        this.legacyRouteTargetRegistry = routeTargetAdmissionCatalog
                instanceof OperationalRouteTargetRegistry registry ? registry : null;
        this.launchTargetRegistry = routeTargetAdmissionCatalog
                instanceof OsrOutboundRouteLaunchTargetRegistry registry ? registry : null;
    }

    public DspOperationalReleaseController controller() {
        return controller;
    }

    public OperationalRouteTargetRegistry routeTargetRegistry() {
        if (legacyRouteTargetRegistry == null) {
            throw new IllegalStateException(
                    "Per-target compatibility route queues are unavailable on this runtime");
        }
        return legacyRouteTargetRegistry;
    }

    public List<OperationalRouteEntryQueueSnapshot> routeEntryQueueSnapshots() {
        return routeTargetRegistry().snapshots();
    }

    public List<OperationalRouteTargetAdmissionSnapshot> routeTargetAdmissionSnapshots() {
        return routeTargetAdmissionCatalog.snapshotAdmissions();
    }

    public OsrOutboundRouteLaunchQueueSnapshot outboundRouteLaunchQueueSnapshot() {
        if (launchTargetRegistry == null) {
            throw new IllegalStateException(
                    "Shared OSR outbound route-launch queue is unavailable on this runtime");
        }
        return launchTargetRegistry.launchQueueSnapshot();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        controller.close();
    }
}
