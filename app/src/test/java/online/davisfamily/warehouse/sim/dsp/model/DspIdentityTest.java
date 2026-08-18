package online.davisfamily.warehouse.sim.dsp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class DspIdentityTest {

    @Test
    void shouldCreateOrderSheetKeyFromOrderIdAndSheetNumber() {
        OrderSheetKey key = new OrderSheetKey(" ORDER-A ", 2);

        assertEquals("ORDER-A", key.orderId());
        assertEquals(2, key.sheetNumber());
    }

    @Test
    void shouldRejectBlankOrderIdAndInvalidSheetNumber() {
        assertThrows(IllegalArgumentException.class, () -> new OrderSheetKey(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new OrderSheetKey(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new OrderSheetKey("ORDER-A", 0));
    }

    @Test
    void shouldTreatDifferentSheetsAsDifferentLogicalIdentities() {
        OrderSheetKey firstSheet = new OrderSheetKey("ORDER-A", 1);
        OrderSheetKey secondSheet = new OrderSheetKey("ORDER-A", 2);

        assertNotEquals(firstSheet, secondSheet);
    }

    @Test
    void shouldValidateAndTrimPhysicalToteId() {
        PhysicalToteId toteId = new PhysicalToteId(" TOTE-100 ");

        assertEquals("TOTE-100", toteId.value());
        assertThrows(IllegalArgumentException.class, () -> new PhysicalToteId(null));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalToteId(" "));
    }

    @Test
    void shouldExposeTypedOrderSheetKeyFromNotionalToteOrder() {
        NotionalToteOrder order = new NotionalToteOrder(
                "ORDER-A",
                "transitional-notional-id",
                "SC-1",
                3,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);

        assertEquals(new OrderSheetKey("ORDER-A", 3), order.orderSheetKey());
    }
}
