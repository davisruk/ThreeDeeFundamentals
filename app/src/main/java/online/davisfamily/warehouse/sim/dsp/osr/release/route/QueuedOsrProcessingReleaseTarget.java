package online.davisfamily.warehouse.sim.dsp.osr.release.route;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

public final class QueuedOsrProcessingReleaseTarget implements OsrProcessingReleaseTarget {
    private final OperationalRouteEntryQueue queue;

    public QueuedOsrProcessingReleaseTarget(OperationalRouteEntryQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("queue must not be null");
        }
        this.queue = queue;
    }

    @Override
    public String targetId() {
        return queue.definition().targetId();
    }

    @Override
    public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        PhysicalToteId physicalToteId = request.manifest().physicalToteId();
        if (queue.contains(physicalToteId)) {
            return SchedulerCommandApplicationResult.rejectedResult(
                    "Physical tote " + physicalToteId.value()
                            + " is already queued at route target " + targetId());
        }
        if (!queue.canAccept()) {
            return SchedulerCommandApplicationResult.deferredResult(
                    "Route target " + targetId() + " has no waiting capacity");
        }

        queue.enqueue(request);
        return SchedulerCommandApplicationResult.appliedResult();
    }
}
