package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

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
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pMissingPackSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;

/**
 * Simulation-thread-owned single-entry projections for completion evaluation.
 *
 * <p>The owner snapshots remain the cache identities. Each projection is built completely before
 * its identity and value are published, and a change in one owner does not invalidate any other
 * projection.</p>
 */
final class DspFullDayCompletionProjectionCache {
    private final Map<String, List<InboundToteManifest>> manifestsByServiceCentre;
    private final Map<String, List<PlannedBag>> plannedBagsByServiceCentre;

    private DspSupplySnapshot lastSupplySnapshot;
    private SupplyProjection lastSupplyProjection;
    private PhysicalToteLifecycleSnapshot lastLifecycleSnapshot;
    private LifecycleProjection lastLifecycleProjection;
    private OsrInventorySnapshot lastOsrSnapshot;
    private OsrProjection lastOsrProjection;
    private Av02InventorySnapshot lastAv02Snapshot;
    private Av02Projection lastAv02Projection;
    private OutboundAllocationSnapshot lastOutboundSnapshot;
    private P2pMissingPackSnapshot lastMissingPackSnapshot;
    private OutboundProjection lastOutboundProjection;

    DspFullDayCompletionProjectionCache(
            Map<String, List<InboundToteManifest>> manifestsByServiceCentre,
            Map<String, List<PlannedBag>> plannedBagsByServiceCentre) {
        if (manifestsByServiceCentre == null) {
            throw new IllegalArgumentException("manifestsByServiceCentre must not be null");
        }
        if (plannedBagsByServiceCentre == null) {
            throw new IllegalArgumentException("plannedBagsByServiceCentre must not be null");
        }
        this.manifestsByServiceCentre = manifestsByServiceCentre;
        this.plannedBagsByServiceCentre = plannedBagsByServiceCentre;
    }

    SupplyProjection supplyProjection(DspSupplySnapshot snapshot) {
        requireSnapshot(snapshot, "supplySnapshot");
        if (snapshot == lastSupplySnapshot && lastSupplyProjection != null) {
            return lastSupplyProjection;
        }
        SupplyProjection replacement = buildSupplyProjection(snapshot);
        lastSupplySnapshot = snapshot;
        lastSupplyProjection = replacement;
        return replacement;
    }

    LifecycleProjection lifecycleProjection(PhysicalToteLifecycleSnapshot snapshot) {
        requireSnapshot(snapshot, "lifecycleSnapshot");
        if (snapshot == lastLifecycleSnapshot && lastLifecycleProjection != null) {
            return lastLifecycleProjection;
        }
        LifecycleProjection replacement = buildLifecycleProjection(snapshot);
        lastLifecycleSnapshot = snapshot;
        lastLifecycleProjection = replacement;
        return replacement;
    }

    OsrProjection osrProjection(OsrInventorySnapshot snapshot) {
        requireSnapshot(snapshot, "osrSnapshot");
        if (snapshot == lastOsrSnapshot && lastOsrProjection != null) {
            return lastOsrProjection;
        }
        OsrProjection replacement = buildOsrProjection(snapshot);
        lastOsrSnapshot = snapshot;
        lastOsrProjection = replacement;
        return replacement;
    }

    Av02Projection av02Projection(Av02InventorySnapshot snapshot) {
        requireSnapshot(snapshot, "av02Snapshot");
        if (snapshot == lastAv02Snapshot && lastAv02Projection != null) {
            return lastAv02Projection;
        }
        Av02Projection replacement = buildAv02Projection(snapshot);
        lastAv02Snapshot = snapshot;
        lastAv02Projection = replacement;
        return replacement;
    }

    OutboundProjection outboundProjection(OutboundAllocationSnapshot snapshot) {
        return outboundProjection(snapshot, P2pMissingPackSnapshot.empty());
    }

