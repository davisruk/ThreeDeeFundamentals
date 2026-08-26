package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

/** Adapts an AV02 release request onto the shared operational launch FIFO. */
public final class Av02OutboundRouteLaunchTarget
        implements OperationalPhysicalToteReleaseTarget {
    private final OperationalRouteDestination destination;
    private final OsrOutboundRouteLaunchQueue launchQueue;

    public Av02OutboundRouteLaunchTarget(
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
    public SchedulerCommandApplicationResult accept(
            OperationalPhysicalToteReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        OperationalRouteLaunchRequest launchRequest =
                OperationalRouteLaunchRequestFactory.fromOperational(request, destination);
        PhysicalToteId physicalToteId = launchRequest.physicalToteId();
        if (launchQueue.contains(physicalToteId)) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote " + physicalToteId.value()
                            + " is already queued for outbound route launch");
        }
        if (!launchQueue.canAccept()) {
            return SchedulerCommandApplicationResult.deferredResult(
                    "AV02 outbound route-launch queue has no capacity for target " + targetId());
        }

        launchQueue.enqueue(launchRequest);
        return SchedulerCommandApplicationResult.appliedResult();
    }
}
