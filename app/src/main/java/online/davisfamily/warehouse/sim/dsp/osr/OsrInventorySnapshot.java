package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OsrInventorySnapshot {
    private static final List<InboundToteManifest> EMPTY_TOTES = List.of();

    private final int capacity;
    private final List<InboundToteManifest> storedTotes;
    private final List<InboundToteManifest> departedTotes;
    private final Map<PhysicalToteId, InboundToteManifest> storedTotesById;
    private final Set<PhysicalToteId> departedToteIds;
    private final Map<OrderSheetKey, List<InboundToteManifest>> storedTotesByOrderSheet;
    private final Map<String, List<InboundToteManifest>> storedTotesByServiceCentre;
    private final Map<String, Integer> occupancyByServiceCentre;
    private final Map<OrderType, Integer> occupancyByOrderType;

    public OsrInventorySnapshot(
            int capacity,
            List<InboundToteManifest> storedTotes,
            List<InboundToteManifest> departedTotes) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        this.storedTotes = copyAndRejectNull(storedTotes, "storedTotes");
        this.departedTotes = copyAndRejectNull(departedTotes, "departedTotes");
        if (this.storedTotes.size() > capacity) {
            throw new IllegalArgumentException("storedTotes must not exceed capacity");
        }

        Map<PhysicalToteId, InboundToteManifest> storedById = new LinkedHashMap<>();
        Set<PhysicalToteId> allPhysicalToteIds = new LinkedHashSet<>();
        Map<OrderSheetKey, List<InboundToteManifest>> byOrderSheet = new LinkedHashMap<>();
        Map<String, List<InboundToteManifest>> byServiceCentre = new LinkedHashMap<>();
        Map<String, Integer> byServiceCentreOccupancy = new LinkedHashMap<>();
        Map<OrderType, Integer> byOrderTypeOccupancy = new LinkedHashMap<>();

        for (InboundToteManifest manifest : this.storedTotes) {
            requireUniquePhysicalToteId(allPhysicalToteIds, manifest);
            storedById.put(manifest.physicalToteId(), manifest);
            byOrderSheet.computeIfAbsent(manifest.orderSheetKey(), ignored -> new ArrayList<>())
                    .add(manifest);
            byServiceCentre.computeIfAbsent(
                    manifest.serviceCentreId(),
                    ignored -> new ArrayList<>()).add(manifest);
            byServiceCentreOccupancy.merge(manifest.serviceCentreId(), 1, Integer::sum);
            byOrderTypeOccupancy.merge(manifest.orderType(), 1, Integer::sum);
        }

        Set<PhysicalToteId> departedIds = new LinkedHashSet<>();
        for (InboundToteManifest manifest : this.departedTotes) {
            requireUniquePhysicalToteId(allPhysicalToteIds, manifest);
            departedIds.add(manifest.physicalToteId());
        }

        this.storedTotesById = Collections.unmodifiableMap(storedById);
        this.departedToteIds = Collections.unmodifiableSet(departedIds);
        this.storedTotesByOrderSheet = immutableListIndex(byOrderSheet);
        this.storedTotesByServiceCentre = immutableListIndex(byServiceCentre);
        this.occupancyByServiceCentre = Collections.unmodifiableMap(byServiceCentreOccupancy);
        this.occupancyByOrderType = Collections.unmodifiableMap(byOrderTypeOccupancy);
    }

    public int capacity() {
        return capacity;
    }

    public List<InboundToteManifest> storedTotes() {
        return storedTotes;
    }

    public List<InboundToteManifest> departedTotes() {
        return departedTotes;
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
        requirePhysicalToteId(physicalToteId);
        return storedTotesById.containsKey(physicalToteId);
    }

    public boolean hasDeparted(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return departedToteIds.contains(physicalToteId);
    }

    public Optional<InboundToteManifest> findStored(PhysicalToteId physicalToteId) {
        requirePhysicalToteId(physicalToteId);
        return Optional.ofNullable(storedTotesById.get(physicalToteId));
    }

    public List<InboundToteManifest> storedTotesFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return storedTotesByOrderSheet.getOrDefault(orderSheetKey, EMPTY_TOTES);
    }

    public List<InboundToteManifest> storedTotesForServiceCentre(String serviceCentreId) {
        String normalizedServiceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        return storedTotesByServiceCentre.getOrDefault(normalizedServiceCentreId, EMPTY_TOTES);
    }

    public Map<String, Integer> occupancyByServiceCentre() {
        return occupancyByServiceCentre;
    }

    public Map<OrderType, Integer> occupancyByOrderType() {
        return occupancyByOrderType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OsrInventorySnapshot that)) {
            return false;
        }
        return capacity == that.capacity
                && storedTotes.equals(that.storedTotes)
                && departedTotes.equals(that.departedTotes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, storedTotes, departedTotes);
    }

    @Override
    public String toString() {
        return "OsrInventorySnapshot[capacity=" + capacity
                + ", storedTotes=" + storedTotes
                + ", departedTotes=" + departedTotes + "]";
    }

    private static <K> Map<K, List<InboundToteManifest>> immutableListIndex(
            Map<K, List<InboundToteManifest>> indexed) {
        Map<K, List<InboundToteManifest>> immutable = new LinkedHashMap<>();
        indexed.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private static List<InboundToteManifest> copyAndRejectNull(
            List<InboundToteManifest> manifests,
            String fieldName) {
        if (manifests == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        for (InboundToteManifest manifest : manifests) {
            if (manifest == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
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
