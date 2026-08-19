package online.davisfamily.warehouse.sim.dsp.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A local operating time together with its explicit day offset. */
public record OperationalDayTime(int dayOffset, LocalTime localTime)
        implements Comparable<OperationalDayTime> {

    public OperationalDayTime {
        if (dayOffset < 0) {
            throw new IllegalArgumentException("dayOffset must be >= 0");
        }
        if (localTime == null) {
            throw new IllegalArgumentException("localTime must not be null");
        }
    }

    public static OperationalDayTime day0(LocalTime localTime) {
        return new OperationalDayTime(0, localTime);
    }

    public static OperationalDayTime day1(LocalTime localTime) {
        return new OperationalDayTime(1, localTime);
    }

    public LocalDateTime onOperatingDate(LocalDate operatingDate) {
        if (operatingDate == null) {
            throw new IllegalArgumentException("operatingDate must not be null");
        }
        return LocalDateTime.of(operatingDate.plusDays(dayOffset), localTime);
    }

    @Override
    public int compareTo(OperationalDayTime other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        int dayComparison = Integer.compare(dayOffset, other.dayOffset);
        return dayComparison != 0 ? dayComparison : localTime.compareTo(other.localTime);
    }
}
