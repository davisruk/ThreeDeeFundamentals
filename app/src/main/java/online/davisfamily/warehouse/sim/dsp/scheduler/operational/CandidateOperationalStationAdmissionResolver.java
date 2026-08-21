package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public final class CandidateOperationalStationAdmissionResolver
        implements OperationalStationAdmissionResolver {

    @Override
    public StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return snapshot.findRouteAdmission(
                candidate.physicalCandidate().physicalToteId(), stationType)
                .map(OperationalCandidateRouteAdmission::stationAdmission)
                .orElse(null);
    }
}
