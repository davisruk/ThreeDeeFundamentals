package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
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
        Map<String, Integer> slotCountByCorrelation = new LinkedHashMap<>();
        for (PlannedPackSlot slot : bagPlanningResult.plannedPackSlots()) {
            String correlationId = slot.bagKey().correlationId();
            slotCountByCorrelation.merge(correlationId, 1, Integer::sum);
        }
        for (PlannedBag plannedBag : bagPlanningResult.plannedBags()) {
            String correlationId = plannedBag.bagKey().correlationId();
            Integer slotCount = slotCountByCorrelation.get(correlationId);
            if (slotCount == null || slotCount.intValue() != plannedBag.physicalPackIds().size()) {
                throw new IllegalArgumentException(
                        "Planned bag does not have a complete slot requirement: "
                                + correlationId);
            }
            if (requirementsByCorrelation.putIfAbsent(
                    correlationId,
                    new P2pBagCorrelationRequirement(correlationId, slotCount)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate planned bag correlation: " + correlationId);
            }
        }

        if (requirementsByCorrelation.size() != slotCountByCorrelation.size()) {
            throw new IllegalArgumentException("Planned slot correlations must be distinct");
        }

        Map<PhysicalToteId, Set<P2pBagCorrelationRequirement>> byPhysicalToteId =
                new LinkedHashMap<>();
        Map<OrderSheetKey, Set<P2pBagCorrelationRequirement>> byOrderSheetKey =
                new LinkedHashMap<>();
        for (PlannedPackSlot slot : bagPlanningResult.plannedPackSlots()) {
            P2pBagCorrelationRequirement requirement = requirementsByCorrelation.get(
                    slot.bagKey().correlationId());
            if (requirement == null) {
                throw new IllegalArgumentException(
                        "Planned slot references an unplanned bag: " + slot.slotKey());
            }
            slot.initialPhysicalToteId().ifPresent(inputToteId ->
                    byPhysicalToteId.computeIfAbsent(
                            inputToteId, ignored -> new LinkedHashSet<>()).add(requirement));
            byOrderSheetKey.computeIfAbsent(
                    slot.fulfilmentOrderSheetKey(), ignored -> new LinkedHashSet<>())
                    .add(requirement);
        }

        return new P2pBagCorrelationRequirementCatalog(byPhysicalToteId, byOrderSheetKey);
    }

    public static P2pBagCorrelationRequirementCatalog from(BagPlanningResult bagPlanningResult) {
        return new P2pBagCorrelationRequirementCatalogFactory().create(bagPlanningResult);
    }
}
