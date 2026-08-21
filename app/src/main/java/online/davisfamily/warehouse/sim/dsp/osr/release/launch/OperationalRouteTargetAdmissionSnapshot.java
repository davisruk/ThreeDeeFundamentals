package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record OperationalRouteTargetAdmissionSnapshot(
        StationType stationType,
        String targetId,
        int capacity,
        int occupancy) {

    public OperationalRouteTargetAdmissionSnapshot {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (occupancy < 0 || occupancy > capacity) {
            throw new IllegalArgumentException(
                    "occupancy must be between zero and capacity");
        }
        targetId = targetId.trim();
    }

    public int remainingCapacity() {
        return capacity - occupancy;
    }

    public boolean canAccept() {
        return occupancy < capacity;
    }
}
