package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssue;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionOutcome;

/** Immutable service-centre metrics at the current full-day snapshot boundary. */
public record DspServiceCentreMetricsSnapshot(
        String serviceCentreId,
        int priority,
        ServiceCentreAuthorizationState supplyState,
        Optional<Duration> authorizationElapsedTime,
        int upstreamWaitingCount,
        ServiceCentreDeadlineSnapshot deadline,
        Optional<Duration> completionElapsedTime,
        Optional<LocalDateTime> completionDateTime,
        DspServiceCentreCompletionOutcome completionOutcome,
        boolean complete,
        int unfinishedSheetCount,
        int unfinishedToteCount,
        int unfinishedPackCount,
        int unfinishedBagCount,
        Optional<Duration> targetLateness,
        Optional<Duration> latestAllowedLateness,
        int requiredLineCount,
        int desiredLineCount,
        int ownedLineCount,
        int unmetLineCount,
        boolean elasticInfeasible,
        List<P2pElasticAllocationIssue> elasticIssues,
        List<String> unsupportedWork,
        Map<DspFullDayBlockCategory, BlockSummary> blockSummaries) {

    public DspServiceCentreMetricsSnapshot {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (priority <= 0 || supplyState == null || authorizationElapsedTime == null
                || deadline == null || completionElapsedTime == null
                || completionDateTime == null || completionOutcome == null) {
            throw new IllegalArgumentException("service-centre metric values are invalid");
        }
        if (authorizationElapsedTime.isPresent()
                && authorizationElapsedTime.orElseThrow().isNegative()) {
            throw new IllegalArgumentException(
                    "authorizationElapsedTime must be nonnegative");
        }
        if (!serviceCentreId.equals(deadline.serviceCentreId())
                || priority != deadline.priority()) {
            throw new IllegalArgumentException(
                    "deadline identity must match service-centre metrics");
        }
        if (completionElapsedTime.isPresent() != completionDateTime.isPresent()) {
            throw new IllegalArgumentException(
                    "completion elapsed time and date-time must be present together");
        }
        if (complete != completionElapsedTime.isPresent()) {
            throw new IllegalArgumentException(
                    "complete must match completion elapsed-time presence");
        }
        if (upstreamWaitingCount < 0 || unfinishedSheetCount < 0 || unfinishedToteCount < 0
                || unfinishedPackCount < 0 || unfinishedBagCount < 0 || requiredLineCount < 0
                || desiredLineCount < 0 || ownedLineCount < 0 || unmetLineCount < 0) {
            throw new IllegalArgumentException("service-centre metric counts must be nonnegative");
        }
        elasticIssues = copyIssues(elasticIssues);
        unsupportedWork = copyStrings(unsupportedWork, "unsupportedWork");
        if (blockSummaries == null) {
            throw new IllegalArgumentException("blockSummaries must not be null");
        }
        EnumMap<DspFullDayBlockCategory, BlockSummary> blockCopy =
                new EnumMap<>(DspFullDayBlockCategory.class);
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            BlockSummary summary = blockSummaries.get(category);
            if (summary == null) {
                throw new IllegalArgumentException(
                        "blockSummaries must contain every block category");
            }
            blockCopy.put(category, summary);
        }
        if (blockSummaries.size() != blockCopy.size()) {
            throw new IllegalArgumentException("blockSummaries contains an unknown category");
        }
        blockSummaries = Collections.unmodifiableMap(blockCopy);
    }

    public BlockSummary block(DspFullDayBlockCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        return blockSummaries.get(category);
    }

    public Optional<Duration> targetLatenessDuration() {
        return targetLateness;
    }

    public Optional<Duration> latestAllowedLatenessDuration() {
        return latestAllowedLateness;
    }

    public record BlockSummary(
            long blockedUnitCount,
            Duration blockedSimulationDuration,
            Optional<String> latestReason) {

        public BlockSummary {
            if (blockedUnitCount < 0 || blockedSimulationDuration == null
                    || blockedSimulationDuration.isNegative() || latestReason == null) {
                throw new IllegalArgumentException("block summary values are invalid");
            }
            latestReason = latestReason.map(value -> requireValue(value, "latestReason"));
        }

        public static BlockSummary zero() {
            return new BlockSummary(0, Duration.ZERO, Optional.empty());
        }
    }

    private static List<P2pElasticAllocationIssue> copyIssues(
            List<P2pElasticAllocationIssue> issues) {
        if (issues == null || issues.stream().anyMatch(issue -> issue == null)) {
            throw new IllegalArgumentException("elasticIssues must not contain null");
        }
        return List.copyOf(issues);
    }

    private static List<String> copyStrings(List<String> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return values.stream().map(String::trim).toList();
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