    OutboundProjection outboundProjection(
            OutboundAllocationSnapshot snapshot,
            P2pMissingPackSnapshot missingPackSnapshot) {
        requireSnapshot(snapshot, "outboundSnapshot");
        requireSnapshot(missingPackSnapshot, "missingPackSnapshot");
        if (snapshot == lastOutboundSnapshot
                && missingPackSnapshot == lastMissingPackSnapshot
                && lastOutboundProjection != null) {
            return lastOutboundProjection;
        }
        OutboundProjection replacement = buildOutboundProjection(snapshot, missingPackSnapshot);
        lastOutboundSnapshot = snapshot;
        lastMissingPackSnapshot = missingPackSnapshot;
        lastOutboundProjection = replacement;
        return replacement;
    }

    private SupplyProjection buildSupplyProjection(DspSupplySnapshot snapshot) {
        List<String> serviceCentreIds = new ArrayList<>();
        Map<String, Integer> capacityBlockedCounts = new LinkedHashMap<>();
        for (ServiceCentreSupplySnapshot serviceCentre : snapshot.serviceCentres()) {
            String serviceCentreId = serviceCentre.serviceCentreId();
            serviceCentreIds.add(serviceCentreId);
            int blockedCount = 0;
            for (var physicalTote : serviceCentre.physicalTotes()) {
                if (physicalTote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY) {
                    blockedCount++;
                }
            }
            capacityBlockedCounts.put(serviceCentreId, blockedCount);
        }
        return new SupplyProjection(serviceCentreIds, capacityBlockedCounts);
    }

    private LifecycleProjection buildLifecycleProjection(
            PhysicalToteLifecycleSnapshot snapshot) {
        Map<String, Integer> nonTerminalInboundCounts = new LinkedHashMap<>();
        for (Map.Entry<String, List<InboundToteManifest>> entry
                : manifestsByServiceCentre.entrySet()) {
            for (InboundToteManifest manifest : entry.getValue()) {
                PhysicalToteRecord tote = snapshot.totes().get(manifest.physicalToteId());
                if (tote != null && !tote.terminal()) {
                    nonTerminalInboundCounts.merge(
                            manifest.serviceCentreId(), 1, Integer::sum);
                }
            }
        }

        List<PhysicalToteId> nonTerminalPhysicalToteIds = new ArrayList<>();
        for (PhysicalToteRecord tote : snapshot.totes().values()) {
            if (!tote.terminal()) {
                nonTerminalPhysicalToteIds.add(tote.id());
            }
        }
        return new LifecycleProjection(nonTerminalInboundCounts, nonTerminalPhysicalToteIds);
    }

    private static OsrProjection buildOsrProjection(OsrInventorySnapshot snapshot) {
        return new OsrProjection(countByServiceCentre(
                snapshot.storedTotes(), InboundToteManifest::serviceCentreId));
    }

    private static Av02Projection buildAv02Projection(Av02InventorySnapshot snapshot) {
        return new Av02Projection(countByServiceCentre(
                snapshot.waitingTotes(), Av02AllocatedTote::serviceCentreId));
    }

