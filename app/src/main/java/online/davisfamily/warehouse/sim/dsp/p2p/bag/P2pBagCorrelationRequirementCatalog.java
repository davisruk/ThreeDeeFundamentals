package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;

/**
 * Immutable lookup of the complete bag correlations required by each physical
 * input tote, and by each logical sheet that may later receive an AV02 tote.
 */
public final class P2pBagCorrelationRequirementCatalog {
    private final Map<PhysicalToteId, Set<P2pBagCorrelationRequirement>> byPhysicalToteId;
    private final Map<OrderSheetKey, Set<P2pBagCorrelationRequirement>> byOrderSheetKey;
    private final List<P2pBagCorrelationRequirement> allRequirements;
    private final Set<String> correlationIds;

    public P2pBagCorrelationRequirementCatalog(
            Map<PhysicalToteId, ? extends Collection<P2pBagCorrelationRequirement>>
                    requirementsByPhysicalToteId,
            Map<OrderSheetKey, ? extends Collection<P2pBagCorrelationRequirement>>
                    requirementsByOrderSheetKey) {
        if (requirementsByPhysicalToteId == null || requirementsByOrderSheetKey == null) {
            throw new IllegalArgumentException("requirement maps must not be null");
        }
        this.byPhysicalToteId = copyMap(
                requirementsByPhysicalToteId, "requirementsByPhysicalToteId");
        this.byOrderSheetKey = copyMap(
                requirementsByOrderSheetKey, "requirementsByOrderSheetKey");

        LinkedHashMap<String, P2pBagCorrelationRequirement> unique = new LinkedHashMap<>();
        this.byPhysicalToteId.values().stream()
                .flatMap(Collection::stream)
                .forEach(requirement -> putUnique(unique, requirement));
        this.byOrderSheetKey.values().stream()
                .flatMap(Collection::stream)
                .forEach(requirement -> putUnique(unique, requirement));
        this.allRequirements = List.copyOf(unique.values());
        this.correlationIds = Set.copyOf(unique.keySet());
    }

    public static P2pBagCorrelationRequirementCatalog empty() {
        return new P2pBagCorrelationRequirementCatalog(Map.of(), Map.of());
    }

    public Set<P2pBagCorrelationRequirement> requirementsFor(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return byPhysicalToteId.getOrDefault(physicalToteId, Set.of());
    }

    public Set<P2pBagCorrelationRequirement> requirementsFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return byOrderSheetKey.getOrDefault(orderSheetKey, Set.of());
    }

    public Set<P2pBagCorrelationRequirement> requirementsFor(
            OperationalPhysicalToteSource source,
            PhysicalToteId physicalToteId,
            OrderSheetKey orderSheetKey) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return switch (source) {
            case OSR -> requirementsFor(physicalToteId);
            case AV02 -> requirementsFor(orderSheetKey);
        };
    }

    public Set<P2pBagCorrelationRequirement> requirementsFor(
            P2pReleaseAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return requirementsFor(
                request.source(), request.physicalToteId(), request.orderSheetKey());
    }

    public Set<P2pBagCorrelationRequirement> forPhysicalTote(PhysicalToteId physicalToteId) {
        return requirementsFor(physicalToteId);
    }

    public Set<P2pBagCorrelationRequirement> forOrderSheet(OrderSheetKey orderSheetKey) {
        return requirementsFor(orderSheetKey);
    }

    public List<P2pBagCorrelationRequirement> allRequirements() {
        return allRequirements;
    }

    public Set<String> correlationIds() {
        return correlationIds;
    }

    public Map<PhysicalToteId, Set<P2pBagCorrelationRequirement>> requirementsByPhysicalToteId() {
        return byPhysicalToteId;
    }

    public Map<OrderSheetKey, Set<P2pBagCorrelationRequirement>> requirementsByOrderSheetKey() {
        return byOrderSheetKey;
    }

    private static <K> Map<K, Set<P2pBagCorrelationRequirement>> copyMap(
            Map<K, ? extends Collection<P2pBagCorrelationRequirement>> source,
            String fieldName) {
        Map<K, Set<P2pBagCorrelationRequirement>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> {
            if (key == null || values == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
            LinkedHashSet<P2pBagCorrelationRequirement> requirements = new LinkedHashSet<>();
            for (P2pBagCorrelationRequirement requirement : values) {
                if (requirement == null) {
                    throw new IllegalArgumentException(fieldName + " must not contain null values");
                }
                requirements.add(requirement);
            }
            copy.put(key, Collections.unmodifiableSet(requirements));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void putUnique(
            Map<String, P2pBagCorrelationRequirement> unique,
            P2pBagCorrelationRequirement requirement) {
        P2pBagCorrelationRequirement existing = unique.putIfAbsent(
                requirement.correlationId(), requirement);
        if (existing != null && !existing.equals(requirement)) {
            throw new IllegalArgumentException(
                    "Conflicting expected pack count for correlation "
                            + requirement.correlationId());
        }
    }
}
