package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

class OperationalRouteEntrySelectorTest {

    private final OperationalRouteEntrySelector selector = new OperationalRouteEntrySelector();

    @Test
    void shouldSelectThirdPartyBeforeAdaptingAndP2p() {
        RouteRequirements route = route(true, true, true, true, true);

        assertEquals(Optional.of(StationType.THIRD_PARTY), selector.firstStation(route));
    }

    @Test
    void shouldSelectAdaptingBeforeP2p() {
        assertEquals(
                Optional.of(StationType.ADAPTING),
                selector.firstStation(route(false, true, true, true, true)));
        assertEquals(
                Optional.of(StationType.MANUAL),
                selector.firstStation(route(false, false, true, true, true)));
        assertEquals(
                Optional.of(StationType.MANUAL_MERGE),
                selector.firstStation(route(false, false, false, false, true)));
    }

    @Test
    void shouldSelectP2pForDirectFullPack() {
        assertEquals(
                Optional.of(StationType.P2P),
                selector.firstStation(route(false, false, false, true, false)));
        assertThrows(IllegalArgumentException.class, () -> selector.firstStation(null));
    }

    private static RouteRequirements route(
            boolean thirdParty,
            boolean sortable,
            boolean manual,
            boolean p2p,
            boolean manualMerge) {
        return new RouteRequirements(
                thirdParty,
                sortable,
                manual,
                p2p,
                manualMerge,
                StartLocation.OSR);
    }
}
