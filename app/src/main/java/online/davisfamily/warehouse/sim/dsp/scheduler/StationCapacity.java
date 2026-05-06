package online.davisfamily.warehouse.sim.dsp.scheduler;

public record StationCapacity(int maxInProgress, int queueLimit) {
    public StationCapacity {
        if (maxInProgress < 0) {
            throw new IllegalArgumentException("maxInProgress must be >= 0");
        }
        if (queueLimit < 0) {
            throw new IllegalArgumentException("queueLimit must be >= 0");
        }
    }

    public boolean canAccept(StationSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return snapshot.inProgress() < maxInProgress || snapshot.queued() < queueLimit;
    }
}
