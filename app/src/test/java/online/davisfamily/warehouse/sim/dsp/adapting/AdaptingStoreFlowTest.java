package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DependencyType;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DependencyBlock;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class AdaptingStoreFlowTest {

    private final DspDependencyEvaluator dependencyEvaluator = new DspDependencyEvaluator();

    @Test
    void shouldPublishPreparedLineReadinessOnlyAfterStoreProcessingCompletes() {
        AdaptedLineStore store = new AdaptedLineStore();
        AdaptingBench bench = new AdaptingBench("bench-1", store, 2d);
        AdaptingArea area = new AdaptingArea(List.of(bench), 0);
        DspSchedulerOrderState associatedOrder = associatedOrderState("target-1", "line-1");
        DspSchedulerRuntimeState runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                List.of(associatedOrder),
                Map.of(),
                Set.of(),
                Optional.empty()));
        AdaptingAreaController controller = new AdaptingAreaController(area, runtimeState);
        DspOrderItem preparedLine = adaptedPreparedLine("line-1", "target-1", "0000310");
        AdaptingBenchId benchId = new AdaptingBenchId("bench-1");

        List<DependencyBlock> beforeProcessing = dependencyEvaluator.findBlocks(
                associatedOrder,
                runtimeState.snapshot());
        assertEquals(1, beforeProcessing.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, beforeProcessing.getFirst().type());

        area.submitVisit(AdaptingVisit.store(
                new PhysicalToteId("source-tote-1"),
                new OrderSheetKey("adapted-source-1", 1),
                "SC-1",
                List.of(preparedLine)));
        bench.startProcessing();

        assertFalse(runtimeState.snapshot().preparedLineKeys().contains(PreparedLineKey.forPreparedLine(preparedLine)));

        bench.tick(2d);
        assertEquals(AdaptingBenchState.COMPLETED, bench.state());
        assertTrue(store.contains(PreparedLineKey.forPreparedLine(preparedLine)));

        AdaptingBenchCompletion completion = controller.applyBenchCompletion(benchId).orElseThrow();
        assertEquals(AdaptingVisitType.STORE, completion.visit().visitType());
        assertTrue(runtimeState.snapshot().preparedLineKeys().contains(PreparedLineKey.forPreparedLine(preparedLine)));
        assertEquals(AdaptingBenchState.IDLE, bench.state());
        assertTrue(bench.snapshot().activeToteId().isBlank());

        assertTrue(dependencyEvaluator.findBlocks(associatedOrder, runtimeState.snapshot()).isEmpty());
    }

    private static DspSchedulerOrderState associatedOrderState(String orderId, String lineId) {
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "SC-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem(
                        lineId,
                        "product-" + lineId,
                        1,
                        "0000310",
                        DspOrderLineType.ADAPTED,
                        orderId,
                        1,
                        0)),
                0L);

        return new DspSchedulerOrderState(
                order,
                new online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements(
                        false,
                        false,
                        false,
                        true,
                        false,
                        StartLocation.OSR),
                DspOrderStatus.WAITING);
    }

    private static DspOrderItem adaptedPreparedLine(String lineId, String targetOrderId, String pharmacyId) {
        return new DspOrderItem(
                lineId,
                "product-" + lineId,
                1,
                pharmacyId,
                DspOrderLineType.ADAPTED,
                targetOrderId,
                1,
                0);
    }
}