    private OutboundProjection buildOutboundProjection(
            OutboundAllocationSnapshot snapshot,
            P2pMissingPackSnapshot missingPackSnapshot) {
        Set<BagKey> allocatedBagKeys = new LinkedHashSet<>(snapshot.allocatedBagKeys());
        Map<BagKey, Set<String>> missingPackIdsByBagKey =
                missingPackSnapshot.missingPhysicalPackIdsByBagKey();
        Set<BagKey> pendingEmptyBagKeys = missingPackSnapshot.pendingEmptyBagKeys();
        Map<String, Integer> remainingPlannedBagCounts = new LinkedHashMap<>();
        Map<String, Integer> remainingPlannedPackCounts = new LinkedHashMap<>();
        Map<String, Integer> affectedAllocatedBagCounts = new LinkedHashMap<>();
        Map<String, Integer> pendingEmptyBagCounts = new LinkedHashMap<>();
        int classifiedMissingBagCount = 0;
        int classifiedPendingEmptyBagCount = 0;
        for (Map.Entry<String, List<PlannedBag>> entry : plannedBagsByServiceCentre.entrySet()) {
            int remainingBags = 0;
            int remainingPacks = 0;
            for (PlannedBag plannedBag : entry.getValue()) {
                BagKey bagKey = plannedBag.bagKey();
                Set<String> missingPackIds = missingPackIdsByBagKey.getOrDefault(bagKey, Set.of());
                if (!plannedBag.physicalPackIds().containsAll(missingPackIds)) {
                    throw new IllegalStateException(
                            "Missing exception packs are absent from the planned bag: " + bagKey);
                }
                if (!missingPackIds.isEmpty()) {
                    classifiedMissingBagCount++;
                }
                boolean pendingEmpty = pendingEmptyBagKeys.contains(bagKey);
                if (pendingEmpty) {
                    classifiedPendingEmptyBagCount++;
                    if (missingPackIds.size() != plannedBag.physicalPackIds().size()) {
                        throw new IllegalStateException(
                                "Pending empty exception bag is not fully missing: " + bagKey);
                    }
                    if (allocatedBagKeys.contains(bagKey)) {
                        throw new IllegalStateException(
                                "Pending empty exception bag must not be allocated: " + bagKey);
                    }
                    pendingEmptyBagCounts.merge(entry.getKey(), 1, Integer::sum);
                }
                if (allocatedBagKeys.contains(bagKey)) {
                    if (!missingPackIds.isEmpty()) {
                        affectedAllocatedBagCounts.merge(entry.getKey(), 1, Integer::sum);
                    }
                    continue;
                }
                if (!pendingEmpty) {
                    remainingBags++;
                    remainingPacks = Math.addExact(
                            remainingPacks,
                            plannedBag.physicalPackIds().size() - missingPackIds.size());
                }
            }
            remainingPlannedBagCounts.put(entry.getKey(), remainingBags);
            remainingPlannedPackCounts.put(entry.getKey(), remainingPacks);
        }
        if (classifiedMissingBagCount != missingPackIdsByBagKey.size()
                || classifiedPendingEmptyBagCount != pendingEmptyBagKeys.size()) {
            throw new IllegalStateException(
                    "Missing-pack snapshot contains bag keys absent from the bag plan");
        }

        Map<String, Integer> openOutboundCounts = new LinkedHashMap<>();
        Map<String, Integer> markedOutboundToteCounts = new LinkedHashMap<>();
        for (OutboundToteSnapshot tote : snapshot.openTotesByLine().values()) {
            tote.serviceCentreId().ifPresent(serviceCentreId ->
                    openOutboundCounts.merge(serviceCentreId, 1, Integer::sum));
            countMarkedExceptionTote(tote, markedOutboundToteCounts);
        }
        for (OutboundToteSnapshot tote : snapshot.closedTotes()) {
            countMarkedExceptionTote(tote, markedOutboundToteCounts);
        }
        return new OutboundProjection(
                allocatedBagKeys,
                remainingPlannedBagCounts,
                remainingPlannedPackCounts,
                openOutboundCounts,
                missingPackSnapshot.missingPackCountByServiceCentreId(),
                missingPackSnapshot.pdcCollectedPackCountByServiceCentreId(),
                affectedAllocatedBagCounts,
                markedOutboundToteCounts,
                pendingEmptyBagCounts);
    }

    private static void countMarkedExceptionTote(
            OutboundToteSnapshot tote,
            Map<String, Integer> counts) {
        if (tote.requiresExceptionProcessing()) {
            tote.serviceCentreId().ifPresent(serviceCentreId ->
                    counts.merge(serviceCentreId, 1, Integer::sum));
        }
    }

    private static <T> Map<String, Integer> countByServiceCentre(
            Iterable<T> values,
            java.util.function.Function<T, String> serviceCentreId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (T value : values) {
            counts.merge(serviceCentreId.apply(value), 1, Integer::sum);
        }
        return counts;
    }

