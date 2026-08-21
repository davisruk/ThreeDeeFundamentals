package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class WarehouseTransportArrivalControllerTest {

    @Test
    void shouldHoldAndHandOffExactTerminalArrival() {
        Fixture fixture = fixture(2, 1);
        RoutedPhysicalTote tote = fixture.activate("tote-1", fixture.p2pDefinition);
        SimulationContext context = trackedContext(tote);

        fixture.controller.handleDetection(
                enter(fixture.p2pDefinition, tote), context);

        assertEquals(ToteMotionState.HELD, tote.tote().getInteractionMode());
        assertSame(tote, fixture.inFlight.find(tote.physicalToteId()).orElseThrow());
        assertTrue(fixture.inFlight.snapshot().entries().get(0).arrivalPending());
        assertEquals(1, fixture.controller.snapshot().pendingArrivals().size());
        assertTrue(fixture.p2pQueue.peek().isEmpty());

        fixture.controller.update(context, 0.1d);

        assertSame(tote, fixture.p2pQueue.peek().orElseThrow());
        assertFalse(fixture.inFlight.contains(tote.physicalToteId()));
        assertTrue(fixture.controller.snapshot().pendingArrivals().isEmpty());
        assertEquals(ToteMotionState.HELD, tote.tote().getInteractionMode());
        assertEquals(1, fixture.controller.snapshot().successfulArrivalCount());

        fixture.controller.handleDetection(
                enter(fixture.p2pDefinition, tote), context);
        assertFalse(fixture.controller.snapshot().blocked());
        assertEquals(1, fixture.controller.snapshot().successfulArrivalCount());
        assertTrue(fixture.controller.snapshot().pendingArrivals().isEmpty());
    }

    @Test
    void shouldRetryHeldHeadWhenArrivalCapacityReturns() {
        Fixture fixture = fixture(2, 1);
        RoutedPhysicalTote occupying = fixture.routedTote(
                "occupying", fixture.p2pDefinition);
        fixture.p2pQueue.enqueue(occupying);
        RoutedPhysicalTote arriving = fixture.activate("arriving", fixture.p2pDefinition);
        SimulationContext context = trackedContext(arriving);
        fixture.controller.handleDetection(enter(fixture.p2pDefinition, arriving), context);

        fixture.controller.update(context, 0.1d);

        assertSame(arriving,
                fixture.inFlight.find(arriving.physicalToteId()).orElseThrow());
        assertEquals(ToteMotionState.HELD, arriving.tote().getInteractionMode());
        assertTrue(fixture.controller.snapshot().blockedReason().contains("no capacity"));
        assertEquals(1, fixture.controller.snapshot().pendingArrivals().size());

        assertSame(occupying, fixture.p2pQueue.dequeue().orElseThrow());
        fixture.controller.update(context, 0.1d);

        assertSame(arriving, fixture.p2pQueue.peek().orElseThrow());
        assertFalse(fixture.controller.snapshot().blocked());
    }

    @Test
    void shouldIgnoreUnrelatedAndDuplicateTerminalEvents() {
        Fixture fixture = fixture(1, 1);
        RoutedPhysicalTote tote = fixture.activate("tote-1", fixture.p2pDefinition);
        SimulationContext context = trackedContext(tote);

        fixture.controller.handleDetection(new DetectionEvent(
                "source", 0d, "unrelated", tote.physicalToteId().value(),
                DetectionType.ENTER), context);
        fixture.controller.handleDetection(new DetectionEvent(
                "source", 0d, fixture.p2pDefinition.terminalArrivalSensorId(),
                tote.physicalToteId().value(), DetectionType.EXIT), context);
        assertTrue(fixture.controller.snapshot().pendingArrivals().isEmpty());
        assertEquals(ToteMotionState.MOVING, tote.tote().getInteractionMode());

        DetectionEvent event = enter(fixture.p2pDefinition, tote);
        fixture.controller.handleDetection(event, context);
        fixture.controller.handleDetection(event, context);

        assertEquals(1, fixture.controller.snapshot().pendingArrivals().size());
        assertEquals(ToteMotionState.HELD, tote.tote().getInteractionMode());
        assertFalse(fixture.controller.snapshot().blocked());
    }

    @Test
    void shouldBlockWrongDestinationMissingActiveAndWrongTrackedObject() {
        Fixture fixture = fixture(3, 1);
        RoutedPhysicalTote p2p = fixture.activate("p2p", fixture.p2pDefinition);
        p2p.tote().getRouteFollower().setCurrentSegment(
                fixture.adaptingDefinition.terminalSegment());
        SimulationContext p2pContext = trackedContext(p2p);
        fixture.controller.handleDetection(
                enter(fixture.adaptingDefinition, p2p), p2pContext);
        assertTrue(fixture.controller.snapshot().blockedReason().contains("destination"));
        assertEquals(ToteMotionState.MOVING, p2p.tote().getInteractionMode());

        RoutedPhysicalTote missing = fixture.routedTote("missing", fixture.p2pDefinition);
        fixture.controller.handleDetection(
                enter(fixture.p2pDefinition, missing), trackedContext(missing));
        assertTrue(fixture.controller.snapshot().blockedReason().contains("not active"));

        RoutedPhysicalTote active = fixture.activate("copy-id", fixture.p2pDefinition);
        RoutedPhysicalTote copy = fixture.routedTote("copy-id", fixture.p2pDefinition);
        fixture.controller.handleDetection(
                enter(fixture.p2pDefinition, active), trackedContext(copy));
        assertTrue(fixture.controller.snapshot().blockedReason().contains("exact active"));
        assertEquals(ToteMotionState.MOVING, active.tote().getInteractionMode());
    }

    @Test
    void shouldValidateTerminalSegmentBeforeHoldingTote() {
        Fixture fixture = fixture(1, 1);
        RoutedPhysicalTote tote = fixture.activate("tote-1", fixture.p2pDefinition);
        tote.tote().getRouteFollower().setCurrentSegment(
                RoutedToteRoutingTestFixtures.routeSegment("not-terminal", 2f));

        fixture.controller.handleDetection(
                enter(fixture.p2pDefinition, tote), trackedContext(tote));

        assertTrue(fixture.controller.snapshot().blockedReason().contains("route segment"));
        assertEquals(ToteMotionState.MOVING, tote.tote().getInteractionMode());
        assertTrue(fixture.controller.snapshot().pendingArrivals().isEmpty());
    }

    @Test
    void shouldProcessAtMostOnePendingArrivalPerUpdateInDetectionOrder() {
        Fixture fixture = fixture(2, 2);
        RoutedPhysicalTote first = fixture.activate("first", fixture.p2pDefinition);
        RoutedPhysicalTote second = fixture.activate("second", fixture.p2pDefinition);
        SimulationContext context = trackedContext(first, second);
        fixture.controller.handleDetection(enter(fixture.p2pDefinition, first), context);
        fixture.controller.handleDetection(enter(fixture.p2pDefinition, second), context);

        fixture.controller.update(context, 0.1d);

        assertSame(first, fixture.p2pQueue.peek().orElseThrow());
        assertFalse(fixture.inFlight.contains(first.physicalToteId()));
        assertTrue(fixture.inFlight.contains(second.physicalToteId()));
        assertEquals(second.physicalToteId(), fixture.controller.snapshot()
                .pendingArrivals().get(0).physicalToteId());

        fixture.controller.update(context, 0.1d);
        assertSame(first, fixture.p2pQueue.dequeue().orElseThrow());
        assertSame(second, fixture.p2pQueue.dequeue().orElseThrow());
    }

    @Test
    void shouldBlockDuplicateQueueIdentityWithoutRemovingInFlightOwnership() {
        Fixture fixture = fixture(1, 2);
        RoutedPhysicalTote tote = fixture.activate("tote-1", fixture.p2pDefinition);
        SimulationContext context = trackedContext(tote);
        fixture.controller.handleDetection(enter(fixture.p2pDefinition, tote), context);
        fixture.p2pQueue.enqueue(tote);

        fixture.controller.update(context, 0.1d);

        assertTrue(fixture.controller.snapshot().blockedReason().contains("already present"));
        assertSame(tote, fixture.inFlight.find(tote.physicalToteId()).orElseThrow());
        assertEquals(1, fixture.controller.snapshot().pendingArrivals().size());
    }

    @Test
    void shouldValidateDependenciesEventsAndUpdates() {
        Fixture fixture = fixture(1, 1);
        SimulationContext context = new SimulationContext();
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller.handleDetection(null, context));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller.handleDetection(
                        new DetectionEvent(), null));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller.update(null, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller.update(context, -1d));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.controller.update(context, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalController(
                        null, fixture.inFlight, fixture.arrivalRegistry));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalController(
                        fixture.catalog, null, fixture.arrivalRegistry));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalController(
                        fixture.catalog, fixture.inFlight, null));
        fixture.controller.update(context, 0d);
        assertFalse(fixture.controller.snapshot().blocked());
    }

    private static DetectionEvent enter(
            WarehouseRouteDefinition definition,
            RoutedPhysicalTote tote) {
        return new DetectionEvent(
                "terminal-source",
                1d,
                definition.terminalArrivalSensorId(),
                tote.physicalToteId().value(),
                DetectionType.ENTER);
    }

    private static SimulationContext trackedContext(RoutedPhysicalTote... totes) {
        SimulationContext context = new SimulationContext();
        for (RoutedPhysicalTote tote : totes) {
            context.addTrackedObject(tote.tote());
        }
        return context;
    }

    private static Fixture fixture(int inFlightCapacity, int arrivalCapacity) {
        RouteSegment entry = RoutedToteRoutingTestFixtures.routeSegment("entry", 2f);
        WarehouseRouteDefinition p2p = definition(
                StationType.P2P, "p2p", entry,
                RoutedToteRoutingTestFixtures.routeSegment("p2p-terminal", 2f));
        WarehouseRouteDefinition adapting = definition(
                StationType.ADAPTING, "adapting", entry,
                RoutedToteRoutingTestFixtures.routeSegment("adapting-terminal", 2f));
        WarehouseRouteCatalog catalog = new WarehouseRouteCatalog(List.of(p2p, adapting));
        StationRoutedToteArrivalQueue p2pQueue =
                new StationRoutedToteArrivalQueue(p2p.destination(), arrivalCapacity);
        StationRoutedToteArrivalQueue adaptingQueue =
                new StationRoutedToteArrivalQueue(adapting.destination(), arrivalCapacity);
        StationRoutedToteArrivalRegistry arrivalRegistry =
                new StationRoutedToteArrivalRegistry(List.of(p2pQueue, adaptingQueue));
        WarehouseTransportInFlightRegistry inFlight =
                new WarehouseTransportInFlightRegistry(inFlightCapacity);
        return new Fixture(
                catalog, p2p, adapting, p2pQueue, arrivalRegistry, inFlight,
                new WarehouseTransportArrivalController(
                        catalog, inFlight, arrivalRegistry));
    }

    private static WarehouseRouteDefinition definition(
            StationType stationType,
            String targetId,
            RouteSegment entry,
            RouteSegment terminal) {
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(stationType, targetId);
        return new WarehouseRouteDefinition(
                destination,
                entry,
                0f,
                TravelDirection.FORWARD,
                targetId + "-sensor",
                terminal);
    }

    private record Fixture(
            WarehouseRouteCatalog catalog,
            WarehouseRouteDefinition p2pDefinition,
            WarehouseRouteDefinition adaptingDefinition,
            StationRoutedToteArrivalQueue p2pQueue,
            StationRoutedToteArrivalRegistry arrivalRegistry,
            WarehouseTransportInFlightRegistry inFlight,
            WarehouseTransportArrivalController controller) {

        private RoutedPhysicalTote activate(
                String physicalToteId,
                WarehouseRouteDefinition definition) {
            RoutedPhysicalTote tote = routedTote(physicalToteId, definition);
            inFlight.register(tote);
            return tote;
        }

        private RoutedPhysicalTote routedTote(
                String physicalToteId,
                WarehouseRouteDefinition definition) {
            RoutedPhysicalTote tote = RoutedToteRoutingTestFixtures.routedTote(
                    physicalToteId, definition.destination());
            tote.tote().getRouteFollower().setCurrentSegment(definition.terminalSegment());
            return tote;
        }
    }
}
