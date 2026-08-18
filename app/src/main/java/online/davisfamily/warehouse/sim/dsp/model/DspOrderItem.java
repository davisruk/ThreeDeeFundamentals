package online.davisfamily.warehouse.sim.dsp.model;

public record DspOrderItem(
        String lineReference,
        String productId,
        int quantity,
        String pharmacyId,
        String patientId,
        String prescriptionId,
        DspOrderLineType lineType,
        String referenceOrderId,
        int referenceSheetNumber,
        int numberOfPacksPicked) {

    public DspOrderItem {
        lineReference = requireTrimmedValue(lineReference, "lineReference");
        productId = requireTrimmedValue(productId, "productId");
        pharmacyId = requireTrimmedValue(pharmacyId, "pharmacyId");
        patientId = requireTrimmedValue(patientId, "patientId");
        prescriptionId = requireTrimmedValue(prescriptionId, "prescriptionId");
        referenceOrderId = requireTrimmedValue(referenceOrderId, "referenceOrderId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (lineType == null) {
            throw new IllegalArgumentException("lineType must not be null");
        }
        if (referenceSheetNumber < 1) {
            throw new IllegalArgumentException("referenceSheetNumber must be >= 1");
        }
        if (numberOfPacksPicked < 0) {
            throw new IllegalArgumentException("numberOfPacksPicked must be >= 0");
        }
    }

    @Deprecated
    public DspOrderItem(
            String lineReference,
            String productId,
            int quantity,
            String pharmacyId,
            DspOrderLineType lineType,
            String referenceOrderId,
            int referenceSheetNumber,
            int numberOfPacksPicked) {
        this(
                lineReference,
                productId,
                quantity,
                pharmacyId,
                fixtureIdentity("patient", lineReference),
                fixtureIdentity("prescription", lineReference),
                lineType,
                referenceOrderId,
                referenceSheetNumber,
                numberOfPacksPicked);
    }

    public DspOrderItem(String lineReference, String productId, int quantity) {
        this(
                lineReference,
                productId,
                quantity,
                "UNKNOWN",
                DspOrderLineType.FULL_PACK,
                lineReference,
                1,
                0);
    }

    private static String fixtureIdentity(String identityType, String lineReference) {
        return "fixture-" + identityType + "-" + requireTrimmedValue(lineReference, "lineReference");
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
