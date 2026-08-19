package online.davisfamily.threedee.sim.framework.time;

import java.time.Duration;

public record FixedStepExecutionSnapshot(
        SimulationExecutionMode mode,
        Duration fixedStep,
        double requestedTimeScale,
        long completedStepCount,
        Duration totalRealTime,
        Duration totalSimulationTime,
        Duration pendingSimulationTime,
        double achievedTimeScale) {

    public FixedStepExecutionSnapshot {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (fixedStep == null || fixedStep.isZero() || fixedStep.isNegative()) {
            throw new IllegalArgumentException("fixedStep must be positive");
        }
        if (!Double.isFinite(requestedTimeScale) || requestedTimeScale <= 0d) {
            throw new IllegalArgumentException("requestedTimeScale must be finite and positive");
        }
        if (completedStepCount < 0) {
            throw new IllegalArgumentException("completedStepCount must be >= 0");
        }
        if (totalRealTime == null || totalRealTime.isNegative()) {
            throw new IllegalArgumentException("totalRealTime must be nonnegative");
        }
        if (totalSimulationTime == null || totalSimulationTime.isNegative()) {
            throw new IllegalArgumentException("totalSimulationTime must be nonnegative");
        }
        if (pendingSimulationTime == null || pendingSimulationTime.isNegative()) {
            throw new IllegalArgumentException("pendingSimulationTime must be nonnegative");
        }
        if (!Double.isFinite(achievedTimeScale) || achievedTimeScale < 0d) {
            throw new IllegalArgumentException("achievedTimeScale must be finite and nonnegative");
        }
    }
}
