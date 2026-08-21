package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

public record OperationalReleaseEvaluationResult(
        long sequence,
        DspOperationalReleaseSnapshot snapshot,
        DspOperationalReleaseEvaluation evaluation) {

    public OperationalReleaseEvaluationResult {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (evaluation == null) {
            throw new IllegalArgumentException("evaluation must not be null");
        }
    }
}
