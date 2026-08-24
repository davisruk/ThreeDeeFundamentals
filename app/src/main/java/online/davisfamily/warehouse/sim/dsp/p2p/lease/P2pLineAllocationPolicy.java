package online.davisfamily.warehouse.sim.dsp.p2p.lease;

@FunctionalInterface
public interface P2pLineAllocationPolicy {
    P2pLineAllocationDecision allocate(P2pLineAllocationRequest request);
}
