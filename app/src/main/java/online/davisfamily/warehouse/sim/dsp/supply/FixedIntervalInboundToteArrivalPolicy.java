package online.davisfamily.warehouse.sim.dsp.supply;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;

public final class FixedIntervalInboundToteArrivalPolicy implements InboundToteArrivalPolicy {
    private final String policyId;
    private final Duration interval;

    public FixedIntervalInboundToteArrivalPolicy(String policyId, Duration interval) {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (interval == null) {
            throw new IllegalArgumentException("interval must not be null");
        }
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.policyId = policyId.trim();
        this.interval = interval;
    }

    public static FixedIntervalInboundToteArrivalPolicy peak() {
        return new FixedIntervalInboundToteArrivalPolicy(
                "FIXED_PEAK_1200_PER_HOUR",
                Duration.ofSeconds(3));
    }

    public static FixedIntervalInboundToteArrivalPolicy representativeBusyHour() {
        return new FixedIntervalInboundToteArrivalPolicy(
                "FIXED_BUSY_400_PER_HOUR",
                Duration.ofSeconds(9));
    }

    @Override
    public String policyId() {
        return policyId;
    }

    public Duration interval() {
        return interval;
    }

    @Override
    public Duration intervalBeforeNextTote(
            InboundToteManifest nextManifest,
            long previouslyAdmittedToteCount) {
        if (nextManifest == null) {
            throw new IllegalArgumentException("nextManifest must not be null");
        }
        if (previouslyAdmittedToteCount < 0) {
            throw new IllegalArgumentException("previouslyAdmittedToteCount must be >= 0");
        }
        return interval;
    }
}
