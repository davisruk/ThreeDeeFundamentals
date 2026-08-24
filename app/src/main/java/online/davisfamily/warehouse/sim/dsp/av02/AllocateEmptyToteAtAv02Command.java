package online.davisfamily.warehouse.sim.dsp.av02;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record AllocateEmptyToteAtAv02Command(
        long snapshotSequence,
        OrderSheetKey orderSheetKey,
        String serviceCentreId) {

    public AllocateEmptyToteAtAv02Command {
        if (snapshotSequence < 0) {
            throw new IllegalArgumentException("snapshotSequence must be >= 0");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
    }
}
