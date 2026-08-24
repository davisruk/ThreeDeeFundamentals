package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

public record OsrOutboundRouteLaunchRequest(
        OsrProcessingReleaseRequest releaseRequest,
        OperationalRouteDestination destination) {

    public OsrOutboundRouteLaunchRequest {
        if (releaseRequest == null) {
            throw new IllegalArgumentException("releaseRequest must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
    }

    public PhysicalToteId physicalToteId() {
        return releaseRequest.manifest().physicalToteId();
    }

    public Optional<P2pPhysicalToteAssignment> p2pAssignment() {
        return releaseRequest.p2pAssignment();
    }
}
