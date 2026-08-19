package online.davisfamily.warehouse.sim.dsp.outbound;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record OutputSheetAllocation(
        OrderSheetKey sourceOwningSheetKey,
        OrderSheetKey outputSheetKey) {

    public OutputSheetAllocation {
        if (sourceOwningSheetKey == null) {
            throw new IllegalArgumentException("sourceOwningSheetKey must not be null");
        }
        if (outputSheetKey == null) {
            throw new IllegalArgumentException("outputSheetKey must not be null");
        }
        if (!sourceOwningSheetKey.orderId().equals(outputSheetKey.orderId())) {
            throw new IllegalArgumentException("outputSheetKey must retain the source order ID");
        }
    }

    public boolean generated() {
        return !sourceOwningSheetKey.equals(outputSheetKey);
    }
}
