package online.davisfamily.warehouse.sim.dsp.routing;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

public class DspRouteDeriver {
    private final ProductMasterRepository productMasterRepository;

    public DspRouteDeriver(ProductMasterRepository productMasterRepository) {
        if (productMasterRepository == null) {
            throw new IllegalArgumentException("productMasterRepository must not be null");
        }
        this.productMasterRepository = productMasterRepository;
    }

    public RouteRequirements derive(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        boolean requiresThirdParty = false;
        boolean requiresSortable = false;
        boolean requiresManual = false;
        for (DspOrderItem item : order.items()) {
            ProductMasterRecord product = productMasterRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("No product master data for " + item.productId()));
            if (product.thirdParty()) {
                requiresThirdParty = true;
            }
            if (item.lineType() == DspOrderLineType.ADAPTED) {
                requiresSortable = true;
            }
            if (item.lineType() == DspOrderLineType.MANUAL) {
                requiresManual = true;
            }
        }

        boolean requiresP2p = order.orderType() == OrderType.ASSOCIATED
                || order.orderType() == OrderType.EMPTY
                || order.orderType() == OrderType.FULL_PACK;
        boolean requiresManualMerge = requiresManual
                && (order.orderType() == OrderType.ASSOCIATED || order.orderType() == OrderType.EMPTY);
        StartLocation startLocation = order.orderType() == OrderType.EMPTY ? StartLocation.AV02 : StartLocation.OSR;

        return new RouteRequirements(
                requiresThirdParty,
                requiresSortable,
                requiresManual,
                requiresP2p,
                requiresManualMerge,
                startLocation);
    }
}
