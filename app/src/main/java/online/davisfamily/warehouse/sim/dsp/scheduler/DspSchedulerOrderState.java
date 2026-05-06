package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

public record DspSchedulerOrderState(
        NotionalToteOrder order,
        RouteRequirements routeRequirements,
        DspOrderStatus status) {

    public DspSchedulerOrderState {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (routeRequirements == null) {
            throw new IllegalArgumentException("routeRequirements must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public DspSchedulerOrderState withStatus(DspOrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return new DspSchedulerOrderState(order, routeRequirements, status);
    }
}
