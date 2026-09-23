package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

/** Strict, all-or-nothing JSON boundary for a full-day timetable override. */
final class DspFullDayServiceCentreScheduleLoader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    DspServiceCentreTimetable load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("service-centre schedule path must not be null");
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            requireFields(root, Set.of("serviceCentres"), "schedule");
            JsonNode entries = root.get("serviceCentres");
            if (!entries.isArray() || entries.isEmpty()) {
                throw new IllegalArgumentException("serviceCentres must be a nonempty array");
            }
            List<ServiceCentreSchedule> schedules = new ArrayList<>();
            for (JsonNode entry : entries) {
                requireFields(entry, Set.of("serviceCentreId", "displayName", "priority",
                        "trunkerDepartureTime"), "service centre");
                JsonNode departure = entry.get("trunkerDepartureTime");
                requireFields(departure, Set.of("dayOffset", "localTime"),
                        "trunkerDepartureTime");
                JsonNode localTime = departure.get("localTime");
                requireFields(localTime, Set.of("hour", "minute"), "localTime");
                schedules.add(new ServiceCentreSchedule(
                        requireText(entry.get("serviceCentreId"), "serviceCentreId"),
                        requireText(entry.get("displayName"), "displayName"),
                        requireInteger(entry.get("priority"), "priority"),
                        new OperationalDayTime(
                                requireInteger(departure.get("dayOffset"), "dayOffset"),
                                LocalTime.of(
                                        requireInteger(localTime.get("hour"), "hour"),
                                        requireInteger(localTime.get("minute"), "minute")))));
            }
            return new DspServiceCentreTimetable(schedules);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "could not read service-centre schedule JSON from path " + path,
                    exception);
        }
    }

    private static void requireFields(JsonNode node, Set<String> expected, String name) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(name + " fields must be exactly " + expected);
        }
        for (String field : expected) {
            if (node.get(field).isNull()) {
                throw new IllegalArgumentException(name + "." + field + " must not be null");
            }
        }
    }

    private static String requireText(JsonNode node, String name) {
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(name + " must be a nonblank string");
        }
        return node.textValue();
    }

    private static int requireInteger(JsonNode node, String name) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalArgumentException(name + " must be a JSON integer in int range");
        }
        return node.intValue();
    }
}
