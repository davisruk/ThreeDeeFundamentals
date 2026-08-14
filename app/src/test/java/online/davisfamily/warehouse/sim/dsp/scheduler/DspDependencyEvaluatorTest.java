package online.davisfamily.warehouse.sim.dsp.scheduler;

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
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

class DspDependencyEvaluatorTest {

    private final DspDependencyEvaluator evaluator = new DspDependencyEvaluator();

    @Test
    void shouldBlockAssociatedUntilRequiredAdaptedLinesAreReady() {
        DspSchedulerOrderState associated = orderState(
                dispatchOrder(
                        "assoc",
                        "notional-a",
                        1,
                        OrderType.ASSOCIATED,
                        line("adapted-line", DspOrderLineType.ADAPTED),
                        line("full-pack-line", DspOrderLineType.FULL_PACK)),
                route(true, false),
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(associated));

        List<DependencyBlock> associatedBlocks = evaluator.findBlocks(associated, snapshot);

        assertEquals(1, associatedBlocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, associatedBlocks.getFirst().type());
        assertTrue(associatedBlocks.getFirst().reason().contains("assoc"));
        assertTrue(associatedBlocks.getFirst().reason().contains("adapted-line"));
    }

    @Test
    void shouldBlockEmptyUntilRequiredAdaptedLinesAreReady() {
        DspSchedulerOrderState emptyOrder = orderState(
                dispatchOrder(
                        "empty",
                        "notional-b",
                        1,
                        OrderType.EMPTY,
                        line("adapted-line", DspOrderLineType.ADAPTED)),
                route(true, false),
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(emptyOrder));

        List<DependencyBlock> blocks = evaluator.findBlocks(emptyOrder, snapshot);

        assertEquals(1, blocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, blocks.getFirst().type());
        assertTrue(blocks.getFirst().reason().contains("empty"));
        assertTrue(blocks.getFirst().reason().contains("adapted-line"));
    }

    @Test
    void shouldBlockAssociatedUntilRequiredManualLinesAreReady() {
        DspSchedulerOrderState associated = orderState(
                dispatchOrder(
                        "assoc",
                        "notional-a",
                        1,
                        OrderType.ASSOCIATED,
                        line("manual-line", DspOrderLineType.MANUAL),
                        line("full-pack-line", DspOrderLineType.FULL_PACK)),
                route(true, true),
                DspOrderStatus.WAITING);
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(associated));

        List<DependencyBlock> blocked = evaluator.findBlocks(associated, snapshot);

