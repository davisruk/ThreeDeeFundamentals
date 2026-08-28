package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

class StationRouteContinuationDecisionTest {

    @Test
    void shouldCreateImmutableContinuationDecisionWithExactlyOneDestination() {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.ADAPTING, "bench-1");

        StationRouteContinuationDecision decision =
                StationRouteContinuationDecision.continueTo(destination);

        assertTrue(decision.permitted());
        assertFalse(decision.deferred());
        assertEquals(Optional.of(destination), decision.destination());
        assertEquals(Optional.empty(), decision.deferralReason());
        assertEquals(Optional.of(destination), decision.nextDestination());
        assertEquals("", decision.reason());
        assertEquals(
                decision,
                new StationRouteContinuationDecision(Optional.of(destination), Optional.empty()));
    }

    @Test
    void shouldCreateTrimmedDeferralDecisionWithExactlyOneReason() {
        StationRouteContinuationDecision decision =
                StationRouteContinuationDecision.defer("  adapting capacity  ");

        assertFalse(decision.permitted());
        assertTrue(decision.deferred());
        assertEquals(Optional.empty(), decision.destination());
        assertEquals(Optional.of("adapting capacity"), decision.deferralReason());
        assertEquals("adapting capacity", decision.reason());
    }

    @Test
    void shouldRejectNullBlankAndAmbiguousValues() {
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.P2P, "p2p-1");

        assertThrows(IllegalArgumentException.class,
                () -> StationRouteContinuationDecision.continueTo(null));
        assertThrows(IllegalArgumentException.class,
                () -> StationRouteContinuationDecision.defer(null));
        assertThrows(IllegalArgumentException.class,
                () -> StationRouteContinuationDecision.defer("  "));
        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteContinuationDecision(null, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteContinuationDecision(Optional.empty(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteContinuationDecision(Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteContinuationDecision(Optional.of(destination),
                        Optional.of("blocked")));
        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteContinuationDecision(Optional.empty(), Optional.of(" ")));
    }
}
