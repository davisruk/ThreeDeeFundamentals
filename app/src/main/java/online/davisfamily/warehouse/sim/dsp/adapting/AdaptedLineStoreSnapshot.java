package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.Set;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record AdaptedLineStoreSnapshot(
        int stagedLineCount,
        Set<PreparedLineKey> stagedLineKeys,
        Map<AdaptingBenchId, Integer> stagedLineCountByBench,
        int activeRackCount,
        int activeShelfCount,
        int activeBinCount) {

    public AdaptedLineStoreSnapshot {
        if (stagedLineCount < 0) {
            throw new IllegalArgumentException("stagedLineCount must be >= 0");
        }
        if (stagedLineKeys == null) {
            throw new IllegalArgumentException("stagedLineKeys must not be null");
        }
        if (stagedLineCountByBench == null) {
            throw new IllegalArgumentException("stagedLineCountByBench must not be null");
        }
        if (activeRackCount < 0 || activeShelfCount < 0 || activeBinCount < 0) {
            throw new IllegalArgumentException("active storage counts must be >= 0");
        }
        stagedLineKeys = Set.copyOf(stagedLineKeys);
        stagedLineCountByBench = Map.copyOf(stagedLineCountByBench);
    }
}
