package online.davisfamily.warehouse.sim.dsp.p2p;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

public class P2pCapacityStationAdapter {
    private final P2pAdmission p2pAdmission;
    private final P2pAdmissionSnapshot p2pSnapshot;
    private final StationCapacity capacity;
    private final StationSnapshot stationSnapshot;
    private final Optional<String> selectedTargetId;

    public P2pCapacityStationAdapter(
            P2pAdmission p2pAdmission,
            P2pAdmissionSnapshot p2pSnapshot,
            StationCapacity capacity,
            StationSnapshot stationSnapshot) {
        this(p2pAdmission, p2pSnapshot, capacity, stationSnapshot, Optional.empty());
    }

    public P2pCapacityStationAdapter(
            P2pAdmission p2pAdmission,
            P2pAdmissionSnapshot p2pSnapshot,
            StationCapacity capacity,
            StationSnapshot stationSnapshot,
            String targetId) {
        this(
                p2pAdmission,
                p2pSnapshot,
                capacity,
                stationSnapshot,
                Optional.of(requireTargetId(targetId)));
    }

    private P2pCapacityStationAdapter(
            P2pAdmission p2pAdmission,
            P2pAdmissionSnapshot p2pSnapshot,
            StationCapacity capacity,
            StationSnapshot stationSnapshot,
            Optional<String> selectedTargetId) {
        if (p2pAdmission == null) {
            throw new IllegalArgumentException("p2pAdmission must not be null");
        }
        if (p2pSnapshot == null) {
            throw new IllegalArgumentException("p2pSnapshot must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("capacity must not be null");
        }
        if (stationSnapshot == null) {
            throw new IllegalArgumentException("stationSnapshot must not be null");
        }
        if (stationSnapshot.stationType() != StationType.P2P) {
            throw new IllegalArgumentException("stationSnapshot stationType must be P2P");
        }
        this.p2pAdmission = p2pAdmission;
        this.p2pSnapshot = p2pSnapshot;
        this.capacity = capacity;
        this.stationSnapshot = stationSnapshot;
        this.selectedTargetId = selectedTargetId;
    }

    public StationAdmissionSnapshot admissionFor(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        P2pAdmissionResult result = p2pAdmission.canAdmit(order, p2pSnapshot);
        boolean capacityOpen = capacity.canAccept(stationSnapshot);
        boolean admissionOpen = capacityOpen && result.accepted();

        String blockedReason = "";
        if (!capacityOpen) {
            blockedReason = "Station P2P has no capacity";
        } else if (!result.accepted()) {
            blockedReason = result.rejectionReason();
        }

        return new StationAdmissionSnapshot(
                StationType.P2P,
                capacity,
                stationSnapshot,
                admissionOpen,
                blockedReason,
                admissionOpen ? selectedTargetId : Optional.empty());
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
