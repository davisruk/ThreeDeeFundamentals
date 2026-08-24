package online.davisfamily.warehouse.sim.dsp.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class ServiceCentreDeadlineSnapshotFactoryTest {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 8, 19);
    private static final Duration DOWNSTREAM_HANDLING = Duration.ofHours(1);

    private final DspOperationalClock clock = new DspOperationalClock(
            DspOperationalClockConfig.productionBaseline(OPERATING_DATE));
    private final ServiceCentreDeadlineSnapshotFactory factory =
            new ServiceCentreDeadlineSnapshotFactory();

    @Test
    void shouldDeriveProductionDeadlinesWithoutUsingSourceDepartureMetadata() {
        List<ServiceCentreDeadlineSnapshot> deadlines = factory.create(
                DspOperationalSchedulingBaselineFactory.createProductionTimetable(),
                clock.initialSnapshot(),
                DOWNSTREAM_HANDLING);

        ServiceCentreDeadlineSnapshot letchworth = find(deadlines, "104");
        assertEquals(OPERATING_DATE.atTime(17, 0), letchworth.trunkerDepartureDateTime());
        assertEquals(OPERATING_DATE.atTime(16, 0), letchworth.trunkerReadyDeadline());
        assertEquals(OPERATING_DATE.atTime(16, 0), letchworth.targetCompletion());
        assertEquals(OPERATING_DATE.atTime(16, 0), letchworth.latestAllowedCompletion());
        assertEquals(Duration.ofHours(10), letchworth.availableTime());
        assertFalse(letchworth.targetPassed());
        assertFalse(letchworth.latestAllowedCompletionPassed());

        ServiceCentreDeadlineSnapshot exeter = find(deadlines, "116");
        assertEquals(OPERATING_DATE.atTime(16, 0), exeter.targetCompletion());

        ServiceCentreDeadlineSnapshot coatbridge = find(deadlines, "121");
        assertEquals(OPERATING_DATE.atTime(22, 0), coatbridge.trunkerReadyDeadline());
        assertEquals(OPERATING_DATE.atTime(22, 0), coatbridge.targetCompletion());
        assertEquals(OPERATING_DATE.atTime(22, 0), coatbridge.latestAllowedCompletion());

        ServiceCentreDeadlineSnapshot preston = find(deadlines, "109");
        assertEquals(OPERATING_DATE.plusDays(1).atTime(5, 0),
                preston.trunkerDepartureDateTime());
        assertEquals(OPERATING_DATE.plusDays(1).atTime(4, 0),
                preston.trunkerReadyDeadline());
        assertEquals(OPERATING_DATE.atTime(22, 0), preston.targetCompletion());
        assertEquals(OPERATING_DATE.plusDays(1).atStartOfDay(),
                preston.latestAllowedCompletion());
    }

    @Test
    void shouldClampAvailableTimeAndTreatExactBoundariesAsPassed() {
        ServiceCentreSchedule schedule = new ServiceCentreSchedule(
                "104",
                "Letchworth",
                999,
                OperationalDayTime.day0(LocalTime.of(17, 0)));

        ServiceCentreDeadlineSnapshot before = factory.create(
                schedule,
                clock.snapshotAt(Duration.ofHours(9)),
                DOWNSTREAM_HANDLING);
        assertEquals(OPERATING_DATE.atTime(15, 0), before.evaluatedAt());
        assertEquals(Duration.ofHours(1), before.availableTime());
        assertFalse(before.targetPassed());

        ServiceCentreDeadlineSnapshot exact = factory.create(
                schedule,
                clock.snapshotAt(Duration.ofHours(10)),
                DOWNSTREAM_HANDLING);
        assertEquals(Duration.ZERO, exact.availableTime());
        assertTrue(exact.targetPassed());
        assertTrue(exact.latestAllowedCompletionPassed());

        ServiceCentreDeadlineSnapshot after = factory.create(
                schedule,
                clock.snapshotAt(Duration.ofHours(12)),
                DOWNSTREAM_HANDLING);
        assertEquals(Duration.ZERO, after.availableTime());
        assertTrue(after.latestAllowedCompletionPassed());
    }

    @Test
    void shouldRejectInvalidFactoryInputsAndInconsistentSnapshots() {
        DspServiceCentreTimetable timetable =
                DspOperationalSchedulingBaselineFactory.createProductionTimetable();
        DspOperationalClockSnapshot initial = clock.initialSnapshot();

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        (DspServiceCentreTimetable) null,
                        initial,
                        DOWNSTREAM_HANDLING));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(timetable, null, DOWNSTREAM_HANDLING));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(timetable, initial, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(timetable, initial, Duration.ofSeconds(-1)));

        LocalDateTime evaluatedAt = OPERATING_DATE.atTime(6, 0);
        LocalDateTime departure = OPERATING_DATE.atTime(17, 0);
        LocalDateTime ready = OPERATING_DATE.atTime(16, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreDeadlineSnapshot(
                        "104",
                        "Letchworth",
                        999,
                        evaluatedAt,
                        departure,
                        ready,
                        ready,
                        ready,
                        Duration.ofHours(9),
                        false,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreDeadlineSnapshot(
                        "104",
                        "Letchworth",
                        999,
                        evaluatedAt,
                        departure,
                        ready,
                        ready,
                        ready,
                        Duration.ofHours(10),
                        true,
                        false));
    }

    private static ServiceCentreDeadlineSnapshot find(
            List<ServiceCentreDeadlineSnapshot> deadlines,
            String serviceCentreId) {
        return deadlines.stream()
                .filter(deadline -> deadline.serviceCentreId().equals(serviceCentreId))
                .findFirst()
                .orElseThrow();
    }
}
