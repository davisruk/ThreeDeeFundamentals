package online.davisfamily.warehouse.sim.dsp.runtime;

import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public record SchedulerEvaluationResult(
        long sequence,
        WarehouseSchedulerSnapshot snapshot,
        SchedulerEvaluation evaluation) {

    public SchedulerEvaluationResult {
        if (sequence < 0L) {
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
