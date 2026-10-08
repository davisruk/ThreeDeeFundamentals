package online.davisfamily.warehouse.sim.dsp.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.scheduler.policy.DspSchedulerPolicy;

/** Package-private validated command values for the full-day analysis entry point. */
record DspFullDayAnalysisCommand(
        Path productMasterPath,
        List<Path> orderPaths,
        Path outputPath,
        Optional<Path> inspectionOutputPath,
        LocalDate operatingDate,
        int osrLowWaterMark,
        Duration inboundInterval,
        int av02Capacity,
        int outboundBagCapacity,
        int maximumPacksPerBag,
        Duration fixedStep,
        int stepsPerBatch,
        Duration metricSampleInterval,
        boolean overwrite,
        Optional<Path> progressLogPath,
        Duration progressInterval,
        Optional<Path> serviceCentreSchedulePath,
        DspFullDayStationProcessingOverrides stationProcessingOverrides,
        DspSchedulerPolicy schedulerPolicy) {

    /** Compatibility constructor retaining the deadline-aware policy set. */
    DspFullDayAnalysisCommand(
            Path productMasterPath,
            List<Path> orderPaths,
            Path outputPath,
            Optional<Path> inspectionOutputPath,
            LocalDate operatingDate,
            int osrLowWaterMark,
            Duration inboundInterval,
            int av02Capacity,
            int outboundBagCapacity,
            int maximumPacksPerBag,
            Duration fixedStep,
            int stepsPerBatch,
            Duration metricSampleInterval,
            boolean overwrite,
            Optional<Path> progressLogPath,
            Duration progressInterval,
            Optional<Path> serviceCentreSchedulePath,
            DspFullDayStationProcessingOverrides stationProcessingOverrides) {
        this(
                productMasterPath,
                orderPaths,
                outputPath,
                inspectionOutputPath,
                operatingDate,
                osrLowWaterMark,
                inboundInterval,
                av02Capacity,
                outboundBagCapacity,
                maximumPacksPerBag,
                fixedStep,
                stepsPerBatch,
                metricSampleInterval,
                overwrite,
                progressLogPath,
                progressInterval,
                serviceCentreSchedulePath,
                stationProcessingOverrides,
                DspSchedulerPolicy.DEADLINE_AWARE_ELASTIC_STICKY_LEASES);
    }

    DspFullDayAnalysisCommand(
            Path productMasterPath,
            List<Path> orderPaths,
            Path outputPath,
            Optional<Path> inspectionOutputPath,
            LocalDate operatingDate,
            int osrLowWaterMark,
            Duration inboundInterval,
            int av02Capacity,
            int outboundBagCapacity,
            int maximumPacksPerBag,
            Duration fixedStep,
            int stepsPerBatch,
            Duration metricSampleInterval,
            boolean overwrite,
            Optional<Path> progressLogPath,
            Duration progressInterval,
            Optional<Path> serviceCentreSchedulePath) {
        this(productMasterPath, orderPaths, outputPath, inspectionOutputPath, operatingDate,
                osrLowWaterMark, inboundInterval, av02Capacity, outboundBagCapacity,
                maximumPacksPerBag, fixedStep, stepsPerBatch, metricSampleInterval, overwrite,
                progressLogPath, progressInterval, serviceCentreSchedulePath,
                DspFullDayStationProcessingOverrides.empty());
    }

    DspFullDayAnalysisCommand {
        if (productMasterPath == null || orderPaths == null || orderPaths.isEmpty()
                || outputPath == null || inspectionOutputPath == null || operatingDate == null
                || inboundInterval == null || fixedStep == null || metricSampleInterval == null
                || progressLogPath == null || progressInterval == null
                || serviceCentreSchedulePath == null || stationProcessingOverrides == null
                || schedulerPolicy == null) {
            throw new IllegalArgumentException("command values must not be null or empty");
        }
        if (osrLowWaterMark < 0 || av02Capacity < 1 || outboundBagCapacity < 1
                || maximumPacksPerBag < 1 || stepsPerBatch < 1
                || inboundInterval.isZero() || inboundInterval.isNegative()
                || fixedStep.isZero() || fixedStep.isNegative()
                || metricSampleInterval.isZero() || metricSampleInterval.isNegative()
                || progressInterval.isZero() || progressInterval.isNegative()) {
            throw new IllegalArgumentException("command numeric values must be positive or nonnegative");
        }
        orderPaths = List.copyOf(orderPaths);
    }

    DspFullDayInputPaths inputPaths() {
        return new DspFullDayInputPaths(productMasterPath, orderPaths);
    }
}
