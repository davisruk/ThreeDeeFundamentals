package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

class StationProcessingOrderCatalogTest {

    @Test
    void shouldRetainExactOrdersAndDeterministicInsertionOrder() {
        NotionalToteOrder first = order("order-1", 1, 1);
        NotionalToteOrder second = order("order-2", 2, 2);
        StationProcessingOrderCatalog catalog = new StationProcessingOrderCatalog(
                List.of(first, second));

        assertSame(first, catalog.find(new OrderSheetKey("order-1", 1)).orElseThrow());
        assertSame(second, catalog.require(new OrderSheetKey("order-2", 2)));
        assertEquals(List.of(first, second), catalog.orders());
        assertThrows(UnsupportedOperationException.class, () -> catalog.orders().add(first));
        assertTrue(catalog.find(new OrderSheetKey("unknown", 1)).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> catalog.require(new OrderSheetKey("unknown", 1)));
    }

    @Test
    void shouldRejectDuplicateOrderSheetsBeforeBuildingTheIndex() {
        NotionalToteOrder first = order("duplicate", 1, 1);
        NotionalToteOrder duplicate = order("duplicate", 1, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingOrderCatalog(List.of(first, duplicate)));
    }

    @Test
    void shouldRejectNullInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingOrderCatalog(null));
        List<NotionalToteOrder> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new StationProcessingOrderCatalog(withNull));
        StationProcessingOrderCatalog catalog = new StationProcessingOrderCatalog(List.of());
        assertThrows(IllegalArgumentException.class, () -> catalog.find(null));
        assertThrows(IllegalArgumentException.class, () -> catalog.require(null));
    }

    private static NotionalToteOrder order(String orderId, int sheetNumber, long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                "tote-" + orderId,
                "SC-1",
                sheetNumber,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem("line-" + orderId, "product-1", 1)),
                0,
                sequenceNumber);
    }
}
