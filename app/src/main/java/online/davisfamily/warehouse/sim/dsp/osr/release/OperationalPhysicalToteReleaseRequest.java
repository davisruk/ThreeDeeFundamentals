package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

/**
 * Source-neutral request accepted by a route-entry target after release validation.
 */
public record OperationalPhysicalToteReleaseRequest(
        OperationalPhysicalToteIdentity identity,
        Duration releaseTime,
        Optional<P2pPhysicalToteAssignment> p2pAssignment) {

    public OperationalPhysicalToteReleaseRequest {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (releaseTime == null) {
            throw new IllegalArgumentException("releaseTime must not be null");
        }
        if (releaseTime.isNegative()) {
            throw new IllegalArgumentException("releaseTime must not be negative");
        }
        if (p2pAssignment == null) {
            throw new IllegalArgumentException("p2pAssignment must not be null");
        }
        p2pAssignment.ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(identity.physicalToteId())
                    || !assignment.serviceCentreId().equals(identity.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "P2P assignment must match the physical tote release identity");
            }
        });
    }

    public PhysicalToteId physicalToteId() {
        return identity.physicalToteId();
    }

    public OrderSheetKey orderSheetKey() {
        return identity.orderSheetKey();
    }

    public String serviceCentreId() {
        return identity.serviceCentreId();
    }

    public OperationalPhysicalToteSource source() {
        return identity.source();
    }
}
