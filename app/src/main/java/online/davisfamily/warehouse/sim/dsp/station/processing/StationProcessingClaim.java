package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

/**
 * Immutable ownership of an exact routed physical tote after station admission.
 */
public record StationProcessingClaim(
        RoutedPhysicalTote routedTote,
        Duration claimedAt) {

    public StationProcessingClaim {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        if (claimedAt == null) {
            throw new IllegalArgumentException("claimedAt must not be null");
        }
        if (claimedAt.isNegative()) {
            throw new IllegalArgumentException("claimedAt must not be negative");
        }
    }

    public PhysicalToteId physicalToteId() {
        return routedTote.physicalToteId();
    }

    public OperationalRouteDestination destination() {
        return routedTote.destination();
    }
}
