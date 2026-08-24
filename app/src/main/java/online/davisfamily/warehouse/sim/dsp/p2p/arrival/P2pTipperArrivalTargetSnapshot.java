package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record P2pTipperArrivalTargetSnapshot(
        OperationalRouteDestination destination,
        int capacity,
        List<PhysicalToteId> queuedPhysicalToteIds,
        long acceptedCount) {

    public P2pTipperArrivalTargetSnapshot {
        if (destination == null || destination.stationType() != StationType.P2P) {
            throw new IllegalArgumentException("destination must identify a P2P station");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (queuedPhysicalToteIds == null) {
            throw new IllegalArgumentException("queuedPhysicalToteIds must not be null");
        }
        if (acceptedCount < 0) {
            throw new IllegalArgumentException("acceptedCount must be >= 0");
        }
        queuedPhysicalToteIds = List.copyOf(queuedPhysicalToteIds);
        if (queuedPhysicalToteIds.size() > capacity) {
            throw new IllegalArgumentException("queuedPhysicalToteIds must not exceed capacity");
        }
    }

    public int occupancy() {
        return queuedPhysicalToteIds.size();
    }

    public boolean canAccept() {
        return occupancy() < capacity;
    }
}
