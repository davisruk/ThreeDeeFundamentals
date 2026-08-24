package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

@FunctionalInterface
public interface P2pTipperArrivalAcceptedListener {
    void onAccepted(TipperTotePayload payload, ToteLoadPlan loadPlan);

    static P2pTipperArrivalAcceptedListener noOp() {
        return (payload, loadPlan) -> {
        };
    }
}