        assertEquals(1, blocked.size());
        assertEquals(DependencyType.MANUAL_READY, blocked.getFirst().type());
        assertTrue(blocked.getFirst().reason().contains("assoc"));
        assertTrue(blocked.getFirst().reason().contains("manual-line"));
    }

    @Test
    void shouldNotBlockWhenAllPreparedLinesAreReady() {
        DspSchedulerOrderState candidate = orderState(
                dispatchOrder(
                        "order-1",
                        "notional-a",
                        1,
                        OrderType.ASSOCIATED,
                        line("adapted-line", DspOrderLineType.ADAPTED),
                        line("manual-line", DspOrderLineType.MANUAL),
                        line("full-pack-line", DspOrderLineType.FULL_PACK)),
                route(true, true),
                DspOrderStatus.WAITING);
        Set<PreparedLineKey> preparedLineKeys = Set.of(
                PreparedLineKey.forDispatchLine(candidate.order(), candidate.order().items().get(0)),
                PreparedLineKey.forDispatchLine(candidate.order(), candidate.order().items().get(1)));
        WarehouseSchedulerSnapshot readySnapshot = snapshot(List.of(candidate), preparedLineKeys);

        assertTrue(evaluator.findBlocks(candidate, readySnapshot).isEmpty());
    }

    @Test
    void shouldNotRequirePreparedLinesForFullPackDispatchLines() {
        DspSchedulerOrderState candidate = orderState(
                dispatchOrder(
                        "order-1",
                        "notional-a",
                        1,
                        OrderType.ASSOCIATED,
                        line("full-pack-line-a", DspOrderLineType.FULL_PACK),
                        line("full-pack-line-b", DspOrderLineType.FULL_PACK)),
                route(true, false),
                DspOrderStatus.WAITING);

        assertTrue(evaluator.findBlocks(candidate, snapshot(List.of(candidate))).isEmpty());
    }

    @Test
    void shouldNotBlockAdaptedOrFullPackOrdersOnPreparedLineReadiness() {
        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(
                orderState(
                        dispatchOrder("adapted", "notional-a", 1, OrderType.ADAPTED, line("adapted-line", DspOrderLineType.ADAPTED)),
                        route(false, false),
                        DspOrderStatus.WAITING),
                orderState(
                        dispatchOrder("full", "notional-b", 1, OrderType.FULL_PACK, line("full-pack-line", DspOrderLineType.FULL_PACK)),
                        route(false, false),
                        DspOrderStatus.WAITING)));

        assertTrue(evaluator.findBlocks(snapshot.orderStates().get(0), snapshot).isEmpty());
        assertTrue(evaluator.findBlocks(snapshot.orderStates().get(1), snapshot).isEmpty());
    }

    @Test
    void shouldBlockLaterSheetUntilPreviousSheetReleasedOrCompleted() {
        DspSchedulerOrderState sheet1Waiting = orderState(
                dispatchOrder("order-1", "notional-a", 1, OrderType.FULL_PACK, line("full-pack-line-a", DspOrderLineType.FULL_PACK)),
                route(false, false),
                DspOrderStatus.WAITING);
        DspSchedulerOrderState sheet2Waiting = orderState(
                dispatchOrder("order-2", "notional-a", 2, OrderType.FULL_PACK, line("full-pack-line-b", DspOrderLineType.FULL_PACK)),
                route(false, false),
                DspOrderStatus.WAITING);

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
    void shouldReturnAllApplicableDependencyBlocks() {
        DspSchedulerOrderState sheet1Waiting = orderState(
                dispatchOrder(
                        "order-1",
                        "notional-a",
                        1,
                        OrderType.ASSOCIATED,
                        line("manual-line-a", DspOrderLineType.MANUAL),
                        line("adapted-line-a", DspOrderLineType.ADAPTED)),
                route(true, true),
                DspOrderStatus.WAITING);
        DspSchedulerOrderState sheet2Waiting = orderState(
                dispatchOrder(
                        "order-2",
                        "notional-a",
                        2,
                        OrderType.ASSOCIATED,
                        line("manual-line-b", DspOrderLineType.MANUAL),
                        line("adapted-line-b", DspOrderLineType.ADAPTED)),
                route(true, true),
                DspOrderStatus.WAITING);

        WarehouseSchedulerSnapshot snapshot = snapshot(List.of(sheet1Waiting, sheet2Waiting));
        List<DependencyBlock> blocks = evaluator.findBlocks(sheet2Waiting, snapshot);

        assertEquals(3, blocks.size());
        assertEquals(DependencyType.ADAPTED_COMPLETION, blocks.get(0).type());
        assertEquals(DependencyType.SHEET_SEQUENCE, blocks.get(1).type());
        assertEquals(DependencyType.MANUAL_READY, blocks.get(2).type());
        assertFalse(blocks.get(0).reason().isBlank());
        assertFalse(blocks.get(2).reason().isBlank());
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

    private static NotionalToteOrder dispatchOrder(
            String orderId,
            String notionalToteId,
            int sheetNumber,
            OrderType orderType,
            DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                notionalToteId,
                "sc-1",
                sheetNumber,
                orderType,
                List.of(items),
                0);
    }

    private static DspOrderItem line(String lineReference, DspOrderLineType lineType) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                "0006515",
                lineType,
                "prepared-order-" + lineReference,
                1,
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
