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

import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;

class DspFullDayAnalysisCommandTest {

    @Test
    void shouldSelectEitherPolicyFromCliOrJsonWithCliPrecedence(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        var parser = new DspFullDayAnalysisCommandParser();
        var baseline = parser.parse(arguments(fixture));
        assertEquals(DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES, baseline.schedulerPolicy());
        var stationAwareLegacy = new DspFullDayAnalysisCommand(
                baseline.productMasterPath(),
                baseline.orderPaths(),
                baseline.outputPath(),
                baseline.inspectionOutputPath(),
                baseline.operatingDate(),
                baseline.osrLowWaterMark(),
                baseline.inboundInterval(),
                baseline.av02Capacity(),
                baseline.outboundBagCapacity(),
                baseline.maximumPacksPerBag(),
                baseline.fixedStep(),
                baseline.stepsPerBatch(),
                baseline.metricSampleInterval(),
                baseline.overwrite(),
                baseline.progressLogPath(),
                baseline.progressInterval(),
                baseline.serviceCentreSchedulePath(),
                baseline.stationProcessingOverrides());
        assertEquals(baseline, stationAwareLegacy);
        assertThrows(IllegalArgumentException.class, () -> new DspFullDayAnalysisCommand(
                baseline.productMasterPath(),
                baseline.orderPaths(),
                baseline.outputPath(),
                baseline.inspectionOutputPath(),
                baseline.operatingDate(),
                baseline.osrLowWaterMark(),
                baseline.inboundInterval(),
                baseline.av02Capacity(),
                baseline.outboundBagCapacity(),
                baseline.maximumPacksPerBag(),
                baseline.fixedStep(),
                baseline.stepsPerBatch(),
                baseline.metricSampleInterval(),
                baseline.overwrite(),
                baseline.progressLogPath(),
                baseline.progressInterval(),
                baseline.serviceCentreSchedulePath(),
                baseline.stationProcessingOverrides(),
                null));
        Path config = directory.resolve("policy.json");
        for (DspSchedulerPolicy selected : DspSchedulerPolicy.values()) {
            assertEquals(selected, parser.parse(arguments(fixture,
                    "--scheduler-policy=" + selected.name())).schedulerPolicy());
            assertEquals(selected, parser.parse(arguments(fixture,
                    "--scheduler-policy", selected.name())).schedulerPolicy());
            Files.writeString(config, stationConfig(fixture,
                    "\"schedulerPolicy\": \"" + selected.name() + "\""));
            assertEquals(selected, parser.parse(new String[] {"--config=" + config}).schedulerPolicy());
            for (DspSchedulerPolicy override : DspSchedulerPolicy.values()) {
                assertEquals(override, parser.parse(new String[] {
                        "--config=" + config, "--scheduler-policy=" + override.name()}).schedulerPolicy());
                assertEquals(override, parser.parse(new String[] {
                        "--scheduler-policy", override.name(), "--config=" + config}).schedulerPolicy());
            }
        }
    }

