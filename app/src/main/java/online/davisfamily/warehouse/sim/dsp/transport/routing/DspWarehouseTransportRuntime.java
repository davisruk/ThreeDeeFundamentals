package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchController;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;

public final class DspWarehouseTransportRuntime implements AutoCloseable {
    private final OsrOutboundRouteLaunchController routeLaunchController;
    private final WarehouseTransportIngressController ingressController;
    private final WarehouseTransportArrivalController arrivalController;
    private final WarehouseRouteCatalog routeCatalog;
    private final OsrOutboundTransportQueue transportQueue;
    private final WarehouseTransportInFlightRegistry inFlightRegistry;
    private final StationRoutedToteArrivalRegistry arrivalRegistry;
    private boolean closed;

    DspWarehouseTransportRuntime(
            OsrOutboundRouteLaunchController routeLaunchController,
            WarehouseTransportIngressController ingressController,
            WarehouseTransportArrivalController arrivalController,
            WarehouseRouteCatalog routeCatalog,
            OsrOutboundTransportQueue transportQueue,
            WarehouseTransportInFlightRegistry inFlightRegistry,
            StationRoutedToteArrivalRegistry arrivalRegistry) {
        if (routeLaunchController == null
                || ingressController == null
                || arrivalController == null
                || routeCatalog == null
                || transportQueue == null
                || inFlightRegistry == null
                || arrivalRegistry == null) {
            throw new IllegalArgumentException("runtime dependencies must not be null");
        }
        this.routeLaunchController = routeLaunchController;
        this.ingressController = ingressController;
        this.arrivalController = arrivalController;
        this.routeCatalog = routeCatalog;
        this.transportQueue = transportQueue;
        this.inFlightRegistry = inFlightRegistry;
        this.arrivalRegistry = arrivalRegistry;
    }

    public OsrOutboundRouteLaunchController routeLaunchController() {
        return routeLaunchController;
    }

    public WarehouseTransportIngressController ingressController() {
        return ingressController;
    }

    public WarehouseTransportArrivalController arrivalController() {
        return arrivalController;
    }

    public WarehouseRouteCatalogSnapshot routeCatalogSnapshot() {
        return routeCatalog.snapshot();
    }

    public OsrOutboundTransportQueueSnapshot outboundTransportSnapshot() {
        return transportQueue.snapshot();
    }

    public WarehouseTransportInFlightSnapshot inFlightSnapshot() {
        return inFlightRegistry.snapshot();
    }

    public List<StationRoutedToteArrivalQueueSnapshot> stationArrivalSnapshots() {
        return arrivalRegistry.snapshots();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }
}
