package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

public record P2pLineActivitySnapshot(
        P2pInputActivitySnapshot input,
        P2pPackPathActivitySnapshot packPath,
        P2pBaggingActivitySnapshot bagging,
        Optional<OutboundToteSnapshot> openOutboundTote) {

    public P2pLineActivitySnapshot {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (packPath == null) {
            throw new IllegalArgumentException("packPath must not be null");
        }
        if (bagging == null) {
            throw new IllegalArgumentException("bagging must not be null");
        }
        if (openOutboundTote == null) {
            throw new IllegalArgumentException("openOutboundTote must not be null");
        }
        openOutboundTote.ifPresent(tote -> {
            if (!tote.open()) {
                throw new IllegalArgumentException("openOutboundTote must contain only an open tote");
            }
        });
    }

    public static P2pLineActivitySnapshot idle() {
        return new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
    }

    public boolean processingDrained() {
        return input.empty() && packPath.empty() && bagging.empty();
    }

    public boolean quiescent() {
        return processingDrained() && openOutboundTote.isEmpty();
    }
}
