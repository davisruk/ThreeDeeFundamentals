package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record OperationalRouteEntryQueueSnapshot(
        StationType stationType,
        String targetId,
        int capacity,
        List<PhysicalToteId> physicalToteIds) {

    public OperationalRouteEntryQueueSnapshot {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (physicalToteIds == null) {
            throw new IllegalArgumentException("physicalToteIds must not be null");
        }

        Set<PhysicalToteId> distinctPhysicalToteIds = new LinkedHashSet<>();
        for (PhysicalToteId physicalToteId : physicalToteIds) {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteIds must not contain null");
            }
            if (!distinctPhysicalToteIds.add(physicalToteId)) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in route-entry queue: "
                                + physicalToteId.value());
            }
        }
        if (physicalToteIds.size() > capacity) {
            throw new IllegalArgumentException("queue occupancy must not exceed capacity");
        }

        targetId = targetId.trim();
        physicalToteIds = List.copyOf(physicalToteIds);
    }

    public int occupancy() {
        return physicalToteIds.size();
    }

    public int remainingCapacity() {
        return capacity - occupancy();
    }

    public boolean canAccept() {
        return occupancy() < capacity;
    }
}
