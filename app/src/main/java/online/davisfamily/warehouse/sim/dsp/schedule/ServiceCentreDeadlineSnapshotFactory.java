package online.davisfamily.warehouse.sim.dsp.schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class ServiceCentreDeadlineSnapshotFactory {

    public List<ServiceCentreDeadlineSnapshot> create(
            DspServiceCentreTimetable timetable,
            DspOperationalClockSnapshot clockSnapshot,
            Duration downstreamHandlingDuration) {
        if (timetable == null || clockSnapshot == null || downstreamHandlingDuration == null) {
            throw new IllegalArgumentException("deadline inputs must not be null");
        }
        if (downstreamHandlingDuration.isZero() || downstreamHandlingDuration.isNegative()) {
            throw new IllegalArgumentException("downstreamHandlingDuration must be positive");
        }
        return timetable.serviceCentres().stream()
                .map(serviceCentre -> create(
                        serviceCentre,
                        clockSnapshot,
                        downstreamHandlingDuration))
                .toList();
    }

    public ServiceCentreDeadlineSnapshot create(
            ServiceCentreSchedule serviceCentre,
            DspOperationalClockSnapshot clockSnapshot,
            Duration downstreamHandlingDuration) {
        if (serviceCentre == null
                || clockSnapshot == null
                || downstreamHandlingDuration == null) {
            throw new IllegalArgumentException("deadline inputs must not be null");
        }
        if (downstreamHandlingDuration.isZero() || downstreamHandlingDuration.isNegative()) {
            throw new IllegalArgumentException("downstreamHandlingDuration must be positive");
        }

        LocalDateTime trunkerDeparture = serviceCentre.trunkerDepartureTime()
                .onOperatingDate(clockSnapshot.operatingDate());
        LocalDateTime readyDeadline = trunkerDeparture.minus(downstreamHandlingDuration);
        LocalDateTime targetCompletion = earlierOf(
                readyDeadline, clockSnapshot.normalEndDateTime());
        LocalDateTime latestAllowedCompletion = earlierOf(
                readyDeadline, clockSnapshot.hardCutoffDateTime());
        LocalDateTime evaluatedAt = clockSnapshot.businessDateTime();
        Duration availableTime = evaluatedAt.isBefore(latestAllowedCompletion)
                ? Duration.between(evaluatedAt, latestAllowedCompletion)
                : Duration.ZERO;

        return new ServiceCentreDeadlineSnapshot(
                serviceCentre.serviceCentreId(),
                serviceCentre.displayName(),
                serviceCentre.priority(),
                evaluatedAt,
                trunkerDeparture,
                readyDeadline,
                targetCompletion,
                latestAllowedCompletion,
                availableTime,
                !evaluatedAt.isBefore(targetCompletion),
                !evaluatedAt.isBefore(latestAllowedCompletion));
    }

    private static LocalDateTime earlierOf(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }
}
