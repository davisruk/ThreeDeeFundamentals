package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

class WarehouseTransportInFlightRegistryTest {

    @Test
    void shouldRetainExactPayloadsInRegistrationOrder() {
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(2);
        RoutedPhysicalTote first = routedTote("tote-1", "third-party");
        RoutedPhysicalTote second = routedTote("tote-2", "adapting");

        registry.register(first);
        registry.register(second);

        assertFalse(registry.canAccept());
        assertTrue(registry.contains(first.physicalToteId()));
        assertSame(first, registry.find(first.physicalToteId()).orElseThrow());
        assertEquals(
                java.util.List.of(first.physicalToteId(), second.physicalToteId()),
                registry.snapshot().entries().stream()
                        .map(WarehouseTransportInFlightSnapshot.Entry::physicalToteId)
                        .toList());
    }

    @Test
    void shouldRejectDuplicateBeforeCapacityAndRecoverCapacityAfterCompletion() {
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(1);
        RoutedPhysicalTote tote = routedTote("tote-1", "p2p");
        registry.register(tote);

        assertThrows(IllegalArgumentException.class, () -> registry.register(tote));
        assertThrows(
                IllegalStateException.class,
                () -> registry.register(routedTote("tote-2", "p2p")));

        assertSame(tote, registry.completeArrival(tote));
        assertTrue(registry.canAccept());
        assertFalse(registry.contains(tote.physicalToteId()));
    }

    @Test
    void shouldSupportZeroCapacity() {
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(0);

        assertFalse(registry.canAccept());
        assertThrows(
                IllegalStateException.class,
                () -> registry.register(routedTote("tote-1", "p2p")));
        assertEquals(0, registry.snapshot().occupancy());
    }

    @Test
    void shouldRequireExactPayloadForPendingAndCompletion() {
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(2);
        RoutedPhysicalTote registered = routedTote("tote-1", "p2p");
        RoutedPhysicalTote sameIdentityCopy = routedTote("tote-1", "p2p");
        RoutedPhysicalTote unknown = routedTote("tote-2", "p2p");
        registry.register(registered);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.markArrivalPending(sameIdentityCopy));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.completeArrival(sameIdentityCopy));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.completeArrival(unknown));

        registry.markArrivalPending(registered);
        registry.markArrivalPending(registered);
        assertTrue(registry.snapshot().entries().get(0).arrivalPending());
        assertSame(registered, registry.completeArrival(registered));
    }

    @Test
    void shouldValidateArgumentsWithoutChangingToteMotion() {
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(1);
        RoutedPhysicalTote tote = routedTote("tote-1", "p2p");

        assertThrows(IllegalArgumentException.class, () -> registry.contains(null));
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertThrows(IllegalArgumentException.class, () -> registry.markArrivalPending(null));
        assertThrows(IllegalArgumentException.class, () -> registry.completeArrival(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightRegistry(-1));

        registry.register(tote);
        registry.markArrivalPending(tote);
        registry.completeArrival(tote);
        assertEquals(
                online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState.MOVING,
                tote.tote().getInteractionMode());
    }

    private static RoutedPhysicalTote routedTote(String physicalToteId, String targetId) {
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, targetId);
        return RoutedToteRoutingTestFixtures.routedTote(physicalToteId, destination);
    }
}
