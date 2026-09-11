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
        assertEquals(List.of(fixture.secondOrder(), fixture.firstOrder()), command.orderPaths());
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
    void shouldExpandOrdersDirectoryInNaturalFilenameOrderAndIgnoreOtherEntries(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path ordersDirectory = Files.createDirectory(directory.resolve("orders"));

        Files.writeString(ordersDirectory.resolve("a-lower.json"), "a");
        Files.writeString(ordersDirectory.resolve("2.json"), "2");
        Files.writeString(ordersDirectory.resolve("A.json"), "A");
        Files.writeString(ordersDirectory.resolve("10.json"), "10");
        Files.writeString(ordersDirectory.resolve("ignored.txt"), "ignored");
        Files.writeString(ordersDirectory.resolve("ignored-uppercase.JSON"), "ignored");
        Path ignoredDirectory = Files.createDirectory(ordersDirectory.resolve("ignored.json"));
        Files.writeString(ignoredDirectory.resolve("nested.json"), "ignored");

        DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommandParser().parse(
                directoryArguments(fixture, ordersDirectory));

        assertEquals(List.of(
                ordersDirectory.resolve("10.json"),
                ordersDirectory.resolve("2.json"),
                ordersDirectory.resolve("A.json"),
                ordersDirectory.resolve("a-lower.json")), command.orderPaths());
    }

    @Test
    void shouldParseEquivalentConfigOnlyInvocationAndIgnoreConfigPosition(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path ordersDirectory = Files.createDirectory(directory.resolve("orders"));
        Files.copy(fixture.firstOrder(), ordersDirectory.resolve("first.json"));
        Files.copy(fixture.secondOrder(), ordersDirectory.resolve("second.json"));
        Path configPath = configDirectory.resolve("full-day.json");
        Files.writeString(configPath, """
                {
                  "productMaster": "../products.csv",
                  "ordersDirectory": "../orders",
                  "output": "run/report.json",
                  "inspectionOutput": "run/inspection.txt",
                  "operatingDate": "2026-09-02",
                  "osrLowWaterMark": 10,
                  "inboundIntervalSeconds": 1.0,
                  "av02Capacity": 2,
                  "outboundBagCapacity": 4,
                  "maximumPacksPerBag": 4,
                  "fixedStepMillis": 125,
                  "stepsPerBatch": 7,
                  "metricSampleSeconds": 15,
                  "overwrite": false
                }
                """);

        DspFullDayAnalysisCommand configCommand = new DspFullDayAnalysisCommandParser().parse(
                new String[] {"--config=" + configPath});
        Path expectedOutput = configDirectory.resolve("run/report.json").normalize();
        Path expectedInspection = configDirectory.resolve("run/inspection.txt").normalize();
        DspFullDayAnalysisCommand cliCommand = new DspFullDayAnalysisCommandParser().parse(
                new String[] {
                    "--product-master=" + fixture.productMaster(),
                    "--orders-directory=" + ordersDirectory,
                    "--output=" + expectedOutput,
                    "--inspection-output=" + expectedInspection,
                    "--operating-date=2026-09-02",
                    "--osr-low-water-mark=10",
                    "--inbound-interval-seconds=1.0",
                    "--av02-capacity=2",
                    "--outbound-bag-capacity=4",
                    "--maximum-packs-per-bag=4",
                    "--fixed-step-millis=125",
                    "--steps-per-batch=7",
                    "--metric-sample-seconds=15"
                });

        assertEquals(cliCommand, configCommand);
        assertEquals(configCommand,
                new DspFullDayAnalysisCommandParser().parse(
                        new String[] {
                            "--fixed-step-millis=125",
                            "--config=" + configPath,
                            "--steps-per-batch=7"
                        }));
    }

    @Test
    void shouldAllowCommandLineOverridesAndReplaceConfiguredOrderMode(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path ordersDirectory = Files.createDirectory(directory.resolve("orders"));
        Files.copy(fixture.firstOrder(), ordersDirectory.resolve("first.json"));
        Files.copy(fixture.secondOrder(), ordersDirectory.resolve("second.json"));
        Path configPath = configDirectory.resolve("full-day.json");
        Files.writeString(configPath, configJson(
                "../products.csv",
                "../orders",
                "configured-report.json",
                null,
                false));
        Path commandLineOutput = directory.resolve("command-line-report.json");

        DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommandParser().parse(
                new String[] {
                    "--config=" + configPath,
                    "--orders=" + fixture.secondOrder(),
                    "--orders=" + fixture.firstOrder(),
                    "--output=" + commandLineOutput,
                    "--overwrite"
                });

        assertEquals(List.of(fixture.secondOrder(), fixture.firstOrder()), command.orderPaths());
        assertEquals(commandLineOutput, command.outputPath());
        assertTrue(command.overwrite());
        assertEquals(configDirectory.resolve("../products.csv").normalize(),
                command.productMasterPath());
    }

    @Test
    void shouldPreserveConfiguredExplicitOrderArrayAndAllowDirectoryReplacement(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path ordersDirectory = Files.createDirectory(directory.resolve("orders"));
        Files.copy(fixture.firstOrder(), ordersDirectory.resolve("first.json"));
        Files.copy(fixture.secondOrder(), ordersDirectory.resolve("second.json"));
        Path configPath = configDirectory.resolve("full-day.json");
        Files.writeString(configPath, configJsonWithOrders(
                "../products.csv",
                List.of("../order-108.json", "../order-104.json"),
                "report.json"));

        DspFullDayAnalysisCommand configured = new DspFullDayAnalysisCommandParser().parse(
                new String[] {"--config=" + configPath});
        assertEquals(List.of(
                directory.resolve("order-108.json").normalize(),
                directory.resolve("order-104.json").normalize()),
                configured.orderPaths());

        DspFullDayAnalysisCommand replaced = new DspFullDayAnalysisCommandParser().parse(
                new String[] {
                    "--config=" + configPath,
                    "--orders-directory=" + ordersDirectory
                });
        assertEquals(List.of(
                ordersDirectory.resolve("first.json"),
                ordersDirectory.resolve("second.json")),
                replaced.orderPaths());
    }

    @Test
    void shouldRejectStrictConfigurationShapesAndInvalidEffectiveValues(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path configPath = directory.resolve("full-day.json");
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();
        String validConfig = configJson(
                jsonPath(fixture.productMaster()),
                jsonPath(directory),
                "report.json",
                null,
                false);

        assertConfigRejected(parser, configPath,
                validConfig.replace("\"productMaster\":", "\"unknown\": true,\n  \"productMaster\":"));
        assertConfigRejected(parser, configPath,
                validConfig.replace("\"output\": \"report.json\"", "\"output\": null"));
        assertConfigRejected(parser, configPath, "{\"orders\":[]}");
        assertConfigRejected(parser, configPath, "{\"orders\":[1]}");
        assertConfigRejected(parser, configPath, "{\"orders\":[],\"ordersDirectory\":\"orders\"}");
        assertConfigRejected(parser, configPath, "{\"osrLowWaterMark\":\"10\"}");
        assertConfigRejected(parser, configPath,
                validConfig.replace("\"output\": \"report.json\",",
                        "\"output\": \"report.json\",\n  \"output\": \"other.json\","));
        assertConfigRejected(parser, configPath,
                validConfig.replace("\"inboundIntervalSeconds\": 1.0", "\"inboundIntervalSeconds\": 0.0"));
        assertConfigRejected(parser, configPath, "{\"output\":\"report.json\"}");

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + directory.resolve("missing.json")}));
        Path configDirectory = Files.createDirectory(directory.resolve("config-directory"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + configDirectory}));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config"}));
    }

    @Test
    void shouldRejectInvalidConfigurationBeforeLoadingAndLeaveNoPartialOutput(
            @TempDir Path directory) throws Exception {
        Path configPath = directory.resolve("invalid.json");
        Files.writeString(configPath, "{\"unknown\":true}");
        Path output = directory.resolve("report.json");
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = DspFullDayAnalysisMain.run(
                new String[] {"--config=" + configPath},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertNotEquals(0, exitCode);
        assertFalse(Files.exists(output));
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("--config"));
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
    void shouldRejectInvalidOrdersDirectoryModes(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();
        Path validOrdersDirectory = Files.createDirectory(directory.resolve("valid-orders"));
        Files.writeString(validOrdersDirectory.resolve("valid.json"), "valid");

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture,
                        "--orders-directory=" + validOrdersDirectory)));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(directoryArguments(fixture, validOrdersDirectory,
                        "--orders-directory=" + validOrdersDirectory)));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(directoryArguments(fixture,
                        directory.resolve("missing-orders"))));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(directoryArguments(fixture, fixture.firstOrder())));

        Path emptyOrdersDirectory = Files.createDirectory(directory.resolve("empty-orders"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(directoryArguments(fixture, emptyOrdersDirectory)));
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

    @Test
    void shouldRejectInvalidDirectoryBeforeLoadingAndLeaveNoPartialOutput(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path emptyOrdersDirectory = Files.createDirectory(directory.resolve("empty-orders"));
        Path inspection = directory.resolve("invalid-directory-inspection.txt");
        String[] arguments = directoryArguments(fixture, emptyOrdersDirectory,
                "--inspection-output=" + inspection);

        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        int exitCode = DspFullDayAnalysisMain.run(
                arguments,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertNotEquals(0, exitCode);
        assertFalse(Files.exists(fixture.output()));
        assertFalse(Files.exists(inspection));
    }

    private static String[] arguments(Fixture fixture, String... extra) {
        String[] base = {
            "--product-master=" + fixture.productMaster(),
            "--orders=" + fixture.secondOrder(),
            "--orders=" + fixture.firstOrder(),
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

    private static String[] directoryArguments(
            Fixture fixture, Path ordersDirectory, String... extra) {
        String[] base = {
            "--product-master=" + fixture.productMaster(),
            "--orders-directory=" + ordersDirectory,
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

    private static String configJson(
            String productMaster,
            String ordersDirectory,
            String output,
            String inspectionOutput,
            boolean overwrite) {
        String inspection = inspectionOutput == null
                ? ""
                : "\n  \"inspectionOutput\": \"" + inspectionOutput + "\",\n";
        return """
                {
                  "productMaster": "%s",
                  "ordersDirectory": "%s",
                  "output": "%s",%s
                  "operatingDate": "2026-09-02",
                  "osrLowWaterMark": 10,
                  "inboundIntervalSeconds": 1.0,
                  "av02Capacity": 2,
                  "outboundBagCapacity": 4,
                  "maximumPacksPerBag": 4,
                  "overwrite": %s
                }
                """.formatted(productMaster, ordersDirectory, output, inspection, overwrite);
    }

    private static String configJsonWithOrders(
            String productMaster,
            List<String> orders,
            String output) {
        String orderValues = orders.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((first, second) -> first + ", " + second)
                .orElseThrow();
        return """
                {
                  "productMaster": "%s",
                  "orders": [%s],
                  "output": "%s",
                  "operatingDate": "2026-09-02",
                  "osrLowWaterMark": 10,
                  "inboundIntervalSeconds": 1.0,
                  "av02Capacity": 2,
                  "outboundBagCapacity": 4,
                  "maximumPacksPerBag": 4
                }
                """.formatted(productMaster, orderValues, output);
    }

    private static void assertConfigRejected(
            DspFullDayAnalysisCommandParser parser,
            Path configPath,
            String json) throws Exception {
        Files.writeString(configPath, json);
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + configPath}),
                () -> "configuration was accepted: " + json);
    }

    private static String jsonPath(Path path) {
        return path.toString().replace("\\", "\\\\");
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
