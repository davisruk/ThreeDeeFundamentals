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
            tracesByPackId.put(trace.physicalPackId(), trace);
        }

        Map<BagKey, PlannedBag> plannedBags = new LinkedHashMap<>();
        Map<String, List<PlannedBag>> groupedBags = new LinkedHashMap<>();
        Set<String> plannedPackIds = new LinkedHashSet<>();
        for (PlannedBag plannedBag : bagPlanningResult.plannedBags()) {
            plannedBags.put(plannedBag.bagKey(), plannedBag);
            groupedBags.computeIfAbsent(plannedBag.serviceCentreId(), ignored -> new ArrayList<>())
                    .add(plannedBag);
            for (String packId : plannedBag.physicalPackIds()) {
                if (!plannedPackIds.add(packId)) {
                    throw new IllegalStateException(
                            "Physical pack appears in multiple planned bags: " + packId);
                }
                PlannedPackTrace trace = tracesByPackId.get(packId);
                if (trace == null || !trace.bagKey().equals(plannedBag.bagKey())) {
                    throw new IllegalStateException(
                            "Planned bag pack is missing its matching pack trace: " + packId);
                }
                if (!trace.sourceProvenance().serviceCentreId()
                        .equals(plannedBag.serviceCentreId())) {
                    throw new IllegalStateException(
                            "Planned bag and pack trace service centres do not match");
                }
                InboundToteManifest inputManifest = manifestCatalog
                        .findByPhysicalToteId(trace.inputPhysicalToteId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Planned pack input tote has no inbound manifest: " + packId));
                if (!inputManifest.serviceCentreId().equals(plannedBag.serviceCentreId())) {
                    throw new IllegalStateException(
                            "Planned bag service centre does not match its input manifest");
                }
            }
        }
        if (!plannedPackIds.equals(tracesByPackId.keySet())) {
            throw new IllegalStateException(
                    "Planned pack traces must exactly match planned bag physical packs");
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
