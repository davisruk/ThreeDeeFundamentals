package online.davisfamily.warehouse.sim.dsp.adapting;

public record AdaptingBenchSnapshot(
        String benchId,
        AdaptingBenchState state,
        String activeToteId,
        AdaptingVisitType activeVisitType,
        double remainingProcessingSeconds,
        String blockedReason) {

    public AdaptingBenchSnapshot {
        if (benchId == null || benchId.isBlank()) {
            throw new IllegalArgumentException("benchId must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (remainingProcessingSeconds < 0d) {
            throw new IllegalArgumentException("remainingProcessingSeconds must be >= 0");
        }
        if (blockedReason == null) {
            blockedReason = "";
        }
    }
}
