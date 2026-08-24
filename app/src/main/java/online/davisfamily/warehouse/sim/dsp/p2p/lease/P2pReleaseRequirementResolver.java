package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

@FunctionalInterface
public interface P2pReleaseRequirementResolver {
    RouteRequirements resolve(OrderSheetKey orderSheetKey);
}
