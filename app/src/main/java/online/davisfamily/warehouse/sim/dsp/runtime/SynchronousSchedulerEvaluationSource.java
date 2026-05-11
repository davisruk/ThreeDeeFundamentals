package online.davisfamily.warehouse.sim.dsp.runtime;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class SynchronousSchedulerEvaluationSource implements SchedulerEvaluationSource {
    private final DspReleaseScheduler scheduler;
    private long nextSequence;
    private SchedulerEvaluationResult pendingResult;

    public SynchronousSchedulerEvaluationSource(DspReleaseScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        this.scheduler = scheduler;
    }

    @Override
    public boolean canSubmit() {
        return pendingResult == null;
    }

    @Override
    public void submit(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (!canSubmit()) {
            throw new IllegalStateException("Cannot submit while an evaluation result is pending");
        }
        pendingResult = new SchedulerEvaluationResult(
                nextSequence++,
                snapshot,
                scheduler.evaluate(snapshot));
    }

    @Override
    public Optional<SchedulerEvaluationResult> pollResult() {
        if (pendingResult == null) {
            return Optional.empty();
        }
        SchedulerEvaluationResult result = pendingResult;
        pendingResult = null;
        return Optional.of(result);
    }

    @Override
    public void close() {
    }

    @Override
    public String modeLabel() {
        return "sync";
    }
}
