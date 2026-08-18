package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class Av02ToteLifecycleControllerTest {

    @Test
    void shouldAllocatePreP2pPhysicalToteForEmptyOrder() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId allocatedId = toteId("av02-1");
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> allocatedId);

        PhysicalToteRecord allocated = controller.allocateFor(
                order("order-1", OrderType.EMPTY),
                Duration.ofSeconds(2));

        assertEquals(allocatedId, allocated.id());
        assertEquals(PhysicalToteRole.PRE_P2P, allocated.role());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, allocated.state());
        assertEquals(allocated, ledger.tote(allocatedId).orElseThrow());
    }

    @Test
    void shouldCreateAssignmentWithoutInboundManifest() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        NotionalToteOrder order = order("order-1", OrderType.EMPTY);
        PhysicalToteId allocatedId = toteId("av02-1");
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> allocatedId);

        controller.allocateFor(order, Duration.ofSeconds(2));

        PhysicalToteAssignment assignment = ledger.activeAssignmentFor(order.orderSheetKey()).orElseThrow();
        assertEquals(allocatedId, assignment.physicalToteId());
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignment.stage());
        assertEquals(Duration.ofSeconds(2), assignment.activatedAt());
        assertEquals(PhysicalToteRole.PRE_P2P, ledger.tote(allocatedId).orElseThrow().role());
    }

    @Test
    void shouldRejectNonEmptyOrderType() {
        AtomicInteger allocations = new AtomicInteger();
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> toteId("av02-" + allocations.incrementAndGet()));

        assertThrows(IllegalArgumentException.class,
                () -> controller.allocateFor(
                        order("order-1", OrderType.FULL_PACK),
                        Duration.ZERO));

        assertEquals(0, allocations.get());
        assertTrue(ledger.snapshot().totes().isEmpty());
    }

    @Test
    void shouldRejectSecondActiveAllocationForSameLogicalSheet() {
        AtomicInteger allocations = new AtomicInteger();
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        NotionalToteOrder order = order("order-1", OrderType.EMPTY);
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> toteId("av02-" + allocations.incrementAndGet()));
        controller.allocateFor(order, Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> controller.allocateFor(order, Duration.ofSeconds(1)));

        assertEquals(1, allocations.get());
        assertEquals(1, ledger.snapshot().totes().size());
        assertEquals(1, ledger.assignmentHistoryFor(order.orderSheetKey()).size());
    }

    @Test
    void shouldRejectNullOrDuplicateAllocatedPhysicalId() {
        PhysicalToteLifecycleLedger nullIdLedger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController nullIdController = new Av02ToteLifecycleController(
                nullIdLedger,
                () -> null);

        assertThrows(IllegalArgumentException.class,
                () -> nullIdController.allocateFor(order("order-1", OrderType.EMPTY), Duration.ZERO));
        assertTrue(nullIdLedger.snapshot().totes().isEmpty());

        PhysicalToteLifecycleLedger duplicateIdLedger = new PhysicalToteLifecycleLedger();
        PhysicalToteId duplicateId = toteId("existing-1");
        duplicateIdLedger.register(PhysicalToteRecord.inboundPack(duplicateId));
        PhysicalToteLifecycleSnapshot before = duplicateIdLedger.snapshot();
        Av02ToteLifecycleController duplicateIdController = new Av02ToteLifecycleController(
                duplicateIdLedger,
                () -> duplicateId);

        assertThrows(IllegalArgumentException.class,
                () -> duplicateIdController.allocateFor(
                        order("order-2", OrderType.EMPTY),
                        Duration.ZERO));
        assertEquals(before, duplicateIdLedger.snapshot());
    }

    private static NotionalToteOrder order(String orderId, OrderType orderType) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                "104",
                1,
                orderType,
                List.of(new DspOrderItem("line-" + orderId, "product-1", 1)),
                0);
    }

    private static PhysicalToteId toteId(String value) {
        return new PhysicalToteId(value);
    }
}
