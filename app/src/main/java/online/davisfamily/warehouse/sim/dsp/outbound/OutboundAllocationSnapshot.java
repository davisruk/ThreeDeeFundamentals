package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OutboundAllocationSnapshot(
        Map<P2pLineId, OutboundToteSnapshot> openTotesByLine,
        List<OutboundToteSnapshot> closedTotes,
        List<AllocatedOutboundBag> allocatedBags) {

    public OutboundAllocationSnapshot {
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
        openTotesByLine = Collections.unmodifiableMap(openCopy);
        closedTotes = copyAndRejectNull(closedTotes, "closedTotes");
        allocatedBags = copyAndRejectNull(allocatedBags, "allocatedBags");
        if (closedTotes.stream().anyMatch(OutboundToteSnapshot::open)) {
            throw new IllegalArgumentException("closedTotes must contain only closed totes");
        }

        Map<PhysicalToteId, OutboundToteSnapshot> totesById = new LinkedHashMap<>();
        openTotesByLine.values().forEach(tote -> putUniqueTote(totesById, tote));
        closedTotes.forEach(tote -> putUniqueTote(totesById, tote));

        Map<BagKey, AllocatedOutboundBag> historyByBagKey = uniqueBags(allocatedBags, "allocatedBags");
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
        Optional<OutboundToteSnapshot> openTote = openTotesByLine.values().stream()
                .filter(tote -> tote.physicalToteId().equals(physicalToteId))
                .findFirst();
        return openTote.isPresent()
                ? openTote
                : closedTotes.stream()
                        .filter(tote -> tote.physicalToteId().equals(physicalToteId))
                        .findFirst();
    }

    public Optional<AllocatedOutboundBag> findAllocatedBag(BagKey bagKey) {
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        return allocatedBags.stream().filter(bag -> bag.bagKey().equals(bagKey)).findFirst();
    }

    private static void putUniqueTote(
            Map<PhysicalToteId, OutboundToteSnapshot> totesById,
            OutboundToteSnapshot tote) {
        if (totesById.putIfAbsent(tote.physicalToteId(), tote) != null) {
            throw new IllegalArgumentException("Duplicate outbound physical tote ID: " + tote.physicalToteId());
        }
    }

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
