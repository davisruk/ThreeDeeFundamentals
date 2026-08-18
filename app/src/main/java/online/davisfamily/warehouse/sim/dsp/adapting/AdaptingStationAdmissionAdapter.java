package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

public class AdaptingStationAdmissionAdapter {
    private final AdaptingArea area;
    private final StationCapacity capacity;
    private final AdaptingVisitFactory visitFactory;

    public AdaptingStationAdmissionAdapter(AdaptingArea area, StationCapacity capacity) {
        this(area, capacity, new AdaptingVisitFactory());
    }

    AdaptingStationAdmissionAdapter(
            AdaptingArea area,
            StationCapacity capacity,
            AdaptingVisitFactory visitFactory) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must not be null");
        }
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        this.area = area;
        this.capacity = capacity;
        this.visitFactory = visitFactory;
    }

    public StationAdmissionSnapshot admissionFor(DspSchedulerOrderState candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }

        AdaptingVisitProfile profile = visitFactory.profileFor(candidate.order());
        AdaptingBenchSelection selection = area.selectBenchFor(profile);
        StationSnapshot snapshot = area.stationSnapshot();
        boolean capacityOpen = capacity.canAccept(snapshot);
        boolean admissionOpen = capacityOpen && selection.accepted();

        String blockedReason = "";
        if (!selection.accepted()) {
            blockedReason = selection.blockedReason();
        } else if (!capacityOpen) {
            blockedReason = "Station ADAPTING has no capacity";
        }

        return new StationAdmissionSnapshot(
                StationType.ADAPTING,
                capacity,
                snapshot,
                admissionOpen,
                blockedReason,
                selection.accepted()
                        ? java.util.Optional.of(selection.benchId().value())
                        : java.util.Optional.empty());
    }

}
