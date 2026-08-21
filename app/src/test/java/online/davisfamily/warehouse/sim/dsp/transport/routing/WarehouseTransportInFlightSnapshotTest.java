package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

class WarehouseTransportInFlightSnapshotTest {

    @Test
    void shouldObserveCurrentRouteAndMotionWithoutChangingOldSnapshot() {
        OperationalRouteDestination destination = destination("p2p");
        RoutedPhysicalTote tote =
                RoutedToteRoutingTestFixtures.routedTote("tote-1", destination);
        WarehouseTransportInFlightRegistry registry =
                new WarehouseTransportInFlightRegistry(2);
        registry.register(tote);

        WarehouseTransportInFlightSnapshot before = registry.snapshot();
        RouteSegment laterSegment =
                RoutedToteRoutingTestFixtures.routeSegment("later-segment", 2f);
        tote.tote().getRouteFollower().setCurrentSegment(laterSegment);
        tote.tote().setInteractionMode(ToteMotionState.HELD);
        registry.markArrivalPending(tote);
        WarehouseTransportInFlightSnapshot after = registry.snapshot();

        assertEquals("route-tote-1", before.entries().get(0).currentRouteSegmentLabel());
        assertEquals(ToteMotionState.MOVING, before.entries().get(0).motionState());
        assertFalse(before.entries().get(0).arrivalPending());
        assertEquals("later-segment", after.entries().get(0).currentRouteSegmentLabel());
        assertEquals(ToteMotionState.HELD, after.entries().get(0).motionState());
        assertTrue(after.entries().get(0).arrivalPending());
    }

    @Test
    void shouldDefensivelyCopyEntriesAndExposeCapacityValues() {
        List<WarehouseTransportInFlightSnapshot.Entry> entries = new ArrayList<>();
        entries.add(entry("tote-1", "entry"));

        WarehouseTransportInFlightSnapshot snapshot =
                new WarehouseTransportInFlightSnapshot(2, entries);
        entries.clear();

        assertEquals(1, snapshot.occupancy());
        assertEquals(1, snapshot.remainingCapacity());
        assertTrue(snapshot.canAccept());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void shouldValidateSnapshotValues() {
        WarehouseTransportInFlightSnapshot.Entry entry = entry("tote-1", "entry");

        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot(-1, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot(1, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot(1, List.of(entry, entry)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot(0, List.of(entry)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot.Entry(
                        null, destination("p2p"), "entry", ToteMotionState.MOVING, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot.Entry(
                        new PhysicalToteId("tote-1"), null, "entry",
                        ToteMotionState.MOVING, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot.Entry(
                        new PhysicalToteId("tote-1"), destination("p2p"), " ",
                        ToteMotionState.MOVING, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportInFlightSnapshot.Entry(
                        new PhysicalToteId("tote-1"), destination("p2p"), "entry",
                        null, false));
    }

    private static WarehouseTransportInFlightSnapshot.Entry entry(
            String physicalToteId,
            String routeLabel) {
        return new WarehouseTransportInFlightSnapshot.Entry(
                new PhysicalToteId(physicalToteId),
                destination("p2p"),
                routeLabel,
                ToteMotionState.MOVING,
                false);
    }

    private static OperationalRouteDestination destination(String targetId) {
        return RoutedToteRoutingTestFixtures.destination(StationType.P2P, targetId);
    }
}
