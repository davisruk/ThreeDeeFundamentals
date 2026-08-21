package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public interface OperationalStationAdmissionResolver {
    StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot);
}
