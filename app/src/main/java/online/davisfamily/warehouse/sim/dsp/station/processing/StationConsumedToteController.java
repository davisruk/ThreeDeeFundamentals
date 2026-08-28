package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.LinkedHashSet;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.tote.Tote;

/**
 * Applies the terminal physical presentation for station-processing consume dispositions.
 *
 * <p>The coordinator remains the owner of the disposition FIFO. This controller only observes
 * that FIFO and remembers which physical ids have already received the presentation, allowing a
 * pending disposition to remain available for later handoff.</p>
 */
public final class StationConsumedToteController implements SimulationController {
    private final StationProcessingCoordinator coordinator;
    private final Set<PhysicalToteId> presentedConsumePhysicalToteIds = new LinkedHashSet<>();

    public StationConsumedToteController(StationProcessingCoordinator coordinator) {
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
        }
        this.coordinator = coordinator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        for (StationProcessingDisposition disposition : coordinator.pendingDispositions()) {
            if (disposition.type() != StationProcessingDispositionType.CONSUME) {
                continue;
            }
            PhysicalToteId physicalToteId = disposition.physicalToteId();
            if (presentedConsumePhysicalToteIds.contains(physicalToteId)) {
                continue;
            }

            Tote tote = disposition.claim().routedTote().tote();
            tote.closeLids();
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            disposition.claim().routedTote().renderable().setVisible(false);
            presentedConsumePhysicalToteIds.add(physicalToteId);
        }
    }
}
