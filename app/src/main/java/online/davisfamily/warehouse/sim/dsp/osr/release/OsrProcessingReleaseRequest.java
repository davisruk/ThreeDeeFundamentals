package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.time.Duration;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

public record OsrProcessingReleaseRequest(
        InboundToteManifest manifest,
        Duration releaseTime,
        Optional<P2pPhysicalToteAssignment> p2pAssignment) {

    public OsrProcessingReleaseRequest(
            InboundToteManifest manifest,
            Duration releaseTime) {
        this(manifest, releaseTime, Optional.empty());
    }

    public OsrProcessingReleaseRequest {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
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
            if (!assignment.physicalToteId().equals(manifest.physicalToteId())
                    || !assignment.serviceCentreId().equals(manifest.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "P2P assignment must match the release manifest identity");
            }
        });
    }
}
