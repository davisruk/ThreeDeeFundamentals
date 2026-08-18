package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public class PhysicalToteLifecycleLedger {
    private final Map<PhysicalToteId, PhysicalToteRecord> totes = new LinkedHashMap<>();
    private final List<PhysicalToteAssignment> assignments = new ArrayList<>();
    private long nextAssignmentSequence;

    public void register(PhysicalToteRecord tote) {
        if (tote == null) {
            throw new IllegalArgumentException("tote must not be null");
        }
        if (totes.putIfAbsent(tote.id(), tote) != null) {
            throw new IllegalArgumentException("Physical tote is already registered: " + tote.id().value());
        }
    }

    public PhysicalToteRecord transitionTote(
            PhysicalToteId toteId,
            PhysicalToteLifecycleState nextState) {
        PhysicalToteRecord current = requireTote(toteId);
        PhysicalToteRecord transitioned = current.transitionTo(nextState);
        totes.put(toteId, transitioned);
        return transitioned;
    }

    public PhysicalToteAssignment assign(
            OrderSheetKey orderSheetKey,
            PhysicalToteId toteId,
            PhysicalToteAssignmentStage stage,
            Duration activationTime) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }

        PhysicalToteRecord tote = requireTote(toteId);
        if (tote.terminal()) {
            throw new IllegalStateException("Cannot assign terminal physical tote: " + toteId.value());
        }
        if (!stageCompatibleWithRole(stage, tote.role())) {
            throw new IllegalArgumentException("Assignment stage " + stage
                    + " is not valid for physical tote role " + tote.role());
        }
        if (activeAssignmentFor(orderSheetKey).isPresent()) {
            throw new IllegalStateException("Logical sheet already has an active physical tote assignment: "
                    + orderSheetKey);
        }
        if (exclusiveOnPhysicalTote(stage) && !activeAssignmentsFor(toteId).isEmpty()) {
            throw new IllegalStateException("Physical tote already has an active logical sheet assignment: "
                    + toteId.value());
        }

        PhysicalToteAssignment assignment = PhysicalToteAssignment.active(
                nextAssignmentSequence,
                orderSheetKey,
                toteId,
                stage,
                activationTime);
        assignments.add(assignment);
        nextAssignmentSequence++;
        return assignment;
    }

    public PhysicalToteAssignment terminateActiveAssignment(
            OrderSheetKey orderSheetKey,
            Duration terminationTime,
            PhysicalToteAssignmentEndReason reason) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }

        for (int i = 0; i < assignments.size(); i++) {
            PhysicalToteAssignment assignment = assignments.get(i);
            if (!assignment.orderSheetKey().equals(orderSheetKey) || !assignment.active()) {
                continue;
            }
            PhysicalToteAssignment terminated = assignment.terminate(terminationTime, reason);
            assignments.set(i, terminated);
            return terminated;
        }

        throw new IllegalStateException("Logical sheet has no active physical tote assignment: " + orderSheetKey);
    }

    public Optional<PhysicalToteRecord> tote(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        return Optional.ofNullable(totes.get(toteId));
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

    private PhysicalToteRecord requireTote(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        PhysicalToteRecord tote = totes.get(toteId);
        if (tote == null) {
            throw new IllegalArgumentException("Unknown physical tote: " + toteId.value());
        }
        return tote;
    }

    private static boolean exclusiveOnPhysicalTote(PhysicalToteAssignmentStage stage) {
        return stage == PhysicalToteAssignmentStage.INBOUND_PACK
                || stage == PhysicalToteAssignmentStage.PREPARATION
                || stage == PhysicalToteAssignmentStage.PRE_P2P;
    }

    private static boolean stageCompatibleWithRole(
            PhysicalToteAssignmentStage stage,
            PhysicalToteRole role) {
        return switch (role) {
            case INBOUND_PACK -> stage == PhysicalToteAssignmentStage.INBOUND_PACK
                    || stage == PhysicalToteAssignmentStage.PREPARATION
                    || stage == PhysicalToteAssignmentStage.PRE_P2P;
            case PRE_P2P -> stage == PhysicalToteAssignmentStage.PRE_P2P;
            case OUTBOUND_BAG -> stage == PhysicalToteAssignmentStage.OUTBOUND_BAG
                    || stage == PhysicalToteAssignmentStage.OUTBOUND;
        };
    }
}
