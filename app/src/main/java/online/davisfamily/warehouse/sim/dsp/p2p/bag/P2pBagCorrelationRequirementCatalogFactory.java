package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

/** Builds the immutable correlation requirements used by operational P2P allocation. */
public final class P2pBagCorrelationRequirementCatalogFactory {

    public P2pBagCorrelationRequirementCatalog create(BagPlanningResult bagPlanningResult) {
        if (bagPlanningResult == null) {
            throw new IllegalArgumentException("bagPlanningResult must not be null");
        }

        Map<String, P2pBagCorrelationRequirement> requirementsByCorrelation =
                new LinkedHashMap<>();
        Map<String, PlannedPackTrace> tracesByPackId = new LinkedHashMap<>();
        for (PlannedPackTrace trace : bagPlanningResult.packTraces()) {
            if (tracesByPackId.putIfAbsent(trace.physicalPackId(), trace) != null) {
                throw new IllegalArgumentException(
                        "Duplicate planned pack trace: " + trace.physicalPackId());
            }
        }

        for (PlannedBag plannedBag : bagPlanningResult.plannedBags()) {
            String correlationId = plannedBag.bagKey().correlationId();
            P2pBagCorrelationRequirement requirement =
                    new P2pBagCorrelationRequirement(
                            correlationId, plannedBag.physicalPackIds().size());
            if (requirementsByCorrelation.putIfAbsent(correlationId, requirement) != null) {
                throw new IllegalArgumentException(
                        "Duplicate planned bag correlation: " + correlationId);
            }
            for (String physicalPackId : plannedBag.physicalPackIds()) {
                PlannedPackTrace trace = tracesByPackId.get(physicalPackId);
                if (trace == null || !trace.bagKey().equals(plannedBag.bagKey())) {
                    throw new IllegalArgumentException(
                            "Planned bag pack is missing its matching trace: " + physicalPackId);
                }
            }
        }

        if (requirementsByCorrelation.size() != bagPlanningResult.plannedBags().size()) {
            throw new IllegalArgumentException("Planned bag correlations must be distinct");
        }

        Set<String> tracedPackIds = new LinkedHashSet<>();
        Map<PhysicalToteId, Set<P2pBagCorrelationRequirement>> byPhysicalToteId =
                new LinkedHashMap<>();
        Map<OrderSheetKey, Set<P2pBagCorrelationRequirement>> byOrderSheetKey =
                new LinkedHashMap<>();
        for (PlannedPackTrace trace : bagPlanningResult.packTraces()) {
            P2pBagCorrelationRequirement requirement = requirementsByCorrelation.get(
                    trace.bagKey().correlationId());
            if (requirement == null) {
                throw new IllegalArgumentException(
                        "Pack trace references an unplanned bag: " + trace.physicalPackId());
            }
            tracedPackIds.add(trace.physicalPackId());
            byPhysicalToteId.computeIfAbsent(
                    trace.inputPhysicalToteId(), ignored -> new LinkedHashSet<>())
                    .add(requirement);
            byOrderSheetKey.computeIfAbsent(
                    trace.fulfilmentOrderSheetKey(), ignored -> new LinkedHashSet<>())
                    .add(requirement);
        }

        Set<String> plannedPackIds = new LinkedHashSet<>();
        bagPlanningResult.plannedBags().forEach(bag -> plannedPackIds.addAll(bag.physicalPackIds()));
        if (!plannedPackIds.equals(tracedPackIds)) {
            throw new IllegalArgumentException(
                    "Planned pack traces must exactly match planned bag packs");
        }

        return new P2pBagCorrelationRequirementCatalog(byPhysicalToteId, byOrderSheetKey);
    }

    public static P2pBagCorrelationRequirementCatalog from(BagPlanningResult bagPlanningResult) {
        return new P2pBagCorrelationRequirementCatalogFactory().create(bagPlanningResult);
    }
}
