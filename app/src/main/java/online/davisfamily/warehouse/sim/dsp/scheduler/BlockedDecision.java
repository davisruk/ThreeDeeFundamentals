package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.List;

public record BlockedDecision(String activeServiceCentreId, List<String> candidateOrderIds, List<String> blockReasons) {
    public BlockedDecision {
        if (activeServiceCentreId == null || activeServiceCentreId.isBlank()) {
            throw new IllegalArgumentException("activeServiceCentreId must not be blank");
        }
        if (candidateOrderIds == null || candidateOrderIds.isEmpty()) {
            throw new IllegalArgumentException("candidateOrderIds must not be empty");
        }
        if (blockReasons == null || blockReasons.isEmpty()) {
            throw new IllegalArgumentException("blockReasons must not be empty");
        }
        candidateOrderIds = List.copyOf(candidateOrderIds);
        blockReasons = List.copyOf(blockReasons);
    }
}
