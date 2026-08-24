package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public record P2pLeaseRetentionDecision(
        P2pLineId lineId,
        String serviceCentreId,
        P2pLeaseRetentionAction action) {

    public P2pLeaseRetentionDecision {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
    }
}
