package online.davisfamily.warehouse.sim.dsp.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void shouldReuseEmptyAndPopulatedSnapshotUntilLifecycleMutation() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();

        PhysicalToteLifecycleSnapshot emptySnapshot = ledger.snapshot();
        assertSame(emptySnapshot, ledger.snapshot());

        ledger.register(PhysicalToteRecord.inboundPack(toteId("inbound-1")));

        PhysicalToteLifecycleSnapshot populatedSnapshot = ledger.snapshot();
        assertNotSame(emptySnapshot, populatedSnapshot);
        assertSame(populatedSnapshot, ledger.snapshot());
    }

    @Test
    void shouldInvalidateSnapshotAfterEachSuccessfulLifecycleMutation() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteLifecycleSnapshot previousSnapshot = ledger.snapshot();
        PhysicalToteId toteId = toteId("inbound-1");
        OrderSheetKey sheet = sheet("ORDER-A", 1);

        ledger.register(PhysicalToteRecord.inboundPack(toteId));
        previousSnapshot = assertFreshSnapshot(ledger, previousSnapshot);

        ledger.assign(
                sheet,
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
        previousSnapshot = assertFreshSnapshot(ledger, previousSnapshot);

        ledger.transitionTote(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        previousSnapshot = assertFreshSnapshot(ledger, previousSnapshot);

        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(1),
                PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE);
        assertFreshSnapshot(ledger, previousSnapshot);
    }

    @Test
    void shouldKeepHistoricalSnapshotsUnchangedAfterTransitionAndTermination() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = registerInbound(ledger, "inbound-1");
        OrderSheetKey sheet = sheet("ORDER-A", 1);
        ledger.assign(
                sheet,
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);

        PhysicalToteLifecycleSnapshot beforeTransition = ledger.snapshot();
        ledger.transitionTote(toteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        PhysicalToteLifecycleSnapshot beforeTermination = ledger.snapshot();

        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(1),
                PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE);
        PhysicalToteLifecycleSnapshot afterTermination = ledger.snapshot();

        assertEquals(
                PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                beforeTransition.totes().get(toteId).state());
        assertTrue(beforeTransition.assignments().getFirst().active());
        assertEquals(
                PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                beforeTermination.totes().get(toteId).state());
        assertTrue(beforeTermination.assignments().getFirst().active());
        assertEquals(
                PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                afterTermination.totes().get(toteId).state());
        assertFalse(afterTermination.assignments().getFirst().active());
    }

    @Test
    void shouldPreserveCachedSnapshotAndAssignmentSequenceAfterRejectedMutations() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId toteId = registerInbound(ledger, "inbound-1");
        OrderSheetKey firstSheet = sheet("ORDER-A", 1);

        PhysicalToteLifecycleSnapshot registeredSnapshot = ledger.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> ledger.register(PhysicalToteRecord.inboundPack(toteId)));
        assertSame(registeredSnapshot, ledger.snapshot());

        assertThrows(IllegalStateException.class,
                () -> ledger.transitionTote(toteId, PhysicalToteLifecycleState.INBOUND_PACK_TOTE));
        assertSame(registeredSnapshot, ledger.snapshot());

        assertThrows(IllegalArgumentException.class,
                () -> ledger.assign(
                        firstSheet,
                        toteId,
                        PhysicalToteAssignmentStage.OUTBOUND_BAG,
                        Duration.ZERO));
        assertSame(registeredSnapshot, ledger.snapshot());

        PhysicalToteAssignment firstAssignment = ledger.assign(
                firstSheet,
                toteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
        assertEquals(0L, firstAssignment.sequenceNumber());
        PhysicalToteLifecycleSnapshot assignedSnapshot = ledger.snapshot();

        assertThrows(IllegalStateException.class,
                () -> ledger.assign(
                        sheet("ORDER-B", 1),
                        toteId,
                        PhysicalToteAssignmentStage.PREPARATION,
                        Duration.ofSeconds(1)));
        assertSame(assignedSnapshot, ledger.snapshot());

        assertThrows(IllegalStateException.class,
                () -> ledger.terminateActiveAssignment(
                        sheet("MISSING", 1),
                        Duration.ofSeconds(1),
                        PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P));
        assertSame(assignedSnapshot, ledger.snapshot());

        ledger.terminateActiveAssignment(
                firstSheet,
                Duration.ofSeconds(1),
                PhysicalToteAssignmentEndReason.ADVANCED_TO_NEXT_STAGE);
        PhysicalToteAssignment secondAssignment = ledger.assign(
                sheet("ORDER-B", 1),
                toteId,
                PhysicalToteAssignmentStage.PREPARATION,
                Duration.ofSeconds(2));
        assertEquals(1L, secondAssignment.sequenceNumber());
    }

    @Test
    void shouldBuildCompleteSnapshotAfterSeveralMutationsBeforeRead() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteLifecycleSnapshot initialSnapshot = ledger.snapshot();
        PhysicalToteId consumedToteId = toteId("inbound-1");
        PhysicalToteId untouchedToteId = toteId("inbound-2");
        OrderSheetKey sheet = sheet("ORDER-A", 1);

        ledger.register(PhysicalToteRecord.inboundPack(consumedToteId));
        ledger.register(PhysicalToteRecord.inboundPack(untouchedToteId));
        ledger.assign(
                sheet,
                consumedToteId,
                PhysicalToteAssignmentStage.INBOUND_PACK,
                Duration.ZERO);
        ledger.transitionTote(consumedToteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        ledger.terminateActiveAssignment(
                sheet,
                Duration.ofSeconds(1),
                PhysicalToteAssignmentEndReason.CONSUMED_AT_P2P);
        ledger.transitionTote(consumedToteId, PhysicalToteLifecycleState.CONSUMED_AT_P2P);

        PhysicalToteLifecycleSnapshot finalSnapshot = ledger.snapshot();

        assertNotSame(initialSnapshot, finalSnapshot);
        assertEquals(2, finalSnapshot.totes().size());
        assertEquals(
                PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                finalSnapshot.totes().get(consumedToteId).state());
        assertEquals(
                PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                finalSnapshot.totes().get(untouchedToteId).state());
        assertEquals(1, finalSnapshot.assignments().size());
        assertFalse(finalSnapshot.assignments().getFirst().active());
        assertSame(finalSnapshot, ledger.snapshot());
    }

    @Test
    void shouldKeepSnapshotCachesIndependentBetweenLedgers() {
        PhysicalToteLifecycleLedger firstLedger = new PhysicalToteLifecycleLedger();
        PhysicalToteLifecycleLedger secondLedger = new PhysicalToteLifecycleLedger();
        PhysicalToteLifecycleSnapshot firstEmptySnapshot = firstLedger.snapshot();
        PhysicalToteLifecycleSnapshot secondEmptySnapshot = secondLedger.snapshot();

        firstLedger.register(PhysicalToteRecord.inboundPack(toteId("first")));
        PhysicalToteLifecycleSnapshot firstPopulatedSnapshot = firstLedger.snapshot();

        assertNotSame(firstEmptySnapshot, firstPopulatedSnapshot);
        assertSame(firstPopulatedSnapshot, firstLedger.snapshot());
        assertSame(secondEmptySnapshot, secondLedger.snapshot());

        secondLedger.register(PhysicalToteRecord.inboundPack(toteId("second")));
        assertSame(firstPopulatedSnapshot, firstLedger.snapshot());
        assertNotSame(secondEmptySnapshot, secondLedger.snapshot());
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

    private static PhysicalToteLifecycleSnapshot assertFreshSnapshot(
            PhysicalToteLifecycleLedger ledger,
            PhysicalToteLifecycleSnapshot previousSnapshot) {
        PhysicalToteLifecycleSnapshot currentSnapshot = ledger.snapshot();
        assertNotSame(previousSnapshot, currentSnapshot);
        assertSame(currentSnapshot, ledger.snapshot());
        return currentSnapshot;
    }
}
