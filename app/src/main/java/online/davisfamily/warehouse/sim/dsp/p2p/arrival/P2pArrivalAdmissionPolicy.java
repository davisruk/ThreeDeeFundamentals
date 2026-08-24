package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

@FunctionalInterface
public interface P2pArrivalAdmissionPolicy {
    P2pArrivalAdmissionDecision evaluate(P2pArrivalAdmissionRequest request);
}
