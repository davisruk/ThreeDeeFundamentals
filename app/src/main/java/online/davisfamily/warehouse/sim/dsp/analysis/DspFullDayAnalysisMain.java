package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.PrintStream;

/** Command-line entry point for one explicitly uncalibrated headless DSP operating-day run. */
public final class DspFullDayAnalysisMain {
    private DspFullDayAnalysisMain() {
    }

    public static void main(String[] arguments) {
        int exitCode = run(arguments, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(
            String[] arguments,
            PrintStream output,
            PrintStream error) {
        if (output == null || error == null) {
            throw new IllegalArgumentException("output and error streams must not be null");
        }
        try {
            DspFullDayAnalysisCommand command = new DspFullDayAnalysisCommandParser().parse(arguments);
            DspUncalibratedFullDayProfile profile = profile(command);
            DspFullDayLoadedInput input = new DspFullDayInputLoader().load(
                    command.inputPaths(),
                    profile);
            new DspFullDayAnalysisRunner(System::nanoTime, output).run(
                    input,
                    profile,
                    command.outputPath(),
                    command.inspectionOutputPath(),
                    command.progressLogPath(),
                    command.progressInterval(),
                    command.overwrite());
            return 0;
        } catch (Exception exception) {
            error.println("dspFullDayAnalysis: " + message(exception));
            return 2;
        }
    }

    static DspUncalibratedFullDayProfile profile(DspFullDayAnalysisCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        DspUncalibratedFullDayProfile baseline = DspUncalibratedFullDayProfile.productionBaseline(
                command.operatingDate(),
                command.osrLowWaterMark(),
                command.inboundInterval(),
                command.av02Capacity(),
                command.outboundBagCapacity(),
                command.maximumPacksPerBag());
        return new DspUncalibratedFullDayProfile(
                baseline.operatingDate(),
                baseline.osrInventoryConfig(),
                baseline.serviceCentreSupplyConfig(),
                baseline.inboundToteArrivalPolicy(),
                baseline.av02AllocationConfig(),
                baseline.p2pElasticAllocationConfig(),
                baseline.outboundToteConfig(),
                baseline.maximumPacksPerBag(),
                command.fixedStep(),
                command.stepsPerBatch(),
                command.metricSampleInterval(),
                baseline.routeSpeedUnitsPerSecond(),
                baseline.queueCapacities(),
                baseline.thirdPartyAreaConfig(),
                baseline.adaptingStorageConfig(),
                baseline.adaptingBenchDefinitions(),
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                baseline.timetable());
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
