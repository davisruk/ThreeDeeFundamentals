package online.davisfamily.warehouse.sim.dsp.bagging;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record PackSourceProvenance(
        OrderSheetKey sourceOrderSheetKey,
        String lineReference,
        String productId,
        String serviceCentreId,
        String pharmacyId,
        String patientId,
        String prescriptionId) {

    public PackSourceProvenance {
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        lineReference = requireTrimmedValue(lineReference, "lineReference");
        productId = requireTrimmedValue(productId, "productId");
        serviceCentreId = requireTrimmedValue(serviceCentreId, "serviceCentreId");
        pharmacyId = requireTrimmedValue(pharmacyId, "pharmacyId");
        patientId = requireTrimmedValue(patientId, "patientId");
        prescriptionId = requireTrimmedValue(prescriptionId, "prescriptionId");
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
