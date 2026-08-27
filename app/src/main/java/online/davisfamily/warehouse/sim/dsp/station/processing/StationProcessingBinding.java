package online.davisfamily.warehouse.sim.dsp.station.processing;

import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;

/**
 * The exact source queue and processing target for one station destination.
 */
public record StationProcessingBinding(
        StationRoutedToteArrivalQueue sourceQueue,
        StationProcessingTarget target) {

    public StationProcessingBinding {
        if (sourceQueue == null) {
            throw new IllegalArgumentException("sourceQueue must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (target.destination() == null) {
            throw new IllegalArgumentException("target destination must not be null");
        }
        if (target.coordinator() == null) {
            throw new IllegalArgumentException("target coordinator must not be null");
        }
        if (!sourceQueue.destination().equals(target.destination())) {
            throw new IllegalArgumentException("source and target destinations must match");
        }
    }

    public OperationalRouteDestination destination() {
        return sourceQueue.destination();
    }
}
