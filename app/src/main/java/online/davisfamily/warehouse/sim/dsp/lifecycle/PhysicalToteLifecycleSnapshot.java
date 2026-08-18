package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public record PhysicalToteLifecycleSnapshot(
        Map<PhysicalToteId, PhysicalToteRecord> totes,
        List<PhysicalToteAssignment> assignments) {

    public PhysicalToteLifecycleSnapshot {
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
        if (assignments.stream().anyMatch(assignment -> assignment == null)) {
            throw new IllegalArgumentException("assignments must not contain null elements");
        }

        totes = Collections.unmodifiableMap(toteCopy);
        assignments = List.copyOf(assignments);
    }

    public Optional<PhysicalToteAssignment> activeAssignmentFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return assignments.stream()
                .filter(PhysicalToteAssignment::active)
                .filter(assignment -> assignment.orderSheetKey().equals(orderSheetKey))
                .findFirst();
    }

    public List<PhysicalToteAssignment> activeAssignmentsFor(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        return assignments.stream()
                .filter(PhysicalToteAssignment::active)
                .filter(assignment -> assignment.physicalToteId().equals(toteId))
                .toList();
    }

    public List<PhysicalToteAssignment> assignmentHistoryFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return assignments.stream()
                .filter(assignment -> assignment.orderSheetKey().equals(orderSheetKey))
                .toList();
    }
}
