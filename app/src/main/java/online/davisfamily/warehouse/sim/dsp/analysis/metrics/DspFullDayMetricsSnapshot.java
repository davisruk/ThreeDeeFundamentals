package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssue;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssueType;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

/** Immutable metrics boundary for one deterministic full-day execution. */
public record DspFullDayMetricsSnapshot(
        String profileId,
        String serviceCentreSupplyPolicyId,
        String orderEligibilityPolicyId,
        String candidateRankingPolicyId,
        String p2pLineAllocationPolicyId,
        String outboundAllocationPolicyId,
        String calibrationStatus,
        String completionMilestone,
        DspFullDayRuntimeState state,
        DspOperationalClockSnapshot clock,
        double requestedExecutionSpeed,
        double achievedExecutionSpeed,
        Duration observedSimulationDuration,
        Duration configuredInboundInterval,
        double configuredInboundTotesPerSecond,
        long admittedInboundToteCount,
        double actualInboundTotesPerSecond,
        long departedInboundToteCount,
        long osrNetFlow,
        Duration capacityBlockedDuration,
        long closedOutboundToteCount,
        double closedOutboundTotesPerSecond,
        long allocatedBagCount,
        double allocatedBagsPerSecond,
        List<DspFullDayOccupancySample> occupancySamples,
        int minimumOsrOccupancy,
        int maximumOsrOccupancy,
        double meanOsrOccupancy,
        Map<DspFullDayBlockCategory, Duration> blockDurations,
        List<DspServiceCentreMetricsSnapshot> serviceCentres,
        List<DspP2pLineMetricsSnapshot> p2pLines,
        List<ElasticInfeasibilityEvent> elasticInfeasibilityHistory,
        int ignoredManualMessageCount,
        int ignoredManualLineCount,
        int omittedOrderCount,
        List<UnresolvedProductLine> unresolvedProductLines,
        List<String> unsupportedWork) {

    public DspFullDayMetricsSnapshot {
        profileId = requireValue(profileId, "profileId");
        serviceCentreSupplyPolicyId = requireValue(
                serviceCentreSupplyPolicyId, "serviceCentreSupplyPolicyId");
        orderEligibilityPolicyId = requireValue(orderEligibilityPolicyId, "orderEligibilityPolicyId");
        candidateRankingPolicyId = requireValue(candidateRankingPolicyId, "candidateRankingPolicyId");
        p2pLineAllocationPolicyId = requireValue(
                p2pLineAllocationPolicyId, "p2pLineAllocationPolicyId");
        outboundAllocationPolicyId = requireValue(
                outboundAllocationPolicyId, "outboundAllocationPolicyId");
        calibrationStatus = requireValue(calibrationStatus, "calibrationStatus");
        completionMilestone = requireValue(completionMilestone, "completionMilestone");
        if (state == null || clock == null || observedSimulationDuration == null
                || observedSimulationDuration.isNegative() || configuredInboundInterval == null
                || configuredInboundInterval.isNegative() || configuredInboundInterval.isZero()
                || capacityBlockedDuration == null || capacityBlockedDuration.isNegative()) {
            throw new IllegalArgumentException("full-day metric values are invalid");
        }
        requireNonnegativeFinite(requestedExecutionSpeed, "requestedExecutionSpeed");
        requireNonnegativeFinite(achievedExecutionSpeed, "achievedExecutionSpeed");
        requireNonnegativeFinite(
                configuredInboundTotesPerSecond, "configuredInboundTotesPerSecond");
        requireNonnegativeFinite(actualInboundTotesPerSecond, "actualInboundTotesPerSecond");
        requireNonnegativeFinite(closedOutboundTotesPerSecond, "closedOutboundTotesPerSecond");
        requireNonnegativeFinite(allocatedBagsPerSecond, "allocatedBagsPerSecond");
        if (admittedInboundToteCount < 0 || departedInboundToteCount < 0
                || closedOutboundToteCount < 0 || allocatedBagCount < 0
                || ignoredManualMessageCount < 0 || ignoredManualLineCount < 0
                || omittedOrderCount < 0 || minimumOsrOccupancy < 0
                || maximumOsrOccupancy < minimumOsrOccupancy) {
            throw new IllegalArgumentException("full-day metric counts are invalid");
        }
        occupancySamples = copyValues(occupancySamples, "occupancySamples");
        if (blockDurations == null) {
            throw new IllegalArgumentException("blockDurations must not be null");
        }
        EnumMap<DspFullDayBlockCategory, Duration> blockCopy =
                new EnumMap<>(DspFullDayBlockCategory.class);
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            Duration duration = blockDurations.get(category);
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException(
                        "blockDurations must contain nonnegative values for every category");
            }
            blockCopy.put(category, duration);
        }
        if (blockDurations.size() != blockCopy.size()) {
            throw new IllegalArgumentException("blockDurations contains an unknown category");
        }
        blockDurations = Collections.unmodifiableMap(blockCopy);
        serviceCentres = copyValues(serviceCentres, "serviceCentres");
        p2pLines = copyValues(p2pLines, "p2pLines");
        elasticInfeasibilityHistory = copyValues(
                elasticInfeasibilityHistory, "elasticInfeasibilityHistory");
        unresolvedProductLines = copyValues(unresolvedProductLines, "unresolvedProductLines");
        unsupportedWork = copyStrings(unsupportedWork, "unsupportedWork");
    }

    public Duration osrCapacityBlockedDuration() {
        return capacityBlockedDuration;
    }

    public List<DspFullDayOccupancySample> occupancyHistory() {
        return occupancySamples;
    }

    public record ElasticInfeasibilityEvent(
            Duration elapsedSimulationTime,
            String serviceCentreId,
            P2pElasticAllocationIssueType type,
            String detail) {

        public ElasticInfeasibilityEvent {
            if (elapsedSimulationTime == null || elapsedSimulationTime.isNegative()) {
                throw new IllegalArgumentException(
                        "elapsedSimulationTime must be nonnegative");
            }
            serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
            detail = requireValue(detail, "detail");
        }

        public ElasticInfeasibilityEvent(
                Duration elapsedSimulationTime,
                P2pElasticAllocationIssue issue) {
            this(
                    elapsedSimulationTime,
                    issue == null ? null : issue.serviceCentreId(),
                    issue == null ? null : issue.type(),
                    issue == null ? null : issue.detail());
        }
    }

    private static <T> List<T> copyValues(List<T> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not be null or contain null");
        }
        return List.copyOf(values);
    }

    private static List<String> copyStrings(List<String> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return values.stream().map(String::trim).toList();
    }

    private static void requireNonnegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and nonnegative");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
