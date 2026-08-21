package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OperationalRouteDestinationTest {

    @Test
    void shouldSupportOperationalProcessingDestinationsAndNormalizeTargetId() {
        for (StationType stationType : List.of(
                StationType.THIRD_PARTY,
                StationType.ADAPTING,
                StationType.P2P)) {
            OperationalRouteDestination destination =
                    new OperationalRouteDestination(stationType, "  target-1  ");

            assertEquals(stationType, destination.stationType());
            assertEquals("target-1", destination.targetId());
        }
    }

    @Test
    void shouldRejectUnsupportedDestinationStations() {
        for (StationType stationType : List.of(
                StationType.OSR,
                StationType.AV02,
                StationType.MANUAL,
                StationType.MANUAL_MERGE,
                StationType.DISPATCH)) {
            assertThrows(IllegalArgumentException.class, () ->
                    new OperationalRouteDestination(stationType, "target-1"));
        }
    }

    @Test
    void shouldRejectNullStationAndBlankTargetId() {
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteDestination(null, "target-1"));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteDestination(StationType.P2P, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OperationalRouteDestination(StationType.P2P, " "));
    }
}
