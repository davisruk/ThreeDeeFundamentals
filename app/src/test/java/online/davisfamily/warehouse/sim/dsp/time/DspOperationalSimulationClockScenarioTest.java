package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.time.FixedStepAdvance;
import online.davisfamily.threedee.sim.framework.time.FixedStepExecutionConfig;
import online.davisfamily.threedee.sim.framework.time.FixedStepExecutionDriver;

class DspOperationalSimulationClockScenarioTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);
    private static final Duration FIXED_STEP = Duration.ofHours(1);

    @Test
    void shouldAdvanceOperatingDayThroughBoundedSimulationSteps() {
        ScenarioRun run = runToHardCutoff();

        assertEquals(18, run.advance().executedStepCount());
        assertEquals(Duration.ofHours(18), run.advance().advancedSimulationTime());
        assertEquals(Duration.ZERO, run.advance().pendingSimulationTime());
        assertEquals(18, run.snapshots().size());
        assertEquals(
                3600d,
                run.stepDurations().stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        assertTrue(run.stepDurations().stream().allMatch(step -> step <= 3600d));
        assertEquals(18d, run.runtime().driver().snapshot().requestedTimeScale());
        assertEquals(18d, run.runtime().driver().snapshot().achievedTimeScale());
        assertEquals(Duration.ZERO, run.runtime().driver().snapshot().pendingSimulationTime());
    }

    @Test
    void shouldRepresentNormalEndAndDayOneHardCutoff() {
        ScenarioRun run = runToHardCutoff();

        DspOperationalClockSnapshot normalEnd = run.snapshots().get(15);
        DspOperationalClockSnapshot hardCutoff = run.snapshots().get(17);

        assertEquals(OPERATING_DATE.atTime(22, 0), normalEnd.businessDateTime());
        assertEquals(DspOperatingPhase.OVERTIME, normalEnd.phase());
        assertEquals(1, hardCutoff.operatingDayTime().dayOffset());
        assertEquals(OPERATING_DATE.plusDays(1).atStartOfDay(), hardCutoff.businessDateTime());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, hardCutoff.phase());
    }

    @Test
    void shouldRestartDeterministicallyWhenRuntimeIsReconstructed() {
        Runtime first = runtime();
        ScenarioRun run = runToHardCutoff();
        Runtime reset = runtime();

        assertEquals(first.controller().snapshot(), reset.controller().snapshot());
        assertEquals(first.driver().snapshot(), reset.driver().snapshot());
        assertEquals(
                OPERATING_DATE.atTime(6, 0),
                reset.controller().snapshot().businessDateTime());
        assertEquals(DspOperatingPhase.NORMAL_OPERATIONS, reset.controller().snapshot().phase());
        assertTrue(run.runtime().controller().snapshot().hardCutoffReached());
    }

    private static ScenarioRun runToHardCutoff() {
        Runtime runtime = runtime();
        List<Double> stepDurations = new ArrayList<>();
        List<DspOperationalClockSnapshot> snapshots = new ArrayList<>();

        FixedStepAdvance advance = runtime.driver().advance(Duration.ofHours(1), stepSeconds -> {
            stepDurations.add(stepSeconds);
            runtime.world().update(stepSeconds);
            snapshots.add(runtime.controller().snapshot());
        });
        return new ScenarioRun(runtime, advance, stepDurations, snapshots);
    }

    private static Runtime runtime() {
        DspOperationalClock clock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(OPERATING_DATE));
        DspOperationalClockController controller = new DspOperationalClockController(clock);
        SimulationWorld world = new SimulationWorld();
        world.addController(controller);
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        FIXED_STEP,
                        18d,
                        18,
                        1));
        return new Runtime(world, controller, driver);
    }

    private record Runtime(
            SimulationWorld world,
            DspOperationalClockController controller,
            FixedStepExecutionDriver driver) {
    }

    private record ScenarioRun(
            Runtime runtime,
            FixedStepAdvance advance,
            List<Double> stepDurations,
            List<DspOperationalClockSnapshot> snapshots) {
    }
}
