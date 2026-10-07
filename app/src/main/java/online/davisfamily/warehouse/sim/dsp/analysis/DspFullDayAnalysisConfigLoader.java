package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads and validates the raw JSON shape for a full-day command configuration. */
final class DspFullDayAnalysisConfigLoader {
    private static final Set<String> PROPERTY_NAMES = Set.of(
            "productMaster",
            "orders",
            "ordersDirectory",
            "output",
            "inspectionOutput",
            "operatingDate",
            "osrLowWaterMark",
            "inboundIntervalSeconds",
            "av02Capacity",
            "outboundBagCapacity",
            "maximumPacksPerBag",
            "fixedStepMillis",
            "stepsPerBatch",
            "metricSampleSeconds",
            "overwrite",
            "progressLog",
            "progressIntervalSeconds",
            "serviceCentreSchedule",
            "thirdParty",
            "adapting");

    private static final Set<String> THIRD_PARTY_PROPERTIES = Set.of("processingDurationSeconds");
    private static final Set<String> ADAPTING_PROPERTIES = Set.of(
            "storeDurationSeconds", "collectDurationSeconds", "processingPositionsPerBench",
            "waitingCapacityPerBench", "benchIds");

    private final ObjectMapper objectMapper;

    DspFullDayAnalysisConfigLoader() {
        objectMapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    DspFullDayAnalysisConfigJson load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("--config path must not be null");
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            validateShape(root);
            return objectMapper.treeToValue(root, DspFullDayAnalysisConfigJson.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "could not read --config JSON from path " + path,
                    exception);
        }
    }

    private static void validateShape(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("--config JSON root must be an object");
        }

        Set<String> seen = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!PROPERTY_NAMES.contains(name)) {
                throw new IllegalArgumentException("unknown --config property: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate --config property: " + name);
            }
            JsonNode value = root.get(name);
            if (value == null || value.isNull()) {
                throw new IllegalArgumentException("--config property must not be null: " + name);
            }
            validateProperty(name, value);
        }

        if (root.has("orders") && root.has("ordersDirectory")) {
            throw new IllegalArgumentException(
                    "--config properties orders and ordersDirectory are mutually exclusive");
        }
    }

    private static void validateProperty(String name, JsonNode value) {
        switch (name) {
            case "thirdParty" -> validateStationObject(name, value, THIRD_PARTY_PROPERTIES);
            case "adapting" -> validateStationObject(name, value, ADAPTING_PROPERTIES);
            case "productMaster", "ordersDirectory", "output", "inspectionOutput", "operatingDate",
                    "progressLog", "serviceCentreSchedule" -> {
                requireText(name, value);
            }
            case "orders" -> {
                if (!value.isArray() || value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "--config property orders must be a nonempty array of strings");
                }
                for (JsonNode entry : value) {
                    requireText("orders entry", entry);
                }
            }
            case "inboundIntervalSeconds" -> {
                if (!value.isNumber()) {
                    throw new IllegalArgumentException(
                            "--config property inboundIntervalSeconds must be a JSON number");
                }
            }
            case "osrLowWaterMark", "av02Capacity", "outboundBagCapacity",
                    "maximumPacksPerBag", "fixedStepMillis", "stepsPerBatch", "metricSampleSeconds",
                    "progressIntervalSeconds" -> {
                if (!value.isIntegralNumber()) {
                    throw new IllegalArgumentException(
                            "--config property " + name + " must be a JSON integer");
                }
            }
            case "overwrite" -> {
                if (!value.isBoolean()) {
                    throw new IllegalArgumentException(
                            "--config property overwrite must be a JSON boolean");
                }
            }
            default -> throw new IllegalArgumentException("unknown --config property: " + name);
        }
    }

    private static void validateStationObject(String name, JsonNode value, Set<String> allowed) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("--config property " + name + " must be an object");
        }
        Iterator<String> names = value.fieldNames();
        while (names.hasNext()) {
            String property = names.next();
            String qualified = name + "." + property;
            if (!allowed.contains(property)) {
                throw new IllegalArgumentException("unknown --config property: " + qualified);
            }
            JsonNode entry = value.get(property);
            if (entry.isNull()) {
                throw new IllegalArgumentException("--config property must not be null: " + qualified);
            }
            switch (property) {
                case "processingDurationSeconds", "storeDurationSeconds", "collectDurationSeconds" -> {
                    if (!entry.isNumber()) {
                        throw new IllegalArgumentException(qualified + " must be a JSON number");
                    }
                }
                case "processingPositionsPerBench", "waitingCapacityPerBench" -> {
                    if (!entry.isIntegralNumber() || !entry.canConvertToInt()) {
                        throw new IllegalArgumentException(qualified + " must be a JSON integer in int range");
                    }
                }
                case "benchIds" -> {
                    if (!entry.isArray() || entry.isEmpty()) {
                        throw new IllegalArgumentException(qualified + " must be a nonempty array of strings");
                    }
                    for (JsonNode id : entry) {
                        requireText(qualified + " entry", id);
                    }
                }
                default -> throw new IllegalArgumentException("unknown --config property: " + qualified);
            }
        }
    }

    private static void requireText(String name, JsonNode value) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(
                    "--config property " + name + " must be a nonblank string");
        }
    }
}