    @Test
    void shouldRejectEveryInvalidPolicyValueEvenWhenCliWouldOverrideJson(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        var parser = new DspFullDayAnalysisCommandParser();
        Path config = directory.resolve("policy.json");
        for (String invalid : List.of("null", "\"\"", "\" \"", "7", "true", "[]", "{}",
                "\"UNKNOWN\"", "\"whole_service_centre_drained_handover\"",
                "\" WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER\"",
                "\"DEADLINE_AWARE_ELASTIC_STICKY_LEASES \"")) {
            Files.writeString(config, stationConfig(fixture, "\"schedulerPolicy\": " + invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(new String[] {"--config=" + config}), invalid);
            for (DspSchedulerPolicy override : DspSchedulerPolicy.values()) {
                assertThrows(IllegalArgumentException.class,
                        () -> parser.parse(new String[] {"--config=" + config,
                                "--scheduler-policy=" + override.name()}), invalid);
            }
        }
        Files.writeString(config, stationConfig(fixture,
                "\"schedulerPolicy\": \"DEADLINE_AWARE_ELASTIC_STICKY_LEASES\","
                        + "\"schedulerPolicy\": \"WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER\""));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + config}));
        for (String invalid : List.of("", " ", "UNKNOWN", "whole_service_centre_drained_handover",
                " WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER", "DEADLINE_AWARE_ELASTIC_STICKY_LEASES ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(arguments(fixture, "--scheduler-policy=" + invalid)), invalid);
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(arguments(fixture, "--scheduler-policy", invalid)), invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--scheduler-policy")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--scheduler-policy", (String) null)));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--scheduler-policy", "--overwrite")));
        for (String[] duplicate : List.of(
                new String[] {"--scheduler-policy", "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER",
                        "--scheduler-policy=DEADLINE_AWARE_ELASTIC_STICKY_LEASES"},
                new String[] {"--scheduler-policy=DEADLINE_AWARE_ELASTIC_STICKY_LEASES",
                        "--scheduler-policy", "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER"},
                new String[] {"--scheduler-policy=DEADLINE_AWARE_ELASTIC_STICKY_LEASES",
                        "--scheduler-policy=WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER"},
                new String[] {"--scheduler-policy", "DEADLINE_AWARE_ELASTIC_STICKY_LEASES",
                        "--scheduler-policy", "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER"})) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(arguments(fixture, duplicate)));
        }
    }

    @Test
    void shouldRetainStationOverridesAndTimetableWithUsableWholeCentrePolicy(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path schedule = Files.writeString(directory.resolve("schedule.json"), """
                {"serviceCentres":[
                {"serviceCentreId":"104","displayName":"Letchworth","priority":999,
                "trunkerDepartureTime":{"dayOffset":0,"localTime":{"hour":18,"minute":30}}},
                {"serviceCentreId":"108","displayName":"Swansea","priority":998,
                "trunkerDepartureTime":{"dayOffset":0,"localTime":{"hour":19,"minute":0}}}]}
                """);
        Path config = directory.resolve("policy.json");
        Files.writeString(config, withSchedule(stationConfig(fixture, """
                "schedulerPolicy": "WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER",
                "thirdParty": {"processingDurationSeconds": 20},
                "adapting": {"storeDurationSeconds":60,"collectDurationSeconds":10,
                "processingPositionsPerBench":3,"waitingCapacityPerBench":3,
                "benchIds":["bench-1","bench-2","bench-3","bench-4","bench-5","bench-6"]}
                """), jsonPath(schedule)));
        var command = new DspFullDayAnalysisCommandParser().parse(new String[] {
                "--config=" + config});
        var profile = DspFullDayAnalysisMain.profile(command);
        assertEquals(DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER, profile.schedulerPolicy());
        assertEquals(6, profile.adaptingBenchDefinitions().size());
        assertTrue(profile.adaptingBenchDefinitions().stream().allMatch(bench ->
                bench.storeDurationSeconds() == 60d && bench.collectDurationSeconds() == 10d
                        && bench.processingPositions() == 3));
        assertEquals(3, profile.queueCapacities().adaptingQueueCapacityPerBench());
        assertEquals(20d, profile.thirdPartyAreaConfig().processingDurationSeconds());
        assertEquals(java.time.LocalTime.of(18, 30), profile.timetable().require("104")
                .trunkerDepartureTime().localTime());
        var input = new DspFullDayInputLoader().load(command.inputPaths(), profile);
        var factory = new DspFullDayAnalysisRuntimeFactory();
        try (var first = factory.create(input, profile);
                var second = factory.create(profile, input);
                var third = factory.create(new online.davisfamily.threedee.sim.framework.SimulationWorld(), input, profile)) {
            for (var runtime : List.of(first, second, third)) {
                assertEquals(profile.p2pLineAllocationPolicyId(), runtime.elasticRuntime().allocationSnapshot().profileId());
                assertEquals("104", runtime.elasticRuntime().allocationSnapshot().wholeServiceCentrePolicy()
                        .orElseThrow().eligibleServiceCentreId().orElseThrow());
            }
        }
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(0, DspFullDayAnalysisMain.run(
                new String[] {"--config=" + config},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8)));
        assertTrue(errors.toString(StandardCharsets.UTF_8).isEmpty());
        assertTrue(Files.readString(fixture.output()).contains("WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER"));
        assertTrue(Files.readString(fixture.output()).contains("wholeServiceCentrePolicy"));
        assertFalse(Files.exists(fixture.inspection()));
    }

    @Test
    void shouldResolveTheAgreedStationSettingsWithoutChangingOtherProfileValues(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path config = directory.resolve("stations.json");
        Files.writeString(config, stationConfig(fixture, """
                "thirdParty": {"processingDurationSeconds": 20},
                "adapting": {
                  "storeDurationSeconds": 60,
                  "collectDurationSeconds": 10,
                  "processingPositionsPerBench": 3,
                  "waitingCapacityPerBench": 3,
                  "benchIds": [" bench-1 ", "bench-2", "bench-3", "bench-4", "bench-5", "bench-6"]
                }
                """));
        DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommandParser().parse(
                new String[] {"--config=" + config, "--fixed-step-millis=125"});
        DspUncalibratedFullDayProfile profile = DspFullDayAnalysisMain.profile(command);
        DspUncalibratedFullDayProfile baseline = DspFullDayAnalysisMain.profile(
                new DspFullDayAnalysisCommandParser().parse(arguments(fixture, "--fixed-step-millis=125")));

        assertEquals(List.of("bench-1", "bench-2", "bench-3", "bench-4", "bench-5", "bench-6"),
                command.stationProcessingOverrides().benchIds().orElseThrow());
        assertEquals(6, profile.adaptingBenchDefinitions().size());
        for (int index = 0; index < 6; index++) {
            var bench = profile.adaptingBenchDefinitions().get(index);
            assertEquals("bench-" + (index + 1), bench.id());
            assertEquals(60d, bench.storeDurationSeconds());
            assertEquals(60d, bench.processingDurationSeconds());
            assertEquals(10d, bench.collectDurationSeconds());
            assertEquals(3, bench.processingPositions());
        }
        assertEquals(3, profile.queueCapacities().adaptingQueueCapacityPerBench());
        assertEquals(20d, profile.thirdPartyAreaConfig().processingDurationSeconds());
        assertEquals(1, profile.thirdPartyAreaConfig().maxConcurrentVisits());
        assertEquals(16, profile.thirdPartyAreaConfig().waitingCapacity());
        assertEquals(baseline.queueCapacities().warehouseTransportCapacity(),
                profile.queueCapacities().warehouseTransportCapacity());
        assertEquals(baseline.queueCapacities().warehouseInFlightCapacity(),
                profile.queueCapacities().warehouseInFlightCapacity());
        assertEquals(baseline.queueCapacities().stationArrivalQueueCapacity(),
                profile.queueCapacities().stationArrivalQueueCapacity());
        assertEquals(baseline.queueCapacities().tipperInputQueueCapacity(),
                profile.queueCapacities().tipperInputQueueCapacity());
        assertEquals(baseline.p2pLineDefinitions(), profile.p2pLineDefinitions());
        assertEquals(baseline.p2pPlaceholderDurations(), profile.p2pPlaceholderDurations());
        assertEquals(baseline.p2pElasticAllocationConfig(), profile.p2pElasticAllocationConfig());
        assertEquals(baseline.adaptingStorageConfig(), profile.adaptingStorageConfig());
        assertEquals(baseline.routeSpeedUnitsPerSecond(), profile.routeSpeedUnitsPerSecond());
        assertEquals(baseline.fixedStep(), profile.fixedStep());
        assertEquals(baseline.metricSampleInterval(), profile.metricSampleInterval());
        assertEquals("UNCALIBRATED", profile.timingCalibrationStatus());
        assertThrows(UnsupportedOperationException.class,
                () -> profile.adaptingBenchDefinitions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> command.stationProcessingOverrides().benchIds().orElseThrow().clear());
    }

    @Test
    void shouldPreserveAbsentAndEmptyStationDefaultsAndTheOldCommandConstructor(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();
        DspFullDayAnalysisCommand baseline = parser.parse(arguments(fixture));
        DspFullDayAnalysisCommand oldConstructor = new DspFullDayAnalysisCommand(
                baseline.productMasterPath(), baseline.orderPaths(), baseline.outputPath(),
                baseline.inspectionOutputPath(), baseline.operatingDate(), baseline.osrLowWaterMark(),
                baseline.inboundInterval(), baseline.av02Capacity(), baseline.outboundBagCapacity(),
                baseline.maximumPacksPerBag(), baseline.fixedStep(), baseline.stepsPerBatch(),
                baseline.metricSampleInterval(), baseline.overwrite(), baseline.progressLogPath(),
                baseline.progressInterval(), baseline.serviceCentreSchedulePath());
        assertEquals(baseline, oldConstructor);
        assertEquals(DspFullDayStationProcessingOverrides.empty(), baseline.stationProcessingOverrides());
        Path config = directory.resolve("stations.json");
        for (String settings : List.of("", "\"thirdParty\": {}", "\"adapting\": {}",
                "\"thirdParty\": {}, \"adapting\": {}")) {
            Files.writeString(config, stationConfig(fixture, settings));
            DspFullDayAnalysisCommand configured = parser.parse(new String[] {"--config=" + config});
            assertEquals(baseline, configured);
            var expected = DspFullDayAnalysisMain.profile(baseline);
            var actual = DspFullDayAnalysisMain.profile(configured);
            assertEquals(expected.adaptingBenchDefinitions(), actual.adaptingBenchDefinitions());
            assertEquals(expected.queueCapacities(), actual.queueCapacities());
            assertEquals(expected.thirdPartyAreaConfig(), actual.thirdPartyAreaConfig());
        }
    }

    @Test
    void shouldApplyOnlyEachSpecifiedStationProperty(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path config = directory.resolve("stations.json");
        List<String> settings = List.of(
                "\"thirdParty\": {\"processingDurationSeconds\": 20.5}",
                "\"adapting\": {\"storeDurationSeconds\": 45.5}",
                "\"adapting\": {\"collectDurationSeconds\": 10.25}",
                "\"adapting\": {\"processingPositionsPerBench\": 3}",
                "\"adapting\": {\"waitingCapacityPerBench\": 0}",
                "\"adapting\": {\"benchIds\": [\" b1 \", \"b2\"]}");
        for (int index = 0; index < settings.size(); index++) {
            Files.writeString(config, stationConfig(fixture, settings.get(index)));
            var profile = DspFullDayAnalysisMain.profile(new DspFullDayAnalysisCommandParser().parse(
                    new String[] {"--config=" + config}));
            assertEquals(index == 0 ? 20.5d : 60d, profile.thirdPartyAreaConfig().processingDurationSeconds());
            assertEquals(16, profile.thirdPartyAreaConfig().waitingCapacity());
            assertEquals(1, profile.thirdPartyAreaConfig().maxConcurrentVisits());
            assertEquals(index == 4 ? 0 : 4, profile.queueCapacities().adaptingQueueCapacityPerBench());
            assertEquals(index == 5 ? 2 : 1, profile.adaptingBenchDefinitions().size());
            for (int benchIndex = 0; benchIndex < profile.adaptingBenchDefinitions().size(); benchIndex++) {
                var bench = profile.adaptingBenchDefinitions().get(benchIndex);
                assertEquals(index == 5 ? "b" + (benchIndex + 1) : "adapting-bench-1", bench.id());
                assertEquals(index == 1 ? 45.5d : 60d, bench.storeDurationSeconds());
                assertEquals(index == 2 ? 10.25d : 60d, bench.collectDurationSeconds());
                assertEquals(index == 3 ? 3 : 1, bench.processingPositions());
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> new DspFullDayAnalysisCommandParser().parse(
                        arguments(fixture, "--adapting-store-duration-seconds=60")));
    }

    @Test
    void shouldRejectInvalidStationSettingsBeforeInputLoadingOrOutputCreation(
            @TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        // Loading this input would fail for a different reason: station validation must win.
        Files.writeString(fixture.productMaster(), "invalid-product-master");
        Path config = directory.resolve("stations.json");
        List<String> invalidSettings = List.of(
                "\"thirdParty\": null",
                "\"adapting\": null",
                "\"thirdParty\": []",
                "\"adapting\": true",
                "\"thirdParty\": {\"unknown\": 20}",
                "\"adapting\": {\"unknown\": 20}",
                "\"thirdParty\": {\"processingDurationSeconds\": null}",
                "\"adapting\": {\"storeDurationSeconds\": null}",
                "\"adapting\": {\"collectDurationSeconds\": null}",
                "\"adapting\": {\"processingPositionsPerBench\": null}",
                "\"adapting\": {\"waitingCapacityPerBench\": null}",
                "\"adapting\": {\"benchIds\": null}",
                "\"thirdParty\": {\"processingDurationSeconds\": \"20\"}",
                "\"adapting\": {\"storeDurationSeconds\": \"60\"}",
                "\"adapting\": {\"collectDurationSeconds\": false}",
                "\"adapting\": {\"processingPositionsPerBench\": 1.5}",
                "\"adapting\": {\"waitingCapacityPerBench\": 1.5}",
                "\"adapting\": {\"processingPositionsPerBench\": \"3\"}",
                "\"adapting\": {\"waitingCapacityPerBench\": \"3\"}",
                "\"adapting\": {\"benchIds\": \"b1\"}",
                "\"adapting\": {\"benchIds\": [1]}",
                "\"adapting\": {\"benchIds\": [null]}",
                "\"thirdParty\": {}, \"thirdParty\": {}",
                "\"thirdParty\": {\"processingDurationSeconds\": 20, \"processingDurationSeconds\": 30}",
                "\"adapting\": {\"benchIds\": [\"b1\"], \"benchIds\": [\"b2\"]}",
                "\"thirdParty\": {\"processingDurationSeconds\": 0}",
                "\"thirdParty\": {\"processingDurationSeconds\": -1}",
                "\"adapting\": {\"storeDurationSeconds\": 0}",
                "\"adapting\": {\"storeDurationSeconds\": -1}",
                "\"adapting\": {\"collectDurationSeconds\": 0}",
                "\"adapting\": {\"collectDurationSeconds\": -1}",
                "\"thirdParty\": {\"processingDurationSeconds\": 1e309}",
                "\"adapting\": {\"collectDurationSeconds\": 1e-400}",
                "\"adapting\": {\"processingPositionsPerBench\": 0}",
                "\"adapting\": {\"processingPositionsPerBench\": -1}",
                "\"adapting\": {\"waitingCapacityPerBench\": -1}",
                "\"adapting\": {\"processingPositionsPerBench\": 2147483648}",
                "\"adapting\": {\"waitingCapacityPerBench\": 2147483648}",
                "\"adapting\": {\"benchIds\": []}",
                "\"adapting\": {\"benchIds\": [\"b1\", \" b1 \"]}",
                "\"adapting\": {\"benchIds\": [\" \"]}",
                "\"adapting\": {\"benchIds\": [\" third-party-1 \"]}",
                "\"adapting\": {\"benchIds\": [\"b1\", \"b2\"], \"processingPositionsPerBench\": 2147483647}",
                "\"adapting\": {\"benchIds\": [\"b1\", \"b2\"], \"waitingCapacityPerBench\": 2147483647}",
                "\"adapting\": {\"processingPositionsPerBench\": 2147483647}");
        for (String settings : invalidSettings) {
            assertStationSettingsRejectedBeforeLoading(fixture, config, settings);
        }
        var baseline = DspFullDayAnalysisMain.profile(new DspFullDayAnalysisCommandParser().parse(arguments(fixture)));
        for (var line : baseline.p2pLineDefinitions()) {
            assertStationSettingsRejectedBeforeLoading(fixture, config,
                    "\"adapting\": {\"benchIds\": [\"" + line.destination().targetId() + "\"]}");
        }
    }

    private static void assertStationSettingsRejectedBeforeLoading(
            Fixture fixture, Path config, String settings) throws Exception {
        Files.writeString(config, stationConfig(fixture, settings).replace("\"operatingDate\":",
                "\"inspectionOutput\": \"" + jsonPath(fixture.inspection()) + "\",\n"
                        + "\"progressLog\": \"progress.log\",\n\"operatingDate\":"));
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = DspFullDayAnalysisMain.run(new String[] {"--config=" + config},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        String message = errors.toString(StandardCharsets.UTF_8);
        assertEquals(2, result, settings);
        assertTrue(message.contains("--config") || message.contains("adapting.")
                || message.contains("adapting total capacity") || message.contains("thirdParty."),
                () -> settings + " produced: " + message);
        assertFalse(Files.exists(fixture.output()));
        assertFalse(Files.exists(fixture.inspection()));
        assertFalse(Files.exists(config.getParent().resolve("progress.log")));
    }

    private static String stationConfig(Fixture fixture, String settings) {
        String base = configJsonWithOrders(jsonPath(fixture.productMaster()),
                List.of(jsonPath(fixture.secondOrder()), jsonPath(fixture.firstOrder())),
                jsonPath(fixture.output()));
        return base.replace("\"operatingDate\":",
                (settings.isEmpty() ? "" : settings + ",\n") + "\"operatingDate\":");
    }

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
        assertTrue(command.progressLogPath().isEmpty());
        assertEquals(Duration.ofSeconds(300), command.progressInterval());
    }

    @Test
    void shouldParseCliAndConfigProgressSettingsWithCliPrecedence(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path cliLog = directory.resolve("cli").resolve("progress.log");
        DspFullDayAnalysisCommand cli = new DspFullDayAnalysisCommandParser().parse(
                arguments(fixture,
                        "--progress-log=" + cliLog,
                        "--progress-interval-seconds=17"));
        assertEquals(cliLog, cli.progressLogPath().orElseThrow());
        assertEquals(Duration.ofSeconds(17), cli.progressInterval());

        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path ordersDirectory = Files.createDirectory(configDirectory.resolve("orders"));
        Files.copy(fixture.firstOrder(), ordersDirectory.resolve("first.json"));
        Path configPath = configDirectory.resolve("full-day.json");
        Files.writeString(configPath, configJson(
                "../products.csv",
                "orders",
                "report.json",
                null,
                false).replace(
                        "\"operatingDate\":",
                        "\"progressLog\": \"progress.log\",\n"
                                + "  \"progressIntervalSeconds\": 11,\n"
                                + "  \"operatingDate\":"));

        DspFullDayAnalysisCommand configured = new DspFullDayAnalysisCommandParser().parse(
                new String[] {"--config=" + configPath});
        assertEquals(configDirectory.resolve("progress.log").normalize(),
                configured.progressLogPath().orElseThrow());
        assertEquals(Duration.ofSeconds(11), configured.progressInterval());

        DspFullDayAnalysisCommand overridden = new DspFullDayAnalysisCommandParser().parse(
                new String[] {
                    "--config=" + configPath,
                    "--progress-log=" + directory.resolve("override.log"),
                    "--progress-interval-seconds=19"
                });
        assertEquals(directory.resolve("override.log"), overridden.progressLogPath().orElseThrow());
        assertEquals(Duration.ofSeconds(19), overridden.progressInterval());
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
    void shouldSelectConfiguredServiceCentreScheduleAndKeepOmittedFallback(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path configDirectory = Files.createDirectory(directory.resolve("config"));
        Path schedulePath = Files.writeString(configDirectory.resolve("schedule.json"), """
                {"serviceCentres":[{"serviceCentreId":"104","displayName":"Letchworth",
                "priority":123,"trunkerDepartureTime":{"dayOffset":0,
                "localTime":{"hour":17,"minute":0}}}]}
                """);
        String base = configJsonWithOrders("../products.csv",
                List.of("../order-104.json"), "report.json");
        Path configPath = configDirectory.resolve("full-day.json");
        Files.writeString(configPath, base);
        DspFullDayAnalysisCommandParser parser = new DspFullDayAnalysisCommandParser();

        DspFullDayAnalysisCommand fallback = parser.parse(new String[] {"--config=" + configPath});
        assertTrue(fallback.serviceCentreSchedulePath().isEmpty());
        assertEquals(999, DspFullDayAnalysisMain.profile(fallback).timetable().require("104").priority());

        Files.writeString(configPath, withSchedule(base, "schedule.json"));
        DspFullDayAnalysisCommand relative = parser.parse(new String[] {"--config=" + configPath});
        assertEquals(schedulePath, relative.serviceCentreSchedulePath().orElseThrow());
        assertEquals(123, DspFullDayAnalysisMain.profile(relative).timetable().require("104").priority());

        Files.writeString(configPath, withSchedule(base, jsonPath(schedulePath)));
        DspFullDayAnalysisCommand absolute = parser.parse(new String[] {"--config=" + configPath});
        assertEquals(schedulePath, absolute.serviceCentreSchedulePath().orElseThrow());

        Files.writeString(configPath, withSchedule(base, "missing.json"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + configPath}));
        Files.writeString(configPath, withSchedule(base, "."));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + configPath}));
        Files.writeString(configPath, withSchedule(base, " "));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + configPath}));
        Files.writeString(schedulePath, "invalid-json");
        Files.writeString(configPath, withSchedule(base, "schedule.json"));
        assertThrows(IllegalArgumentException.class,
                () -> DspFullDayAnalysisMain.profile(
                        parser.parse(new String[] {"--config=" + configPath})));
    }

    private static String withSchedule(String base, String schedulePath) {
        return base.replace("\"operatingDate\":",
                "\"serviceCentreSchedule\": \"" + schedulePath + "\",\n"
                        + "  \"operatingDate\":");
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
                () -> parser.parse(arguments(fixture, "--progress-interval-seconds=0")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture,
                        "--progress-log=" + directory.resolve("one.log"),
                        "--progress-log=" + directory.resolve("two.log"))));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "--operating-date=not-a-date")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture, "plain-argument")));

        String progressConfig = configJson(
                jsonPath(fixture.productMaster()),
                jsonPath(directory),
                "report.json",
                null,
                false).replace(
                        "\"operatingDate\":",
                        "\"progressLog\": \"progress.log\",\n"
                                + "  \"operatingDate\":");
        Path progressConfigPath = directory.resolve("progress-config.json");
        Files.writeString(progressConfigPath, progressConfig.replace(
                "\"progressLog\": \"progress.log\"",
                "\"progressLog\": \"progress.log\",\n"
                        + "  \"progressLog\": \"other.log\""));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new String[] {"--config=" + progressConfigPath}));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture,
                        "--inspection-output=" + fixture.output())));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture,
                        "--progress-log=" + fixture.output())));
        Path inspection = directory.resolve("same-inspection.txt");
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(arguments(fixture,
                        "--inspection-output=" + inspection,
                        "--progress-log=" + inspection)));
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
    void shouldRefuseExistingProgressLogBeforeCreatingFinalOutputs(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path progressLog = directory.resolve("existing-progress.log");
        Files.writeString(progressLog, "keep-this-content", StandardCharsets.UTF_8);

        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        int exitCode = DspFullDayAnalysisMain.run(
                arguments(fixture, "--progress-log=" + progressLog),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertNotEquals(0, exitCode);
        assertEquals("keep-this-content", Files.readString(progressLog));
        assertFalse(Files.exists(fixture.output()));
        assertFalse(Files.exists(fixture.inspection()));
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
