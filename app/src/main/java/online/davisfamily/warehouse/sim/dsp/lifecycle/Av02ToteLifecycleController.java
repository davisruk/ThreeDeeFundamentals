package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.time.Duration;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class Av02ToteLifecycleController {
    private final PhysicalToteLifecycleLedger ledger;
    private final PhysicalToteIdAllocator idAllocator;

    public Av02ToteLifecycleController(
            PhysicalToteLifecycleLedger ledger,
            PhysicalToteIdAllocator idAllocator) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger must not be null");
        }
        if (idAllocator == null) {
            throw new IllegalArgumentException("idAllocator must not be null");
        }
        this.ledger = ledger;
        this.idAllocator = idAllocator;
    }

    public PhysicalToteRecord allocateFor(
            NotionalToteOrder order,
            Duration allocationTime) {
        validateAllocationRequest(order, allocationTime);
        PhysicalToteId allocatedId = idAllocator.nextPhysicalToteId();
        return allocateValidated(order, allocationTime, allocatedId);
    }

    public PhysicalToteRecord allocateFor(
            NotionalToteOrder order,
            Duration allocationTime,
            PhysicalToteId allocatedId) {
        validateAllocationRequest(order, allocationTime);
        return allocateValidated(order, allocationTime, allocatedId);
    }

    public PhysicalToteRecord consumeAtP2p(
            OrderSheetKey orderSheetKey,
            PhysicalToteId physicalToteId,
            Duration consumptionTime) {
        PhysicalToteRecord physicalTote = validateConsumptionRequest(
                orderSheetKey,
                physicalToteId,
                consumptionTime);

        ledger.terminateActiveAssignment(
                orderSheetKey,
                consumptionTime,
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        return ledger.transitionTote(
                physicalToteId,
                PhysicalToteLifecycleState.CONSUMED_AT_P2P);
    }

    private void validateAllocationRequest(
            NotionalToteOrder order,
            Duration allocationTime) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.orderType() != OrderType.EMPTY) {
            throw new IllegalArgumentException("AV02 may allocate physical totes only for EMPTY orders");
        }
        requireNonNegative(allocationTime, "allocationTime");
        if (ledger.activeAssignmentFor(order.orderSheetKey()).isPresent()) {
            throw new IllegalStateException(
                    "Logical sheet already has an active physical tote assignment: "
                    + order.orderSheetKey());
        }
    }

    private PhysicalToteRecord validateConsumptionRequest(
            OrderSheetKey orderSheetKey,
            PhysicalToteId physicalToteId,
            Duration consumptionTime) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        requireNonNegative(consumptionTime, "consumptionTime");

        PhysicalToteRecord physicalTote = ledger.tote(physicalToteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown physical tote: " + physicalToteId.value()));
        if (!physicalTote.id().equals(physicalToteId)) {
            throw new IllegalStateException(
                    "Physical tote record does not match requested physical tote: "
                            + physicalToteId.value());
        }
        if (physicalTote.role() != PhysicalToteRole.PRE_P2P) {
            throw new IllegalStateException("P2P consumption requires a PRE_P2P physical tote");
        }
        if (physicalTote.state() != PhysicalToteLifecycleState.ACTIVE_PRE_P2P) {
            throw new IllegalStateException(
                    "AV02 tote cannot complete P2P from state " + physicalTote.state());
        }

        List<PhysicalToteAssignment> activeAssignments = ledger.activeAssignmentsFor(physicalToteId);
        if (activeAssignments.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one active assignment for physical tote "
                            + physicalToteId.value() + " but found " + activeAssignments.size());
        }
        PhysicalToteAssignment physicalAssignment = activeAssignments.getFirst();
        if (!physicalAssignment.orderSheetKey().equals(orderSheetKey)) {
            throw new IllegalStateException(
                    "Physical tote is assigned to a different logical sheet: "
                            + physicalAssignment.orderSheetKey());
        }
        if (!physicalAssignment.physicalToteId().equals(physicalToteId)) {
            throw new IllegalStateException(
                    "Active assignment does not match the requested physical tote: "
                            + physicalToteId.value());
        }
        if (physicalAssignment.stage() != PhysicalToteAssignmentStage.PRE_P2P) {
            throw new IllegalStateException(
                    "Active assignment must be at stage PRE_P2P but was "
                            + physicalAssignment.stage());
        }
        requireNotBeforeActivation(consumptionTime, physicalAssignment);

        PhysicalToteAssignment sheetAssignment = ledger.activeAssignmentFor(orderSheetKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Logical sheet has no active physical tote assignment: " + orderSheetKey));
        if (!sheetAssignment.equals(physicalAssignment)) {
            throw new IllegalStateException(
                    "Logical sheet assignment does not match the requested physical tote assignment");
        }
        return physicalTote;
    }

    private PhysicalToteRecord allocateValidated(
            NotionalToteOrder order,
            Duration allocationTime,
            PhysicalToteId allocatedId) {
        if (allocatedId == null) {
            throw new IllegalArgumentException("allocatedId must not be null");
        }
        if (ledger.tote(allocatedId).isPresent()) {
            throw new IllegalArgumentException(
                    "Physical tote is already registered: " + allocatedId.value());
        }

        PhysicalToteRecord allocatedTote = PhysicalToteRecord.preP2p(allocatedId);
        ledger.register(allocatedTote);
        ledger.assign(
                order.orderSheetKey(),
                allocatedId,
                PhysicalToteAssignmentStage.PRE_P2P,
                allocationTime);
        return allocatedTote;
    }

    private static void requireNonNegative(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireNotBeforeActivation(
            Duration operationTime,
            PhysicalToteAssignment assignment) {
        if (operationTime.compareTo(assignment.activatedAt()) < 0) {
            throw new IllegalArgumentException("Operation time must not precede assignment activation");
        }
    }
}
