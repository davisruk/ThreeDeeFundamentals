package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.time.Duration;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
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

        PhysicalToteId allocatedId = idAllocator.nextPhysicalToteId();
        if (allocatedId == null) {
            throw new IllegalArgumentException("idAllocator returned null");
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
}
