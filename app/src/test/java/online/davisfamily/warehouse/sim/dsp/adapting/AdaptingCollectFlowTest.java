package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class AdaptingCollectFlowTest {

    @Test
    void shouldAppendCollectedAdaptedLinesToAssociatedToteLoadPlan() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem collectedLine = adaptedPreparedLine("line-1", "dispatch-1", 2);
        store.stage(collectedLine);
        AdaptingBench bench = new AdaptingBench("bench-1", store, 1d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0);
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        loadPlans.putLoadPlan(new ToteLoadPlan(
                "collect-tote-1",
                List.of(new PackPlan("pack-existing-1", "bag-existing", testDimensions()))));
        AdaptingAreaController controller = new AdaptingAreaController(
                area,
                emptyRuntimeState(),
                loadPlans,
                new DefaultCollectedPackPlanFactory(testDimensions()));
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();

        NotionalToteOrder collectingOrder = dispatchOrder(
                "dispatch-1",
                OrderType.ASSOCIATED,
                new DspOrderItem(
                        "line-1",
                        "product-line-1",
                        2,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        "dispatch-1",
                        1,
                        0));

        area.submitVisit(visitFactory.create(new PhysicalToteId("collect-tote-1"), collectingOrder));
        bench.startProcessing();
        bench.tick(1d);

        AdaptingBenchCompletion completion = controller.applyBenchCompletion(new AdaptingBenchId("bench-1")).orElseThrow();
        assertEquals(AdaptingVisitType.COLLECT, completion.visit().visitType());
        assertEquals(1, completion.collectedLines().size());

        ToteLoadPlan updatedLoadPlan = loadPlans.getLoadPlanFor("collect-tote-1");
        assertEquals(3, updatedLoadPlan.getPackPlans().size());
        assertEquals(List.of("bag-existing", "line-1", "line-1"),
                updatedLoadPlan.getPackPlans().stream().map(PackPlan::correlationId).toList());
        assertEquals(List.of("pack-existing-1", "pack-line-1-1", "pack-line-1-2"),
                updatedLoadPlan.getPackPlans().stream().map(PackPlan::packId).toList());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(collectedLine)));
    }

    @Test
    void shouldCreateLoadPlanForEmptyCollectingTote() {
        AdaptedLineStore store = new AdaptedLineStore();
        DspOrderItem collectedLine = adaptedPreparedLine("line-2", "dispatch-2", 1);
        store.stage(collectedLine);
        AdaptingBench bench = new AdaptingBench("bench-1", store, 0d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0);
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        AdaptingAreaController controller = new AdaptingAreaController(
                area,
                emptyRuntimeState(),
                loadPlans,
                new DefaultCollectedPackPlanFactory(testDimensions()));
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();

        NotionalToteOrder collectingOrder = dispatchOrder(
                "dispatch-2",
                OrderType.EMPTY,
                new DspOrderItem(
                        "line-2",
                        "product-line-2",
                        1,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        "dispatch-2",
                        1,
                        0));

        area.submitVisit(visitFactory.create(new PhysicalToteId("empty-tote-1"), collectingOrder));
        bench.startProcessing();

        controller.applyBenchCompletion(new AdaptingBenchId("bench-1")).orElseThrow();

        ToteLoadPlan createdLoadPlan = loadPlans.getLoadPlanFor("empty-tote-1");
        assertEquals(1, createdLoadPlan.getPackPlans().size());
        assertEquals("line-2", createdLoadPlan.getPackPlans().getFirst().correlationId());
        assertEquals("pack-line-2-1", createdLoadPlan.getPackPlans().getFirst().packId());
        assertFalse(store.contains(PreparedLineKey.forPreparedLine(collectedLine)));
    }

    @Test
    void shouldRejectFullPackCollectVisitByContract() {
        AdaptingVisitFactory visitFactory = new AdaptingVisitFactory();
        NotionalToteOrder fullPackOrder = dispatchOrder(
                "dispatch-3",
                OrderType.FULL_PACK,
                new DspOrderItem(
                        "line-3",
                        "product-line-3",
                        1,
                        "0000310",
                        DspOrderLineType.FULL_PACK,
                        "dispatch-3",
                        1,
                        0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> visitFactory.create(new PhysicalToteId("collect-tote-3"), fullPackOrder));
        assertTrue(ex.getMessage().contains("FULL_PACK"));
    }

    private static DspSchedulerRuntimeState emptyRuntimeState() {
        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(),
                Map.of(),
                Set.of(),
                Optional.empty()));
    }

    private static NotionalToteOrder dispatchOrder(String orderId, OrderType orderType, DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                orderType,
                List.of(items),
                0L);
    }

    private static DspOrderItem adaptedPreparedLine(String lineId, String targetOrderId, int quantity) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                quantity,
                "0000310",
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }

    private static PackDimensions testDimensions() {
        return new PackDimensions(0.20f, 0.10f, 0.08f);
    }
}
