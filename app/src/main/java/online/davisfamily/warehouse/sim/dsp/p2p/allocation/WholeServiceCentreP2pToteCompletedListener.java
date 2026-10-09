package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedger;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

/**
 * Records the whole-centre inbound watermark release after the existing P2P completion chain.
 */
public final class WholeServiceCentreP2pToteCompletedListener implements TipperToteCompletedListener {
    private final P2pLineId lineId;
    private final WholeServiceCentreReleaseLedger ledger;
    private final TipperToteCompletedListener delegate;

    public WholeServiceCentreP2pToteCompletedListener(
            P2pLineId lineId,
            WholeServiceCentreReleaseLedger ledger,
            TipperToteCompletedListener delegate) {
        if (lineId == null || ledger == null || delegate == null) {
            throw new IllegalArgumentException("lineId, ledger and delegate must not be null");
        }
        ledger.snapshot().outstandingToteCount(lineId);
        this.lineId = lineId;
        this.ledger = ledger;
        this.delegate = delegate;
    }

    @Override
    public void onToteCompleted(Tote tote, SimulationContext context) {
        if (tote == null || context == null) {
            throw new IllegalArgumentException("tote and context must not be null");
        }
        PhysicalToteId physicalToteId = new PhysicalToteId(tote.getId());
        ledger.validateTippingCompletion(physicalToteId, lineId);
        delegate.onToteCompleted(tote, context);
        ledger.recordTippingCompleted(physicalToteId, lineId);
    }
}
