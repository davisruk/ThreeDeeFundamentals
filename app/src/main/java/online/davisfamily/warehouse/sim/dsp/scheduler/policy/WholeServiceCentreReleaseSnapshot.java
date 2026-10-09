package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

/**
 * Immutable release and outstanding-P2P accounting. Release completion remains
 * independent of subsequent machine/output completion; outstanding inbound totes
 * end only when actual tipper completion is recorded.
 */
public record WholeServiceCentreReleaseSnapshot(
        long version,
        List<String> orderedServiceCentreIds,
        Optional<String> releaseServiceCentreId,
        Map<String, Integer> unreleasedOsrToteCounts,
        Map<String, Integer> unreleasedEmptySheetCounts,
        Map<String, Map<P2pLineId, Integer>> committedP2pToteCounts,
        long outstandingVersion,
        int p2pOutstandingToteWatermark,
        Map<P2pLineId, Integer> outstandingP2pToteCounts) {

    /** Compatibility constructor for callers that only publish release accounting. */
    public WholeServiceCentreReleaseSnapshot(
            long version,
            List<String> orderedServiceCentreIds,
            Optional<String> releaseServiceCentreId,
            Map<String, Integer> unreleasedOsrToteCounts,
            Map<String, Integer> unreleasedEmptySheetCounts,
            Map<String, Map<P2pLineId, Integer>> committedP2pToteCounts) {
        this(version, orderedServiceCentreIds, releaseServiceCentreId,
                unreleasedOsrToteCounts, unreleasedEmptySheetCounts, committedP2pToteCounts,
                0, Integer.MAX_VALUE, zeroCountsFrom(committedP2pToteCounts));
    }

    public WholeServiceCentreReleaseSnapshot {
        if (version < 0 || outstandingVersion < 0 || p2pOutstandingToteWatermark < 1
                || orderedServiceCentreIds == null || releaseServiceCentreId == null
                || unreleasedOsrToteCounts == null || unreleasedEmptySheetCounts == null
                || committedP2pToteCounts == null || outstandingP2pToteCounts == null) {
            throw new IllegalArgumentException(
                    "release snapshot values must be nonnull, versions nonnegative and watermark positive");
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
        outstandingP2pToteCounts = copyOutstandingCounts(
                configuredLines, outstandingP2pToteCounts, p2pOutstandingToteWatermark);
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

    /** O(1) outstanding inbound P2P tote count for a configured line. */
    public int outstandingToteCount(P2pLineId lineId) {
        Integer count = outstandingP2pToteCounts.get(lineId);
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

    private static Map<P2pLineId, Integer> copyOutstandingCounts(
            Set<P2pLineId> configuredLines,
            Map<P2pLineId, Integer> source,
            int watermark) {
        if (configuredLines != null && !source.keySet().equals(configuredLines)) {
            throw new IllegalArgumentException("outstanding counts must contain exactly the configured P2P lines");
        }
        Map<P2pLineId, Integer> copy = new LinkedHashMap<>();
        Iterable<P2pLineId> lineIds = configuredLines == null ? source.keySet() : configuredLines;
        for (P2pLineId lineId : lineIds) {
            Integer count = source.get(lineId);
            if (lineId == null || count == null || count < 0 || count > watermark
                    || (configuredLines == null && count != 0)) {
                throw new IllegalArgumentException(
                        "outstanding identities/counts must be valid and within the watermark");
            }
            copy.put(lineId, count);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<P2pLineId, Integer> zeroCountsFrom(
            Map<String, Map<P2pLineId, Integer>> committedCounts) {
        if (committedCounts == null || committedCounts.isEmpty()) {
            return Map.of();
        }
        Map<P2pLineId, Integer> zeros = new LinkedHashMap<>();
        Map<P2pLineId, Integer> firstCentreCounts = committedCounts.values().iterator().next();
        if (firstCentreCounts != null) {
            firstCentreCounts.keySet().forEach(lineId -> zeros.put(lineId, 0));
        }
        return zeros;
    }
}
