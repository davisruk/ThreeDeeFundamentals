package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/** Immutable exception and first-COLLECT publication for worker-side evaluation. */
public final class P2pMissingPackSnapshot {
    private final long version;
    private final Map<BagKey, Set<String>> missingPhysicalPackIdsByBagKey;
    private final Set<BagKey> pendingEmptyBagKeys;
    private final Map<String, Integer> missingPackCountByServiceCentreId;
    private final Map<String, Integer> pdcCollectedPackCountByServiceCentreId;
    private final Map<String, OrderSheetKey> firstCollectedSheetByOrderId;

    private static final P2pMissingPackSnapshot EMPTY = new P2pMissingPackSnapshot(
            0, Map.of(), Set.of(), Map.of(), Map.of(), Map.of());

    public P2pMissingPackSnapshot(
            long version,
            Map<BagKey, Set<String>> missingPhysicalPackIdsByBagKey,
            Set<BagKey> pendingEmptyBagKeys,
            Map<String, Integer> missingPackCountByServiceCentreId,
            Map<String, Integer> pdcCollectedPackCountByServiceCentreId,
            Map<String, OrderSheetKey> firstCollectedSheetByOrderId) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be nonnegative");
        }
        if (missingPhysicalPackIdsByBagKey == null || pendingEmptyBagKeys == null
                || missingPackCountByServiceCentreId == null
                || pdcCollectedPackCountByServiceCentreId == null
                || firstCollectedSheetByOrderId == null) {
            throw new IllegalArgumentException("Snapshot collections must not be null");
        }
        Map<BagKey, Set<String>> missingCopy = new LinkedHashMap<>();
        Set<String> allPackIds = new LinkedHashSet<>();
        for (var entry : missingPhysicalPackIdsByBagKey.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("Missing-pack entry must have a bag and pack IDs");
            }
            Set<String> ids = new LinkedHashSet<>();
            for (String id : entry.getValue()) {
                requireId(id, "physicalPackId");
                if (!ids.add(id) || !allPackIds.add(id)) {
                    throw new IllegalArgumentException("Duplicate missing physical pack ID: " + id);
                }
            }
            missingCopy.put(entry.getKey(), Collections.unmodifiableSet(ids));
        }
        this.version = version;
        this.missingPhysicalPackIdsByBagKey = Collections.unmodifiableMap(missingCopy);
        Set<BagKey> pendingCopy = new LinkedHashSet<>();
        for (BagKey key : pendingEmptyBagKeys) {
            if (key == null || !missingCopy.containsKey(key)) {
                throw new IllegalArgumentException("Pending empty bag must have missing packs: " + key);
            }
            pendingCopy.add(key);
        }
        this.pendingEmptyBagKeys = Collections.unmodifiableSet(pendingCopy);
        this.missingPackCountByServiceCentreId = copyCounts(missingPackCountByServiceCentreId);
        this.pdcCollectedPackCountByServiceCentreId = copyCounts(pdcCollectedPackCountByServiceCentreId);
        Map<String, OrderSheetKey> firstCopy = new LinkedHashMap<>();
        for (var entry : firstCollectedSheetByOrderId.entrySet()) {
            requireId(entry.getKey(), "orderId");
            if (entry.getValue() == null || !entry.getKey().equals(entry.getValue().orderId())) {
                throw new IllegalArgumentException("First COLLECT sheet must match its order ID");
            }
            firstCopy.put(entry.getKey(), entry.getValue());
        }
        this.firstCollectedSheetByOrderId = Collections.unmodifiableMap(firstCopy);
    }

    /** Trusted path: previous owns only validated immutable collections, counts is freshly frozen. */
    private P2pMissingPackSnapshot(P2pMissingPackSnapshot previous, long version,
            Map<String, Integer> pdcCounts) {
        this.version = version;
        missingPhysicalPackIdsByBagKey = previous.missingPhysicalPackIdsByBagKey;
        pendingEmptyBagKeys = previous.pendingEmptyBagKeys;
        missingPackCountByServiceCentreId = previous.missingPackCountByServiceCentreId;
        firstCollectedSheetByOrderId = previous.firstCollectedSheetByOrderId;
        pdcCollectedPackCountByServiceCentreId = pdcCounts;
    }

    public P2pMissingPackSnapshot withPdcCollectedPack(String serviceCentreId) {
        requireId(serviceCentreId, "serviceCentreId");
        Map<String, Integer> counts = new LinkedHashMap<>(pdcCollectedPackCountByServiceCentreId);
        counts.put(serviceCentreId, Math.addExact(counts.getOrDefault(serviceCentreId, 0), 1));
        return new P2pMissingPackSnapshot(this, Math.addExact(version, 1),
                Collections.unmodifiableMap(counts));
    }

    public long version() { return version; }
    public Map<BagKey, Set<String>> missingPhysicalPackIdsByBagKey() { return missingPhysicalPackIdsByBagKey; }
    public Set<BagKey> pendingEmptyBagKeys() { return pendingEmptyBagKeys; }
    public Map<String, Integer> missingPackCountByServiceCentreId() { return missingPackCountByServiceCentreId; }
    public Map<String, Integer> pdcCollectedPackCountByServiceCentreId() { return pdcCollectedPackCountByServiceCentreId; }
    public Map<String, OrderSheetKey> firstCollectedSheetByOrderId() { return firstCollectedSheetByOrderId; }

    @Override
    public boolean equals(Object object) {
        if (object == this) { return true; }
        if (!(object instanceof P2pMissingPackSnapshot other)) { return false; }
        return version == other.version
                && missingPhysicalPackIdsByBagKey.equals(other.missingPhysicalPackIdsByBagKey)
                && pendingEmptyBagKeys.equals(other.pendingEmptyBagKeys)
                && missingPackCountByServiceCentreId.equals(other.missingPackCountByServiceCentreId)
                && pdcCollectedPackCountByServiceCentreId.equals(other.pdcCollectedPackCountByServiceCentreId)
                && firstCollectedSheetByOrderId.equals(other.firstCollectedSheetByOrderId);
    }

    @Override
    public int hashCode() {
        int hash = Long.hashCode(version);
        hash = 31 * hash + missingPhysicalPackIdsByBagKey.hashCode();
        hash = 31 * hash + pendingEmptyBagKeys.hashCode();
        hash = 31 * hash + missingPackCountByServiceCentreId.hashCode();
        hash = 31 * hash + pdcCollectedPackCountByServiceCentreId.hashCode();
        return 31 * hash + firstCollectedSheetByOrderId.hashCode();
    }

    @Override
    public String toString() {
        return "P2pMissingPackSnapshot[version=" + version
                + ", missingPhysicalPackIdsByBagKey=" + missingPhysicalPackIdsByBagKey
                + ", pendingEmptyBagKeys=" + pendingEmptyBagKeys
                + ", missingPackCountByServiceCentreId=" + missingPackCountByServiceCentreId
                + ", pdcCollectedPackCountByServiceCentreId=" + pdcCollectedPackCountByServiceCentreId
                + ", firstCollectedSheetByOrderId=" + firstCollectedSheetByOrderId + "]";
    }

    public static P2pMissingPackSnapshot empty() {
        return EMPTY;
    }

    private static Map<String, Integer> copyCounts(Map<String, Integer> source) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            requireId(entry.getKey(), "serviceCentreId");
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Service-centre count must be nonnegative");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " must be a nonblank trimmed ID");
        }
    }
}
