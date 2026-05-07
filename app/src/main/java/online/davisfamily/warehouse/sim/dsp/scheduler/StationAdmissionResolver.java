package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public interface StationAdmissionResolver {
    StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot);
}
