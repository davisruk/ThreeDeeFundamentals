package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

class WarehouseTransportIngressControllerTest {

    @Test
    void shouldPublishAtMostOneHeadPerUpdateInFifoOrder() {
        Fixture fixture = fixture(3);
        RoutedPhysicalTote first = fixture.routedTote("tote-1");
        RoutedPhysicalTote second = fixture.routedTote("tote-2");
        fixture.queue.enqueue(first);
        fixture.queue.enqueue(second);
        List<RoutedPhysicalTote> published = new ArrayList<>();
        TestPublisher publisher = new TestPublisher(published::add);
        WarehouseTransportIngressController controller = fixture.controller(publisher);

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(List.of(first), published);
        assertSame(second, fixture.queue.peek().orElseThrow());
        assertSame(first, fixture.inFlight.find(first.physicalToteId()).orElseThrow());
        assertEquals(1, controller.snapshot().successfulIngressCount());

        controller.update(new SimulationContext(), 0d);

        assertEquals(List.of(first, second), published);
        assertTrue(fixture.queue.peek().isEmpty());
        assertEquals(2, fixture.inFlight.snapshot().occupancy());
    }

    @Test
    void shouldPublishBeforeRegisteringAndRemovingExactSourceHead() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = fixture.routedTote("tote-1");
        fixture.queue.enqueue(head);
        TestPublisher publisher = new TestPublisher(routedTote -> {
            assertSame(head, routedTote);
            assertSame(head, fixture.queue.peek().orElseThrow());
            assertFalse(fixture.inFlight.contains(head.physicalToteId()));
        });

        fixture.controller(publisher).update(new SimulationContext(), 0.1d);

