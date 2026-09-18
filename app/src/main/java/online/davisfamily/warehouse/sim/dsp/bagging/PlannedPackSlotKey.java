package online.davisfamily.warehouse.sim.dsp.bagging;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

/** Identifies one logical pack position before a physical pack is realised. */
public record PlannedPackSlotKey(
        OrderSheetKey sourceOrderSheetKey,
        String lineReference,
        int packOrdinal) {

    public PlannedPackSlotKey {
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        lineReference = requireTrimmedValue(lineReference, "lineReference");
        if (packOrdinal < 1) {
            throw new IllegalArgumentException("packOrdinal must be >= 1");
        }
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
