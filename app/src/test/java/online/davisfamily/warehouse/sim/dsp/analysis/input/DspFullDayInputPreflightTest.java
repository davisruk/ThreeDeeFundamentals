package online.davisfamily.warehouse.sim.dsp.analysis.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DspFullDayInputPreflightTest {
    @Test
    void shouldReportAndRemoveAssociatedAliasWithoutSource() {
        NotionalToteOrder aliasOrder = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("alias-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(aliasOrder),
                List.of(manifest(aliasOrder, "alias-tote")));

        assertEquals(List.of(aliasOrder), projection.reportableOrders());
        assertTrue(projection.executableData().orders().isEmpty());
        assertTrue(projection.executableData().preparedLines().isEmpty());
        assertTrue(projection.executableData().loadedPreparedLineKeys().isEmpty());
        assertTrue(projection.executableData().inboundToteManifests().isEmpty());
        assertEquals(1, projection.rejectionCatalog().rejectedLineCount());
        DspRejectedLine rejected = projection.rejectionCatalog().rejectedLines().getFirst();
        assertEquals(DspInputRejectionReason.MISSING_ADAPTED_SOURCE, rejected.reason());
        assertEquals(Optional.of(aliasOrder.orderSheetKey()), rejected.targetOrderSheetKey());
        assertEquals(
                Optional.of(new PreparedLineKey("target-order", "alias-line")),
                rejected.preparedLineKey());
    }

    @Test
    void shouldRejectOrphanSourceWithoutExecutableSourceWork() {
        NotionalToteOrder sourceOrder = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("source-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(sourceOrder),
                List.of(manifest(sourceOrder, "source-tote")));

        assertTrue(projection.executableData().orders().isEmpty());
        assertTrue(projection.executableData().preparedLines().isEmpty());
        assertEquals(
                DspInputRejectionReason.MISSING_ADAPTED_FULFILMENT,
                projection.rejectionCatalog().rejectedLines().getFirst().reason());
        assertTrue(projection.rejectionCatalog().rejectedLines().getFirst()
                .targetOrderSheetKey().isEmpty());
    }

    @Test
    void shouldRejectEveryDuplicateSourceParticipant() {
        NotionalToteOrder firstSource = order(
                "source-one",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        NotionalToteOrder secondSource = order(
                "source-two",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                1);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                2);

        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(firstSource, secondSource, fulfilment),
                List.of());

        assertEquals(3, projection.rejectionCatalog().rejectedLineCount());
        assertEquals(3, projection.rejectionCatalog().count(
                DspInputRejectionReason.DUPLICATE_ADAPTED_SOURCE));
        assertTrue(projection.rejectionCatalog().rejectedLines().stream()
                .allMatch(line -> line.reason() == DspInputRejectionReason.DUPLICATE_ADAPTED_SOURCE));
        assertTrue(projection.executableData().orders().isEmpty());
    }

    @Test
    void shouldRejectEveryDuplicateFulfilmentParticipant() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        NotionalToteOrder firstFulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "source-order")),
                1);
        NotionalToteOrder secondFulfilment = order(
                "target-order",
                2,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "source-order")),
                2);

        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(source, firstFulfilment, secondFulfilment),
                List.of());

        assertEquals(3, projection.rejectionCatalog().rejectedLineCount());
        assertEquals(3, projection.rejectionCatalog().count(
                DspInputRejectionReason.DUPLICATE_ADAPTED_FULFILMENT));
        assertTrue(projection.rejectionCatalog().rejectedLines().stream()
                .allMatch(line -> line.reason() == DspInputRejectionReason.DUPLICATE_ADAPTED_FULFILMENT));
        assertTrue(projection.executableData().orders().isEmpty());
    }

    @Test
    void shouldRejectCompletePairForEveryNamedIdentityMismatch() {
        for (String mismatch : List.of(
                "productId",
                "pharmacyId",
                "patientId",
                "prescriptionId",
                "lineType",
                "serviceCentreId")) {
            DspOrderItem sourceLine = line(
                    "shared-line",
                    DspOrderLineType.ADAPTED,
                    "product",
                    "target-order");
            DspOrderItem fulfilmentLine = line(
                    "shared-line",
                    "lineType".equals(mismatch)
                            ? DspOrderLineType.FULL_PACK
                            : DspOrderLineType.ADAPTED,
                    "product",
                    "source-order");
            sourceLine = withIdentityDifference(sourceLine, mismatch, false);
            fulfilmentLine = withIdentityDifference(fulfilmentLine, mismatch, true);
            String sourceCentre = "serviceCentreId".equals(mismatch) ? "104" : "104";
            String fulfilmentCentre = "serviceCentreId".equals(mismatch) ? "108" : "104";

            NotionalToteOrder source = order(
                    "source-order",
                    1,
                    OrderType.ADAPTED,
                    sourceCentre,
                    List.of(sourceLine),
                    0);
            NotionalToteOrder fulfilment = order(
                    "target-order",
                    1,
                    OrderType.ASSOCIATED,
                    fulfilmentCentre,
                    List.of(fulfilmentLine),
                    1);

            DspFullDayInputProjection projection = project(
                    List.of(product("product"), product("other-product")),
                    List.of(source, fulfilment),
                    List.of());

            assertEquals(2, projection.rejectionCatalog().rejectedLineCount(), mismatch);
            assertTrue(projection.rejectionCatalog().rejectedLines().stream()
                    .allMatch(line -> line.reason()
                            == DspInputRejectionReason.ADAPTED_SOURCE_FULFILMENT_MISMATCH),
                    mismatch);
            assertTrue(projection.executableData().orders().isEmpty(), mismatch);
        }
    }

    @Test
    void shouldKeepValidPairAndItsSourceIdentityUnchanged() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "source-order")),
                1);

        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(source, fulfilment),
                List.of(manifest(source, "source-tote"), manifest(fulfilment, "fulfilment-tote")));

        assertEquals(List.of(source, fulfilment), projection.reportableOrders());
        assertEquals(List.of(source, fulfilment), projection.executableData().orders());
        assertEquals(List.of(source.items().getFirst()), projection.executableData().preparedLines());
        assertEquals(Set.of(new PreparedLineKey("target-order", "shared-line")),
                projection.executableData().loadedPreparedLineKeys());
        assertEquals(2, projection.executableData().retainedInputLines().size());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
    }

    @Test
    void shouldExcludeCompletePairForUnresolvedProductWithoutRelabellingIt() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "missing-product", "target-order")),
                0);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "missing-product", "source-order")),
                1);
        DspDatasetLoadReport report = new DspDatasetLoadReport(
                0,
                0,
                0,
                List.of(
                        new UnresolvedProductLine("source-order", "shared-line", "missing-product", "104"),
                        new UnresolvedProductLine("target-order", "shared-line", "missing-product", "104")),
                List.of());

        DspFullDayInputProjection projection = project(
                List.of(),
                List.of(source, fulfilment),
                List.of(),
                report);

        assertEquals(2, projection.reportableOrders().size());
        assertTrue(projection.executableData().orders().isEmpty());
        assertTrue(projection.executableData().preparedLines().isEmpty());
        assertTrue(projection.rejectionCatalog().rejectedLines().isEmpty());
        assertEquals(report, projection.executableData().report());
    }

    @Test
    void shouldFilterRejectedLineOnceAndPreserveSiblingOrderAndManifest() {
        NotionalToteOrder order = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(
                        line("rejected-alias", DspOrderLineType.ADAPTED, "product-a", "target-order"),
                        line("sibling", DspOrderLineType.FULL_PACK, "product-b", "target-order")),
                0);
        DspFullDayInputProjection projection = project(
                List.of(product("product-a"), product("product-b")),
                List.of(order),
                List.of(manifest(order, "target-tote")));

        assertEquals(List.of("sibling"), projection.executableData().orders().getFirst().items()
                .stream().map(DspOrderItem::lineReference).toList());
        assertEquals(List.of("sibling"), projection.executableData().inboundToteManifests()
                .getFirst().items().stream().map(DspOrderItem::lineReference).toList());
        assertEquals(List.of("rejected-alias"), projection.rejectionCatalog().rejectedLines().stream()
                .map(rejected -> rejected.orderItem().lineReference()).toList());
    }

    @Test
    void shouldRemoveAllRejectedPhysicalAndEmptyOrders() {
        NotionalToteOrder physical = order(
                "physical-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("physical-alias", DspOrderLineType.ADAPTED, "product", "physical-order")),
                0);
        NotionalToteOrder empty = order(
                "empty-order",
                1,
                OrderType.EMPTY,
                "104",
                List.of(line("empty-alias", DspOrderLineType.ADAPTED, "product", "empty-order")),
                1);

        DspFullDayInputProjection projection = project(
                List.of(product("product")),
                List.of(physical, empty),
                List.of(manifest(physical, "physical-tote")));

        assertTrue(projection.executableData().orders().isEmpty());
        assertTrue(projection.executableData().inboundToteManifests().isEmpty());
        assertEquals(2, projection.rejectionCatalog().rejectedLineCount());
    }

    @Test
    void shouldBuildImmutableIndexesOnceAndReuseThem() {
        NotionalToteOrder alias = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("alias-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        DspInputRejectionCatalog catalog = project(
                List.of(product("product")),
                List.of(alias),
                List.of()).rejectionCatalog();

        assertSame(catalog.countsByReason(), catalog.countsByReason());
        assertSame(catalog.rejectedLinesByTargetOrder(), catalog.rejectedLinesByTargetOrder());
        assertSame(
                catalog.rejectedLinesForTargetOrder(alias.orderSheetKey()),
                catalog.rejectedLinesForTargetOrder(alias.orderSheetKey()));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.countsByReason().put(DspInputRejectionReason.MALFORMED_12N_MESSAGE, 99));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.rejectedLinesByTargetOrder().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.rejectedLinesForTargetOrder(alias.orderSheetKey()).clear());
    }

    @Test
    void shouldQuarantineGroupScopedRuntimeFailureWithCompleteDiagnostics() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "source-order")),
                1);

        DspFullDayInputPreflight preflight = new DspFullDayInputPreflight(
                key -> {
                    throw new IllegalStateException("injected group failure");
                });
        DspFullDayInputProjection projection = preflight.project(
                data(List.of(product("product")), List.of(source, fulfilment), List.of(),
                        DspDatasetLoadReport.empty()),
                DspInputRejectionCatalog.empty());

        assertEquals(2, projection.rejectionCatalog().rejectedLineCount());
        assertTrue(projection.rejectionCatalog().rejectedLines().stream()
                .allMatch(line -> line.reason() == DspInputRejectionReason.UNCLASSIFIED_CORRELATION_ANOMALY));
        assertTrue(projection.rejectionCatalog().rejectedLines().stream()
                .allMatch(line -> line.diagnostic().contains("REQUIRES INVESTIGATION")
                        && line.exceptionClassName().isPresent()
                        && !line.stackTraceLines().isEmpty()));
    }

    @Test
    void shouldKeepFailuresOutsideAnAttributableGroupFatal() {
        NotionalToteOrder order = order(
                "order",
                1,
                OrderType.FULL_PACK,
                "104",
                List.of(line("line", DspOrderLineType.FULL_PACK, "product", "order")),
                0);

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputPreflight().project(
                        data(List.of(product("product"), product("product")), List.of(order), List.of(),
                                DspDatasetLoadReport.empty()),
                        DspInputRejectionCatalog.empty()));
    }

    @Test
    void shouldNotCatchErrorFromGroupValidation() {
        NotionalToteOrder source = order(
                "source-order",
                1,
                OrderType.ADAPTED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "target-order")),
                0);
        NotionalToteOrder fulfilment = order(
                "target-order",
                1,
                OrderType.ASSOCIATED,
                "104",
                List.of(line("shared-line", DspOrderLineType.ADAPTED, "product", "source-order")),
                1);

        DspFullDayInputPreflight preflight = new DspFullDayInputPreflight(
                key -> {
                    throw new AssertionError("injected error");
                });

        assertThrows(AssertionError.class,
                () -> preflight.project(
                        data(List.of(product("product")), List.of(source, fulfilment), List.of(),
                                DspDatasetLoadReport.empty()),
                        DspInputRejectionCatalog.empty()));
    }

    private static DspFullDayInputProjection project(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests) {
        return project(products, orders, manifests, DspDatasetLoadReport.empty());
    }

    private static DspFullDayInputProjection project(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests,
            DspDatasetLoadReport report) {
        return new DspFullDayInputPreflight().project(
                data(products, orders, manifests, report),
                DspInputRejectionCatalog.empty());
    }

    private static LoadedDspData data(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<InboundToteManifest> manifests,
            DspDatasetLoadReport report) {
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
                report);
    }

    private static NotionalToteOrder order(
            String orderId,
            int sheetNumber,
            OrderType orderType,
            String serviceCentreId,
            List<DspOrderItem> items,
            long sequenceNumber) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                serviceCentreId,
                sheetNumber,
                orderType,
                items,
                999,
                sequenceNumber);
    }

    private static InboundToteManifest manifest(NotionalToteOrder order, String toteId) {
        return new InboundToteManifest(
                new PhysicalToteId(toteId),
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
        return new DspOrderItem(
                lineReference,
                productId,
                1,
                "pharmacy",
                "patient",
                "prescription",
                lineType,
                referenceOrderId,
                1,
                0);
    }

    private static DspOrderItem withIdentityDifference(
            DspOrderItem line,
            String mismatch,
            boolean fulfilment) {
        String productId = "product";
        String pharmacyId = "pharmacy";
        String patientId = "patient";
        String prescriptionId = "prescription";
        if ("productId".equals(mismatch)) {
            productId = fulfilment ? "other-product" : "product";
        } else if ("pharmacyId".equals(mismatch)) {
            pharmacyId = fulfilment ? "other-pharmacy" : "pharmacy";
        } else if ("patientId".equals(mismatch)) {
            patientId = fulfilment ? "other-patient" : "patient";
        } else if ("prescriptionId".equals(mismatch)) {
            prescriptionId = fulfilment ? "other-prescription" : "prescription";
        }
        return new DspOrderItem(
                line.lineReference(),
                productId,
                line.quantity(),
                pharmacyId,
                patientId,
                prescriptionId,
                line.lineType(),
                line.referenceOrderId(),
                line.referenceSheetNumber(),
                line.numberOfPacksPicked());
    }

    private static ProductMasterRecord product(String productId) {
        return new ProductMasterRecord(
                productId,
                "Product " + productId,
                Optional.empty(),
                Optional.of(new PackDimensions(0.20f, 0.10f, 0.08f)));
    }
}
