package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;

class DspOperationalClockControllerTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);

    @Test
    void shouldStartAtConfiguredOperatingTimeBeforeWorldAdvances() {
        DspOperationalClockController controller = controller();

        assertEquals(
                OPERATING_DATE.atTime(6, 0),
                controller.snapshot().businessDateTime());
        assertEquals(DspOperatingPhase.NORMAL_OPERATIONS, controller.snapshot().phase());
    }

    @Test
    void shouldFollowAbsoluteSimulationContextTime() {
        SimulationWorld world = new SimulationWorld();
        DspOperationalClockController controller = controller();
        world.addController(controller);

        world.update(2.5d);

        assertEquals(2.5d, controller.snapshot().elapsedSimulationTime().toNanos() / 1_000_000_000d);
        assertEquals(OPERATING_DATE.atTime(6, 0).plusSeconds(2).plusNanos(500_000_000),
                controller.snapshot().businessDateTime());
    }

    @Test
    void shouldNotDriftWhenWorldUsesUnevenBoundedSteps() {
        SimulationWorld world = new SimulationWorld();
        DspOperationalClockController controller = controller();
        world.addController(controller);

        world.update(0.25d);
        world.update(1.75d);
        world.update(0.125d);

        DspOperationalClockSnapshot expected = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(OPERATING_DATE))
                .snapshotAt(Duration.ofMillis(2_125));
        assertEquals(expected, controller.snapshot());
    }

    @Test
    void shouldExposeHardCutoffWithoutStoppingSimulationWorld() {
        SimulationWorld world = new SimulationWorld();
        DspOperationalClockController controller = controller();
        world.addController(controller);

        world.update(Duration.ofHours(18).toSeconds());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, controller.snapshot().phase());

        world.update(1d);

        assertEquals(
                OPERATING_DATE.plusDays(1).atStartOfDay().plusSeconds(1),
                controller.snapshot().businessDateTime());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, controller.snapshot().phase());
    }

    @Test
    void shouldRejectNullControllerInputs() {
        assertThrows(IllegalArgumentException.class, () -> new DspOperationalClockController(null));

        DspOperationalClockController controller = controller();
        assertThrows(IllegalArgumentException.class, () -> controller.update(null, 0d));
    }

    private static DspOperationalClockController controller() {
        return new DspOperationalClockController(
                new DspOperationalClock(
                        DspOperationalClockConfig.productionBaseline(OPERATING_DATE)));
    }
}
