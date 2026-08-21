package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OsrOutboundRouteLaunchQueueSnapshotTest {

    @Test
    void shouldExposeDerivedCapacityForEmptyPartialAndFullSnapshots() {
        OsrOutboundRouteLaunchQueueSnapshot empty = snapshot(2, List.of());

        assertEquals(0, empty.occupancy());
        assertEquals(2, empty.remainingCapacity());
        assertTrue(empty.canAccept());

        OsrOutboundRouteLaunchQueueSnapshot partial = snapshot(
                2, List.of(entry("tote-1", StationType.P2P, "p2p-1")));
        assertEquals(1, partial.occupancy());
        assertEquals(1, partial.remainingCapacity());
        assertTrue(partial.canAccept());

        OsrOutboundRouteLaunchQueueSnapshot full = snapshot(
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
        OsrOutboundRouteLaunchQueueSnapshot snapshot = snapshot(0, List.of());

        assertEquals(0, snapshot.occupancy());
        assertEquals(0, snapshot.remainingCapacity());
        assertFalse(snapshot.canAccept());
    }

    @Test
    void shouldNormalizeIdAndDefensivelyPreserveEntries() {
        List<OsrOutboundRouteLaunchQueueSnapshot.Entry> source = new ArrayList<>(List.of(
                entry("tote-1", StationType.THIRD_PARTY, "third-party-1")));

        OsrOutboundRouteLaunchQueueSnapshot snapshot =
                new OsrOutboundRouteLaunchQueueSnapshot("  outbound  ", 2, source);
        source.clear();

        assertEquals("outbound", snapshot.queueId());
        assertEquals(1, snapshot.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void shouldRejectInvalidSnapshotAndEntryState() {
        OsrOutboundRouteLaunchQueueSnapshot.Entry valid =
                entry("tote-1", StationType.P2P, "p2p-1");

        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot(null, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot(" ", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot("outbound", -1, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot("outbound", 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot("outbound", 1, listWithNull()));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(2, List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () ->
                snapshot(0, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot.Entry(null, valid.destination()));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrOutboundRouteLaunchQueueSnapshot.Entry(
                        valid.physicalToteId(), null));
    }

    private static OsrOutboundRouteLaunchQueueSnapshot snapshot(
            int capacity,
            List<OsrOutboundRouteLaunchQueueSnapshot.Entry> entries) {
        return new OsrOutboundRouteLaunchQueueSnapshot("outbound", capacity, entries);
    }

    private static OsrOutboundRouteLaunchQueueSnapshot.Entry entry(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        return new OsrOutboundRouteLaunchQueueSnapshot.Entry(
                new PhysicalToteId(physicalToteId),
                new OperationalRouteDestination(stationType, targetId));
    }

    private static List<OsrOutboundRouteLaunchQueueSnapshot.Entry> listWithNull() {
        List<OsrOutboundRouteLaunchQueueSnapshot.Entry> entries = new ArrayList<>();
        entries.add(null);
        return entries;
    }
}
