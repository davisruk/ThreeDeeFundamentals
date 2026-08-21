package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

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
    }
}
