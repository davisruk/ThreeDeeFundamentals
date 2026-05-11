package online.davisfamily.warehouse.sim.dsp.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentrePriority;
import online.davisfamily.warehouse.sim.dsp.scheduler.ServiceCentreWindowPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class ThreadedSchedulerEvaluationSourceTest {

    @Test
    void shouldRejectInvalidInputs() {
        DspReleaseScheduler scheduler = scheduler();

        assertThrows(IllegalArgumentException.class, () -> new ThreadedSchedulerEvaluationSource(null, "worker"));
        assertThrows(IllegalArgumentException.class, () -> new ThreadedSchedulerEvaluationSource(scheduler, null));
        assertThrows(IllegalArgumentException.class, () -> new ThreadedSchedulerEvaluationSource(scheduler, " "));
    }

    @Test
    void shouldEvaluateOnNamedWorkerThreadAndReturnResultLater() throws Exception {
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        TrackingScheduler scheduler = new TrackingScheduler(started, workerThreadName, null);
        ThreadedSchedulerEvaluationSource source = new ThreadedSchedulerEvaluationSource(scheduler, "dsp-scheduler-worker");
        try {
            WarehouseSchedulerSnapshot snapshot = snapshot("order-1");

            assertTrue(source.canSubmit());

            source.submit(snapshot);

            assertFalse(source.canSubmit());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread().getName(), workerThreadName.get());
            assertEquals("dsp-scheduler-worker", workerThreadName.get());

            SchedulerEvaluationResult result = awaitResult(source);
            assertEquals(0L, result.sequence());
            assertEquals(Optional.of("order-1"), result.evaluation().releaseDecision().map(decision -> decision.orderId()));
            assertTrue(source.canSubmit());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldRejectSubmitWhileEvaluationInFlightOrResultPending() {
        TrackingScheduler scheduler = new TrackingScheduler(new CountDownLatch(0), new AtomicReference<>(), Duration.ofMillis(150));
        ThreadedSchedulerEvaluationSource source = new ThreadedSchedulerEvaluationSource(scheduler, "dsp-scheduler-worker");
        try {
            source.submit(snapshot("order-1"));

            assertThrows(IllegalStateException.class, () -> source.submit(snapshot("order-2")));

            SchedulerEvaluationResult result = awaitResult(source);
            assertEquals(0L, result.sequence());

            source.submit(snapshot("order-2"));
            assertEquals(1L, awaitResult(source).sequence());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldRethrowWorkerFailureFromPollResult() {
        TrackingScheduler scheduler = new TrackingScheduler(
                new CountDownLatch(0),
                new AtomicReference<>(),
                null,
                new IllegalStateException("boom"));
        ThreadedSchedulerEvaluationSource source = new ThreadedSchedulerEvaluationSource(scheduler, "dsp-scheduler-worker");
        try {
            source.submit(snapshot("order-1"));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> awaitResult(source));
            assertEquals("boom", exception.getMessage());
            assertTrue(source.canSubmit());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldRejectSubmitAfterClose() {
        ThreadedSchedulerEvaluationSource source = new ThreadedSchedulerEvaluationSource(scheduler(), "dsp-scheduler-worker");

        source.close();

        assertThrows(IllegalStateException.class, () -> source.submit(snapshot("order-1")));
    }

    private static SchedulerEvaluationResult awaitResult(ThreadedSchedulerEvaluationSource source) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos) {
            Optional<SchedulerEvaluationResult> result = source.pollResult();
            if (result.isPresent()) {
                return result.get();
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for scheduler evaluation result", exception);
            }
        }
        throw new IllegalStateException("Timed out waiting for scheduler evaluation result");
    }

    private static DspReleaseScheduler scheduler() {
        return new DspReleaseScheduler(
                new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("sc-1"))),
                new DspDependencyEvaluator());
    }

    private static WarehouseSchedulerSnapshot snapshot(String orderId) {
        return new WarehouseSchedulerSnapshot(
                List.of(orderState(orderId)),
                Map.of(StationType.P2P, new StationAdmissionSnapshot(
                        StationType.P2P,
                        new StationCapacity(1, 1),
                        new StationSnapshot(StationType.P2P, 0, 0),
                        true,
                        "")),
                Set.of(),
                Optional.empty());
    }

    private static DspSchedulerOrderState orderState(String orderId) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                0);
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static final class TrackingScheduler extends DspReleaseScheduler {
        private final CountDownLatch started;
        private final AtomicReference<String> workerThreadName;
        private final Duration delay;
        private final RuntimeException failure;

        private TrackingScheduler(
                CountDownLatch started,
                AtomicReference<String> workerThreadName,
                Duration delay) {
            this(started, workerThreadName, delay, null);
        }

        private TrackingScheduler(
                CountDownLatch started,
                AtomicReference<String> workerThreadName,
                Duration delay,
                RuntimeException failure) {
            super(
                    new ServiceCentreWindowPolicy(new ServiceCentrePriority(List.of("sc-1"))),
                    new DspDependencyEvaluator());
            this.started = started;
            this.workerThreadName = workerThreadName;
            this.delay = delay;
            this.failure = failure;
        }

        @Override
        public SchedulerEvaluation evaluate(WarehouseSchedulerSnapshot snapshot) {
            workerThreadName.set(Thread.currentThread().getName());
            started.countDown();
            if (delay != null) {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during test scheduler delay", exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
            return super.evaluate(snapshot);
        }
    }
}
