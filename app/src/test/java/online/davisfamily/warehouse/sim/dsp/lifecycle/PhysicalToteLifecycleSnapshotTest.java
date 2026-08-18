package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class PhysicalToteLifecycleSnapshotTest {

    @Test
    void shouldDefensivelyCopyTotesAndAssignments() {
        PhysicalToteId toteId = toteId("tote-1");
        LinkedHashMap<PhysicalToteId, PhysicalToteRecord> sourceTotes = new LinkedHashMap<>();
        sourceTotes.put(toteId, PhysicalToteRecord.inboundPack(toteId));
        ArrayList<PhysicalToteAssignment> sourceAssignments = new ArrayList<>();
        sourceAssignments.add(assignment(0, sheet("ORDER-A", 1), toteId));

        PhysicalToteLifecycleSnapshot snapshot = new PhysicalToteLifecycleSnapshot(
                sourceTotes,
                sourceAssignments);
        sourceTotes.clear();
        sourceAssignments.clear();

        assertEquals(1, snapshot.totes().size());
        assertEquals(1, snapshot.assignments().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.totes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.assignments().clear());
    }

    @Test
    void shouldPreserveDeterministicToteAndAssignmentOrder() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId firstTote = toteId("tote-1");
        PhysicalToteId secondTote = toteId("tote-2");
        ledger.register(PhysicalToteRecord.outboundBag(firstTote));
        ledger.register(PhysicalToteRecord.outboundBag(secondTote));
        ledger.assign(
                sheet("ORDER-A", 1),
                firstTote,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ZERO);
        ledger.assign(
                sheet("ORDER-B", 1),
                secondTote,
                PhysicalToteAssignmentStage.OUTBOUND_BAG,
                Duration.ofSeconds(1));

        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();

        assertEquals(List.of(firstTote, secondTote), new ArrayList<>(snapshot.totes().keySet()));
        assertEquals(List.of(0L, 1L),
                snapshot.assignments().stream().map(PhysicalToteAssignment::sequenceNumber).toList());
    }

    @Test
    void shouldExposeActiveAssignmentsAndHistory() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId inboundTote = toteId("inbound-1");
        PhysicalToteId outboundTote = toteId("outbound-1");
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        ledger.register(PhysicalToteRecord.inboundPack(inboundTote));
        ledger.register(PhysicalToteRecord.outboundBag(outboundTote));
        ledger.assign(sheet, inboundTote, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ZERO);
        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(5),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.assign(sheet, outboundTote, PhysicalToteAssignmentStage.OUTBOUND_BAG, Duration.ofSeconds(6));

        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();

        assertEquals(outboundTote, snapshot.activeAssignmentFor(sheet).orElseThrow().physicalToteId());
        assertEquals(1, snapshot.activeAssignmentsFor(outboundTote).size());
        assertEquals(2, snapshot.assignmentHistoryFor(sheet).size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.assignmentHistoryFor(sheet).clear());
    }

    @Test
    void shouldRemainUnchangedAfterLedgerAdvances() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = toteId("inbound-1");
        ledger.register(PhysicalToteRecord.inboundPack(toteId));
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        ledger.assign(sheet, toteId, PhysicalToteAssignmentStage.INBOUND_PACK, Duration.ZERO);
        PhysicalToteLifecycleSnapshot snapshot = ledger.snapshot();

        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(5),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.transitionTote(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);

        assertTrue(snapshot.activeAssignmentFor(sheet).orElseThrow().active());
        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                snapshot.totes().get(toteId).state());
    }

    @Test
    void shouldRejectNullSnapshotContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteLifecycleSnapshot(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteLifecycleSnapshot(Map.of(), null));

        LinkedHashMap<PhysicalToteId, PhysicalToteRecord> nullKey = new LinkedHashMap<>();
        nullKey.put(null, PhysicalToteRecord.inboundPack(toteId("tote-1")));
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteLifecycleSnapshot(nullKey, List.of()));

        ArrayList<PhysicalToteAssignment> nullAssignment = new ArrayList<>();
        nullAssignment.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new PhysicalToteLifecycleSnapshot(Map.of(), nullAssignment));
    }

    private static PhysicalToteAssignment assignment(
            long sequenceNumber,
            OrderSheetKey sheet,
            PhysicalToteId toteId) {
        return PhysicalToteAssignment.active(
                sequenceNumber,
                sheet,
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private static PhysicalToteId toteId(String value) {
        return new PhysicalToteId(value);
    }
}
