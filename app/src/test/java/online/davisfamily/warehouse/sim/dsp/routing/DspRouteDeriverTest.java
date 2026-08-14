package online.davisfamily.warehouse.sim.dsp.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspRouteDeriverTest {

    @Test
    void shouldStartEmptyOrdersAtAv02AndOthersAtOsr() {
        DspRouteDeriver deriver = newDeriver(
                product("product-1", null));

        assertEquals(StartLocation.AV02, deriver.derive(order(OrderType.EMPTY, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.ADAPTED, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.ASSOCIATED, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.FULL_PACK, item("product-1"))).startLocation());
    }

    @Test
    void shouldRequireP2pForAssociatedEmptyAndFullPackOrdersOnly() {
        DspRouteDeriver deriver = newDeriver(
                product("product-1", null));

        assertTrue(deriver.derive(order(OrderType.ASSOCIATED, item("product-1"))).requiresP2p());
        assertTrue(deriver.derive(order(OrderType.EMPTY, item("product-1"))).requiresP2p());
        assertTrue(deriver.derive(order(OrderType.FULL_PACK, item("product-1"))).requiresP2p());
        assertFalse(deriver.derive(order(OrderType.ADAPTED, item("product-1"))).requiresP2p());
    }

    @Test
    void shouldDeriveThirdPartyAndAdaptingRequirementsWithoutManualFlow() {
        DspRouteDeriver deriver = newDeriver(
                product("auto", "Y74"),
                product("sortable", null));

        RouteRequirements requirements = deriver.derive(order(
                OrderType.ADAPTED,
                item("auto", DspOrderLineType.FULL_PACK),
                item("sortable", DspOrderLineType.ADAPTED)));

        assertTrue(requirements.requiresThirdParty());
        assertTrue(requirements.requiresSortable());
        assertFalse(requirements.requiresManual());
        assertFalse(requirements.requiresP2p());
        assertFalse(requirements.requiresManualMerge());
    }

    @Test
    void shouldRequireThirdPartyAndAdaptingForMixedAssociatedOrder() {
        DspRouteDeriver deriver = newDeriver(
                product("direct", "Y74"),
                product("adapted", "Y75"));

        RouteRequirements requirements = deriver.derive(order(
                OrderType.ASSOCIATED,
                item("direct", DspOrderLineType.FULL_PACK),
                item("adapted", DspOrderLineType.ADAPTED)));

        assertTrue(requirements.requiresThirdParty());
        assertTrue(requirements.requiresSortable());
        assertTrue(requirements.requiresP2p());
        assertFalse(requirements.requiresManual());
        assertFalse(requirements.requiresManualMerge());
    }

    @Test
    void shouldRejectManualLinesOutsideActiveSimulationScope() {
        DspRouteDeriver deriver = newDeriver(product("manual", null));

        assertThrows(IllegalArgumentException.class,
                () -> deriver.derive(order(OrderType.ASSOCIATED, item("manual", DspOrderLineType.MANUAL))));
    }

    @Test
    void shouldRejectMissingProductMasterData() {
        DspRouteDeriver deriver = newDeriver(
                product("known", null));

        assertThrows(IllegalArgumentException.class,
                () -> deriver.derive(order(OrderType.ASSOCIATED, item("missing"))));
    }

    private static DspRouteDeriver newDeriver(ProductMasterRecord... products) {
        return new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(products)));
    }

    private static NotionalToteOrder order(OrderType orderType, DspOrderItem... items) {
        return new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                orderType,
                List.of(items),
                0);
    }

    private static DspOrderItem item(String productId) {
        return item(productId, DspOrderLineType.FULL_PACK);
    }

    private static DspOrderItem item(String productId, DspOrderLineType lineType) {
        return new DspOrderItem(
                "item-" + productId,
                productId,
                1,
                "0006515",
                lineType,
                "order-1",
                1,
                0);
    }

    private static ProductMasterRecord product(String productId, String thirdPartyLocation) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.ofNullable(thirdPartyLocation),
                Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }
}
