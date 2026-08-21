package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record WarehouseTransportIngressControllerSnapshot(
        int transportCapacity,
        int transportOccupancy,
        int inFlightCapacity,
        int inFlightOccupancy,
        Optional<PhysicalToteId> headPhysicalToteId,
        Optional<OperationalRouteDestination> headDestination,
        Optional<PhysicalToteId> lastIngressPhysicalToteId,
        Optional<OperationalRouteDestination> lastIngressDestination,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        long successfulIngressCount) {

    public WarehouseTransportIngressControllerSnapshot {
        requireOccupancy("transport", transportCapacity, transportOccupancy);
        requireOccupancy("in-flight", inFlightCapacity, inFlightOccupancy);
        if (headPhysicalToteId == null || headDestination == null) {
            throw new IllegalArgumentException("head optionals must not be null");
        }
        if (lastIngressPhysicalToteId == null || lastIngressDestination == null) {
            throw new IllegalArgumentException("last ingress optionals must not be null");
        }
        if (blockedPhysicalToteId == null) {
            throw new IllegalArgumentException("blockedPhysicalToteId must not be null");
        }
        requirePair("head", headPhysicalToteId.isPresent(), headDestination.isPresent());
        requirePair(
                "last ingress",
                lastIngressPhysicalToteId.isPresent(),
                lastIngressDestination.isPresent());
        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        if (blockedPhysicalToteId.isPresent() != !blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked physical tote and reason must both be present or both be absent");
        }
        if (successfulIngressCount < 0) {
            throw new IllegalArgumentException("successfulIngressCount must be >= 0");
        }
        if ((successfulIngressCount == 0) != lastIngressPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last ingress identity must be present exactly when ingress count is positive");
        }
    }

    public int transportRemainingCapacity() {
        return transportCapacity - transportOccupancy;
    }

    public int inFlightRemainingCapacity() {
        return inFlightCapacity - inFlightOccupancy;
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
