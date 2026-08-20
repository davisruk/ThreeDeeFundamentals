package online.davisfamily.warehouse.sim.dsp.osr.release;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;

public record ReleasePhysicalToteFromOsrCommand(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        String serviceCentreId,
        String releaseTargetId) implements SchedulerCommand {

    public ReleasePhysicalToteFromOsrCommand {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        releaseTargetId = requireValue(releaseTargetId, "releaseTargetId");
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
