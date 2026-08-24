package online.davisfamily.warehouse.sim.dsp.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class DspServiceCentreTimetableTest {

    @Test
    void shouldExposeProductionBaselineInConfiguredPriorityOrder() {
        DspServiceCentreTimetable timetable =
                DspOperationalSchedulingBaselineFactory.createProductionTimetable();

        assertEquals(10, timetable.serviceCentres().size());
        assertEquals(
                List.of("104", "108", "116", "110", "101", "102", "105", "106", "121", "109"),
                timetable.serviceCentres().stream()
                        .map(ServiceCentreSchedule::serviceCentreId)
                        .toList());
        assertEquals(999, timetable.require("104").priority());
        assertEquals("Letchworth", timetable.require(" 104 ").displayName());
        assertEquals(
                OperationalDayTime.day0(LocalTime.of(17, 0)),
                timetable.require("104").trunkerDepartureTime());
        assertEquals(
                OperationalDayTime.day1(LocalTime.of(5, 0)),
                timetable.require("109").trunkerDepartureTime());
    }

    @Test
    void shouldRetainImmutableStructuredValuesAndPermitPriorityTies() {
        List<ServiceCentreSchedule> source = new ArrayList<>(List.of(
                schedule("A", "Alpha", 100),
                schedule("B", "Beta", 100)));

        DspServiceCentreTimetable timetable = new DspServiceCentreTimetable(source);
        source.clear();

        assertEquals(List.of("A", "B"), timetable.serviceCentres().stream()
                .map(ServiceCentreSchedule::serviceCentreId)
                .toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> timetable.serviceCentres().add(schedule("C", "Gamma", 99)));
    }

    @Test
    void shouldRejectInvalidScheduleAndTimetableValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreSchedule(" ", "Alpha", 1,
                        OperationalDayTime.day0(LocalTime.NOON)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreSchedule("A", " ", 1,
                        OperationalDayTime.day0(LocalTime.NOON)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreSchedule("A", "Alpha", 0,
                        OperationalDayTime.day0(LocalTime.NOON)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceCentreSchedule("A", "Alpha", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new DspServiceCentreTimetable(null));
        assertThrows(IllegalArgumentException.class, () -> new DspServiceCentreTimetable(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspServiceCentreTimetable(java.util.Arrays.asList(
                        schedule("A", "Alpha", 1), null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DspServiceCentreTimetable(List.of(
                        schedule("A", "Alpha", 1),
                        schedule("A", "Another Alpha", 2))));

        DspServiceCentreTimetable timetable = new DspServiceCentreTimetable(List.of(
                schedule("A", "Alpha", 1)));
        assertThrows(IllegalArgumentException.class, () -> timetable.find(" "));
        assertThrows(IllegalArgumentException.class, () -> timetable.require("missing"));
    }

    private static ServiceCentreSchedule schedule(
            String serviceCentreId,
            String displayName,
            int priority) {
        return new ServiceCentreSchedule(
                serviceCentreId,
                displayName,
                priority,
                OperationalDayTime.day0(LocalTime.NOON));
    }
}
