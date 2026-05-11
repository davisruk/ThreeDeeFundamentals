package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;

public class TipperInputQueueController implements SimulationController {
    private final TipperInputQueue tipperInputQueue;
    private final ToteTrackTipperFlowController tipperFlowController;

    public TipperInputQueueController(
            TipperInputQueue tipperInputQueue,
            ToteTrackTipperFlowController tipperFlowController) {
        if (tipperInputQueue == null || tipperFlowController == null) {
            throw new IllegalArgumentException("Tipper input queue controller inputs must not be null");
        }
        this.tipperInputQueue = tipperInputQueue;
        this.tipperFlowController = tipperFlowController;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        TipperTotePayload payload = tipperInputQueue.peekPayload();
        if (payload == null || !tipperFlowController.canAcceptNextTote()) {
            return;
        }
        tipperInputQueue.dequeuePayload();
        payload.getTote().setInteractionMode(ToteMotionState.MOVING);
        tipperFlowController.acceptNextTote(payload.getTote());
    }
}
