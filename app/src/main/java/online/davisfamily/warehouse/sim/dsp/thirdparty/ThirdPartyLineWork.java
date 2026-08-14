package online.davisfamily.warehouse.sim.dsp.thirdparty;

public record ThirdPartyLineWork(
        String lineReference,
        String productId,
        int outstandingQuantity,
        String binLocation,
        ThirdPartyWorkType workType) {

    public ThirdPartyLineWork {
        lineReference = requireValue(lineReference, "lineReference");
        productId = requireValue(productId, "productId");
        binLocation = requireValue(binLocation, "binLocation");
        if (outstandingQuantity <= 0) {
            throw new IllegalArgumentException("outstandingQuantity must be positive");
        }
        if (workType == null) {
            throw new IllegalArgumentException("workType must not be null");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
