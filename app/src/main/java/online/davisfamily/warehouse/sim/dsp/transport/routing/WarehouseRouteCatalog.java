package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public final class WarehouseRouteCatalog {
    private final List<WarehouseRouteDefinition> definitions;
    private final Map<String, WarehouseRouteDefinition> definitionsByTargetId;
    private final Map<String, WarehouseRouteDefinition> definitionsByTerminalSensorId;
    private final RouteSegment commonEntrySegment;
    private final float entryDistance;
    private final TravelDirection entryDirection;

    public WarehouseRouteCatalog(List<WarehouseRouteDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions must not be empty");
        }

        List<WarehouseRouteDefinition> configuredDefinitions = new ArrayList<>();
        Map<String, WarehouseRouteDefinition> configuredByTargetId =
                new LinkedHashMap<>();
        Map<String, WarehouseRouteDefinition> configuredByTerminalSensorId =
                new LinkedHashMap<>();
        WarehouseRouteDefinition first = definitions.get(0);
        if (first == null) {
            throw new IllegalArgumentException("definitions must not contain null");
        }
        commonEntrySegment = first.entrySegment();
        entryDistance = first.entryDistance();
        entryDirection = first.entryDirection();

        for (WarehouseRouteDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("definitions must not contain null");
            }
            requireCommonEntry(definition);
            String targetId = definition.destination().targetId();
            if (configuredByTargetId.putIfAbsent(targetId, definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate warehouse route target ID: " + targetId);
            }
            String sensorId = definition.terminalArrivalSensorId();
            if (configuredByTerminalSensorId.putIfAbsent(sensorId, definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate terminal arrival sensor ID: " + sensorId);
            }
            configuredDefinitions.add(definition);
        }
        this.definitions = List.copyOf(configuredDefinitions);
        this.definitionsByTargetId = Map.copyOf(configuredByTargetId);
        this.definitionsByTerminalSensorId = Map.copyOf(
                configuredByTerminalSensorId);
    }

    public List<WarehouseRouteDefinition> definitions() {
        return definitions;
    }

    public RouteSegment commonEntrySegment() {
        return commonEntrySegment;
    }

    public float entryDistance() {
        return entryDistance;
    }

    public TravelDirection entryDirection() {
        return entryDirection;
    }

    public Optional<WarehouseRouteDefinition> find(
            OperationalRouteDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        return find(destination.stationType(), destination.targetId());
    }

    public Optional<WarehouseRouteDefinition> find(
            StationType stationType,
            String targetId) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        WarehouseRouteDefinition definition =
                definitionsByTargetId.get(requireId(targetId, "targetId"));
        if (definition == null) {
            return Optional.empty();
        }
        if (definition.destination().stationType() != stationType) {
            throw new IllegalArgumentException(
                    "Warehouse route target " + definition.destination().targetId()
                            + " belongs to station "
                            + definition.destination().stationType()
                            + " instead of " + stationType);
        }
        return Optional.of(definition);
    }

    public Optional<WarehouseRouteDefinition> findByTargetId(String targetId) {
        return Optional.ofNullable(
                definitionsByTargetId.get(requireId(targetId, "targetId")));
    }

    public Optional<WarehouseRouteDefinition> findByTerminalSensorId(String sensorId) {
        return Optional.ofNullable(definitionsByTerminalSensorId.get(
                requireId(sensorId, "sensorId")));
    }

    public WarehouseRouteCatalogSnapshot snapshot() {
        List<WarehouseRouteCatalogSnapshot.Entry> entries = definitions.stream()
                .map(definition -> new WarehouseRouteCatalogSnapshot.Entry(
                        definition.destination(),
                        definition.terminalArrivalSensorId(),
                        definition.terminalSegment().getLabel()))
                .toList();
        return new WarehouseRouteCatalogSnapshot(
                commonEntrySegment.getLabel(),
                entryDistance,
                entryDirection,
                entries);
    }

    private void requireCommonEntry(WarehouseRouteDefinition definition) {
        if (definition.entrySegment() != commonEntrySegment) {
            throw new IllegalArgumentException(
                    "Every warehouse route must use the exact common entry segment");
        }
        if (Float.compare(definition.entryDistance(), entryDistance) != 0) {
            throw new IllegalArgumentException(
                    "Every warehouse route must use the same entry distance");
        }
        if (definition.entryDirection() != entryDirection) {
            throw new IllegalArgumentException(
                    "Every warehouse route must use the same entry direction");
        }
    }

    private static String requireId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
