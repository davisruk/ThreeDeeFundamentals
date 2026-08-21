package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record OperationalRouteEntry(
        StationType stationType,
        String targetId) {

    public OperationalRouteEntry {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        targetId = targetId.trim();
    }
}
