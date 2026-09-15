package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;

public final class P2pWorkloadSnapshotFactory {

    private BagPlanningResult lastBagPlanningResult;
    private InboundToteManifestCatalog lastManifestCatalog;
    private P2pWorkloadPlanIndex lastPlanIndex;
    private P2pWorkloadSnapshot lastSnapshot;
    private Map<String, P2pServiceCentreWorkloadSnapshot> lastServiceCentreSnapshots = Map.of();
    private ValidationCache lastValidationCache;

    public P2pWorkloadSnapshot create(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            BagPlanningResult bagPlanningResult,
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            P2pWorkloadCostConfig costConfig) {
        return create(
                workSnapshot,
                manifestCatalog,
                bagPlanningResult,
                outboundAllocationSnapshot,
                costConfig,
                new Av02InventorySnapshot(1, List.of(), List.of()),
                compatibilityLifecycleSnapshot(manifestCatalog));
    }

    public P2pWorkloadSnapshot create(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            BagPlanningResult bagPlanningResult,
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            P2pWorkloadCostConfig costConfig,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        if (workSnapshot == null
                || manifestCatalog == null
                || bagPlanningResult == null
                || outboundAllocationSnapshot == null
                || costConfig == null
                || av02InventorySnapshot == null
                || lifecycleSnapshot == null) {
            throw new IllegalArgumentException("workload inputs must not be null");
        }

        Map<PhysicalToteId, String> remainingToteOwners;
        Set<BagKey> allocatedBagKeys;
        P2pWorkloadPlanIndex planIndex;
        ValidationCache replacementValidationCache = null;
        if (lastValidationCache != null && lastValidationCache.matches(
                workSnapshot,
                manifestCatalog,
                bagPlanningResult,
                outboundAllocationSnapshot,
                av02InventorySnapshot,
                lifecycleSnapshot)) {
            remainingToteOwners = lastValidationCache.remainingToteOwners();
            allocatedBagKeys = lastValidationCache.allocatedBagKeys();
            planIndex = planIndexFor(bagPlanningResult, manifestCatalog);
        } else {
            remainingToteOwners = validateRemainingTotes(
                    workSnapshot,
                    manifestCatalog,
                    av02InventorySnapshot,
                    lifecycleSnapshot);
            planIndex = planIndexFor(bagPlanningResult, manifestCatalog);
            allocatedBagKeys = validateAllocatedBags(
                    outboundAllocationSnapshot, planIndex.plannedBagsByKey());
            replacementValidationCache = new ValidationCache(
                    workSnapshot,
                    manifestCatalog,
                    bagPlanningResult,
                    outboundAllocationSnapshot,
                    av02InventorySnapshot,
                    lifecycleSnapshot,
                    remainingToteOwners,
                    allocatedBagKeys);
        }

        LinkedHashSet<String> orderedServiceCentreIds = new LinkedHashSet<>();
        orderedServiceCentreIds.addAll(workSnapshot.remainingToteIdsByServiceCentre().keySet());
        orderedServiceCentreIds.addAll(workSnapshot.unallocatedEmptyOrdersByServiceCentre().keySet());
        orderedServiceCentreIds.addAll(planIndex.orderedServiceCentreIds());

        List<P2pServiceCentreWorkloadSnapshot> serviceCentres = new ArrayList<>();
        for (String serviceCentreId : orderedServiceCentreIds) {
            String normalizedServiceCentreId = serviceCentreId.trim();
            List<PhysicalToteId> remainingToteIds = workSnapshot.remainingToteIds(
                    normalizedServiceCentreId);
            remainingToteIds.forEach(toteId -> {
                if (!normalizedServiceCentreId.equals(remainingToteOwners.get(toteId))) {
                    throw new IllegalStateException("Remaining tote owner changed during workload creation");
                }
            });

            List<BagKey> remainingBagKeys = new ArrayList<>();
            int remainingPackCount = 0;
            try {
                for (PlannedBag plannedBag : planIndex.plannedBagsByServiceCentre()
                        .getOrDefault(normalizedServiceCentreId, List.of())) {
                    if (allocatedBagKeys.contains(plannedBag.bagKey())) {
                        continue;
                    }
                    remainingBagKeys.add(plannedBag.bagKey());
                    remainingPackCount = Math.addExact(
                            remainingPackCount,
                            plannedBag.physicalPackIds().size());
                }
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("remaining pack count overflow", exception);
            }
            List<OrderSheetKey> emptyOrders = workSnapshot
                    .unallocatedEmptyOrdersByServiceCentre()
                    .getOrDefault(normalizedServiceCentreId, List.of());
            Duration estimate = estimate(
                    remainingToteIds.size(),
                    remainingPackCount,
                    remainingBagKeys.size(),
                    costConfig);

            P2pServiceCentreWorkloadSnapshot previous = lastServiceCentreSnapshots.get(
                    normalizedServiceCentreId);
            if (sameWorkloadValues(
                    previous,
                    normalizedServiceCentreId,
                    remainingToteIds,
                    remainingPackCount,
                    remainingBagKeys,
                    emptyOrders,
                    estimate)) {
                serviceCentres.add(previous);
            } else {
                serviceCentres.add(new P2pServiceCentreWorkloadSnapshot(
                        normalizedServiceCentreId,
                        remainingToteIds,
                        remainingPackCount,
                        remainingBagKeys,
                        emptyOrders,
                        estimate));
            }
        }

        if (sameServiceCentreSequence(serviceCentres)) {
            if (replacementValidationCache != null) {
                lastValidationCache = replacementValidationCache;
            }
            return lastSnapshot;
        }

        P2pWorkloadSnapshot replacement = new P2pWorkloadSnapshot(serviceCentres);
        Map<String, P2pServiceCentreWorkloadSnapshot> replacementServiceCentreSnapshots =
                new LinkedHashMap<>();
        for (P2pServiceCentreWorkloadSnapshot serviceCentre : serviceCentres) {
            replacementServiceCentreSnapshots.put(
                    serviceCentre.serviceCentreId(),
                    serviceCentre);
        }
        lastSnapshot = replacement;
        lastServiceCentreSnapshots = Collections.unmodifiableMap(
                replacementServiceCentreSnapshots);
        if (replacementValidationCache != null) {
            lastValidationCache = replacementValidationCache;
        }
        return replacement;
    }

