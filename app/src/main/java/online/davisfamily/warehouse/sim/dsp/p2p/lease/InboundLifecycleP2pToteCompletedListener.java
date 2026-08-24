package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.time.Duration;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

public final class InboundLifecycleP2pToteCompletedListener implements TipperToteCompletedListener {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;

    private final InboundToteLifecycleController lifecycleController;

    public InboundLifecycleP2pToteCompletedListener(
            InboundToteLifecycleController lifecycleController) {
        if (lifecycleController == null) {
            throw new IllegalArgumentException("lifecycleController must not be null");
        }
        this.lifecycleController = lifecycleController;
    }

    @Override
    public void onToteCompleted(Tote tote, SimulationContext context) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Duration completionTime = simulationTime(context.getSimulationTimeSeconds());
        PhysicalToteId toteId = new PhysicalToteId(tote.getId());
        OrderType orderType = lifecycleController.manifestFor(toteId).orderType();
        if (orderType != OrderType.ASSOCIATED && orderType != OrderType.FULL_PACK) {
            throw new IllegalStateException(
                    "Only ASSOCIATED or FULL_PACK inbound totes may complete at P2P");
        }
        PhysicalToteRecord record = lifecycleController.snapshot().totes().get(toteId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown inbound physical tote: " + toteId.value());
        }
        if (record.role() != PhysicalToteRole.INBOUND_PACK) {
            throw new IllegalStateException("P2P completion requires an inbound pack tote");
        }
        if (record.state() == PhysicalToteLifecycleState.INBOUND_PACK_TOTE) {
            lifecycleController.advanceToPreP2p(toteId, completionTime);
        } else if (record.state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            throw new IllegalStateException(
                    "Inbound tote cannot complete P2P from state " + record.state());
        }
        lifecycleController.consumeAtP2p(toteId, completionTime);
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
