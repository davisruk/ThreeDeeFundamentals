package online.davisfamily.warehouse.sim.dsp.io;

public record UnresolvedProductLine(String orderId, String lineReference, String productId) {
    public UnresolvedProductLine {
        orderId = requireValue(orderId, "orderId");
        lineReference = requireValue(lineReference, "lineReference");
        productId = requireValue(productId, "productId");
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
