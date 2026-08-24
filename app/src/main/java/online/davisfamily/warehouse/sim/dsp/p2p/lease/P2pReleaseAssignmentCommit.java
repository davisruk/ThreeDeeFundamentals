package online.davisfamily.warehouse.sim.dsp.p2p.lease;

@FunctionalInterface
public interface P2pReleaseAssignmentCommit {
    P2pReleaseAssignmentCommit NO_OP = () -> { };

    void commit();
}
