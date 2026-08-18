package online.davisfamily.warehouse.sim.dsp.routing;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;

public class DspRouteDeriver {
    private final ThirdPartyVisitFactory thirdPartyVisitFactory;

    public DspRouteDeriver(ProductMasterRepository productMasterRepository) {
        if (productMasterRepository == null) {
            throw new IllegalArgumentException("productMasterRepository must not be null");
        }
        this.thirdPartyVisitFactory = new ThirdPartyVisitFactory(productMasterRepository);
    }

    public RouteRequirements derive(NotionalToteOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        boolean requiresThirdParty = thirdPartyVisitFactory.planFor(order).isPresent();
        boolean requiresSortable = order.orderType() == OrderType.ADAPTED
                || order.items().stream().anyMatch(item -> item.lineType() == DspOrderLineType.ADAPTED);

        boolean requiresP2p = order.orderType() == OrderType.ASSOCIATED
                || order.orderType() == OrderType.EMPTY
                || order.orderType() == OrderType.FULL_PACK;
        StartLocation startLocation = order.orderType() == OrderType.EMPTY ? StartLocation.AV02 : StartLocation.OSR;

        return new RouteRequirements(
                requiresThirdParty,
                requiresSortable,
                false,
                requiresP2p,
                false,
                startLocation);
    }
}
