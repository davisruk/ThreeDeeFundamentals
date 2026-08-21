package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalCandidateRankingPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntryAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.PharmacyGroupedSourceSequenceRankingPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.ServiceCentrePharmacyGroup;

class ThreadedOperationalReleaseEvaluationSourceTest {

    @Test
    void shouldEvaluateOnNamedPlatformWorker() throws Exception {
        AtomicReference<Thread> evaluationThread = new AtomicReference<>();
        CountDownLatch evaluated = new CountDownLatch(1);
        OperationalCandidateRankingPolicy trackingRanking = (candidates, snapshot) -> {
            evaluationThread.set(Thread.currentThread());
            evaluated.countDown();
            return new PharmacyGroupedSourceSequenceRankingPolicy().rank(candidates, snapshot);
        };
        DspOperationalReleaseScheduler scheduler = scheduler(trackingRanking);
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        scheduler, "dsp-operational-release-worker");
        try {
            DspOperationalReleaseSnapshot snapshot = snapshot("tote-1", "order-1");

            source.submit(snapshot);

            assertTrue(evaluated.await(1, TimeUnit.SECONDS));
            OperationalReleaseEvaluationResult result = awaitResult(source);
            assertSame(snapshot, result.snapshot());
            assertEquals(0L, result.sequence());
            assertEquals("dsp-operational-release-worker", evaluationThread.get().getName());
            assertTrue(evaluationThread.get().isDaemon());
            assertFalse(evaluationThread.get().isVirtual());
            assertEquals("threaded", source.modeLabel());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldKeepOneOperationalEvaluationInFlight() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationalCandidateRankingPolicy blockingRanking = (candidates, snapshot) -> {
            started.countDown();
            awaitLatch(release);
            return new PharmacyGroupedSourceSequenceRankingPolicy().rank(candidates, snapshot);
        };
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        scheduler(blockingRanking), "dsp-operational-release-worker");
        try {
            DspOperationalReleaseSnapshot firstSnapshot = snapshot("tote-1", "order-1");
            DspOperationalReleaseSnapshot secondSnapshot = snapshot("tote-2", "order-2");

            source.submit(firstSnapshot);
            assertTrue(started.await(1, TimeUnit.SECONDS));

            assertTrue(source.evaluationInFlight());
            assertFalse(source.canSubmit());
            assertThrows(IllegalStateException.class, () -> source.submit(secondSnapshot));

            release.countDown();
            awaitEvaluationCompletion(source);

            assertFalse(source.canSubmit());
            assertThrows(IllegalStateException.class, () -> source.submit(secondSnapshot));
            assertEquals(0L, source.pollResult().orElseThrow().sequence());

            source.submit(secondSnapshot);
            OperationalReleaseEvaluationResult second = awaitResult(source);
            assertEquals(1L, second.sequence());
            assertSame(secondSnapshot, second.snapshot());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldRethrowOperationalWorkerFailure() {
        OperationalCandidateRankingPolicy failingRanking = (candidates, snapshot) -> {
            throw new IllegalStateException("operational boom");
        };
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        scheduler(failingRanking), "dsp-operational-release-worker");
        try {
            source.submit(snapshot("tote-1", "order-1"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> awaitResult(source));

            assertEquals("operational boom", exception.getMessage());
            awaitEvaluationCompletion(source);
            assertTrue(source.canSubmit());
        } finally {
            source.close();
        }
    }

    @Test
    void shouldRejectThreadedSubmissionAfterClose() {
        DspOperationalReleaseScheduler scheduler = new DspOperationalReleaseScheduler();
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        scheduler, "dsp-operational-release-worker");

        source.close();

        assertFalse(source.canSubmit());
        assertThrows(
                IllegalStateException.class,
                () -> source.submit(snapshot("tote-1", "order-1")));
        assertThrows(IllegalArgumentException.class, () -> source.submit(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ThreadedOperationalReleaseEvaluationSource(
                        null, "dsp-operational-release-worker"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ThreadedOperationalReleaseEvaluationSource(scheduler, " "));
    }

    private static DspOperationalReleaseScheduler scheduler(
            OperationalCandidateRankingPolicy rankingPolicy) {
        return new DspOperationalReleaseScheduler(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                rankingPolicy);
    }

    private static OperationalReleaseEvaluationResult awaitResult(
            ThreadedOperationalReleaseEvaluationSource source) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Optional<OperationalReleaseEvaluationResult> result = source.pollResult();
            if (result.isPresent()) {
                return result.orElseThrow();
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Timed out waiting for operational evaluation result");
    }

    private static void awaitEvaluationCompletion(
            ThreadedOperationalReleaseEvaluationSource source) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (source.evaluationInFlight() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        if (source.evaluationInFlight()) {
            throw new IllegalStateException("Timed out waiting for operational evaluation completion");
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test release latch", exception);
        }
    }

    private static DspOperationalReleaseSnapshot snapshot(
            String physicalToteId,
            String orderId) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                "pharmacy-1",
                "patient-" + orderId,
                "prescription-" + orderId,
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(item),
                999,
                1);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order,
                new RouteRequirements(
                        false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                OrderType.FULL_PACK,
                "sc-1",
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate candidate = new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of("pharmacy-1"));
        StationAdmissionSnapshot admission = new StationAdmissionSnapshot(
                StationType.P2P,
                new StationCapacity(1, 1),
                new StationSnapshot(StationType.P2P, 0, 0),
                true,
                "",
                Optional.of("p2p-1"));
        return new DspOperationalReleaseSnapshot(
                List.of(candidate),
                List.of(new ServiceCentrePharmacyGroup(
                        "sc-1", "pharmacy-1", 0, 1)),
                Map.of(StationType.P2P, admission),
                Set.of());
    }
}
