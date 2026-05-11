package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmission;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;

public class QueuedReleaseP2pAdmission implements P2pAdmission {
    private final TipperInputQueue tipperInputQueue;

    public QueuedReleaseP2pAdmission(TipperInputQueue tipperInputQueue) {
        if (tipperInputQueue == null) {
            throw new IllegalArgumentException("tipperInputQueue must not be null");
        }
        this.tipperInputQueue = tipperInputQueue;
    }

    @Override
    public P2pAdmissionResult canAdmit(NotionalToteOrder order, P2pAdmissionSnapshot snapshot) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (tipperInputQueue.canAccept()) {
            return P2pAdmissionResult.acceptedResult();
        }
        return P2pAdmissionResult.rejectedResult("P2P input queue is full");
    }
}
