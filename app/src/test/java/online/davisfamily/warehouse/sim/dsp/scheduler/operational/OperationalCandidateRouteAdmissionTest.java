package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OperationalCandidateRouteAdmissionTest {

    @Test
    void shouldRetainOpenAndClosedEffectiveAdmission() {
        OperationalCandidateRouteAdmission open = new OperationalCandidateRouteAdmission(
                new PhysicalToteId("tote-1"),
                OperationalRouteAdmissionTestSupport.openAdmission(
                        StationType.P2P, "p2p-ingress"));
        OperationalCandidateRouteAdmission closed = new OperationalCandidateRouteAdmission(
                new PhysicalToteId("tote-2"),
                OperationalRouteAdmissionTestSupport.closedAdmission(
                        StationType.P2P, null, "P2P closed"));

        assertEquals("p2p-ingress", open.stationAdmission()
                .selectedTargetId().orElseThrow());
        assertTrue(closed.stationAdmission().selectedTargetId().isEmpty());
        assertEquals("P2P closed", closed.stationAdmission().blockedReason());
    }

    @Test
    void shouldRejectInvalidOrTargetlessOpenAdmission() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmission(
                        null,
                        OperationalRouteAdmissionTestSupport.openAdmission(
                                StationType.P2P, "p2p-ingress")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmission(
                        new PhysicalToteId("tote-1"), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalCandidateRouteAdmission(
                        new PhysicalToteId("tote-1"),
                        OperationalRouteAdmissionTestSupport.openAdmission(
                                StationType.P2P, null)));
    }
}
