package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.function.BiFunction;

import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.ProductMasterRepository;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public class ProductMasterThirdPartyPackPlanFactory implements ThirdPartyPackPlanFactory {
    private final ProductMasterRepository productMasterRepository;
    private final BiFunction<ThirdPartyVisit, ThirdPartyLineWork, String> correlationIdResolver;

    public ProductMasterThirdPartyPackPlanFactory(
            ProductMasterRepository productMasterRepository,
            BiFunction<ThirdPartyVisit, ThirdPartyLineWork, String> correlationIdResolver) {
        if (productMasterRepository == null) {
            throw new IllegalArgumentException("productMasterRepository must not be null");
        }
        if (correlationIdResolver == null) {
            throw new IllegalArgumentException("correlationIdResolver must not be null");
        }
        this.productMasterRepository = productMasterRepository;
        this.correlationIdResolver = correlationIdResolver;
    }

    @Override
    public PackPlan createPackPlan(ThirdPartyVisit visit, ThirdPartyLineWork lineWork, int packOrdinal) {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
        if (lineWork == null) {
            throw new IllegalArgumentException("lineWork must not be null");
        }
        if (packOrdinal <= 0 || packOrdinal > lineWork.outstandingQuantity()) {
            throw new IllegalArgumentException("packOrdinal must identify an outstanding pack");
        }

        ProductMasterRecord product = productMasterRepository.findByProductId(lineWork.productId())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing product master record for " + lineWork.productId()));
        PackDimensions dimensions = product.dimensions()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing pack dimensions for product " + lineWork.productId()));
        String correlationId = correlationIdResolver.apply(visit, lineWork);
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalStateException("Resolved correlationId must not be blank");
        }

        return new PackPlan(
                "pack-" + lineWork.lineReference() + "-" + packOrdinal,
                correlationId.trim(),
                dimensions);
    }
}
