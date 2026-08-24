package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record P2pServiceCentreWorkSnapshot(
        Map<String, List<PhysicalToteId>> remainingToteIdsByServiceCentre,
        Map<String, List<OrderSheetKey>> unallocatedEmptyOrdersByServiceCentre) {

    public P2pServiceCentreWorkSnapshot {
        remainingToteIdsByServiceCentre = immutableCopy(
                remainingToteIdsByServiceCentre, "remainingToteIdsByServiceCentre");
        unallocatedEmptyOrdersByServiceCentre = immutableCopy(
                unallocatedEmptyOrdersByServiceCentre, "unallocatedEmptyOrdersByServiceCentre");
    }

    public static P2pServiceCentreWorkSnapshot empty() {
        return new P2pServiceCentreWorkSnapshot(Map.of(), Map.of());
    }

    public List<PhysicalToteId> remainingToteIds(String serviceCentreId) {
        return remainingToteIdsByServiceCentre.getOrDefault(
                requireValue(serviceCentreId, "serviceCentreId"), List.of());
    }

    public boolean hasRemainingWork(String serviceCentreId) {
        return !remainingToteIds(serviceCentreId).isEmpty();
    }

    private static <T> Map<String, List<T>> immutableCopy(
            Map<String, List<T>> source,
            String fieldName) {
        if (source == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        Map<String, List<T>> copy = new LinkedHashMap<>();
        source.forEach((serviceCentreId, values) -> {
            String normalizedId = requireValue(serviceCentreId, fieldName + " key");
            if (values == null || values.stream().anyMatch(value -> value == null)) {
                throw new IllegalArgumentException(fieldName + " values must not be null or contain null");
            }
            if (copy.putIfAbsent(normalizedId, List.copyOf(values)) != null) {
                throw new IllegalArgumentException(fieldName + " contains duplicate normalized keys");
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
