package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.ProductMasterRepository;

public class ThirdPartyVisitFactory {
    private final ProductMasterRepository productMasterRepository;

    public ThirdPartyVisitFactory(ProductMasterRepository productMasterRepository) {
        if (productMasterRepository == null) {
            throw new IllegalArgumentException("productMasterRepository must not be null");
        }
        this.productMasterRepository = productMasterRepository;
    }

    public Optional<ThirdPartyVisit> create(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        DspOrderItem manualLine = order.items().stream()
                .filter(line -> line.lineType() == DspOrderLineType.MANUAL)
                .findFirst()
                .orElse(null);
        if (manualLine != null) {
            throw new IllegalArgumentException(
                    "MANUAL line " + manualLine.lineReference() + " is outside active simulation scope");
        }

        List<ThirdPartyLineWork> lineWork = new ArrayList<>();
        for (DspOrderItem line : order.items()) {
            ProductMasterRecord product = productMasterRepository.findByProductId(line.productId())
                    .orElseThrow(() -> new IllegalArgumentException("No product master data for " + line.productId()));
            int outstandingQuantity = line.quantity() - line.numberOfPacksPicked();
            if (outstandingQuantity <= 0 || !product.thirdParty()) {
                continue;
            }

            ThirdPartyWorkType workType = workType(order.orderType(), line.lineType());
            if (workType == null) {
                continue;
            }
            lineWork.add(new ThirdPartyLineWork(
                    line.lineReference(),
                    line.productId(),
                    outstandingQuantity,
                    product.thirdPartyLocation().orElseThrow(),
                    workType));
        }

        if (lineWork.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ThirdPartyVisit(
                order.orderId(),
                order.notionalToteId(),
                order.orderType(),
                lineWork));
    }

    private ThirdPartyWorkType workType(OrderType orderType, DspOrderLineType lineType) {
        if (orderType == OrderType.ADAPTED) {
            return ThirdPartyWorkType.ADAPTED_PREPARATION;
        }
        if (lineType == DspOrderLineType.FULL_PACK) {
            return ThirdPartyWorkType.DIRECT_FULFILMENT;
        }
        return null;
    }
}
