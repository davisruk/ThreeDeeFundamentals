package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * Common completion-controller contract for station domains that own processing time.
 */
public interface StationProcessingCompletionController extends SimulationController {
    String processingControllerId();

    Set<OperationalRouteDestination> destinations();

    StationProcessingCoordinator coordinator();
}
