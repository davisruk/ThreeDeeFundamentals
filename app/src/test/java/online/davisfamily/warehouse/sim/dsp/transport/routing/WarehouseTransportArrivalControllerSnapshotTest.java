package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class WarehouseTransportArrivalControllerSnapshotTest {

    @Test
    void shouldDefensivelyCopyPendingOrderAndExposeBlock() {
        PhysicalToteId toteId = new PhysicalToteId("tote-1");
        OperationalRouteDestination destination = destination();
        List<WarehouseTransportArrivalControllerSnapshot.PendingArrival> pending =
                new ArrayList<>();
        pending.add(new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                toteId, destination, "sensor"));
        WarehouseTransportArrivalControllerSnapshot snapshot =
                new WarehouseTransportArrivalControllerSnapshot(
                        pending,
                        Optional.of(toteId),
                        Optional.of(destination),
                        Optional.of(toteId),
                        " blocked ",
                        1);
        pending.clear();

        assertEquals(1, snapshot.pendingArrivals().size());
        assertEquals("blocked", snapshot.blockedReason());
        assertTrue(snapshot.blocked());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.pendingArrivals().clear());
    }

    @Test
    void shouldRejectInvalidSnapshotState() {
        PhysicalToteId toteId = new PhysicalToteId("tote-1");
        OperationalRouteDestination destination = destination();
        var pending = new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                toteId, destination, "sensor");

        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot(
                        null, Optional.empty(), Optional.empty(), Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot(
                        List.of(pending, pending), Optional.empty(), Optional.empty(),
                        Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot(
                        List.of(), Optional.of(toteId), Optional.empty(),
                        Optional.empty(), "", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot(
                        List.of(), Optional.empty(), Optional.empty(),
                        Optional.of(toteId), "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot(
                        List.of(), Optional.empty(), Optional.empty(),
                        Optional.empty(), "", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                        null, destination, "sensor"));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                        toteId, null, "sensor"));
        assertThrows(IllegalArgumentException.class,
                () -> new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                        toteId, destination, " "));
    }

    private static OperationalRouteDestination destination() {
        return RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");
    }
}
