package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.time.Duration;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingClaim;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;

/**
 * Bridges exact tipper completion into P2P lifecycle completion and the shared station ledger.
 */
public final class StationProcessingP2pToteCompletedListener
        implements TipperToteCompletedListener {
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private final TipperToteCompletedListener lifecycleDelegate;
    private final StationProcessingCoordinator coordinator;

    public StationProcessingP2pToteCompletedListener(
            TipperToteCompletedListener lifecycleDelegate,
            StationProcessingCoordinator coordinator) {
        if (lifecycleDelegate == null) {
            throw new IllegalArgumentException("lifecycleDelegate must not be null");
        }
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.lifecycleDelegate = lifecycleDelegate;
        this.coordinator = coordinator;
    }

    @Override
    public void onToteCompleted(Tote tote, SimulationContext context) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Duration completedAt = simulationTime(context.getSimulationTimeSeconds());
        StationProcessingClaim claim = coordinator.requireActiveClaim(
                new online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId(tote.getId()));
        if (claim.destination().stationType() != StationType.P2P) {
            throw new IllegalStateException(
                    "P2P completion requires an active P2P station claim");
        }
        if (claim.routedTote().tote() != tote) {
            throw new IllegalStateException(
                    "P2P completion tote must be the exact claimed tote instance");
        }

        coordinator.validateCanComplete(
                claim.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                claim.routedTote().loadPlan(),
                completedAt);
        lifecycleDelegate.onToteCompleted(tote, context);
        coordinator.complete(
                claim.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                claim.routedTote().loadPlan(),
                completedAt);
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
