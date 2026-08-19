package online.davisfamily.threedee.sim.framework.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FixedStepExecutionDriverTest {

    @Test
    void shouldEmitRepeatedBoundedStepsForAcceleratedVisualTime() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofMillis(500),
                        4d,
                        20,
                        1));
        List<Double> steps = new ArrayList<>();

        FixedStepAdvance advance = driver.advance(
                Duration.ofSeconds(1),
                steps::add);

        assertEquals(8, advance.executedStepCount());
        assertEquals(List.of(0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d), steps);
        assertEquals(Duration.ofSeconds(4), advance.advancedSimulationTime());
        assertEquals(Duration.ZERO, advance.pendingSimulationTime());
    }

    @Test
    void shouldRetainBacklogWhenAdvanceBudgetIsExhausted() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        10d,
                        3,
                        1));

        FixedStepAdvance first = driver.advance(Duration.ofSeconds(1), ignored -> {
        });
        FixedStepAdvance second = driver.advance(Duration.ZERO, ignored -> {
        });

        assertEquals(3, first.executedStepCount());
        assertEquals(Duration.ofSeconds(7), first.pendingSimulationTime());
        assertEquals(3, second.executedStepCount());
        assertEquals(Duration.ofSeconds(4), second.pendingSimulationTime());
    }

    @Test
    void shouldPreserveFractionalTimeUntilACompleteStepExists() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.realtime(Duration.ofSeconds(1), 2));
        List<Double> steps = new ArrayList<>();

        FixedStepAdvance first = driver.advance(Duration.ofMillis(500), steps::add);
        FixedStepAdvance second = driver.advance(Duration.ofMillis(500), steps::add);

        assertEquals(0, first.executedStepCount());
        assertEquals(Duration.ofMillis(500), first.pendingSimulationTime());
        assertEquals(1, second.executedStepCount());
        assertEquals(Duration.ZERO, second.pendingSimulationTime());
        assertEquals(List.of(1d), steps);
    }

    @Test
    void shouldDecimateVisualRenderRequestsWithoutChangingSimulationSteps() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        1d,
                        1,
                        2));

        FixedStepAdvance first = driver.advance(Duration.ofSeconds(1), ignored -> {
        });
        FixedStepAdvance second = driver.advance(Duration.ofSeconds(1), ignored -> {
        });
        FixedStepAdvance third = driver.advance(Duration.ofSeconds(1), ignored -> {
        });

        assertTrue(first.renderDue());
        assertFalse(second.renderDue());
        assertTrue(third.renderDue());
        assertEquals(1, first.executedStepCount());
        assertEquals(1, second.executedStepCount());
        assertEquals(1, third.executedStepCount());
    }

    @Test
    void shouldRunHeadlessBatchesWithoutRequestingRendering() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.headless(Duration.ofSeconds(1), 3));
        List<Double> steps = new ArrayList<>();

        FixedStepAdvance advance = driver.advance(Duration.ZERO, steps::add);

        assertEquals(3, advance.executedStepCount());
        assertEquals(List.of(1d, 1d, 1d), steps);
        assertEquals(Duration.ofSeconds(3), advance.advancedSimulationTime());
        assertEquals(Duration.ZERO, advance.pendingSimulationTime());
        assertFalse(advance.renderDue());
    }

    @Test
    void shouldReportRequestedAndAchievedSimulationSpeed() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        10d,
                        10,
                        1));

        driver.advance(Duration.ofSeconds(1), ignored -> {
        });

        FixedStepExecutionSnapshot snapshot = driver.snapshot();
        assertEquals(10d, snapshot.requestedTimeScale());
        assertEquals(10d, snapshot.achievedTimeScale());
        assertEquals(Duration.ofSeconds(1), snapshot.totalRealTime());
        assertEquals(Duration.ofSeconds(10), snapshot.totalSimulationTime());
        assertEquals(10L, snapshot.completedStepCount());
    }

    @Test
    void shouldRejectInvalidElapsedTimeAndOverflow() {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.realtime(Duration.ofSeconds(1), 1));

        assertThrows(IllegalArgumentException.class, () -> driver.advance(null, ignored -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> driver.advance(Duration.ofNanos(-1), ignored -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> driver.advance(Duration.ZERO, null));

        FixedStepExecutionDriver overflowDriver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.acceleratedVisual(
                        Duration.ofSeconds(1),
                        2d,
                        1,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> overflowDriver.advance(Duration.ofNanos(Long.MAX_VALUE), ignored -> {
                }));
    }
}
