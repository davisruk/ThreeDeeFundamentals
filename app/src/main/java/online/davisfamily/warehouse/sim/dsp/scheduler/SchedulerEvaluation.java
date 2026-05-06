package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.Optional;

public record SchedulerEvaluation(Optional<ReleaseDecision> releaseDecision, Optional<BlockedDecision> blockedDecision) {
    public SchedulerEvaluation {
        if (releaseDecision == null) {
            throw new IllegalArgumentException("releaseDecision must not be null");
        }
        if (blockedDecision == null) {
            throw new IllegalArgumentException("blockedDecision must not be null");
        }
        if (releaseDecision.isPresent() && blockedDecision.isPresent()) {
            throw new IllegalArgumentException("releaseDecision and blockedDecision cannot both be present");
        }
    }

    public static SchedulerEvaluation release(ReleaseDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        return new SchedulerEvaluation(Optional.of(decision), Optional.empty());
    }

    public static SchedulerEvaluation blocked(BlockedDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        return new SchedulerEvaluation(Optional.empty(), Optional.of(decision));
    }

    public static SchedulerEvaluation nothingToRelease() {
        return new SchedulerEvaluation(Optional.empty(), Optional.empty());
    }
}
