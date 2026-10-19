package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.Optional;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkLedger;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

/** Accounts actual tipper completion only after the entire existing completion chain succeeds. */
public final class OperationalWindowP2pToteCompletedListener implements TipperToteCompletedListener {
    private final Optional<P2pLineId> line;
    private final DspOperationalWorkLedger work;
    private final TipperToteCompletedListener delegate;

    public OperationalWindowP2pToteCompletedListener(
            P2pLineId lineId, DspOperationalWorkLedger work, TipperToteCompletedListener delegate) {
        if (lineId == null || work == null || delegate == null) {
            throw new IllegalArgumentException("lineId, work and delegate must not be null");
        }
        if (!work.snapshot().totalsByLine().containsKey(lineId)) {
            throw new IllegalArgumentException("Unknown configured P2P line: " + lineId);
        }
        this.line = Optional.of(lineId);
        this.work = work;
        this.delegate = delegate;
    }

    @Override
    public void onToteCompleted(Tote tote, SimulationContext context) {
        if (tote == null || context == null) {
            throw new IllegalArgumentException("tote and context must not be null");
        }
        PhysicalToteId id = new PhysicalToteId(tote.getId());
        work.validateInboundConsumed(id, StationType.P2P, line);
        delegate.onToteCompleted(tote, context);
        work.recordInboundConsumed(id, StationType.P2P, line);
    }
}
