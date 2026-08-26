package online.davisfamily.warehouse.sim.dsp.p2p.lease;

@FunctionalInterface
public interface OperationalP2pReleaseAssignmentCommitter {
    OperationalP2pReleaseAssignmentCommitter NO_OP = request ->
            P2pReleaseAssignmentCommit.NO_OP;

    P2pReleaseAssignmentCommit prepare(P2pReleaseAssignmentRequest request);
}
