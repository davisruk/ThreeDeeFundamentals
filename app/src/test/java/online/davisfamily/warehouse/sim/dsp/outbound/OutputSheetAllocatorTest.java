package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutputSheetAllocatorTest {

    @Test
    void shouldRetainOriginalSheetForFirstOutboundAssignment() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        List<OutputSheetAllocation> allocations = allocator.resolve(
                List.of(sourceSheet), tote("outbound-1"), emptySnapshot());

        assertEquals(List.of(new OutputSheetAllocation(sourceSheet, sourceSheet)), allocations);
        assertThrows(UnsupportedOperationException.class, allocations::clear);
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(
                        List.of(sourceSheet, sourceSheet), tote("outbound-1"), emptySnapshot()));
    }

    @Test
    void shouldReuseOutputSheetForSameSourceAndTargetTote() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId targetTote = tote("outbound-1");
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        OutputSheetAllocation first = allocator.resolve(
                List.of(sourceSheet), targetTote, emptySnapshot()).getFirst();
        OutputSheetAllocation repeated = allocator.resolve(
                List.of(sourceSheet), targetTote, emptySnapshot()).getFirst();

        assertEquals(first, repeated);
    }

    @Test
    void shouldGenerateNextAvailableSheetForConcurrentOutboundAssignment() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId firstTote = tote("outbound-1");
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));
        allocator.resolve(List.of(sourceSheet), firstTote, emptySnapshot());

        OutputSheetAllocation generated = allocator.resolve(
                List.of(sourceSheet),
                tote("outbound-2"),
                outboundSnapshot(sourceSheet, firstTote)).getFirst();

        assertEquals(
                new OutputSheetAllocation(sourceSheet, sheet("order-1", 2)),
                generated);
    }

    @Test
    void shouldConsiderExistingHigherSheetNumbersWhenGenerating() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId firstTote = tote("outbound-1");
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(
                sourceSheet,
                sheet("order-1", 4),
                sheet("other-order", 20)));
        allocator.resolve(List.of(sourceSheet), firstTote, emptySnapshot());

        OutputSheetAllocation generated = allocator.resolve(
                List.of(sourceSheet),
                tote("outbound-2"),
                outboundSnapshot(sourceSheet, firstTote)).getFirst();

        assertEquals(sheet("order-1", 5), generated.outputSheetKey());
    }

    @Test
    void shouldAllocateSeveralOrdersIntoOneTargetToteDeterministically() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-2", 3);
        PhysicalToteId targetTote = tote("outbound-1");
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(firstSource, secondSource));
        List<OrderSheetKey> sourceOrder = new ArrayList<>(List.of(secondSource, firstSource));

        List<OutputSheetAllocation> allocations = allocator.resolve(
                sourceOrder, targetTote, emptySnapshot());
        sourceOrder.clear();

        assertEquals(List.of(
                new OutputSheetAllocation(secondSource, secondSource),
                new OutputSheetAllocation(firstSource, firstSource)), allocations);
    }

    @Test
    void shouldRejectSourceSheetStillAssignedToNonOutboundStage() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId inboundTote = tote("inbound-1");
        ledger.register(PhysicalToteRecord.inboundPack(inboundTote));
        ledger.assign(sourceSheet, inboundTote, PhysicalToteAssignmentStage.PRE_P2P, Duration.ZERO);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        assertThrows(
                IllegalStateException.class,
                () -> allocator.resolve(
                        List.of(sourceSheet), tote("outbound-1"), ledger.snapshot()));

        assertEquals(
                sourceSheet,
                allocator.resolve(List.of(sourceSheet), tote("outbound-1"), emptySnapshot())
                        .getFirst().outputSheetKey());
    }

    private static PhysicalToteLifecycleSnapshot outboundSnapshot(
            OrderSheetKey sheet,
            PhysicalToteId toteId) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.outboundBag(toteId));
        ledger.assign(sheet, toteId, PhysicalToteAssignmentStage.OUTBOUND_BAG, Duration.ZERO);
        return ledger.snapshot();
    }

    private static PhysicalToteLifecycleSnapshot emptySnapshot() {
        return new PhysicalToteLifecycleSnapshot(Map.of(), List.of());
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private static PhysicalToteId tote(String value) {
        return new PhysicalToteId(value);
    }
}
