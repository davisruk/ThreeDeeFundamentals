package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;

class P2pBagCorrelationRequirementCatalogFactoryTest {
    private static final String SERVICE_CENTRE = "SC-1";
    private static final OrderSheetKey SHEET_A = new OrderSheetKey("sheet-a", 1);
    private static final OrderSheetKey SHEET_B = new OrderSheetKey("sheet-b", 1);

    @Test
    void shouldGroupCompleteBagCorrelationAcrossTotesAndSheetsInTraceOrder() {
        BagKey bagA = new BagKey("prescription-a", 1);
        BagKey bagB = new BagKey("prescription-b", 1);
        PhysicalToteId firstTote = new PhysicalToteId("osr-tote-1");
        PhysicalToteId secondTote = new PhysicalToteId("osr-tote-2");

        BagPlanningResult result = new BagPlanningResult(
                List.of(
                        plannedBag(bagA, List.of("pack-a-1", "pack-a-2"), SHEET_A),
                        plannedBag(bagB, List.of("pack-b-1"), SHEET_B)),
                List.of(),
                List.of(
                        trace("pack-a-1", firstTote, SHEET_A, bagA),
                        trace("pack-b-1", firstTote, SHEET_B, bagB),
                        trace("pack-a-2", secondTote, SHEET_A, bagA)));

        P2pBagCorrelationRequirementCatalog catalog =
                new P2pBagCorrelationRequirementCatalogFactory().create(result);
        P2pBagCorrelationRequirement requirementA =
                new P2pBagCorrelationRequirement(bagA.correlationId(), 2);
        P2pBagCorrelationRequirement requirementB =
                new P2pBagCorrelationRequirement(bagB.correlationId(), 1);

        assertEquals(
                List.of(requirementA, requirementB),
                new ArrayList<>(catalog.requirementsFor(firstTote)));
        assertEquals(List.of(requirementA), new ArrayList<>(catalog.requirementsFor(secondTote)));
        assertEquals(List.of(requirementA), new ArrayList<>(catalog.requirementsFor(SHEET_A)));
        assertEquals(List.of(requirementB), new ArrayList<>(catalog.requirementsFor(SHEET_B)));
        assertTrue(catalog.requirementsFor(new PhysicalToteId("empty-tote")).isEmpty());
    }

    @Test
    void shouldResolveAv02RequirementsByLogicalSheetRatherThanAllocatedPhysicalId() {
        BagKey bag = new BagKey("prescription-av02", 1);
        P2pBagCorrelationRequirement expected =
                new P2pBagCorrelationRequirement(bag.correlationId(), 1);
        PhysicalToteId allocatedPhysicalId = new PhysicalToteId("av02-allocated-1");
        BagPlanningResult result = new BagPlanningResult(
                List.of(plannedBag(bag, List.of("pack-1"), SHEET_A)),
                List.of(),
                List.of(trace("pack-1", new PhysicalToteId("planned-source"), SHEET_A, bag)));
        P2pBagCorrelationRequirementCatalog catalog =
                P2pBagCorrelationRequirementCatalogFactory.from(result);

        P2pReleaseAssignmentRequest request = new P2pReleaseAssignmentRequest(
                allocatedPhysicalId,
                SHEET_A,
                SERVICE_CENTRE,
                "p2p-target",
                OperationalPhysicalToteSource.AV02,
                java.util.Optional.empty());

        assertEquals(Set.of(expected), catalog.requirementsFor(request));
        assertTrue(catalog.requirementsFor(allocatedPhysicalId).isEmpty());
    }

    private static PlannedBag plannedBag(
            BagKey bagKey,
            List<String> packIds,
            OrderSheetKey owningSheet) {
        return new PlannedBag(
                bagKey,
                SERVICE_CENTRE,
                "pharmacy-1",
                "patient-1",
                bagKey.prescriptionId(),
                packIds,
                List.of(owningSheet));
    }

    private static PlannedPackTrace trace(
            String packId,
            PhysicalToteId inputTote,
            OrderSheetKey fulfilmentSheet,
            BagKey bagKey) {
        return new PlannedPackTrace(
                packId,
                new PackSourceProvenance(
                        fulfilmentSheet,
                        "line-" + packId,
                        "product-1",
                        SERVICE_CENTRE,
                        "pharmacy-1",
                        "patient-1",
                        bagKey.prescriptionId()),
                inputTote,
                fulfilmentSheet,
                bagKey);
    }
}
