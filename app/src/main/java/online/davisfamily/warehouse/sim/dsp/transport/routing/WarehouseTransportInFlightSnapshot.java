package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;

public record WarehouseTransportInFlightSnapshot(
        int capacity,
        List<Entry> entries) {

    public record Entry(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            String currentRouteSegmentLabel,
            ToteMotionState motionState,
            boolean arrivalPending) {

        public Entry {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteId must not be null");
            }
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (currentRouteSegmentLabel == null || currentRouteSegmentLabel.isBlank()) {
                throw new IllegalArgumentException(
                        "currentRouteSegmentLabel must not be blank");
            }
            if (motionState == null) {
                throw new IllegalArgumentException("motionState must not be null");
            }
            currentRouteSegmentLabel = currentRouteSegmentLabel.trim();
        }
    }

    public WarehouseTransportInFlightSnapshot {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }

        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            if (!physicalToteIds.add(entry.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate in-flight physical tote ID: "
                                + entry.physicalToteId().value());
            }
        }
        if (entries.size() > capacity) {
            throw new IllegalArgumentException(
                    "in-flight occupancy must not exceed capacity");
        }
        entries = List.copyOf(entries);
    }

    public int occupancy() {
        return entries.size();
    }

    public int remainingCapacity() {
        return capacity - occupancy();
    }

    public boolean canAccept() {
        return occupancy() < capacity;
    }
}
