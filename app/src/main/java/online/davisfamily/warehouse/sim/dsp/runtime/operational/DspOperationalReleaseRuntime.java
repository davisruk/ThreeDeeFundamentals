package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;

public final class DspOperationalReleaseRuntime implements AutoCloseable {
    private final DspOperationalReleaseController controller;
    private final OperationalRouteTargetRegistry routeTargetRegistry;
    private boolean closed;

    DspOperationalReleaseRuntime(
            DspOperationalReleaseController controller,
            OperationalRouteTargetRegistry routeTargetRegistry) {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        if (routeTargetRegistry == null) {
            throw new IllegalArgumentException("routeTargetRegistry must not be null");
        }
        this.controller = controller;
        this.routeTargetRegistry = routeTargetRegistry;
    }

    public DspOperationalReleaseController controller() {
        return controller;
    }

    public OperationalRouteTargetRegistry routeTargetRegistry() {
        return routeTargetRegistry;
    }

    public List<OperationalRouteEntryQueueSnapshot> routeEntryQueueSnapshots() {
        return routeTargetRegistry.snapshots();
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
