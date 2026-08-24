package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

public record DspP2pStickyLineSnapshot(
        P2pLineLeaseSnapshot lease,
        Optional<OutboundToteSnapshot> latestClosedOutboundTote,
        Optional<P2pLineLeaseTransitionSnapshot> lastTransition) {

    public DspP2pStickyLineSnapshot {
        if (lease == null) {
            throw new IllegalArgumentException("lease must not be null");
        }
        if (latestClosedOutboundTote == null || lastTransition == null) {
            throw new IllegalArgumentException("optional inspection values must not be null");
        }
        latestClosedOutboundTote.ifPresent(tote -> {
            if (tote.open()
                    || !tote.p2pLineId().equals(lease.definition().lineId())) {
                throw new IllegalArgumentException(
                        "latest closed outbound tote must match the lease line");
            }
        });
    }
}
