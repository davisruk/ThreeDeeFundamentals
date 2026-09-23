package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class PhysicalToteLifecycleSnapshot {
    private final Map<PhysicalToteId, PhysicalToteRecord> totes;
    private final List<PhysicalToteAssignment> assignments;
    private final Map<OrderSheetKey, PhysicalToteAssignment>
            firstActiveAssignmentByOrderSheet;
    private final Map<PhysicalToteId, List<PhysicalToteAssignment>> activeAssignmentsByTote;
    private final Map<OrderSheetKey, List<PhysicalToteAssignment>> assignmentHistoryByOrderSheet;

    public PhysicalToteLifecycleSnapshot(
            Map<PhysicalToteId, PhysicalToteRecord> totes,
            List<PhysicalToteAssignment> assignments) {
        if (totes == null) {
            throw new IllegalArgumentException("totes must not be null");
        }
        if (assignments == null) {
            throw new IllegalArgumentException("assignments must not be null");
        }

        LinkedHashMap<PhysicalToteId, PhysicalToteRecord> toteCopy = new LinkedHashMap<>();
        for (Map.Entry<PhysicalToteId, PhysicalToteRecord> entry : totes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("totes must not contain null keys or values");
            }
            toteCopy.put(entry.getKey(), entry.getValue());
        }
        for (PhysicalToteAssignment assignment : assignments) {
            if (assignment == null) {
                throw new IllegalArgumentException("assignments must not contain null elements");
            }
        }

        totes = Collections.unmodifiableMap(toteCopy);
        assignments = List.copyOf(assignments);

        LinkedHashMap<OrderSheetKey, PhysicalToteAssignment> firstActiveByOrderSheet =
                new LinkedHashMap<>();
        LinkedHashMap<PhysicalToteId, List<PhysicalToteAssignment>> activeByTote =
                new LinkedHashMap<>();
        LinkedHashMap<OrderSheetKey, List<PhysicalToteAssignment>> historyByOrderSheet =
                new LinkedHashMap<>();
        for (PhysicalToteAssignment assignment : assignments) {
            historyByOrderSheet
                    .computeIfAbsent(assignment.orderSheetKey(), ignored -> new ArrayList<>())
                    .add(assignment);
            if (assignment.active()) {
                firstActiveByOrderSheet.putIfAbsent(
                        assignment.orderSheetKey(), assignment);
                activeByTote
                        .computeIfAbsent(assignment.physicalToteId(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }

        this.totes = totes;
        this.assignments = assignments;
        this.firstActiveAssignmentByOrderSheet = Collections.unmodifiableMap(
                firstActiveByOrderSheet);
        this.activeAssignmentsByTote = immutableListMap(activeByTote);
        this.assignmentHistoryByOrderSheet = immutableListMap(historyByOrderSheet);
    }

    public Map<PhysicalToteId, PhysicalToteRecord> totes() {
        return totes;
    }

    public List<PhysicalToteAssignment> assignments() {
        return assignments;
    }

    public Optional<PhysicalToteAssignment> activeAssignmentFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return Optional.ofNullable(firstActiveAssignmentByOrderSheet.get(orderSheetKey));
    }

    public List<PhysicalToteAssignment> activeAssignmentsFor(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        return activeAssignmentsByTote.getOrDefault(toteId, List.of());
    }

    public List<PhysicalToteAssignment> assignmentHistoryFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return assignmentHistoryByOrderSheet.getOrDefault(orderSheetKey, List.of());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PhysicalToteLifecycleSnapshot that)) {
            return false;
        }
        return totes.equals(that.totes) && assignments.equals(that.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totes, assignments);
    }

    @Override
    public String toString() {
        return "PhysicalToteLifecycleSnapshot[totes=" + totes
                + ", assignments=" + assignments + "]";
    }

    private static <K, V> Map<K, List<V>> immutableListMap(
            Map<K, List<V>> source) {
        LinkedHashMap<K, List<V>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
