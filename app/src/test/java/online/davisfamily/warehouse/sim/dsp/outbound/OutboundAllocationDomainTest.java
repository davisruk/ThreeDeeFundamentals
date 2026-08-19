package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class OutboundAllocationDomainTest {

    @Test
    void shouldValidateP2pLineAndOutboundCapacity() {
        assertEquals("p2p-1", new P2pLineId(" p2p-1 ").value());
        assertEquals(10, new OutboundToteConfig(10).maximumBagCount());

        assertThrows(IllegalArgumentException.class, () -> new P2pLineId(null));
        assertThrows(IllegalArgumentException.class, () -> new P2pLineId("  "));
        assertThrows(IllegalArgumentException.class, () -> new OutboundToteConfig(0));
        assertThrows(IllegalArgumentException.class, () -> new OutboundToteConfig(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new OutputSheetAllocation(sheet("order-1", 1), sheet("order-2", 2)));
    }

    @Test
    void shouldRepresentOriginalAndGeneratedOutputSheetOwnership() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-2", 1);
        OutputSheetAllocation original = new OutputSheetAllocation(firstSource, firstSource);
        OutputSheetAllocation generated = new OutputSheetAllocation(secondSource, sheet("order-2", 2));
        List<OutputSheetAllocation> sourceAllocations = new ArrayList<>(List.of(original, generated));
        PlannedBag plannedBag = plannedBag(firstSource, secondSource);

        AllocatedOutboundBag allocatedBag = new AllocatedOutboundBag(
                plannedBag,
                new PhysicalToteId("outbound-1"),
                sourceAllocations);
        sourceAllocations.clear();

        assertFalse(original.generated());
        assertTrue(generated.generated());
        assertEquals(plannedBag.bagKey(), allocatedBag.bagKey());
        assertEquals(List.of(original, generated), allocatedBag.outputSheetAllocations());
        assertThrows(UnsupportedOperationException.class,
                () -> allocatedBag.outputSheetAllocations().clear());
    }

    @Test
    void shouldRejectAllocationThatDoesNotCoverPlannedBagOwnership() {
        OrderSheetKey firstSource = sheet("order-1", 1);
        OrderSheetKey secondSource = sheet("order-2", 1);
        PlannedBag plannedBag = plannedBag(firstSource, secondSource);
        PhysicalToteId toteId = new PhysicalToteId("outbound-1");

        assertThrows(IllegalArgumentException.class,
                () -> new AllocatedOutboundBag(
                        plannedBag,
                        toteId,
                        List.of(new OutputSheetAllocation(firstSource, firstSource))));
        assertThrows(IllegalArgumentException.class,
                () -> new AllocatedOutboundBag(
                        plannedBag,
                        toteId,
                        List.of(
                                new OutputSheetAllocation(secondSource, secondSource),
                                new OutputSheetAllocation(firstSource, firstSource))));
        OrderSheetKey sameOrderSecondSource = sheet("order-1", 2);
        PlannedBag sameOrderBag = plannedBag(firstSource, sameOrderSecondSource);
        assertThrows(IllegalArgumentException.class,
                () -> new AllocatedOutboundBag(
                        sameOrderBag,
                        toteId,
                        List.of(
                                new OutputSheetAllocation(firstSource, sheet("order-1", 3)),
                                new OutputSheetAllocation(sameOrderSecondSource, sheet("order-1", 3)))));
    }

    private static PlannedBag plannedBag(OrderSheetKey... owningSheets) {
        return new PlannedBag(
                new BagKey("prescription-1", 1),
                "SC-1",
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                List.of("pack-1"),
                List.of(owningSheets));
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }
}
