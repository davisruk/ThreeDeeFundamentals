package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OperationalPhysicalToteIdentity(
        OperationalPhysicalToteSource source,
        PhysicalToteId physicalToteId,
        OrderSheetKey orderSheetKey,
        OrderType orderType,
        String serviceCentreId,
        PhysicalToteRole physicalToteRole,
        long sourceSequenceNumber) {

    public OperationalPhysicalToteIdentity {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("orderType must not be null");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        if (physicalToteRole == null) {
            throw new IllegalArgumentException("physicalToteRole must not be null");
        }
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
        }
        serviceCentreId = serviceCentreId.trim();

        switch (source) {
            case OSR -> {
                if (orderType == OrderType.EMPTY) {
                    throw new IllegalArgumentException("OSR physical identity must not represent EMPTY work");
                }
                if (physicalToteRole != PhysicalToteRole.INBOUND_PACK) {
                    throw new IllegalArgumentException("OSR physical identity must use INBOUND_PACK role");
                }
            }
            case AV02 -> {
                if (orderType != OrderType.EMPTY) {
                    throw new IllegalArgumentException("AV02 physical identity must represent EMPTY work");
                }
                if (physicalToteRole != PhysicalToteRole.PRE_P2P) {
                    throw new IllegalArgumentException("AV02 physical identity must use PRE_P2P role");
                }
            }
        }
    }
}
