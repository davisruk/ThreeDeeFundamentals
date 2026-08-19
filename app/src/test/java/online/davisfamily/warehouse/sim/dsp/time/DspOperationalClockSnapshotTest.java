package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class DspOperationalClockSnapshotTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);
    private static final LocalDateTime NORMAL_END = OPERATING_DATE.atTime(22, 0);
    private static final LocalDateTime HARD_CUTOFF = OPERATING_DATE.plusDays(1).atStartOfDay();

    @Test
    void shouldRepresentNormalOvertimeAndHardCutoffPhases() {
        DspOperationalClockSnapshot normal = snapshot(
                Duration.ZERO,
                OPERATING_DATE.atTime(6, 0),
                OperationalDayTime.day0(LocalTime.of(6, 0)),
                DspOperatingPhase.NORMAL_OPERATIONS);
        DspOperationalClockSnapshot overtime = snapshot(
                Duration.ofHours(16),
                NORMAL_END,
                OperationalDayTime.day0(LocalTime.of(22, 0)),
                DspOperatingPhase.OVERTIME);
        DspOperationalClockSnapshot hardCutoff = snapshot(
                Duration.ofHours(18),
                HARD_CUTOFF,
                OperationalDayTime.day1(LocalTime.MIDNIGHT),
                DspOperatingPhase.HARD_CUTOFF_REACHED);

        assertEquals(DspOperatingPhase.NORMAL_OPERATIONS, normal.phase());
        assertEquals(DspOperatingPhase.OVERTIME, overtime.phase());
        assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, hardCutoff.phase());
    }

    @Test
    void shouldExposeConfiguredBoundaryState() {
        DspOperationalClockSnapshot beforeEnd = snapshot(
                Duration.ofHours(15),
                OPERATING_DATE.atTime(21, 0),
                OperationalDayTime.day0(LocalTime.of(21, 0)),
                DspOperatingPhase.NORMAL_OPERATIONS);
        DspOperationalClockSnapshot atHardCutoff = snapshot(
                Duration.ofHours(18),
                HARD_CUTOFF,
                OperationalDayTime.day1(LocalTime.MIDNIGHT),
                DspOperatingPhase.HARD_CUTOFF_REACHED);

        assertEquals(OPERATING_DATE, beforeEnd.operatingDate());
        assertEquals(NORMAL_END, beforeEnd.normalEndDateTime());
        assertEquals(HARD_CUTOFF, beforeEnd.hardCutoffDateTime());
        assertFalse(beforeEnd.normalEndReached());
        assertFalse(beforeEnd.hardCutoffReached());
        assertTrue(atHardCutoff.normalEndReached());
        assertTrue(atHardCutoff.hardCutoffReached());
    }

    @Test
    void shouldRejectInternallyInconsistentSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        Duration.ZERO,
                        OPERATING_DATE.atTime(6, 0),
                        OperationalDayTime.day1(LocalTime.of(6, 0)),
                        DspOperatingPhase.NORMAL_OPERATIONS));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(
                        Duration.ZERO,
                        OPERATING_DATE.atTime(6, 0),
                        OperationalDayTime.day0(LocalTime.of(6, 0)),
                        DspOperatingPhase.OVERTIME));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalClockSnapshot(
                        Duration.ofSeconds(-1),
                        OPERATING_DATE,
                        OPERATING_DATE.atTime(6, 0),
                        OperationalDayTime.day0(LocalTime.of(6, 0)),
                        DspOperatingPhase.NORMAL_OPERATIONS,
                        NORMAL_END,
                        HARD_CUTOFF));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalClockSnapshot(
                        Duration.ZERO,
                        OPERATING_DATE,
                        OPERATING_DATE.atTime(6, 0),
                        OperationalDayTime.day0(LocalTime.of(6, 0)),
                        DspOperatingPhase.NORMAL_OPERATIONS,
                        HARD_CUTOFF,
                        HARD_CUTOFF));
    }

    private static DspOperationalClockSnapshot snapshot(
            Duration elapsed,
            LocalDateTime businessDateTime,
            OperationalDayTime operatingDayTime,
            DspOperatingPhase phase) {
        return new DspOperationalClockSnapshot(
                elapsed,
                OPERATING_DATE,
                businessDateTime,
                operatingDayTime,
                phase,
                NORMAL_END,
                HARD_CUTOFF);
    }
}
