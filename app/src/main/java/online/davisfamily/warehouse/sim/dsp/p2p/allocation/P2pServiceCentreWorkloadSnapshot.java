package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record P2pServiceCentreWorkloadSnapshot(
        String serviceCentreId,
        List<PhysicalToteId> remainingToteIds,
        int remainingUnallocatedPackCount,
        List<BagKey> remainingBagKeys,
        List<OrderSheetKey> unallocatedEmptyOrderSheetKeys,
        Duration estimatedSingleLineWork) {

    public P2pServiceCentreWorkloadSnapshot {
        serviceCentreId = requireValue(serviceCentreId);
        remainingToteIds = distinctCopy(remainingToteIds, "remainingToteIds");
        if (remainingUnallocatedPackCount < 0) {
            throw new IllegalArgumentException(
                    "remainingUnallocatedPackCount must be nonnegative");
        }
        remainingBagKeys = distinctCopy(remainingBagKeys, "remainingBagKeys");
        unallocatedEmptyOrderSheetKeys = distinctCopy(
                unallocatedEmptyOrderSheetKeys, "unallocatedEmptyOrderSheetKeys");
        if (estimatedSingleLineWork == null || estimatedSingleLineWork.isNegative()) {
            throw new IllegalArgumentException(
                    "estimatedSingleLineWork must be nonnull and nonnegative");
        }
    }

    public int remainingInboundToteCount() {
        return remainingToteIds.size();
    }

    public int remainingUnallocatedBagCount() {
        return remainingBagKeys.size();
    }

    public int unallocatedEmptyOrderCount() {
        return unallocatedEmptyOrderSheetKeys.size();
    }

    public boolean hasEstimatedWork() {
        return remainingInboundToteCount() > 0
                || remainingUnallocatedPackCount > 0
                || remainingUnallocatedBagCount() > 0;
    }

    private static <T> List<T> distinctCopy(List<T> values, String fieldName) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not be null or contain null");
        }
        Set<T> distinct = new LinkedHashSet<>(values);
        if (distinct.size() != values.size()) {
            throw new IllegalArgumentException(fieldName + " must contain distinct values");
        }
        return List.copyOf(values);
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        return value.trim();
    }
}
