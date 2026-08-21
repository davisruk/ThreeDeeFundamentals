package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

class OperationalRouteEntryAdmissionPolicyTest {

    @Test
    void shouldUseCapturedCandidateTargetInsteadOfLegacyStationTarget() {
        DspOperationalReleaseCandidate candidate = OperationalRouteAdmissionTestSupport.candidate(
                "tote-1", OperationalRouteAdmissionTestSupport.route(StationType.P2P));
        DspOperationalReleaseSnapshot snapshot = new DspOperationalReleaseSnapshot(
                List.of(candidate),
                List.of(new ServiceCentrePharmacyGroup("sc-1", "pharmacy-1", 0, 1)),
                Map.of(
                        StationType.P2P,
                        OperationalRouteAdmissionTestSupport.openAdmission(
                                StationType.P2P, "legacy-target")),
                Set.of(),
                List.of(new OperationalCandidateRouteAdmission(
                        candidate.physicalCandidate().physicalToteId(),
                        OperationalRouteAdmissionTestSupport.openAdmission(
                                StationType.P2P, "candidate-target"))));

        OperationalRouteEntryEvaluation evaluation = new OperationalRouteEntryAdmissionPolicy()
                .evaluate(candidate, snapshot);

        assertEquals("candidate-target", evaluation.routeEntry().orElseThrow().targetId());
        assertTrue(evaluation.blocks().isEmpty());
    }

    @Test
    void shouldExposeCapturedQueueCapacityBlock() {
        DspOperationalReleaseCandidate candidate = OperationalRouteAdmissionTestSupport.candidate(
                "tote-1", OperationalRouteAdmissionTestSupport.route(StationType.P2P));
        DspOperationalReleaseSnapshot snapshot =
                OperationalRouteAdmissionTestSupport.operationalSnapshot(
                        List.of(candidate),
                        List.of(new OperationalCandidateRouteAdmission(
                                candidate.physicalCandidate().physicalToteId(),
                                OperationalRouteAdmissionTestSupport.closedAdmission(
                                        StationType.P2P,
                                        null,
                                        "Operational route admission target p2p-1 has no waiting capacity"))));

        OperationalRouteEntryEvaluation evaluation = new OperationalRouteEntryAdmissionPolicy()
                .evaluate(candidate, snapshot);

        assertTrue(evaluation.routeEntry().isEmpty());
        assertEquals(
                OperationalReleaseBlockType.STATION_ADMISSION,
                evaluation.blocks().get(0).type());
        assertEquals(
                "Operational route admission target p2p-1 has no waiting capacity",
                evaluation.blocks().get(0).reason());
    }
}
