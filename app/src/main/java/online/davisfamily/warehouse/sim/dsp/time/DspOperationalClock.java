package online.davisfamily.warehouse.sim.dsp.time;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class DspOperationalClock {

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final DspOperationalClockConfig config;

    public DspOperationalClock(DspOperationalClockConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    public DspOperationalClockSnapshot snapshotAt(Duration elapsedSimulationTime) {
        if (elapsedSimulationTime == null) {
            throw new IllegalArgumentException("elapsedSimulationTime must not be null");
        }
        if (elapsedSimulationTime.isNegative()) {
            throw new IllegalArgumentException("elapsedSimulationTime must not be negative");
        }

        LocalDateTime businessDateTime = config.normalStartDateTime().plus(elapsedSimulationTime);
        int dayOffset = Math.toIntExact(
                ChronoUnit.DAYS.between(config.operatingDate(), businessDateTime.toLocalDate()));
        OperationalDayTime operatingDayTime = new OperationalDayTime(
                dayOffset,
                businessDateTime.toLocalTime());
        DspOperatingPhase phase = DspOperationalClockSnapshot.phaseFor(
                businessDateTime,
                config.normalEndDateTime(),
                config.hardCutoffDateTime());

        return new DspOperationalClockSnapshot(
                elapsedSimulationTime,
                config.operatingDate(),
                businessDateTime,
                operatingDayTime,
                phase,
                config.normalEndDateTime(),
                config.hardCutoffDateTime());
    }

    public DspOperationalClockSnapshot snapshotAtSimulationSeconds(double simulationTimeSeconds) {
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0d) {
            throw new IllegalArgumentException("simulationTimeSeconds must be finite and nonnegative");
        }
        return snapshotAt(Duration.ofNanos(
                Math.round(simulationTimeSeconds * NANOSECONDS_PER_SECOND)));
    }

    public DspOperationalClockSnapshot initialSnapshot() {
        return snapshotAt(Duration.ZERO);
    }
}
