package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record OsrOutboundRouteLaunchControllerSnapshot(
        int launchCapacity,
        int launchOccupancy,
        int transportCapacity,
        int transportOccupancy,
        Optional<PhysicalToteId> headPhysicalToteId,
        Optional<OperationalRouteDestination> headDestination,
        Optional<PhysicalToteId> lastHydratedPhysicalToteId,
        Optional<OperationalRouteDestination> lastHydratedDestination,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        long successfulHydrationCount) {

    public OsrOutboundRouteLaunchControllerSnapshot {
        requireOccupancy("launch", launchCapacity, launchOccupancy);
        requireOccupancy("transport", transportCapacity, transportOccupancy);
        if (headPhysicalToteId == null || headDestination == null) {
            throw new IllegalArgumentException("head optionals must not be null");
        }
        if (lastHydratedPhysicalToteId == null || lastHydratedDestination == null) {
            throw new IllegalArgumentException("last hydrated optionals must not be null");
        }
        if (blockedPhysicalToteId == null) {
            throw new IllegalArgumentException("blockedPhysicalToteId must not be null");
        }
        requirePair(
                "head",
                headPhysicalToteId.isPresent(),
                headDestination.isPresent());
        requirePair(
                "last hydrated",
                lastHydratedPhysicalToteId.isPresent(),
                lastHydratedDestination.isPresent());
        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        if (blockedPhysicalToteId.isPresent() != !blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked physical tote and reason must both be present or both be absent");
        }
        if (successfulHydrationCount < 0) {
            throw new IllegalArgumentException("successfulHydrationCount must be >= 0");
        }
        if ((successfulHydrationCount == 0) != lastHydratedPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last hydrated identity must be present exactly when hydration count is positive");
        }
    }

    public int launchRemainingCapacity() {
        return launchCapacity - launchOccupancy;
    }

    public int transportRemainingCapacity() {
        return transportCapacity - transportOccupancy;
    }

    public boolean blocked() {
        return blockedPhysicalToteId.isPresent();
    }

    private static void requireOccupancy(String owner, int capacity, int occupancy) {
        if (capacity < 0) {
            throw new IllegalArgumentException(owner + " capacity must be >= 0");
        }
        if (occupancy < 0 || occupancy > capacity) {
            throw new IllegalArgumentException(
                    owner + " occupancy must be between zero and capacity");
        }
    }

    private static void requirePair(String owner, boolean firstPresent, boolean secondPresent) {
        if (firstPresent != secondPresent) {
            throw new IllegalArgumentException(owner + " identity and destination must match");
        }
    }
}
