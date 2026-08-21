package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

public interface OperationalReleaseEvaluationSource extends AutoCloseable {
    boolean canSubmit();

    void submit(DspOperationalReleaseSnapshot snapshot);

    Optional<OperationalReleaseEvaluationResult> pollResult();

    @Override
    void close();

    default String modeLabel() {
        return "custom";
    }

    default boolean evaluationInFlight() {
        return false;
    }
}
