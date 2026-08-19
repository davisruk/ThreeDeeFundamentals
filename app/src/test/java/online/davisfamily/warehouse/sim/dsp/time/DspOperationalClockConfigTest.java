package online.davisfamily.warehouse.sim.dsp.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class DspOperationalClockConfigTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);

    @Test
    void shouldCreateProductionOperatingWindowForConfiguredDate() {
        DspOperationalClockConfig config = DspOperationalClockConfig.productionBaseline(OPERATING_DATE);

        assertEquals(OPERATING_DATE, config.operatingDate());
        assertEquals(OperationalDayTime.day0(LocalTime.of(6, 0)), config.normalStart());
        assertEquals(OperationalDayTime.day0(LocalTime.of(22, 0)), config.normalEnd());
        assertEquals(OperationalDayTime.day1(LocalTime.MIDNIGHT), config.hardCutoff());
        assertEquals(OPERATING_DATE.atTime(6, 0), config.normalStartDateTime());
        assertEquals(OPERATING_DATE.atTime(22, 0), config.normalEndDateTime());
        assertEquals(OPERATING_DATE.plusDays(1).atStartOfDay(), config.hardCutoffDateTime());
    }

    @Test
    void shouldCalculateNormalAndHardCutoffDurationsFromStart() {
        DspOperationalClockConfig config = DspOperationalClockConfig.productionBaseline(OPERATING_DATE);

        assertEquals(Duration.ofHours(16), config.operatingDurationUntilNormalEnd());
        assertEquals(Duration.ofHours(18), config.operatingDurationUntilHardCutoff());
    }

    @Test
    void shouldRejectMisorderedOperatingWindowBoundaries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalClockConfig(
                        OPERATING_DATE,
                        OperationalDayTime.day1(LocalTime.of(6, 0)),
                        OperationalDayTime.day1(LocalTime.of(22, 0)),
                        new OperationalDayTime(2, LocalTime.MIDNIGHT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalClockConfig(
                        OPERATING_DATE,
                        OperationalDayTime.day0(LocalTime.of(22, 0)),
                        OperationalDayTime.day0(LocalTime.of(6, 0)),
                        OperationalDayTime.day1(LocalTime.MIDNIGHT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspOperationalClockConfig(
                        OPERATING_DATE,
                        OperationalDayTime.day0(LocalTime.of(6, 0)),
                        OperationalDayTime.day1(LocalTime.MIDNIGHT),
                        OperationalDayTime.day1(LocalTime.MIDNIGHT)));
    }
}
