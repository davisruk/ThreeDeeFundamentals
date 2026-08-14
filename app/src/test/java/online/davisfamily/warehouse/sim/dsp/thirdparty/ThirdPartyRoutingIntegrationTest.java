package online.davisfamily.warehouse.sim.dsp.thirdparty;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

class ThirdPartyRoutingIntegrationTest {

    @Test
    void shouldRequireBothAreasButRemainBlockedUntilAdaptedLineIsReady() {
        NotionalToteOrder order = new NotionalToteOrder(
                "associated-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(
                        line("direct-line", "direct", DspOrderLineType.FULL_PACK),
                        line("adapted-line", "adapted", DspOrderLineType.ADAPTED)),
                0);
        DspRouteDeriver routeDeriver = new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                product("direct", "Y74"),
                product("adapted", "Y75"))));
        DspSchedulerOrderState orderState = new DspSchedulerOrderState(
                order,
                routeDeriver.derive(order),
                DspOrderStatus.WAITING);
        DspDependencyEvaluator dependencyEvaluator = new DspDependencyEvaluator();

        assertTrue(orderState.routeRequirements().requiresThirdParty());
        assertTrue(orderState.routeRequirements().requiresSortable());
        assertEquals(
                List.of(DependencyType.ADAPTED_COMPLETION),
                dependencyEvaluator.findBlocks(orderState, snapshot(orderState, Set.of())).stream()
                        .map(block -> block.type())
                        .toList());

        PreparedLineKey adaptedLineKey = PreparedLineKey.forDispatchLine(order, order.items().get(1));
        assertTrue(dependencyEvaluator.findBlocks(
                orderState,
                snapshot(orderState, Set.of(adaptedLineKey))).isEmpty());
    }

    private static WarehouseSchedulerSnapshot snapshot(
            DspSchedulerOrderState orderState,
            Set<PreparedLineKey> preparedLineKeys) {
        return new WarehouseSchedulerSnapshot(
                List.of(orderState),
                Map.of(),
                preparedLineKeys,
                Optional.empty());
    }

    private static DspOrderItem line(String lineReference, String productId, DspOrderLineType lineType) {
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                "0006515",
                lineType,
                "associated-1",
                1,
                0);
    }

    private static ProductMasterRecord product(String productId, String thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.of(thirdPartyLocation),
                Optional.empty());
    }
}
