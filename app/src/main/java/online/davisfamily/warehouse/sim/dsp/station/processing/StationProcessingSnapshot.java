package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * Immutable inspection view of station-processing ownership.
 */
public record StationProcessingSnapshot(
        List<ActiveClaim> activeClaims,
        List<PendingDisposition> pendingDispositions,
        long completedCount,
        Optional<PhysicalToteId> lastCompletedPhysicalToteId,
        Optional<StationProcessingDispositionType> lastCompletedType,
        long acknowledgedContinuationCount,
        long acknowledgedConsumeCount,
        Optional<PhysicalToteId> lastAcknowledgedPhysicalToteId,
        Optional<StationProcessingDispositionType> lastAcknowledgedType) {

    public record ActiveClaim(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            Duration claimedAt) {

        public ActiveClaim {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteId must not be null");
            }
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (claimedAt == null || claimedAt.isNegative()) {
                throw new IllegalArgumentException("claimedAt must be nonnegative");
            }
        }
    }

    public record PendingDisposition(
            PhysicalToteId physicalToteId,
            OperationalRouteDestination destination,
            StationProcessingDispositionType type,
            Duration claimedAt,
            Duration completedAt) {

        public PendingDisposition {
            if (physicalToteId == null) {
                throw new IllegalArgumentException("physicalToteId must not be null");
            }
            if (destination == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
            if (claimedAt == null || claimedAt.isNegative()) {
                throw new IllegalArgumentException("claimedAt must be nonnegative");
            }
            if (completedAt == null || completedAt.isNegative()) {
                throw new IllegalArgumentException("completedAt must be nonnegative");
            }
            if (completedAt.compareTo(claimedAt) < 0) {
                throw new IllegalArgumentException("completedAt must not precede claimedAt");
            }
        }
    }

    public StationProcessingSnapshot {
        if (activeClaims == null) {
            throw new IllegalArgumentException("activeClaims must not be null");
        }
        if (pendingDispositions == null) {
            throw new IllegalArgumentException("pendingDispositions must not be null");
        }
        if (completedCount < 0) {
            throw new IllegalArgumentException("completedCount must be >= 0");
        }
        if (lastCompletedPhysicalToteId == null) {
            throw new IllegalArgumentException("lastCompletedPhysicalToteId must not be null");
        }
        if (lastCompletedType == null) {
            throw new IllegalArgumentException("lastCompletedType must not be null");
        }
        if (lastCompletedPhysicalToteId.isPresent() != lastCompletedType.isPresent()) {
            throw new IllegalArgumentException(
                    "last completed physical tote ID and type must be present together");
        }
        if (acknowledgedContinuationCount < 0) {
            throw new IllegalArgumentException(
                    "acknowledgedContinuationCount must be >= 0");
        }
        if (acknowledgedConsumeCount < 0) {
            throw new IllegalArgumentException("acknowledgedConsumeCount must be >= 0");
        }
        if (lastAcknowledgedPhysicalToteId == null) {
            throw new IllegalArgumentException(
                    "lastAcknowledgedPhysicalToteId must not be null");
        }
        if (lastAcknowledgedType == null) {
            throw new IllegalArgumentException("lastAcknowledgedType must not be null");
        }
        if (lastAcknowledgedPhysicalToteId.isPresent() != lastAcknowledgedType.isPresent()) {
            throw new IllegalArgumentException(
                    "last acknowledged physical tote ID and type must be present together");
        }
        if (acknowledgedContinuationCount + acknowledgedConsumeCount > completedCount) {
            throw new IllegalArgumentException(
                    "acknowledged disposition count must not exceed completed count");
        }
        activeClaims = List.copyOf(activeClaims);
        pendingDispositions = List.copyOf(pendingDispositions);
    }

    /**
     * Compatibility constructor for callers that only know the original completion fields.
     */
    public StationProcessingSnapshot(
            List<ActiveClaim> activeClaims,
            List<PendingDisposition> pendingDispositions,
            long completedCount,
            Optional<PhysicalToteId> lastCompletedPhysicalToteId,
            Optional<StationProcessingDispositionType> lastCompletedType) {
        this(
                activeClaims,
                pendingDispositions,
                completedCount,
                lastCompletedPhysicalToteId,
                lastCompletedType,
                0,
                0,
                Optional.empty(),
                Optional.empty());
    }
}
