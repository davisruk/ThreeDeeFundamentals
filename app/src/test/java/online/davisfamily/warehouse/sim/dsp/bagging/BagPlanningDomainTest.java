package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class BagPlanningDomainTest {
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldValidateOrderedBagPlanningInput() {
        BagPlanningTote first = planningTote("order-1", "tote-1", pack("pack-1"));
        BagPlanningTote second = planningTote("order-2", "tote-2", pack("pack-2"));
        List<BagPlanningTote> source = new ArrayList<>(List.of(first, second));

        BagPlanningRequest request = new BagPlanningRequest(source);
        source.clear();

        assertEquals(List.of(first, second), request.planningTotes());
        assertThrows(UnsupportedOperationException.class, () -> request.planningTotes().clear());
        assertThrows(IllegalArgumentException.class, () -> new BagPlanningRequest(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BagPlanningRequest(Arrays.asList(first, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new BagPlanningRequest(List.of(
                        first,
                        planningTote("order-2", "tote-1", pack("pack-2")))));
        assertThrows(IllegalArgumentException.class,
                () -> new BagPlanningRequest(List.of(
                        first,
                        planningTote("order-2", "tote-2", pack("pack-1")))));
    }

    @Test
    void shouldRepresentOneBagOwnedBySeveralLogicalSheets() {
        OrderSheetKey firstSheet = new OrderSheetKey("order-1", 1);
        OrderSheetKey secondSheet = new OrderSheetKey("order-2", 3);

        PlannedBag plannedBag = new PlannedBag(
                new BagKey("prescription-1", 1),
                " SC-1 ",
                " pharmacy-1 ",
                " patient-1 ",
                " prescription-1 ",
                List.of(" pack-1 ", "pack-2"),
                List.of(firstSheet, secondSheet, firstSheet));

        assertEquals("SC-1", plannedBag.serviceCentreId());
        assertEquals(List.of("pack-1", "pack-2"), plannedBag.physicalPackIds());
        assertEquals(List.of(firstSheet, secondSheet), plannedBag.owningOrderSheetKeys());
        assertThrows(UnsupportedOperationException.class, () -> plannedBag.physicalPackIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> plannedBag.owningOrderSheetKeys().clear());
    }

    @Test
    void shouldJoinSourceAndFulfilmentIdentityInPlannedPackTrace() {
        PackSourceProvenance source = provenance("source-order", "prescription-1");
        PhysicalToteId inputToteId = new PhysicalToteId("input-tote-1");
        OrderSheetKey fulfilmentSheet = new OrderSheetKey("associated-order", 4);
        BagKey bagKey = new BagKey("prescription-1", 1);

        PlannedPackTrace trace = new PlannedPackTrace(
                " pack-1 ", source, inputToteId, fulfilmentSheet, bagKey);

        assertEquals("pack-1", trace.physicalPackId());
        assertEquals(new OrderSheetKey("source-order", 2), trace.sourceProvenance().sourceOrderSheetKey());
        assertEquals(inputToteId, trace.inputPhysicalToteId());
        assertEquals(fulfilmentSheet, trace.fulfilmentOrderSheetKey());
        assertEquals(bagKey, trace.bagKey());
    }

    @Test
    void shouldProvideImmutableBagAndPackLookups() {
        BagKey bagKey = new BagKey("prescription-1", 1);
        PlannedBag plannedBag = plannedBag(bagKey, "pack-1");
        PlannedPackTrace packTrace = new PlannedPackTrace(
                "pack-1",
                provenance("source-order", "prescription-1"),
                new PhysicalToteId("input-tote-1"),
                new OrderSheetKey("fulfilment-order", 1),
                bagKey);
        ToteLoadPlan p2pLoadPlan = new ToteLoadPlan("input-tote-1", List.of(pack("pack-1")));

        BagPlanningResult result = new BagPlanningResult(
                List.of(plannedBag), List.of(p2pLoadPlan), List.of(packTrace));

        assertEquals(plannedBag, result.findBag(bagKey).orElseThrow());
        assertEquals(plannedBag, result.findBagByCorrelationId(" prescription-1/bag-1 ").orElseThrow());
        assertEquals(packTrace, result.findPackTrace(" pack-1 ").orElseThrow());
        assertFalse(result.findPackTrace("missing-pack").isPresent());
        assertThrows(UnsupportedOperationException.class, () -> result.plannedBags().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.p2pToteLoadPlans().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.packTraces().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new BagPlanningResult(
                        List.of(plannedBag, plannedBag), List.of(), List.of(packTrace)));
        assertThrows(IllegalArgumentException.class,
                () -> new BagPlanningResult(
                        List.of(plannedBag), List.of(), List.of(packTrace, packTrace)));
    }

    private static BagPlanningTote planningTote(String orderId, String toteId, PackPlan... packPlans) {
        return new BagPlanningTote(
                new OrderSheetKey(orderId, 1),
                "SC-1",
                new ToteLoadPlan(toteId, List.of(packPlans)));
    }

    private static PlannedBag plannedBag(BagKey bagKey, String... packIds) {
        return new PlannedBag(
                bagKey,
                "SC-1",
                "pharmacy-1",
                "patient-1",
                bagKey.prescriptionId(),
                List.of(packIds),
                List.of(new OrderSheetKey("fulfilment-order", 1)));
    }

    private static PackPlan pack(String packId) {
        return new PackPlan(packId, "legacy-correlation", DIMENSIONS);
    }

    private static PackSourceProvenance provenance(String sourceOrderId, String prescriptionId) {
        return new PackSourceProvenance(
                new OrderSheetKey(sourceOrderId, 2),
                "line-1",
                "product-1",
                "SC-1",
                "pharmacy-1",
                "patient-1",
                prescriptionId);
    }
}
