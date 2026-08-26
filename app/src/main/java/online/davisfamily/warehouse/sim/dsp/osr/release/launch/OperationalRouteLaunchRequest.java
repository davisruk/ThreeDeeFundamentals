package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

/**
 * Source-neutral launch request for the first station on an operational route.
 */
public record OperationalRouteLaunchRequest(
        OperationalPhysicalToteReleaseRequest releaseRequest,
        OperationalRouteDestination destination) {

    public OperationalRouteLaunchRequest {
        if (releaseRequest == null) {
            throw new IllegalArgumentException("releaseRequest must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        Optional<P2pPhysicalToteAssignment> assignment = releaseRequest.p2pAssignment();
        if (destination.stationType() == StationType.P2P && assignment.isPresent()
                && !destination.equals(assignment.orElseThrow().destination())) {
            throw new IllegalArgumentException(
                    "direct P2P destination must match the pinned P2P assignment destination");
        }
    }

    public OperationalPhysicalToteIdentity identity() {
        return releaseRequest.identity();
    }

    public OperationalPhysicalToteSource source() {
        return releaseRequest.source();
    }

    public PhysicalToteId physicalToteId() {
        return releaseRequest.physicalToteId();
    }

    public OrderSheetKey orderSheetKey() {
        return releaseRequest.orderSheetKey();
    }

    public OrderType orderType() {
        return identity().orderType();
    }

    public String serviceCentreId() {
        return releaseRequest.serviceCentreId();
    }

    public List<String> pharmacyIds() {
        return releaseRequest.pharmacyIds();
    }

    public Duration releaseTime() {
        return releaseRequest.releaseTime();
    }

    public Optional<P2pPhysicalToteAssignment> p2pAssignment() {
        return releaseRequest.p2pAssignment();
    }
}
