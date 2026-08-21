package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.List;
import java.util.Optional;

public record OperationalRouteEntryEvaluation(
        Optional<OperationalRouteEntry> routeEntry,
        List<OperationalReleaseBlock> blocks) {

    public OperationalRouteEntryEvaluation {
        if (routeEntry == null) {
            throw new IllegalArgumentException("routeEntry must not be null");
        }
        if (blocks == null) {
            throw new IllegalArgumentException("blocks must not be null");
        }
        if (blocks.stream().anyMatch(block -> block == null)) {
            throw new IllegalArgumentException("blocks must not contain null elements");
        }
        blocks = List.copyOf(blocks);
    }
}
