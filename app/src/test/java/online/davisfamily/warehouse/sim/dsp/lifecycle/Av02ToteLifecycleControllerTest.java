package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
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

    @Test
    void shouldConsumeExactPreP2pAssignmentAtP2p() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        NotionalToteOrder order = order("order-1", OrderType.EMPTY);
        PhysicalToteId allocatedId = toteId("av02-1");
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> allocatedId);
        controller.allocateFor(order, Duration.ofSeconds(2), allocatedId);

        PhysicalToteRecord consumed = controller.consumeAtP2p(
                order.orderSheetKey(),
                allocatedId,
                Duration.ofSeconds(5));

        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P, consumed.state());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                ledger.tote(allocatedId).orElseThrow().state());
        assertTrue(ledger.activeAssignmentFor(order.orderSheetKey()).isEmpty());
        PhysicalToteAssignment assignment = ledger.assignmentHistoryFor(order.orderSheetKey())
                .getFirst();
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignment.stage());
        assertEquals(PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P,
                assignment.endReason().orElseThrow());
        assertEquals(Duration.ofSeconds(5), assignment.terminatedAt().orElseThrow());
    }

    @Test
    void shouldRejectInvalidP2pConsumptionWithoutMutatingLedger() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        NotionalToteOrder order = order("order-1", OrderType.EMPTY);
        PhysicalToteId allocatedId = toteId("av02-1");
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> allocatedId);
        controller.allocateFor(order, Duration.ofSeconds(2), allocatedId);

        PhysicalToteLifecycleSnapshot beforeTime = ledger.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(
                        order.orderSheetKey(), allocatedId, Duration.ofSeconds(1)));
        assertEquals(beforeTime, ledger.snapshot());

        PhysicalToteLifecycleSnapshot beforeSheet = ledger.snapshot();
        assertThrows(IllegalStateException.class,
                () -> controller.consumeAtP2p(
                        new online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey("other", 1),
                        allocatedId,
                        Duration.ofSeconds(5)));
        assertEquals(beforeSheet, ledger.snapshot());

        PhysicalToteLifecycleSnapshot beforeId = ledger.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(
                        order.orderSheetKey(), toteId("unknown"), Duration.ofSeconds(5)));
        assertEquals(beforeId, ledger.snapshot());

        PhysicalToteLifecycleSnapshot beforeNulls = ledger.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(null, allocatedId, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(order.orderSheetKey(), null, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(order.orderSheetKey(), allocatedId, null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.consumeAtP2p(
                        order.orderSheetKey(), allocatedId, Duration.ofSeconds(-1)));
        assertEquals(beforeNulls, ledger.snapshot());
    }

    @Test
    void shouldRejectP2pConsumptionWhenNoActiveAssignmentIsReturnedWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(List.of());

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectP2pConsumptionWhenMultipleActiveAssignmentsAreReturnedWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        PhysicalToteAssignment additionalAssignment = PhysicalToteAssignment.active(
                fixture.assignment().sequenceNumber() + 1,
                new OrderSheetKey("another-sheet", 1),
                fixture.physicalToteId(),
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(
                List.of(fixture.assignment(), additionalAssignment));

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectP2pConsumptionWhenReturnedAssignmentStageIsNotPreP2pWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        PhysicalToteAssignment malformedAssignment = PhysicalToteAssignment.active(
                fixture.assignment().sequenceNumber(),
                fixture.order().orderSheetKey(),
                fixture.physicalToteId(),
                PhysicalToteAssignmentStage.PREPARATION,
                fixture.assignment().activatedAt());
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(List.of(malformedAssignment));

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectP2pConsumptionWhenReturnedAssignmentHasDifferentPhysicalToteIdWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        PhysicalToteAssignment malformedAssignment = PhysicalToteAssignment.active(
                fixture.assignment().sequenceNumber(),
                fixture.order().orderSheetKey(),
                toteId("different-physical-tote"),
                PhysicalToteAssignmentStage.PRE_P2P,
                fixture.assignment().activatedAt());
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(List.of(malformedAssignment));

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectP2pConsumptionWhenSheetAssignmentIsAbsentWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(List.of(fixture.assignment()));
        fixture.ledger().overrideActiveAssignmentForSheet(Optional.empty());

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectP2pConsumptionWhenSheetAssignmentDiffersWithoutMutation() {
        AssignmentValidationFixture fixture = assignmentValidationFixture();
        PhysicalToteAssignment differentSheetAssignment = PhysicalToteAssignment.active(
                fixture.assignment().sequenceNumber() + 1,
                fixture.assignment().orderSheetKey(),
                fixture.assignment().physicalToteId(),
                fixture.assignment().stage(),
                fixture.assignment().activatedAt());
        fixture.ledger().overrideActiveAssignmentsForPhysicalTote(List.of(fixture.assignment()));
        fixture.ledger().overrideActiveAssignmentForSheet(Optional.of(differentSheetAssignment));

        assertRejectsMalformedAssignmentWithoutMutation(fixture);
    }

    @Test
    void shouldRejectWrongRoleOrStateAndRepeatedP2pConsumptionWithoutMutation() {
        PhysicalToteLifecycleLedger wrongRoleLedger = new PhysicalToteLifecycleLedger();
        PhysicalToteId inboundId = toteId("inbound-1");
        wrongRoleLedger.register(PhysicalToteRecord.inboundPack(inboundId));
        NotionalToteOrder wrongRoleOrder = order("wrong-role", OrderType.EMPTY);
        wrongRoleLedger.assign(
                wrongRoleOrder.orderSheetKey(),
                inboundId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
        Av02ToteLifecycleController wrongRoleController = new Av02ToteLifecycleController(
                wrongRoleLedger,
                () -> inboundId);
        PhysicalToteLifecycleSnapshot wrongRoleBefore = wrongRoleLedger.snapshot();
        assertThrows(IllegalStateException.class,
                () -> wrongRoleController.consumeAtP2p(
                        wrongRoleOrder.orderSheetKey(), inboundId, Duration.ofSeconds(1)));
        assertEquals(wrongRoleBefore, wrongRoleLedger.snapshot());

        PhysicalToteLifecycleLedger wrongStateLedger = new PhysicalToteLifecycleLedger();
        PhysicalToteId wrongStateId = toteId("inbound-2");
        wrongStateLedger.register(PhysicalToteRecord.preP2p(wrongStateId));
        NotionalToteOrder wrongStateOrder = order("wrong-state", OrderType.EMPTY);
        wrongStateLedger.assign(
                wrongStateOrder.orderSheetKey(),
                wrongStateId,
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);
        wrongStateLedger.transitionTote(
                wrongStateId,
                PhysicalToteLifecycleState.CONSUMED_AT_P2P);
        Av02ToteLifecycleController wrongStateController = new Av02ToteLifecycleController(
                wrongStateLedger,
                () -> wrongStateId);
        PhysicalToteLifecycleSnapshot wrongStateBefore = wrongStateLedger.snapshot();
        assertThrows(IllegalStateException.class,
                () -> wrongStateController.consumeAtP2p(
                        wrongStateOrder.orderSheetKey(), wrongStateId, Duration.ofSeconds(1)));
        assertEquals(wrongStateBefore, wrongStateLedger.snapshot());

        PhysicalToteLifecycleLedger repeatedLedger = new PhysicalToteLifecycleLedger();
        NotionalToteOrder repeatedOrder = order("repeated", OrderType.EMPTY);
        PhysicalToteId repeatedId = toteId("av02-repeated");
        Av02ToteLifecycleController repeatedController = new Av02ToteLifecycleController(
                repeatedLedger,
                () -> repeatedId);
        repeatedController.allocateFor(repeatedOrder, Duration.ZERO, repeatedId);
        repeatedController.consumeAtP2p(repeatedOrder.orderSheetKey(), repeatedId, Duration.ZERO);
        PhysicalToteLifecycleSnapshot afterFirst = repeatedLedger.snapshot();

        assertThrows(IllegalStateException.class,
                () -> repeatedController.consumeAtP2p(
                        repeatedOrder.orderSheetKey(), repeatedId, Duration.ofSeconds(1)));
        assertEquals(afterFirst, repeatedLedger.snapshot());
    }

    private static AssignmentValidationFixture assignmentValidationFixture() {
        MalformedAssignmentLedger ledger = new MalformedAssignmentLedger();
        NotionalToteOrder order = order("malformed-assignment", OrderType.EMPTY);
        PhysicalToteId physicalToteId = toteId("av02-malformed");
        Av02ToteLifecycleController controller = new Av02ToteLifecycleController(
                ledger,
                () -> physicalToteId);
        controller.allocateFor(order, Duration.ZERO, physicalToteId);
        PhysicalToteAssignment assignment = ledger.activeAssignmentFor(order.orderSheetKey())
                .orElseThrow();
        return new AssignmentValidationFixture(order, physicalToteId, ledger, controller, assignment);
    }

    private static void assertRejectsMalformedAssignmentWithoutMutation(
            AssignmentValidationFixture fixture) {
        PhysicalToteLifecycleSnapshot before = fixture.ledger().snapshot();

        assertThrows(IllegalStateException.class,
                () -> fixture.controller().consumeAtP2p(
                        fixture.order().orderSheetKey(),
                        fixture.physicalToteId(),
                        Duration.ofSeconds(1)));

        assertEquals(before, fixture.ledger().snapshot());
    }

    private static final class MalformedAssignmentLedger extends PhysicalToteLifecycleLedger {
        private List<PhysicalToteAssignment> activeAssignmentsForPhysicalTote;
        private Optional<PhysicalToteAssignment> activeAssignmentForSheet;

        private void overrideActiveAssignmentsForPhysicalTote(
                List<PhysicalToteAssignment> assignments) {
            activeAssignmentsForPhysicalTote = List.copyOf(assignments);
        }

        private void overrideActiveAssignmentForSheet(
                Optional<PhysicalToteAssignment> assignment) {
            activeAssignmentForSheet = assignment;
        }

        @Override
        public List<PhysicalToteAssignment> activeAssignmentsFor(PhysicalToteId toteId) {
            if (activeAssignmentsForPhysicalTote != null) {
                return activeAssignmentsForPhysicalTote;
            }
            return super.activeAssignmentsFor(toteId);
        }

        @Override
        public Optional<PhysicalToteAssignment> activeAssignmentFor(OrderSheetKey orderSheetKey) {
            if (activeAssignmentForSheet != null) {
                return activeAssignmentForSheet;
            }
            return super.activeAssignmentFor(orderSheetKey);
        }
    }

    private record AssignmentValidationFixture(
            NotionalToteOrder order,
            PhysicalToteId physicalToteId,
            MalformedAssignmentLedger ledger,
            Av02ToteLifecycleController controller,
            PhysicalToteAssignment assignment) {
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
