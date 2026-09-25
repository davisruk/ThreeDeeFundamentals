package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
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
                fixture.ledger().activeAssignmentFor(sheet("order-1", 101)).orElseThrow().stage());
        assertTrue(fixture.ledger().activeAssignmentFor(sourceSheet).isEmpty());
        assertEquals(sourceSheet, allocated.outputSheetAllocations().getFirst().sourceOwningSheetKey());
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
        assertEquals(List.of(
                        sheet("order-1", 101),
                        sheet("order-2", 101),
                        sheet("order-3", 101)),
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
        OutboundAllocationSnapshot afterFirstAllocation = fixture.allocator().snapshot();
        fixture.allocator().allocate(
                LINE,
                bag("rx-2", 1, "SC-1", "pharmacy-2", "patient-2", secondSheet),
                seconds(2));
        OutboundAllocationSnapshot afterPharmacyChange = fixture.allocator().snapshot();
        fixture.allocator().allocate(
                LINE,
                bag("rx-3", 1, "SC-2", "pharmacy-2", "patient-3", thirdSheet),
                seconds(3));

        OutboundAllocationSnapshot snapshot = fixture.allocator().snapshot();
        assertNotSame(afterFirstAllocation, afterPharmacyChange);
        assertNotSame(afterPharmacyChange, snapshot);
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
    void shouldAllocateWhileInboundSourceRemainsAssignedAndKeepOrdinalAcrossOutboundTotes() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId inboundToteId = new PhysicalToteId("inbound-source");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.inboundPack(inboundToteId));
        ledger.transitionTote(inboundToteId, PhysicalToteLifecycleState.ACTIVE_PRE_P2P);
        ledger.assign(sourceSheet, inboundToteId, PhysicalToteAssignmentStage.PRE_P2P, seconds(1));
        PhysicalToteAssignment sourceAssignment = ledger.activeAssignmentFor(sourceSheet).orElseThrow();
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(sourceSheet)),
                new OutboundToteConfig(1));
        PlannedBag firstBag = bag("rx-1", 1, "SC-1", "pharmacy-1", "patient-1", sourceSheet);
        PlannedBag secondBag = bag("rx-1", 2, "SC-1", "pharmacy-1", "patient-1", sourceSheet);

        AllocatedOutboundBag first = allocator.allocate(LINE, firstBag, seconds(2));
        AllocatedOutboundBag second = allocator.allocate(LINE, secondBag, seconds(3));

        assertSame(firstBag, first.plannedBag());
        assertSame(secondBag, second.plannedBag());
        assertEquals(sheet("order-1", 101), first.outputSheetAllocations().getFirst().outputSheetKey());
        assertEquals(sheet("order-1", 102), second.outputSheetAllocations().getFirst().outputSheetKey());
        assertNotEquals(first.outboundPhysicalToteId(), second.outboundPhysicalToteId());
        assertEquals(sourceAssignment, ledger.activeAssignmentFor(sourceSheet).orElseThrow());
        assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                ledger.tote(inboundToteId).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                ledger.activeAssignmentFor(sheet("order-1", 101)).orElseThrow().stage());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                ledger.activeAssignmentFor(sheet("order-1", 102)).orElseThrow().stage());
        assertEquals(List.of(sourceSheet), first.outputSheetAllocations().stream()
                .map(OutputSheetAllocation::sourceOwningSheetKey)
                .toList());
        assertEquals(firstBag.physicalPackIds(), first.plannedBag().physicalPackIds());
    }

    @Test
    void shouldAdvanceClosedToteAndAssignmentsToOutboundLifecycle() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OrderSheetKey outputSheet = sheet("order-1", 101);
        Fixture fixture = fixture(1, sourceSheet);

        fixture.allocator().allocate(LINE, bag("rx-1", sourceSheet), seconds(1));

        OutboundToteSnapshot closedTote = fixture.allocator().snapshot().closedTotes().getFirst();
        assertEquals(PhysicalToteLifecycleState.OUTBOUND,
                fixture.ledger().tote(closedTote.physicalToteId()).orElseThrow().state());
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                fixture.ledger().activeAssignmentFor(outputSheet).orElseThrow().stage());
        assertEquals(2, fixture.ledger().assignmentHistoryFor(outputSheet).size());
        assertEquals(PhysicalToteAssignmentEndReason.OUTBOUND_TOTE_CLOSED,
                fixture.ledger().assignmentHistoryFor(outputSheet).getFirst().endReason().orElseThrow());
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
        OutboundAllocationSnapshot beforeClose = fixture.allocator().snapshot();

        OutboundToteSnapshot closed = fixture.allocator()
                .closeForApplicableWorkCompletion(LINE, seconds(2))
                .orElseThrow();
        OutboundAllocationSnapshot afterClose = fixture.allocator().snapshot();

        assertEquals(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE,
                closed.closureReason().orElseThrow());
        assertNotSame(beforeClose, afterClose);
        assertSame(afterClose, fixture.allocator().snapshot());
        assertFalse(fixture.allocator().closeForHardCutoff(LINE, seconds(3)).isPresent());
        assertFalse(fixture.allocator().closeForServiceCentreChange(LINE, seconds(3)).isPresent());
        assertSame(afterClose, fixture.allocator().snapshot());
    }

    @Test
    void shouldReuseSnapshotsUntilEachGenuineOutboundMutation() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        Fixture fixture = fixture(3, firstSheet, secondSheet, thirdSheet);

        OutboundAllocationSnapshot initial = fixture.allocator().snapshot();
        assertSame(initial, fixture.allocator().snapshot());

        fixture.allocator().allocate(LINE, bag("rx-1", firstSheet), seconds(1));
        OutboundAllocationSnapshot afterFirstAllocation = fixture.allocator().snapshot();
        assertNotSame(initial, afterFirstAllocation);
        assertSame(afterFirstAllocation, fixture.allocator().snapshot());

        fixture.allocator().allocate(LINE, bag("rx-2", secondSheet), seconds(2));
        OutboundAllocationSnapshot afterOrdinaryAllocation = fixture.allocator().snapshot();
        assertNotSame(afterFirstAllocation, afterOrdinaryAllocation);

        fixture.allocator().closeForApplicableWorkCompletion(LINE, seconds(3));
        OutboundAllocationSnapshot afterExplicitClosure = fixture.allocator().snapshot();
        assertNotSame(afterOrdinaryAllocation, afterExplicitClosure);

        fixture.allocator().allocate(LINE, bag("rx-3", thirdSheet), seconds(4));
        OutboundAllocationSnapshot afterNewOpen = fixture.allocator().snapshot();
        assertNotSame(afterExplicitClosure, afterNewOpen);
        assertEquals(1, afterNewOpen.openToteFor(LINE).orElseThrow().bagCount());
        assertEquals(1, afterFirstAllocation.openToteFor(LINE).orElseThrow().bagCount());
        assertEquals(1, afterFirstAllocation.allocatedBags().size());

        assertThrows(IllegalStateException.class,
                () -> fixture.allocator().allocate(LINE, bag("rx-3", thirdSheet), seconds(5)));
        assertSame(afterNewOpen, fixture.allocator().snapshot());

        Fixture other = fixture(3, firstSheet);
        assertNotSame(afterNewOpen, other.allocator().snapshot());
    }

    @Test
    void shouldRefreshSnapshotsAfterCapacityClosureAndRetainPartialAllocationOnLaterFailure() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        Fixture fixture = fixture(1, firstSheet, secondSheet);

        OutboundAllocationSnapshot initial = fixture.allocator().snapshot();
        fixture.allocator().allocate(LINE, bag("rx-1", firstSheet), seconds(1));
        OutboundAllocationSnapshot afterCapacityClosure = fixture.allocator().snapshot();

        assertNotSame(initial, afterCapacityClosure);
        assertEquals(1, afterCapacityClosure.closedTotes().size());
        assertTrue(afterCapacityClosure.openToteFor(LINE).isEmpty());
        assertSame(afterCapacityClosure, fixture.allocator().snapshot());

        FailingLifecycleLedger ledger = new FailingLifecycleLedger();
        ledger.failOnTransition = true;
        OutboundToteAllocator failingAllocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(firstSheet)),
                new OutboundToteConfig(1));
        OutboundAllocationSnapshot beforeAllocation = failingAllocator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> failingAllocator.allocate(LINE, bag("rx-2", firstSheet), seconds(1)));
        OutboundAllocationSnapshot afterPartialAllocation = failingAllocator.snapshot();

        assertNotSame(beforeAllocation, afterPartialAllocation);
        assertEquals(1, afterPartialAllocation.allocatedBags().size());
        assertEquals(1, afterPartialAllocation.openToteFor(LINE).orElseThrow().bagCount());
        assertTrue(afterPartialAllocation.closedTotes().isEmpty());
    }

    @Test
    void shouldPublishClosedToteBeforeALaterNewToteFailure() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new OutboundToteIdSource() {
                    private int calls;

                    @Override
                    public PhysicalToteId nextId(P2pLineId lineId) {
                        if (++calls > 1) {
                            throw new IllegalStateException("injected tote ID failure");
                        }
                        return new PhysicalToteId("outbound-first");
                    }
                },
                new OutputSheetAllocator(List.of(firstSheet, secondSheet)),
                new OutboundToteConfig(3));
        allocator.allocate(LINE, bag("rx-1", firstSheet), seconds(1));
        OutboundAllocationSnapshot beforeMismatch = allocator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> allocator.allocate(
                        LINE,
                        bag("rx-2", 1, "SC-2", "pharmacy-2", "patient-2", secondSheet),
                        seconds(2)));
        OutboundAllocationSnapshot afterPartialClosure = allocator.snapshot();

        assertNotSame(beforeMismatch, afterPartialClosure);
        assertEquals(1, afterPartialClosure.closedTotes().size());
        assertTrue(afterPartialClosure.openTotesByLine().isEmpty());
        assertEquals(1, afterPartialClosure.allocatedBags().size());
    }

    @Test
    void shouldPublishCreatedOpenToteBeforeALaterAssignmentFailure() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        FailingLifecycleLedger ledger = new FailingLifecycleLedger();
        ledger.failOnAssign = true;
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(sourceSheet)),
                new OutboundToteConfig(3));
        OutboundAllocationSnapshot beforeAllocation = allocator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> allocator.allocate(LINE, bag("rx-1", sourceSheet), seconds(1)));
        OutboundAllocationSnapshot afterPartialCreation = allocator.snapshot();

        assertNotSame(beforeAllocation, afterPartialCreation);
        OutboundToteSnapshot open = afterPartialCreation.openToteFor(LINE).orElseThrow();
        assertFalse(open.assigned());
        assertTrue(afterPartialCreation.allocatedBags().isEmpty());
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

    private static final class FailingLifecycleLedger extends PhysicalToteLifecycleLedger {
        private boolean failOnAssign;
        private boolean failOnTransition;

        @Override
        public PhysicalToteAssignment assign(
                OrderSheetKey orderSheetKey,
                PhysicalToteId toteId,
                PhysicalToteAssignmentStage stage,
                Duration activationTime) {
            if (failOnAssign) {
                throw new IllegalStateException("injected assignment failure");
            }
            return super.assign(orderSheetKey, toteId, stage, activationTime);
        }

        @Override
        public PhysicalToteRecord transitionTote(
                PhysicalToteId toteId,
                PhysicalToteLifecycleState nextState) {
            if (failOnTransition) {
                throw new IllegalStateException("injected transition failure");
            }
            return super.transitionTote(toteId, nextState);
        }
    }
}
