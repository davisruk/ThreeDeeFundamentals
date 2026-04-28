package online.davisfamily.warehouse.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;

public class DebugToteInjectorController implements SimulationController {
    private final SimulationWorld sim;
    private final List<RenderableObject> objects;
    private final ToteTrackTipperFlowController tipperFlowController;
    private final Deque<TipperTotePayload> pendingPayloads = new ArrayDeque<>();

    public DebugToteInjectorController(
            SimulationWorld sim,
            List<RenderableObject> objects,
            ToteTrackTipperFlowController tipperFlowController,
            List<TipperTotePayload> pendingPayloads) {
        if (sim == null || objects == null || tipperFlowController == null || pendingPayloads == null) {
            throw new IllegalArgumentException("Debug tote injector inputs must not be null");
        }
        this.sim = sim;
        this.objects = objects;
        this.tipperFlowController = tipperFlowController;
        this.pendingPayloads.addAll(pendingPayloads);
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        injectNextToteIfPossible();
    }

    public boolean hasPendingTotes() {
        return !pendingPayloads.isEmpty();
    }

    private void injectNextToteIfPossible() {
        if (pendingPayloads.isEmpty() || !tipperFlowController.canAcceptNextTote()) {
            return;
        }
        TipperTotePayload payload = pendingPayloads.removeFirst();
        RenderableObject toteRenderable = payload.getToteRenderable();
        if (!objects.contains(toteRenderable)) {
            objects.add(toteRenderable);
        }
        sim.addTrackableObject(payload.getTote());
        tipperFlowController.acceptNextTote(payload.getTote());
    }
}
