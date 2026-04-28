package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.assignment.PrlAssignmentPlan;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagBatchPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class ToteToBagAssignmentPlannerTest {

    @Test
    void shouldAssignAsManyPrlsAsThereAreCorrelations() {
        PackDimensions dimensions = new PackDimensions(0.08f, 0.05f, 0.04f);
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(
                        new PackPlan("pack-a1", "bag-a", dimensions),
                        new PackPlan("pack-b1", "bag-b", dimensions),
                        new PackPlan("pack-c1", "bag-c", dimensions),
                        new PackPlan("pack-d1", "bag-d", dimensions),
                        new PackPlan("pack-e1", "bag-e", dimensions),
                        new PackPlan("pack-f1", "bag-f", dimensions),
                        new PackPlan("pack-g1", "bag-g", dimensions),
                        new PackPlan("pack-h1", "bag-h", dimensions),
                        new PackPlan("pack-i1", "bag-i", dimensions),
                        new PackPlan("pack-j1", "bag-j", dimensions),
                        new PackPlan("pack-a2", "bag-a", dimensions),
                        new PackPlan("pack-j2", "bag-j", dimensions)));

        List<PrlAssignmentPlan> plans = new ToteToBagAssignmentPlanner().createPlans(
                toteLoadPlan,
                java.util.stream.IntStream.rangeClosed(1, 15)
                        .mapToObj(index -> "prl-" + index)
                        .toList());

        assertEquals(10, plans.size());
        assertEquals("prl-1", plans.getFirst().prlId());
        assertEquals("bag-a", plans.getFirst().correlationId());
        assertEquals(2, plans.getFirst().expectedPackCount());
        assertEquals("prl-10", plans.getLast().prlId());
        assertEquals("bag-j", plans.getLast().correlationId());
        assertEquals(2, plans.getLast().expectedPackCount());
    }

    @Test
    void shouldAssignExpectedCountsFromBatchPlan() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("bag-a", 3);
        counts.put("bag-b", 1);
        ToteToBagBatchPlan batchPlan = new ToteToBagBatchPlan(counts);

        List<PrlAssignmentPlan> plans = new ToteToBagAssignmentPlanner().createPlans(
                batchPlan,
                List.of("prl-1", "prl-2"));

        assertEquals(2, plans.size());
        assertEquals("prl-1", plans.getFirst().prlId());
        assertEquals("bag-a", plans.getFirst().correlationId());
        assertEquals(3, plans.getFirst().expectedPackCount());
        assertEquals("prl-2", plans.getLast().prlId());
        assertEquals("bag-b", plans.getLast().correlationId());
        assertEquals(1, plans.getLast().expectedPackCount());
    }

    @Test
    void shouldAggregateExpectedCountsAcrossMultipleTotePlans() {
        PackDimensions dimensions = new PackDimensions(0.08f, 0.05f, 0.04f);
        ToteLoadPlan toteA = new ToteLoadPlan(
                "tote-a",
                List.of(
                        new PackPlan("pack-a1", "bag-a", dimensions),
                        new PackPlan("pack-b1", "bag-b", dimensions)));
        ToteLoadPlan toteB = new ToteLoadPlan(
                "tote-b",
                List.of(
                        new PackPlan("pack-a2", "bag-a", dimensions),
                        new PackPlan("pack-c1", "bag-c", dimensions),
                        new PackPlan("pack-b2", "bag-b", dimensions)));

        ToteToBagBatchPlan batchPlan = ToteToBagBatchPlan.fromToteLoadPlans(List.of(toteA, toteB));
        List<PrlAssignmentPlan> plans = new ToteToBagAssignmentPlanner().createPlans(
                batchPlan,
                List.of("prl-1", "prl-2", "prl-3"));

        assertEquals(3, plans.size());
        assertEquals("bag-a", plans.get(0).correlationId());
        assertEquals(2, plans.get(0).expectedPackCount());
        assertEquals("bag-b", plans.get(1).correlationId());
        assertEquals(2, plans.get(1).expectedPackCount());
        assertEquals("bag-c", plans.get(2).correlationId());
        assertEquals(1, plans.get(2).expectedPackCount());
    }
}
