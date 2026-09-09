package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;

/** Immutable cumulative metrics and current activity for one P2P line. */
public record DspP2pLineMetricsSnapshot(
        P2pLineId lineId,
        Optional<String> serviceCentreId,
        boolean feeding,
        boolean draining,
        P2pLineActivitySnapshot activity,
        Duration observedSimulationDuration,
        Duration busySimulationDuration,
        double utilization,
        long consumedToteCount,
        double consumedToteRate,
        long allocatedBagCount,
        double allocatedBagRate,
        long closedOutboundToteCount,
        double closedOutboundToteRate) {

    public DspP2pLineMetricsSnapshot {
        if (lineId == null || serviceCentreId == null || activity == null) {
            throw new IllegalArgumentException("P2P line metric values must not be null");
        }
        serviceCentreId = serviceCentreId.map(value -> requireValue(value, "serviceCentreId"));
        if (observedSimulationDuration == null || observedSimulationDuration.isNegative()
                || busySimulationDuration == null || busySimulationDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "line metric durations must be nonnegative");
        }
        if (busySimulationDuration.compareTo(observedSimulationDuration) > 0) {
            throw new IllegalArgumentException(
                    "busySimulationDuration must not exceed observedSimulationDuration");
        }
        requireRatio(utilization, "utilization", 0d, 1d);
        requireRatio(consumedToteRate, "consumedToteRate", 0d, Double.MAX_VALUE);
        requireRatio(allocatedBagRate, "allocatedBagRate", 0d, Double.MAX_VALUE);
        requireRatio(closedOutboundToteRate, "closedOutboundToteRate", 0d, Double.MAX_VALUE);
        if (consumedToteCount < 0 || allocatedBagCount < 0 || closedOutboundToteCount < 0) {
            throw new IllegalArgumentException("line metric counts must be nonnegative");
        }
    }

    public Optional<String> owner() {
        return serviceCentreId;
    }

    private static void requireRatio(double value, String fieldName, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + " must be finite and in range");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
