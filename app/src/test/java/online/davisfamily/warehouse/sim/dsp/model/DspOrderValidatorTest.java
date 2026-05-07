package online.davisfamily.warehouse.sim.dsp.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DspOrderValidatorTest {

    private final DspOrderValidator validator = new DspOrderValidator();

    @Test
    void shouldAcceptPharmacyPureAssociatedEmptyAndFullPackOrders() {
        assertDoesNotThrow(() -> validator.validateForScheduler(order(
                "assoc-1",
                OrderType.ASSOCIATED,
                item("line-1", "0006515", DspOrderLineType.ADAPTED),
                item("line-2", "0006515", DspOrderLineType.FULL_PACK))));
        assertDoesNotThrow(() -> validator.validateForScheduler(order(
                "empty-1",
                OrderType.EMPTY,
                item("line-1", "0006515", DspOrderLineType.MANUAL))));
        assertDoesNotThrow(() -> validator.validateForScheduler(order(
                "full-1",
                OrderType.FULL_PACK,
                item("line-1", "0006515", DspOrderLineType.FULL_PACK),
                item("line-2", "0006515", DspOrderLineType.FULL_PACK))));
    }

    @Test
    void shouldRejectMixedPharmacyAssociatedEmptyAndFullPackOrders() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateForScheduler(order(
                "assoc-1",
                OrderType.ASSOCIATED,
                item("line-1", "0006515", DspOrderLineType.ADAPTED),
                item("line-2", "0006461", DspOrderLineType.FULL_PACK))));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForScheduler(order(
                "empty-1",
                OrderType.EMPTY,
                item("line-1", "0006515", DspOrderLineType.MANUAL),
                item("line-2", "0006461", DspOrderLineType.ADAPTED))));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForScheduler(order(
                "full-1",
                OrderType.FULL_PACK,
                item("line-1", "0006515", DspOrderLineType.FULL_PACK),
                item("line-2", "0006461", DspOrderLineType.FULL_PACK))));
    }

    @Test
    void shouldAllowMixedPharmacyAdaptedOrders() {
        NotionalToteOrder adaptedOrder = order(
                "adapted-1",
                OrderType.ADAPTED,
                item("line-1", "0006515", DspOrderLineType.ADAPTED),
                item("line-2", "0006461", DspOrderLineType.ADAPTED));

        assertDoesNotThrow(() -> validator.validateForScheduler(adaptedOrder));
        assertFalse(validator.isPharmacyPure(adaptedOrder));
    }

    @Test
    void shouldExposePharmacyIdsForDiagnostics() {
        NotionalToteOrder order = order(
                "adapted-1",
                OrderType.ADAPTED,
                item("line-1", "0006515", DspOrderLineType.ADAPTED),
                item("line-2", "0006461", DspOrderLineType.ADAPTED),
                item("line-3", "0006515", DspOrderLineType.FULL_PACK));

        assertEquals(Set.of("0006515", "0006461"), validator.pharmacyIds(order));
        assertTrue(validator.isPharmacyPure(order(
                "assoc-1",
                OrderType.ASSOCIATED,
                item("line-1", "0006515", DspOrderLineType.ADAPTED),
                item("line-2", "0006515", DspOrderLineType.FULL_PACK))));
    }

    private static NotionalToteOrder order(String orderId, OrderType orderType, DspOrderItem... items) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                orderType,
                List.of(items),
                0);
    }

    private static DspOrderItem item(String itemId, String pharmacyId, DspOrderLineType lineType) {
        return new DspOrderItem(
                itemId,
                "product-" + itemId,
                1,
                pharmacyId,
                lineType,
                "target-order-1",
                1,
                0);
    }
}
