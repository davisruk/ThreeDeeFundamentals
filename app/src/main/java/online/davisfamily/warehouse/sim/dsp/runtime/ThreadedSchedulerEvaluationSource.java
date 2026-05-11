package online.davisfamily.warehouse.sim.dsp.runtime;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class ThreadedSchedulerEvaluationSource implements SchedulerEvaluationSource {
    private final DspReleaseScheduler scheduler;
    private final ExecutorService executor;
    private volatile SchedulerEvaluationResult pendingResult;
    private volatile RuntimeException pendingFailure;
    private volatile boolean evaluationInFlight;
    private boolean closed;
    private long nextSequence;

    public ThreadedSchedulerEvaluationSource(DspReleaseScheduler scheduler, String workerThreadName) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (workerThreadName == null || workerThreadName.isBlank()) {
            throw new IllegalArgumentException("workerThreadName must not be blank");
        }
        this.scheduler = scheduler;
        this.executor = Executors.newSingleThreadExecutor(namedThreadFactory(workerThreadName));
    }

    @Override
    public synchronized boolean canSubmit() {
        return !closed && !evaluationInFlight && pendingResult == null && pendingFailure == null;
    }

    @Override
    public synchronized void submit(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (closed) {
            throw new IllegalStateException("Scheduler evaluation source is closed");
        }
        if (!canSubmit()) {
            throw new IllegalStateException("Cannot submit while an evaluation is in flight or a result is pending");
        }
        long sequence = nextSequence++;
        evaluationInFlight = true;
        executor.execute(() -> evaluateOnWorker(sequence, snapshot));
    }

    @Override
    public Optional<SchedulerEvaluationResult> pollResult() {
        RuntimeException failure = pendingFailure;
        if (failure != null) {
            pendingFailure = null;
            throw failure;
        }
        SchedulerEvaluationResult result = pendingResult;
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

    private void evaluateOnWorker(long sequence, WarehouseSchedulerSnapshot snapshot) {
        try {
            pendingResult = new SchedulerEvaluationResult(
                    sequence,
                    snapshot,
                    scheduler.evaluate(snapshot));
        } catch (RuntimeException exception) {
            pendingFailure = exception;
        } catch (Throwable throwable) {
            pendingFailure = new IllegalStateException("Scheduler evaluation failed", throwable);
        } finally {
            evaluationInFlight = false;
        }
    }

    private ThreadFactory namedThreadFactory(String workerThreadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, workerThreadName);
            thread.setDaemon(true);
            return thread;
        };
    }
}
