package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;

class P2pArrivalRouteBindingTest {

    @Test
    void shouldAcceptDeterministicConnectedRoute() {
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment approach = segment("p2p-approach", 1f);
        RouteSegment tipperEntry = segment("p2p-tipper-entry", 2f);
        terminal.connectTo(approach);
        approach.connectTo(tipperEntry);

        P2pArrivalRouteBinding binding = new P2pArrivalRouteBinding(
                terminal,
                tipperEntry);

        assertEquals("p2p-terminal", binding.terminalSegmentLabel());
        assertEquals("p2p-tipper-entry", binding.tipperEntrySegmentLabel());
        assertEquals(2, binding.hopCount());
        assertTrue(binding.matchesTerminalSegment(terminal));
        assertTrue(binding.matchesTipperEntrySegment(tipperEntry));
        assertFalse(binding.matchesTerminalSegment(approach));
        assertFalse(binding.matchesTipperEntrySegment(approach));
    }

    @Test
    void shouldAcceptZeroHopBinding() {
        RouteSegment terminalAndEntry = segment("p2p-terminal-and-entry", 0f);

        P2pArrivalRouteBinding binding = new P2pArrivalRouteBinding(
                terminalAndEntry,
                terminalAndEntry);

        assertEquals(0, binding.hopCount());
        assertTrue(binding.matchesTerminalSegment(terminalAndEntry));
        assertTrue(binding.matchesTipperEntrySegment(terminalAndEntry));
    }

    @Test
    void shouldRejectMissingForwardPath() {
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment disconnectedEntry = segment("p2p-tipper-entry", 2f);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(terminal, disconnectedEntry));
    }

    @Test
    void shouldRejectCycleBeforeTipperEntry() {
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment approach = segment("p2p-approach", 1f);
        RouteSegment tipperEntry = segment("p2p-tipper-entry", 2f);
        terminal.connectTo(approach);
        approach.connectTo(terminal);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(terminal, tipperEntry));
    }

    @Test
    void shouldRejectAmbiguousForwardConnectionsBeforeTipperEntry() {
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment approach = segment("p2p-approach", 1f);
        RouteSegment alternate = segment("p2p-alternate", 1f);
        RouteSegment tipperEntry = segment("p2p-tipper-entry", 2f);
        terminal.connectTo(approach);
        terminal.connectTo(alternate);
        approach.connectTo(tipperEntry);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(terminal, tipperEntry));
    }

    @Test
    void shouldUseSegmentIdentityRatherThanLabels() {
        RouteSegment terminal = segment("p2p-terminal", 0f);
        RouteSegment connectedEntry = segment("p2p-tipper-entry", 1f);
        RouteSegment sameLabelButDifferentEntry = segment("p2p-tipper-entry", 2f);
        terminal.connectTo(connectedEntry);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(terminal, sameLabelButDifferentEntry));
    }

    @Test
    void shouldRejectNullSegments() {
        RouteSegment segment = segment("p2p-segment", 0f);

        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(null, segment));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pArrivalRouteBinding(segment, null));
    }

    private static RouteSegment segment(String label, float startX) {
        return new RouteSegment(
                label,
                new LinearSegment3(
                        new Vec3(startX, 0f, 0f),
                        new Vec3(startX + 1f, 0f, 0f),
                        false));
    }
}
