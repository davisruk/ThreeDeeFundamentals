package online.davisfamily.warehouse.sim.dsp.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductCategory;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;

class DspRouteDeriverTest {

    @Test
    void shouldStartEmptyOrdersAtAv02AndOthersAtOsr() {
        DspRouteDeriver deriver = newDeriver(
                new ProductMasterRecord("product-1", ProductCategory.AUTOMATED, false));

        assertEquals(StartLocation.AV02, deriver.derive(order(OrderType.EMPTY, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.ADAPTED, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.ASSOCIATED, item("product-1"))).startLocation());
        assertEquals(StartLocation.OSR, deriver.derive(order(OrderType.FULL_PACK, item("product-1"))).startLocation());
    }

    @Test
    void shouldRequireP2pForAssociatedEmptyAndFullPackOrdersOnly() {
        DspRouteDeriver deriver = newDeriver(
                new ProductMasterRecord("product-1", ProductCategory.AUTOMATED, false));

        assertTrue(deriver.derive(order(OrderType.ASSOCIATED, item("product-1"))).requiresP2p());
        assertTrue(deriver.derive(order(OrderType.EMPTY, item("product-1"))).requiresP2p());
        assertTrue(deriver.derive(order(OrderType.FULL_PACK, item("product-1"))).requiresP2p());
        assertFalse(deriver.derive(order(OrderType.ADAPTED, item("product-1"))).requiresP2p());
    }

    @Test
    void shouldDeriveThirdPartySortableAndManualRequirementsFromProductMaster() {
        DspRouteDeriver deriver = newDeriver(
                new ProductMasterRecord("auto", ProductCategory.AUTOMATED, true),
                new ProductMasterRecord("sortable", ProductCategory.SORTABLE, false),
                new ProductMasterRecord("manual", ProductCategory.MANUAL, false));

        RouteRequirements requirements = deriver.derive(order(
                OrderType.ADAPTED,
                item("auto"),
                item("sortable"),
                item("manual")));

        assertTrue(requirements.requiresThirdParty());
        assertTrue(requirements.requiresSortable());
        assertTrue(requirements.requiresManual());
        assertFalse(requirements.requiresP2p());
        assertFalse(requirements.requiresManualMerge());
    }

    @Test
    void shouldRequireManualMergeForAssociatedOrEmptyOrdersWithManualItems() {
        DspRouteDeriver deriver = newDeriver(
                new ProductMasterRecord("manual", ProductCategory.MANUAL, false));

        assertTrue(deriver.derive(order(OrderType.ASSOCIATED, item("manual"))).requiresManualMerge());
        assertTrue(deriver.derive(order(OrderType.EMPTY, item("manual"))).requiresManualMerge());
        assertFalse(deriver.derive(order(OrderType.ADAPTED, item("manual"))).requiresManualMerge());
        assertFalse(deriver.derive(order(OrderType.FULL_PACK, item("manual"))).requiresManualMerge());
    }

    @Test
    void shouldRejectMissingProductMasterData() {
        DspRouteDeriver deriver = newDeriver(
                new ProductMasterRecord("known", ProductCategory.AUTOMATED, false));

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
        return new DspOrderItem("item-" + productId, productId, 1);
    }
}
