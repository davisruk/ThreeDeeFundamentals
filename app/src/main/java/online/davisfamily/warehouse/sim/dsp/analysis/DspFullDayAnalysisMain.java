package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.AdaptingBenchDefinition;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.QueueCapacities;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;

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
        DspFullDayStationProcessingOverrides overrides = command.stationProcessingOverrides();
        ThirdPartyAreaConfig thirdParty = baseline.thirdPartyAreaConfig();
        if (overrides.thirdPartyDurationSeconds().isPresent()) {
            thirdParty = new ThirdPartyAreaConfig(thirdParty.waitingCapacity(),
                    thirdParty.maxConcurrentVisits(), overrides.thirdPartyDurationSeconds().getAsDouble());
        }
        QueueCapacities queues = baseline.queueCapacities();
        if (overrides.waitingCapacityPerBench().isPresent()) {
            queues = new QueueCapacities(queues.warehouseTransportCapacity(),
                    queues.warehouseInFlightCapacity(), queues.stationArrivalQueueCapacity(),
                    queues.tipperInputQueueCapacity(), overrides.waitingCapacityPerBench().getAsInt());
        }
        List<AdaptingBenchDefinition> benches = adaptingDefinitions(baseline, overrides);
        validateStationCapacityAndTargets(baseline, benches, queues);
        DspServiceCentreTimetable timetable = command.serviceCentreSchedulePath()
                .map(path -> new DspFullDayServiceCentreScheduleLoader().load(path))
                .orElseGet(baseline::timetable);
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
                queues,
                thirdParty,
                baseline.adaptingStorageConfig(),
                benches,
                baseline.p2pPlaceholderDurations(),
                baseline.p2pLineDefinitions(),
                baseline.prlCountPerLine(),
                timetable,
                command.schedulerPolicy(),
                command.p2pOutstandingToteWatermark());
    }

    private static List<AdaptingBenchDefinition> adaptingDefinitions(
            DspUncalibratedFullDayProfile baseline,
            DspFullDayStationProcessingOverrides overrides) {
        List<AdaptingBenchDefinition> original = baseline.adaptingBenchDefinitions();
        if (overrides.benchIds().isEmpty() && overrides.adaptingStoreDurationSeconds().isEmpty()
                && overrides.adaptingCollectDurationSeconds().isEmpty()
                && overrides.processingPositionsPerBench().isEmpty()) {
            return original;
        }
        List<String> ids = overrides.benchIds().orElse(null);
        int count = ids == null ? original.size() : ids.size();
        List<AdaptingBenchDefinition> definitions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            AdaptingBenchDefinition defaults = original.get(ids == null ? index : 0);
            definitions.add(new AdaptingBenchDefinition(
                    ids == null ? defaults.id() : ids.get(index),
                    overrides.adaptingStoreDurationSeconds().orElse(defaults.storeDurationSeconds()),
                    overrides.adaptingCollectDurationSeconds().orElse(defaults.collectDurationSeconds()),
                    overrides.processingPositionsPerBench().orElse(defaults.processingPositions())));
        }
        return List.copyOf(definitions);
    }

    private static void validateStationCapacityAndTargets(
            DspUncalibratedFullDayProfile baseline,
            List<AdaptingBenchDefinition> benches,
            QueueCapacities queues) {
        Set<String> reservedTargets = new HashSet<>();
        reservedTargets.add("third-party-1");
        for (var line : baseline.p2pLineDefinitions()) {
            reservedTargets.add(line.destination().targetId());
        }
        try {
            int totalPositions = 0;
            for (AdaptingBenchDefinition bench : benches) {
                if (reservedTargets.contains(bench.id())) {
                    throw new IllegalArgumentException("adapting.benchIds collides with route target: " + bench.id());
                }
                totalPositions = Math.addExact(totalPositions, bench.processingPositions());
            }
            int totalWaiting = Math.multiplyExact(queues.adaptingQueueCapacityPerBench(), benches.size());
            Math.addExact(totalPositions, totalWaiting);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("adapting total capacity exceeds int range", exception);
        }
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
