package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

public class AdaptingStationAdmissionAdapter {
    private final AdaptingArea area;
    private final StationCapacity capacity;

    public AdaptingStationAdmissionAdapter(AdaptingArea area, StationCapacity capacity) {
        if (area == null) {
            throw new IllegalArgumentException("area must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must not be null");
        }
        this.area = area;
        this.capacity = capacity;
    }

    public StationAdmissionSnapshot admissionFor(DspSchedulerOrderState candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }

        AdaptingBenchSelection selection = area.selectBenchFor(visitFor(candidate.order()));
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

    private static AdaptingVisit visitFor(NotionalToteOrder order) {
        if (order.orderType() == OrderType.ADAPTED) {
            return AdaptingVisit.store(order.notionalToteId(), order.items());
        }
        return new AdaptingCollectVisitFactory().create(order.notionalToteId(), order);
    }
}
