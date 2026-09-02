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
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

class DspFullDayInputLoaderTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldLoadInSuppliedOrderAndRetainMixedWorkAndBagProvenance(@TempDir Path directory)
            throws IOException {
        Path productMaster = write(directory, "products.csv", """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                product-b,Product B,Y74,200,100,80
                """);
        Path adapted = write(directory, "01-adapted.json", message(
                "adapted-order", "001", "02", "adapted-tote", "104", "999",
                "adapted-line", "02", "product-a", "adapted-pharmacy", "adapted-patient",
                "adapted-prescription", "0001", "0001"));
        Path full = write(directory, "02-full.json", message(
                "full-order", "001", "05", "full-tote", "108", "998",
                "full-line", "05", "product-b", "full-pharmacy", "full-patient",
                "full-prescription", "0001", "0001"));
        Path associated = write(directory, "03-associated.json", message(
                "associated-order", "001", "04", "associated-tote", "104", "999",
                "associated-line", "05", "product-a", "associated-pharmacy", "associated-patient",
                "associated-prescription", "0001", "0001"));
        Path empty = write(directory, "04-empty.json", message(
                "empty-order", "001", "03", null, "104", "999",
                "empty-line", "05", "product-a", "empty-pharmacy", "empty-patient",
                "empty-prescription", "0001", "0000"));
        Path manual = write(directory, "05-manual.json", message(
                "manual-order", "001", "01", null, "104", "999",
                "manual-line", "01", "product-a", "manual-pharmacy", "manual-patient",
                "manual-prescription", "0001", "0001"));
        Path unresolved = write(directory, "06-unresolved.json", message(
                "unresolved-order", "001", "05", "unresolved-tote", "104", "999",
                "unresolved-line", "05", "missing-product", "unresolved-pharmacy", "unresolved-patient",
                "unresolved-prescription", "0001", "0001"));

        DspFullDayLoadedInput loaded = new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(adapted, full, associated, empty, manual, unresolved)),
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(3), 2, 4, 2));

        LoadedDspData data = loaded.loadedData();
        assertEquals(List.of("adapted-order", "full-order", "associated-order", "empty-order", "unresolved-order"),
                data.orders().stream().map(order -> order.orderId()).toList());
        assertEquals(List.of(OrderType.ADAPTED, OrderType.FULL_PACK, OrderType.ASSOCIATED, OrderType.EMPTY, OrderType.FULL_PACK),
                data.orders().stream().map(order -> order.orderType()).toList());
        assertEquals(List.of("adapted-tote", "full-tote", "associated-tote", "unresolved-tote"),
                data.inboundToteManifests().stream().map(manifest -> manifest.physicalToteId().value()).toList());
        assertEquals(1, data.report().ignoredManualMessageCount());
        assertEquals(1, data.report().ignoredManualLineCount());
        assertEquals(List.of("missing-product"),
                data.report().unresolvedProductLines().stream().map(issue -> issue.productId()).toList());

        BagPlanningResult plan = loaded.bagPlanningResult();
        assertEquals(3, plan.packTraces().size());
        assertEquals(3, plan.plannedBags().size());
        for (PlannedPackTrace trace : plan.packTraces()) {
            assertTrue(trace.physicalPackId().startsWith("pack-"));
            assertFalse(trace.sourceProvenance().sourceOrderSheetKey().orderId().isBlank());
            assertEquals(trace.sourceProvenance().prescriptionId(), trace.bagKey().prescriptionId());
        }
        assertEquals(List.of("adapted-tote", "full-tote", "associated-tote", "unresolved-tote"),
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
}
