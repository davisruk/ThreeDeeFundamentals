package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public class SnapshotStationAdmissionResolver implements StationAdmissionResolver {
    @Override
    public StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return snapshot.stationAdmissions().get(stationType);
    }
}
