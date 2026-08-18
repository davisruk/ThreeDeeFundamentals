package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class LogicalPhysicalIdentityScenarioTest {

    @Test
    void shouldRetainIdentityAcrossInboundConsumptionAndOutboundSubstitution() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey orderAFirstSheet = sheet("ORDER-A", 1);
        PhysicalToteId inboundId = toteId("INBOUND-100");
        PhysicalToteId outboundId = toteId("OUTBOUND-900");
        ledger.register(PhysicalToteRecord.inboundPack(inboundId));
        ledger.register(PhysicalToteRecord.outboundBag(outboundId));

        ledger.assign(
                orderAFirstSheet,
                inboundId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
        ledger.terminateActiveAssignment(
                orderAFirstSheet,
                Duration.ofSeconds(10),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.transitionTote(inboundId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        ledger.transitionTote(inboundId, PhysicalToteLifecycleState.CONSUMED_AT_P2P);
        ledger.assign(
                orderAFirstSheet,
                outboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(11));

        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();
        List<PhysicalToteAssignment> history = snapshot.assignmentHistoryFor(orderAFirstSheet);

        assertEquals(2, history.size());
        assertEquals(List.of(inboundId, outboundId),
                history.stream().map(PhysicalToteAssignment::physicalToteId).toList());
        assertFalse(history.get(0).active());
        assertTrue(history.get(1).active());
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                snapshot.totes().get(inboundId).state());
        assertEquals(orderAFirstSheet,
                snapshot.activeAssignmentFor(orderAFirstSheet).orElseThrow().orderSheetKey());
        assertFalse(orderAFirstSheet.orderId().equals(outboundId.value()));
    }

    @Test
    void shouldAggregateDifferentLogicalSheetsIntoOneOutboundTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId outboundId = toteId("OUTBOUND-900");
        OrderSheetKey orderAFirstSheet = sheet("ORDER-A", 1);
        OrderSheetKey orderBFirstSheet = sheet("ORDER-B", 1);
        ledger.register(PhysicalToteRecord.outboundBag(outboundId));

        ledger.assign(
                orderAFirstSheet,
                outboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ZERO);
        ledger.assign(
                orderBFirstSheet,
                outboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(1));

        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();

        assertEquals(List.of(orderAFirstSheet, orderBFirstSheet),
                snapshot.activeAssignmentsFor(outboundId).stream()
                        .map(PhysicalToteAssignment::orderSheetKey)
                        .toList());
    }

    @Test
    void shouldRequireDifferentSheetForConcurrentOutputAssignment() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId firstOutboundId = toteId("OUTBOUND-900");
        PhysicalToteId secondOutboundId = toteId("OUTBOUND-901");
        OrderSheetKey orderAFirstSheet = sheet("ORDER-A", 1);
        OrderSheetKey orderASecondSheet = sheet("ORDER-A", 2);
        ledger.register(PhysicalToteRecord.outboundBag(firstOutboundId));
        ledger.register(PhysicalToteRecord.outboundBag(secondOutboundId));
        ledger.assign(
                orderAFirstSheet,
                firstOutboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> ledger.assign(
                        orderAFirstSheet,
                        secondOutboundId,
                        PhysicalToteAssignmentStage.OUTBOUND_BAG,
                        Duration.ofSeconds(1)));

        ledger.assign(
                orderASecondSheet,
                secondOutboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(1));

        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();
        assertEquals(firstOutboundId,
                snapshot.activeAssignmentFor(orderAFirstSheet).orElseThrow().physicalToteId());
        assertEquals(secondOutboundId,
                snapshot.activeAssignmentFor(orderASecondSheet).orElseThrow().physicalToteId());
        assertEquals(2, snapshot.assignments().size());
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private static PhysicalToteId toteId(String value) {
        return new PhysicalToteId(value);
    }
}
