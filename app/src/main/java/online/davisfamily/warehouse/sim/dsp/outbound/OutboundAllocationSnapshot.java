package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OutboundAllocationSnapshot {
    private final Map<P2pLineId, OutboundToteSnapshot> openTotesByLine;
    private final List<OutboundToteSnapshot> closedTotes;
    private final List<AllocatedOutboundBag> allocatedBags;
    private final Map<PhysicalToteId, OutboundToteSnapshot> totesById;
    private final Map<BagKey, AllocatedOutboundBag> allocatedBagsByKey;
    private final long allocatedBagCount;
    private final Map<P2pLineId, Long> allocatedBagCountsByLine;
    private final Map<P2pLineId, Long> closedToteCountsByLine;
    private final Map<String, Long> closedToteCountsByServiceCentre;
    private final Map<String, Long> markedClosedToteCountsByServiceCentre;
    private final Map<OutboundToteClosureReason, Long> closureCountsByReason;

    public OutboundAllocationSnapshot(
            Map<P2pLineId, OutboundToteSnapshot> openTotesByLine,
            List<OutboundToteSnapshot> closedTotes,
            List<AllocatedOutboundBag> allocatedBags) {
        this(openTotesByLine, closedTotes, allocatedBags, fixtureCounts(openTotesByLine, closedTotes, allocatedBags));
    }

    private OutboundAllocationSnapshot(
            Map<P2pLineId, OutboundToteSnapshot> openTotesByLine,
            List<OutboundToteSnapshot> closedTotes,
            List<AllocatedOutboundBag> allocatedBags,
            FixtureCounts counts) {
        this(openTotesByLine, closedTotes, allocatedBags, allocatedBags.size(), counts.allocatedByLine,
                counts.closedByLine, counts.closedByCentre, counts.markedByCentre, counts.closedByReason);
    }

    public OutboundAllocationSnapshot(
            Map<P2pLineId, OutboundToteSnapshot> openTotesByLine,
            List<OutboundToteSnapshot> closedTotes,
            List<AllocatedOutboundBag> allocatedBags,
            long allocatedBagCount,
            Map<P2pLineId, Long> allocatedBagCountsByLine,
            Map<P2pLineId, Long> closedToteCountsByLine,
            Map<String, Long> closedToteCountsByServiceCentre,
            Map<String, Long> markedClosedToteCountsByServiceCentre,
            Map<OutboundToteClosureReason, Long> closureCountsByReason) {
        if (openTotesByLine == null) {
            throw new IllegalArgumentException("openTotesByLine must not be null");
        }
        if (closedTotes == null) {
            throw new IllegalArgumentException("closedTotes must not be null");
        }
        if (allocatedBags == null) {
            throw new IllegalArgumentException("allocatedBags must not be null");
        }

        LinkedHashMap<P2pLineId, OutboundToteSnapshot> openCopy = new LinkedHashMap<>();
        for (Map.Entry<P2pLineId, OutboundToteSnapshot> entry : openTotesByLine.entrySet()) {
            P2pLineId lineId = entry.getKey();
            OutboundToteSnapshot tote = entry.getValue();
            if (lineId == null || tote == null) {
                throw new IllegalArgumentException("openTotesByLine must not contain null");
            }
            if (!lineId.equals(tote.p2pLineId())) {
                throw new IllegalArgumentException("open tote map key must match tote P2P line");
            }
            if (!tote.open()) {
                throw new IllegalArgumentException("openTotesByLine must contain only open totes");
            }
            openCopy.put(lineId, tote);
        }
        this.openTotesByLine = Collections.unmodifiableMap(openCopy);
        this.closedTotes = copyAndRejectNull(closedTotes, "closedTotes");
        this.allocatedBags = copyAndRejectNull(allocatedBags, "allocatedBags");
        if (this.closedTotes.stream().anyMatch(OutboundToteSnapshot::open)) {
            throw new IllegalArgumentException("closedTotes must contain only closed totes");
        }

        Map<PhysicalToteId, OutboundToteSnapshot> totesById = new LinkedHashMap<>();
        this.openTotesByLine.values().forEach(tote -> putUniqueTote(totesById, tote));
        this.closedTotes.forEach(tote -> putUniqueTote(totesById, tote));

        Map<BagKey, AllocatedOutboundBag> historyByBagKey = uniqueBags(
                this.allocatedBags, "allocatedBags");
        Map<BagKey, AllocatedOutboundBag> toteContentsByBagKey = new LinkedHashMap<>();
        for (OutboundToteSnapshot tote : totesById.values()) {
            for (AllocatedOutboundBag bag : tote.allocatedBags()) {
                if (toteContentsByBagKey.putIfAbsent(bag.bagKey(), bag) != null) {
                    throw new IllegalArgumentException("Bag appears in more than one outbound tote: " + bag.bagKey());
                }
            }
        }
        if (!historyByBagKey.equals(toteContentsByBagKey)) {
            throw new IllegalArgumentException("allocatedBags must match outbound tote contents");
        }
        this.totesById = Collections.unmodifiableMap(totesById);
        this.allocatedBagsByKey = Collections.unmodifiableMap(historyByBagKey);
        if (allocatedBagCount < this.allocatedBags.size()) {
            throw new IllegalArgumentException("allocatedBagCount must cover retained allocations");
        }
        this.allocatedBagCount = allocatedBagCount;
        this.allocatedBagCountsByLine = immutableCounts(allocatedBagCountsByLine);
        this.closedToteCountsByLine = immutableCounts(closedToteCountsByLine);
        this.closedToteCountsByServiceCentre = immutableCounts(closedToteCountsByServiceCentre);
        this.markedClosedToteCountsByServiceCentre = immutableCounts(markedClosedToteCountsByServiceCentre);
        this.closureCountsByReason = immutableCounts(closureCountsByReason);
        long closedCount = sum(this.closedToteCountsByLine);
        if (sum(this.allocatedBagCountsByLine) != allocatedBagCount
                || closedCount < this.closedTotes.size()
                || sum(this.closureCountsByReason) != closedCount
                || sum(this.closedToteCountsByServiceCentre) > closedCount) {
            throw new IllegalArgumentException("Outbound counters must have consistent totals");
        }
        for (Map.Entry<String, Long> entry : this.markedClosedToteCountsByServiceCentre.entrySet()) {
            if (entry.getValue() > this.closedToteCountsByServiceCentre.getOrDefault(entry.getKey(), 0L)) {
                throw new IllegalArgumentException("Marked closed count must not exceed centre closed count");
            }
        }
    }

    public Map<P2pLineId, OutboundToteSnapshot> openTotesByLine() {
        return openTotesByLine;
    }

    public List<OutboundToteSnapshot> closedTotes() {
        return closedTotes;
    }

    public List<AllocatedOutboundBag> allocatedBags() {
        return allocatedBags;
    }

    public long allocatedBagCount() {
        return allocatedBagCount;
    }

    public Map<P2pLineId, Long> allocatedBagCountsByLine() {
        return allocatedBagCountsByLine;
    }

    public Map<P2pLineId, Long> closedToteCountsByLine() {
        return closedToteCountsByLine;
    }

    public Map<String, Long> closedToteCountsByServiceCentre() {
        return closedToteCountsByServiceCentre;
    }

    public Map<String, Long> markedClosedToteCountsByServiceCentre() {
        return markedClosedToteCountsByServiceCentre;
    }

    public Map<OutboundToteClosureReason, Long> closureCountsByReason() {
        return closureCountsByReason;
    }

    public Optional<OutboundToteSnapshot> openToteFor(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        return Optional.ofNullable(openTotesByLine.get(lineId));
    }

    public Optional<OutboundToteSnapshot> findTote(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return Optional.ofNullable(totesById.get(physicalToteId));
    }

    public Optional<AllocatedOutboundBag> findAllocatedBag(BagKey bagKey) {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        return Optional.ofNullable(allocatedBagsByKey.get(bagKey));
    }

    public Set<BagKey> allocatedBagKeys() {
        return allocatedBagsByKey.keySet();
    }

    public Optional<String> ownerFor(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        OutboundToteSnapshot tote = totesById.get(physicalToteId);
        return tote == null ? Optional.empty() : tote.serviceCentreId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OutboundAllocationSnapshot that)) {
            return false;
        }
        return openTotesByLine.equals(that.openTotesByLine)
                && closedTotes.equals(that.closedTotes)
                && allocatedBags.equals(that.allocatedBags)
                && allocatedBagCount == that.allocatedBagCount
                && allocatedBagCountsByLine.equals(that.allocatedBagCountsByLine)
                && closedToteCountsByLine.equals(that.closedToteCountsByLine)
                && closedToteCountsByServiceCentre.equals(that.closedToteCountsByServiceCentre)
                && markedClosedToteCountsByServiceCentre.equals(that.markedClosedToteCountsByServiceCentre)
                && closureCountsByReason.equals(that.closureCountsByReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openTotesByLine, closedTotes, allocatedBags, allocatedBagCount,
                allocatedBagCountsByLine, closedToteCountsByLine, closedToteCountsByServiceCentre,
                markedClosedToteCountsByServiceCentre, closureCountsByReason);
    }

    @Override
    public String toString() {
        return "OutboundAllocationSnapshot[openTotesByLine=" + openTotesByLine
                + ", closedTotes=" + closedTotes
                + ", allocatedBags=" + allocatedBags + "]";
    }

    private static void putUniqueTote(
            Map<PhysicalToteId, OutboundToteSnapshot> totesById,
            OutboundToteSnapshot tote) {
        if (totesById.putIfAbsent(tote.physicalToteId(), tote) != null) {
            throw new IllegalArgumentException("Duplicate outbound physical tote ID: " + tote.physicalToteId());
        }
    }

    private static <K> Map<K, Long> immutableCounts(Map<K, Long> values) {
        if (values == null) {
            throw new IllegalArgumentException("Counter maps must not be null");
        }
        Map<K, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Counter maps require non-null keys and nonnegative counts");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static long sum(Map<?, Long> counts) {
        long total = 0;
        for (long count : counts.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }

    // Compatibility fixtures derive initial scalar facts once; production supplies its own counters.
    private static FixtureCounts fixtureCounts(
            Map<P2pLineId, OutboundToteSnapshot> openTotes,
            List<OutboundToteSnapshot> closedTotes,
            List<AllocatedOutboundBag> allocatedBags) {
        if (openTotes == null || closedTotes == null || allocatedBags == null) {
            throw new IllegalArgumentException("Outbound snapshot inputs must not be null");
        }
        Map<P2pLineId, Long> allocatedByLine = new LinkedHashMap<>();
        Map<P2pLineId, Long> closedByLine = new LinkedHashMap<>();
        Map<String, Long> closedByCentre = new LinkedHashMap<>();
        Map<String, Long> markedByCentre = new LinkedHashMap<>();
        Map<OutboundToteClosureReason, Long> closedByReason = new LinkedHashMap<>();
        for (OutboundToteSnapshot tote : openTotes.values()) {
            if (tote == null) {
                throw new IllegalArgumentException("openTotesByLine must not contain null");
            }
            if (tote.bagCount() > 0) {
                allocatedByLine.merge(tote.p2pLineId(), (long) tote.bagCount(), Math::addExact);
            }
        }
        for (OutboundToteSnapshot tote : closedTotes) {
            if (tote == null || tote.open()) {
                throw new IllegalArgumentException("closedTotes must contain only closed totes");
            }
            if (tote.bagCount() > 0) {
                allocatedByLine.merge(tote.p2pLineId(), (long) tote.bagCount(), Math::addExact);
            }
            closedByLine.merge(tote.p2pLineId(), 1L, Math::addExact);
            closedByReason.merge(tote.closureReason().orElseThrow(), 1L, Math::addExact);
            tote.serviceCentreId().ifPresent(centre -> {
                closedByCentre.merge(centre, 1L, Math::addExact);
                if (tote.requiresExceptionProcessing()) {
                    markedByCentre.merge(centre, 1L, Math::addExact);
                }
            });
        }
        return new FixtureCounts(allocatedByLine, closedByLine, closedByCentre, markedByCentre, closedByReason);
    }

    private record FixtureCounts(Map<P2pLineId, Long> allocatedByLine, Map<P2pLineId, Long> closedByLine,
            Map<String, Long> closedByCentre, Map<String, Long> markedByCentre,
            Map<OutboundToteClosureReason, Long> closedByReason) { }

    private static Map<BagKey, AllocatedOutboundBag> uniqueBags(
            List<AllocatedOutboundBag> bags,
            String fieldName) {
        Map<BagKey, AllocatedOutboundBag> bagsByKey = new LinkedHashMap<>();
        for (AllocatedOutboundBag bag : bags) {
            if (bagsByKey.putIfAbsent(bag.bagKey(), bag) != null) {
                throw new IllegalArgumentException("Duplicate bag key in " + fieldName + ": " + bag.bagKey());
            }
        }
        return bagsByKey;
    }

    private static <T> List<T> copyAndRejectNull(List<T> values, String fieldName) {
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null");
        }
        return List.copyOf(values);
    }
}
