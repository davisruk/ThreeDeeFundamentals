package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalRouteBinding;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperPayloadFactory;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;

public record P2pStickyArrivalBinding(
        P2pLineId lineId,
        StationRoutedToteArrivalQueue sourceQueue,
        P2pArrivalRouteBinding routeBinding,
        P2pTipperPayloadFactory payloadFactory,
        P2pTipperArrivalTarget target) {

    public P2pStickyArrivalBinding {
        if (lineId == null
                || sourceQueue == null
                || routeBinding == null
                || payloadFactory == null
                || target == null) {
            throw new IllegalArgumentException("sticky arrival binding inputs must not be null");
        }
    }
}
