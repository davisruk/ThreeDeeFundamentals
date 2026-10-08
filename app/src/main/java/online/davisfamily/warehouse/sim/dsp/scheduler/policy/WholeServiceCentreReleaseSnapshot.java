package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

/** Immutable release accounting, independent of subsequent machine/output completion. */
public record WholeServiceCentreReleaseSnapshot(
        long version,
        List<String> orderedServiceCentreIds,
        Optional<String> releaseServiceCentreId,
        Map<String, Integer> unreleasedOsrToteCounts,
        Map<String, Integer> unreleasedEmptySheetCounts,
        Map<String, Map<P2pLineId, Integer>> committedP2pToteCounts) {

    public WholeServiceCentreReleaseSnapshot {
        if (version < 0 || orderedServiceCentreIds == null || releaseServiceCentreId == null
                || unreleasedOsrToteCounts == null || unreleasedEmptySheetCounts == null
                || committedP2pToteCounts == null) {
            throw new IllegalArgumentException("release snapshot values must be nonnull and version nonnegative");
        }
        Set<String> centres = new LinkedHashSet<>();
        for (String centre : orderedServiceCentreIds) {
            if (centre == null || centre.isBlank() || !centres.add(centre)) {
                throw new IllegalArgumentException("ordered service-centre IDs must be nonblank and distinct");
            }
        }
        orderedServiceCentreIds = List.copyOf(orderedServiceCentreIds);
        unreleasedOsrToteCounts = copyCounts(centres, unreleasedOsrToteCounts);
        unreleasedEmptySheetCounts = copyCounts(centres, unreleasedEmptySheetCounts);
        if (!committedP2pToteCounts.keySet().equals(centres)) {
            throw new IllegalArgumentException("committed counts must contain exactly the ordered centres");
        }
        Map<String, Map<P2pLineId, Integer>> committed = new LinkedHashMap<>();
        Set<P2pLineId> configuredLines = null;
        Optional<String> firstUnreleased = Optional.empty();
        for (String centre : orderedServiceCentreIds) {
            Map<P2pLineId, Integer> counts = committedP2pToteCounts.get(centre);
            if (counts == null || counts.isEmpty()) {
                throw new IllegalArgumentException("each centre must have configured P2P line counts");
            }
            if (configuredLines == null) {
                configuredLines = new LinkedHashSet<>(counts.keySet());
            }
            committed.put(centre, copyCounts(configuredLines, counts));
            if (firstUnreleased.isEmpty()
                    && (unreleasedOsrToteCounts.get(centre) > 0
                            || unreleasedEmptySheetCounts.get(centre) > 0)) {
                firstUnreleased = Optional.of(centre);
            }
        }
        if (!releaseServiceCentreId.equals(firstUnreleased)) {
            throw new IllegalArgumentException("release centre must be the first centre with unreleased work");
        }
        committedP2pToteCounts = Collections.unmodifiableMap(committed);
    }

    public boolean allReleased(String serviceCentreId) {
        requireCentre(serviceCentreId);
        return unreleasedOsrToteCounts.get(serviceCentreId) == 0
                && unreleasedEmptySheetCounts.get(serviceCentreId) == 0;
    }

    public int committedToteCount(String serviceCentreId, P2pLineId lineId) {
        requireCentre(serviceCentreId);
        Integer count = committedP2pToteCounts.get(serviceCentreId).get(lineId);
        if (count == null) {
            throw new IllegalArgumentException("Unknown P2P line ID: " + lineId);
        }
        return count;
    }

    private void requireCentre(String serviceCentreId) {
        if (serviceCentreId == null || !unreleasedOsrToteCounts.containsKey(serviceCentreId)) {
            throw new IllegalArgumentException("Unknown service-centre ID: " + serviceCentreId);
        }
    }

    private static <K> Map<K, Integer> copyCounts(Set<K> keys, Map<K, Integer> source) {
        if (!source.keySet().equals(keys)) {
            throw new IllegalArgumentException("count maps must have matching configured identities");
        }
        Map<K, Integer> copy = new LinkedHashMap<>();
        for (K key : keys) {
            Integer count = source.get(key);
            if (key == null || count == null || count < 0) {
                throw new IllegalArgumentException("count identities/values must be nonnull and nonnegative");
            }
            copy.put(key, count);
        }
        return Collections.unmodifiableMap(copy);
    }
}
