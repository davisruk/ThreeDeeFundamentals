package online.davisfamily.warehouse.sim.dsp.routing;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;

public interface ProductMasterRepository {
    Optional<ProductMasterRecord> findByProductId(String productId);
}
