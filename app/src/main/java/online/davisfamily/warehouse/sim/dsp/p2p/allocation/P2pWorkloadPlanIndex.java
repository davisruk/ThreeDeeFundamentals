package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;

final class P2pWorkloadPlanIndex {

    private final Map<BagKey, PlannedBag> plannedBagsByKey;
    private final Map<String, List<PlannedBag>> plannedBagsByServiceCentre;
    private final List<String> orderedServiceCentreIds;

    private P2pWorkloadPlanIndex(
            Map<BagKey, PlannedBag> plannedBagsByKey,
            Map<String, List<PlannedBag>> plannedBagsByServiceCentre,
            List<String> orderedServiceCentreIds) {
        this.plannedBagsByKey = plannedBagsByKey;
        this.plannedBagsByServiceCentre = plannedBagsByServiceCentre;
        this.orderedServiceCentreIds = orderedServiceCentreIds;
    }

    static P2pWorkloadPlanIndex from(
            BagPlanningResult bagPlanningResult,
            InboundToteManifestCatalog manifestCatalog) {
        if (bagPlanningResult == null || manifestCatalog == null) {
            throw new IllegalArgumentException("workload plan inputs must not be null");
        }

        Map<String, PlannedPackTrace> tracesByPackId = new LinkedHashMap<>();
        for (PlannedPackTrace trace : bagPlanningResult.packTraces()) {
            if (tracesByPackId.putIfAbsent(trace.physicalPackId(), trace) != null) {
                throw new IllegalStateException(
                        "Duplicate planned pack trace: " + trace.physicalPackId());
            }
        }
        Map<String, PlannedPackSlot> slotsByPackId = new LinkedHashMap<>();
        Map<BagKey, List<PlannedPackSlot>> slotsByBagKey = new LinkedHashMap<>();
        for (PlannedPackSlot slot : bagPlanningResult.plannedPackSlots()) {
            if (slotsByPackId.putIfAbsent(slot.reservedPhysicalPackId(), slot) != null) {
                throw new IllegalStateException(
                        "Duplicate planned pack slot: " + slot.reservedPhysicalPackId());
            }
            slotsByBagKey.computeIfAbsent(slot.bagKey(), ignored -> new ArrayList<>()).add(slot);
        }

        Map<BagKey, PlannedBag> plannedBags = new LinkedHashMap<>();
        Map<String, List<PlannedBag>> groupedBags = new LinkedHashMap<>();
        Set<String> plannedPackIds = new LinkedHashSet<>();
        for (PlannedBag plannedBag : bagPlanningResult.plannedBags()) {
            if (plannedBags.putIfAbsent(plannedBag.bagKey(), plannedBag) != null) {
                throw new IllegalStateException("Duplicate planned bag: " + plannedBag.bagKey());
            }
            groupedBags.computeIfAbsent(plannedBag.serviceCentreId(), ignored -> new ArrayList<>())
                    .add(plannedBag);
            List<PlannedPackSlot> bagSlots = slotsByBagKey.get(plannedBag.bagKey());
            if (bagSlots == null || !plannedBag.physicalPackIds().equals(
                    bagSlots.stream().map(PlannedPackSlot::reservedPhysicalPackId).toList())) {
                throw new IllegalStateException(
                        "Planned bag membership does not match planned slots: "
                                + plannedBag.bagKey());
            }
            for (String packId : plannedBag.physicalPackIds()) {
                if (!plannedPackIds.add(packId)) {
                    throw new IllegalStateException(
                            "Physical pack appears in multiple planned bags: " + packId);
                }
                PlannedPackSlot slot = slotsByPackId.get(packId);
                if (slot == null || !slot.bagKey().equals(plannedBag.bagKey())) {
                    throw new IllegalStateException(
                            "Planned bag pack is missing its matching planned slot: " + packId);
                }
                if (!slot.sourceProvenance().serviceCentreId()
                        .equals(plannedBag.serviceCentreId())) {
                    throw new IllegalStateException(
                            "Planned bag and pack slot service centres do not match");
                }
            }
        }
        if (!plannedPackIds.equals(slotsByPackId.keySet())) {
            throw new IllegalStateException(
                    "Planned pack slots must exactly match planned bag physical packs");
        }

        Set<String> tracedPackIds = new LinkedHashSet<>();
        for (PlannedPackSlot slot : bagPlanningResult.plannedPackSlots()) {
            String packId = slot.reservedPhysicalPackId();
            PlannedPackTrace trace = tracesByPackId.get(packId);
            if (slot.initialPhysicalToteId().isPresent()) {
                if (trace == null || !trace.bagKey().equals(slot.bagKey())
                        || !trace.inputPhysicalToteId().equals(
                                slot.initialPhysicalToteId().orElseThrow())) {
                    throw new IllegalStateException(
                            "Initially physical planned slot is missing its matching pack trace: "
                                    + packId);
                }
                tracedPackIds.add(packId);
                InboundToteManifest inputManifest = manifestCatalog
                        .findByPhysicalToteId(trace.inputPhysicalToteId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Planned pack input tote has no inbound manifest: " + packId));
                if (!inputManifest.serviceCentreId().equals(
                        slot.sourceProvenance().serviceCentreId())) {
                    throw new IllegalStateException(
                            "Planned slot service centre does not match its input manifest");
                }
            } else if (trace != null) {
                throw new IllegalStateException(
                        "Station-pending planned slot must not have a pack trace: " + packId);
            }
        }
        if (!tracedPackIds.equals(tracesByPackId.keySet())) {
            throw new IllegalStateException(
                    "Pack traces must exactly match initially physical planned slots");
        }

        Map<String, List<PlannedBag>> immutableGroupedBags = new LinkedHashMap<>();
        groupedBags.forEach((serviceCentreId, bags) ->
                immutableGroupedBags.put(serviceCentreId, List.copyOf(bags)));
        return new P2pWorkloadPlanIndex(
                immutableMap(plannedBags),
                immutableMap(immutableGroupedBags),
                List.copyOf(groupedBags.keySet()));
    }

    Map<BagKey, PlannedBag> plannedBagsByKey() {
        return plannedBagsByKey;
    }

    Map<String, List<PlannedBag>> plannedBagsByServiceCentre() {
        return plannedBagsByServiceCentre;
    }

    List<String> orderedServiceCentreIds() {
        return orderedServiceCentreIds;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
