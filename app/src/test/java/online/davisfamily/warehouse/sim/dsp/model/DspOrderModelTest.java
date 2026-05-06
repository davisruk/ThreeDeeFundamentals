package online.davisfamily.warehouse.sim.dsp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DspOrderModelTest {

    @Test
    void shouldRejectBlankIdentifiers() {
        DspOrderItem item = validItem();

        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord(" ", ProductCategory.AUTOMATED, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("", "product-1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", " ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder("", "notional-1", "sc-1", 1, OrderType.ASSOCIATED, List.of(item), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder("order-1", "", "sc-1", 1, OrderType.ASSOCIATED, List.of(item), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder("order-1", "notional-1", "", 1, OrderType.ASSOCIATED, List.of(item), 0));
    }

    @Test
    void shouldRejectNullCategoryAndOrderType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord("product-1", null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        1,
                        null,
                        List.of(validItem()),
                        0));
    }

    @Test
    void shouldRejectEmptyOrderItems() {
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        1,
                        OrderType.ASSOCIATED,
                        List.of(),
                        0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        1,
                        OrderType.ASSOCIATED,
                        null,
                        0));
    }

    @Test
    void shouldRejectInvalidQuantitySheetAndSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        0,
                        OrderType.ASSOCIATED,
                        List.of(validItem()),
                        0));
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        1,
                        OrderType.ASSOCIATED,
                        List.of(validItem()),
                        -1));
    }

    @Test
    void shouldDefensivelyCopyOrderItems() {
        List<DspOrderItem> sourceItems = new ArrayList<>();
        sourceItems.add(validItem());

        NotionalToteOrder order = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                sourceItems,
                0);

        sourceItems.add(new DspOrderItem("item-2", "product-2", 1));

        assertEquals(1, order.items().size());
        assertThrows(UnsupportedOperationException.class,
                () -> order.items().add(new DspOrderItem("item-3", "product-3", 1)));
    }

    private static DspOrderItem validItem() {
        return new DspOrderItem("item-1", "product-1", 1);
    }
}
