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

class PhysicalToteLifecycleLedgerTest {

    @Test
    void shouldRegisterAndTransitionPhysicalTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = toteId("inbound-1");
        ledger.register(PhysicalToteRecord.inboundPack(toteId));

        PhysicalToteRecord transitioned = ledger.transitionTote(
                toteId,
                PhysicalToteLifecycleState.ACTIVE_PRE_P2P);

        assertEquals(transitioned, ledger.tote(toteId).orElseThrow());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, transitioned.state());
    }

    @Test
    void shouldRejectDuplicatePhysicalToteIdentity() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = toteId("inbound-1");
        ledger.register(PhysicalToteRecord.inboundPack(toteId));

        assertThrows(IllegalArgumentException.class,
                () -> ledger.register(PhysicalToteRecord.inboundPack(toteId)));
    }

    @Test
    void shouldRejectAssignmentToUnknownOrTerminalTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();

        assertThrows(IllegalArgumentException.class,
                () -> ledger.assign(
                        sheet("ORDER-A", 1),
                        toteId("missing"),
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ZERO));

        PhysicalToteId consumedId = toteId("consumed-1");
        ledger.register(PhysicalToteRecord.inboundPack(consumedId));
        ledger.transitionTote(consumedId, PhysicalToteLifecycleState.CONSUMED_AT_ADAPTING);

        assertThrows(IllegalStateException.class,
                () -> ledger.assign(
                        sheet("ORDER-A", 1),
                        consumedId,
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ZERO));
    }

    @Test
    void shouldAllowOnlyOneActivePhysicalTotePerLogicalSheet() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId first = registerInbound(ledger, "inbound-1");
        PhysicalToteId second = registerInbound(ledger, "inbound-2");
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        ledger.assign(sheet, first, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> ledger.assign(sheet, second, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ofSeconds(1)));
    }

    @Test
    void shouldAllowOnlyOneLogicalSheetOnInboundTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = registerInbound(ledger, "inbound-1");
        ledger.assign(
                sheet("ORDER-A", 1),
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);

        assertThrows(IllegalStateException.class,
                () -> ledger.assign(
                        sheet("ORDER-B", 1),
                        toteId,
                        PhysicalToteAssignmentStage.PREPARATION,
                        Duration.ofSeconds(1)));
    }

    @Test
    void shouldAllowMultipleLogicalSheetsOnOutboundTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = toteId("outbound-1");
        ledger.register(PhysicalToteRecord.outboundBag(toteId));

        ledger.assign(
                sheet("ORDER-A", 1),
                toteId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ZERO);
        ledger.assign(
                sheet("ORDER-B", 1),
                toteId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(1));

        assertEquals(2, ledger.activeAssignmentsFor(toteId).size());
    }

    @Test
    void shouldPermitSequentialAssignmentsAfterTermination() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        PhysicalToteId inboundId = registerInbound(ledger, "inbound-1");
        PhysicalToteId outboundId = toteId("outbound-1");
        ledger.register(PhysicalToteRecord.outboundBag(outboundId));
        ledger.assign(sheet, inboundId, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ZERO);
        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(5),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);

        PhysicalToteAssignment outboundAssignment = ledger.assign(
                sheet,
                outboundId,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(6));

        assertTrue(outboundAssignment.active());
        assertEquals(outboundId, ledger.activeAssignmentFor(sheet).orElseThrow().physicalToteId());
    }

    @Test
    void shouldRetainAssignmentHistoryInSequenceOrder() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        PhysicalToteId inboundId = registerInbound(ledger, "inbound-1");
        PhysicalToteId outboundId = toteId("outbound-1");
        ledger.register(PhysicalToteRecord.outboundBag(outboundId));
        ledger.assign(sheet, inboundId, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ZERO);
        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(5),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.assign(sheet, outboundId, PhysicalToteAssignmentStage.OUTBOUND_BAG, Duration.ofSeconds(6));

        List<PhysicalToteAssignment> history = ledger.assignmentHistoryFor(sheet);

        assertEquals(List.of(0L, 1L), history.stream().map(PhysicalToteAssignment::sequenceNumber).toList());
        assertFalse(history.get(0).active());
        assertTrue(history.get(1).active());
        assertThrows(UnsupportedOperationException.class, () -> history.add(history.get(0)));
    }

    @Test
    void shouldRejectStageIncompatibleWithPhysicalToteRole() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId inboundId = registerInbound(ledger, "inbound-1");
        PhysicalToteId outboundId = toteId("outbound-1");
        ledger.register(PhysicalToteRecord.outboundBag(outboundId));

        assertThrows(IllegalArgumentException.class,
                () -> ledger.assign(
                        sheet("ORDER-A", 1),
                        inboundId,
                        PhysicalToteAssignmentStage.OUTBOUND_BAG,
                        Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.assign(
                        sheet("ORDER-B", 1),
                        outboundId,
                        PhysicalToteAssignmentStage.INBOUND_PACK,
                        Duration.ZERO));
    }

    private static PhysicalToteId registerInbound(PhysicalToteLifecycleLedger ledger, String value) {
        PhysicalToteId toteId = toteId(value);
        ledger.register(PhysicalToteRecord.inboundPack(toteId));
        return toteId;
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private static PhysicalToteId toteId(String value) {
        return new PhysicalToteId(value);
    }
}
