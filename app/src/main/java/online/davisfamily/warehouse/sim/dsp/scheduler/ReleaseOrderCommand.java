package online.davisfamily.warehouse.sim.dsp.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

public record ReleaseOrderCommand(String orderId, String serviceCentreId, StartLocation startLocation) implements SchedulerCommand {
    public ReleaseOrderCommand {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (startLocation == null) {
            throw new IllegalArgumentException("startLocation must not be null");
        }
    }
}
