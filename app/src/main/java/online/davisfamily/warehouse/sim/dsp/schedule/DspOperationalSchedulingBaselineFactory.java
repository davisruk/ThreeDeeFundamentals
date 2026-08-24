package online.davisfamily.warehouse.sim.dsp.schedule;

import java.time.LocalTime;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

public final class DspOperationalSchedulingBaselineFactory {

    private DspOperationalSchedulingBaselineFactory() {
    }

    public static DspServiceCentreTimetable createProductionTimetable() {
        return new DspServiceCentreTimetable(List.of(
                schedule("104", "Letchworth", 999, OperationalDayTime.day0(LocalTime.of(17, 0))),
                schedule("108", "Swansea", 998, OperationalDayTime.day0(LocalTime.of(17, 0))),
                schedule("116", "Exeter", 997, OperationalDayTime.day0(LocalTime.of(17, 0))),
                schedule("110", "Newcastle", 996, OperationalDayTime.day0(LocalTime.of(21, 0))),
                schedule("101", "Chessington", 995, OperationalDayTime.day0(LocalTime.of(19, 0))),
                schedule("102", "Croydon", 994, OperationalDayTime.day0(LocalTime.of(19, 0))),
                schedule("105", "Hinckley", 993, OperationalDayTime.day0(LocalTime.of(20, 0))),
                schedule("106", "Leeds", 992, OperationalDayTime.day0(LocalTime.of(21, 0))),
                schedule("121", "Coatbridge", 991, OperationalDayTime.day0(LocalTime.of(23, 0))),
                schedule("109", "Preston", 990, OperationalDayTime.day1(LocalTime.of(5, 0)))));
    }

    private static ServiceCentreSchedule schedule(
            String serviceCentreId,
            String displayName,
            int priority,
            OperationalDayTime trunkerDepartureTime) {
        return new ServiceCentreSchedule(
                serviceCentreId,
                displayName,
                priority,
                trunkerDepartureTime);
    }
}
