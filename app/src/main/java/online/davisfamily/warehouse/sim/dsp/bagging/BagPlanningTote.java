package online.davisfamily.warehouse.sim.dsp.bagging;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public record BagPlanningTote(
        OrderSheetKey fulfilmentOrderSheetKey,
        String serviceCentreId,
        ToteLoadPlan toteLoadPlan) {

    public BagPlanningTote {
        if (fulfilmentOrderSheetKey == null) {
            throw new IllegalArgumentException("fulfilmentOrderSheetKey must not be null");
        }
        serviceCentreId = requireTrimmedValue(serviceCentreId, "serviceCentreId");
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
