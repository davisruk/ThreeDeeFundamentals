package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

public record P2pServiceCentreLineDemandSnapshot(
        String serviceCentreId,
        int priority,
        Duration authorizationElapsedTime,
        ServiceCentreDeadlineSnapshot deadline,
        P2pServiceCentreWorkloadSnapshot workload,
        Duration adjustedSingleLineWork,
        long rawRequiredLines,
        int requiredLines,
        int desiredLines,
        List<P2pLineId> feedingOwnedLineIds,
        List<P2pLineId> drainingSurplusLineIds,
        int additionalLineSlots,
        int unmetRequiredLines,
        boolean withinConcurrencyWindow,
        List<P2pElasticAllocationIssueType> issues) {

    public P2pServiceCentreLineDemandSnapshot {
        serviceCentreId = requireValue(serviceCentreId);
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        if (authorizationElapsedTime == null || authorizationElapsedTime.isNegative()) {
            throw new IllegalArgumentException(
                    "authorizationElapsedTime must be nonnull and nonnegative");
        }
        if (deadline == null || workload == null) {
            throw new IllegalArgumentException("deadline and workload must not be null");
        }
        if (!serviceCentreId.equals(deadline.serviceCentreId())
                || !serviceCentreId.equals(workload.serviceCentreId())
                || priority != deadline.priority()) {
            throw new IllegalArgumentException("demand identities must be consistent");
        }
        if (adjustedSingleLineWork == null || adjustedSingleLineWork.isNegative()) {
            throw new IllegalArgumentException(
                    "adjustedSingleLineWork must be nonnull and nonnegative");
        }
        if (rawRequiredLines < 0 || requiredLines < 1 || desiredLines < 0
                || additionalLineSlots < 0 || unmetRequiredLines < 0) {
            throw new IllegalArgumentException("line demand counts must be nonnegative");
        }
        feedingOwnedLineIds = distinctCopy(feedingOwnedLineIds, "feedingOwnedLineIds");
        drainingSurplusLineIds = distinctCopy(
                drainingSurplusLineIds, "drainingSurplusLineIds");
        Set<P2pLineId> overlap = new LinkedHashSet<>(feedingOwnedLineIds);
        overlap.retainAll(drainingSurplusLineIds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("feeding and draining line IDs must be disjoint");
        }
        if (additionalLineSlots != Math.max(0, desiredLines - feedingOwnedLineIds.size())) {
            throw new IllegalArgumentException("additionalLineSlots must match desired feeding");
        }
        if (unmetRequiredLines != Math.max(0, requiredLines - desiredLines)) {
            throw new IllegalArgumentException("unmetRequiredLines must match required demand");
        }
        if (!withinConcurrencyWindow
                && (desiredLines != 0 || !feedingOwnedLineIds.isEmpty()
                        || additionalLineSlots != 0)) {
            throw new IllegalArgumentException(
                    "a centre outside the concurrency window must not feed lines");
        }
        if (issues == null || issues.stream().anyMatch(issue -> issue == null)) {
            throw new IllegalArgumentException("issues must not be null or contain null");
        }
        issues = List.copyOf(new LinkedHashSet<>(issues));
    }

    public int ownedLineCount() {
        return feedingOwnedLineIds.size() + drainingSurplusLineIds.size();
    }

    public boolean infeasible() {
        return !issues.isEmpty();
    }

    private static List<P2pLineId> distinctCopy(List<P2pLineId> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not be null or contain null");
        }
        Set<P2pLineId> distinct = new LinkedHashSet<>(values);
        if (distinct.size() != values.size()) {
            throw new IllegalArgumentException(fieldName + " must contain distinct values");
        }
        return List.copyOf(values);
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        return value.trim();
    }
}
