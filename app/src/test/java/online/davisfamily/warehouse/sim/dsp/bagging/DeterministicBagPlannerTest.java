package online.davisfamily.warehouse.sim.dsp.bagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DeterministicBagPlannerTest {
    private static final PackDimensions DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    @Test
    void shouldGroupPacksForOnePrescriptionIntoOneBagWhenCapacityAllows() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        register(registry, "pack-1", "source-1", "rx-1", "patient-1", "pharmacy-1", "SC-1");
        register(registry, "pack-2", "source-2", "rx-1", "patient-1", "pharmacy-1", "SC-1");
        DeterministicBagPlanner planner = planner(registry, 3);

        BagPlanningResult result = planner.plan(request(
                planningTote("fulfilment-1", "tote-1", "SC-1", "pack-1"),
                planningTote("fulfilment-2", "tote-2", "SC-1", "pack-2")));

        assertEquals(1, result.plannedBags().size());
        PlannedBag bag = result.plannedBags().get(0);
        assertEquals(new BagKey("rx-1", 1), bag.bagKey());
        assertEquals(List.of("pack-1", "pack-2"), bag.physicalPackIds());
        assertEquals(
                List.of(new OrderSheetKey("fulfilment-1", 1), new OrderSheetKey("fulfilment-2", 1)),
                bag.owningOrderSheetKeys());
        assertEquals(2, result.packTraces().size());
        assertTrue(result.p2pToteLoadPlans().isEmpty());
    }

    @Test
    void shouldSplitPrescriptionIntoDeterministicBagOrdinalsAtPackLimit() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        for (int ordinal = 1; ordinal <= 5; ordinal++) {
            register(registry, "pack-" + ordinal, "source-1", "rx-1",
                    "patient-1", "pharmacy-1", "SC-1");
        }

        BagPlanningResult result = planner(registry, 2).plan(request(
                planningTote("fulfilment-1", "tote-1", "SC-1",
                        "pack-1", "pack-2", "pack-3", "pack-4", "pack-5")));

        assertEquals(
                List.of(new BagKey("rx-1", 1), new BagKey("rx-1", 2), new BagKey("rx-1", 3)),
                result.plannedBags().stream().map(PlannedBag::bagKey).toList());
        assertEquals(List.of("pack-1", "pack-2"), result.plannedBags().get(0).physicalPackIds());
        assertEquals(List.of("pack-3", "pack-4"), result.plannedBags().get(1).physicalPackIds());
        assertEquals(List.of("pack-5"), result.plannedBags().get(2).physicalPackIds());
    }

    @Test
    void shouldPreservePrescriptionAndPackFirstOccurrenceOrder() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        register(registry, "pack-b1", "source-b", "rx-b", "patient-b", "pharmacy-b", "SC-1");
        register(registry, "pack-a1", "source-a", "rx-a", "patient-a", "pharmacy-a", "SC-1");
        register(registry, "pack-b2", "source-b", "rx-b", "patient-b", "pharmacy-b", "SC-1");

        BagPlanningResult result = planner(registry, 3).plan(request(
                planningTote("fulfilment-1", "tote-1", "SC-1", "pack-b1", "pack-a1"),
                planningTote("fulfilment-2", "tote-2", "SC-1", "pack-b2")));

        assertEquals(
                List.of(new BagKey("rx-b", 1), new BagKey("rx-a", 1)),
                result.plannedBags().stream().map(PlannedBag::bagKey).toList());
        assertEquals(List.of("pack-b1", "pack-b2"), result.plannedBags().get(0).physicalPackIds());
        assertEquals(List.of("pack-a1"), result.plannedBags().get(1).physicalPackIds());
    }

    @Test
    void shouldRejectConflictingPatientPharmacyOrServiceCentreForPrescription() {
        assertConflict("patient-1", "pharmacy-1", "SC-1", "patient-2", "pharmacy-1", "SC-1",
                "Conflicting patient");
        assertConflict("patient-1", "pharmacy-1", "SC-1", "patient-1", "pharmacy-2", "SC-1",
                "Conflicting pharmacy");
        assertConflict("patient-1", "pharmacy-1", "SC-1", "patient-1", "pharmacy-1", "SC-2",
                "Conflicting service centre");
    }

    @Test
    void shouldFailClearlyWhenPhysicalPackProvenanceIsMissing() {
        DeterministicBagPlanner planner = planner(new PackProvenanceRegistry(), 2);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.plan(request(
                        planningTote("fulfilment-1", "tote-1", "SC-1", "missing-pack"))));

        assertTrue(exception.getMessage().contains("Missing source provenance"));
        assertTrue(exception.getMessage().contains("missing-pack"));
    }

    @Test
    void shouldCreateNoPhysicalBagForLineWithoutPhysicalPacks() {
        BagPlanningTote emptyTote = new BagPlanningTote(
                new OrderSheetKey("fulfilment-1", 1),
                "SC-1",
                new ToteLoadPlan("tote-1", List.of()));

        BagPlanningResult result = planner(new PackProvenanceRegistry(), 2)
                .plan(request(emptyTote));

        assertTrue(result.plannedBags().isEmpty());
        assertTrue(result.packTraces().isEmpty());
        assertTrue(result.p2pToteLoadPlans().isEmpty());
    }

    @Test
    void shouldRejectPolicyThatCannotAcceptPackIntoEmptyBag() {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        register(registry, "pack-1", "source-1", "rx-1", "patient-1", "pharmacy-1", "SC-1");
        DeterministicBagPlanner planner = new DeterministicBagPlanner(
                (currentPackPlans, candidatePackPlan) -> false,
                registry.snapshot());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.plan(request(
                        planningTote("fulfilment-1", "tote-1", "SC-1", "pack-1"))));

        assertTrue(exception.getMessage().contains("empty bag"));
        assertTrue(exception.getMessage().contains("pack-1"));
    }

    private static void assertConflict(
            String firstPatient,
            String firstPharmacy,
            String firstServiceCentre,
            String secondPatient,
            String secondPharmacy,
            String secondServiceCentre,
            String expectedMessage) {
        PackProvenanceRegistry registry = new PackProvenanceRegistry();
        register(registry, "pack-1", "source-1", "rx-1",
                firstPatient, firstPharmacy, firstServiceCentre);
        register(registry, "pack-2", "source-2", "rx-1",
                secondPatient, secondPharmacy, secondServiceCentre);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner(registry, 2).plan(request(
                        planningTote("fulfilment-1", "tote-1", firstServiceCentre, "pack-1"),
                        planningTote("fulfilment-2", "tote-2", secondServiceCentre, "pack-2"))));

        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    private static DeterministicBagPlanner planner(PackProvenanceRegistry registry, int maximumPackCount) {
        return new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(maximumPackCount),
                registry.snapshot());
    }

    private static BagPlanningRequest request(BagPlanningTote... planningTotes) {
        return new BagPlanningRequest(List.of(planningTotes));
    }

    private static BagPlanningTote planningTote(
            String fulfilmentOrderId,
            String physicalToteId,
            String serviceCentreId,
            String... packIds) {
        List<PackPlan> packPlans = java.util.Arrays.stream(packIds)
                .map(DeterministicBagPlannerTest::pack)
                .toList();
        return new BagPlanningTote(
                new OrderSheetKey(fulfilmentOrderId, 1),
                serviceCentreId,
                new ToteLoadPlan(physicalToteId, packPlans));
    }

    private static PackPlan pack(String packId) {
        return new PackPlan(packId, "legacy-correlation", DIMENSIONS);
    }

    private static void register(
            PackProvenanceRegistry registry,
            String packId,
            String sourceOrderId,
            String prescriptionId,
            String patientId,
            String pharmacyId,
            String serviceCentreId) {
        registry.register(packId, new PackSourceProvenance(
                new OrderSheetKey(sourceOrderId, 1),
                "line-" + packId,
                "product-" + packId,
                serviceCentreId,
                pharmacyId,
                patientId,
                prescriptionId));
    }
}
