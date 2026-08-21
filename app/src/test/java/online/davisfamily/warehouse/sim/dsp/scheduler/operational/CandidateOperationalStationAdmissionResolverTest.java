package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

class CandidateOperationalStationAdmissionResolverTest {

    @Test
    void shouldResolveOnlyExactCandidateAndStationAdmission() {
        DspOperationalReleaseCandidate first = candidate("tote-1");
        DspOperationalReleaseCandidate second = candidate("tote-2");
        OperationalCandidateRouteAdmission firstAdmission = admission(first, "p2p-1");
        OperationalCandidateRouteAdmission secondAdmission = admission(second, "p2p-2");
        DspOperationalReleaseSnapshot snapshot = OperationalRouteAdmissionTestSupport
                .operationalSnapshot(
                        List.of(first, second),
                        List.of(firstAdmission, secondAdmission));
        CandidateOperationalStationAdmissionResolver resolver =
                new CandidateOperationalStationAdmissionResolver();

        assertEquals(
                "p2p-1",
                resolver.admissionFor(StationType.P2P, first, snapshot)
                        .selectedTargetId().orElseThrow());
        assertEquals(
                "p2p-2",
                resolver.admissionFor(StationType.P2P, second, snapshot)
                        .selectedTargetId().orElseThrow());
        assertNull(resolver.admissionFor(StationType.THIRD_PARTY, first, snapshot));
        assertNull(resolver.admissionFor(
                StationType.P2P,
                candidate("missing"),
                snapshot));
    }

    @Test
    void shouldResolveCapturedAdmissionWithoutReadingMutatedQueue() {
        DspOperationalReleaseCandidate candidate = candidate("tote-1");
        OperationalRouteEntryQueue queue = OperationalRouteAdmissionTestSupport.queue(
                StationType.P2P, "p2p-1", 1);
        OperationalRouteTargetRegistry targets = new OperationalRouteTargetRegistry(List.of(queue));
        OperationalCandidateRouteAdmissionFactory factory =
                new OperationalCandidateRouteAdmissionFactory(
                        new OperationalRouteEntrySelector(),
                        (stationType, order, logicalSnapshot) ->
                                OperationalRouteAdmissionTestSupport.openAdmission(
                                        stationType, "p2p-1"),
                        targets);
        List<OperationalCandidateRouteAdmission> capturedAdmissions = factory.create(
                List.of(candidate),
                OperationalRouteAdmissionTestSupport.logicalSnapshot(List.of(candidate)));
        DspOperationalReleaseSnapshot snapshot = OperationalRouteAdmissionTestSupport
                .operationalSnapshot(List.of(candidate), capturedAdmissions);

        queue.enqueue(OperationalRouteAdmissionTestSupport.request(candidate));

        StationAdmissionSnapshot resolved = new CandidateOperationalStationAdmissionResolver()
                .admissionFor(StationType.P2P, candidate, snapshot);
        assertTrue(resolved.canAccept());
        assertEquals("p2p-1", resolved.selectedTargetId().orElseThrow());
        assertFalse(queue.snapshot().canAccept());
    }

    @Test
    void shouldValidateResolverInputs() {
        DspOperationalReleaseCandidate candidate = candidate("tote-1");
        DspOperationalReleaseSnapshot snapshot = OperationalRouteAdmissionTestSupport
                .operationalSnapshot(List.of(candidate), List.of());
        CandidateOperationalStationAdmissionResolver resolver =
                new CandidateOperationalStationAdmissionResolver();

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.admissionFor(null, candidate, snapshot));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.admissionFor(StationType.P2P, null, snapshot));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.admissionFor(StationType.P2P, candidate, null));
    }

    private static DspOperationalReleaseCandidate candidate(String physicalToteId) {
        return OperationalRouteAdmissionTestSupport.candidate(
                physicalToteId,
                OperationalRouteAdmissionTestSupport.route(StationType.P2P));
    }

    private static OperationalCandidateRouteAdmission admission(
            DspOperationalReleaseCandidate candidate,
            String targetId) {
        return new OperationalCandidateRouteAdmission(
                candidate.physicalCandidate().physicalToteId(),
                OperationalRouteAdmissionTestSupport.openAdmission(
                        StationType.P2P, targetId));
    }
}
