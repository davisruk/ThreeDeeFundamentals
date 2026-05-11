package online.davisfamily.warehouse.sim.dsp.runtime;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public interface SchedulerEvaluationSource {
    boolean canSubmit();

    void submit(WarehouseSchedulerSnapshot snapshot);

    Optional<SchedulerEvaluationResult> pollResult();

    void close();
}
