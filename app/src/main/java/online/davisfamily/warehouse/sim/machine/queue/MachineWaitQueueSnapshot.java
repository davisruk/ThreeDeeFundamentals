package online.davisfamily.warehouse.sim.machine.queue;

import java.util.List;

public record MachineWaitQueueSnapshot(
        String id,
        int capacity,
        List<String> toteIds) {

    public MachineWaitQueueSnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        if (toteIds == null) {
            throw new IllegalArgumentException("toteIds must not be null");
        }
        toteIds = List.copyOf(toteIds);
    }

    public boolean canAccept() {
        return toteIds.size() < capacity;
    }
}
