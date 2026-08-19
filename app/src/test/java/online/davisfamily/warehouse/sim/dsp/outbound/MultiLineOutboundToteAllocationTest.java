package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;

class MultiLineOutboundToteAllocationTest {
    private static final P2pLineId LINE_1 = new P2pLineId("p2p-1");
    private static final P2pLineId LINE_2 = new P2pLineId("p2p-2");

    @Test
    void shouldMaintainIndependentOpenOutboundTotePerP2pLine() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OutboundToteAllocator allocator = allocator(3, firstSheet, secondSheet);

        allocator.allocate(LINE_1, bag("rx-1", firstSheet), seconds(1));
        allocator.allocate(LINE_2, bag("rx-2", secondSheet), seconds(1));

        OutboundAllocationSnapshot snapshot = allocator.snapshot();
        assertEquals(2, snapshot.openTotesByLine().size());
        assertEquals("outbound-p2p-1-1",
                snapshot.openToteFor(LINE_1).orElseThrow().physicalToteId().value());
        assertEquals("outbound-p2p-2-1",
                snapshot.openToteFor(LINE_2).orElseThrow().physicalToteId().value());
        assertEquals("pharmacy-1",
                snapshot.openToteFor(LINE_1).orElseThrow().pharmacyId().orElseThrow());
        assertEquals("pharmacy-1",
                snapshot.openToteFor(LINE_2).orElseThrow().pharmacyId().orElseThrow());
    }

    @Test
    void shouldNotCloseOtherLineWhenOneLineReachesCapacity() {
        OrderSheetKey firstSheet = sheet("order-1", 1);
        OrderSheetKey secondSheet = sheet("order-2", 1);
        OrderSheetKey thirdSheet = sheet("order-3", 1);
        OutboundToteAllocator allocator = allocator(2, firstSheet, secondSheet, thirdSheet);
        allocator.allocate(LINE_2, bag("rx-3", thirdSheet), seconds(1));

        allocator.allocate(LINE_1, bag("rx-1", firstSheet), seconds(1));
        allocator.allocate(LINE_1, bag("rx-2", secondSheet), seconds(2));

        OutboundAllocationSnapshot snapshot = allocator.snapshot();
        assertTrue(snapshot.openToteFor(LINE_1).isEmpty());
        assertEquals(1, snapshot.openToteFor(LINE_2).orElseThrow().bagCount());
        assertEquals(List.of(LINE_1), snapshot.closedTotes().stream()
                .map(OutboundToteSnapshot::p2pLineId)
                .toList());
        assertEquals(OutboundToteClosureReason.CAPACITY_REACHED,
                snapshot.closedTotes().getFirst().closureReason().orElseThrow());
    }

    @Test
    void shouldGenerateSheetForConcurrentSameOrderOutputAcrossLines() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OutboundToteAllocator allocator = allocator(3, sourceSheet);

        AllocatedOutboundBag first = allocator.allocate(
                LINE_1, bag("rx-1", 1, sourceSheet), seconds(1));
        AllocatedOutboundBag second = allocator.allocate(
                LINE_2, bag("rx-1", 2, sourceSheet), seconds(1));

        assertEquals(sourceSheet, outputSheet(first));
        assertEquals(sheet("order-1", 2), outputSheet(second));
        assertTrue(second.outputSheetAllocations().getFirst().generated());
        assertEquals(2, allocator.snapshot().openTotesByLine().size());
    }

    @Test
    void shouldPreserveDeterministicLineAndToteHistoryOrder() {
        OrderSheetKey sheet1 = sheet("order-1", 1);
        OrderSheetKey sheet2 = sheet("order-2", 1);
        OrderSheetKey sheet3 = sheet("order-3", 1);
        OrderSheetKey sheet4 = sheet("order-4", 1);
        OrderSheetKey sheet5 = sheet("order-5", 1);
        OrderSheetKey sheet6 = sheet("order-6", 1);
        OutboundToteAllocator allocator = allocator(
                3, sheet1, sheet2, sheet3, sheet4, sheet5, sheet6);
        allocator.allocate(LINE_2, bag("rx-1", sheet1), seconds(1));
        allocator.allocate(LINE_1, bag("rx-2", sheet2), seconds(1));
        allocator.allocate(LINE_1, bag("rx-3", sheet3), seconds(2));
        allocator.closeForApplicableWorkCompletion(LINE_1, seconds(3));
        allocator.allocate(LINE_2, bag("rx-4", sheet4), seconds(2));
        allocator.closeForApplicableWorkCompletion(LINE_2, seconds(3));
        allocator.allocate(LINE_2, bag("rx-5", sheet5), seconds(4));
        allocator.allocate(LINE_1, bag("rx-6", sheet6), seconds(4));

        OutboundAllocationSnapshot snapshot = allocator.snapshot();
        assertEquals(List.of(LINE_2, LINE_1), List.copyOf(snapshot.openTotesByLine().keySet()));
        assertEquals(
                List.of("outbound-p2p-1-1", "outbound-p2p-2-1"),
                snapshot.closedTotes().stream()
                        .map(tote -> tote.physicalToteId().value())
                        .toList());
        assertEquals("outbound-p2p-2-2",
                snapshot.openToteFor(LINE_2).orElseThrow().physicalToteId().value());
        assertEquals("outbound-p2p-1-2",
                snapshot.openToteFor(LINE_1).orElseThrow().physicalToteId().value());
        assertEquals(
                List.of("rx-1", "rx-2", "rx-3", "rx-4", "rx-5", "rx-6"),
                snapshot.allocatedBags().stream()
                        .map(allocation -> allocation.plannedBag().prescriptionId())
                        .toList());
    }

    private static OutboundToteAllocator allocator(
            int capacity,
            OrderSheetKey... knownSheets) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        return new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(knownSheets)),
                new OutboundToteConfig(capacity));
    }

    private static PlannedBag bag(String prescriptionId, OrderSheetKey... owningSheets) {
        return bag(prescriptionId, 1, owningSheets);
    }

    private static PlannedBag bag(
            String prescriptionId,
            int ordinal,
            OrderSheetKey... owningSheets) {
        return new PlannedBag(
                new BagKey(prescriptionId, ordinal),
                "SC-1",
                "pharmacy-1",
                "patient-1",
                prescriptionId,
                List.of("pack-" + prescriptionId + "-" + ordinal),
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
}
