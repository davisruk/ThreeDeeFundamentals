package online.davisfamily.warehouse.sim.dsp.bagging;

public record BagKey(String prescriptionId, int bagOrdinal) {

    public BagKey {
        prescriptionId = requireTrimmedValue(prescriptionId, "prescriptionId");
        if (bagOrdinal < 1) {
            throw new IllegalArgumentException("bagOrdinal must be >= 1");
        }
    }

    public String correlationId() {
        return prescriptionId + "/bag-" + bagOrdinal;
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
