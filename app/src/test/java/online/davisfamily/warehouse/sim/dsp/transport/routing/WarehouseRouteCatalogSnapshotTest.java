package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class WarehouseRouteCatalogSnapshotTest {

    @Test
    void shouldExposeOnlyImmutableLabelsAndOrderedDestinationIdentity() {
        OperationalRouteDestination thirdParty = destination(
                StationType.THIRD_PARTY, "third-party-1");
        OperationalRouteDestination p2p = destination(
                StationType.P2P, "p2p-1");
        List<WarehouseRouteCatalogSnapshot.Entry> source = new ArrayList<>(List.of(
                entry(thirdParty, "third-party-sensor", "third-party-terminal"),
                entry(p2p, "p2p-sensor", "p2p-terminal")));

        WarehouseRouteCatalogSnapshot snapshot = new WarehouseRouteCatalogSnapshot(
                "  common-entry  ",
                1f,
                TravelDirection.FORWARD,
                source);
        source.clear();

        assertEquals("common-entry", snapshot.commonEntrySegmentLabel());
        assertEquals(1f, snapshot.entryDistance());
        assertEquals(TravelDirection.FORWARD, snapshot.entryDirection());
        assertEquals(List.of(thirdParty, p2p), snapshot.entries().stream()
                .map(WarehouseRouteCatalogSnapshot.Entry::destination)
                .toList());
        assertEquals("third-party-terminal",
                snapshot.entries().get(0).terminalSegmentLabel());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void shouldRejectInvalidSnapshotAndEntryState() {
        OperationalRouteDestination p2p = destination(
                StationType.P2P, "p2p-1");
        WarehouseRouteCatalogSnapshot.Entry valid =
                entry(p2p, "p2p-sensor", "p2p-terminal");

        assertThrows(IllegalArgumentException.class, () -> snapshot(
                null, 0f, TravelDirection.FORWARD, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                " ", 0f, TravelDirection.FORWARD, List.of(valid)));
        for (float invalidDistance : new float[] {
                -0.1f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        }) {
            assertThrows(IllegalArgumentException.class, () -> snapshot(
                    "entry", invalidDistance, TravelDirection.FORWARD, List.of(valid)));
        }
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry", 0f, null, List.of(valid)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry", 0f, TravelDirection.FORWARD, null));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry", 0f, TravelDirection.FORWARD, List.of()));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry", 0f, TravelDirection.FORWARD, listWithNull()));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry", 0f, TravelDirection.FORWARD, List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "entry",
                0f,
                TravelDirection.FORWARD,
                List.of(
                        valid,
                        entry(
                                destination(StationType.ADAPTING, "bench-1"),
                                "p2p-sensor",
                                "adapting-terminal"))));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalogSnapshot.Entry(
                        null, "sensor", "terminal"));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalogSnapshot.Entry(p2p, null, "terminal"));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalogSnapshot.Entry(p2p, " ", "terminal"));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalogSnapshot.Entry(p2p, "sensor", null));
        assertThrows(IllegalArgumentException.class, () ->
                new WarehouseRouteCatalogSnapshot.Entry(p2p, "sensor", " "));
    }

    private static WarehouseRouteCatalogSnapshot snapshot(
            String commonEntrySegmentLabel,
            float entryDistance,
            TravelDirection entryDirection,
            List<WarehouseRouteCatalogSnapshot.Entry> entries) {
        return new WarehouseRouteCatalogSnapshot(
                commonEntrySegmentLabel,
                entryDistance,
                entryDirection,
                entries);
    }

    private static WarehouseRouteCatalogSnapshot.Entry entry(
            OperationalRouteDestination destination,
            String sensorId,
            String terminalSegmentLabel) {
        return new WarehouseRouteCatalogSnapshot.Entry(
                destination, sensorId, terminalSegmentLabel);
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return RoutedToteRoutingTestFixtures.destination(stationType, targetId);
    }

    private static List<WarehouseRouteCatalogSnapshot.Entry> listWithNull() {
        List<WarehouseRouteCatalogSnapshot.Entry> entries = new ArrayList<>();
        entries.add(null);
        return entries;
    }
}
