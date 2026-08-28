package online.davisfamily.warehouse.sim.dsp.station.continuation;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublisher;

/**
 * Composes the production station route-continuation controller.
 *
 * <p>All dependencies are validated before the supplied simulation world is changed.  This
 * factory does not construct station machines, transport infrastructure, or a publisher; the
 * composition root supplies those exact owners and the shared coordinator.</p>
 */
public final class DspStationRouteContinuationRuntimeFactory {

    public DspStationRouteContinuationRuntime create(
            SimulationWorld simulationWorld,
            StationProcessingCoordinator coordinator,
            StationProcessingOrderCatalog orderCatalog,
            MutableToteLoadPlanRegistry loadPlanRegistry,
            DspRouteDeriver routeDeriver,
            StationRouteContinuationSelector selector,
            StationRouteContinuationTargetResolver targetResolver,
            WarehouseRouteCatalog routeCatalog,
            OsrOutboundTransportQueue transportQueue,
            WarehouseTransportPublisher publisher) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(coordinator, "coordinator");
        requireNonNull(orderCatalog, "orderCatalog");
        requireNonNull(loadPlanRegistry, "loadPlanRegistry");
        requireNonNull(routeDeriver, "routeDeriver");
        requireNonNull(selector, "selector");
        requireNonNull(targetResolver, "targetResolver");
        requireNonNull(routeCatalog, "routeCatalog");
        requireNonNull(transportQueue, "transportQueue");
        requireNonNull(publisher, "publisher");

        StationRouteContinuationController controller = new StationRouteContinuationController(
                coordinator,
                orderCatalog,
                loadPlanRegistry,
                routeDeriver,
                selector,
                targetResolver,
                routeCatalog,
                transportQueue,
                publisher);
        simulationWorld.addController(controller);
        return new DspStationRouteContinuationRuntime(controller);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
