package online.davisfamily.threedee.sim.framework.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class FixedStepExecutionConfigTest {

    @Test
    void shouldCreateRealtimeAcceleratedAndHeadlessConfigurations() {
        FixedStepExecutionConfig realtime = FixedStepExecutionConfig.realtime(
                Duration.ofMillis(16),
                4);
        FixedStepExecutionConfig accelerated = FixedStepExecutionConfig.acceleratedVisual(
                Duration.ofMillis(10),
                1000d,
                20,
                5);
        FixedStepExecutionConfig headless = FixedStepExecutionConfig.headless(
                Duration.ofSeconds(1),
                100);

        assertEquals(SimulationExecutionMode.REALTIME, realtime.mode());
        assertEquals(1.0d, realtime.requestedTimeScale());
        assertEquals(1, realtime.renderEveryAdvanceCount());
        assertEquals(SimulationExecutionMode.ACCELERATED_VISUAL, accelerated.mode());
        assertEquals(1000d, accelerated.requestedTimeScale());
        assertEquals(5, accelerated.renderEveryAdvanceCount());
        assertEquals(SimulationExecutionMode.HEADLESS_ANALYSIS, headless.mode());
        assertEquals(100, headless.maximumStepsPerAdvance());
        assertEquals(1_000_000_000L, headless.fixedStepNanos());
    }

    @Test
    void shouldRejectInvalidFixedStepAndWorkBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedStepExecutionConfig(
                        null,
                        Duration.ofSeconds(1),
                        1d,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedStepExecutionConfig(
                        SimulationExecutionMode.REALTIME,
                        null,
                        1d,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> FixedStepExecutionConfig.realtime(Duration.ZERO, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> FixedStepExecutionConfig.realtime(Duration.ofSeconds(1), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        10d,
                        1,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedStepExecutionConfig(
                        SimulationExecutionMode.HEADLESS_ANALYSIS,
                        Duration.ofSeconds(1),
                        1d,
                        1,
                        2));
    }

    @Test
    void shouldEnforceExecutionModeScaleRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedStepExecutionConfig(
                        SimulationExecutionMode.REALTIME,
                        Duration.ofSeconds(1),
                        2d,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        0d,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        Double.NaN,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedStepExecutionConfig(
                        SimulationExecutionMode.HEADLESS_ANALYSIS,
                        Duration.ofSeconds(1),
                        1d,
                        1,
                        2));
    }
}
