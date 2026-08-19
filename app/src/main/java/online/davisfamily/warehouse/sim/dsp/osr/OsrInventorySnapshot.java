package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OsrInventorySnapshot(
        int capacity,
        List<InboundToteManifest> storedTotes,
        List<InboundToteManifest> departedTotes) {

    public OsrInventorySnapshot {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        storedTotes = copyAndRejectNull(storedTotes, "storedTotes");
        departedTotes = copyAndRejectNull(departedTotes, "departedTotes");
        if (storedTotes.size() > capacity) {
            throw new IllegalArgumentException("storedTotes must not exceed capacity");
        }

        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (InboundToteManifest manifest : storedTotes) {
            requireUniquePhysicalToteId(physicalToteIds, manifest);
        }
        for (InboundToteManifest manifest : departedTotes) {
            requireUniquePhysicalToteId(physicalToteIds, manifest);
        }
    }

    public int occupancy() {
        return storedTotes.size();
    }

    public int remainingCapacity() {
        return capacity - occupancy();
    }

    public boolean full() {
        return occupancy() == capacity;
    }

    public boolean contains(PhysicalToteId physicalToteId) {
        return findStored(physicalToteId).isPresent();
    }

    public boolean hasDeparted(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return departedTotes.stream()
                .anyMatch(manifest -> manifest.physicalToteId().equals(physicalToteId));
    }

    public Optional<InboundToteManifest> findStored(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return storedTotes.stream()
                .filter(manifest -> manifest.physicalToteId().equals(physicalToteId))
                .findFirst();
    }

    public List<InboundToteManifest> storedTotesFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return storedTotes.stream()
                .filter(manifest -> manifest.orderSheetKey().equals(orderSheetKey))
                .toList();
    }

    public List<InboundToteManifest> storedTotesForServiceCentre(String serviceCentreId) {
        String normalizedServiceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        return storedTotes.stream()
                .filter(manifest -> manifest.serviceCentreId().equals(normalizedServiceCentreId))
                .toList();
    }

    public Map<String, Integer> occupancyByServiceCentre() {
        Map<String, Integer> occupancy = new LinkedHashMap<>();
        for (InboundToteManifest manifest : storedTotes) {
            occupancy.merge(manifest.serviceCentreId(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(occupancy);
    }

    public Map<OrderType, Integer> occupancyByOrderType() {
        Map<OrderType, Integer> occupancy = new LinkedHashMap<>();
        for (InboundToteManifest manifest : storedTotes) {
            occupancy.merge(manifest.orderType(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(occupancy);
    }

    private static List<InboundToteManifest> copyAndRejectNull(
            List<InboundToteManifest> manifests,
            String fieldName) {
        if (manifests == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (manifests.stream().anyMatch(manifest -> manifest == null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null");
        }
        return List.copyOf(manifests);
    }

    private static void requireUniquePhysicalToteId(
            Set<PhysicalToteId> physicalToteIds,
            InboundToteManifest manifest) {
        if (!physicalToteIds.add(manifest.physicalToteId())) {
            throw new IllegalArgumentException(
                    "Duplicate physical tote ID: " + manifest.physicalToteId().value());
        }
    }

    private static void requirePhysicalToteId(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
