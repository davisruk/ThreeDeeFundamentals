package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public record OperationalCandidateRouteAdmission(
        PhysicalToteId physicalToteId,
        StationAdmissionSnapshot stationAdmission) {

    public OperationalCandidateRouteAdmission {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (stationAdmission == null) {
            throw new IllegalArgumentException("stationAdmission must not be null");
        }
        if (stationAdmission.canAccept() && stationAdmission.selectedTargetId().isEmpty()) {
            throw new IllegalArgumentException(
                    "Open candidate route admission must have a selected target ID");
        }
    }
}
