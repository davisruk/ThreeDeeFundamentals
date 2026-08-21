package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record WarehouseRouteCatalogSnapshot(
        String commonEntrySegmentLabel,
        float entryDistance,
        TravelDirection entryDirection,
        List<Entry> entries) {

    public record Entry(
            OperationalRouteDestination destination,
            String terminalArrivalSensorId,
            String terminalSegmentLabel) {

        public Entry {
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (terminalArrivalSensorId == null || terminalArrivalSensorId.isBlank()) {
                throw new IllegalArgumentException(
                        "terminalArrivalSensorId must not be blank");
            }
            if (terminalSegmentLabel == null || terminalSegmentLabel.isBlank()) {
                throw new IllegalArgumentException("terminalSegmentLabel must not be blank");
            }
            terminalArrivalSensorId = terminalArrivalSensorId.trim();
            terminalSegmentLabel = terminalSegmentLabel.trim();
        }
    }

    public WarehouseRouteCatalogSnapshot {
        if (commonEntrySegmentLabel == null || commonEntrySegmentLabel.isBlank()) {
            throw new IllegalArgumentException(
                    "commonEntrySegmentLabel must not be blank");
        }
        if (!Float.isFinite(entryDistance) || entryDistance < 0f) {
            throw new IllegalArgumentException("entryDistance must be finite and >= 0");
        }
        if (entryDirection == null) {
            throw new IllegalArgumentException("entryDirection must not be null");
        }
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        Set<String> targetIds = new LinkedHashSet<>();
        Set<String> terminalArrivalSensorIds = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            if (!targetIds.add(entry.destination().targetId())) {
                throw new IllegalArgumentException(
                        "Duplicate warehouse route target ID: "
                                + entry.destination().targetId());
            }
            if (!terminalArrivalSensorIds.add(entry.terminalArrivalSensorId())) {
                throw new IllegalArgumentException(
                        "Duplicate terminal arrival sensor ID: "
                                + entry.terminalArrivalSensorId());
            }
        }
        commonEntrySegmentLabel = commonEntrySegmentLabel.trim();
        entries = List.copyOf(entries);
    }
}
