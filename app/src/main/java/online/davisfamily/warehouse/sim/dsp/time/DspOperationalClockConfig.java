package online.davisfamily.warehouse.sim.dsp.time;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DspOperationalClockConfig(
        LocalDate operatingDate,
        OperationalDayTime normalStart,
        OperationalDayTime normalEnd,
        OperationalDayTime hardCutoff) {

    public DspOperationalClockConfig {
        if (operatingDate == null) {
            throw new IllegalArgumentException("operatingDate must not be null");
        }
        if (normalStart == null) {
            throw new IllegalArgumentException("normalStart must not be null");
        }
        if (normalEnd == null) {
            throw new IllegalArgumentException("normalEnd must not be null");
        }
        if (hardCutoff == null) {
            throw new IllegalArgumentException("hardCutoff must not be null");
        }
        if (normalStart.dayOffset() != 0) {
            throw new IllegalArgumentException("normalStart must be on day 0");
        }
        if (normalStart.compareTo(normalEnd) >= 0) {
            throw new IllegalArgumentException("normalStart must be before normalEnd");
        }
        if (normalEnd.compareTo(hardCutoff) >= 0) {
            throw new IllegalArgumentException("normalEnd must be before hardCutoff");
        }
    }

    public static DspOperationalClockConfig productionBaseline(LocalDate operatingDate) {
        return new DspOperationalClockConfig(
                operatingDate,
                OperationalDayTime.day0(java.time.LocalTime.of(6, 0)),
                OperationalDayTime.day0(java.time.LocalTime.of(22, 0)),
                OperationalDayTime.day1(java.time.LocalTime.MIDNIGHT));
    }

    public LocalDateTime normalStartDateTime() {
        return normalStart.onOperatingDate(operatingDate);
    }

    public LocalDateTime normalEndDateTime() {
        return normalEnd.onOperatingDate(operatingDate);
    }

    public LocalDateTime hardCutoffDateTime() {
        return hardCutoff.onOperatingDate(operatingDate);
    }

    public Duration operatingDurationUntilNormalEnd() {
        return Duration.between(normalStartDateTime(), normalEndDateTime());
    }

    public Duration operatingDurationUntilHardCutoff() {
        return Duration.between(normalStartDateTime(), hardCutoffDateTime());
    }
}
