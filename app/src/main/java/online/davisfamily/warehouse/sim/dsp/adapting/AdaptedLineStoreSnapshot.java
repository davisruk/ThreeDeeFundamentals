package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptedLineStoreSnapshot(
        int stagedLineCount,
        Set<PreparedLineKey> stagedLineKeys) {

    public AdaptedLineStoreSnapshot {
        if (stagedLineCount < 0) {
            throw new IllegalArgumentException("stagedLineCount must be >= 0");
        }
        if (stagedLineKeys == null) {
            throw new IllegalArgumentException("stagedLineKeys must not be null");
        }
        stagedLineKeys = Set.copyOf(stagedLineKeys);
    }
}
