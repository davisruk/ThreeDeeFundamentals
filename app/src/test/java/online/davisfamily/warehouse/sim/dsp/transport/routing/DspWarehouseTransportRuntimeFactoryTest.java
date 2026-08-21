package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

class DspWarehouseTransportRuntimeFactoryTest {

    @Test
    void shouldConsumeExactSharedLaunchQueueAndPublishBeforeNextMovementPass() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.sharedLaunchQueue.enqueue(fixture.source.launchRequest());

        fixture.simulationWorld.update(0.25d);

        assertTrue(fixture.sharedLaunchQueue.peek().isEmpty());
        assertEquals(0, fixture.runtime.outboundTransportSnapshot().occupancy());
        assertEquals(1, fixture.runtime.inFlightSnapshot().occupancy());
        assertEquals(1, fixture.renderables.size());
        assertEquals(0f,
                fixture.renderables.get(0).transformation.xTranslation);

        fixture.simulationWorld.update(0.25d);

        assertEquals(0.5f,
                fixture.renderables.get(0).transformation.xTranslation);
        assertEquals(1,
                fixture.runtime.ingressController().snapshot().successfulIngressCount());
        assertEquals(1,
                fixture.runtime.routeLaunchController().snapshot()
                        .successfulHydrationCount());
    }

    @Test
    void shouldSupplyDestinationStrategiesToTransferInstallationInTopologyOrder() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        SimulationWorld simulationWorld = new SimulationWorld();
        List<String> installedMachineIds = new ArrayList<>();
        List<DestinationAwareTransferTargetDecisionStrategy> installedStrategies =
                new ArrayList<>();
        List<StationRoutedToteArrivalQueue> arrivals = topology.catalog().definitions()
                .stream()
                .map(definition ->
                        new StationRoutedToteArrivalQueue(definition.destination(), 1))
                .toList();

        DspWarehouseTransportRuntime runtime = new DspWarehouseTransportRuntimeFactory().create(
                simulationWorld,
                new ArrayList<>(),
                new OsrOutboundRouteLaunchQueue("shared", 2),
                physicalToteId -> null,
                topology.catalog(),
                (request, plan) -> RoutedToteRoutingTestFixtures.renderable(
                        request.physicalToteId().value()),
                1d,
                new Vec3(),
                0f,
                2,
                2,
                List.of(topology.firstMachine(), topology.secondMachine()),
                topology.entries(),
                (machine, strategy) -> {
                    installedMachineIds.add(machine.getId());
                    installedStrategies.add(strategy);
                },
                arrivals);

        assertEquals(
                List.of(topology.firstMachine().getId(), topology.secondMachine().getId()),
                installedMachineIds);
        assertEquals(2, installedStrategies.size());
        assertEquals(3, runtime.stationArrivalSnapshots().size());
    }

    @Test
    void shouldPublishExactToteAndRejectDuplicateOrConflictingRenderable() {
        SimulationWorld world = new SimulationWorld();
        List<RenderableObject> renderables = new ArrayList<>();
        SimulationWorldWarehouseTransportPublisher publisher =
                new SimulationWorldWarehouseTransportPublisher(world, renderables);
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");
        RoutedPhysicalTote routedTote =
                RoutedToteRoutingTestFixtures.routedTote("tote-1", destination);

        publisher.publish(routedTote);

        assertTrue(publisher.contains(routedTote.physicalToteId()));
        assertEquals(List.of(routedTote.renderable()), renderables);
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(routedTote));

        RoutedPhysicalTote conflicting =
                RoutedToteRoutingTestFixtures.routedTote("tote-2", destination);
        conflicting.renderable().id = "existing-id";
        renderables.add(RoutedToteRoutingTestFixtures.renderable("tote-2"));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(conflicting));
        assertFalse(publisher.contains(conflicting.physicalToteId()));
    }

    @Test
    void shouldValidateFactoryAndPublisherDependencies() {
        RuntimeFixture fixture = createRuntimeFixture();
        DspWarehouseTransportRuntimeFactory factory =
                new DspWarehouseTransportRuntimeFactory();

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorldWarehouseTransportPublisher(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorldWarehouseTransportPublisher(
                        new SimulationWorld(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorldWarehouseTransportPublisher(
                        new SimulationWorld(), java.util.Arrays.asList((RenderableObject) null)));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorldWarehouseTransportPublisher(
                        new SimulationWorld(), new ArrayList<>()).contains(null));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorldWarehouseTransportPublisher(
                        new SimulationWorld(), new ArrayList<>()).publish(null));

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        null,
                        fixture.renderables,
                        fixture.sharedLaunchQueue,
                        physicalToteId -> fixture.source.loadPlan(),
                        fixture.catalog,
                        fixture.renderableFactory(),
                        1d,
                        new Vec3(),
                        0f,
                        1,
                        1,
                        List.of(),
                        List.of(),
                        (machine, strategy) -> { },
                        fixture.arrivalQueues));
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        new SimulationWorld(),
                        new ArrayList<>(),
                        null,
                        physicalToteId -> fixture.source.loadPlan(),
                        fixture.catalog,
                        fixture.renderableFactory(),
                        1d,
                        new Vec3(),
                        0f,
                        1,
                        1,
                        List.of(),
                        List.of(),
                        (machine, strategy) -> { },
                        fixture.arrivalQueues));
    }

    static RuntimeFixture createRuntimeFixture() {
        RouteSegment entry = RoutedToteRoutingTestFixtures.routeSegment("entry", 4f);
        RouteSegment terminal =
                RoutedToteRoutingTestFixtures.routeSegment("terminal", 2f);
        entry.connectTo(terminal);
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");
        WarehouseRouteDefinition definition = new WarehouseRouteDefinition(
                destination,
                entry,
                0f,
                TravelDirection.FORWARD,
                "terminal-sensor",
                terminal);
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(definition));
        RoutedPhysicalTote source =
                RoutedToteRoutingTestFixtures.routedTote("tote-1", destination);
        OsrOutboundRouteLaunchQueue sharedLaunchQueue =
                new OsrOutboundRouteLaunchQueue("shared-launch", 2);
        SimulationWorld simulationWorld = new SimulationWorld();
        List<RenderableObject> renderables = new ArrayList<>();
        List<StationRoutedToteArrivalQueue> arrivalQueues = List.of(
                new StationRoutedToteArrivalQueue(destination, 1));
        RuntimeFixture unbuilt = new RuntimeFixture(
                simulationWorld,
                renderables,
                sharedLaunchQueue,
                catalog,
                source,
                arrivalQueues,
                null);
        DspWarehouseTransportRuntime runtime = new DspWarehouseTransportRuntimeFactory().create(
                simulationWorld,
                renderables,
                sharedLaunchQueue,
                physicalToteId -> source.loadPlan(),
                catalog,
                unbuilt.renderableFactory(),
                2d,
                new Vec3(),
                0f,
                2,
                2,
                List.of(),
                List.of(),
                (machine, strategy) -> { },
                arrivalQueues);
        return new RuntimeFixture(
                simulationWorld,
                renderables,
                sharedLaunchQueue,
                catalog,
                source,
                arrivalQueues,
                runtime);
    }

    record RuntimeFixture(
            SimulationWorld simulationWorld,
            List<RenderableObject> renderables,
            OsrOutboundRouteLaunchQueue sharedLaunchQueue,
            WarehouseRouteCatalog catalog,
            RoutedPhysicalTote source,
            List<StationRoutedToteArrivalQueue> arrivalQueues,
            DspWarehouseTransportRuntime runtime) {

        DetachedToteRenderableFactory renderableFactory() {
            return (request, loadPlan) -> RoutedToteRoutingTestFixtures.renderable(
                    request.physicalToteId().value());
        }
    }
}
