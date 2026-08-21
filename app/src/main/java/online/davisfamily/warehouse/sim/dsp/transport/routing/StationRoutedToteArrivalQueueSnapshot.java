package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record StationRoutedToteArrivalQueueSnapshot(
        OperationalRouteDestination destination,
        int capacity,
        List<Entry> entries) {

    public record Entry(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination) {

        public Entry {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteId must not be null");
            }
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
        }
    }

    public StationRoutedToteArrivalQueueSnapshot {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
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
            if (!destination.equals(entry.destination())) {
                throw new IllegalArgumentException(
                        "entry destination must match station arrival destination");
            }
            if (!physicalToteIds.add(entry.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in station arrival queue: "
                                + entry.physicalToteId().value());
            }
        }
        if (entries.size() > capacity) {
            throw new IllegalArgumentException("queue occupancy must not exceed capacity");
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
