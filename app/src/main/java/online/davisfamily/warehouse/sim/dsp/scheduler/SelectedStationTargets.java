package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record SelectedStationTargets(Map<StationType, String> selectedTargetIds) {

    public SelectedStationTargets {
        if (selectedTargetIds == null) {
            throw new IllegalArgumentException("selectedTargetIds must not be null");
        }
        Map<StationType, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<StationType, String> entry : selectedTargetIds.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("selectedTargetIds must not contain null station types");
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("selected target id must not be blank for station " + entry.getKey());
            }
            normalized.put(entry.getKey(), entry.getValue().trim());
        }
        selectedTargetIds = Map.copyOf(normalized);
    }

    public static SelectedStationTargets empty() {
        return new SelectedStationTargets(Map.of());
    }

    public Optional<String> selectedTargetIdFor(StationType stationType) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        return Optional.ofNullable(selectedTargetIds.get(stationType));
    }

    public boolean isEmpty() {
        return selectedTargetIds.isEmpty();
    }
}
