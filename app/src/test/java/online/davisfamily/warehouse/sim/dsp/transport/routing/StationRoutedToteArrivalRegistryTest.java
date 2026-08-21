package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class StationRoutedToteArrivalRegistryTest {

    @Test
    void shouldPreserveExactQueueOrderAndLookupByNormalizedTargetId() {
        StationRoutedToteArrivalQueue thirdParty = queue(
                StationType.THIRD_PARTY, "third-party-1", 1);
        StationRoutedToteArrivalQueue adapting = queue(
                StationType.ADAPTING, "bench-1", 2);
        StationRoutedToteArrivalQueue p2p = queue(
                StationType.P2P, "p2p-1", 3);
        StationRoutedToteArrivalRegistry registry =
                new StationRoutedToteArrivalRegistry(List.of(
                        thirdParty, adapting, p2p));

        assertEquals(List.of(thirdParty, adapting, p2p), registry.queues());
        assertSame(thirdParty, registry.find("third-party-1").orElseThrow());
        assertSame(adapting, registry.find("  bench-1  ").orElseThrow());
        assertSame(p2p, registry.find("p2p-1").orElseThrow());
        assertTrue(registry.find("missing").isEmpty());
        assertEquals(
                List.of("third-party-1", "bench-1", "p2p-1"),
                registry.snapshots().stream()
                        .map(snapshot -> snapshot.destination().targetId())
                        .toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.queues().clear());
    }

    @Test
    void shouldReturnFreshSnapshotsWithoutChangingOldSnapshots() {
        OperationalRouteDestination destination = destination(
                StationType.P2P, "p2p-1");
        StationRoutedToteArrivalQueue queue =
                new StationRoutedToteArrivalQueue(destination, 1);
        StationRoutedToteArrivalRegistry registry =
                new StationRoutedToteArrivalRegistry(List.of(queue));

        List<StationRoutedToteArrivalQueueSnapshot> before = registry.snapshots();
        queue.enqueue(RoutedToteRoutingTestFixtures.routedTote("tote-1", destination));
        List<StationRoutedToteArrivalQueueSnapshot> after = registry.snapshots();

        assertEquals(0, before.get(0).occupancy());
        assertEquals(1, after.get(0).occupancy());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertThrows(UnsupportedOperationException.class, () ->
                after.get(0).entries().clear());
    }

    @Test
    void shouldRejectDuplicateTargetIdsAcrossDifferentStationsAndInvalidInput() {
        StationRoutedToteArrivalQueue p2p = queue(
                StationType.P2P, "shared-target", 1);
        StationRoutedToteArrivalQueue adapting = queue(
                StationType.ADAPTING, "shared-target", 1);

        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalRegistry(null));
        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalRegistry(Arrays.asList(p2p, null)));
        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalRegistry(List.of(p2p, p2p)));
        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalRegistry(List.of(p2p, adapting)));

        StationRoutedToteArrivalRegistry empty =
                new StationRoutedToteArrivalRegistry(new ArrayList<>());
        assertTrue(empty.queues().isEmpty());
        assertTrue(empty.snapshots().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> empty.find(null));
        assertThrows(IllegalArgumentException.class, () -> empty.find(" "));
    }

    private static StationRoutedToteArrivalQueue queue(
            StationType stationType,
            String targetId,
            int capacity) {
        return new StationRoutedToteArrivalQueue(
                destination(stationType, targetId), capacity);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }
}
