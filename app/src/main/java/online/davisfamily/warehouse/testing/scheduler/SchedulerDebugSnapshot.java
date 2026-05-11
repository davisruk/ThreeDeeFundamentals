package online.davisfamily.warehouse.testing.scheduler;

import java.util.List;
import java.util.Optional;

public record SchedulerDebugSnapshot(
        String evaluationMode,
        boolean evaluationInFlight,
        Optional<Long> lastCompletedEvaluationSequence,
        Optional<String> activeServiceCentreId,
        List<String> waitingOrderIds,
        Optional<String> releaseOrderId,
        Optional<String> blockedServiceCentreId,
        List<String> blockedCandidateOrderIds,
        List<String> blockedReasons,
        Optional<String> lastAppliedOrderId,
        Optional<String> lastDeferredOrderId,
        Optional<String> lastDeferredReason,
        Optional<String> lastRejectedOrderId,
        Optional<String> lastRejectedReason) {

    public SchedulerDebugSnapshot {
        if (evaluationMode == null || evaluationMode.isBlank()
                || lastCompletedEvaluationSequence == null
                || activeServiceCentreId == null
                || waitingOrderIds == null
                || releaseOrderId == null
                || blockedServiceCentreId == null
                || blockedCandidateOrderIds == null
                || blockedReasons == null
                || lastAppliedOrderId == null
                || lastDeferredOrderId == null
                || lastDeferredReason == null
                || lastRejectedOrderId == null
                || lastRejectedReason == null) {
            throw new IllegalArgumentException("SchedulerDebugSnapshot values must not be null");
        }
        waitingOrderIds = List.copyOf(waitingOrderIds);
        blockedCandidateOrderIds = List.copyOf(blockedCandidateOrderIds);
        blockedReasons = List.copyOf(blockedReasons);
    }

    public static SchedulerDebugSnapshot empty() {
        return new SchedulerDebugSnapshot(
                "unknown",
                false,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
