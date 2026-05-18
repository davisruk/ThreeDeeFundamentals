package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class AdaptingStationAdmissionResolver implements StationAdmissionResolver {
    private final StationAdmissionResolver fallbackResolver;
    private final AdaptingStationAdmissionAdapter adapter;

    public AdaptingStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            AdaptingArea area,
            StationCapacity capacity) {
        if (fallbackResolver == null) {
            throw new IllegalArgumentException("fallbackResolver must not be null");
        }
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must not be null");
        }
        this.fallbackResolver = fallbackResolver;
        this.adapter = new AdaptingStationAdmissionAdapter(area, capacity);
    }

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
        if (stationType != StationType.ADAPTING) {
            return fallbackResolver.admissionFor(stationType, candidate, snapshot);
        }
        return adapter.admissionFor(candidate);
    }
}
