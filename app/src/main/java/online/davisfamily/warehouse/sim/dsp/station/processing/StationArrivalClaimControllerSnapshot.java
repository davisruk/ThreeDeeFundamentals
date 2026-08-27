package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * Immutable inspection values for one station arrival claimant.
 */
public record StationArrivalClaimControllerSnapshot(
        OperationalRouteDestination sourceDestination,
        int sourceCapacity,
        int sourceOccupancy,
        Optional<PhysicalToteId> headPhysicalToteId,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        Optional<PhysicalToteId> lastClaimedPhysicalToteId,
        long successfulClaimCount) {

    public StationArrivalClaimControllerSnapshot {
        if (sourceDestination == null) {
            throw new IllegalArgumentException("sourceDestination must not be null");
        }
        if (sourceCapacity < 0) {
            throw new IllegalArgumentException("sourceCapacity must be >= 0");
        }
        if (sourceOccupancy < 0 || sourceOccupancy > sourceCapacity) {
            throw new IllegalArgumentException(
                    "sourceOccupancy must be between zero and sourceCapacity");
        }
        if (headPhysicalToteId == null
                || blockedPhysicalToteId == null
                || lastClaimedPhysicalToteId == null) {
            throw new IllegalArgumentException("physical tote optionals must not be null");
        }
        if (sourceOccupancy == 0 && headPhysicalToteId.isPresent()) {
            throw new IllegalArgumentException(
                    "head physical tote ID must be absent when source occupancy is zero");
        }
        if (sourceOccupancy > 0 && headPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "head physical tote ID must be present when source has occupancy");
        }
        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        if (blockedPhysicalToteId.isPresent() != !blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked physical tote and reason must both be present or both be absent");
        }
        if (successfulClaimCount < 0) {
            throw new IllegalArgumentException("successfulClaimCount must be >= 0");
        }
        if ((successfulClaimCount == 0) != lastClaimedPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last claimed identity must be present exactly when count is positive");
        }
    }

    public OperationalRouteDestination destination() {
        return sourceDestination;
    }

    public boolean blocked() {
        return blockedPhysicalToteId.isPresent();
    }
}
