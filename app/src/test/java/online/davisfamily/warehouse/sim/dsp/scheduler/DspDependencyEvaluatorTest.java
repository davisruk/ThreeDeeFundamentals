package online.davisfamily.warehouse.sim.dsp.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DependencyType;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

class DspDependencyEvaluatorTest {

    private final DspDependencyEvaluator evaluator = new DspDependencyEvaluator();

    @Test
    void shouldBlockAssociatedAndEmptyUntilAdaptedComplete() {
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(
                orderState(order("assoc", "notional-a", 1, OrderType.ASSOCIATED), route(true, false), DspOrderStatus.WAITING),
                orderState(order("empty", "notional-b", 1, OrderType.EMPTY), route(true, false), DspOrderStatus.WAITING)));

        List<DependencyBlock> associatedBlocks = evaluator.findBlocks(snapshot.orderStates().get(0), snapshot);
        List<DependencyBlock> emptyBlocks = evaluator.findBlocks(snapshot.orderStates().get(1), snapshot);

        assertEquals(1, associatedBlocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, associatedBlocks.getFirst().type());
        assertEquals(1, emptyBlocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, emptyBlocks.getFirst().type());
    }

    @Test
    void shouldNotBlockAdaptedOrFullPackOnAdaptedCompletion() {
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(
                orderState(order("adapted", "notional-a", 1, OrderType.ADAPTED), route(false, false), DspOrderStatus.WAITING),
                orderState(order("full", "notional-b", 1, OrderType.FULL_PACK), route(false, false), DspOrderStatus.WAITING)));

        assertTrue(evaluator.findBlocks(snapshot.orderStates().get(0), snapshot).isEmpty());
        assertTrue(evaluator.findBlocks(snapshot.orderStates().get(1), snapshot).isEmpty());
    }

    @Test
    void shouldBlockLaterSheetUntilPreviousSheetReleasedOrCompleted() {
        DspSchedulerOrderState sheet1Waiting = orderState(order("order-1", "notional-a", 1, OrderType.FULL_PACK), route(false, false), DspOrderStatus.WAITING);
        DspSchedulerOrderState sheet2Waiting = orderState(order("order-2", "notional-a", 2, OrderType.FULL_PACK), route(false, false), DspOrderStatus.WAITING);

        WarehouseSchedulerSnapshot blockedSnapshot = snapshot(List.of(sheet1Waiting, sheet2Waiting));
        List<DependencyBlock> blocked = evaluator.findBlocks(sheet2Waiting, blockedSnapshot);

        assertEquals(1, blocked.size());
        assertEquals(DependencyType.SHEET_SEQUENCE, blocked.getFirst().type());

        WarehouseSchedulerSnapshot releasedSnapshot = snapshot(List.of(
                sheet1Waiting.withStatus(DspOrderStatus.RELEASED),
                sheet2Waiting));
        assertTrue(evaluator.findBlocks(sheet2Waiting, releasedSnapshot).isEmpty());

        WarehouseSchedulerSnapshot completedSnapshot = snapshot(List.of(
                sheet1Waiting.withStatus(DspOrderStatus.COMPLETED),
                sheet2Waiting));
        assertTrue(evaluator.findBlocks(sheet2Waiting, completedSnapshot).isEmpty());
    }

    @Test
    void shouldBlockManualMergeUntilManualReady() {
        DspSchedulerOrderState candidate = orderState(
                order("order-1", "notional-a", 1, OrderType.ASSOCIATED),
                route(true, true),
                DspOrderStatus.WAITING);

        WarehouseSchedulerSnapshot blockedSnapshot = snapshot(List.of(candidate), Set.of());
        List<DependencyBlock> blocked = evaluator.findBlocks(candidate, blockedSnapshot);

        assertEquals(1, blocked.size());
        assertEquals(DependencyType.MANUAL_READY, blocked.getFirst().type());

        WarehouseSchedulerSnapshot readySnapshot = snapshot(List.of(candidate), Set.of());
        assertTrue(evaluator.findBlocks(candidate, readySnapshot).isEmpty());
    }

    @Test
    void shouldReturnAllApplicableDependencyBlocks() {
        DspSchedulerOrderState sheet1Waiting = orderState(
                order("order-1", "notional-a", 1, OrderType.ASSOCIATED),
                route(true, true),
                DspOrderStatus.WAITING);
        DspSchedulerOrderState sheet2Waiting = orderState(
                order("order-2", "notional-a", 2, OrderType.ASSOCIATED),
                route(true, true),
                DspOrderStatus.WAITING);

        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(sheet1Waiting, sheet2Waiting));
        List<DependencyBlock> blocks = evaluator.findBlocks(sheet2Waiting, snapshot);

        assertEquals(3, blocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, blocks.get(0).type());
        assertEquals(DependencyType.SHEET_SEQUENCE, blocks.get(1).type());
        assertEquals(DependencyType.MANUAL_READY, blocks.get(2).type());
    }

    private static WarehouseSchedulerSnapshot snapshot(List<DspSchedulerOrderState> orderStates) {
        return snapshot(orderStates, Set.of());
    }

    private static WarehouseSchedulerSnapshot snapshot(
            List<DspSchedulerOrderState> orderStates,
            Set<PreparedLineKey> preparedLineKeys) {
        return new WarehouseSchedulerSnapshot(
                orderStates,
                Map.of(),
                preparedLineKeys,
                Optional.empty());
    }

    private static DspSchedulerOrderState orderState(
            NotionalToteOrder order,
            online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements routeRequirements,
            DspOrderStatus status) {
        return new DspSchedulerOrderState(order, routeRequirements, status);
    }

    private static NotionalToteOrder order(String orderId, String notionalToteId, int sheetNumber, OrderType orderType) {
        return new NotionalToteOrder(
                orderId,
                notionalToteId,
                "sc-1",
                sheetNumber,
                orderType,
                List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                0);
    }

    private static online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements route(
            boolean requiresP2p,
            boolean requiresManualMerge) {
        return new online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements(
                false,
                false,
                requiresManualMerge,
                requiresP2p,
                requiresManualMerge,
                StartLocation.OSR);
    }
}
