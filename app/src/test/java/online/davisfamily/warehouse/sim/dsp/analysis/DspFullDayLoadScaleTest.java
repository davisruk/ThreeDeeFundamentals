package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Load and planning scale proof without composing or executing the physical runtime. */
class DspFullDayLoadScaleTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);
    private static final int SYNTHETIC_PACK_LINE_COUNT = 110_000;

    @Test
    void shouldPlanAbout110000PackLinesWithoutRuntimeObjects(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile =
                DspUncalibratedFullDayProfile.productionBaseline(
                        OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
        DspFullDayLoadedInput first = loadScaleInput(directory.resolve("first"), profile);
        DspFullDayLoadedInput second = loadScaleInput(directory.resolve("second"), profile);

        assertEquals(1, first.data().orders().size());
        assertEquals(SYNTHETIC_PACK_LINE_COUNT,
                first.data().orders().getFirst().items().size());
        assertEquals(1, first.data().inboundToteManifests().size());
        assertEquals(SYNTHETIC_PACK_LINE_COUNT, first.bagPlan().packTraces().size());
        assertEquals(SYNTHETIC_PACK_LINE_COUNT / profile.maximumPacksPerBag(),
                first.bagPlan().plannedBags().size());
        assertEquals(1, first.bagPlan().p2pToteLoadPlans().size());
        assertEquals(SYNTHETIC_PACK_LINE_COUNT,
                first.bagPlan().p2pToteLoadPlans().getFirst().getPackPlans().size());
        assertEquals(0, first.data().preparedLines().size());
        assertEquals(0, first.report().ignoredManualMessageCount());
        assertEquals(0, first.report().ignoredManualLineCount());

        assertEquals("scale-line-000001",
                first.data().orders().getFirst().items().getFirst().lineReference());
        assertEquals("scale-line-110000",
                first.data().orders().getFirst().items().getLast().lineReference());
        assertEquals("pack-scale-tote-scale-line-000001-1",
                first.bagPlan().packTraces().getFirst().physicalPackId());
        assertEquals("pack-scale-tote-scale-line-110000-1",
                first.bagPlan().packTraces().getLast().physicalPackId());
        assertEquals("scale-rx/bag-1",
                first.bagPlan().plannedBags().getFirst().bagKey().correlationId());
        assertEquals("scale-rx/bag-27500",
                first.bagPlan().plannedBags().getLast().bagKey().correlationId());
        assertEquals(4, first.bagPlan().plannedBags().getFirst().physicalPackIds().size());
        assertEquals(4, first.bagPlan().plannedBags().getLast().physicalPackIds().size());

        assertEquals(fingerprint(first), fingerprint(second));
        assertEquals(first.bagPlan().plannedBags().stream()
                .map(bag -> bag.bagKey().correlationId()).toList(),
                second.bagPlan().plannedBags().stream()
                        .map(bag -> bag.bagKey().correlationId()).toList());
        assertEquals(first.bagPlan().p2pToteLoadPlans().getFirst().orderedCorrelationIds(),
                second.bagPlan().p2pToteLoadPlans().getFirst().orderedCorrelationIds());
        assertEquals(first.bagPlan().packTraces().getFirst(),
                second.bagPlan().packTraces().getFirst());
        assertEquals(first.bagPlan().packTraces().getLast(),
                second.bagPlan().packTraces().getLast());

        // This test intentionally stops at the public loader/bag-plan boundary: no SimulationWorld,
        // full-day runtime, renderable tote, or physical Pack is created for the 110,000-line case.
        assertTrue(first.bagPlan().plannedBags().stream()
                .allMatch(bag -> bag.physicalPackIds().size() <= profile.maximumPacksPerBag()));
        assertFalse(first.bagPlan().packTraces().isEmpty());
    }

    private static DspFullDayLoadedInput loadScaleInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Files.createDirectories(directory);
        Path products = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                scale-product,Scale Product,,200,100,80
                """);
        Path message = Files.writeString(
                directory.resolve("scale-order.json"), scaleMessage());
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(products, List.of(message)), profile);
    }

    private static String scaleMessage() {
        StringBuilder json = new StringBuilder(SYNTHETIC_PACK_LINE_COUNT * 220);
        json.append("{\n")
                .append("  \"header\": {\"orderId\":\"scale-order\",\"sheetNumber\":\"001\"},\n")
                .append("  \"toteIdentifier\": {\"payload\":\"05\"},\n")
                .append("  \"transportContainer\": {\"payload\":\"scale-tote\"},\n")
                .append("  \"orderPriority\": {\"payload\":\"999\"},\n")
                .append("  \"serviceCentre\": {\"payload\":\"104\"},\n")
                .append("  \"orderDetail\": {\n")
                .append("    \"numberOfOrderLines\": ").append(SYNTHETIC_PACK_LINE_COUNT).append(",\n")
                .append("    \"orderLines\": [\n");
        for (int index = 1; index <= SYNTHETIC_PACK_LINE_COUNT; index++) {
            json.append("      {\"orderLineNumber\":\"")
                    .append(scaleLineReference(index))
                    .append("\",\"orderLineType\":\"05\"")
                    .append(",\"pharmacyId\":\"scale-pharmacy\"")
                    .append(",\"patientId\":\"scale-patient\"")
                    .append(",\"prescriptionId\":\"scale-rx\"")
                    .append(",\"productId\":\"scale-product\"")
                    .append(",\"numberOfPacks\":\"0001\"")
                    .append(",\"referenceSheetNumber\":\"001\"")
                    .append(",\"numberOfPacksPicked\":\"0001\"")
                    .append(",\"referenceOrderId\":\"scale-order\"}")
                    .append(index == SYNTHETIC_PACK_LINE_COUNT ? "\n" : ",\n");
        }
        return json.append("    ]\n  }\n}\n").toString();
    }

    private static String scaleLineReference(int index) {
        String value = Integer.toString(index);
        return "scale-line-" + "000000".substring(value.length()) + value;
    }

    private static String fingerprint(DspFullDayLoadedInput input) {
        MessageDigest digest = sha256();
        BagPlanningResult bagPlan = input.bagPlan();
        for (PlannedPackTrace trace : bagPlan.packTraces()) {
            update(digest, trace.physicalPackId());
            update(digest, trace.inputPhysicalToteId().value());
            update(digest, trace.fulfilmentOrderSheetKey().orderId());
            update(digest, Integer.toString(trace.fulfilmentOrderSheetKey().sheetNumber()));
            update(digest, trace.sourceProvenance().lineReference());
            update(digest, trace.sourceProvenance().productId());
            update(digest, trace.sourceProvenance().pharmacyId());
            update(digest, trace.sourceProvenance().patientId());
            update(digest, trace.sourceProvenance().prescriptionId());
            update(digest, trace.bagKey().correlationId());
        }
        for (PlannedBag bag : bagPlan.plannedBags()) {
            update(digest, bag.bagKey().correlationId());
            update(digest, String.join(",", bag.physicalPackIds()));
            update(digest, bag.owningOrderSheetKeys().toString());
        }
        for (ToteLoadPlan toteLoadPlan : bagPlan.p2pToteLoadPlans()) {
            update(digest, toteLoadPlan.physicalToteId().value());
            for (PackPlan packPlan : toteLoadPlan.getPackPlans()) {
                update(digest, packPlan.packId());
                update(digest, packPlan.correlationId());
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the JDK", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }
}
