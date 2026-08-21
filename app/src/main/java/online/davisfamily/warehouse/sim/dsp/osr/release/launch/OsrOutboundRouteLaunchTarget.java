package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

public final class OsrOutboundRouteLaunchTarget implements OsrProcessingReleaseTarget {
    private final OperationalRouteDestination destination;
    private final OsrOutboundRouteLaunchQueue launchQueue;

    public OsrOutboundRouteLaunchTarget(
            OperationalRouteDestination destination,
            OsrOutboundRouteLaunchQueue launchQueue) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (launchQueue == null) {
            throw new IllegalArgumentException("launchQueue must not be null");
        }
        this.destination = destination;
        this.launchQueue = launchQueue;
    }

    public OperationalRouteDestination destination() {
        return destination;
    }

    @Override
    public String targetId() {
        return destination.targetId();
    }

    @Override
    public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        PhysicalToteId physicalToteId = request.manifest().physicalToteId();
        if (launchQueue.contains(physicalToteId)) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote " + physicalToteId.value()
                            + " is already queued for outbound route launch");
        }
        if (!launchQueue.canAccept()) {
            return SchedulerCommandApplicationResult.deferredResult(
                    "OSR outbound route-launch queue has no capacity for target " + targetId());
        }

        launchQueue.enqueue(new OsrOutboundRouteLaunchRequest(request, destination));
        return SchedulerCommandApplicationResult.appliedResult();
    }
}
