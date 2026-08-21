package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OperationalBlockedCandidate(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        List<OperationalReleaseBlock> blocks) {

    public OperationalBlockedCandidate {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("blocks must not be empty");
        }
        if (blocks.stream().anyMatch(block -> block == null)) {
            throw new IllegalArgumentException("blocks must not contain null elements");
        }
        blocks = List.copyOf(blocks);
    }
}
