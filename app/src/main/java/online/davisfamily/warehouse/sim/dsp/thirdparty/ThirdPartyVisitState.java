package online.davisfamily.warehouse.sim.dsp.thirdparty;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record ThirdPartyVisitState(
        OrderSheetKey orderSheetKey,
        PhysicalToteId physicalToteId,
        int lineCount,
        int outstandingPackCount,
        double remainingProcessingSeconds) {

    public ThirdPartyVisitState {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (lineCount <= 0) {
            throw new IllegalArgumentException("lineCount must be > 0");
        }
        if (outstandingPackCount <= 0) {
            throw new IllegalArgumentException("outstandingPackCount must be > 0");
        }
        if (!Double.isFinite(remainingProcessingSeconds) || remainingProcessingSeconds < 0d) {
            throw new IllegalArgumentException("remainingProcessingSeconds must be finite and >= 0");
        }
    }
}
