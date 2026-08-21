package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class StationRoutedToteArrivalQueueSnapshotTest {

    @Test
    void shouldExposeDestinationAndDerivedCapacity() {
        OperationalRouteDestination destination = destination(
                StationType.P2P, "p2p-1");
        StationRoutedToteArrivalQueueSnapshot empty = snapshot(
                destination, 2, List.of());
        StationRoutedToteArrivalQueueSnapshot partial = snapshot(
                destination, 2, List.of(entry("tote-1", destination)));
        StationRoutedToteArrivalQueueSnapshot full = snapshot(
                destination,
                2,
                List.of(entry("tote-1", destination), entry("tote-2", destination)));

        assertSame(destination, empty.destination());
        assertEquals(0, empty.occupancy());
        assertEquals(2, empty.remainingCapacity());
        assertTrue(empty.canAccept());
        assertEquals(1, partial.occupancy());
        assertEquals(1, partial.remainingCapacity());
        assertTrue(partial.canAccept());
        assertEquals(2, full.occupancy());
        assertEquals(0, full.remainingCapacity());
        assertFalse(full.canAccept());
        assertFalse(snapshot(destination, 0, List.of()).canAccept());
    }

    @Test
    void shouldDefensivelyPreserveEntriesAndOldQueueState() {
        OperationalRouteDestination destination = destination(
                StationType.ADAPTING, "bench-1");
        List<StationRoutedToteArrivalQueueSnapshot.Entry> source =
                new ArrayList<>(List.of(entry("tote-1", destination)));
        StationRoutedToteArrivalQueueSnapshot snapshot = snapshot(
                destination, 2, source);
        source.clear();

        assertEquals(1, snapshot.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());

        StationRoutedToteArrivalQueue queue =
                new StationRoutedToteArrivalQueue(destination, 2);
        StationRoutedToteArrivalQueueSnapshot before = queue.snapshot();
        queue.enqueue(RoutedToteRoutingTestFixtures.routedTote("tote-2", destination));
        assertEquals(0, before.occupancy());
        assertEquals(1, queue.snapshot().occupancy());
    }

    @Test
    void shouldRejectInvalidSnapshotAndEntryState() {
        OperationalRouteDestination destination = destination(
                StationType.THIRD_PARTY, "third-party-1");
        StationRoutedToteArrivalQueueSnapshot.Entry valid =
                entry("tote-1", destination);

        assertThrows(IllegalArgumentException.class, () ->
                snapshot(null, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(destination, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(destination, 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(destination, 1, listWithNull()));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(destination, 2, List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(destination, 0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(
                        destination,
                        1,
                        List.of(entry(
                                "tote-2",
                                destination(StationType.THIRD_PARTY, "third-party-2")))));
        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalQueueSnapshot.Entry(null, destination));
        assertThrows(IllegalArgumentException.class, () ->
                new StationRoutedToteArrivalQueueSnapshot.Entry(
                        new PhysicalToteId("tote-1"), null));
    }

    private static StationRoutedToteArrivalQueueSnapshot snapshot(
            OperationalRouteDestination destination,
            int capacity,
            List<StationRoutedToteArrivalQueueSnapshot.Entry> entries) {
        return new StationRoutedToteArrivalQueueSnapshot(
                destination, capacity, entries);
    }

    private static StationRoutedToteArrivalQueueSnapshot.Entry entry(
            String physicalToteId,
            OperationalRouteDestination destination) {
        return new StationRoutedToteArrivalQueueSnapshot.Entry(
                new PhysicalToteId(physicalToteId), destination);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }

    private static List<StationRoutedToteArrivalQueueSnapshot.Entry> listWithNull() {
        List<StationRoutedToteArrivalQueueSnapshot.Entry> entries = new ArrayList<>();
        entries.add(null);
        return entries;
    }
}
