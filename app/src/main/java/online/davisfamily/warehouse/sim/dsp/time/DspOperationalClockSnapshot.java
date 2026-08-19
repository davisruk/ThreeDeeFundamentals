package online.davisfamily.warehouse.sim.dsp.time;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DspOperationalClockSnapshot(
        Duration elapsedSimulationTime,
        LocalDate operatingDate,
        LocalDateTime businessDateTime,
        OperationalDayTime operatingDayTime,
        DspOperatingPhase phase,
        LocalDateTime normalEndDateTime,
        LocalDateTime hardCutoffDateTime) {

    public DspOperationalClockSnapshot {
        if (elapsedSimulationTime == null) {
            throw new IllegalArgumentException("elapsedSimulationTime must not be null");
        }
        if (operatingDate == null) {
            throw new IllegalArgumentException("operatingDate must not be null");
        }
        if (businessDateTime == null) {
            throw new IllegalArgumentException("businessDateTime must not be null");
        }
        if (operatingDayTime == null) {
            throw new IllegalArgumentException("operatingDayTime must not be null");
        }
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (normalEndDateTime == null) {
            throw new IllegalArgumentException("normalEndDateTime must not be null");
        }
        if (hardCutoffDateTime == null) {
            throw new IllegalArgumentException("hardCutoffDateTime must not be null");
        }
        if (elapsedSimulationTime.isNegative()) {
            throw new IllegalArgumentException("elapsedSimulationTime must not be negative");
        }
        if (!normalEndDateTime.isBefore(hardCutoffDateTime)) {
            throw new IllegalArgumentException("normalEndDateTime must be before hardCutoffDateTime");
        }
        if (!businessDateTime.equals(operatingDayTime.onOperatingDate(operatingDate))) {
            throw new IllegalArgumentException(
                    "businessDateTime must match operatingDate and operatingDayTime");
        }

        DspOperatingPhase expectedPhase = phaseFor(businessDateTime, normalEndDateTime, hardCutoffDateTime);
        if (phase != expectedPhase) {
            throw new IllegalArgumentException(
                    "phase does not match businessDateTime and configured boundaries");
        }
    }

    public boolean normalEndReached() {
        return !businessDateTime.isBefore(normalEndDateTime);
    }

    public boolean hardCutoffReached() {
        return !businessDateTime.isBefore(hardCutoffDateTime);
    }

    static DspOperatingPhase phaseFor(
            LocalDateTime businessDateTime,
            LocalDateTime normalEndDateTime,
            LocalDateTime hardCutoffDateTime) {
        if (businessDateTime.isBefore(normalEndDateTime)) {
            return DspOperatingPhase.NORMAL_OPERATIONS;
        }
        if (businessDateTime.isBefore(hardCutoffDateTime)) {
            return DspOperatingPhase.OVERTIME;
        }
        return DspOperatingPhase.HARD_CUTOFF_REACHED;
    }
}
