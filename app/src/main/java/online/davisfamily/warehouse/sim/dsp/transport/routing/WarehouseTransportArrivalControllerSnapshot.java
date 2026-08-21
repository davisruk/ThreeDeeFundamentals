package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

public record WarehouseTransportArrivalControllerSnapshot(
        List<PendingArrival> pendingArrivals,
        Optional<PhysicalToteId> lastArrivedPhysicalToteId,
        Optional<OperationalRouteDestination> lastArrivedDestination,
        Optional<PhysicalToteId> blockedPhysicalToteId,
        String blockedReason,
        long successfulArrivalCount) {

    public record PendingArrival(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            String terminalSensorId) {

        public PendingArrival {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteId must not be null");
            }
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (terminalSensorId == null || terminalSensorId.isBlank()) {
                throw new IllegalArgumentException("terminalSensorId must not be blank");
            }
            terminalSensorId = terminalSensorId.trim();
        }
    }

    public WarehouseTransportArrivalControllerSnapshot {
        if (pendingArrivals == null) {
            throw new IllegalArgumentException("pendingArrivals must not be null");
        }
        Set<PhysicalToteId> pendingIds = new LinkedHashSet<>();
        for (PendingArrival pending : pendingArrivals) {
            if (pending == null) {
                throw new IllegalArgumentException("pendingArrivals must not contain null");
            }
            if (!pendingIds.add(pending.physicalToteId())) {
                throw new IllegalArgumentException(
                        "Duplicate pending arrival: " + pending.physicalToteId().value());
            }
        }
        pendingArrivals = List.copyOf(pendingArrivals);
        if (lastArrivedPhysicalToteId == null || lastArrivedDestination == null) {
            throw new IllegalArgumentException("last-arrived optionals must not be null");
        }
        if (blockedPhysicalToteId == null) {
            throw new IllegalArgumentException("blockedPhysicalToteId must not be null");
        }
        if (lastArrivedPhysicalToteId.isPresent() != lastArrivedDestination.isPresent()) {
            throw new IllegalArgumentException(
                    "last-arrived identity and destination must match");
        }
        blockedReason = blockedReason == null ? "" : blockedReason.trim();
        if (blockedPhysicalToteId.isPresent() && blockedReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "blocked reason must be present with blocked physical tote");
        }
        if (successfulArrivalCount < 0) {
            throw new IllegalArgumentException("successfulArrivalCount must be >= 0");
        }
        if ((successfulArrivalCount == 0) != lastArrivedPhysicalToteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "last-arrived identity must be present exactly when arrival count is positive");
        }
    }

    public boolean blocked() {
        return !blockedReason.isEmpty();
    }
}
