package online.davisfamily.threedee.sim.framework.time;

import java.time.Duration;

public final class FixedStepExecutionDriver {

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final FixedStepExecutionConfig config;
    private final long fixedStepNanos;
    private long pendingSimulationNanos;
    private long completedStepCount;
    private long totalRealNanos;
    private long totalSimulationNanos;
    private long advanceCount;

    public FixedStepExecutionDriver(FixedStepExecutionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.fixedStepNanos = config.fixedStepNanos();
    }

    public FixedStepAdvance advance(Duration realElapsedTime, SimulationStepConsumer consumer) {
        long realElapsedNanos = toNanos(realElapsedTime, "realElapsedTime");
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }

        totalRealNanos = addExact(totalRealNanos, realElapsedNanos, "total real time");
        long callNumber = addExact(advanceCount, 1L, "advance count");
        advanceCount = callNumber;

        int executedStepCount;
        if (config.mode() == SimulationExecutionMode.HEADLESS_ANALYSIS) {
            pendingSimulationNanos = 0L;
            executedStepCount = config.maximumStepsPerAdvance();
        } else {
            long scaledNanos = scale(realElapsedNanos, config.requestedTimeScale());
            pendingSimulationNanos = addExact(
                    pendingSimulationNanos,
                    scaledNanos,
                    "pending simulation time");
            long availableStepCount = pendingSimulationNanos / fixedStepNanos;
            executedStepCount = (int) Math.min(
                    availableStepCount,
                    (long) config.maximumStepsPerAdvance());
        }

        double fixedStepSeconds = fixedStepNanos / (double) NANOSECONDS_PER_SECOND;
        for (int step = 0; step < executedStepCount; step++) {
            consumer.advance(fixedStepSeconds);
            completedStepCount = addExact(completedStepCount, 1L, "completed step count");
            totalSimulationNanos = addExact(
                    totalSimulationNanos,
                    fixedStepNanos,
                    "total simulation time");
            if (config.mode() != SimulationExecutionMode.HEADLESS_ANALYSIS) {
                pendingSimulationNanos -= fixedStepNanos;
            }
        }

        boolean renderDue = config.mode() != SimulationExecutionMode.HEADLESS_ANALYSIS
                && (callNumber - 1L) % config.renderEveryAdvanceCount() == 0L;
        return new FixedStepAdvance(
                executedStepCount,
                config.fixedStep(),
                Duration.ofNanos(Math.multiplyExact(
                        fixedStepNanos,
                        (long) executedStepCount)),
                Duration.ofNanos(pendingSimulationNanos),
                renderDue);
    }

    public FixedStepExecutionSnapshot snapshot() {
        double achievedTimeScale = totalRealNanos == 0L
                ? 0d
                : totalSimulationNanos / (double) totalRealNanos;
        return new FixedStepExecutionSnapshot(
                config.mode(),
                config.fixedStep(),
                config.requestedTimeScale(),
                completedStepCount,
                Duration.ofNanos(totalRealNanos),
                Duration.ofNanos(totalSimulationNanos),
                Duration.ofNanos(pendingSimulationNanos),
                achievedTimeScale);
    }

    private static long toNanos(Duration duration, String fieldName) {
        if (duration == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be nonnegative");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(fieldName + " is too large", ex);
        }
    }

    private static long scale(long nanos, double scale) {
        if (nanos == 0L) {
            return 0L;
        }
        double scaled = nanos * scale;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE || scaled < 0d) {
            throw new IllegalArgumentException("scaled simulation time overflows nanoseconds");
        }
        return Math.round(scaled);
    }

    private static long addExact(long left, long right, String description) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(description + " overflows nanoseconds", ex);
        }
    }
}
