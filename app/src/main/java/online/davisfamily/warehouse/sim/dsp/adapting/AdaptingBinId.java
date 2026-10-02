package online.davisfamily.warehouse.sim.dsp.adapting;

/** Stable identity for one store/order bin, including its overflow ordinal. */
public record AdaptingBinId(String storeId, String referenceOrderId, int ordinal) {

    public AdaptingBinId {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("storeId must not be blank");
        }
        if (referenceOrderId == null || referenceOrderId.isBlank()) {
            throw new IllegalArgumentException("referenceOrderId must not be blank");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be >= 1");
        }
        storeId = storeId.trim();
        referenceOrderId = referenceOrderId.trim();
    }
}
