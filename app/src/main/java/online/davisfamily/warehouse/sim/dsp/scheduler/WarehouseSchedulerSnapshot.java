package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record WarehouseSchedulerSnapshot(
        List<DspSchedulerOrderState> orderStates,
        Map<StationType, StationAdmissionSnapshot> stationAdmissions,
        Set<PreparedLineKey> preparedLineKeys,
        Optional<String> activeServiceCentreId) {

    public WarehouseSchedulerSnapshot {
        if (orderStates == null) {
            throw new IllegalArgumentException("orderStates must not be null");
        }
        if (stationAdmissions == null) {
            throw new IllegalArgumentException("stationAdmissions must not be null");
        }
        if (preparedLineKeys == null) {
            throw new IllegalArgumentException("preparedLineKeys must not be null");
        }
        if (activeServiceCentreId == null) {
            throw new IllegalArgumentException("activeServiceCentreId must not be null");
        }
        orderStates = List.copyOf(orderStates);
        stationAdmissions = Map.copyOf(stationAdmissions);
        preparedLineKeys = Set.copyOf(preparedLineKeys);
    }
}
