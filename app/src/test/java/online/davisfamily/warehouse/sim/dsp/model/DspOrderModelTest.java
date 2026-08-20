package online.davisfamily.warehouse.sim.dsp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class DspOrderModelTest {

    @Test
    void shouldParseKnownDspOrderLineTypeCodes() {
        assertEquals(DspOrderLineType.MANUAL, DspOrderLineType.fromCode("01"));
        assertEquals(DspOrderLineType.ADAPTED, DspOrderLineType.fromCode("02"));
        assertEquals(DspOrderLineType.FULL_PACK, DspOrderLineType.fromCode("05"));
        assertEquals("01", DspOrderLineType.MANUAL.code());
        assertEquals("02", DspOrderLineType.ADAPTED.code());
        assertEquals("05", DspOrderLineType.FULL_PACK.code());
    }

    @Test
    void shouldRejectUnknownDspOrderLineTypeCode() {
        assertThrows(IllegalArgumentException.class, () -> DspOrderLineType.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> DspOrderLineType.fromCode(" "));
        assertThrows(IllegalArgumentException.class, () -> DspOrderLineType.fromCode("99"));
    }

    @Test
    void shouldRejectBlankIdentifiers() {
        DspOrderItem item = validItem();

        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord(" ", "Product", Optional.empty(), Optional.empty()));
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
    void shouldTrimPatientAndPrescriptionIdentity() {
        DspOrderItem item = new DspOrderItem(
                " item-1 ",
                " product-1 ",
                2,
                " 0006515 ",
                " patient-1 ",
                " prescription-1 ",
                DspOrderLineType.ADAPTED,
                " TOTE0007170299 ",
                3,
                1);

        assertEquals("item-1", item.lineReference());
        assertEquals("product-1", item.productId());
        assertEquals("0006515", item.pharmacyId());
        assertEquals("patient-1", item.patientId());
        assertEquals("prescription-1", item.prescriptionId());
        assertEquals(DspOrderLineType.ADAPTED, item.lineType());
        assertEquals("TOTE0007170299", item.referenceOrderId());
        assertEquals(3, item.referenceSheetNumber());
        assertEquals(1, item.numberOfPacksPicked());
    }

    @Test
    void shouldRejectMissingPatientOrPrescriptionIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem(
                        "item-1", "product-1", 1, "0006515", null, "prescription-1",
                        DspOrderLineType.FULL_PACK, "order-1", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem(
                        "item-1", "product-1", 1, "0006515", "patient-1", " ",
                        DspOrderLineType.FULL_PACK, "order-1", 1, 0));
    }

    @Test
    void shouldRejectBlankPharmacyAndReferenceOrderIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 1, " ", DspOrderLineType.FULL_PACK, "order-1", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 1, "0006515", DspOrderLineType.FULL_PACK, " ", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 1, "0006515", null, "order-1", 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 1, "0006515", DspOrderLineType.FULL_PACK, "order-1", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DspOrderItem("item-1", "product-1", 1, "0006515", DspOrderLineType.FULL_PACK, "order-1", 1, -1));
    }

    @Test
    void shouldKeepLegacyDspOrderItemConstructorForDebugData() {
        DspOrderItem item = new DspOrderItem("item-1", "product-1", 1);

        assertEquals("item-1", item.lineReference());
        assertEquals("product-1", item.productId());
        assertEquals(1, item.quantity());
        assertEquals("UNKNOWN", item.pharmacyId());
        assertEquals("fixture-patient-item-1", item.patientId());
        assertEquals("fixture-prescription-item-1", item.prescriptionId());
        assertEquals(DspOrderLineType.FULL_PACK, item.lineType());
        assertEquals("item-1", item.referenceOrderId());
        assertEquals(1, item.referenceSheetNumber());
        assertEquals(0, item.numberOfPacksPicked());
    }

    @Test
    void shouldRejectInvalidProductMasterFieldsAndNullOrderType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord("product-1", null, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord("product-1", "Product", null, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductMasterRecord("product-1", "Product", Optional.empty(), null));
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
        assertThrows(IllegalArgumentException.class,
                () -> new NotionalToteOrder(
                        "order-1",
                        "notional-1",
                        "sc-1",
                        1,
                        OrderType.ASSOCIATED,
                        List.of(validItem()),
                        -1,
                        0));
    }

    @Test
    void shouldRetainOrderPriorityAndSupportLegacyConstructor() {
        NotionalToteOrder prioritized = new NotionalToteOrder(
                "order-1",
                "notional-1",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(validItem()),
                999,
                0);
        NotionalToteOrder legacy = new NotionalToteOrder(
                "order-2",
                "notional-2",
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(validItem()),
                1);

        assertEquals(999, prioritized.orderPriority());
        assertEquals(0, legacy.orderPriority());
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
