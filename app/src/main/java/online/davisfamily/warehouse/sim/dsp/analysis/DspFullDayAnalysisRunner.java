package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import online.davisfamily.threedee.sim.framework.time.FixedStepExecutionConfig;
import online.davisfamily.threedee.sim.framework.time.FixedStepExecutionDriver;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayAnalysisReport;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionFormatter;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayInspectionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.report.DspFullDayReportJsonWriter;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntime;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;

/** Executes one loaded full-day DSP analysis through bounded headless fixed steps. */
public final class DspFullDayAnalysisRunner {
    private final LongSupplier monotonicNanos;
    private final PrintStream inspectionOutput;
    private final DspFullDayAnalysisRuntimeFactory runtimeFactory;
    private final DspFullDayReportFactory reportFactory;
    private final DspFullDayReportJsonWriter reportWriter;
    private final DspFullDayInspectionFormatter inspectionFormatter;

    public DspFullDayAnalysisRunner() {
        this(System::nanoTime, System.out);
    }

    public DspFullDayAnalysisRunner(
            LongSupplier monotonicNanos,
            PrintStream inspectionOutput) {
        this(
                monotonicNanos,
                inspectionOutput,
                new DspFullDayAnalysisRuntimeFactory(),
                new DspFullDayReportFactory(),
                new DspFullDayReportJsonWriter(),
                new DspFullDayInspectionFormatter());
    }

    DspFullDayAnalysisRunner(
            LongSupplier monotonicNanos,
            PrintStream inspectionOutput,
            DspFullDayAnalysisRuntimeFactory runtimeFactory,
            DspFullDayReportFactory reportFactory,
            DspFullDayReportJsonWriter reportWriter,
            DspFullDayInspectionFormatter inspectionFormatter) {
        if (monotonicNanos == null || inspectionOutput == null || runtimeFactory == null
                || reportFactory == null || reportWriter == null || inspectionFormatter == null) {
            throw new IllegalArgumentException("runner dependencies must not be null");
        }
        this.monotonicNanos = monotonicNanos;
        this.inspectionOutput = inspectionOutput;
        this.runtimeFactory = runtimeFactory;
        this.reportFactory = reportFactory;
        this.reportWriter = reportWriter;
        this.inspectionFormatter = inspectionFormatter;
    }

    public DspFullDayAnalysisReport run(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile,
            Path outputPath,
            Optional<Path> inspectionPath,
            boolean overwrite) throws IOException {
        if (input == null || profile == null || outputPath == null || inspectionPath == null) {
            throw new IllegalArgumentException("runner inputs must not be null");
        }
        DspFullDayAnalysisRuntime runtime = runtimeFactory.create(input, profile);
        try {
            return execute(runtime, input, profile, outputPath, inspectionPath, overwrite);
        } finally {
            runtime.close();
        }
    }

    public DspFullDayAnalysisReport run(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile,
            Path outputPath,
            boolean overwrite) throws IOException {
        return run(input, profile, outputPath, Optional.empty(), overwrite);
    }

    private DspFullDayAnalysisReport execute(
            DspFullDayAnalysisRuntime runtime,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile,
            Path outputPath,
            Optional<Path> inspectionPath,
            boolean overwrite) throws IOException {
        FixedStepExecutionDriver driver = new FixedStepExecutionDriver(
                FixedStepExecutionConfig.headless(
                        profile.fixedStep(),
                        profile.maximumStepsPerAdvance()));
        Set<String> completedServiceCentres = new HashSet<>();
        Duration nextHourlyInspection = Duration.ofHours(1);

        printInspection(
                "start",
                reportFactory.createInspectionSnapshot(runtime.snapshot(), input, profile));

        long batchCount = 0L;
        long maximumBatchCount = maximumBatchCount(profile);
        while (runtime.state() == DspFullDayRuntimeState.RUNNING) {
            if (++batchCount > maximumBatchCount) {
                throw new IllegalStateException(
                        "full-day analysis exceeded the hard-cutoff batch bound");
            }

            long startNanos = monotonicNanos.getAsLong();
            try {
                driver.advance(Duration.ZERO, stepSeconds -> {
                    if (runtime.state() != DspFullDayRuntimeState.RUNNING) {
                        throw TerminalReached.INSTANCE;
                    }
                    runtime.update(stepSeconds);
                });
            } catch (TerminalReached reached) {
                // The terminal step has already returned to the driver and was counted. The
                // next callback is stopped before it can call runtime.update(...).
            }
            long endNanos = monotonicNanos.getAsLong();
            if (endNanos < startNanos) {
                throw new IllegalStateException("monotonic clock moved backwards");
            }
            driver.recordHeadlessRealElapsed(Duration.ofNanos(endNanos - startNanos));

            var currentRuntime = runtime.snapshot();
            while (!currentRuntime.clock().elapsedSimulationTime().minus(nextHourlyInspection).isNegative()) {
                printInspection(
                        "hour=" + nextHourlyInspection,
                        reportFactory.createInspectionSnapshot(
                                currentRuntime,
                                input,
                                profile));
                nextHourlyInspection = nextHourlyInspection.plus(Duration.ofHours(1));
            }
            for (var completion : currentRuntime.completions()) {
                if (completion.complete() && completedServiceCentres.add(completion.serviceCentreId())) {
                    printInspection(
                            "completion=" + completion.serviceCentreId(),
                            reportFactory.createInspectionSnapshot(
                                    currentRuntime,
                                    input,
                                    profile));
                }
            }
        }

        var executionSnapshot = driver.snapshot();
        runtime.metricsCollector().recordExecutionSpeed(
                executionSnapshot.requestedTimeScale(),
                executionSnapshot.achievedTimeScale());
        DspFullDayAnalysisReport report = reportFactory.create(runtime.snapshot(), input, profile);
        printInspection("final", DspFullDayInspectionSnapshot.from(report));
        reportWriter.write(report, outputPath, overwrite);
        if (inspectionPath.isPresent()) {
            writeInspectionFile(
                    inspectionPath.orElseThrow(),
                    inspectionFormatter.describe(DspFullDayInspectionSnapshot.from(report)),
                    overwrite);
        }
        return report;
    }

    private void printInspection(
            String milestone,
            DspFullDayInspectionSnapshot snapshot) {
        inspectionOutput.println("[dsp-full-day:" + milestone + "]");
        for (String line : inspectionFormatter.describe(snapshot)) {
            inspectionOutput.println(line);
        }
    }

    private static long maximumBatchCount(DspUncalibratedFullDayProfile profile) {
        long cutoffNanos = profile.operationalClockConfig().operatingDurationUntilHardCutoff()
                .toNanos();
        long batchNanos = Math.multiplyExact(
                profile.fixedStep().toNanos(),
                (long) profile.maximumStepsPerAdvance());
        return Math.addExact(cutoffNanos / batchNanos, 2L);
    }

    private static void writeInspectionFile(
            Path output,
            List<String> lines,
            boolean overwrite) throws IOException {
        byte[] bytes = (String.join(System.lineSeparator(), lines)
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("inspection output has no parent directory: " + output);
        }
        if (!overwrite && Files.exists(output)) {
            throw new FileAlreadyExistsException(output.toString());
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(
                parent,
                output.getFileName().toString() + ".",
                ".tmp");
        boolean moved = false;
        try {
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                if (overwrite) {
                    Files.move(
                            temporary,
                            output,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException unsupported) {
                if (overwrite) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, output);
                }
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static final class TerminalReached extends RuntimeException {
        private static final TerminalReached INSTANCE = new TerminalReached();

        private TerminalReached() {
            super(null, null, false, false);
        }
    }
}
