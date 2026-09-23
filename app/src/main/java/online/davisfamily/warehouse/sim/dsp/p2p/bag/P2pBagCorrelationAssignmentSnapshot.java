package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

/** Immutable value view of committed correlation ownership. */
public final class P2pBagCorrelationAssignmentSnapshot {
    private static final P2pBagCorrelationAssignmentSnapshot EMPTY =
            new P2pBagCorrelationAssignmentSnapshot(List.of());

    private final List<P2pBagCorrelationAssignment> assignments;
    private final Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation;
    private final Map<P2pLineId, Set<String>> correlationIdsByLine;

    public P2pBagCorrelationAssignmentSnapshot(
            List<P2pBagCorrelationAssignment> assignments) {
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }
        Map<String, P2pBagCorrelationAssignment> byCorrelation = new LinkedHashMap<>();
        Map<P2pLineId, LinkedHashSet<String>> byLine = new LinkedHashMap<>();
        for (P2pBagCorrelationAssignment assignment : assignments) {
            if (assignment == null) {
                throw new IllegalArgumentException("assignments must not contain null");
            }
            if (byCorrelation.putIfAbsent(assignment.correlationId(), assignment) != null) {
                throw new IllegalArgumentException(
                        "correlation assignments must be unique: "
                                + assignment.correlationId());
            }
            byLine.computeIfAbsent(assignment.lineId(), ignored -> new LinkedHashSet<>())
                    .add(assignment.correlationId());
        }
        this.assignments = List.copyOf(assignments);
        this.assignmentsByCorrelation = Collections.unmodifiableMap(byCorrelation);
        Map<P2pLineId, Set<String>> immutableByLine = new LinkedHashMap<>();
        byLine.forEach((lineId, correlationIds) -> immutableByLine.put(
                lineId,
                Collections.unmodifiableSet(new LinkedHashSet<>(correlationIds))));
        this.correlationIdsByLine = Collections.unmodifiableMap(immutableByLine);
    }

    public P2pBagCorrelationAssignmentSnapshot(
            Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation) {
        this(valuesOf(assignmentsByCorrelation));
    }

    public static P2pBagCorrelationAssignmentSnapshot empty() {
        return EMPTY;
    }

    public List<P2pBagCorrelationAssignment> assignments() {
        return assignments;
    }

    public Optional<P2pBagCorrelationAssignment> find(String correlationId) {
        String normalized = requireValue(correlationId, "correlationId");
        return Optional.ofNullable(assignmentsByCorrelation.get(normalized));
    }

    public Optional<P2pLineId> lineFor(String correlationId) {
        return find(correlationId).map(P2pBagCorrelationAssignment::lineId);
    }

    public Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation() {
        return assignmentsByCorrelation;
    }

    public Map<String, P2pBagCorrelationAssignment> correlationAssignments() {
        return assignmentsByCorrelation();
    }

    public Set<String> correlationIdsFor(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        return correlationIdsByLine.getOrDefault(lineId, Set.of());
    }

    public boolean compatibleWith(
            java.util.Set<P2pBagCorrelationRequirement> requirements,
            P2pLineId lineId) {
        if (requirements == null || lineId == null) {
            throw new IllegalArgumentException("requirements and lineId must not be null");
        }
        return requirements.stream()
                .map(P2pBagCorrelationRequirement::correlationId)
                .map(this::lineFor)
                .flatMap(Optional::stream)
                .allMatch(lineId::equals);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof P2pBagCorrelationAssignmentSnapshot that)) {
            return false;
        }
        return assignments.equals(that.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(assignments);
    }

    @Override
    public String toString() {
        return "P2pBagCorrelationAssignmentSnapshot[assignments=" + assignments + "]";
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static List<P2pBagCorrelationAssignment> valuesOf(
            Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation) {
        if (assignmentsByCorrelation == null) {
            return null;
        }
        if (assignmentsByCorrelation.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "assignmentsByCorrelation must not contain null keys or values");
        }
        return List.copyOf(assignmentsByCorrelation.values());
    }
}
