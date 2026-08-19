package online.davisfamily.threedee.sim.framework.time;

import java.time.Duration;

public record FixedStepAdvance(
        int executedStepCount,
        Duration fixedStep,
        Duration advancedSimulationTime,
        Duration pendingSimulationTime,
        boolean renderDue) {

    public FixedStepAdvance {
        if (executedStepCount < 0) {
            throw new IllegalArgumentException("executedStepCount must be >= 0");
        }
        if (fixedStep == null || fixedStep.isZero() || fixedStep.isNegative()) {
            throw new IllegalArgumentException("fixedStep must be positive");
        }
        if (advancedSimulationTime == null || advancedSimulationTime.isNegative()) {
            throw new IllegalArgumentException("advancedSimulationTime must be nonnegative");
        }
        if (pendingSimulationTime == null || pendingSimulationTime.isNegative()) {
            throw new IllegalArgumentException("pendingSimulationTime must be nonnegative");
        }
    }
}
