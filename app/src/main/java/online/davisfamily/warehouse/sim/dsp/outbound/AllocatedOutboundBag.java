package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record AllocatedOutboundBag(
        PlannedBag plannedBag,
        PhysicalToteId outboundPhysicalToteId,
        List<OutputSheetAllocation> outputSheetAllocations,
        List<String> actualPhysicalPackIds,
        List<String> missingPhysicalPackIds) {

    public AllocatedOutboundBag(
            PlannedBag plannedBag,
            PhysicalToteId outboundPhysicalToteId,
            List<OutputSheetAllocation> outputSheetAllocations) {
        this(plannedBag, outboundPhysicalToteId, outputSheetAllocations,
                allPlannedPackIds(plannedBag), List.of());
    }

    public AllocatedOutboundBag {
        if (plannedBag == null) {
            throw new IllegalArgumentException("plannedBag must not be null");
        }
        if (outboundPhysicalToteId == null) {
            throw new IllegalArgumentException("outboundPhysicalToteId must not be null");
        }
        if (outputSheetAllocations == null) {
            throw new IllegalArgumentException("outputSheetAllocations must not be null");
        }
        if (actualPhysicalPackIds == null) {
            throw new IllegalArgumentException("actualPhysicalPackIds must not be null");
        }
        if (missingPhysicalPackIds == null) {
            throw new IllegalArgumentException("missingPhysicalPackIds must not be null");
        }
        if (actualPhysicalPackIds.isEmpty()) {
            throw new IllegalArgumentException("actualPhysicalPackIds must not be empty");
        }
        validatePackPartition(
                plannedBag.physicalPackIds(), actualPhysicalPackIds, missingPhysicalPackIds);
        if (missingPhysicalPackIds.isEmpty()
                && actualPhysicalPackIds == plannedBag.physicalPackIds()) {
            actualPhysicalPackIds = plannedBag.physicalPackIds();
        } else {
            actualPhysicalPackIds = List.copyOf(actualPhysicalPackIds);
        }
        missingPhysicalPackIds = List.copyOf(missingPhysicalPackIds);
        outputSheetAllocations = List.copyOf(outputSheetAllocations);
        if (outputSheetAllocations.stream().anyMatch(allocation -> allocation == null)) {
            throw new IllegalArgumentException("outputSheetAllocations must not contain null");
        }

        List<OrderSheetKey> allocatedSourceKeys = outputSheetAllocations.stream()
                .map(OutputSheetAllocation::sourceOwningSheetKey)
                .toList();
        if (!allocatedSourceKeys.equals(plannedBag.owningOrderSheetKeys())) {
            throw new IllegalArgumentException(
                    "outputSheetAllocations must cover planned bag owning sheets in order");
        }
        requireDistinct(allocatedSourceKeys, "source owning sheet");
        requireDistinct(
                outputSheetAllocations.stream().map(OutputSheetAllocation::outputSheetKey).toList(),
                "output sheet");
    }

    public BagKey bagKey() {
        return plannedBag.bagKey();
    }

    private static List<String> allPlannedPackIds(PlannedBag plannedBag) {
        if (plannedBag == null) {
            throw new IllegalArgumentException("plannedBag must not be null");
        }
        return plannedBag.physicalPackIds();
    }

    private static void validatePackPartition(
            List<String> plannedPackIds,
            List<String> actualPackIds,
            List<String> missingPackIds) {
        Iterator<String> actualIterator = actualPackIds.iterator();
        Iterator<String> missingIterator = missingPackIds.iterator();
        boolean hasActual = actualIterator.hasNext();
        boolean hasMissing = missingIterator.hasNext();
        String nextActual = hasActual ? actualIterator.next() : null;
        String nextMissing = hasMissing ? missingIterator.next() : null;
        int actualCursor = 0;
        int missingCursor = 0;

        for (String plannedPackId : plannedPackIds) {
            if (hasActual && plannedPackId.equals(nextActual)) {
                actualCursor++;
                hasActual = actualIterator.hasNext();
                nextActual = hasActual ? actualIterator.next() : null;
            } else if (hasMissing && plannedPackId.equals(nextMissing)) {
                missingCursor++;
                hasMissing = missingIterator.hasNext();
                nextMissing = hasMissing ? missingIterator.next() : null;
            } else {
                throw new IllegalArgumentException(
                        "actualPhysicalPackIds and missingPhysicalPackIds must partition planned IDs in order");
            }
        }

        if (actualCursor != actualPackIds.size() || missingCursor != missingPackIds.size()) {
            throw new IllegalArgumentException(
                    "actualPhysicalPackIds and missingPhysicalPackIds must partition planned IDs in order");
        }
    }

    private static void requireDistinct(List<OrderSheetKey> keys, String identityName) {
        Set<OrderSheetKey> distinctKeys = new LinkedHashSet<>(keys);
        if (distinctKeys.size() != keys.size()) {
            throw new IllegalArgumentException("Duplicate " + identityName + " identity");
        }
    }
}
