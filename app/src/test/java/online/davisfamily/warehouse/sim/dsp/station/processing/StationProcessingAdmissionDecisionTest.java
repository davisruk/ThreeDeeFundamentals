package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StationProcessingAdmissionDecisionTest {

    @Test
    void shouldProvidePermitAndTrimmedDeferralFactories() {
        StationProcessingAdmissionDecision permit = StationProcessingAdmissionDecision.permit();
        StationProcessingAdmissionDecision defer =
                StationProcessingAdmissionDecision.defer("  target full  ");

        assertTrue(permit.permitted());
        assertEquals("", permit.reason());
        assertFalse(defer.permitted());
        assertEquals("target full", defer.reason());
    }

    @Test
    void shouldRejectContradictoryOrBlankReasons() {
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingAdmissionDecision(true, "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingAdmissionDecision(false, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> StationProcessingAdmissionDecision.defer(null));
    }
}
