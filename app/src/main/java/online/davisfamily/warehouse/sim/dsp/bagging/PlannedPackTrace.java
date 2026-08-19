package online.davisfamily.warehouse.sim.dsp.bagging;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record PlannedPackTrace(
        String physicalPackId,
        PackSourceProvenance sourceProvenance,
        PhysicalToteId inputPhysicalToteId,
        OrderSheetKey fulfilmentOrderSheetKey,
        BagKey bagKey) {

    public PlannedPackTrace {
        physicalPackId = requireTrimmedValue(physicalPackId, "physicalPackId");
        if (sourceProvenance == null) {
            throw new IllegalArgumentException("sourceProvenance must not be null");
        }
        if (inputPhysicalToteId == null) {
            throw new IllegalArgumentException("inputPhysicalToteId must not be null");
        }
        if (fulfilmentOrderSheetKey == null) {
            throw new IllegalArgumentException("fulfilmentOrderSheetKey must not be null");
        }
        if (bagKey == null) {
            throw new IllegalArgumentException("bagKey must not be null");
        }
        if (!sourceProvenance.prescriptionId().equals(bagKey.prescriptionId())) {
            throw new IllegalArgumentException("sourceProvenance prescriptionId must match bagKey");
        }
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
