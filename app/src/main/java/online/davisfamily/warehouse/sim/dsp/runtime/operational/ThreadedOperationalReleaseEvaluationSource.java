package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

public final class ThreadedOperationalReleaseEvaluationSource
        implements OperationalReleaseEvaluationSource {
    private final DspOperationalReleaseScheduler scheduler;
    private final ExecutorService executor;
    private volatile OperationalReleaseEvaluationResult pendingResult;
    private volatile RuntimeException pendingFailure;
    private volatile boolean evaluationInFlight;
    private boolean closed;
    private long nextSequence;

    public ThreadedOperationalReleaseEvaluationSource(
            DspOperationalReleaseScheduler scheduler,
            String workerThreadName) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (workerThreadName == null || workerThreadName.isBlank()) {
            throw new IllegalArgumentException("workerThreadName must not be blank");
        }
        this.scheduler = scheduler;
        this.executor = Executors.newSingleThreadExecutor(
                namedThreadFactory(workerThreadName.trim()));
    }

    @Override
    public synchronized boolean canSubmit() {
        return !closed
                && !evaluationInFlight
                && pendingResult == null
                && pendingFailure == null;
    }

    @Override
    public synchronized void submit(DspOperationalReleaseSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (closed) {
            throw new IllegalStateException("Operational evaluation source is closed");
        }
        if (!canSubmit()) {
            throw new IllegalStateException(
                    "Cannot submit while an operational evaluation is in flight"
                            + " or a result is pending");
        }
        long sequence = nextSequence++;
        evaluationInFlight = true;
        executor.execute(() -> evaluateOnWorker(sequence, snapshot));
    }

    @Override
    public Optional<OperationalReleaseEvaluationResult> pollResult() {
        RuntimeException failure = pendingFailure;
        if (failure != null) {
            pendingFailure = null;
            throw failure;
        }
        OperationalReleaseEvaluationResult result = pendingResult;
        if (result == null) {
            return Optional.empty();
        }
        pendingResult = null;
        return Optional.of(result);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdownNow();
    }

    @Override
    public String modeLabel() {
        return "threaded";
    }

    @Override
    public boolean evaluationInFlight() {
        return evaluationInFlight;
    }

    @Override
    public Optional<String> p2pAllocationProfileId() {
        return Optional.of(scheduler.p2pAllocationProfileId());
    }

    private void evaluateOnWorker(
            long sequence,
            DspOperationalReleaseSnapshot snapshot) {
        try {
            pendingResult = new OperationalReleaseEvaluationResult(
                    sequence,
                    snapshot,
                    scheduler.evaluate(snapshot));
        } catch (RuntimeException exception) {
            pendingFailure = exception;
        } catch (Throwable throwable) {
            pendingFailure = new IllegalStateException(
                    "Operational scheduler evaluation failed", throwable);
        } finally {
            evaluationInFlight = false;
        }
    }

    private static ThreadFactory namedThreadFactory(String workerThreadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, workerThreadName);
            thread.setDaemon(true);
            return thread;
        };
    }
}
