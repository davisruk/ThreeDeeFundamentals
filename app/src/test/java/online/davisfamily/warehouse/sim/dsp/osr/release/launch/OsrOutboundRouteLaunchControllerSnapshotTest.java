package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OsrOutboundRouteLaunchControllerSnapshotTest {

    private static final PhysicalToteId TOTE_ID = new PhysicalToteId("tote-1");
    private static final OperationalRouteDestination DESTINATION =
            new OperationalRouteDestination(StationType.P2P, "p2p-1");

    @Test
    void shouldExposeDerivedCapacityAndBlockedState() {
        OsrOutboundRouteLaunchControllerSnapshot snapshot = snapshot(
                3, 2, 2, 1,
                Optional.of(TOTE_ID), Optional.of(DESTINATION),
                Optional.empty(), Optional.empty(),
                Optional.of(TOTE_ID), "  waiting for transport  ", 0);

        assertEquals(1, snapshot.launchRemainingCapacity());
        assertEquals(1, snapshot.transportRemainingCapacity());
        assertTrue(snapshot.blocked());
        assertEquals("waiting for transport", snapshot.blockedReason());
    }

    @Test
    void shouldValidateOccupancyPairedFieldsAndHydrationHistory() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                -1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 2, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 0, 1, 0,
                Optional.of(TOTE_ID), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.of(TOTE_ID), Optional.of(DESTINATION),
                Optional.empty(), "", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.of(TOTE_ID), "", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "blocked", 0));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "", -1));

        OsrOutboundRouteLaunchControllerSnapshot empty = snapshot(
                1, 0, 1, 0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), "", 0);
        assertFalse(empty.blocked());
    }

    private static OsrOutboundRouteLaunchControllerSnapshot snapshot(
            int launchCapacity,
            int launchOccupancy,
            int transportCapacity,
            int transportOccupancy,
            Optional<PhysicalToteId> headPhysicalToteId,
            Optional<OperationalRouteDestination> headDestination,
            Optional<PhysicalToteId> lastHydratedPhysicalToteId,
            Optional<OperationalRouteDestination> lastHydratedDestination,
            Optional<PhysicalToteId> blockedPhysicalToteId,
            String blockedReason,
            long successfulHydrationCount) {
        return new OsrOutboundRouteLaunchControllerSnapshot(
                launchCapacity,
                launchOccupancy,
                transportCapacity,
                transportOccupancy,
                headPhysicalToteId,
                headDestination,
                lastHydratedPhysicalToteId,
                lastHydratedDestination,
                blockedPhysicalToteId,
                blockedReason,
                successfulHydrationCount);
    }
}
