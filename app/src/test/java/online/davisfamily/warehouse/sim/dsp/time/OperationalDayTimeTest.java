package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class OperationalDayTimeTest {

    @Test
    void shouldOrderTimesUsingExplicitDayOffset() {
        OperationalDayTime lateOnDayZero = OperationalDayTime.day0(LocalTime.of(23, 59));
        OperationalDayTime midnightOnDayOne = OperationalDayTime.day1(LocalTime.MIDNIGHT);
        OperationalDayTime laterOnDayOne = OperationalDayTime.day1(LocalTime.NOON);

        assertEquals(-1, lateOnDayZero.compareTo(midnightOnDayOne));
        assertEquals(-1, midnightOnDayOne.compareTo(laterOnDayOne));
        assertEquals(1, laterOnDayOne.compareTo(lateOnDayZero));
    }

    @Test
    void shouldResolveDayOffsetAgainstOperatingDate() {
        LocalDate operatingDate = LocalDate.of(2026, 8, 19);

        assertEquals(
                LocalDate.of(2026, 8, 19).atTime(6, 0),
                OperationalDayTime.day0(LocalTime.of(6, 0)).onOperatingDate(operatingDate));
        assertEquals(
                LocalDate.of(2026, 8, 20).atTime(0, 0),
                OperationalDayTime.day1(LocalTime.MIDNIGHT).onOperatingDate(operatingDate));
    }

    @Test
    void shouldRejectInvalidOperatingDayTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalDayTime(-1, LocalTime.NOON));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OperationalDayTime(0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationalDayTime.day0(LocalTime.NOON).onOperatingDate(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> OperationalDayTime.day0(LocalTime.NOON).compareTo(null));
    }
}
