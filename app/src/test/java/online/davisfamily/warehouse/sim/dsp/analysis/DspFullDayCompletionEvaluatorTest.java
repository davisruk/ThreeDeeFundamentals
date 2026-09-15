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
}
