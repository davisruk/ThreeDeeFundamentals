package online.davisfamily.warehouse.sim.dsp.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocation;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class DspFullDayAnalysisRunnerTest {

    @Test
    void shouldCompleteEarlyInBoundedHeadlessBatchesAndPrintLockedInspections(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = profile(directory, Duration.ofSeconds(1), 20);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path output = directory.resolve("early-report.json");
        ByteArrayOutputStream inspectionBytes = new ByteArrayOutputStream();
        AtomicLong clock = new AtomicLong();

        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> clock.addAndGet(1_000_000L),
                new PrintStream(inspectionBytes, true, StandardCharsets.UTF_8))
                .run(input, profile, output, Optional.empty(), false);

        assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, report.state());
        assertTrue(report.metrics().observedSimulationDuration().compareTo(Duration.ofHours(18)) < 0);
        assertTrue(Files.isRegularFile(output));
        String inspection = inspectionBytes.toString(StandardCharsets.UTF_8);
        assertTrue(inspection.contains("[dsp-full-day:start]"));
        assertTrue(inspection.contains("[dsp-full-day:final]"));
        assertTrue(inspection.contains("[dsp-full-day:completion="));
        assertTrue(clock.get() >= 4);
        assertFalse(inspection.contains("state=RUNNING termination=ALL_SUPPORTED_WORK_COMPLETE"));
    }

    @Test
    void shouldStopAtTheExactHardCutoffWithoutUpdatingAfterTerminal(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = slowProfile(directory, Duration.ofHours(1), 3);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path output = directory.resolve("cutoff-report.json");
        AtomicLong clock = new AtomicLong();

        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> clock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(input, profile, output, false);

        Duration hardCutoff = profile.operationalClockConfig().operatingDurationUntilHardCutoff();
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
        assertEquals(hardCutoff, report.metrics().observedSimulationDuration());
        assertEquals(hardCutoff, report.runtimeSnapshot().clock().elapsedSimulationTime());
        assertEquals(18, report.runtimeSnapshot().clock().elapsedSimulationTime().toHours());
    }

    @Test
    void shouldEmitCompactProgressAtFixedStepThresholdsInsideEachBatch(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = slowProfile(directory, Duration.ofHours(1), 3);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path output = directory.resolve("threshold-report.json");
        Path progressLog = directory.resolve("progress").resolve("full-day.log");
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();

        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> 1_000_000L,
                new PrintStream(consoleBytes, true, StandardCharsets.UTF_8))
                .run(
                        input,
                        profile,
                        output,
                        Optional.empty(),
                        Optional.of(progressLog),
                        Duration.ofHours(2),
                        false);

        String progress = Files.readString(progressLog);
        assertEquals(1, occurrences(progress, "[dsp-full-day:progress=PT2H]"));
        assertEquals(1, occurrences(progress, "[dsp-full-day:progress=PT4H]"));
        assertEquals(1, occurrences(progress, "[dsp-full-day:progress=PT18H]"));
        assertTrue(progress.indexOf("[dsp-full-day:start]")
                < progress.indexOf("[dsp-full-day:progress=PT2H]"));
        assertTrue(progress.lastIndexOf("[dsp-full-day:final]")
                > progress.lastIndexOf("[dsp-full-day:progress=PT18H]"));
        assertFalse(progress.contains("unfinishedIdentities"));
        assertTrue(progress.contains("InboundReleasedNotConsumed: "));
        assertTrue(progress.contains("ClosedOutboundTotesByServiceCentre: "));
        assertTrue(progress.lines().filter(line -> line.startsWith(
                "ClosedOutboundTotesByServiceCentre: ")).allMatch(
                        line -> line.contains(" | AllocatedBagsByServiceCentre: ")
                                && line.contains(" | MissingPacksByServiceCentre: ")));
        assertEquals(
                progress,
                consoleBytes.toString(StandardCharsets.UTF_8));
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
    }

    @Test
    void shouldCountReleasedInboundTotesUntilTheirConsumption() {
        InboundToteManifestCatalog manifests = new InboundToteManifestCatalog(List.of(
                manifest("adapted", OrderType.ADAPTED),
                manifest("full-pack", OrderType.FULL_PACK),
                manifest("associated", OrderType.ASSOCIATED),
                manifest("unreleased", OrderType.FULL_PACK)));
        InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(), manifests);
        lifecycle.activate(new PhysicalToteId("adapted"), Duration.ZERO);
        lifecycle.activate(new PhysicalToteId("full-pack"), Duration.ZERO);
        lifecycle.activate(new PhysicalToteId("associated"), Duration.ZERO);

        assertEquals("InboundReleasedNotConsumed: adapted=1 fullPack=1 associated=1",
                DspFullDayAnalysisRunner.inboundTotesInFlight(lifecycle.snapshot(), manifests));
        lifecycle.advanceToPreP2p(new PhysicalToteId("full-pack"), Duration.ofSeconds(1));
        lifecycle.consumeAtAdapting(new PhysicalToteId("adapted"), Duration.ofSeconds(1));
        assertEquals("InboundReleasedNotConsumed: adapted=0 fullPack=1 associated=1",
                DspFullDayAnalysisRunner.inboundTotesInFlight(lifecycle.snapshot(), manifests));
        lifecycle.consumeAtP2p(new PhysicalToteId("full-pack"), Duration.ofSeconds(2));
        assertEquals("InboundReleasedNotConsumed: adapted=0 fullPack=0 associated=1",
                DspFullDayAnalysisRunner.inboundTotesInFlight(lifecycle.snapshot(), manifests));
        lifecycle.advanceToPreP2p(new PhysicalToteId("associated"), Duration.ofSeconds(2));
        lifecycle.consumeAtP2p(new PhysicalToteId("associated"), Duration.ofSeconds(3));
        assertEquals("InboundReleasedNotConsumed: adapted=0 fullPack=0 associated=0",
                DspFullDayAnalysisRunner.inboundTotesInFlight(lifecycle.snapshot(), manifests));
        assertThrows(IllegalArgumentException.class,
                () -> DspFullDayAnalysisRunner.inboundTotesInFlight(null, manifests));
    }

    @Test
    void shouldCountClosedOutboundTotesAndAllocatedBagsByServiceCentre() {
        AllocatedOutboundBag openBag = allocatedBag("open", "104", "open-prescription");
        AllocatedOutboundBag firstClosedBag = allocatedBag(
                "closed-1", "104", "first-closed-prescription");
        AllocatedOutboundBag secondClosedBag = allocatedBag(
                "closed-2", "104", "second-closed-prescription");
        AllocatedOutboundBag otherCentreBag = allocatedBag(
                "closed-3", "108", "other-centre-prescription");
        OutboundAllocationSnapshot firstLine = new OutboundAllocationSnapshot(
                Map.of(new P2pLineId("line-1"), outboundTote(
                        "open", "line-1", "104", false, List.of(openBag))),
                List.of(outboundTote("closed-1", "line-1", "104", true,
                        List.of(firstClosedBag))),
                List.of(openBag, firstClosedBag));
        OutboundAllocationSnapshot secondLine = new OutboundAllocationSnapshot(Map.of(),
                List.of(outboundTote("closed-2", "line-2", "104", true,
                                List.of(secondClosedBag)),
                        outboundTote("closed-3", "line-2", "108", true,
                                List.of(otherCentreBag))),
                List.of(secondClosedBag, otherCentreBag));

        String result = DspFullDayAnalysisRunner.closedOutboundTotesByServiceCentre(
                List.of(firstLine, secondLine), List.of("109", "108", "104"),
                Map.of("104", 2, "108", 1));
        String originalToteSegment = "ClosedOutboundTotesByServiceCentre: 104=2 108=1 109=0";
        assertTrue(result.startsWith(originalToteSegment + " | "));
        assertEquals(originalToteSegment
                + " | AllocatedBagsByServiceCentre: 104=3 108=1 109=0"
                + " | MissingPacksByServiceCentre: 104=2 108=1 109=0", result);

        OutboundAllocationSnapshot empty = new OutboundAllocationSnapshot(
                Map.of(), List.of(), List.of());
        assertEquals("ClosedOutboundTotesByServiceCentre: 104=0 108=0"
                        + " | AllocatedBagsByServiceCentre: 104=0 108=0"
                        + " | MissingPacksByServiceCentre: 104=0 108=0",
                DspFullDayAnalysisRunner.closedOutboundTotesByServiceCentre(
                        List.of(empty), List.of("108", "104")));
    }

    @Test
    void shouldLogMonotonicWallIntervalAtEachConfiguredProgressMilestone(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = slowProfile(directory, Duration.ofHours(1), 3);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path progressLog = directory.resolve("wall-interval-progress.log");
        AtomicLong clock = new AtomicLong();

        new DspFullDayAnalysisRunner(
                () -> clock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(input, profile, directory.resolve("wall-interval-report.json"),
                        Optional.empty(), Optional.of(progressLog), Duration.ofHours(2), false);

        List<String> lines = Files.readAllLines(progressLog);
        int first = lines.indexOf("[dsp-full-day:progress=PT2H]");
        int second = lines.indexOf("[dsp-full-day:progress=PT4H]");
        assertTrue(first >= 0);
        assertTrue(second > first);
        assertEquals("WallClock: sincePreviousProgress=PT0.002S", lines.get(first + 3));
        assertEquals("WallClock: sincePreviousProgress=PT0.003S", lines.get(second + 3));
        assertEquals(lines.stream().filter(line -> line.startsWith("[dsp-full-day:progress=")).count(),
                lines.stream().filter(line -> line.startsWith("WallClock: ")).count());
    }

    @Test
    void shouldReadPublishedClockForNonAlignedProgressThresholds(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = slowProfile(directory, Duration.ofHours(1), 3);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path output = directory.resolve("non-aligned-report.json");
        Path progressLog = directory.resolve("non-aligned-progress.log");
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();

        DspFullDayAnalysisReport report = new DspFullDayAnalysisRunner(
                () -> 1_000_000L,
                new PrintStream(consoleBytes, true, StandardCharsets.UTF_8))
                .run(
                        input,
                        profile,
                        output,
                        Optional.empty(),
                        Optional.of(progressLog),
                        Duration.ofMinutes(90),
                        false);

        String progress = Files.readString(progressLog);
        List<String> labels = progress.lines()
                .filter(line -> line.startsWith("[dsp-full-day:progress="))
                .toList();
        assertEquals(List.of(
                "[dsp-full-day:progress=PT2H]",
                "[dsp-full-day:progress=PT3H]",
                "[dsp-full-day:progress=PT5H]",
                "[dsp-full-day:progress=PT6H]",
                "[dsp-full-day:progress=PT8H]",
                "[dsp-full-day:progress=PT9H]",
                "[dsp-full-day:progress=PT11H]",
                "[dsp-full-day:progress=PT12H]",
                "[dsp-full-day:progress=PT14H]",
                "[dsp-full-day:progress=PT15H]",
                "[dsp-full-day:progress=PT17H]",
                "[dsp-full-day:progress=PT18H]"), labels);
        assertTrue(progress.indexOf("[dsp-full-day:start]")
                < progress.indexOf(labels.getFirst()));
        assertTrue(progress.lastIndexOf("[dsp-full-day:final]")
                > progress.lastIndexOf(labels.getLast()));
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
        assertEquals(Duration.ofHours(18), report.runtimeSnapshot().clock().elapsedSimulationTime());
        assertEquals(progress, consoleBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldLeaveFlushedProgressAndFailureBlockWhenFinalReportWriteFails(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = profile(directory, Duration.ofSeconds(1), 20);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);
        Path progressLog = directory.resolve("failed-run.log");

        Exception failure = assertThrows(Exception.class, () -> new DspFullDayAnalysisRunner(
                () -> 1_000_000L,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(
                        input,
                        profile,
                        directory,
                        Optional.empty(),
                        Optional.of(progressLog),
                        Duration.ofSeconds(5),
                        false));

        String progress = Files.readString(progressLog);
        assertTrue(progress.contains("[dsp-full-day:start]"));
        assertTrue(progress.contains("[dsp-full-day:final]"));
        assertTrue(progress.contains("[dsp-full-day:failure]"));
        assertTrue(progress.contains(failure.getClass().getName()));
    }

    @Test
    void shouldRecordMeasuredRealTimeAndProduceByteIdenticalRepeats(@TempDir Path directory)
            throws Exception {
        Path firstDirectory = Files.createDirectory(directory.resolve("first"));
        Path secondDirectory = Files.createDirectory(directory.resolve("second"));
        Path firstOutput = firstDirectory.resolve("report.json");
        Path secondOutput = secondDirectory.resolve("report.json");

        DspUncalibratedFullDayProfile firstProfile = profile(
                firstDirectory, Duration.ofSeconds(1), 20);
        DspUncalibratedFullDayProfile secondProfile = profile(
                secondDirectory, Duration.ofSeconds(1), 20);
        DspFullDayLoadedInput firstInput = DspFullDayReportTestSupport.input(
                firstDirectory, firstProfile);
        DspFullDayLoadedInput secondInput = DspFullDayReportTestSupport.input(
                secondDirectory, secondProfile);
        AtomicLong firstClock = new AtomicLong();
        AtomicLong secondClock = new AtomicLong();
        ByteArrayOutputStream firstProgress = new ByteArrayOutputStream();

        DspFullDayAnalysisReport first = new DspFullDayAnalysisRunner(
                () -> firstClock.addAndGet(1_000_000L),
                new PrintStream(firstProgress, true, StandardCharsets.UTF_8))
                .run(firstInput, firstProfile, firstOutput, false);
        DspFullDayAnalysisReport second = new DspFullDayAnalysisRunner(
                () -> secondClock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(secondInput, secondProfile, secondOutput, false);

        long progressClockReads = firstProgress.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("[dsp-full-day:progress=")).count();
        long batchCount = (firstClock.get() / 1_000_000L - progressClockReads - 1L) / 2L;
        double expectedSpeed = first.metrics().observedSimulationDuration().toNanos()
                / (double) (batchCount * 1_000_000L);
        assertEquals(1d, first.metrics().requestedExecutionSpeed());
        assertEquals(expectedSpeed, first.metrics().achievedExecutionSpeed(), 1.0e-12);
        assertEquals(first.metrics().achievedExecutionSpeed(), second.metrics().achievedExecutionSpeed());
        assertArrayEquals(Files.readAllBytes(firstOutput), Files.readAllBytes(secondOutput));
    }

    @Test
    void shouldPropagateWriteFailureAfterTheRuntimeHasBeenExecuted(@TempDir Path directory)
            throws Exception {
        DspUncalibratedFullDayProfile profile = profile(directory, Duration.ofSeconds(1), 20);
        DspFullDayLoadedInput input = DspFullDayReportTestSupport.input(directory, profile);

        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> new DspFullDayAnalysisRunner(
                () -> 1_000_000L,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(input, profile, directory, false));
    }

    private static DspUncalibratedFullDayProfile profile(
            Path directory,
            Duration fixedStep,
            int stepsPerBatch) {
        DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommand(
                directory.resolve("products.csv"),
                List.of(directory.resolve("orders.json")),
                directory.resolve("report.json"),
                Optional.empty(),
                LocalDate.of(2026, 9, 2),
                10,
                Duration.ofSeconds(1),
                2,
                4,
                4,
                fixedStep,
                stepsPerBatch,
                Duration.ofSeconds(60),
                false,
                Optional.empty(),
                Duration.ofSeconds(300),
                Optional.empty());
        return DspFullDayAnalysisMain.profile(command);
    }

    private static DspUncalibratedFullDayProfile slowProfile(
            Path directory,
            Duration fixedStep,
            int stepsPerBatch) {
        DspUncalibratedFullDayProfile baseline = profile(directory, fixedStep, stepsPerBatch);
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                baseline.osrInventoryConfig(),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                baseline.outboundToteConfig(),
                baseline.maximumPacksPerBag(),
                baseline.fixedStep(),
                baseline.maximumStepsPerAdvance(),
                baseline.metricSampleInterval(),
                0.000001d,
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                List.of(new DspUncalibratedFullDayProfile.AdaptingBenchDefinition(
                        "adapting-bench-1", 100_000d)),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static InboundToteManifest manifest(String id, OrderType orderType) {
        return new InboundToteManifest(new PhysicalToteId(id), new OrderSheetKey("order-" + id, 1),
                orderType, "104", List.of(new DspOrderItem("line-" + id, "product", 1)), 1);
    }

    private static OutboundToteSnapshot outboundTote(
            String id, String lineId, String serviceCentreId, boolean closed,
            List<AllocatedOutboundBag> bags) {
        return new OutboundToteSnapshot(new PhysicalToteId(id), new P2pLineId(lineId),
                Optional.of(serviceCentreId), Optional.of("pharmacy"), 2, bags,
                closed ? Optional.of(OutboundToteClosureReason.HARD_CUTOFF) : Optional.empty());
    }

    private static AllocatedOutboundBag allocatedBag(
            String toteId, String serviceCentreId, String prescriptionId) {
        OrderSheetKey sheet = new OrderSheetKey("order-" + prescriptionId, 1);
        PlannedBag plannedBag = new PlannedBag(
                new BagKey(prescriptionId, 1), serviceCentreId, "pharmacy", "patient",
                prescriptionId, List.of("pack-" + prescriptionId), List.of(sheet));
        return new AllocatedOutboundBag(plannedBag, new PhysicalToteId(toteId),
                List.of(new OutputSheetAllocation(sheet, sheet)));
    }

    private static int occurrences(String value, String searched) {
        int count = 0;
        int position = 0;
        while ((position = value.indexOf(searched, position)) >= 0) {
            count++;
            position += searched.length();
        }
        return count;
    }
}
