package online.davisfamily.warehouse.sim.dsp.supply;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record PhysicalToteSupplySnapshot(
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        String serviceCentreId,
        long sourceSequenceNumber,
        PhysicalToteSupplyState state) {

    public PhysicalToteSupplySnapshot {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (orderType == OrderType.EMPTY) {
            throw new IllegalArgumentException("EMPTY orders must not have physical supply snapshots");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }
}
