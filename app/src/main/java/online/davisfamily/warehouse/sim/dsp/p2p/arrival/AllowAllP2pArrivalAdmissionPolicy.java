package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

public final class AllowAllP2pArrivalAdmissionPolicy implements P2pArrivalAdmissionPolicy {

    @Override
    public P2pArrivalAdmissionDecision evaluate(P2pArrivalAdmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return P2pArrivalAdmissionDecision.permit();
    }
}
