package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OperationalRouteEntryQueueSnapshotTest {

    @Test
    void shouldNormalizeAndRetainSupportedTargetDefinition() {
        OperationalRouteTargetDefinition definition = new OperationalRouteTargetDefinition(
                StationType.THIRD_PARTY,
                "  third-party-ingress  ",
                2);

        assertEquals(StationType.THIRD_PARTY, definition.stationType());
        assertEquals("third-party-ingress", definition.targetId());
        assertEquals(2, definition.waitingCapacity());

        assertEquals(
                StationType.ADAPTING,
                new OperationalRouteTargetDefinition(
                        StationType.ADAPTING, "bench-1", 1).stationType());
        assertEquals(
                StationType.P2P,
                new OperationalRouteTargetDefinition(
                        StationType.P2P, "p2p-1", 0).stationType());
    }

    @Test
    void shouldRejectInvalidOrUnsupportedTargetDefinition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(null, "target", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(StationType.OSR, "target", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(StationType.MANUAL, "target", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(StationType.P2P, null, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(StationType.P2P, "  ", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteTargetDefinition(StationType.P2P, "target", -1));
    }

    @Test
    void shouldExposeDerivedCapacityForEmptyAndFullSnapshots() {
        OperationalRouteEntryQueueSnapshot empty = snapshot(2, List.of());

        assertEquals(0, empty.occupancy());
        assertEquals(2, empty.remainingCapacity());
        assertTrue(empty.canAccept());

        OperationalRouteEntryQueueSnapshot full = snapshot(
                2,
                List.of(tote("tote-1"), tote("tote-2")));

        assertEquals(2, full.occupancy());
        assertEquals(0, full.remainingCapacity());
        assertFalse(full.canAccept());
    }

    @Test
    void shouldTreatZeroCapacitySnapshotAsFull() {
        OperationalRouteEntryQueueSnapshot snapshot = snapshot(0, List.of());

        assertEquals(0, snapshot.occupancy());
        assertEquals(0, snapshot.remainingCapacity());
        assertFalse(snapshot.canAccept());
    }

    @Test
    void shouldNormalizeTargetAndDefensivelyPreserveFifoIdentityOrder() {
        List<PhysicalToteId> source = new ArrayList<>(List.of(
                tote("tote-1"),
                tote("tote-2")));
        OperationalRouteEntryQueueSnapshot snapshot = new OperationalRouteEntryQueueSnapshot(
                StationType.ADAPTING,
                "  bench-1  ",
                3,
                source);

        source.clear();

        assertEquals("bench-1", snapshot.targetId());
        assertEquals(List.of(tote("tote-1"), tote("tote-2")), snapshot.physicalToteIds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.physicalToteIds().clear());
    }

    @Test
    void shouldRejectInvalidSnapshotState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(null, "target", 1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(StationType.P2P, null, 1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(StationType.P2P, " ", 1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(StationType.P2P, "target", -1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(StationType.P2P, "target", 1, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(
                        StationType.P2P, "target", 1, listWithNull()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(
                        StationType.P2P,
                        "target",
                        2,
                        List.of(tote("tote-1"), tote("tote-1"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalRouteEntryQueueSnapshot(
                        StationType.P2P,
                        "target",
                        1,
                        List.of(tote("tote-1"), tote("tote-2"))));
    }

    private static OperationalRouteEntryQueueSnapshot snapshot(
            int capacity,
            List<PhysicalToteId> physicalToteIds) {
        return new OperationalRouteEntryQueueSnapshot(
                StationType.P2P,
                "p2p-ingress",
                capacity,
                physicalToteIds);
    }

    private static PhysicalToteId tote(String value) {
        return new PhysicalToteId(value);
    }

    private static List<PhysicalToteId> listWithNull() {
        List<PhysicalToteId> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
