package online.davisfamily.threedee.sim.framework.time;

import java.time.Duration;

public record FixedStepExecutionConfig(
        SimulationExecutionMode mode,
        Duration fixedStep,
        double requestedTimeScale,
        int maximumStepsPerAdvance,
        int renderEveryAdvanceCount) {

    public FixedStepExecutionConfig {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (fixedStep == null) {
            throw new IllegalArgumentException("fixedStep must not be null");
        }
        if (fixedStep.isZero() || fixedStep.isNegative()) {
            throw new IllegalArgumentException("fixedStep must be positive");
        }
        try {
            if (fixedStep.toNanos() <= 0) {
                throw new IllegalArgumentException("fixedStep must convert to positive nanoseconds");
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("fixedStep must convert to finite nanoseconds", ex);
        }
        if (!Double.isFinite(requestedTimeScale) || requestedTimeScale <= 0d) {
            throw new IllegalArgumentException("requestedTimeScale must be finite and positive");
        }
        if (mode == SimulationExecutionMode.REALTIME
                && Double.compare(requestedTimeScale, 1.0d) != 0) {
            throw new IllegalArgumentException("REALTIME requestedTimeScale must be 1.0");
        }
        if (maximumStepsPerAdvance < 1) {
            throw new IllegalArgumentException("maximumStepsPerAdvance must be >= 1");
        }
        if (mode == SimulationExecutionMode.HEADLESS_ANALYSIS) {
            if (renderEveryAdvanceCount != 1) {
                throw new IllegalArgumentException(
                        "HEADLESS_ANALYSIS renderEveryAdvanceCount must be 1");
            }
        } else if (renderEveryAdvanceCount < 1) {
            throw new IllegalArgumentException("renderEveryAdvanceCount must be >= 1");
        }
    }

    public static FixedStepExecutionConfig realtime(
            Duration fixedStep,
            int maximumStepsPerAdvance) {
        return new FixedStepExecutionConfig(
                SimulationExecutionMode.REALTIME,
                fixedStep,
                1.0d,
                maximumStepsPerAdvance,
                1);
    }

    public static FixedStepExecutionConfig acceleratedVisual(
            Duration fixedStep,
            double requestedTimeScale,
            int maximumStepsPerAdvance,
            int renderEveryAdvanceCount) {
        return new FixedStepExecutionConfig(
                SimulationExecutionMode.ACCELERATED_VISUAL,
                fixedStep,
                requestedTimeScale,
                maximumStepsPerAdvance,
                renderEveryAdvanceCount);
    }

    public static FixedStepExecutionConfig headless(
            Duration fixedStep,
            int maximumStepsPerAdvance) {
        return new FixedStepExecutionConfig(
                SimulationExecutionMode.HEADLESS_ANALYSIS,
                fixedStep,
                1.0d,
                maximumStepsPerAdvance,
                1);
    }

    public long fixedStepNanos() {
        return fixedStep.toNanos();
    }
}