    P2pWorkloadPlanIndex planIndexFor(
            BagPlanningResult bagPlanningResult,
            InboundToteManifestCatalog manifestCatalog) {
        if (bagPlanningResult == lastBagPlanningResult
                && manifestCatalog == lastManifestCatalog
                && lastPlanIndex != null) {
            return lastPlanIndex;
        }
        P2pWorkloadPlanIndex replacement = P2pWorkloadPlanIndex.from(
                bagPlanningResult, manifestCatalog);
        lastBagPlanningResult = bagPlanningResult;
        lastManifestCatalog = manifestCatalog;
        lastPlanIndex = replacement;
        return replacement;
    }

    private static Map<PhysicalToteId, String> validateRemainingTotes(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
        Map<PhysicalToteId, String> owners = new LinkedHashMap<>();
        workSnapshot.remainingToteIdsByServiceCentre().forEach((serviceCentreId, toteIds) -> {
            for (PhysicalToteId toteId : toteIds) {
                InboundToteManifest manifest = manifestCatalog.findByPhysicalToteId(toteId)
                        .orElse(null);
                Av02AllocatedTote av02Tote = av02InventorySnapshot.findTote(toteId).orElse(null);
                if (manifest != null && av02Tote != null) {
                    throw new IllegalStateException(
                            "Remaining P2P tote is present in both OSR and AV02 sources: "
                                    + toteId.value());
                }
                if (manifest == null && av02Tote == null) {
                    throw new IllegalStateException(
                            "Remaining P2P tote has no OSR manifest or AV02 identity: "
                                    + toteId.value());
                }

                PhysicalToteRecord lifecycleRecord = lifecycleSnapshot.totes().get(toteId);
                if (lifecycleRecord == null || !lifecycleRecord.id().equals(toteId)) {
                    throw new IllegalStateException(
                            "Remaining P2P tote has no physical lifecycle record: "
                                    + toteId.value());
                }
                if (manifest != null) {
                    if (!manifest.serviceCentreId().equals(serviceCentreId)) {
                        throw new IllegalStateException(
                                "Remaining P2P tote service centre does not match its manifest");
                    }
                    if (lifecycleRecord.role() != PhysicalToteRole.INBOUND_PACK) {
                        throw new IllegalStateException(
                                "OSR physical tote lifecycle record must use INBOUND_PACK role: "
                                        + toteId.value());
                    }
                } else {
                    if (!av02Tote.serviceCentreId().equals(serviceCentreId)
                            || av02Tote.identity().physicalToteRole() != PhysicalToteRole.PRE_P2P
                            || av02Tote.physicalTote().role() != PhysicalToteRole.PRE_P2P
                            || lifecycleRecord.role() != PhysicalToteRole.PRE_P2P
                            || lifecycleRecord.state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
                        throw new IllegalStateException(
                                "AV02 physical tote identity, lifecycle role, or service centre does not match: "
                                        + toteId.value());
                    }
                }
                if (owners.putIfAbsent(toteId, serviceCentreId) != null) {
                    throw new IllegalStateException(
                            "Remaining P2P tote appears under multiple service centres");
                }
            }
        });
        return Map.copyOf(owners);
    }

