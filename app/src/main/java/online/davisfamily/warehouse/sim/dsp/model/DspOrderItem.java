package online.davisfamily.warehouse.sim.dsp.model;

public record DspOrderItem(String itemId, String productId, int quantity) {
    public DspOrderItem {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
