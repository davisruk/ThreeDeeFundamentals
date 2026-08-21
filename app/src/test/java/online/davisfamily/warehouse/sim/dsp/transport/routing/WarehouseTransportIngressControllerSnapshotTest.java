package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class WarehouseTransportIngressControllerSnapshotTest {

    @Test
    void shouldExposeDerivedCapacityAndBlockState() {
        PhysicalToteId toteId = new PhysicalToteId("tote-1");
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");
        WarehouseTransportIngressControllerSnapshot snapshot =
                new WarehouseTransportIngressControllerSnapshot(
                        4,
                        2,
                        3,
                        1,
                        Optional.of(toteId),
                        Optional.of(destination),
                        Optional.of(toteId),
                        Optional.of(destination),
                        Optional.of(toteId),
                        " blocked ",
                        1);

        assertEquals(2, snapshot.transportRemainingCapacity());
        assertEquals(2, snapshot.inFlightRemainingCapacity());
        assertTrue(snapshot.blocked());
        assertEquals("blocked", snapshot.blockedReason());
    }

    @Test
    void shouldRejectInconsistentSnapshotState() {
        PhysicalToteId toteId = new PhysicalToteId("tote-1");
        OperationalRouteDestination destination =
                RoutedToteRoutingTestFixtures.destination(StationType.P2P, "p2p");

        assertInvalid(-1, 0, 1, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "", 0);
        assertInvalid(1, 2, 1, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "", 0);
        assertInvalid(1, 0, 1, 0, Optional.of(toteId), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "", 0);
        assertInvalid(1, 0, 1, 0, Optional.empty(), Optional.empty(),
                Optional.of(toteId), Optional.empty(), Optional.empty(), "", 1);
        assertInvalid(1, 0, 1, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(toteId), "", 0);
        assertInvalid(1, 0, 1, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "blocked", 0);
        assertInvalid(1, 0, 1, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), "", 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportIngressControllerSnapshot(
                        1, 0, 1, 0, null, Optional.of(destination),
                        Optional.empty(), Optional.empty(), Optional.empty(), "", 0));
    }

    private static void assertInvalid(
            int transportCapacity,
            int transportOccupancy,
            int inFlightCapacity,
            int inFlightOccupancy,
            Optional<PhysicalToteId> headId,
            Optional<OperationalRouteDestination> headDestination,
            Optional<PhysicalToteId> lastId,
            Optional<OperationalRouteDestination> lastDestination,
            Optional<PhysicalToteId> blockedId,
            String blockedReason,
            long successfulCount) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarehouseTransportIngressControllerSnapshot(
                        transportCapacity,
                        transportOccupancy,
                        inFlightCapacity,
                        inFlightOccupancy,
                        headId,
                        headDestination,
                        lastId,
                        lastDestination,
                        blockedId,
                        blockedReason,
                        successfulCount));
    }
}
