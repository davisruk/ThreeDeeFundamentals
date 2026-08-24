package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;

public record P2pElasticAllocationConfig(
        int p2pLineCount,
        int maximumConcurrentServiceCentres,
        int minimumReservedLinesForEarlierCentre,
        int safetyFactorPermille,
        int parallelEfficiencyPermille,
        Duration downstreamHandlingDuration,
        P2pWorkloadCostConfig workloadCostConfig) {

    public P2pElasticAllocationConfig {
        if (p2pLineCount < 1) {
            throw new IllegalArgumentException("p2pLineCount must be positive");
        }
        if (maximumConcurrentServiceCentres < 1
                || maximumConcurrentServiceCentres > p2pLineCount) {
            throw new IllegalArgumentException(
                    "maximumConcurrentServiceCentres must be between one and p2pLineCount");
        }
        if (minimumReservedLinesForEarlierCentre < 1
                || minimumReservedLinesForEarlierCentre > p2pLineCount) {
            throw new IllegalArgumentException(
                    "minimumReservedLinesForEarlierCentre must be between one and p2pLineCount");
        }
        if (safetyFactorPermille < 1) {
            throw new IllegalArgumentException("safetyFactorPermille must be positive");
        }
        if (parallelEfficiencyPermille < 1 || parallelEfficiencyPermille > 1000) {
            throw new IllegalArgumentException(
                    "parallelEfficiencyPermille must be between one and 1000");
        }
        if (downstreamHandlingDuration == null
                || downstreamHandlingDuration.isZero()
                || downstreamHandlingDuration.isNegative()) {
            throw new IllegalArgumentException("downstreamHandlingDuration must be positive");
        }
        if (workloadCostConfig == null) {
            throw new IllegalArgumentException("workloadCostConfig must not be null");
        }
    }

    public static P2pElasticAllocationConfig productionBaseline(
            P2pWorkloadCostConfig workloadCostConfig) {
        return new P2pElasticAllocationConfig(
                5,
                2,
                1,
                1000,
                1000,
                Duration.ofHours(1),
                workloadCostConfig);
    }
}
