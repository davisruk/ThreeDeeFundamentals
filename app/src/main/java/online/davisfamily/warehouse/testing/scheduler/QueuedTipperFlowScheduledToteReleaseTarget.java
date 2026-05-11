package online.davisfamily.warehouse.testing.scheduler;

import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public class QueuedTipperFlowScheduledToteReleaseTarget implements ScheduledToteReleaseTarget {
    private final SimulationWorld sim;
    private final List<RenderableObject> objects;
    private final TipperInputQueue tipperInputQueue;

    public QueuedTipperFlowScheduledToteReleaseTarget(
            SimulationWorld sim,
            List<RenderableObject> objects,
            TipperInputQueue tipperInputQueue) {
        if (sim == null || objects == null || tipperInputQueue == null) {
            throw new IllegalArgumentException("Queued tipper release target inputs must not be null");
        }
        this.sim = sim;
        this.objects = objects;
        this.tipperInputQueue = tipperInputQueue;
    }

    @Override
    public boolean canAcceptRelease() {
        return tipperInputQueue.canAccept();
    }

    @Override
    public SchedulerCommandApplicationResult release(TipperTotePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (!canAcceptRelease()) {
            return SchedulerCommandApplicationResult.deferredResult("Tipper input queue is full");
        }

        RenderableObject toteRenderable = payload.getToteRenderable();
        if (!objects.contains(toteRenderable)) {
            objects.add(toteRenderable);
        }
        sim.addTrackableObject(payload.getTote());
        tipperInputQueue.enqueue(payload);
        return SchedulerCommandApplicationResult.appliedResult();
    }
}
