package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;

public record P2pWorkloadCostConfig(
        Duration toteHandlingCost,
        Duration packProcessingCost,
        Duration baggingCost) {

    public P2pWorkloadCostConfig {
        toteHandlingCost = requireCost(toteHandlingCost, "toteHandlingCost");
        packProcessingCost = requireCost(packProcessingCost, "packProcessingCost");
        baggingCost = requireCost(baggingCost, "baggingCost");
        if (toteHandlingCost.isZero()
                && packProcessingCost.isZero()
                && baggingCost.isZero()) {
            throw new IllegalArgumentException("at least one workload cost must be positive");
        }
    }

    long toteHandlingNanos() {
        return toteHandlingCost.toNanos();
    }

    long packProcessingNanos() {
        return packProcessingCost.toNanos();
    }

    long baggingNanos() {
        return baggingCost.toNanos();
    }

    private static Duration requireCost(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        try {
            value.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " must fit in nanoseconds", exception);
        }
        return value;
    }
}
