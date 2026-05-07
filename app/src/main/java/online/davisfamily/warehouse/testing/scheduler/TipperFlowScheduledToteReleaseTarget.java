package online.davisfamily.warehouse.testing.scheduler;

import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;

public class TipperFlowScheduledToteReleaseTarget implements ScheduledToteReleaseTarget {
    private final SimulationWorld sim;
    private final List<RenderableObject> objects;
    private final ToteTrackTipperFlowController tipperFlowController;

    public TipperFlowScheduledToteReleaseTarget(
            SimulationWorld sim,
            List<RenderableObject> objects,
            ToteTrackTipperFlowController tipperFlowController) {
        if (sim == null || objects == null || tipperFlowController == null) {
            throw new IllegalArgumentException("Tipper flow scheduled release target inputs must not be null");
        }
        this.sim = sim;
        this.objects = objects;
        this.tipperFlowController = tipperFlowController;
    }

    @Override
    public boolean canAcceptRelease() {
        return tipperFlowController.canAcceptNextTote();
    }

    @Override
    public SchedulerCommandApplicationResult release(TipperTotePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (!canAcceptRelease()) {
            return SchedulerCommandApplicationResult.deferredResult("Tipper flow cannot accept next tote");
        }

        RenderableObject toteRenderable = payload.getToteRenderable();
        if (!objects.contains(toteRenderable)) {
            objects.add(toteRenderable);
        }
        sim.addTrackableObject(payload.getTote());
        tipperFlowController.acceptNextTote(payload.getTote());
        return SchedulerCommandApplicationResult.appliedResult();
    }
}