    private static void requireSnapshot(Object snapshot, String fieldName) {
        if (snapshot == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private static List<String> immutableStrings(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static List<PhysicalToteId> immutablePhysicalToteIds(
            List<PhysicalToteId> values) {
        if (values == null) {
            throw new IllegalArgumentException("nonTerminalPhysicalToteIds must not be null");
        }
        List<PhysicalToteId> copy = new ArrayList<>();
        for (PhysicalToteId value : values) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "nonTerminalPhysicalToteIds must not contain null");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static Map<String, Integer> immutableCounts(
            Map<String, Integer> values,
            String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null");
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(fieldName + " must not contain negatives");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<BagKey> immutableBagKeys(Set<BagKey> values) {
        if (values == null) {
            throw new IllegalArgumentException("allocatedBagKeys must not be null");
        }
        Set<BagKey> copy = new LinkedHashSet<>();
        for (BagKey value : values) {
            if (value == null) {
                throw new IllegalArgumentException("allocatedBagKeys must not contain null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }

    record SupplyProjection(
            List<String> serviceCentreIds,
            Map<String, Integer> capacityBlockedCounts) {

        SupplyProjection {
            serviceCentreIds = immutableStrings(serviceCentreIds, "serviceCentreIds");
            capacityBlockedCounts = immutableCounts(
                    capacityBlockedCounts, "capacityBlockedCounts");
        }
    }

    record LifecycleProjection(
            Map<String, Integer> nonTerminalInboundCounts,
            List<PhysicalToteId> nonTerminalPhysicalToteIds) {

        LifecycleProjection {
            nonTerminalInboundCounts = immutableCounts(
                    nonTerminalInboundCounts, "nonTerminalInboundCounts");
            nonTerminalPhysicalToteIds = immutablePhysicalToteIds(nonTerminalPhysicalToteIds);
        }
    }

    record OsrProjection(Map<String, Integer> waitingCounts) {

        OsrProjection {
            waitingCounts = immutableCounts(waitingCounts, "waitingCounts");
        }
    }

    record Av02Projection(Map<String, Integer> waitingCounts) {

        Av02Projection {
            waitingCounts = immutableCounts(waitingCounts, "waitingCounts");
        }
    }

    record OutboundProjection(
            Set<BagKey> allocatedBagKeys,
            Map<String, Integer> remainingPlannedBagCounts,
            Map<String, Integer> remainingPlannedPackCounts,
            Map<String, Integer> openOutboundCounts,
            Map<String, Integer> missingPackCounts,
            Map<String, Integer> pdcCollectedPackCounts,
            Map<String, Integer> affectedAllocatedBagCounts,
            Map<String, Integer> markedOutboundToteCounts,
            Map<String, Integer> pendingEmptyBagCounts) {

        OutboundProjection {
            allocatedBagKeys = immutableBagKeys(allocatedBagKeys);
            remainingPlannedBagCounts = immutableCounts(
                    remainingPlannedBagCounts, "remainingPlannedBagCounts");
            remainingPlannedPackCounts = immutableCounts(
                    remainingPlannedPackCounts, "remainingPlannedPackCounts");
            openOutboundCounts = immutableCounts(openOutboundCounts, "openOutboundCounts");
            missingPackCounts = immutableCounts(missingPackCounts, "missingPackCounts");
            pdcCollectedPackCounts = immutableCounts(
                    pdcCollectedPackCounts, "pdcCollectedPackCounts");
            affectedAllocatedBagCounts = immutableCounts(
                    affectedAllocatedBagCounts, "affectedAllocatedBagCounts");
            markedOutboundToteCounts = immutableCounts(
                    markedOutboundToteCounts, "markedOutboundToteCounts");
            pendingEmptyBagCounts = immutableCounts(
                    pendingEmptyBagCounts, "pendingEmptyBagCounts");
        }
    }
}
