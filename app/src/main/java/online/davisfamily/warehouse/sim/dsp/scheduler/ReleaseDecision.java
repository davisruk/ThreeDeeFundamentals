package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

public record ReleaseDecision(
        String orderId,
        String serviceCentreId,
        StartLocation startLocation,
        RouteRequirements routeRequirements,
        ReleaseOrderCommand command) {

    public ReleaseDecision {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (startLocation == null) {
            throw new IllegalArgumentException("startLocation must not be null");
        }
        if (routeRequirements == null) {
            throw new IllegalArgumentException("routeRequirements must not be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
    }
}
