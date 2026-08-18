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
        TipperTotePayload payload = payloadFactory.createPayload();
        if (payload == null) {
            throw new IllegalStateException("Payload factory returned null for order " + orderId);
        }
        String payloadToteId = payload.getTote().getId();
        String plannedToteId = toteLoadPlan.physicalToteId().value();
        if (!plannedToteId.equals(payloadToteId)) {
            throw new IllegalStateException(
                    "Scheduled release physical tote mismatch for order " + orderId
                            + ": plan=" + plannedToteId + ", payload=" + payloadToteId);
        }
        return payload;
    }
}
