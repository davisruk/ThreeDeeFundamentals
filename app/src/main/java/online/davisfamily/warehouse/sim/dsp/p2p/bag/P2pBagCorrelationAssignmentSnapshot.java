package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

/** Immutable value view of committed correlation ownership. */
public record P2pBagCorrelationAssignmentSnapshot(
        List<P2pBagCorrelationAssignment> assignments) {

    public P2pBagCorrelationAssignmentSnapshot {
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }
        Map<String, P2pBagCorrelationAssignment> byCorrelation = new LinkedHashMap<>();
        for (P2pBagCorrelationAssignment assignment : assignments) {
            if (assignment == null) {
                throw new IllegalArgumentException("assignments must not contain null");
            }
            if (byCorrelation.putIfAbsent(assignment.correlationId(), assignment) != null) {
                throw new IllegalArgumentException(
                        "correlation assignments must be unique: "
                                + assignment.correlationId());
            }
        }
        assignments = List.copyOf(assignments);
    }

    public P2pBagCorrelationAssignmentSnapshot(
            Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation) {
        this(valuesOf(assignmentsByCorrelation));
    }

    public static P2pBagCorrelationAssignmentSnapshot empty() {
        return new P2pBagCorrelationAssignmentSnapshot(List.of());
    }

    public Optional<P2pBagCorrelationAssignment> find(String correlationId) {
        String normalized = requireValue(correlationId, "correlationId");
        return assignments.stream()
                .filter(assignment -> assignment.correlationId().equals(normalized))
                .findFirst();
    }

    public Optional<P2pLineId> lineFor(String correlationId) {
        return find(correlationId).map(P2pBagCorrelationAssignment::lineId);
    }

    public Map<String, P2pBagCorrelationAssignment> assignmentsByCorrelation() {
        Map<String, P2pBagCorrelationAssignment> result = new LinkedHashMap<>();
        assignments.forEach(assignment -> result.put(assignment.correlationId(), assignment));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, P2pBagCorrelationAssignment> correlationAssignments() {
        return assignmentsByCorrelation();
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
        return new ArrayList<>(assignmentsByCorrelation.values());
    }
}
