package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class DspOperationalClockTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);

    private final DspOperationalClock clock = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(OPERATING_DATE));

    @Test
    void shouldMapElapsedZeroToConfiguredNormalStart() {
        DspOperationalClockSnapshot snapshot = clock.initialSnapshot();

        assertEquals(Duration.ZERO, snapshot.elapsedSimulationTime());
        assertEquals(OPERATING_DATE, snapshot.operatingDate());
        assertEquals(OPERATING_DATE.atTime(6, 0), snapshot.businessDateTime());
        assertEquals(OperationalDayTime.day0(LocalTime.of(6, 0)), snapshot.operatingDayTime());
        assertEquals(DspOperatingPhase.NORMAL_OPERATIONS, snapshot.phase());
    }

    @Test
    void shouldMapElapsedTimeAcrossMidnightWithExplicitDayOffset() {
        DspOperationalClockSnapshot snapshot = clock.snapshotAt(Duration.ofHours(19).plusMinutes(30));

        assertEquals(OPERATING_DATE.plusDays(1).atTime(1, 30), snapshot.businessDateTime());
        assertEquals(OperationalDayTime.day1(LocalTime.of(1, 30)), snapshot.operatingDayTime());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, snapshot.phase());

        DspOperationalClockSnapshot later = clock.snapshotAt(Duration.ofHours(42));
        assertEquals(OPERATING_DATE.plusDays(2).atTime(0, 0), later.businessDateTime());
        assertEquals(new OperationalDayTime(2, LocalTime.MIDNIGHT), later.operatingDayTime());
    }

    @Test
    void shouldClassifyExactNormalEndAndHardCutoffBoundaries() {
        DspOperationalClockSnapshot normalEnd = clock.snapshotAt(Duration.ofHours(16));
        DspOperationalClockSnapshot hardCutoff = clock.snapshotAt(Duration.ofHours(18));

        assertEquals(OPERATING_DATE.atTime(22, 0), normalEnd.businessDateTime());
        assertEquals(DspOperatingPhase.OVERTIME, normalEnd.phase());
        assertEquals(OPERATING_DATE.plusDays(1).atStartOfDay(), hardCutoff.businessDateTime());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, hardCutoff.phase());
    }

    @Test
    void shouldMapSimulationSecondsUsingRoundedNanoseconds() {
        DspOperationalClockSnapshot snapshot = clock.snapshotAtSimulationSeconds(0.0000000006d);

        assertEquals(Duration.ofNanos(1), snapshot.elapsedSimulationTime());
        assertEquals(
                LocalDateTime.of(OPERATING_DATE, LocalTime.of(6, 0)).plusNanos(1),
                snapshot.businessDateTime());
    }

    @Test
    void shouldRejectInvalidElapsedTime() {
        assertThrows(IllegalArgumentException.class, () -> new DspOperationalClock(null));
        assertThrows(IllegalArgumentException.class, () -> clock.snapshotAt(null));
        assertThrows(IllegalArgumentException.class, () -> clock.snapshotAt(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> clock.snapshotAtSimulationSeconds(-1d));
        assertThrows(IllegalArgumentException.class, () -> clock.snapshotAtSimulationSeconds(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> clock.snapshotAtSimulationSeconds(Double.POSITIVE_INFINITY));
    }
}
