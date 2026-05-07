package online.davisfamily.warehouse.sim.dsp.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class DspSchedulerRuntimeState {
    private final List<DspSchedulerOrderState> orderStates;
    private final Map<StationType, StationAdmissionSnapshot> stationAdmissions;
    private final Set<PreparedLineKey> preparedLineKeys;
    private Optional<String> activeServiceCentreId;

    public DspSchedulerRuntimeState(WarehouseSchedulerSnapshot initialSnapshot) {
        if (initialSnapshot == null) {
            throw new IllegalArgumentException("initialSnapshot must not be null");
        }
        this.orderStates = new ArrayList<>(initialSnapshot.orderStates());
        this.stationAdmissions = new LinkedHashMap<>(initialSnapshot.stationAdmissions());
        this.preparedLineKeys = new LinkedHashSet<>(initialSnapshot.preparedLineKeys());
        this.activeServiceCentreId = initialSnapshot.activeServiceCentreId();
    }

    public WarehouseSchedulerSnapshot snapshot() {
        return new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                preparedLineKeys,
                activeServiceCentreId);
    }

    public void markReleased(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        for (int i = 0; i < orderStates.size(); i++) {
            DspSchedulerOrderState orderState = orderStates.get(i);
            if (!orderState.order().orderId().equals(orderId)) {
                continue;
            }
            if (orderState.status() == DspOrderStatus.COMPLETED) {
                throw new IllegalArgumentException("Cannot mark completed order as released: " + orderId);
            }
            if (orderState.status() != DspOrderStatus.WAITING && orderState.status() != DspOrderStatus.BLOCKED) {
                throw new IllegalArgumentException("Order is not releasable from status " + orderState.status() + ": " + orderId);
            }

            orderStates.set(i, orderState.withStatus(DspOrderStatus.RELEASED));
            activeServiceCentreId = Optional.of(orderState.order().serviceCentreId());
            return;
        }

        throw new IllegalArgumentException("Unknown order id: " + orderId);
    }

    public void replaceStationAdmission(StationAdmissionSnapshot stationAdmission) {
        if (stationAdmission == null) {
            throw new IllegalArgumentException("stationAdmission must not be null");
        }
        stationAdmissions.put(stationAdmission.stationType(), stationAdmission);
    }

    public void addPreparedLineKey(PreparedLineKey preparedLineKey) {
        if (preparedLineKey == null) {
            throw new IllegalArgumentException("preparedLineKey must not be null");
        }
        preparedLineKeys.add(preparedLineKey);
    }

    public void addPreparedLineKeys(Set<PreparedLineKey> preparedLineKeys) {
        if (preparedLineKeys == null) {
            throw new IllegalArgumentException("preparedLineKeys must not be null");
        }
        for (PreparedLineKey preparedLineKey : preparedLineKeys) {
            addPreparedLineKey(preparedLineKey);
        }
    }
}
