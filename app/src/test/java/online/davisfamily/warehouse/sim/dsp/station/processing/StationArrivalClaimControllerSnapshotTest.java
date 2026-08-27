package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class StationArrivalClaimControllerSnapshotTest {

    @Test
    void shouldPublishImmutableSourceAndClaimValues() {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.ADAPTING, "bench-1");
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        var target = new StationProcessingTestTarget(destination, coordinator);
        var source = new online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue(
                destination, 2);
        var controller = new StationArrivalClaimController(
                new StationProcessingBinding(source, target));
        var first = StationProcessingTestFixtures.routedTote("snapshot-first", destination);
        var second = StationProcessingTestFixtures.routedTote("snapshot-second", destination);
        source.enqueue(first);
        source.enqueue(second);
        target.decision(StationProcessingAdmissionDecision.defer("bench occupied"));

        controller.update(new online.davisfamily.threedee.sim.framework.SimulationContext(), 0d);

        var snapshot = controller.snapshot();
        assertEquals(destination, snapshot.sourceDestination());
        assertEquals(destination, snapshot.destination());
        assertEquals(2, snapshot.sourceCapacity());
        assertEquals(2, snapshot.sourceOccupancy());
        assertEquals(Optional.of(first.physicalToteId()), snapshot.headPhysicalToteId());
        assertEquals(Optional.of(first.physicalToteId()), snapshot.blockedPhysicalToteId());
        assertEquals("bench occupied", snapshot.blockedReason());
        assertTrue(snapshot.blocked());
        assertTrue(snapshot.lastClaimedPhysicalToteId().isEmpty());
        assertEquals(0L, snapshot.successfulClaimCount());

        assertThrows(UnsupportedOperationException.class,
                () -> List.of(snapshot.headPhysicalToteId()).clear());
    }

    @Test
    void shouldValidateSnapshotValueInvariants() {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1");
        PhysicalToteId id = new PhysicalToteId("snapshot-id");

        assertTrue(new StationArrivalClaimControllerSnapshot(
                destination,
                0,
                0,
                Optional.empty(),
                Optional.empty(),
                "",
                Optional.empty(),
                0).headPhysicalToteId().isEmpty());
        assertFalse(new StationArrivalClaimControllerSnapshot(
                destination,
                1,
                1,
                Optional.of(id),
                Optional.of(id),
                "blocked",
                Optional.of(id),
                1).lastClaimedPhysicalToteId().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new StationArrivalClaimControllerSnapshot(
                        destination, 1, 0, Optional.of(id), Optional.empty(), "", Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StationArrivalClaimControllerSnapshot(
                        destination, 1, 1, Optional.empty(), Optional.empty(), "", Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StationArrivalClaimControllerSnapshot(
                        destination, 1, 0, Optional.empty(), Optional.of(id), "", Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StationArrivalClaimControllerSnapshot(
                        destination, 1, 0, Optional.empty(), Optional.empty(), "", Optional.empty(), 1));
    }
}
