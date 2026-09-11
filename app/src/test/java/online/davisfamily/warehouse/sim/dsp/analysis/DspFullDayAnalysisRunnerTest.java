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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportTestSupport;

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
        assertEquals(
                progress,
                consoleBytes.toString(StandardCharsets.UTF_8));
        assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, report.state());
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

        DspFullDayAnalysisReport first = new DspFullDayAnalysisRunner(
                () -> firstClock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(firstInput, firstProfile, firstOutput, false);
        DspFullDayAnalysisReport second = new DspFullDayAnalysisRunner(
                () -> secondClock.addAndGet(1_000_000L),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(secondInput, secondProfile, secondOutput, false);

        long batchCount = firstClock.get() / 2_000_000L;
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
                Duration.ofSeconds(300));
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
