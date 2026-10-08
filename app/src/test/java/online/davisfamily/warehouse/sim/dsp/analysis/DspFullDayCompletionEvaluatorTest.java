package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.schedule.DspOperationalSchedulingBaselineFactory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class DspFullDayCompletionEvaluatorTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    private final DspOperationalClock clock = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(OPERATING_DATE));
    private final DspOperationalClock lateClock = new DspOperationalClock(
            new DspOperationalClockConfig(
                    OPERATING_DATE,
                    OperationalDayTime.day0(LocalTime.of(6, 0)),
                    OperationalDayTime.day0(LocalTime.of(22, 0)),
                    OperationalDayTime.day1(LocalTime.of(6, 0))));
    private final DspFullDayCompletionEvaluator evaluator = new DspFullDayCompletionEvaluator(
            DspOperationalSchedulingBaselineFactory.createProductionTimetable(),
            Duration.ofHours(1));

    @Test
    void shouldRequireEveryCompletionPredicateAndRetainFirstCompletionTime() {
        DspOperationalClockSnapshot initial = clock.initialSnapshot();
        DspFullDayCompletionEvaluator.Observation complete = observation(
                initial,
                true,
                0,
                List.of(),
                Optional.empty());

        DspServiceCentreCompletionSnapshot first = evaluator.evaluate(complete);
        assertTrue(first.complete());
        assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED,
                first.p2pOutputClosureState());
        assertEquals(Duration.ZERO, first.completionElapsedTime().orElseThrow());

        DspFullDayCompletionEvaluator.Observation blocked = observation(
                clock.snapshotAtSimulationSeconds(1),
                true,
                1,
                List.of(),
                first.completionElapsedTime());
        DspServiceCentreCompletionSnapshot stillIncomplete = evaluator.evaluate(blocked);
        assertFalse(stillIncomplete.complete());
        assertTrue(stillIncomplete.completionElapsedTime().isEmpty());

        DspFullDayCompletionEvaluator.Observation later = observation(
                clock.snapshotAtSimulationSeconds(2),
                true,
                0,
                List.of(),
                first.completionElapsedTime());
        DspServiceCentreCompletionSnapshot retained = evaluator.evaluate(later);
        assertTrue(retained.complete());
        assertEquals(Duration.ZERO, retained.completionElapsedTime().orElseThrow());
    }

    @Test
    void shouldTreatUnsupportedWorkAsBlocking() {
        DspServiceCentreCompletionSnapshot snapshot = evaluator.evaluate(observation(
                clock.initialSnapshot(),
                true,
                0,
                List.of("unresolved product line"),
                Optional.empty()));

        assertFalse(snapshot.complete());
        assertEquals(DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF,
                snapshot.outcome());
    }

    @Test
    void shouldEvaluateAllObservationsAtTheSuppliedClock() {
        DspOperationalClockSnapshot oldClock = clock.initialSnapshot();
        DspOperationalClockSnapshot currentClock = clock.snapshotAtSimulationSeconds(60 * 60);
        DspFullDayCompletionEvaluator.Observation observation = observation(
                oldClock,
                true,
                0,
                List.of(),
                Optional.empty());

        DspServiceCentreCompletionSnapshot snapshot = evaluator
                .evaluateAll(currentClock, List.of(observation))
                .getFirst();

        assertEquals(currentClock.businessDateTime(), snapshot.deadline().evaluatedAt());
        assertEquals(currentClock.elapsedSimulationTime(), snapshot.completionElapsedTime().orElseThrow());
    }

    @Test
    void shouldExposeAllTimetableOutcomes() {
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET,
                evaluator.evaluate(observation(
                        lateClock.snapshotAtSimulationSeconds(Duration.ofHours(10).toSeconds()),
                        true, 0, List.of(), Optional.empty())).outcome());
        assertEquals(DspServiceCentreCompletionOutcome.OVERTIME_BUT_DISPATCHABLE,
                evaluator.evaluate(observation(
                        lateClock.snapshotAtSimulationSeconds(16.5 * 60 * 60),
                        true, 0, List.of(), Optional.empty())).outcome());
        assertEquals(DspServiceCentreCompletionOutcome.MISSED_TRUNKER,
                evaluator.evaluate(observation(
                        lateClock.snapshotAtSimulationSeconds(Duration.ofHours(23).toSeconds()),
                        true, 0, List.of(), Optional.empty())).outcome());
        assertEquals(DspServiceCentreCompletionOutcome.UNFINISHED_AT_HARD_CUTOFF,
                evaluator.evaluate(observation(
                        lateClock.snapshotAtSimulationSeconds(Duration.ofHours(24).toSeconds()),
                        false, 0, List.of(), Optional.empty())).outcome());
    }

    @Test
    void shouldWaitForPdcCollectionAndClosePartialOrZeroPackWorkWithException() {
        DspOperationalClockSnapshot now = clock.initialSnapshot();

        DspServiceCentreCompletionSnapshot waitingForPdc = evaluator.evaluate(
                exceptionObservation(now, 1, 0, 0, 0, 0));
        assertFalse(waitingForPdc.complete());
        assertEquals(DspP2pOutputClosureState.NOT_CLOSED,
                waitingForPdc.p2pOutputClosureState());
        assertTrue(waitingForPdc.completionElapsedTime().isEmpty());

        DspServiceCentreCompletionSnapshot partialBag = evaluator.evaluate(
                exceptionObservation(now, 1, 1, 1, 1, 0));
        assertTrue(partialBag.complete());
        assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION,
                partialBag.p2pOutputClosureState());
        assertEquals(1, partialBag.missingPackCount());
        assertEquals(1, partialBag.pdcCollectedPackCount());
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET, partialBag.outcome());

        DspServiceCentreCompletionSnapshot zeroPackBag = evaluator.evaluate(
                exceptionObservation(now, 1, 1, 0, 0, 1));
        assertTrue(zeroPackBag.complete());
        assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION,
                zeroPackBag.p2pOutputClosureState());
        assertEquals(1, zeroPackBag.pendingEmptyBagCount());
        assertEquals(partialBag.outcome(), zeroPackBag.outcome());

        DspServiceCentreCompletionSnapshot lateException = evaluator.evaluate(
                exceptionObservation(
                        lateClock.snapshotAtSimulationSeconds(Duration.ofHours(23).toSeconds()),
                        1, 1, 1, 1, 0));
        assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION,
                lateException.p2pOutputClosureState());
        assertEquals(DspServiceCentreCompletionOutcome.MISSED_TRUNKER,
                lateException.outcome());

        assertThrows(IllegalArgumentException.class,
                () -> exceptionObservation(now, 0, 1, 0, 0, 0));
    }

    @Test
    void shouldCompletePhysicalWorkWithNsPendingButKeepOtherPredicatesBlocking() {
        var complete = evaluator.evaluate(nsObservation(2, 0, List.of()));
        assertTrue(complete.complete());
        assertEquals(2, complete.nsCandidateInputLineCount());
        assertEquals(Duration.ZERO, complete.completionElapsedTime().orElseThrow());
        assertEquals(DspServiceCentreCompletionOutcome.ON_TARGET, complete.outcome());
        assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION,
                complete.p2pOutputClosureState());
        assertEquals(0, complete.missingPackCount());
        assertEquals(0, complete.markedOutboundToteCount());
        assertFalse(evaluator.evaluate(nsObservation(2, 1, List.of())).complete());
        var unsupported = evaluator.evaluate(nsObservation(2, 0, List.of("MANUAL work")));
        assertFalse(unsupported.complete());
        assertEquals(DspP2pOutputClosureState.NOT_CLOSED, unsupported.p2pOutputClosureState());
        assertThrows(IllegalArgumentException.class, () -> nsObservation(-1, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> nsSnapshot(complete, DspP2pOutputClosureState.P2P_OUTPUT_CLOSED, 2));
        assertThrows(IllegalArgumentException.class,
                () -> nsSnapshot(complete, DspP2pOutputClosureState.P2P_OUTPUT_CLOSED_WITH_EXCEPTION, -1));
    }

    @Test
    void shouldDefaultBothLegacyConstructorShapesToZeroNs() {
        var preException = observation(clock.initialSnapshot(), true, 0, List.of(), Optional.empty());
        var exceptionAware = exceptionObservation(clock.initialSnapshot(), 0, 0, 0, 0, 0);
        assertEquals(0, preException.nsCandidateInputLineCount());
        assertEquals(0, exceptionAware.nsCandidateInputLineCount());
        var original = evaluator.evaluate(preException);
        for (var snapshot : List.of(legacySnapshot(original, false), legacySnapshot(original, true))) {
            assertEquals(0, snapshot.nsCandidateInputLineCount());
            assertEquals(original, snapshot);
            assertEquals(DspP2pOutputClosureState.P2P_OUTPUT_CLOSED, snapshot.p2pOutputClosureState());
        }
    }

    private DspFullDayCompletionEvaluator.Observation nsObservation(
            int count, int upstreamWaiting, List<String> unsupported) {
        return new DspFullDayCompletionEvaluator.Observation(
                "109", clock.initialSnapshot(), true,
                upstreamWaiting, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                unsupported, Optional.empty(), 0, 0, 0, 0, 0, count);
    }

    private static DspServiceCentreCompletionSnapshot nsSnapshot(
            DspServiceCentreCompletionSnapshot snapshot, DspP2pOutputClosureState closure, int nsCount) {
        return new DspServiceCentreCompletionSnapshot(
                snapshot.serviceCentreId(),
                snapshot.supplyComplete(),
                snapshot.upstreamWaitingCount(),
                snapshot.capacityBlockedManifestCount(),
                snapshot.osrWaitingCount(),
                snapshot.av02WaitingCount(),
                snapshot.nonTerminalInboundToteCount(),
                snapshot.remainingPhysicalToteCount(),
                snapshot.remainingPhysicalPackCount(),
                snapshot.remainingPlannedBagCount(),
                snapshot.activeStationClaimCount(),
                snapshot.pendingStationDispositionCount(),
                snapshot.transportEnvelopeCount(),
                snapshot.tipperInputCount(),
                snapshot.p2pAssignmentCount(),
                snapshot.openOutboundToteCount(),
                snapshot.unallocatedCompletedBagCount(),
                snapshot.unsupportedWork(),
                snapshot.completionElapsedTime(),
                snapshot.completionDateTime(),
                snapshot.outcome(),
                snapshot.deadline(),
                snapshot.complete(),
                closure,
                snapshot.missingPackCount(),
                snapshot.pdcCollectedPackCount(),
                snapshot.affectedAllocatedBagCount(),
                snapshot.markedOutboundToteCount(),
                snapshot.pendingEmptyBagCount(),
                nsCount);
    }

    private static DspServiceCentreCompletionSnapshot legacySnapshot(
            DspServiceCentreCompletionSnapshot snapshot, boolean exceptionAware) {
        if (exceptionAware) {
            return new DspServiceCentreCompletionSnapshot(
                    snapshot.serviceCentreId(),
                    snapshot.supplyComplete(),
                    snapshot.upstreamWaitingCount(),
                    snapshot.capacityBlockedManifestCount(),
                    snapshot.osrWaitingCount(),
                    snapshot.av02WaitingCount(),
                    snapshot.nonTerminalInboundToteCount(),
                    snapshot.remainingPhysicalToteCount(),
                    snapshot.remainingPhysicalPackCount(),
                    snapshot.remainingPlannedBagCount(),
                    snapshot.activeStationClaimCount(),
                    snapshot.pendingStationDispositionCount(),
                    snapshot.transportEnvelopeCount(),
                    snapshot.tipperInputCount(),
                    snapshot.p2pAssignmentCount(),
                    snapshot.openOutboundToteCount(),
                    snapshot.unallocatedCompletedBagCount(),
                    snapshot.unsupportedWork(),
                    snapshot.completionElapsedTime(),
                    snapshot.completionDateTime(),
                    snapshot.outcome(),
                    snapshot.deadline(),
                    snapshot.complete(),
                    snapshot.p2pOutputClosureState(),
                    snapshot.missingPackCount(),
                    snapshot.pdcCollectedPackCount(),
                    snapshot.affectedAllocatedBagCount(),
                    snapshot.markedOutboundToteCount(),
                    snapshot.pendingEmptyBagCount());
        }
        return new DspServiceCentreCompletionSnapshot(
                snapshot.serviceCentreId(),
                snapshot.supplyComplete(),
                snapshot.upstreamWaitingCount(),
                snapshot.capacityBlockedManifestCount(),
                snapshot.osrWaitingCount(),
                snapshot.av02WaitingCount(),
                snapshot.nonTerminalInboundToteCount(),
                snapshot.remainingPhysicalToteCount(),
                snapshot.remainingPhysicalPackCount(),
                snapshot.remainingPlannedBagCount(),
                snapshot.activeStationClaimCount(),
                snapshot.pendingStationDispositionCount(),
                snapshot.transportEnvelopeCount(),
                snapshot.tipperInputCount(),
                snapshot.p2pAssignmentCount(),
                snapshot.openOutboundToteCount(),
                snapshot.unallocatedCompletedBagCount(),
                snapshot.unsupportedWork(),
                snapshot.completionElapsedTime(),
                snapshot.completionDateTime(),
                snapshot.outcome(),
                snapshot.deadline(),
                snapshot.complete());
    }

    @Test
    void shouldPublishOneNormalEvaluationAndReplaceItOnTheNextUpdate() {
        DspServiceCentreCompletionSnapshot incomplete = evaluator.evaluate(observation(
                clock.initialSnapshot(), false, 0, List.of(), Optional.empty()));
        AtomicInteger evaluationCount = new AtomicInteger();
        DspFullDayCutoffController controller = new DspFullDayCutoffController(
                clock::initialSnapshot,
                () -> {
                    evaluationCount.incrementAndGet();
                    return new ArrayList<>(List.of(incomplete));
                },
                List.of(),
                ignored -> { });

        assertTrue(controller.latestCompletionSnapshots().isEmpty());

        controller.update(new SimulationContext(), 0d);

        List<DspServiceCentreCompletionSnapshot> firstPublication = controller
                .latestCompletionSnapshots().orElseThrow();
        assertEquals(1, evaluationCount.get());
        assertSame(firstPublication, controller.latestCompletionSnapshots().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> firstPublication.add(incomplete));

        controller.update(new SimulationContext(), 0d);

        assertEquals(2, evaluationCount.get());
        assertNotSame(firstPublication, controller.latestCompletionSnapshots().orElseThrow());
    }

    @Test
    void shouldEvaluateAfterHardCutoffOutputClosureBeforeTerminalPublication() {
        DspServiceCentreCompletionSnapshot incomplete = evaluator.evaluate(observation(
                lateClock.snapshotAtSimulationSeconds(Duration.ofHours(24).toSeconds()),
                false, 0, List.of(), Optional.empty()));
        AtomicInteger evaluationCount = new AtomicInteger();
        AtomicInteger closeCount = new AtomicInteger();
        DspFullDayCutoffController controller = new DspFullDayCutoffController(
                () -> lateClock.snapshotAtSimulationSeconds(Duration.ofHours(24).toSeconds()),
                () -> {
                    int evaluation = evaluationCount.incrementAndGet();
                    if (evaluation == 2) {
                        assertEquals(1, closeCount.get());
                    }
                    return new ArrayList<>(List.of(incomplete));
                },
                closeCount::incrementAndGet,
                List.of(),
                ignored -> { });

        controller.update(new SimulationContext(), 0d);

        assertEquals(2, evaluationCount.get());
        assertEquals(1, closeCount.get());
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, controller.state());
        assertEquals(incomplete,
                controller.latestCompletionSnapshots().orElseThrow().getFirst());
    }

    @Test
    void shouldNotPerformHardCutoffReevaluationAfterEarlyCompletion() {
        DspServiceCentreCompletionSnapshot complete = evaluator.evaluate(observation(
                clock.initialSnapshot(), true, 0, List.of(), Optional.empty()));
        AtomicInteger evaluationCount = new AtomicInteger();
        DspFullDayCutoffController controller = new DspFullDayCutoffController(
                () -> lateClock.snapshotAtSimulationSeconds(Duration.ofHours(24).toSeconds()),
                () -> {
                    evaluationCount.incrementAndGet();
                    return new ArrayList<>(List.of(complete));
                },
                List.of(),
                ignored -> { });

        controller.update(new SimulationContext(), 0d);

        assertEquals(1, evaluationCount.get());
        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, controller.state());
    }

    @Test
    void shouldRetainTheLastPublicationWhenEvaluationReturnsNullOrFails() {
        DspServiceCentreCompletionSnapshot incomplete = evaluator.evaluate(observation(
                clock.initialSnapshot(), false, 0, List.of(), Optional.empty()));
        AtomicInteger evaluationCount = new AtomicInteger();
        DspFullDayCutoffController controller = new DspFullDayCutoffController(
                clock::initialSnapshot,
                () -> {
                    int evaluation = evaluationCount.incrementAndGet();
                    if (evaluation == 1) {
                        return new ArrayList<>(List.of(incomplete));
                    }
                    if (evaluation == 2) {
                        return null;
                    }
                    throw new IllegalStateException("completion evaluation failed");
                },
                List.of(),
                ignored -> { });

        controller.update(new SimulationContext(), 0d);
        List<DspServiceCentreCompletionSnapshot> firstPublication = controller
                .latestCompletionSnapshots().orElseThrow();

        assertThrows(IllegalStateException.class,
                () -> controller.update(new SimulationContext(), 0d));
        assertSame(firstPublication, controller.latestCompletionSnapshots().orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> controller.update(new SimulationContext(), 0d));
        assertSame(firstPublication, controller.latestCompletionSnapshots().orElseThrow());
    }

    private static DspFullDayCompletionEvaluator.Observation observation(
            DspOperationalClockSnapshot clock,
            boolean supplyComplete,
            int upstreamWaiting,
            List<String> unsupportedWork,
            Optional<Duration> previousCompletion) {
        return new DspFullDayCompletionEvaluator.Observation(
                "109",
                clock,
                supplyComplete,
                upstreamWaiting,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                unsupportedWork,
                previousCompletion);
    }

    private static DspFullDayCompletionEvaluator.Observation exceptionObservation(
            DspOperationalClockSnapshot clock,
            int missingPackCount,
            int pdcCollectedPackCount,
            int affectedAllocatedBagCount,
            int markedOutboundToteCount,
            int pendingEmptyBagCount) {
        return new DspFullDayCompletionEvaluator.Observation(
                "109",
                clock,
                true,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(),
                Optional.empty(),
                missingPackCount,
                pdcCollectedPackCount,
                affectedAllocatedBagCount,
                markedOutboundToteCount,
                pendingEmptyBagCount);
    }
}
