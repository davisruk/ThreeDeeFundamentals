package online.davisfamily.warehouse.sim.dsp.outbound;

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
        List<OutputSheetAllocation> outputSheetAllocations) {

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

    private static void requireDistinct(List<OrderSheetKey> keys, String identityName) {
        Set<OrderSheetKey> distinctKeys = new LinkedHashSet<>(keys);
        if (distinctKeys.size() != keys.size()) {
            throw new IllegalArgumentException("Duplicate " + identityName + " identity");
        }
    }
}
