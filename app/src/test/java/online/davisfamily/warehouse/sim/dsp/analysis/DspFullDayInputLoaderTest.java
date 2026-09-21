package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionReason;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

class DspFullDayInputLoaderTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldLoadInSuppliedOrderAndRetainMixedWorkAndBagProvenance(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                product-b,Product B,,200,100,80
                """);
        Path adapted = write(directory, "01-adapted.json", message(
                "adapted-order", "001", "02", "adapted-tote", "104", "999",
                "adapted-line", "02", "product-a", "adapted-pharmacy", "adapted-patient",
                "adapted-prescription", "0003", "0002")
                .replace("\"referenceOrderId\":\"adapted-order\"",
                        "\"referenceOrderId\":\"empty-order\""));
        Path full = write(directory, "02-full.json", message(
                "full-order", "001", "05", "full-tote", "108", "998",
                "full-line", "03", "product-b", "full-pharmacy", "full-patient",
                "full-prescription", "0003", "0000"));
        Path associated = write(directory, "03-associated.json", message(
                "associated-order", "001", "04", "associated-tote", "104", "999",
                "associated-line", "05", "product-a", "associated-pharmacy", "associated-patient",
                "associated-prescription", "0002", "0001"));
        Path empty = write(directory, "04-empty.json", message(
                "empty-order", "001", "03", null, "104", "999",
                "adapted-line", "02", "product-a", "adapted-pharmacy", "adapted-patient",
                "adapted-prescription", "0002", "0001"));
        Path manual = write(directory, "05-manual.json", message(
                "manual-order", "001", "01", null, "104", "999",
                "manual-line", "01", "product-a", "manual-pharmacy", "manual-patient",
                "manual-prescription", "0001", "0001"));
        Path unresolved = write(directory, "06-unresolved.json", message(
                "unresolved-order", "001", "05", "unresolved-tote", "104", "999",
                "unresolved-line", "03", "missing-product", "unresolved-pharmacy", "unresolved-patient",
                "unresolved-prescription", "0001", "0001"));
        Path partialKnown = write(directory, "07-partial-known.json", message(
                "partial-order", "001", "05", "partial-known-tote", "104", "999",
                "partial-known-line", "05", "product-a", "partial-pharmacy", "partial-patient",
                "partial-prescription", "0003", "0001"));
        Path partialUnresolved = write(directory, "08-partial-unresolved.json", message(
                "partial-order", "001", "05", "partial-unresolved-tote", "104", "999",
                "partial-unresolved-line", "03", "missing-partial-product", "partial-pharmacy", "partial-patient",
                "partial-prescription", "0001", "0000"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster,
                        List.of(adapted, full, associated, empty, manual, unresolved, partialKnown, partialUnresolved)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 2, 4, 2));

        LoadedDspData data = loaded.loadedData();
        assertEquals(List.of("adapted-order", "full-order", "associated-order", "empty-order", "partial-order"),
                data.orders().stream().map(order -> order.orderId()).toList());
        assertEquals(
                List.of("adapted-order", "full-order", "associated-order", "empty-order",
                        "unresolved-order", "partial-order"),
                loaded.reportableOrders().stream().map(order -> order.orderId()).toList());
        assertTrue(loaded.rejectionCatalog().rejectedLines().isEmpty());
        assertEquals(0, loaded.rejectionCatalog().rejectedMessageCount());
        assertEquals(List.of(OrderType.ADAPTED, OrderType.FULL_PACK, OrderType.ASSOCIATED, OrderType.EMPTY, OrderType.FULL_PACK),
                data.orders().stream().map(order -> order.orderType()).toList());
        assertEquals(List.of("adapted-tote", "full-tote", "associated-tote", "partial-known-tote"),
                data.inboundToteManifests().stream().map(manifest -> manifest.physicalToteId().value()).toList());
        assertEquals(DspOrderLineType.FULL_PACK, data.orders().get(1).items().getFirst().lineType());
        assertEquals(3, data.orders().get(1).items().getFirst().quantity());
        assertEquals(0, data.orders().get(1).items().getFirst().numberOfPacksPicked());
        assertEquals(List.of("partial-known-line"), data.orders().get(4).items().stream()
                .map(DspOrderItem::lineReference).toList());
        assertEquals(1, data.report().ignoredManualMessageCount());
        assertEquals(1, data.report().ignoredManualLineCount());
        assertEquals(List.of("missing-product", "missing-partial-product"),
                data.report().unresolvedProductLines().stream().map(issue -> issue.productId()).toList());
        assertEquals(List.of("104", "104"),
                data.report().unresolvedProductLines().stream().map(issue -> issue.serviceCentreId()).toList());

        BagPlanningResult plan = loaded.bagPlanningResult();
        assertEquals(3, plan.packTraces().size());
        assertEquals(4, plan.plannedPackSlots().size());
        assertEquals(1, plan.plannedPackSlots().stream()
                .filter(slot -> slot.slotKey().lineReference().equals("full-line"))
                .count());
        assertEquals(1, plan.plannedPackSlots().stream()
                .filter(slot -> slot.slotKey().lineReference().equals("adapted-line"))
                .count());
        assertTrue(plan.packTraces().stream()
                .anyMatch(trace -> trace.physicalPackId().equals("pack-full-tote-full-line-1")));
        assertEquals(4, plan.plannedBags().size());
        for (PlannedPackTrace trace : plan.packTraces()) {
            assertTrue(trace.physicalPackId().startsWith("pack-"));
            assertFalse(trace.sourceProvenance().sourceOrderSheetKey().orderId().isBlank());
            assertEquals(trace.sourceProvenance().prescriptionId(), trace.bagKey().prescriptionId());
        }
        assertEquals(List.of("full-tote", "associated-tote", "partial-known-tote"),
                plan.p2pToteLoadPlans().stream().map(loadPlan -> loadPlan.physicalToteId().value()).toList());
        assertEquals(loaded.loadReport(), loaded.loadedData().report());
    }

    @Test
    void shouldPreserveSeparatePhysicalManifestsForOneLogicalSheet(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path first = write(directory, "01.json", message(
                "same-order", "001", "05", "tote-one", "104", "999",
                "line-one", "05", "product-a", "pharmacy", "patient", "prescription", "0001", "0001"));
        Path second = write(directory, "02.json", message(
                "same-order", "001", "05", "tote-two", "104", "999",
                "line-two", "05", "product-a", "pharmacy", "patient", "prescription", "0001", "0001"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(first, second)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 4));

        assertEquals(1, loaded.loadedData().orders().size());
        assertEquals(List.of("tote-one", "tote-two"), loaded.loadedData().inboundToteManifests().stream()
                .map(manifest -> manifest.physicalToteId().value()).toList());
        assertEquals(List.of("tote-one", "tote-two"), loaded.bagPlanningResult().p2pToteLoadPlans().stream()
                .map(loadPlan -> loadPlan.physicalToteId().value()).toList());
        assertEquals(1, loaded.bagPlanningResult().plannedBags().size());
        assertEquals(2, loaded.bagPlanningResult().plannedBags().getFirst().physicalPackIds().size());
    }

    @Test
    void shouldNormalizeRepeatedCarrierBarcodeThroughOrderedInput(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path first = write(directory, "01-first.json", message(
                "first-order", "001", "05", "shared-carrier", "104", "999",
                "first-line", "05", "product-a", "pharmacy-first", "patient-first",
                "prescription-first", "0001", "0001"));
        Path second = write(directory, "02-second.json", message(
                "second-order", "001", "05", "shared-carrier", "108", "998",
                "second-line", "05", "product-a", "pharmacy-second", "patient-second",
                "prescription-second", "0001", "0001"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(first, second)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 4));

        assertEquals(List.of("shared-carrier", "dsp-reused-shared-carrier-2"),
                loaded.loadedData().inboundToteManifests().stream()
                        .map(manifest -> manifest.physicalToteId().value())
                        .toList());
        assertEquals(List.of("shared-carrier", "dsp-reused-shared-carrier-2"),
                loaded.bagPlanningResult().p2pToteLoadPlans().stream()
                        .map(loadPlan -> loadPlan.physicalToteId().value())
                        .toList());
        assertEquals(1, loaded.loadReport().inboundToteIdSubstitutions().size());
        assertTrue(loaded.loadReport().unresolvedProductLines().isEmpty());
        assertEquals(loaded.loadReport(), loaded.loadedData().report());
    }

    @Test
    void shouldRecoverMalformedMessageAndPlanSuccessfulLaterMessage(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path malformed = write(directory, "01-malformed.json", "{ not valid json");
        Path valid = write(directory, "02-valid.json", message(
                "valid-order", "001", "05", "valid-tote", "104", "999",
                "valid-line", "05", "product-a", "pharmacy", "patient", "prescription",
                "0001", "0000"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(malformed, valid)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2));

        assertEquals(List.of("valid-order"), loaded.reportableOrders().stream()
                .map(order -> order.orderId()).toList());
        assertEquals(List.of("valid-order"), loaded.data().orders().stream()
                .map(order -> order.orderId()).toList());
        assertEquals(1, loaded.rejectionCatalog().rejectedMessageCount());
        assertEquals(1, loaded.rejectionCatalog().count(
                DspInputRejectionReason.MALFORMED_12N_MESSAGE));
        assertEquals(malformed, loaded.rejectionCatalog().rejectedMessages().getFirst().path());
        assertEquals(0, loaded.rejectionCatalog().rejectedMessages().getFirst()
                .sourceMessageEncounterIndex());
        assertEquals(1, loaded.bagPlan().plannedPackSlots().size());
    }

    @Test
    void shouldQuarantineMissingSourceAliasAndPlanSiblingLine(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                product-b,Product B,,200,100,80
                """);
        Path associated = write(directory, "associated.json", twoLineAssociatedMessage());

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(associated)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2));

        assertEquals(List.of("associated-order"), loaded.reportableOrders().stream()
                .map(order -> order.orderId()).toList());
        assertEquals(List.of("sibling-line"), loaded.data().orders().getFirst().items().stream()
                .map(DspOrderItem::lineReference).toList());
        assertEquals(List.of("sibling-line"), loaded.data().inboundToteManifests().getFirst()
                .items().stream().map(DspOrderItem::lineReference).toList());
        assertEquals(1, loaded.rejectionCatalog().rejectedLineCount());
        assertEquals(
                DspInputRejectionReason.MISSING_ADAPTED_SOURCE,
                loaded.rejectionCatalog().rejectedLines().getFirst().reason());
        assertEquals(1, loaded.bagPlan().plannedPackSlots().size());
        assertTrue(loaded.bagPlan().plannedPackSlots().stream()
                .noneMatch(slot -> slot.slotKey().lineReference().equals("alias-line")));
    }

    @Test
    void shouldExcludeCompletelyRejectedOrderBeforeStrictPlanning(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path rejected = write(directory, "01-rejected.json", message(
                "rejected-order", "001", "04", "rejected-tote", "104", "999",
                "alias-line", "02", "product-a", "pharmacy", "patient", "prescription",
                "0001", "0000"));
        Path valid = write(directory, "02-valid.json", message(
                "valid-order", "001", "05", "valid-tote", "104", "999",
                "valid-line", "05", "product-a", "pharmacy", "patient", "valid-prescription",
                "0001", "0000"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(rejected, valid)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2));

        assertEquals(List.of("rejected-order", "valid-order"), loaded.reportableOrders().stream()
                .map(order -> order.orderId()).toList());
        assertEquals(List.of("valid-order"), loaded.data().orders().stream()
                .map(order -> order.orderId()).toList());
        assertEquals(1, loaded.rejectionCatalog().rejectedLineCount());
        assertTrue(loaded.bagPlan().plannedPackSlots().stream()
                .allMatch(slot -> !slot.fulfilmentOrderSheetKey().orderId().equals("rejected-order")));
        assertTrue(loaded.bagPlan().p2pToteLoadPlans().stream()
                .noneMatch(plan -> plan.physicalToteId().value().equals("rejected-tote")));
    }

    @Test
    void shouldRejectInvalidPathsBeforeAnyDatasetLoad(@TempDir Path directory) throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path order = write(directory, "order.json", message(
                "order", "001", "05", "tote", "104", "999",
                "line", "05", "product-a", "pharmacy", "patient", "prescription", "0001", "0001"));

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputPaths(productMaster, List.of(productMaster)));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputPaths(productMaster, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputPaths(productMaster, List.of(order, order)));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputLoader().load(
                        new DspFullDayInputPaths(productMaster, List.of(directory.resolve("missing.json"))),
                        DspUncalibratedFullDayProfile.productionBaseline(
                                OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputLoader().load(
                        new DspFullDayInputPaths(directory, List.of(order)),
                        DspUncalibratedFullDayProfile.productionBaseline(
                                OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2)));
    }

    @Test
    void shouldRejectTimetableMismatchAndEmptyRetainedWork(@TempDir Path directory) throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path order = write(directory, "order.json", message(
                "order", "001", "05", "tote", "104", "998",
                "line", "05", "product-a", "pharmacy", "patient", "prescription", "0001", "0001"));
        DspUncalibratedFullDayProfile profile = DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(3), 1, 4, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputLoader().load(
                        new DspFullDayInputPaths(productMaster, List.of(order)), profile));

        Path manual = write(directory, "manual.json", message(
                "manual", "001", "01", null, "104", "999",
                "manual-line", "01", "product-a", "pharmacy", "patient", "manual-prescription", "0001", "0001"));
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayInputLoader().load(
                        new DspFullDayInputPaths(productMaster, List.of(manual)), profile));
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private static String message(
            String orderId,
            String sheetNumber,
            String toteType,
            String physicalToteId,
            String serviceCentreId,
            String priority,
            String lineReference,
            String lineType,
            String productId,
            String pharmacyId,
            String patientId,
            String prescriptionId,
            String numberOfPacks,
            String numberOfPacksPicked) {
        String transportField = physicalToteId == null
                ? ""
                : "\"transportContainer\": {\"payload\":\"" + physicalToteId + "\"},";
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"%s"},
                  "toteIdentifier": {"payload":"%s"},
                  %s
                  "orderPriority": {"payload":"%s"},
                  "serviceCentre": {"payload":"%s"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"%s",
                        "orderLineType":"%s",
                        "pharmacyId":"%s",
                        "patientId":"%s",
                        "prescriptionId":"%s",
                        "productId":"%s",
                        "numberOfPacks":"%s",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"%s",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(
                orderId, sheetNumber, toteType, transportField, priority, serviceCentreId,
                lineReference, lineType, pharmacyId, patientId, prescriptionId, productId,
                numberOfPacks, numberOfPacksPicked, orderId);
    }

    private static String twoLineAssociatedMessage() {
        return """
                {
                  "header": {"orderId":"associated-order","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"04"},
                  "transportContainer": {"payload":"associated-tote"},
                  "orderPriority": {"payload":"999"},
                  "serviceCentre": {"payload":"104"},
                  "orderDetail": {
                    "numberOfOrderLines": 2,
                    "orderLines": [
                      {
                        "orderLineNumber":"alias-line",
                        "orderLineType":"02",
                        "pharmacyId":"pharmacy",
                        "patientId":"patient",
                        "prescriptionId":"alias-prescription",
                        "productId":"product-a",
                        "numberOfPacks":"0001",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0000",
                        "referenceOrderId":"associated-order"
                      },
                      {
                        "orderLineNumber":"sibling-line",
                        "orderLineType":"05",
                        "pharmacyId":"pharmacy",
                        "patientId":"patient",
                        "prescriptionId":"sibling-prescription",
                        "productId":"product-b",
                        "numberOfPacks":"0001",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"0000",
                        "referenceOrderId":"associated-order"
                      }
                    ]
                  }
                }
                """;
    }
}
