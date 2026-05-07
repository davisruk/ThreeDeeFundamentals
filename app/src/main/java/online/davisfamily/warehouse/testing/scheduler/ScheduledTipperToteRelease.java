package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public record ScheduledTipperToteRelease(
        String orderId,
        ToteLoadPlan toteLoadPlan,
        TipperTotePayloadFactory payloadFactory) {

    public ScheduledTipperToteRelease {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (toteLoadPlan == null) {
            throw new IllegalArgumentException("toteLoadPlan must not be null");
        }
        if (payloadFactory == null) {
            throw new IllegalArgumentException("payloadFactory must not be null");
        }
    }

    public TipperTotePayload createPayload() {
        return payloadFactory.createPayload();
    }
}
