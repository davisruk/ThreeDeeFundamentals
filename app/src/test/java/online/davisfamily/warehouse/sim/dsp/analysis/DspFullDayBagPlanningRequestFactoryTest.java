package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningRequest;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspFullDayBagPlanningRequestFactoryTest {
    private final DspFullDayBagPlanningRequestFactory factory =
            new DspFullDayBagPlanningRequestFactory();

    @Test
    void shouldRemainStrictForMissingAdaptedSource() {
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                List.of(line("alias-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(data(
                        List.of(product("product")),
                        List.of(fulfilment),
                        List.of(manifest(fulfilment)))));

        assertTrue(exception.getMessage().contains("Missing ADAPTED source line"));
    }

    @Test
    void shouldRemainStrictForDuplicateAdaptedSources() {
        NotionalToteOrder firstSource = order(
                "source-one",
                1,
                OrderType.ADAPTED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                1);
        NotionalToteOrder secondSource = order(
                "source-two",
                1,
                OrderType.ADAPTED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                2);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                3);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(data(
                        List.of(product("product")),
                        List.of(firstSource, secondSource, fulfilment),
                        List.of())));

        assertTrue(exception.getMessage().contains("Duplicate ADAPTED prepared-line key"));
    }

    @Test
    void shouldRemainStrictForDuplicateAdaptedFulfilments() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                1);
        NotionalToteOrder firstFulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                2);
        NotionalToteOrder secondFulfilment = order(
                "target-order",
                2,
                OrderType.ASSOCIATED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                3);

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(data(
                        List.of(product("product")),
                        List.of(source, firstFulfilment, secondFulfilment),
                        List.of())));
    }

    @Test
    void shouldRemainStrictForAdaptedIdentityMismatch() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product-a", "target-order")),
                1);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product-b", "target-order")),
                2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(data(
                        List.of(product("product-a"), product("product-b")),
                        List.of(source, fulfilment),
                        List.of())));

        assertTrue(exception.getMessage().contains("source and fulfilment line mismatch"));
    }

    @Test
    void shouldKeepPackCountFieldsBehaviourallyIrrelevant() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order", 1, 0)),
                1);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order", 99, 12)),
                2);

        BagPlanningRequest request = factory.create(data(
                List.of(product("product")),
                List.of(source, fulfilment),
                List.of()));

        assertEquals(1, request.packDemands().size());
        assertEquals(source.orderSheetKey(),
                request.packDemands().getFirst().sourceProvenance().sourceOrderSheetKey());
    }

    private static LoadedDspData data(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests) {
        List<DspOrderItem> preparedLines = orders.stream()
                .filter(order -> order.orderType() == OrderType.ADAPTED)
                .flatMap(order -> order.items().stream())
                .toList();
        Set<PreparedLineKey> preparedLineKeys = preparedLines.stream()
                .map(PreparedLineKey::forPreparedLine)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return new LoadedDspData(
                products,
                orders,
                preparedLines,
                preparedLineKeys,
                Set.of(),
                manifests,
                DspDatasetLoadReport.empty());
    }

    private static NotionalToteOrder order(
            String orderId,
            int sheetNumber,
            OrderType orderType,
            List<DspOrderItem> items,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                "104",
                sheetNumber,
                orderType,
                items,
                999,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(NotionalToteOrder order) {
        return new InboundToteManifest(
                new PhysicalToteId("tote-" + order.orderId()),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                order.sequenceNumber());
    }

    private static DspOrderItem line(
            String lineReference,
            DspOrderLineType lineType,
            String productId,
            String referenceOrderId) {
        return line(lineReference, lineType, productId, referenceOrderId, 1, 0);
    }

    private static DspOrderItem line(
            String lineReference,
            DspOrderLineType lineType,
            String productId,
            String referenceOrderId,
            int quantity,
            int numberOfPacksPicked) {
        return new DspOrderItem(
                lineReference,
                productId,
                quantity,
                "pharmacy",
                "patient",
                "prescription",
                lineType,
                referenceOrderId,
                1,
                numberOfPacksPicked);
    }

    private static ProductMasterRecord product(String productId) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.empty(),
                Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }
}
