package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

/**
 * Simulation-thread-owned ledger for exact station claims and their completion dispositions.
 *
 * <p>No method on this type is synchronized. Callers must use it from the simulation thread only;
 * detached scheduler snapshots are represented by {@link StationProcessingSnapshot}.</p>
 */
public final class StationProcessingCoordinator {
    private final Map<PhysicalToteId, StationProcessingClaim> activeClaims = new LinkedHashMap<>();
    private final Deque<StationProcessingDisposition> pendingDispositions = new ArrayDeque<>();
    private final Set<PhysicalToteId> completedPhysicalToteIds = new LinkedHashSet<>();
    private Optional<PhysicalToteId> lastCompletedPhysicalToteId = Optional.empty();
    private Optional<StationProcessingDispositionType> lastCompletedType = Optional.empty();

    public void validateCanClaim(
            RoutedPhysicalTote routedTote,
            Duration claimedAt) {
        validateClaimInput(routedTote, claimedAt);
        PhysicalToteId physicalToteId = routedTote.physicalToteId();
        if (activeClaims.containsKey(physicalToteId)) {
            throw new IllegalStateException(
                    "Physical tote already has an active station processing claim: "
                            + physicalToteId.value());
        }
        if (completedPhysicalToteIds.contains(physicalToteId)) {
            throw new IllegalStateException(
                    "Physical tote already has a completed station processing disposition: "
                            + physicalToteId.value());
        }
    }

    public StationProcessingClaim claim(
            RoutedPhysicalTote routedTote,
            Duration claimedAt) {
        validateCanClaim(routedTote, claimedAt);
        StationProcessingClaim claim = new StationProcessingClaim(routedTote, claimedAt);
        activeClaims.put(claim.physicalToteId(), claim);
        return claim;
    }

    public StationProcessingClaim requireActiveClaim(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        StationProcessingClaim claim = activeClaims.get(physicalToteId);
        if (claim == null) {
            throw new IllegalStateException(
                    "No active station processing claim for physical tote: "
                            + physicalToteId.value());
        }
        return claim;
    }

    public void validateCanComplete(
            PhysicalToteId physicalToteId,
            StationProcessingDispositionType type,
            ToteLoadPlan currentLoadPlan,
            Duration completedAt) {
        StationProcessingClaim claim = requireActiveClaim(physicalToteId);
        validateCompletionInput(type, currentLoadPlan, completedAt);
        if (!claim.physicalToteId().equals(currentLoadPlan.physicalToteId())) {
            throw new IllegalArgumentException(
                    "currentLoadPlan physical tote ID must match active claim: "
                            + claim.physicalToteId().value());
        }
        if (completedAt.compareTo(claim.claimedAt()) < 0) {
            throw new IllegalArgumentException(
                    "completedAt must not precede claimedAt");
        }
    }

    public StationProcessingDisposition complete(
            PhysicalToteId physicalToteId,
            StationProcessingDispositionType type,
            ToteLoadPlan currentLoadPlan,
            Duration completedAt) {
        validateCanComplete(physicalToteId, type, currentLoadPlan, completedAt);
        StationProcessingClaim claim = activeClaims.remove(physicalToteId);
        StationProcessingDisposition disposition = new StationProcessingDisposition(
                claim,
                type,
                currentLoadPlan,
                completedAt);
        pendingDispositions.addLast(disposition);
        completedPhysicalToteIds.add(physicalToteId);
        lastCompletedPhysicalToteId = Optional.of(physicalToteId);
        lastCompletedType = Optional.of(type);
        return disposition;
    }

    public Optional<StationProcessingDisposition> peekDisposition() {
        return Optional.ofNullable(pendingDispositions.peekFirst());
    }

    public Optional<StationProcessingDisposition> dequeueDisposition() {
        return Optional.ofNullable(pendingDispositions.pollFirst());
    }

    /**
     * Returns the live simulation-thread handoff values in completion FIFO order.
     */
    public List<StationProcessingDisposition> pendingDispositions() {
        return List.copyOf(pendingDispositions);
    }

    public StationProcessingSnapshot snapshot() {
        List<StationProcessingSnapshot.ActiveClaim> active = new ArrayList<>();
        for (StationProcessingClaim claim : activeClaims.values()) {
            active.add(new StationProcessingSnapshot.ActiveClaim(
                    claim.physicalToteId(),
                    claim.destination(),
                    claim.claimedAt()));
        }

        List<StationProcessingSnapshot.PendingDisposition> pending = new ArrayList<>();
        for (StationProcessingDisposition disposition : pendingDispositions) {
            pending.add(new StationProcessingSnapshot.PendingDisposition(
                    disposition.physicalToteId(),
                    disposition.claim().destination(),
                    disposition.type(),
                    disposition.claim().claimedAt(),
                    disposition.completedAt()));
        }

        return new StationProcessingSnapshot(
                active,
                pending,
                completedPhysicalToteIds.size(),
                lastCompletedPhysicalToteId,
                lastCompletedType);
    }

    private static void validateClaimInput(
            RoutedPhysicalTote routedTote,
            Duration claimedAt) {
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

    private static void validateCompletionInput(
            StationProcessingDispositionType type,
            ToteLoadPlan currentLoadPlan,
            Duration completedAt) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (currentLoadPlan == null) {
            throw new IllegalArgumentException("currentLoadPlan must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        if (completedAt.isNegative()) {
            throw new IllegalArgumentException("completedAt must not be negative");
        }
    }
}
