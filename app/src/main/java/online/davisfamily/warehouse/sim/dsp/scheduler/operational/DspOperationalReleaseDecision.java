package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;

public record DspOperationalReleaseDecision(
        DspOperationalReleaseCandidate candidate,
        OperationalRouteEntry routeEntry,
        ReleasePhysicalToteFromOsrCommand command) {

    public DspOperationalReleaseDecision {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (routeEntry == null) {
            throw new IllegalArgumentException("routeEntry must not be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (!command.physicalToteId().equals(candidate.physicalCandidate().physicalToteId())
                || !command.orderSheetKey().equals(candidate.physicalCandidate().orderSheetKey())
                || !command.serviceCentreId().equals(
                        candidate.physicalCandidate().serviceCentreId())
                || !command.releaseTargetId().equals(routeEntry.targetId())) {
            throw new IllegalArgumentException(
                    "command identity and target must match the operational decision");
        }
        if (!candidate.logicalOrderState().routeRequirements().requiresP2p()
                && command.proposedP2pAssignment().isPresent()) {
            throw new IllegalArgumentException(
                    "non-P2P operational decisions must not carry a P2P assignment");
        }
        if (routeEntry.stationType() == StationType.P2P
                && command.proposedP2pAssignment().isPresent()
                && !command.proposedP2pAssignment().orElseThrow().destination().targetId()
                        .equals(routeEntry.targetId())) {
            throw new IllegalArgumentException(
                    "direct P2P command target must match its proposed assignment");
        }
    }
}
