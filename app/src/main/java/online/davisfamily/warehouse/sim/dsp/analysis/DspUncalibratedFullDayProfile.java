package online.davisfamily.warehouse.sim.dsp.analysis;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.time.FixedStepExecutionConfig;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageConfig;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocationConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadCostConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.schedule.DspOperationalSchedulingBaselineFactory;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.supply.FixedIntervalInboundToteArrivalPolicy;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplyConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;

/**
 * Complete, immutable assumptions for the first full-day analysis profile.
 *
 * <p>The values in this type describe an analytical execution model. They are deliberately not
 * production calibration data. In particular, the profile always identifies the deadline-aware
 * elastic policy as {@link #PROFILE_ID} and timing as {@code UNCALIBRATED}.</p>
 */
public record DspUncalibratedFullDayProfile(
        LocalDate operatingDate,
        online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig osrInventoryConfig,
        ServiceCentreSupplyConfig serviceCentreSupplyConfig,
        FixedIntervalInboundToteArrivalPolicy inboundToteArrivalPolicy,
        Av02AllocationConfig av02AllocationConfig,
        P2pElasticAllocationConfig p2pElasticAllocationConfig,
        OutboundToteConfig outboundToteConfig,
        int maximumPacksPerBag,
        Duration fixedStep,
        int maximumStepsPerAdvance,
        Duration metricSampleInterval,
        double routeSpeedUnitsPerSecond,
        QueueCapacities queueCapacities,
        ThirdPartyAreaConfig thirdPartyAreaConfig,
        AdaptingStorageConfig adaptingStorageConfig,
        List<AdaptingBenchDefinition> adaptingBenchDefinitions,
        P2pPlaceholderDurations p2pPlaceholderDurations,
        List<P2pLineDefinition> p2pLineDefinitions,
        int prlCountPerLine,
        DspServiceCentreTimetable timetable) {

    public static final String PROFILE_ID =
            P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES;
    public static final String TIMING_CALIBRATION_STATUS = "UNCALIBRATED";
    public static final String COMPLETION_MILESTONE = "P2P_OUTPUT_CLOSED";
    public static final String SERVICE_CENTRE_SUPPLY_POLICY_ID =
            "PRIORITY_ORDERED_OSR_LOW_WATERMARK";
    public static final String ORDER_ELIGIBILITY_POLICY_ID = "DEPENDENCY_READY_OVERLAP";
    public static final String CANDIDATE_RANKING_POLICY_ID =
            "PHARMACY_GROUPED_THEN_SOURCE_SEQUENCE";
    public static final String OUTBOUND_ALLOCATION_POLICY_ID =
            "PHARMACY_PURE_FIXED_BAG_CAPACITY";
    public static final int P2P_LINE_COUNT = 5;
    public static final int PRL_COUNT_PER_LINE = 31;

    public DspUncalibratedFullDayProfile {
        if (operatingDate == null) {
            throw new IllegalArgumentException("operatingDate must not be null");
        }
        if (osrInventoryConfig == null) {
            throw new IllegalArgumentException("osrInventoryConfig must not be null");
        }
        if (serviceCentreSupplyConfig == null) {
            throw new IllegalArgumentException("serviceCentreSupplyConfig must not be null");
        }
        if (inboundToteArrivalPolicy == null) {
            throw new IllegalArgumentException("inboundToteArrivalPolicy must not be null");
        }
        if (av02AllocationConfig == null) {
            throw new IllegalArgumentException("av02AllocationConfig must not be null");
        }
        if (p2pElasticAllocationConfig == null) {
            throw new IllegalArgumentException("p2pElasticAllocationConfig must not be null");
        }
        if (p2pElasticAllocationConfig.p2pLineCount() != P2P_LINE_COUNT) {
            throw new IllegalArgumentException("p2pElasticAllocationConfig must configure exactly five lines");
        }
        if (outboundToteConfig == null) {
            throw new IllegalArgumentException("outboundToteConfig must not be null");
        }
        if (maximumPacksPerBag < 1) {
            throw new IllegalArgumentException("maximumPacksPerBag must be positive");
        }
        requirePositiveDuration(fixedStep, "fixedStep");
        if (maximumStepsPerAdvance < 1) {
            throw new IllegalArgumentException("maximumStepsPerAdvance must be positive");
        }
        requirePositiveDuration(metricSampleInterval, "metricSampleInterval");
        if (!Double.isFinite(routeSpeedUnitsPerSecond) || routeSpeedUnitsPerSecond <= 0d) {
            throw new IllegalArgumentException("routeSpeedUnitsPerSecond must be finite and positive");
        }
        if (queueCapacities == null) {
            throw new IllegalArgumentException("queueCapacities must not be null");
        }
        if (thirdPartyAreaConfig == null) {
            throw new IllegalArgumentException("thirdPartyAreaConfig must not be null");
        }
        if (adaptingStorageConfig == null) {
            throw new IllegalArgumentException("adaptingStorageConfig must not be null");
        }
        if (adaptingBenchDefinitions == null || adaptingBenchDefinitions.isEmpty()) {
            throw new IllegalArgumentException("adaptingBenchDefinitions must not be empty");
        }
        Set<String> benchIds = new HashSet<>();
        for (AdaptingBenchDefinition definition : adaptingBenchDefinitions) {
            if (definition == null) {
                throw new IllegalArgumentException("adaptingBenchDefinitions must not contain null");
            }
            if (!benchIds.add(definition.id())) {
                throw new IllegalArgumentException("adapting bench IDs must be distinct: " + definition.id());
            }
        }
        if (p2pPlaceholderDurations == null) {
            throw new IllegalArgumentException("p2pPlaceholderDurations must not be null");
        }
        if (p2pLineDefinitions == null || p2pLineDefinitions.size() != P2P_LINE_COUNT) {
            throw new IllegalArgumentException("Exactly five P2P line definitions are required");
        }
        Set<P2pLineId> lineIds = new HashSet<>();
        Set<OperationalRouteDestination> destinations = new HashSet<>();
        for (P2pLineDefinition definition : p2pLineDefinitions) {
            if (definition == null) {
                throw new IllegalArgumentException("p2pLineDefinitions must not contain null");
            }
            if (!lineIds.add(definition.lineId())) {
                throw new IllegalArgumentException("P2P line IDs must be distinct");
            }
            if (!destinations.add(definition.destination())) {
                throw new IllegalArgumentException("P2P destinations must be distinct");
            }
        }
        if (prlCountPerLine != PRL_COUNT_PER_LINE) {
            throw new IllegalArgumentException("Each P2P line must configure exactly 31 PRLs");
        }
        if (timetable == null) {
            throw new IllegalArgumentException("timetable must not be null");
        }
        osrInventoryConfig = osrInventoryConfig;
        adaptingBenchDefinitions = List.copyOf(adaptingBenchDefinitions);
        p2pLineDefinitions = List.copyOf(p2pLineDefinitions);
    }

    /**
     * Creates the first profile with all unknown operational values supplied explicitly.
     * Known analytical placeholders use stable, documented defaults.
     */
    public static DspUncalibratedFullDayProfile productionBaseline(
            LocalDate operatingDate,
            int osrLowWaterMark,
            Duration inboundToteInterval,
            int av02Capacity,
            int outboundBagCapacity,
            int maximumPacksPerBag) {
        if (operatingDate == null) {
            throw new IllegalArgumentException("operatingDate must not be null");
        }
        if (inboundToteInterval == null) {
            throw new IllegalArgumentException("inboundToteInterval must not be null");
        }
        return uncalibrated(
                operatingDate,
                new online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig(
                        1200, List.of("104", "108")),
                new ServiceCentreSupplyConfig(osrLowWaterMark),
                new FixedIntervalInboundToteArrivalPolicy(
                        "FIXED_CONFIGURED_INTERVAL", inboundToteInterval),
                new Av02AllocationConfig(av02Capacity),
                new P2pElasticAllocationConfig(
                        P2P_LINE_COUNT,
                        2,
                        1,
                        1000,
                        1000,
                        Duration.ofHours(1),
                        new P2pWorkloadCostConfig(
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(1))),
                new OutboundToteConfig(outboundBagCapacity),
                maximumPacksPerBag);
    }

    public static DspUncalibratedFullDayProfile productionBaseline(
            LocalDate operatingDate,
            int osrLowWaterMark,
            int inboundToteIntervalSeconds,
            int av02Capacity,
            int outboundBagCapacity,
            int maximumPacksPerBag) {
        if (inboundToteIntervalSeconds <= 0) {
            throw new IllegalArgumentException("inboundToteIntervalSeconds must be positive");
        }
        return productionBaseline(
                operatingDate,
                osrLowWaterMark,
                Duration.ofSeconds(inboundToteIntervalSeconds),
                av02Capacity,
                outboundBagCapacity,
                maximumPacksPerBag);
    }

    /** Alias that makes the calibration status explicit at call sites. */
    public static DspUncalibratedFullDayProfile uncalibrated(
            LocalDate operatingDate,
            int osrLowWaterMark,
            Duration inboundToteInterval,
            int av02Capacity,
            int outboundBagCapacity,
            int maximumPacksPerBag) {
        return productionBaseline(
                operatingDate,
                osrLowWaterMark,
                inboundToteInterval,
                av02Capacity,
                outboundBagCapacity,
                maximumPacksPerBag);
    }

    /** Builds a profile from caller-owned domain configs while retaining fixed profile defaults. */
    public static DspUncalibratedFullDayProfile uncalibrated(
            LocalDate operatingDate,
            online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig osrInventoryConfig,
            ServiceCentreSupplyConfig serviceCentreSupplyConfig,
            FixedIntervalInboundToteArrivalPolicy inboundToteArrivalPolicy,
            Av02AllocationConfig av02AllocationConfig,
            P2pElasticAllocationConfig p2pElasticAllocationConfig,
            OutboundToteConfig outboundToteConfig,
            int maximumPacksPerBag) {
        return new DspUncalibratedFullDayProfile(
                operatingDate,
                osrInventoryConfig,
                serviceCentreSupplyConfig,
                inboundToteArrivalPolicy,
                av02AllocationConfig,
                p2pElasticAllocationConfig,
                outboundToteConfig,
                maximumPacksPerBag,
                Duration.ofMillis(50),
                2_000,
                Duration.ofSeconds(60),
                1d,
                QueueCapacities.defaults(),
                new ThirdPartyAreaConfig(16, 1, 60d),
                AdaptingStorageConfig.defaults(),
                List.of(new AdaptingBenchDefinition("adapting-bench-1", 60d)),
                P2pPlaceholderDurations.defaults(),
                defaultP2pLineDefinitions(),
                PRL_COUNT_PER_LINE,
                DspOperationalSchedulingBaselineFactory.createProductionTimetable());
    }

    public String profileId() {
        return PROFILE_ID;
    }

    public String timingCalibrationStatus() {
        return TIMING_CALIBRATION_STATUS;
    }

    public String calibrationStatus() {
        return TIMING_CALIBRATION_STATUS;
    }

    public String completionMilestone() {
        return COMPLETION_MILESTONE;
    }

    public String serviceCentreSupplyPolicyId() {
        return SERVICE_CENTRE_SUPPLY_POLICY_ID;
    }

    public String orderEligibilityPolicyId() {
        return ORDER_ELIGIBILITY_POLICY_ID;
    }

    public String candidateRankingPolicyId() {
        return CANDIDATE_RANKING_POLICY_ID;
    }

    public String p2pLineAllocationPolicyId() {
        return PROFILE_ID;
    }

    public String outboundAllocationPolicyId() {
        return OUTBOUND_ALLOCATION_POLICY_ID;
    }

    public DspOperationalClockConfig operationalClockConfig() {
        return DspOperationalClockConfig.productionBaseline(operatingDate);
    }

    public DspOperationalClockConfig clockConfig() {
        return operationalClockConfig();
    }

    public List<P2pLineDefinition> p2pLines() {
        return p2pLineDefinitions;
    }

    public int p2pLineCount() {
        return p2pLineDefinitions.size();
    }

    public int prlsPerLine() {
        return prlCountPerLine;
    }

    public double routeSpeed() {
        return routeSpeedUnitsPerSecond;
    }

    private static List<P2pLineDefinition> defaultP2pLineDefinitions() {
        List<P2pLineDefinition> definitions = new ArrayList<>();
        for (int index = 1; index <= P2P_LINE_COUNT; index++) {
            String id = "dsp-p2p-line-" + index;
            definitions.add(new P2pLineDefinition(
                    new P2pLineId(id),
                    new OperationalRouteDestination(StationType.P2P, id)));
        }
        return List.copyOf(definitions);
    }

    private static void requirePositiveDuration(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        try {
            if (value.toNanos() <= 0) {
                throw new IllegalArgumentException(fieldName + " must convert to positive nanoseconds");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " must fit in nanoseconds", exception);
        }
    }

    public record QueueCapacities(
            int warehouseTransportCapacity,
            int warehouseInFlightCapacity,
            int stationArrivalQueueCapacity,
            int tipperInputQueueCapacity,
            int adaptingQueueCapacityPerBench) {

        public QueueCapacities {
            requireNonNegative(warehouseTransportCapacity, "warehouseTransportCapacity");
            requireNonNegative(warehouseInFlightCapacity, "warehouseInFlightCapacity");
            requireNonNegative(stationArrivalQueueCapacity, "stationArrivalQueueCapacity");
            requireNonNegative(tipperInputQueueCapacity, "tipperInputQueueCapacity");
            requireNonNegative(adaptingQueueCapacityPerBench, "adaptingQueueCapacityPerBench");
        }

        public static QueueCapacities defaults() {
            return new QueueCapacities(64, 64, 4, 4, 4);
        }

        public int transportCapacity() {
            return warehouseTransportCapacity;
        }

        public int inFlightCapacity() {
            return warehouseInFlightCapacity;
        }

        public int stationQueueCapacity() {
            return stationArrivalQueueCapacity;
        }

        private static void requireNonNegative(int value, String fieldName) {
            if (value < 0) {
                throw new IllegalArgumentException(fieldName + " must be >= 0");
            }
        }
    }

    public record AdaptingBenchDefinition(String id, double processingDurationSeconds) {
        public AdaptingBenchDefinition {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            id = id.trim();
            if (!Double.isFinite(processingDurationSeconds) || processingDurationSeconds < 0d) {
                throw new IllegalArgumentException(
                        "processingDurationSeconds must be finite and >= 0");
            }
        }

        public AdaptingBenchId benchId() {
            return new AdaptingBenchId(id);
        }
    }

    public record P2pPlaceholderDurations(
            double tippingDurationSeconds,
            double tipperEmitIntervalSeconds,
            double tipperResetDurationSeconds,
            double tipperDischargeDurationSeconds,
            double sorterReleaseIntervalSeconds,
            double pdcTransferDurationSeconds,
            double prlToPcrTransferDurationSeconds,
            double bagReceivingDurationSeconds,
            double bagDroppingDurationSeconds,
            double bagSealingDurationSeconds,
            double bagDischargingDurationSeconds) {

        public P2pPlaceholderDurations {
            requireDuration(tippingDurationSeconds, "tippingDurationSeconds");
            requireDuration(tipperEmitIntervalSeconds, "tipperEmitIntervalSeconds");
            requireDuration(tipperResetDurationSeconds, "tipperResetDurationSeconds");
            requireDuration(tipperDischargeDurationSeconds, "tipperDischargeDurationSeconds");
            requireDuration(sorterReleaseIntervalSeconds, "sorterReleaseIntervalSeconds");
            requireDuration(pdcTransferDurationSeconds, "pdcTransferDurationSeconds");
            requireDuration(prlToPcrTransferDurationSeconds, "prlToPcrTransferDurationSeconds");
            requireDuration(bagReceivingDurationSeconds, "bagReceivingDurationSeconds");
            requireDuration(bagDroppingDurationSeconds, "bagDroppingDurationSeconds");
            requireDuration(bagSealingDurationSeconds, "bagSealingDurationSeconds");
            requireDuration(bagDischargingDurationSeconds, "bagDischargingDurationSeconds");
        }

        public static P2pPlaceholderDurations defaults() {
            return new P2pPlaceholderDurations(
                    1d,
                    0.1d,
                    1d,
                    1d,
                    0.1d,
                    0.1d,
                    0.1d,
                    0.1d,
                    0.1d,
                    0.1d,
                    0.1d);
        }

        private static void requireDuration(double value, String fieldName) {
            if (!Double.isFinite(value) || value < 0d) {
                throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
            }
        }
    }
}
