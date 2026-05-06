package online.davisfamily.warehouse.sim.dsp.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;

public class InMemoryProductMasterRepository implements ProductMasterRepository {
    private final Map<String, ProductMasterRecord> productsById;

    public InMemoryProductMasterRepository(List<ProductMasterRecord> products) {
        if (products == null) {
            throw new IllegalArgumentException("products must not be null");
        }
        Map<String, ProductMasterRecord> result = new LinkedHashMap<>();
        for (ProductMasterRecord product : List.copyOf(products)) {
            if (result.putIfAbsent(product.productId(), product) != null) {
                throw new IllegalArgumentException("Duplicate productId: " + product.productId());
            }
        }
        productsById = Map.copyOf(result);
    }

    @Override
    public Optional<ProductMasterRecord> findByProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        return Optional.ofNullable(productsById.get(productId));
    }
}
