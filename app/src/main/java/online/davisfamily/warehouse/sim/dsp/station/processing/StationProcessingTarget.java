package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

/**
 * Simulation-thread admission and claim boundary for one station destination.
 */
public interface StationProcessingTarget {
    OperationalRouteDestination destination();

    StationProcessingCoordinator coordinator();

    StationProcessingAdmissionDecision evaluate(RoutedPhysicalTote routedTote);

    StationProcessingClaim accept(RoutedPhysicalTote routedTote, Duration claimedAt);
}
