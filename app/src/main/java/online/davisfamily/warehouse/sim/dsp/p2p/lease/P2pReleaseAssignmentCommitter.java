package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;

@FunctionalInterface
public interface P2pReleaseAssignmentCommitter {
    P2pReleaseAssignmentCommitter NO_OP = (command, manifest) ->
            P2pReleaseAssignmentCommit.NO_OP;

    P2pReleaseAssignmentCommit prepare(
            ReleasePhysicalToteFromOsrCommand command,
            InboundToteManifest manifest);
}
