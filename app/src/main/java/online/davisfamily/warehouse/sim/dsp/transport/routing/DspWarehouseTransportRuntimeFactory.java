package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.List;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchController;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.transport.LoadPlanOsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public final class DspWarehouseTransportRuntimeFactory {
    private static final String TRANSPORT_QUEUE_ID = "dsp-osr-outbound-transport";

    public DspWarehouseTransportRuntime create(
            SimulationWorld simulationWorld,
            List<RenderableObject> renderables,
            OsrOutboundRouteLaunchQueue sharedLaunchQueue,
            ToteLoadPlanProvider loadPlanProvider,
            WarehouseRouteCatalog routeCatalog,
            DetachedToteRenderableFactory renderableFactory,
            double routeSpeedUnitsPerSecond,
            Vec3 toteRenderOffsets,
            float toteYawOffsetRadians,
            int transportCapacity,
            int inFlightCapacity,
            List<TransferZoneMachine> transferMachines,
            List<WarehouseTransferRoutingTable.Entry> transferEntries,
            WarehouseTransferStrategyInstaller transferStrategyInstaller,
            List<StationRoutedToteArrivalQueue> arrivalQueues) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(renderables, "renderables");
        WarehouseTransportPublisher publisher =
                new SimulationWorldWarehouseTransportPublisher(
                        simulationWorld, renderables);
        return create(
                simulationWorld,
                sharedLaunchQueue,
                loadPlanProvider,
                routeCatalog,
                renderableFactory,
                routeSpeedUnitsPerSecond,
                toteRenderOffsets,
                toteYawOffsetRadians,
                transportCapacity,
                inFlightCapacity,
                publisher,
                transferMachines,
                transferEntries,
                transferStrategyInstaller,
                arrivalQueues);
    }

    DspWarehouseTransportRuntime create(
            SimulationWorld simulationWorld,
            OsrOutboundRouteLaunchQueue sharedLaunchQueue,
            ToteLoadPlanProvider loadPlanProvider,
            WarehouseRouteCatalog routeCatalog,
            DetachedToteRenderableFactory renderableFactory,
            double routeSpeedUnitsPerSecond,
            Vec3 toteRenderOffsets,
            float toteYawOffsetRadians,
            int transportCapacity,
            int inFlightCapacity,
            WarehouseTransportPublisher publisher,
            List<TransferZoneMachine> transferMachines,
            List<WarehouseTransferRoutingTable.Entry> transferEntries,
            WarehouseTransferStrategyInstaller transferStrategyInstaller,
            List<StationRoutedToteArrivalQueue> arrivalQueues) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(sharedLaunchQueue, "sharedLaunchQueue");
        requireNonNull(loadPlanProvider, "loadPlanProvider");
        requireNonNull(routeCatalog, "routeCatalog");
        requireNonNull(renderableFactory, "renderableFactory");
        requireNonNull(publisher, "publisher");
        requireNonNull(transferMachines, "transferMachines");
        requireNonNull(transferEntries, "transferEntries");
        requireNonNull(transferStrategyInstaller, "transferStrategyInstaller");
        requireNonNull(arrivalQueues, "arrivalQueues");
        if (transportCapacity < 0) {
            throw new IllegalArgumentException("transportCapacity must be >= 0");
        }
        if (inFlightCapacity < 0) {
            throw new IllegalArgumentException("inFlightCapacity must be >= 0");
        }

        RouteBoundDetachedOutboundToteFactory detachedToteFactory =
                new RouteBoundDetachedOutboundToteFactory(
                        routeCatalog,
                        renderableFactory,
                        routeSpeedUnitsPerSecond,
                        toteRenderOffsets,
                        toteYawOffsetRadians);
        LoadPlanOsrOutboundToteHydrator hydrator =
                new LoadPlanOsrOutboundToteHydrator(
                        loadPlanProvider, detachedToteFactory);
        OsrOutboundTransportQueue transportQueue =
                new OsrOutboundTransportQueue(TRANSPORT_QUEUE_ID, transportCapacity);
        OsrOutboundRouteLaunchController routeLaunchController =
                new OsrOutboundRouteLaunchController(
                        sharedLaunchQueue, transportQueue, hydrator);
        WarehouseTransportInFlightRegistry inFlightRegistry =
                new WarehouseTransportInFlightRegistry(inFlightCapacity);
        WarehouseTransportIngressController ingressController =
                new WarehouseTransportIngressController(
                        transportQueue, routeCatalog, inFlightRegistry, publisher);
        WarehouseTransferRoutingTable routingTable =
                new WarehouseTransferRoutingTable(
                        routeCatalog, transferMachines, transferEntries);
        StationRoutedToteArrivalRegistry arrivalRegistry =
                new StationRoutedToteArrivalRegistry(arrivalQueues);
        WarehouseTransportArrivalController arrivalController =
                new WarehouseTransportArrivalController(
                        routeCatalog, inFlightRegistry, arrivalRegistry);

        simulationWorld.addController(routeLaunchController);
        simulationWorld.addController(ingressController);
        for (TransferZoneMachine machine : transferMachines) {
            transferStrategyInstaller.install(
                    machine,
                    new DestinationAwareTransferTargetDecisionStrategy(
                            machine.getId(), routingTable, inFlightRegistry));
        }
        simulationWorld.registerListener(
                online.davisfamily.threedee.sim.framework.events.DetectionEvent.class,
                arrivalController.detectionHandler());
        simulationWorld.addController(arrivalController);

        return new DspWarehouseTransportRuntime(
                routeLaunchController,
                ingressController,
                arrivalController,
                routeCatalog,
                transportQueue,
                inFlightRegistry,
                arrivalRegistry);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
