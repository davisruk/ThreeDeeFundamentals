package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StationType;

public record StationSnapshot(StationType stationType, int inProgress, int queued) {
    public StationSnapshot {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (inProgress < 0) {
            throw new IllegalArgumentException("inProgress must be >= 0");
        }
        if (queued < 0) {
            throw new IllegalArgumentException("queued must be >= 0");
        }
    }
}
