package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.util.ArrayList;
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
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;

public final class P2pWorkloadSnapshotFactory {

    public P2pWorkloadSnapshot create(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            BagPlanningResult bagPlanningResult,
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            P2pWorkloadCostConfig costConfig) {
        if (workSnapshot == null
                || manifestCatalog == null
                || bagPlanningResult == null
                || outboundAllocationSnapshot == null
                || costConfig == null) {
            throw new IllegalArgumentException("workload inputs must not be null");
        }

        Map<PhysicalToteId, String> remainingToteOwners = validateRemainingTotes(
                workSnapshot, manifestCatalog);
        Map<BagKey, PlannedBag> plannedBags = indexPlannedBags(
                bagPlanningResult, manifestCatalog);
        Set<BagKey> allocatedBagKeys = validateAllocatedBags(
                outboundAllocationSnapshot, plannedBags);

        LinkedHashSet<String> orderedServiceCentreIds = new LinkedHashSet<>();
        orderedServiceCentreIds.addAll(workSnapshot.remainingToteIdsByServiceCentre().keySet());
        orderedServiceCentreIds.addAll(workSnapshot.unallocatedEmptyOrdersByServiceCentre().keySet());
        bagPlanningResult.plannedBags().stream()
                .map(PlannedBag::serviceCentreId)
                .forEach(orderedServiceCentreIds::add);

        List<P2pServiceCentreWorkloadSnapshot> serviceCentres = new ArrayList<>();
        for (String serviceCentreId : orderedServiceCentreIds) {
            List<PhysicalToteId> remainingToteIds = workSnapshot.remainingToteIds(serviceCentreId);
            remainingToteIds.forEach(toteId -> {
                if (!serviceCentreId.equals(remainingToteOwners.get(toteId))) {
                    throw new IllegalStateException("Remaining tote owner changed during workload creation");
                }
            });

            List<PlannedBag> remainingBags = bagPlanningResult.plannedBags().stream()
                    .filter(bag -> bag.serviceCentreId().equals(serviceCentreId))
                    .filter(bag -> !allocatedBagKeys.contains(bag.bagKey()))
                    .toList();
            int remainingPackCount = remainingPackCount(remainingBags);
            List<BagKey> remainingBagKeys = remainingBags.stream()
                    .map(PlannedBag::bagKey)
                    .toList();
            List<OrderSheetKey> emptyOrders = workSnapshot
                    .unallocatedEmptyOrdersByServiceCentre()
                    .getOrDefault(serviceCentreId, List.of());
            Duration estimate = estimate(
                    remainingToteIds.size(),
                    remainingPackCount,
                    remainingBagKeys.size(),
                    costConfig);

            serviceCentres.add(new P2pServiceCentreWorkloadSnapshot(
                    serviceCentreId,
                    remainingToteIds,
                    remainingPackCount,
                    remainingBagKeys,
                    emptyOrders,
                    estimate));
        }
        return new P2pWorkloadSnapshot(serviceCentres);
    }

    private static Map<PhysicalToteId, String> validateRemainingTotes(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog) {
        Map<PhysicalToteId, String> owners = new LinkedHashMap<>();
        workSnapshot.remainingToteIdsByServiceCentre().forEach((serviceCentreId, toteIds) -> {
            for (PhysicalToteId toteId : toteIds) {
                InboundToteManifest manifest = manifestCatalog.findByPhysicalToteId(toteId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Remaining P2P tote has no inbound manifest: " + toteId.value()));
                if (!manifest.serviceCentreId().equals(serviceCentreId)) {
                    throw new IllegalStateException(
                            "Remaining P2P tote service centre does not match its manifest");
                }
                if (owners.putIfAbsent(toteId, serviceCentreId) != null) {
                    throw new IllegalStateException(
                            "Remaining P2P tote appears under multiple service centres");
                }
            }
        });
        return Map.copyOf(owners);
    }

    private static Map<BagKey, PlannedBag> indexPlannedBags(
            BagPlanningResult bagPlanningResult,
            InboundToteManifestCatalog manifestCatalog) {
        Map<String, PlannedPackTrace> tracesByPackId = new LinkedHashMap<>();
        for (PlannedPackTrace trace : bagPlanningResult.packTraces()) {
            tracesByPackId.put(trace.physicalPackId(), trace);
        }

        Map<BagKey, PlannedBag> plannedBags = new LinkedHashMap<>();
        Set<String> plannedPackIds = new LinkedHashSet<>();
        for (PlannedBag plannedBag : bagPlanningResult.plannedBags()) {
            plannedBags.put(plannedBag.bagKey(), plannedBag);
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
        return Map.copyOf(plannedBags);
    }

    private static Set<BagKey> validateAllocatedBags(
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            Map<BagKey, PlannedBag> plannedBags) {
        Set<BagKey> allocatedBagKeys = new LinkedHashSet<>();
        for (AllocatedOutboundBag allocatedBag : outboundAllocationSnapshot.allocatedBags()) {
            PlannedBag plannedBag = plannedBags.get(allocatedBag.bagKey());
            if (plannedBag == null) {
                throw new IllegalStateException(
                        "Allocated output bag is absent from the bag plan: "
                                + allocatedBag.bagKey());
            }
            if (!plannedBag.equals(allocatedBag.plannedBag())) {
                throw new IllegalStateException(
                        "Allocated output bag does not match the original planned bag");
            }
            allocatedBagKeys.add(allocatedBag.bagKey());
        }
        return Set.copyOf(allocatedBagKeys);
    }

    private static int remainingPackCount(List<PlannedBag> remainingBags) {
        int count = 0;
        try {
            for (PlannedBag remainingBag : remainingBags) {
                count = Math.addExact(count, remainingBag.physicalPackIds().size());
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("remaining pack count overflow", exception);
        }
        return count;
    }

    private static Duration estimate(
            int toteCount,
            int packCount,
            int bagCount,
            P2pWorkloadCostConfig costConfig) {
        try {
            long toteWork = Math.multiplyExact(toteCount, costConfig.toteHandlingNanos());
            long packWork = Math.multiplyExact(packCount, costConfig.packProcessingNanos());
            long bagWork = Math.multiplyExact(bagCount, costConfig.baggingNanos());
            return Duration.ofNanos(Math.addExact(Math.addExact(toteWork, packWork), bagWork));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("normalized P2P workload overflow", exception);
        }
    }
}
