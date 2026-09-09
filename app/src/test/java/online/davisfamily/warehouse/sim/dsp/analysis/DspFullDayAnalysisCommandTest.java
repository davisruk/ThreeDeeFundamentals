package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DspFullDayAnalysisCommandTest {

    @Test
    void shouldParseRequiredRepeatedAndOptionalArguments(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommandParser().parse(
                arguments(fixture, "--fixed-step-millis=125", "--steps-per-batch=7",
                        "--metric-sample-seconds=15", "--inspection-output="
                                + fixture.inspection().toString(), "--overwrite"));

        assertEquals(fixture.productMaster(), command.productMasterPath());
        assertEquals(List.of(fixture.firstOrder(), fixture.secondOrder()), command.orderPaths());
        assertEquals(fixture.output(), command.outputPath());
        assertEquals(fixture.inspection(), command.inspectionOutputPath().orElseThrow());
        assertEquals(LocalDate.of(2026, 9, 2), command.operatingDate());
        assertEquals(10, command.osrLowWaterMark());
        assertEquals(Duration.ofSeconds(1), command.inboundInterval());
        assertEquals(2, command.av02Capacity());
        assertEquals(4, command.outboundBagCapacity());
        assertEquals(4, command.maximumPacksPerBag());
        assertEquals(Duration.ofMillis(125), command.fixedStep());
        assertEquals(7, command.stepsPerBatch());
        assertEquals(Duration.ofSeconds(15), command.metricSampleInterval());
        assertTrue(command.overwrite());
    }

    @Test
    void shouldRejectUnknownDuplicateMalformedAndInvalidArguments(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--unknown=value")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--output=" + fixture.output())));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--overwrite", "--overwrite")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--steps-per-batch")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--inbound-interval-seconds=0")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--operating-date=not-a-date")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "plain-argument")));
    }

    @Test
    void shouldRejectMissingNonexistentAndDirectoryValuedFiles(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();

        String[] missing = arguments(fixture);
        missing[0] = "--product-master=" + directory.resolve("missing.csv");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(missing));

        String[] directoryOutput = arguments(fixture);
        directoryOutput[3] = "--output=" + directory;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(directoryOutput));

        String[] directoryInput = arguments(fixture);
        directoryInput[1] = "--orders=" + directory;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(directoryInput));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--product-master=" + fixture.productMaster()}));
    }

    @Test
    void shouldRunSuccessfullyWriteJsonAndInspectionAndRefuseOverwrite(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        String[] arguments = arguments(
                fixture,
                "--fixed-step-millis=1000",
                "--steps-per-batch=20",
                "--metric-sample-seconds=60",
                "--inspection-output=" + fixture.inspection());
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int success = DspFullDayAnalysisMain.run(
                arguments,
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertEquals(0, success);
        assertTrue(Files.isRegularFile(fixture.output()));
        assertTrue(Files.isRegularFile(fixture.inspection()));
        assertTrue(Files.readString(fixture.output()).contains("\"schemaVersion\":1"));
        assertTrue(Files.readString(fixture.inspection()).contains("Run: state="));
        assertTrue(
                outputBytes.toString(StandardCharsets.UTF_8).contains("[dsp-full-day:final]"),
                () -> "console output was: " + outputBytes.toString(StandardCharsets.UTF_8));
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).isEmpty());

        byte[] original = Files.readAllBytes(fixture.output());
        int refused = DspFullDayAnalysisMain.run(
                arguments,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertNotEquals(0, refused);
        assertArrayEquals(original, Files.readAllBytes(fixture.output()));
    }

    @Test
    void shouldFailBeforeLoadingAndLeaveNoPartialOutput(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path missingProduct = directory.resolve("does-not-exist.csv");
        Path output = directory.resolve("not-created.json");
        String[] arguments = arguments(fixture);
        arguments[0] = "--product-master=" + missingProduct;
        arguments[2] = "--output=" + output;

        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        int exitCode = DspFullDayAnalysisMain.run(
                arguments,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertNotEquals(0, exitCode);
        assertFalse(Files.exists(output));
    }

    private static String[] arguments(Fixture fixture, String... extra) {
        String[] base = {
            "--product-master=" + fixture.productMaster(),
            "--orders=" + fixture.firstOrder(),
            "--orders=" + fixture.secondOrder(),
            "--output=" + fixture.output(),
            "--operating-date=2026-09-02",
            "--osr-low-water-mark=10",
            "--inbound-interval-seconds=1.0",
            "--av02-capacity=2",
            "--outbound-bag-capacity=4",
            "--maximum-packs-per-bag=4"
        };
        String[] result = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }

    private static Fixture fixture(Path directory) throws Exception {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path firstOrder = Files.writeString(
                directory.resolve("order-104.json"), message("order-104", "tote-104", "104", "999"));
        Path secondOrder = Files.writeString(
                directory.resolve("order-108.json"), message("order-108", "tote-108", "108", "998"));
        return new Fixture(
                productMaster,
                firstOrder,
                secondOrder,
                directory.resolve("report.json"),
                directory.resolve("inspection.txt"));
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"001"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"%s"},
                  "orderPriority": {"payload":"%s"},
                  "serviceCentre": {"payload":"%s"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-1",
                        "orderLineType":"05",
                        "pharmacyId":"pharmacy-1",
                        "patientId":"patient-1",
                        "prescriptionId":"%s-prescription",
                        "productId":"product-a",
                        "numberOfPacks":"1",
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(orderId, physicalToteId, priority, serviceCentreId, orderId, orderId);
    }

    private record Fixture(
            Path productMaster,
            Path firstOrder,
            Path secondOrder,
            Path output,
            Path inspection) {
    }
}
