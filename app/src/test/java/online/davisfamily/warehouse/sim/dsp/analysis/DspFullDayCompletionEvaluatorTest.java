package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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
