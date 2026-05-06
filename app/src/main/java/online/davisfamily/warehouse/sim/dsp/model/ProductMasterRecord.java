package online.davisfamily.warehouse.sim.dsp.model;

public record ProductMasterRecord(String productId, ProductCategory category, boolean thirdParty) {
    public ProductMasterRecord {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
    }
}