    private static PhysicalToteLifecycleSnapshot compatibilityLifecycleSnapshot(
            InboundToteManifestCatalog manifestCatalog) {
        if (manifestCatalog == null) {
            return new PhysicalToteLifecycleSnapshot(Map.of(), List.of());
        }
        Map<PhysicalToteId, PhysicalToteRecord> records = new LinkedHashMap<>();
        for (InboundToteManifest manifest : manifestCatalog.manifests()) {
            records.put(manifest.physicalToteId(),
                    PhysicalToteRecord.inboundPack(manifest.physicalToteId()));
        }
        return new PhysicalToteLifecycleSnapshot(records, List.of());
    }

    private static Set<BagKey> validateAllocatedBags(
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            Map<BagKey, PlannedBag> plannedBags) {
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
        }
        return outboundAllocationSnapshot.allocatedBagKeys();
    }

    private static boolean sameWorkloadValues(
            P2pServiceCentreWorkloadSnapshot previous,
            String serviceCentreId,
            List<PhysicalToteId> remainingToteIds,
            int remainingPackCount,
            List<BagKey> remainingBagKeys,
            List<OrderSheetKey> emptyOrders,
            Duration estimate) {
        return previous != null
                && previous.serviceCentreId().equals(serviceCentreId)
                && previous.remainingToteIds().equals(remainingToteIds)
                && previous.remainingUnallocatedPackCount() == remainingPackCount
                && previous.remainingBagKeys().equals(remainingBagKeys)
                && previous.unallocatedEmptyOrderSheetKeys().equals(emptyOrders)
                && previous.estimatedSingleLineWork().equals(estimate);
    }

    private boolean sameServiceCentreSequence(
            List<P2pServiceCentreWorkloadSnapshot> serviceCentres) {
        if (lastSnapshot == null || serviceCentres.size() != lastSnapshot.serviceCentres().size()) {
            return false;
        }
        for (int index = 0; index < serviceCentres.size(); index++) {
            if (serviceCentres.get(index) != lastSnapshot.serviceCentres().get(index)) {
                return false;
            }
        }
        return true;
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

    private record ValidationCache(
            P2pServiceCentreWorkSnapshot workSnapshot,
            InboundToteManifestCatalog manifestCatalog,
            BagPlanningResult bagPlanningResult,
            OutboundAllocationSnapshot outboundAllocationSnapshot,
            Av02InventorySnapshot av02InventorySnapshot,
            PhysicalToteLifecycleSnapshot lifecycleSnapshot,
            Map<PhysicalToteId, String> remainingToteOwners,
            Set<BagKey> allocatedBagKeys) {

        private boolean matches(
                P2pServiceCentreWorkSnapshot workSnapshot,
                InboundToteManifestCatalog manifestCatalog,
                BagPlanningResult bagPlanningResult,
                OutboundAllocationSnapshot outboundAllocationSnapshot,
                Av02InventorySnapshot av02InventorySnapshot,
                PhysicalToteLifecycleSnapshot lifecycleSnapshot) {
            return this.workSnapshot == workSnapshot
                    && this.manifestCatalog == manifestCatalog
                    && this.bagPlanningResult == bagPlanningResult
                    && this.outboundAllocationSnapshot == outboundAllocationSnapshot
                    && this.av02InventorySnapshot == av02InventorySnapshot
                    && this.lifecycleSnapshot == lifecycleSnapshot;
        }
    }
}
