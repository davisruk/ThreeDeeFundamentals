package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;

public record P2pArrivalConsumerBinding(
        StationRoutedToteArrivalQueue sourceQueue,
        P2pArrivalAdmissionPolicy admissionPolicy,
        P2pArrivalRouteBinding routeBinding,
        P2pTipperPayloadFactory payloadFactory,
        P2pTipperArrivalTarget target) {

    public P2pArrivalConsumerBinding {
        if (sourceQueue == null) {
            throw new IllegalArgumentException("sourceQueue must not be null");
        }
        if (admissionPolicy == null) {
            throw new IllegalArgumentException("admissionPolicy must not be null");
        }
        if (routeBinding == null) {
            throw new IllegalArgumentException("routeBinding must not be null");
        }
        if (payloadFactory == null) {
            throw new IllegalArgumentException("payloadFactory must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (sourceQueue.destination().stationType() != StationType.P2P) {
            throw new IllegalArgumentException("sourceQueue must identify a P2P destination");
        }
        if (!sourceQueue.destination().equals(target.destination())) {
            throw new IllegalArgumentException("source and target destinations must match");
        }
    }

    public OperationalRouteDestination destination() {
        return sourceQueue.destination();
    }
}
