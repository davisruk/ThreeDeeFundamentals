package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

public record OperationalReleaseSelection(
        DspOperationalReleaseCandidate candidate,
        OperationalRouteEntry routeEntry,
        Optional<P2pPhysicalToteAssignment> proposedP2pAssignment,
        boolean activePharmacyAffinity) {

    public OperationalReleaseSelection(
            DspOperationalReleaseCandidate candidate,
            OperationalRouteEntry routeEntry) {
        this(candidate, routeEntry, Optional.empty(), false);
    }

    public OperationalReleaseSelection {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (routeEntry == null) {
            throw new IllegalArgumentException("routeEntry must not be null");
        }
        if (proposedP2pAssignment == null) {
            throw new IllegalArgumentException("proposedP2pAssignment must not be null");
        }
        boolean requiresP2p = candidate.logicalOrderState().routeRequirements().requiresP2p();
        if (!requiresP2p && (proposedP2pAssignment.isPresent() || activePharmacyAffinity)) {
            throw new IllegalArgumentException(
                    "non-P2P candidates must not have a P2P assignment or affinity");
        }
        if (activePharmacyAffinity && proposedP2pAssignment.isEmpty()) {
            throw new IllegalArgumentException("P2P affinity requires a proposed assignment");
        }
        proposedP2pAssignment.ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(
                    candidate.physicalCandidate().physicalToteId())
                    || !assignment.serviceCentreId().equals(
                            candidate.physicalCandidate().serviceCentreId())) {
                throw new IllegalArgumentException(
                        "proposed P2P assignment must match the selected physical candidate");
            }
            if (routeEntry.stationType() == StationType.P2P
                    && !routeEntry.targetId().equals(assignment.destination().targetId())) {
                throw new IllegalArgumentException(
                        "direct P2P route entry must match the proposed assignment destination");
            }
        });
    }
}
