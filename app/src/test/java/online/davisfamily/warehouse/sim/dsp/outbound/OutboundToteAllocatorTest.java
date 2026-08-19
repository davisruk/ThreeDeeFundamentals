package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentEndReason;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutboundToteAllocatorTest {
    private static final P2pLineId LINE = new P2pLineId("p2p-1");

    @Test
    void shouldOpenOutboundToteAndAssignFirstBagIdentity() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(3, sourceSheet);
        PlannedBag bag = bag("rx-1", 1, "SC-1", "pharmacy-1", "patient-1", sourceSheet);

        AllocatedOutboundBag allocated = fixture.allocator().allocate(LINE, bag, seconds(1));
        OutboundToteSnapshot openTote = fixture.allocator().snapshot().openToteFor(LINE).orElseThrow();

        assertEquals("outbound-p2p-1-1", openTote.physicalToteId().value());
        assertEquals("SC-1", openTote.serviceCentreId().orElseThrow());
        assertEquals("pharmacy-1", openTote.pharmacyId().orElseThrow());
        assertEquals(List.of(allocated), openTote.allocatedBags());
        assertEquals(PhysicalToteLifecycleState.OUTBOUND_BAG_TOTE,
                fixture.ledger().tote(openTote.physicalToteId()).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND_BAG,
                fixture.ledger().activeAssignmentFor(sourceSheet).orElseThrow().stage());
    }

    @Test
    void shouldAggregateSeveralLogicalSheetsIntoOnePureOutboundTote() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        Fixture fixture = fixture(4, firstSheet, secondSheet, thirdSheet);

        fixture.allocator().allocate(
                LINE,
                bag("rx-1", 1, "SC-1", "pharmacy-1", "patient-1", firstSheet),
                seconds(1));
        fixture.allocator().allocate(
                LINE,
                bag("rx-2", 1, "SC-1", "pharmacy-1", "patient-2", secondSheet, thirdSheet),
                seconds(2));

        OutboundToteSnapshot tote = fixture.allocator().snapshot().openToteFor(LINE).orElseThrow();
        assertEquals(2, tote.bagCount());
        assertEquals(3, fixture.ledger().activeAssignmentsFor(tote.physicalToteId()).size());
        assertEquals(List.of(firstSheet, secondSheet, thirdSheet),
                fixture.ledger().activeAssignmentsFor(tote.physicalToteId()).stream()
                        .map(assignment -> assignment.orderSheetKey())
                        .toList());
    }

    @Test
    void shouldCloseAtConfiguredBagCapacityAndOpenAnotherForLaterBag() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        Fixture fixture = fixture(2, firstSheet, secondSheet, thirdSheet);

        fixture.allocator().allocate(LINE, bag("rx-1", firstSheet), seconds(1));
        fixture.allocator().allocate(LINE, bag("rx-2", secondSheet), seconds(2));
        fixture.allocator().allocate(LINE, bag("rx-3", thirdSheet), seconds(3));

        OutboundAllocationSnapshot snapshot = fixture.allocator().snapshot();
        assertEquals(1, snapshot.closedTotes().size());
        assertEquals(OutboundToteClosureReason.CAPACITY_REACHED,
                snapshot.closedTotes().getFirst().closureReason().orElseThrow());
        assertEquals("outbound-p2p-1-1", snapshot.closedTotes().getFirst().physicalToteId().value());
        assertEquals("outbound-p2p-1-2", snapshot.openToteFor(LINE).orElseThrow().physicalToteId().value());
    }

    @Test
    void shouldCloseBeforeAcceptingDifferentPharmacyOrServiceCentre() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        Fixture fixture = fixture(5, firstSheet, secondSheet, thirdSheet);

        fixture.allocator().allocate(
                LINE,
                bag("rx-1", 1, "SC-1", "pharmacy-1", "patient-1", firstSheet),
                seconds(1));
        fixture.allocator().allocate(
                LINE,
                bag("rx-2", 1, "SC-1", "pharmacy-2", "patient-2", secondSheet),
                seconds(2));
        fixture.allocator().allocate(
                LINE,
                bag("rx-3", 1, "SC-2", "pharmacy-2", "patient-3", thirdSheet),
                seconds(3));

        OutboundAllocationSnapshot snapshot = fixture.allocator().snapshot();
        assertEquals(
                List.of(
                        OutboundToteClosureReason.PHARMACY_CHANGED,
                        OutboundToteClosureReason.SERVICE_CENTRE_CHANGED),
                snapshot.closedTotes().stream()
                        .map(tote -> tote.closureReason().orElseThrow())
                        .toList());
        OutboundToteSnapshot current = snapshot.openToteFor(LINE).orElseThrow();
        assertEquals("SC-2", current.serviceCentreId().orElseThrow());
        assertEquals("pharmacy-2", current.pharmacyId().orElseThrow());
    }

    @Test
    void shouldNeverReuseInboundPhysicalToteAsOutboundTote() {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId inboundToteId = new PhysicalToteId("inbound-1");
        ledger.register(PhysicalToteRecord.inboundPack(inboundToteId));
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                ignored -> inboundToteId,
                new OutputSheetAllocator(List.of(sourceSheet)),
                new OutboundToteConfig(3));

        assertThrows(
                IllegalStateException.class,
                () -> allocator.allocate(LINE, bag("rx-1", sourceSheet), seconds(1)));
        assertTrue(allocator.snapshot().openTotesByLine().isEmpty());
        assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE,
                ledger.tote(inboundToteId).orElseThrow().state());
    }

    @Test
    void shouldAdvanceClosedToteAndAssignmentsToOutboundLifecycle() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(1, sourceSheet);

        fixture.allocator().allocate(LINE, bag("rx-1", sourceSheet), seconds(1));

        OutboundToteSnapshot closedTote = fixture.allocator().snapshot().closedTotes().getFirst();
        assertEquals(PhysicalToteLifecycleState.OUTBOUND,
                fixture.ledger().tote(closedTote.physicalToteId()).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                fixture.ledger().activeAssignmentFor(sourceSheet).orElseThrow().stage());
        assertEquals(2, fixture.ledger().assignmentHistoryFor(sourceSheet).size());
        assertEquals(PhysicalToteAssignmentEndReason.OUTBOUND_TOTE_CLOSED,
                fixture.ledger().assignmentHistoryFor(sourceSheet).getFirst().endReason().orElseThrow());
    }

    @Test
    void shouldKeepSamePatientTogetherOnlyWhileCapacityAllows() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        Fixture fixture = fixture(2, firstSheet, secondSheet, thirdSheet);

        fixture.allocator().allocate(
                LINE, bag("rx-1", 1, "SC-1", "pharmacy-1", "patient-1", firstSheet), seconds(1));
        fixture.allocator().allocate(
                LINE, bag("rx-2", 1, "SC-1", "pharmacy-1", "patient-1", secondSheet), seconds(2));
        fixture.allocator().allocate(
                LINE, bag("rx-3", 1, "SC-1", "pharmacy-1", "patient-1", thirdSheet), seconds(3));

        OutboundAllocationSnapshot snapshot = fixture.allocator().snapshot();
        assertEquals(2, snapshot.closedTotes().getFirst().bagCount());
        assertTrue(snapshot.closedTotes().getFirst().containsPatient("patient-1"));
        assertEquals(1, snapshot.openToteFor(LINE).orElseThrow().bagCount());
        assertTrue(snapshot.openToteFor(LINE).orElseThrow().containsPatient("patient-1"));
        assertNotEquals(
                snapshot.closedTotes().getFirst().physicalToteId(),
                snapshot.openToteFor(LINE).orElseThrow().physicalToteId());
    }

    @Test
    void shouldRejectDuplicateBagAllocation() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(3, sourceSheet);
        PlannedBag bag = bag("rx-1", sourceSheet);
        fixture.allocator().allocate(LINE, bag, seconds(1));

        assertThrows(
                IllegalStateException.class,
                () -> fixture.allocator().allocate(LINE, bag, seconds(2)));
        assertEquals(1, fixture.allocator().snapshot().allocatedBags().size());
    }

    @Test
    void shouldCloseExplicitlyAndRemainIdempotentWhenLineIsIdle() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(3, sourceSheet);
        fixture.allocator().allocate(LINE, bag("rx-1", sourceSheet), seconds(1));

        OutboundToteSnapshot closed = fixture.allocator()
                .closeForApplicableWorkCompletion(LINE, seconds(2))
                .orElseThrow();

        assertEquals(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE,
                closed.closureReason().orElseThrow());
        assertFalse(fixture.allocator().closeForHardCutoff(LINE, seconds(3)).isPresent());
        assertFalse(fixture.allocator().closeForServiceCentreChange(LINE, seconds(3)).isPresent());
    }

    private static Fixture fixture(int capacity, OrderSheetKey... knownSheets) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        return new Fixture(
                ledger,
                new OutboundToteAllocator(
                        ledger,
                        new DeterministicOutboundToteIdSource(),
                        new OutputSheetAllocator(List.of(knownSheets)),
                        new OutboundToteConfig(capacity)));
    }

    private static PlannedBag bag(String prescriptionId, OrderSheetKey... owningSheets) {
        return bag(prescriptionId, 1, "SC-1", "pharmacy-1", "patient-1", owningSheets);
    }

    private static PlannedBag bag(
            String prescriptionId,
            int bagOrdinal,
            String serviceCentreId,
            String pharmacyId,
            String patientId,
            OrderSheetKey... owningSheets) {
        return new PlannedBag(
                new BagKey(prescriptionId, bagOrdinal),
                serviceCentreId,
                pharmacyId,
                patientId,
                prescriptionId,
                List.of("pack-" + prescriptionId + "-" + bagOrdinal),
                Arrays.asList(owningSheets));
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private static Duration seconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }

    private record Fixture(
            PhysicalToteLifecycleLedger ledger,
            OutboundToteAllocator allocator) {
    }
}
