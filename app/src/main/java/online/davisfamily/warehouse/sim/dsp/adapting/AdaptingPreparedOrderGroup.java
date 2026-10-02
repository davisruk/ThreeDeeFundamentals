package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.List;

/** Immutable preview of one strict order-owned bin-group collection. */
public record AdaptingPreparedOrderGroup(
        String storeId,
        String referenceOrderId,
        long mutationVersion,
        List<AdaptedLineRecord> records,
        boolean firstCollection) {

    public AdaptingPreparedOrderGroup {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("storeId must not be blank");
        }
        if (referenceOrderId == null || referenceOrderId.isBlank()) {
            throw new IllegalArgumentException("referenceOrderId must not be blank");
        }
        if (mutationVersion < 0) {
            throw new IllegalArgumentException("mutationVersion must not be negative");
        }
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }
        storeId = storeId.trim();
        referenceOrderId = referenceOrderId.trim();
        records = List.copyOf(records);
        if (firstCollection == records.isEmpty()) {
            throw new IllegalArgumentException("First collection requires records; later collection must be empty");
        }
    }
}
