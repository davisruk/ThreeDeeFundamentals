package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutputSheetAllocatorTest {

    @Test
    void shouldDeriveFirstOutputSheetWhileSourceRemainsActivelyAssigned() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId inboundTote = tote("inbound-1");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.inboundPack(inboundTote));
        ledger.assign(sourceSheet, inboundTote, PhysicalToteAssignmentStage.PRE_P2P, Duration.ZERO);
        var sourceAssignment = ledger.activeAssignmentFor(sourceSheet).orElseThrow();
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        List<OutputSheetAllocation> allocations = allocator.resolve(
                List.of(sourceSheet), tote("outbound-1"), ledger.snapshot());

        assertEquals(List.of(new OutputSheetAllocation(sourceSheet, sheet("order-1", 101))), allocations);
        assertEquals(sourceAssignment, ledger.activeAssignmentFor(sourceSheet).orElseThrow());
    }

    @Test
    void shouldAdvanceOrdinalPerSourceAndTargetAndReuseMappingsAfterClosure() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-1", 2);
        PhysicalToteId firstTote = tote("outbound-1");
        PhysicalToteId secondTote = tote("outbound-2");
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(firstSource, secondSource));

        OrderSheetKey firstOutput = allocator.resolve(
                List.of(firstSource), firstTote, emptySnapshot()).getFirst().outputSheetKey();
        OrderSheetKey secondOutput = allocator.resolve(
                List.of(firstSource),
                secondTote,
                outboundSnapshot(firstOutput, firstTote, PhysicalToteAssignmentStage.OUTBOUND))
                .getFirst().outputSheetKey();
        OrderSheetKey otherSourceOutput = allocator.resolve(
                List.of(secondSource), secondTote, emptySnapshot()).getFirst().outputSheetKey();
        OrderSheetKey reusedOutput = allocator.resolve(
                List.of(firstSource),
                firstTote,
                outboundSnapshot(firstOutput, firstTote, PhysicalToteAssignmentStage.OUTBOUND))
                .getFirst().outputSheetKey();

        assertEquals(sheet("order-1", 101), firstOutput);
        assertEquals(sheet("order-1", 102), secondOutput);
        assertEquals(sheet("order-1", 121), otherSourceOutput);
        assertEquals(firstOutput, reusedOutput);
        assertEquals(sheet("order-1", 103), allocator.resolve(
                List.of(firstSource), tote("outbound-3"), emptySnapshot())
                .getFirst().outputSheetKey());
    }

    @Test
    void shouldAllocateSeveralOrdersIntoOneTargetToteInInputOrder() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-2", 3);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(firstSource, secondSource));
        List<OrderSheetKey> sourceOrder = new ArrayList<>(List.of(secondSource, firstSource));

        List<OutputSheetAllocation> allocations = allocator.resolve(
                sourceOrder, tote("outbound-1"), emptySnapshot());
        sourceOrder.clear();

        assertEquals(List.of(
                new OutputSheetAllocation(secondSource, sheet("order-2", 141)),
                new OutputSheetAllocation(firstSource, sheet("order-1", 101))), allocations);
        assertThrows(UnsupportedOperationException.class, allocations::clear);
    }

    @Test
    void shouldRejectOrdinalTwentyAndLater() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        for (int ordinal = 1; ordinal <= 19; ordinal++) {
            assertEquals(
                    sheet("order-1", 100 + ordinal),
                    allocator.resolve(
                            List.of(sourceSheet), tote("outbound-" + ordinal), emptySnapshot())
                            .getFirst().outputSheetKey());
        }

        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sourceSheet), tote("outbound-20"), emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sourceSheet), tote("outbound-21"), emptySnapshot()));
    }

    @Test
    void shouldRejectDerivedSheetNumbersOutsideThreeDigitRangeAndArithmeticRange() {
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of());

        assertEquals(sheet("unlisted-order", 101), allocator.resolve(
                List.of(sheet("unlisted-order", 1)), tote("outbound-valid"), emptySnapshot())
                .getFirst().outputSheetKey());
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sheet("large-sheet", 46)), tote("outbound-1"), emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(
                        List.of(sheet("overflow-sheet", Integer.MAX_VALUE)),
                        tote("outbound-2"),
                        emptySnapshot()));
    }

    @Test
    void shouldRejectKnownSheetCollisionWithoutPublishingEarlierMappings() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-2", 1);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(
                firstSource, secondSource, sheet("order-2", 101)));
        PhysicalToteId failedBatchTote = tote("outbound-failed-batch");

        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(firstSource, secondSource), failedBatchTote, emptySnapshot()));

        assertEquals(sheet("order-1", 101), allocator.resolve(
                List.of(firstSource), tote("outbound-after-failure"), emptySnapshot())
                .getFirst().outputSheetKey());
    }

    @Test
    void shouldRejectOutputAssignmentOnAnotherToteWithoutChangingMappingState() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId targetTote = tote("outbound-1");
        OrderSheetKey outputSheet = sheet("order-1", 101);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));
        assertEquals(outputSheet, allocator.resolve(List.of(sourceSheet), targetTote, emptySnapshot())
                .getFirst().outputSheetKey());

        PhysicalToteLifecycleSnapshot foreignToteSnapshot = outboundSnapshot(
                outputSheet, tote("outbound-foreign"), PhysicalToteAssignmentStage.OUTBOUND_BAG);
        assertThrows(IllegalStateException.class,
                () -> allocator.resolve(List.of(sourceSheet), targetTote, foreignToteSnapshot));

        assertEquals(outputSheet, allocator.resolve(List.of(sourceSheet), targetTote, emptySnapshot())
                .getFirst().outputSheetKey());
        assertEquals(sheet("order-1", 102), allocator.resolve(
                List.of(sourceSheet), tote("outbound-2"), emptySnapshot()).getFirst().outputSheetKey());
    }

    @Test
    void shouldRejectExistingOutputMappingAtNonOutboundStage() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        PhysicalToteId targetTote = tote("outbound-1");
        OrderSheetKey outputSheet = sheet("order-1", 101);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));
        assertEquals(outputSheet, allocator.resolve(List.of(sourceSheet), targetTote, emptySnapshot())
                .getFirst().outputSheetKey());
        PhysicalToteLifecycleSnapshot nonOutboundSnapshot = new PhysicalToteLifecycleSnapshot(
                Map.of(),
                List.of(PhysicalToteAssignment.active(
                        0, outputSheet, targetTote, PhysicalToteAssignmentStage.PRE_P2P, Duration.ZERO)));

        assertThrows(IllegalStateException.class,
                () -> allocator.resolve(List.of(sourceSheet), targetTote, nonOutboundSnapshot));
        assertEquals(outputSheet, allocator.resolve(List.of(sourceSheet), targetTote, emptySnapshot())
                .getFirst().outputSheetKey());
    }

    @Test
    void shouldRejectActiveNonOutboundAssignmentOnUnmappedOutputKeyWithoutPublishing() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        OrderSheetKey derivedOutput = sheet("order-1", 101);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId assignedTote = tote("inbound-output-collision");
        ledger.register(PhysicalToteRecord.inboundPack(assignedTote));
        ledger.assign(derivedOutput, assignedTote, PhysicalToteAssignmentStage.PRE_P2P, Duration.ZERO);
        OutputSheetAllocator allocator = new OutputSheetAllocator(List.of(sourceSheet));

        assertThrows(IllegalStateException.class,
                () -> allocator.resolve(List.of(sourceSheet), tote("outbound-1"), ledger.snapshot()));
        assertEquals(derivedOutput, allocator.resolve(
                List.of(sourceSheet), tote("outbound-1"), emptySnapshot()).getFirst().outputSheetKey());
    }

    @Test
    void shouldCopyKnownSheetCatalogAndValidateInputs() {
        OrderSheetKey sourceSheet = sheet("order-1", 1);
        List<OrderSheetKey> knownSheets = new ArrayList<>(List.of(sourceSheet));
        OutputSheetAllocator allocator = new OutputSheetAllocator(knownSheets);
        knownSheets.add(sheet("order-1", 101));

        assertEquals(sheet("order-1", 101), allocator.resolve(
                List.of(sourceSheet), tote("outbound-1"), emptySnapshot()).getFirst().outputSheetKey());
        assertThrows(IllegalArgumentException.class,
                () -> new OutputSheetAllocator(Arrays.asList(sourceSheet, null)));
        assertThrows(IllegalArgumentException.class, () -> new OutputSheetAllocator(null));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(null, tote("outbound-2"), emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(Arrays.asList(sourceSheet, null), tote("outbound-2"), emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sourceSheet, sourceSheet), tote("outbound-2"), emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sourceSheet), null, emptySnapshot()));
        assertThrows(IllegalArgumentException.class,
                () -> allocator.resolve(List.of(sourceSheet), tote("outbound-2"), null));
    }

    private static PhysicalToteLifecycleSnapshot outboundSnapshot(
            OrderSheetKey sheet,
            PhysicalToteId toteId,
            PhysicalToteAssignmentStage stage) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(PhysicalToteRecord.outboundBag(toteId));
        ledger.assign(sheet, toteId, stage, Duration.ZERO);
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
