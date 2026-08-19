package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutboundGeneratedSheetIntegrationTest {
    private static final P2pLineId LINE = new P2pLineId("p2p-1");

    @Test
    void shouldGenerateOverflowSheetWhenOriginalRemainsAssignedToClosedOutboundTote() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(1, sourceSheet);

        AllocatedOutboundBag first = fixture.allocator().allocate(
                LINE, bag("rx-1", 1, sourceSheet), seconds(1));
        AllocatedOutboundBag second = fixture.allocator().allocate(
                LINE, bag("rx-1", 2, sourceSheet), seconds(2));

        assertEquals(sourceSheet, outputSheet(first));
        assertEquals(sheet("order-1", 2), outputSheet(second));
        assertEquals(PhysicalToteAssignmentStage.OUTBOUND,
                fixture.ledger().activeAssignmentFor(sourceSheet).orElseThrow().stage());
        assertTrue(second.outputSheetAllocations().getFirst().generated());
    }

    @Test
    void shouldReuseGeneratedSheetForLaterBagInSameOutboundTote() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        Fixture fixture = fixture(3, sourceSheet);
        fixture.allocator().allocate(LINE, bag("rx-1", 1, sourceSheet), seconds(1));
        fixture.allocator().closeForApplicableWorkCompletion(LINE, seconds(2));

        AllocatedOutboundBag second = fixture.allocator().allocate(
                LINE, bag("rx-1", 2, sourceSheet), seconds(3));
        AllocatedOutboundBag third = fixture.allocator().allocate(
                LINE, bag("rx-1", 3, sourceSheet), seconds(4));

        assertEquals(sheet("order-1", 2), outputSheet(second));
        assertEquals(outputSheet(second), outputSheet(third));
        assertEquals(second.outboundPhysicalToteId(), third.outboundPhysicalToteId());
    }

    @Test
    void shouldKeepOriginalSourceAndFulfilmentProvenanceAfterOutputSplit() {
        OrderSheetKey adaptedSourceSheet = sheet("adapted-order", 3);
        OrderSheetKey associatedFulfilmentSheet = sheet("associated-order", 1);
        PlannedBag secondBag = bag("rx-1", 2, associatedFulfilmentSheet);
        PackSourceProvenance sourceProvenance = new PackSourceProvenance(
                adaptedSourceSheet,
                "line-1",
                "product-1",
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "rx-1");
        PlannedPackTrace trace = new PlannedPackTrace(
                secondBag.physicalPackIds().getFirst(),
                sourceProvenance,
                new PhysicalToteId("inbound-1"),
                associatedFulfilmentSheet,
                secondBag.bagKey());
        BagPlanningResult planningResult = new BagPlanningResult(
                List.of(secondBag), List.of(), List.of(trace));
        Fixture fixture = fixture(1, associatedFulfilmentSheet);
        fixture.allocator().allocate(
                LINE, bag("rx-1", 1, associatedFulfilmentSheet), seconds(1));

        AllocatedOutboundBag allocated = fixture.allocator().allocate(
                LINE, planningResult.plannedBags().getFirst(), seconds(2));
        PlannedPackTrace retainedTrace = planningResult.findPackTrace(trace.physicalPackId()).orElseThrow();

        assertEquals(sheet("associated-order", 2), outputSheet(allocated));
        assertSame(secondBag, allocated.plannedBag());
        assertEquals(List.of(associatedFulfilmentSheet), allocated.plannedBag().owningOrderSheetKeys());
        assertEquals(adaptedSourceSheet, retainedTrace.sourceProvenance().sourceOrderSheetKey());
        assertEquals(associatedFulfilmentSheet, retainedTrace.fulfilmentOrderSheetKey());
    }

    @Test
    void shouldAvoidSheetNumberCollisionsAcrossSeveralOrders() {
        OrderSheetKey sourceA = sheet("order-a", 1);
        OrderSheetKey sourceB = sheet("order-b", 1);
        Fixture fixture = fixture(
                4,
                sourceA,
                sheet("order-a", 4),
                sourceB,
                sheet("order-b", 7));
        fixture.allocator().allocate(LINE, bag("rx-a", 1, sourceA), seconds(1));
        fixture.allocator().allocate(LINE, bag("rx-b", 1, sourceB), seconds(2));
        fixture.allocator().closeForApplicableWorkCompletion(LINE, seconds(3));

        AllocatedOutboundBag overflowA = fixture.allocator().allocate(
                LINE, bag("rx-a", 2, sourceA), seconds(4));
        AllocatedOutboundBag overflowB = fixture.allocator().allocate(
                LINE, bag("rx-b", 2, sourceB), seconds(5));

        assertEquals(sheet("order-a", 5), outputSheet(overflowA));
        assertEquals(sheet("order-b", 8), outputSheet(overflowB));
        assertEquals("order-a", outputSheet(overflowA).orderId());
        assertEquals("order-b", outputSheet(overflowB).orderId());
        assertEquals(overflowA.outboundPhysicalToteId(), overflowB.outboundPhysicalToteId());
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

    private static PlannedBag bag(
            String prescriptionId,
            int bagOrdinal,
            OrderSheetKey... owningSheets) {
        return new PlannedBag(
                new BagKey(prescriptionId, bagOrdinal),
                "SC-1",
                "pharmacy-1",
                "patient-1",
                prescriptionId,
                List.of("pack-" + prescriptionId + "-" + bagOrdinal),
                Arrays.asList(owningSheets));
    }

    private static OrderSheetKey outputSheet(AllocatedOutboundBag allocation) {
        return allocation.outputSheetAllocations().getFirst().outputSheetKey();
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
