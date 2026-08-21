package online.davisfamily.warehouse.sim.dsp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class OsrOutboundTransportQueueSnapshotTest {

    @Test
    void shouldExposeDerivedCapacityForEmptyPartialAndFullSnapshots() {
        OsrOutboundTransportQueueSnapshot empty = snapshot(2, List.of());
        assertEquals(0, empty.occupancy());
        assertEquals(2, empty.remainingCapacity());
        assertTrue(empty.canAccept());

        OsrOutboundTransportQueueSnapshot partial = snapshot(
                2, List.of(entry("tote-1", StationType.P2P, "p2p-1")));
        assertEquals(1, partial.occupancy());
        assertEquals(1, partial.remainingCapacity());
        assertTrue(partial.canAccept());

        OsrOutboundTransportQueueSnapshot full = snapshot(
                2,
                List.of(
                        entry("tote-1", StationType.P2P, "p2p-1"),
                        entry("tote-2", StationType.ADAPTING, "adapting-1")));
        assertEquals(2, full.occupancy());
        assertEquals(0, full.remainingCapacity());
        assertFalse(full.canAccept());
    }

    @Test
    void shouldTreatZeroCapacitySnapshotAsFull() {
        OsrOutboundTransportQueueSnapshot snapshot = snapshot(0, List.of());

        assertEquals(0, snapshot.remainingCapacity());
        assertFalse(snapshot.canAccept());
    }

    @Test
    void shouldNormalizeIdAndDefensivelyPreserveEntries() {
        List<OsrOutboundTransportQueueSnapshot.Entry> source = new ArrayList<>(List.of(
                entry("tote-1", StationType.THIRD_PARTY, "third-party-1")));

        OsrOutboundTransportQueueSnapshot snapshot =
                new OsrOutboundTransportQueueSnapshot("  transport  ", 2, source);
        source.clear();

        assertEquals("transport", snapshot.queueId());
        assertEquals(1, snapshot.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void shouldRejectInvalidSnapshotAndEntryState() {
        OsrOutboundTransportQueueSnapshot.Entry valid =
                entry("tote-1", StationType.P2P, "p2p-1");

        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot(null, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot(" ", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot("transport", -1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot("transport", 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot("transport", 1, listWithNull()));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(2, List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot.Entry(null, valid.destination()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundTransportQueueSnapshot.Entry(
                        valid.physicalToteId(), null));
    }

    private static OsrOutboundTransportQueueSnapshot snapshot(
            int capacity,
            List<OsrOutboundTransportQueueSnapshot.Entry> entries) {
        return new OsrOutboundTransportQueueSnapshot("transport", capacity, entries);
    }

    private static OsrOutboundTransportQueueSnapshot.Entry entry(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        return new OsrOutboundTransportQueueSnapshot.Entry(
                new PhysicalToteId(physicalToteId),
                new OperationalRouteDestination(stationType, targetId));
    }

    private static List<OsrOutboundTransportQueueSnapshot.Entry> listWithNull() {
        List<OsrOutboundTransportQueueSnapshot.Entry> entries = new ArrayList<>();
        entries.add(null);
        return entries;
    }
}
