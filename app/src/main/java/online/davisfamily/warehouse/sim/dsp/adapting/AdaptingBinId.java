package online.davisfamily.warehouse.sim.dsp.adapting;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/** Stable identity for one store/order/sheet bin, including its overflow ordinal. */
public record AdaptingBinId(String storeId, OrderSheetKey targetOrderSheetKey, int ordinal) {

    public AdaptingBinId {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("storeId must not be blank");
        }
        if (targetOrderSheetKey == null) {
            throw new IllegalArgumentException("targetOrderSheetKey must not be null");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be >= 1");
        }
        storeId = storeId.trim();
    }
}
