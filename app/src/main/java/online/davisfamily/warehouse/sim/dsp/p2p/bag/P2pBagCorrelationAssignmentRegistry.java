package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;

/**
 * Simulation-thread-owned, append-only ownership ledger for planned bag
 * correlations.
 */
public final class P2pBagCorrelationAssignmentRegistry {
    private final Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation =
            new LinkedHashMap<>();
    private Set<String> correlationIdsSnapshot = Set.of();

    public Optional<P2pBagCorrelationAssignment> find(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        return Optional.ofNullable(assignmentsByCorrelation.get(correlationId.trim()));
    }

    public Optional<P2pLineId> lineFor(String correlationId) {
        return find(correlationId).map(P2pBagCorrelationAssignment::lineId);
    }

    public P2pBagCorrelationAssignmentSnapshot snapshot() {
        return new P2pBagCorrelationAssignmentSnapshot(
                new ArrayList<>(assignmentsByCorrelation.values()));
    }

    public P2pBagCorrelationAssignmentSnapshot assignmentSnapshot() {
        return snapshot();
    }

    public Set<String> correlationIdsSnapshot() {
        return correlationIdsSnapshot;
    }

    public void validateCanCommit(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pPhysicalToteAssignment toteAssignment) {
        List<P2pBagCorrelationRequirement> copiedRequirements =
                validateInputsAndReturn(requirements, toteAssignment);
        for (P2pBagCorrelationRequirement requirement : copiedRequirements) {
            P2pBagCorrelationAssignment existing = assignmentsByCorrelation.get(
                    requirement.correlationId());
            if (existing == null) {
                continue;
            }
            if (!existing.lineId().equals(toteAssignment.lineId())) {
                throw new IllegalStateException(
                        "Bag correlation is already pinned to another P2P line: "
                                + requirement.correlationId());
            }
            if (existing.serviceCentreId() != null
                    && !existing.serviceCentreId().equals(toteAssignment.serviceCentreId())) {
                throw new IllegalStateException(
                        "Bag correlation service centre does not match its existing assignment: "
                                + requirement.correlationId());
            }
        }
    }

    /**
     * Returns a prevalidated simulation-thread commit. The returned action
     * only appends previously unassigned correlations.
     */
    public Runnable prepareCommit(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pPhysicalToteAssignment toteAssignment) {
        List<P2pBagCorrelationRequirement> copiedRequirements =
                validateInputsAndReturn(requirements, toteAssignment);
        validateCanCommit(copiedRequirements, toteAssignment);

        List<P2pBagCorrelationAssignment> additions = new ArrayList<>();
        for (P2pBagCorrelationRequirement requirement : copiedRequirements) {
            if (!assignmentsByCorrelation.containsKey(requirement.correlationId())) {
                additions.add(new P2pBagCorrelationAssignment(
                        requirement.correlationId(), toteAssignment));
            }
        }
        return () -> {
            for (P2pBagCorrelationAssignment addition : additions) {
                if (assignmentsByCorrelation.containsKey(addition.correlationId())) {
                    throw new IllegalStateException(
                            "Bag correlation assignment changed after validation: "
                                    + addition.correlationId());
                }
            }
            if (additions.isEmpty()) {
                return;
            }

            LinkedHashSet<String> prospectiveCorrelationIds =
                    new LinkedHashSet<>(assignmentsByCorrelation.keySet());
            for (P2pBagCorrelationAssignment addition : additions) {
                prospectiveCorrelationIds.add(addition.correlationId());
            }
            Set<String> publishedCorrelationIds = Set.copyOf(prospectiveCorrelationIds);

            for (P2pBagCorrelationAssignment addition : additions) {
                assignmentsByCorrelation.put(addition.correlationId(), addition);
            }
            correlationIdsSnapshot = publishedCorrelationIds;
        };
    }

    public void commit(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pPhysicalToteAssignment toteAssignment) {
        Runnable pending = prepareCommit(requirements, toteAssignment);
        pending.run();
    }

    public boolean compatibleWith(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pLineId lineId) {
        if (requirements == null || lineId == null) {
            throw new IllegalArgumentException("requirements and lineId must not be null");
        }
        for (P2pBagCorrelationRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException("requirements must not contain null");
            }
            P2pBagCorrelationAssignment existing = assignmentsByCorrelation.get(
                    requirement.correlationId());
            if (existing != null && !existing.lineId().equals(lineId)) {
                return false;
            }
        }
        return true;
    }

    public boolean compatibleWith(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pBagCorrelationAssignmentSnapshot assignmentSnapshot,
            P2pLineId lineId) {
        if (assignmentSnapshot == null) {
            throw new IllegalArgumentException("assignmentSnapshot must not be null");
        }
        if (!assignmentSnapshot.compatibleWith(
                new LinkedHashSet<>(requirements == null ? List.of() : requirements), lineId)) {
            return false;
        }
        return compatibleWith(requirements, lineId);
    }

    private static List<P2pBagCorrelationRequirement> validateInputsAndReturn(
            Collection<P2pBagCorrelationRequirement> requirements,
            P2pPhysicalToteAssignment toteAssignment) {
        if (requirements == null) {
            throw new IllegalArgumentException("requirements must not be null");
        }
        if (requirements.isEmpty()) {
            return List.of();
        }
        if (toteAssignment == null) {
            throw new IllegalArgumentException(
                    "toteAssignment must not be null when requirements are present");
        }
        Map<String, P2pBagCorrelationRequirement> distinct = new LinkedHashMap<>();
        for (P2pBagCorrelationRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException("requirements must not contain null");
            }
            P2pBagCorrelationRequirement previous = distinct.putIfAbsent(
                    requirement.correlationId(), requirement);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "requirements must contain distinct correlations");
            }
        }
        return List.copyOf(distinct.values());
    }

}
