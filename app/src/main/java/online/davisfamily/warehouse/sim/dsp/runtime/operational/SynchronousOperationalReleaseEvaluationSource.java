package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

public final class SynchronousOperationalReleaseEvaluationSource
        implements OperationalReleaseEvaluationSource {
    private final DspOperationalReleaseScheduler scheduler;
    private long nextSequence;
    private OperationalReleaseEvaluationResult pendingResult;

    public SynchronousOperationalReleaseEvaluationSource(
            DspOperationalReleaseScheduler scheduler) {
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
    public void submit(DspOperationalReleaseSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (!canSubmit()) {
            throw new IllegalStateException(
                    "Cannot submit while an operational evaluation result is pending");
        }
        pendingResult = new OperationalReleaseEvaluationResult(
                nextSequence++,
                snapshot,
                scheduler.evaluate(snapshot));
    }

    @Override
    public Optional<OperationalReleaseEvaluationResult> pollResult() {
        if (pendingResult == null) {
            return Optional.empty();
        }
        OperationalReleaseEvaluationResult result = pendingResult;
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

    @Override
    public Optional<String> p2pAllocationProfileId() {
        return Optional.of(scheduler.p2pAllocationProfileId());
    }
}
