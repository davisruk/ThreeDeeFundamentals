package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;

class DspFullDayServiceCentreScheduleLoaderTest {
    private static final String VALID = """
            {"serviceCentres":[
              {"serviceCentreId":"125","displayName":"Birmingham","priority":993,
               "trunkerDepartureTime":{"dayOffset":0,"localTime":{"hour":20,"minute":0}}},
              {"serviceCentreId":"109","displayName":"Preston","priority":989,
               "trunkerDepartureTime":{"dayOffset":1,"localTime":{"hour":5,"minute":0}}}
            ]}
            """;

    @Test
    void shouldLoadOrderedImmutableReplacementWithDayOnePreston(@TempDir Path directory)
            throws Exception {
        Path path = Files.writeString(directory.resolve("schedule.json"), VALID);

        DspServiceCentreTimetable timetable = new DspFullDayServiceCentreScheduleLoader().load(path);

        assertEquals(2, timetable.serviceCentres().size());
        assertEquals("125", timetable.serviceCentres().get(0).serviceCentreId());
        assertEquals("Birmingham", timetable.serviceCentres().get(0).displayName());
        assertEquals(993, timetable.require("125").priority());
        assertEquals("109", timetable.serviceCentres().get(1).serviceCentreId());
        assertEquals(989, timetable.require("109").priority());
        assertEquals(1, timetable.require("109").trunkerDepartureTime().dayOffset());
        assertEquals(LocalTime.of(5, 0), timetable.require("109").trunkerDepartureTime().localTime());
        assertThrows(UnsupportedOperationException.class,
                () -> timetable.serviceCentres().clear());
    }

    @Test
    void shouldRejectEveryInvalidFileWithoutPublishingATimetable(@TempDir Path directory)
            throws Exception {
        String entry = """
                {"serviceCentreId":"125","displayName":"Birmingham","priority":993,
                 "trunkerDepartureTime":{"dayOffset":0,"localTime":{"hour":20,"minute":0}}}
                """.trim();
        String[] invalid = {
            "not-json",
            VALID + " {}",
            VALID.replace("\"serviceCentres\":", "\"serviceCentres\":[],\"serviceCentres\":"),
            VALID.replace("\"serviceCentres\":", "\"unexpected\":1,\"serviceCentres\":"),
            VALID.replace("\"serviceCentres\":", "\"unexpected\":null,\"serviceCentres\":"),
            "{}",
            "{\"serviceCentres\":null}",
            "{\"serviceCentres\":[]}",
            "{\"serviceCentres\":[null]}",
            VALID.replace("\"displayName\":\"Birmingham\",", ""),
            VALID.replace("\"displayName\":\"Birmingham\"",
                    "\"displayName\":null"),
            VALID.replace("\"priority\":993", "\"priority\":\"993\""),
            VALID.replace("\"priority\":993", "\"priority\":0"),
            VALID.replace("\"dayOffset\":0", "\"dayOffset\":-1"),
            VALID.replace("\"hour\":20", "\"hour\":24"),
            VALID.replace("\"minute\":0", "\"minute\":60"),
            VALID.replace("\"minute\":0", "\"minute\":0,\"second\":0"),
            VALID.replace("\"hour\":20", "\"hour\":20,\"hour\":21"),
            "{\"serviceCentres\":[" + entry + "," + entry + "]}"
        };
        Path path = directory.resolve("schedule.json");
        DspFullDayServiceCentreScheduleLoader loader = new DspFullDayServiceCentreScheduleLoader();
        for (String json : invalid) {
            Files.writeString(path, json);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> loader.load(path), () -> "accepted: " + json);
            assertTrue(failure.getMessage().contains(path.toString()));
            assertNotNull(failure.getCause());
        }
    }
}
