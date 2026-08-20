package online.davisfamily.warehouse.sim.dsp.supply;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

public record ServiceCentreSupplySnapshot(
        String serviceCentreId,
        int priority,
        ServiceCentreAuthorizationState authorizationState,
        Optional<Duration> authorizationElapsedTime,
        int physicalManifestCount,
        int preloadedCount,
        int admittedAfterStartupCount,
        int upstreamWaitingCount,
        Set<OrderSheetKey> authorizedEmptyOrderSheetKeys,
        List<PhysicalToteSupplySnapshot> physicalTotes) {

    public ServiceCentreSupplySnapshot {
        if (serviceCentreId == null || serviceCentreId.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        serviceCentreId = serviceCentreId.trim();
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        if (authorizationState == null) {
            throw new IllegalArgumentException("authorizationState must not be null");
        }
        if (authorizationElapsedTime == null) {
            throw new IllegalArgumentException("authorizationElapsedTime must not be null");
        }
        if (authorizationElapsedTime.isPresent()
                && authorizationElapsedTime.orElseThrow().isNegative()) {
            throw new IllegalArgumentException("authorizationElapsedTime must not be negative");
        }
        if (physicalManifestCount < 0) {
            throw new IllegalArgumentException("physicalManifestCount must be >= 0");
        }
        if (preloadedCount < 0) {
            throw new IllegalArgumentException("preloadedCount must be >= 0");
        }
        if (admittedAfterStartupCount < 0) {
            throw new IllegalArgumentException("admittedAfterStartupCount must be >= 0");
        }
        if (upstreamWaitingCount < 0) {
            throw new IllegalArgumentException("upstreamWaitingCount must be >= 0");
        }
        if (preloadedCount + admittedAfterStartupCount + upstreamWaitingCount
                != physicalManifestCount) {
            throw new IllegalArgumentException(
                    "Supply counts must sum to physicalManifestCount");
        }
        if (authorizedEmptyOrderSheetKeys == null) {
            throw new IllegalArgumentException("authorizedEmptyOrderSheetKeys must not be null");
        }
        Set<OrderSheetKey> copiedEmptyKeys = new LinkedHashSet<>();
        for (OrderSheetKey key : authorizedEmptyOrderSheetKeys) {
            if (key == null) {
                throw new IllegalArgumentException(
                        "authorizedEmptyOrderSheetKeys must not contain null");
            }
            copiedEmptyKeys.add(key);
        }
        if (physicalTotes == null) {
            throw new IllegalArgumentException("physicalTotes must not be null");
        }
        List<PhysicalToteSupplySnapshot> copiedPhysicalTotes = new ArrayList<>();
        Set<String> physicalToteIds = new LinkedHashSet<>();
        for (PhysicalToteSupplySnapshot physicalTote : physicalTotes) {
            if (physicalTote == null) {
                throw new IllegalArgumentException("physicalTotes must not contain null");
            }
            if (!serviceCentreId.equals(physicalTote.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Physical tote serviceCentreId must match service-centre snapshot");
            }
            if (!physicalToteIds.add(physicalTote.physicalToteId().value())) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in service-centre snapshot: "
                                + physicalTote.physicalToteId().value());
            }
            copiedPhysicalTotes.add(physicalTote);
        }
        if (copiedPhysicalTotes.size() != physicalManifestCount) {
            throw new IllegalArgumentException(
                    "physicalTotes size must match physicalManifestCount");
        }
        authorizedEmptyOrderSheetKeys = java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(copiedEmptyKeys));
        physicalTotes = List.copyOf(copiedPhysicalTotes);
    }
}