        assertTrue(fixture.queue.peek().isEmpty());
        assertSame(head, fixture.inFlight.find(head.physicalToteId()).orElseThrow());
    }

    @Test
    void shouldRetainUnknownRouteAndMismatchedEntryBindings() {
        Fixture fixture = fixture(4);
        OperationalRouteDestination unknown =
                new OperationalRouteDestination(StationType.P2P, "unknown");
        RoutedPhysicalTote unknownRoute =
                RoutedToteRoutingTestFixtures.routedTote("unknown", unknown);
        fixture.queue.enqueue(unknownRoute);
        TestPublisher publisher = new TestPublisher(null);
        WarehouseTransportIngressController controller = fixture.controller(publisher);

        controller.update(new SimulationContext(), 0.1d);

        assertSame(unknownRoute, fixture.queue.peek().orElseThrow());
        assertTrue(controller.snapshot().blockedReason().contains("No warehouse route"));
        assertEquals(0, publisher.published.size());
        fixture.queue.dequeue().orElseThrow();

        RoutedPhysicalTote wrongSegment = fixture.routedTote("wrong-segment");
        wrongSegment.tote().getRouteFollower().setCurrentSegment(
                RoutedToteRoutingTestFixtures.routeSegment("other", 2f));
        assertBindingBlocked(fixture, controller, publisher, wrongSegment, "segment");

        RoutedPhysicalTote wrongDistance = fixture.routedTote("wrong-distance");
        wrongDistance.tote().getRouteFollower().setDistanceAlongSegment(0.75f);
        assertBindingBlocked(fixture, controller, publisher, wrongDistance, "distance");

        RoutedPhysicalTote wrongDirection = fixture.routedTote("wrong-direction");
        wrongDirection.tote().getRouteFollower().setTravelDirection(TravelDirection.FORWARD);
        assertBindingBlocked(fixture, controller, publisher, wrongDirection, "direction");
    }

    @Test
    void shouldApplyInFlightAndPublishedIdentityBackpressure() {
        Fixture fullFixture = fixture(1);
        RoutedPhysicalTote active = fullFixture.routedTote("active");
        RoutedPhysicalTote waiting = fullFixture.routedTote("waiting");
        fullFixture.inFlight.register(active);
        fullFixture.queue.enqueue(waiting);
        TestPublisher publisher = new TestPublisher(null);
        WarehouseTransportIngressController fullController =
                fullFixture.controller(publisher);

        fullController.update(new SimulationContext(), 0.1d);

        assertSame(waiting, fullFixture.queue.peek().orElseThrow());
        assertTrue(fullController.snapshot().blockedReason().contains("capacity"));
        assertTrue(publisher.published.isEmpty());

        Fixture duplicateFixture = fixture(2);
        RoutedPhysicalTote duplicate = duplicateFixture.routedTote("duplicate");
        duplicateFixture.queue.enqueue(duplicate);
        duplicateFixture.inFlight.register(duplicate);
        WarehouseTransportIngressController duplicateController =
                duplicateFixture.controller(new TestPublisher(null));
        duplicateController.update(new SimulationContext(), 0.1d);
        assertTrue(duplicateController.snapshot().blockedReason().contains("already active"));

        Fixture publishedFixture = fixture(1);
        RoutedPhysicalTote published = publishedFixture.routedTote("published");
        publishedFixture.queue.enqueue(published);
        TestPublisher alreadyPublished = new TestPublisher(null);
        alreadyPublished.publishedIds.add(published.physicalToteId());
        WarehouseTransportIngressController publishedController =
                publishedFixture.controller(alreadyPublished);
        publishedController.update(new SimulationContext(), 0.1d);
        assertTrue(publishedController.snapshot().blockedReason().contains("already published"));
    }

    @Test
    void shouldPropagatePublisherFailureAndRetainSourceOwnership() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = fixture.routedTote("tote-1");
        fixture.queue.enqueue(head);
        IllegalStateException failure = new IllegalStateException("publication failed");
        TestPublisher publisher = new TestPublisher(routedTote -> {
            throw failure;
        });

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> fixture.controller(publisher)
                                .update(new SimulationContext(), 0.1d)));
        assertSame(head, fixture.queue.peek().orElseThrow());
        assertFalse(fixture.inFlight.contains(head.physicalToteId()));
    }

    @Test
    void shouldExposeFreshSnapshotHistoryWithoutMutatingArrivalQueues() {
        Fixture fixture = fixture(1);
        RoutedPhysicalTote head = fixture.routedTote("tote-1");
        fixture.queue.enqueue(head);
        StationRoutedToteArrivalQueue arrivals =
                new StationRoutedToteArrivalQueue(fixture.destination, 1);
        WarehouseTransportIngressController controller =
                fixture.controller(new TestPublisher(null));

        WarehouseTransportIngressControllerSnapshot before = controller.snapshot();
        controller.update(new SimulationContext(), 0.1d);
        WarehouseTransportIngressControllerSnapshot after = controller.snapshot();

        assertEquals(head.physicalToteId(), before.headPhysicalToteId().orElseThrow());
        assertEquals(0, before.successfulIngressCount());
        assertEquals(1, after.successfulIngressCount());
        assertEquals(head.physicalToteId(),
                after.lastIngressPhysicalToteId().orElseThrow());
        assertEquals(0, arrivals.snapshot().occupancy());
        assertFalse(after.blocked());
    }

    @Test
    void shouldValidateDependenciesAndUpdates() {
        Fixture fixture = fixture(1);
        TestPublisher publisher = new TestPublisher(null);
        WarehouseTransportIngressController controller = fixture.controller(publisher);

        assertThrows(IllegalArgumentException.class, () -> controller.update(null, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(new SimulationContext(), -1d));
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(new SimulationContext(), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportIngressController(
                        null, fixture.catalog, fixture.inFlight, publisher));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportIngressController(
                        fixture.queue, null, fixture.inFlight, publisher));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportIngressController(
                        fixture.queue, fixture.catalog, null, publisher));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportIngressController(
                        fixture.queue, fixture.catalog, fixture.inFlight, null));

        controller.update(new SimulationContext(), 0d);
        assertFalse(controller.snapshot().blocked());
    }

    private static void assertBindingBlocked(
            Fixture fixture,
            WarehouseTransportIngressController controller,
            TestPublisher publisher,
            RoutedPhysicalTote routedTote,
            String reasonFragment) {
        fixture.queue.enqueue(routedTote);
        controller.update(new SimulationContext(), 0.1d);
        assertSame(routedTote, fixture.queue.peek().orElseThrow());
        assertTrue(controller.snapshot().blockedReason().contains(reasonFragment));
        assertTrue(publisher.published.isEmpty());
        fixture.queue.dequeue().orElseThrow();
    }

    private static Fixture fixture(int inFlightCapacity) {
        RouteSegment entry = RoutedToteRoutingTestFixtures.routeSegment("entry", 2f);
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");
        WarehouseRouteDefinition definition = new WarehouseRouteDefinition(
                destination,
                entry,
                0.5f,
                TravelDirection.REVERSE,
                "p2p-sensor",
                RoutedToteRoutingTestFixtures.routeSegment("terminal", 2f));
        return new Fixture(
                destination,
                definition,
                new WarehouseRouteCatalog(List.of(definition)),
                new OsrOutboundTransportQueue("outbound", 8),
                new WarehouseTransportInFlightRegistry(inFlightCapacity));
    }

    private record Fixture(
            OperationalRouteDestination destination,
            WarehouseRouteDefinition definition,
            WarehouseRouteCatalog catalog,
            OsrOutboundTransportQueue queue,
            WarehouseTransportInFlightRegistry inFlight) {

        private RoutedPhysicalTote routedTote(String physicalToteId) {
            RoutedPhysicalTote routedTote =
                    RoutedToteRoutingTestFixtures.routedTote(physicalToteId, destination);
            routedTote.tote().getRouteFollower().setCurrentSegment(definition.entrySegment());
            routedTote.tote().getRouteFollower().setDistanceAlongSegment(
                    definition.entryDistance());
            routedTote.tote().getRouteFollower().setTravelDirection(
                    definition.entryDirection());
            return routedTote;
        }

        private WarehouseTransportIngressController controller(
                WarehouseTransportPublisher publisher) {
            return new WarehouseTransportIngressController(
                    queue, catalog, inFlight, publisher);
        }
    }

    private static final class TestPublisher implements WarehouseTransportPublisher {
        private final Set<PhysicalToteId> publishedIds = new HashSet<>();
        private final List<RoutedPhysicalTote> published = new ArrayList<>();
        private final java.util.function.Consumer<RoutedPhysicalTote> onPublish;

        private TestPublisher(java.util.function.Consumer<RoutedPhysicalTote> onPublish) {
            this.onPublish = onPublish;
        }

        @Override
        public boolean contains(PhysicalToteId physicalToteId) {
            return publishedIds.contains(physicalToteId);
        }

        @Override
        public void publish(RoutedPhysicalTote routedTote) {
            if (onPublish != null) {
                onPublish.accept(routedTote);
            }
            if (!publishedIds.add(routedTote.physicalToteId())) {
                throw new IllegalArgumentException("duplicate publication");
            }
            published.add(routedTote);
        }
    }
}
