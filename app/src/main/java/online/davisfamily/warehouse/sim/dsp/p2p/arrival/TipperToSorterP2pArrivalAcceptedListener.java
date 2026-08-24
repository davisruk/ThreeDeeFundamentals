package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

public final class TipperToSorterP2pArrivalAcceptedListener
        implements P2pTipperArrivalAcceptedListener {
    private final TipperToSorterSection section;

    public TipperToSorterP2pArrivalAcceptedListener(TipperToSorterSection section) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        this.section = section;
    }

    @Override
    public void onAccepted(TipperTotePayload payload, ToteLoadPlan loadPlan) {
        section.registerToteSource(payload, loadPlan);
    }
}
