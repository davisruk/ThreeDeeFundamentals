package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageConfig;
import online.davisfamily.warehouse.sim.dsp.analysis.DspCompletionMilestone;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayTerminationReason;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.AdaptingBenchDefinition;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.P2pPlaceholderDurations;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile.QueueCapacities;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspP2pLineMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspServiceCentreMetricsSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationConfig;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pWorkloadCostConfig;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

/** Builds a stable report from immutable terminal runtime values. */
public final class DspFullDayReportFactory {
    private final DspUncalibratedFullDayProfile configuredProfile;
    private final DspFullDayLoadedInput configuredInput;

    public DspFullDayReportFactory() {
        configuredProfile = null;
        configuredInput = null;
    }

    public DspFullDayReportFactory(
            DspUncalibratedFullDayProfile profile,
            DspFullDayLoadedInput input) {
        requireNonNull(profile, "profile");
        requireNonNull(input, "input");
        configuredProfile = profile;
        configuredInput = input;
    }

    public DspFullDayReportFactory(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        this(profile, input);
    }

    public DspFullDayAnalysisReport create(DspFullDayAnalysisRuntimeSnapshot runtime) {
        if (configuredProfile == null || configuredInput == null) {
            throw new IllegalStateException(
                    "this factory was not constructed with profile and input values");
        }
        return create(runtime, configuredInput, configuredProfile);
    }

    public DspFullDayAnalysisReport create(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        requireNonNull(runtime, "runtime");
        requireNonNull(input, "input");
        requireNonNull(profile, "profile");

        DspFullDayMetricsSnapshot metrics = runtime.metrics();
        if (runtime.state() == DspFullDayRuntimeState.RUNNING) {
            throw new IllegalArgumentException("a final report requires a terminal runtime state");
        }
        if (!profile.profileId().equals(metrics.profileId())
                || !profile.calibrationStatus().equals(metrics.calibrationStatus())
                || !profile.completionMilestone().equals(metrics.completionMilestone())) {
            throw new IllegalArgumentException(
                    "profile identity must match the final runtime metrics");
        }

        Map<String, DspServiceCentreCompletionSnapshot> completionsById = runtime.completions()
                .stream()
                .collect(Collectors.toMap(
                        DspServiceCentreCompletionSnapshot::serviceCentreId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "duplicate completion snapshot for "
                                            + first.serviceCentreId());
                        },
                        LinkedHashMap::new));
        Map<String, DspServiceCentreMetricsSnapshot> metricsById = metrics.serviceCentres()
                .stream()
                .collect(Collectors.toMap(
                        DspServiceCentreMetricsSnapshot::serviceCentreId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "duplicate metric snapshot for " + first.serviceCentreId());
                        },
                        LinkedHashMap::new));

        List<ServiceCentreSchedule> timetableOrder = new ArrayList<>(
                input.timetable().serviceCentres());
        Comparator<ServiceCentreSchedule> timetableComparator = Comparator
                .comparingInt(ServiceCentreSchedule::priority)
                .reversed()
                .thenComparing(ServiceCentreSchedule::serviceCentreId);
        timetableOrder.sort(timetableComparator);

        Map<String, Integer> timetableRank = new LinkedHashMap<>();
        Map<String, Integer> timetablePriority = new LinkedHashMap<>();
        for (int index = 0; index < timetableOrder.size(); index++) {
            ServiceCentreSchedule schedule = timetableOrder.get(index);
            timetableRank.put(schedule.serviceCentreId(), index);
            timetablePriority.put(schedule.serviceCentreId(), schedule.priority());
        }

        List<String> metricIds = metricsById.keySet().stream().toList();
        for (String id : metricIds) {
            if (!timetableRank.containsKey(id)) {
                throw new IllegalArgumentException(
                        "metric service centre is absent from timetable: " + id);
            }
            if (!timetablePriority.get(id).equals(metricsById.get(id).priority())) {
                throw new IllegalArgumentException(
                        "metric priority does not match timetable for " + id);
            }
            if (!completionsById.containsKey(id)) {
                throw new IllegalArgumentException(
                        "completion snapshot is absent for service centre: " + id);
            }
        }

        List<DspServiceCentreAnalysisResult> serviceCentres = new ArrayList<>();
        for (String id : metricIds) {
            DspServiceCentreMetricsSnapshot centreMetrics = metricsById.get(id);
            DspServiceCentreCompletionSnapshot completion = completionsById.get(id);
            if (centreMetrics.complete() != completion.complete()
                    || centreMetrics.completionOutcome() != completion.outcome()) {
                throw new IllegalArgumentException(
                        "completion and metrics facts do not match for " + id);
            }
            serviceCentres.add(new DspServiceCentreAnalysisResult(
                    id,
                    centreMetrics,
                    completion,
                    unfinishedIdentitiesFor(id, runtime, input.data(), completion)));
        }
        serviceCentres.sort(Comparator
                .comparingInt((DspServiceCentreAnalysisResult value)
                        -> timetableRank.get(value.serviceCentreId()))
                .thenComparing(DspServiceCentreAnalysisResult::serviceCentreId));

        Map<P2pLineId, Integer> configuredLineRank = new LinkedHashMap<>();
        for (int index = 0; index < profile.p2pLines().size(); index++) {
            configuredLineRank.put(profile.p2pLines().get(index).lineId(), index);
        }
        List<DspP2pLineMetricsSnapshot> p2pLines = new ArrayList<>(metrics.p2pLines());
        Set<P2pLineId> metricLineIds = new LinkedHashSet<>();
        for (DspP2pLineMetricsSnapshot line : p2pLines) {
            if (!configuredLineRank.containsKey(line.lineId())) {
                throw new IllegalArgumentException(
                        "metric P2P line is absent from profile: " + line.lineId().value());
            }
            if (!metricLineIds.add(line.lineId())) {
                throw new IllegalArgumentException(
                        "duplicate metric P2P line: " + line.lineId().value());
            }
        }
        p2pLines.sort(Comparator
                .comparingInt((DspP2pLineMetricsSnapshot value)
                        -> configuredLineRank.getOrDefault(value.lineId(), Integer.MAX_VALUE))
                .thenComparing(value -> value.lineId().value()));

        List<online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample>
                occupancySamples = new ArrayList<>(metrics.occupancySamples());
        occupancySamples.sort(Comparator
                .comparing(online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample::elapsedSimulationTime)
                .thenComparing(online.davisfamily.warehouse.sim.dsp.analysis.metrics.DspFullDayOccupancySample::businessDateTime));

        List<DspFullDayMetricsSnapshot.ElasticInfeasibilityEvent> infeasibilityHistory =
                List.copyOf(metrics.elasticInfeasibilityHistory());
        List<String> unfinishedIdentities = allUnfinishedIdentities(serviceCentres);
        List<String> unsupportedWork = stableDistinct(
                concat(metrics.unsupportedWork(), serviceCentres.stream()
                        .flatMap(value -> value.unsupportedWork().stream())
                        .toList()));
        List<String> warnings = warnings(
                input.report(),
                metrics,
                unsupportedWork,
                runtime.cutoff().diagnostic());

        return new DspFullDayAnalysisReport(
                metrics.profileId(),
                metrics.calibrationStatus(),
                DspCompletionMilestone.valueOf(metrics.completionMilestone()),
                terminationReason(runtime.state()),
                runtime.state(),
                metrics,
                runtime,
                configuration(profile),
                input.report(),
                serviceCentres,
                p2pLines,
                occupancySamples,
                infeasibilityHistory,
                warnings,
                unsupportedWork,
                unfinishedIdentities);
    }

    public DspFullDayAnalysisReport create(
            DspUncalibratedFullDayProfile profile,
            DspFullDayLoadedInput input,
            DspFullDayAnalysisRuntimeSnapshot runtime) {
        return create(runtime, input, profile);
    }

    public DspFullDayAnalysisReport create(
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile,
            DspFullDayAnalysisRuntimeSnapshot runtime) {
        return create(runtime, input, profile);
    }

    /**
     * Creates the immutable inspection boundary used while a run is still in progress.  A
     * terminal runtime is promoted to the final report shape so the same formatter can be used
     * for the last inspection without reading a mutable runtime.
     */
    public DspFullDayInspectionSnapshot createInspectionSnapshot(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            DspFullDayLoadedInput input,
            DspUncalibratedFullDayProfile profile) {
        requireNonNull(runtime, "runtime");
        requireNonNull(input, "input");
        requireNonNull(profile, "profile");
        if (!profile.profileId().equals(runtime.metrics().profileId())
                || !profile.calibrationStatus().equals(runtime.metrics().calibrationStatus())
                || !profile.completionMilestone().equals(runtime.metrics().completionMilestone())) {
            throw new IllegalArgumentException(
                    "profile identity must match the runtime metrics");
        }
        if (runtime.state() != DspFullDayRuntimeState.RUNNING) {
            return new DspFullDayInspectionSnapshot(create(runtime, input, profile));
        }

        List<DspServiceCentreAnalysisResult> serviceCentres = currentServiceCentreResults(
                runtime,
                input);
        List<String> unsupportedWork = stableDistinct(
                concat(runtime.metrics().unsupportedWork(), serviceCentres.stream()
                        .flatMap(value -> value.unsupportedWork().stream())
                        .toList()));
        List<String> unfinishedIdentities = allUnfinishedIdentities(serviceCentres);
        return new DspFullDayInspectionSnapshot(
                runtime,
                profile.profileId(),
                profile.calibrationStatus(),
                DspCompletionMilestone.valueOf(profile.completionMilestone()),
                input.report(),
                serviceCentres,
                unsupportedWork,
                unfinishedIdentities,
                java.util.Optional.empty());
    }

    public DspFullDayInspectionSnapshot createInspectionSnapshot(
            DspUncalibratedFullDayProfile profile,
            DspFullDayLoadedInput input,
            DspFullDayAnalysisRuntimeSnapshot runtime) {
        return createInspectionSnapshot(runtime, input, profile);
    }

    private List<DspServiceCentreAnalysisResult> currentServiceCentreResults(
            DspFullDayAnalysisRuntimeSnapshot runtime,
            DspFullDayLoadedInput input) {
        Map<String, DspServiceCentreCompletionSnapshot> completionsById = runtime.completions()
                .stream()
                .collect(Collectors.toMap(
                        DspServiceCentreCompletionSnapshot::serviceCentreId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "duplicate completion snapshot for "
                                            + first.serviceCentreId());
                        },
                        LinkedHashMap::new));
        Map<String, DspServiceCentreMetricsSnapshot> metricsById = runtime.metrics().serviceCentres()
                .stream()
                .collect(Collectors.toMap(
                        DspServiceCentreMetricsSnapshot::serviceCentreId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "duplicate metric snapshot for " + first.serviceCentreId());
                        },
                        LinkedHashMap::new));
        Map<String, Integer> timetableRank = new LinkedHashMap<>();
        Map<String, Integer> timetablePriority = new LinkedHashMap<>();
        List<ServiceCentreSchedule> schedules = new ArrayList<>(input.timetable().serviceCentres());
        schedules.sort(Comparator.comparingInt(ServiceCentreSchedule::priority)
                .reversed()
                .thenComparing(ServiceCentreSchedule::serviceCentreId));
        for (int index = 0; index < schedules.size(); index++) {
            ServiceCentreSchedule schedule = schedules.get(index);
            timetableRank.put(schedule.serviceCentreId(), index);
            timetablePriority.put(schedule.serviceCentreId(), schedule.priority());
        }

        List<DspServiceCentreAnalysisResult> result = new ArrayList<>();
        for (Map.Entry<String, DspServiceCentreMetricsSnapshot> entry : metricsById.entrySet()) {
            String id = entry.getKey();
            DspServiceCentreMetricsSnapshot metrics = entry.getValue();
            DspServiceCentreCompletionSnapshot completion = completionsById.get(id);
            if (completion == null || !timetableRank.containsKey(id)
                    || !timetablePriority.get(id).equals(metrics.priority())
                    || metrics.complete() != completion.complete()
                    || metrics.completionOutcome() != completion.outcome()) {
                throw new IllegalArgumentException(
                        "current service-centre snapshots do not agree for " + id);
            }
            result.add(new DspServiceCentreAnalysisResult(
                    id,
                    metrics,
                    completion,
                    unfinishedIdentitiesFor(id, runtime, input.data(), completion)));
        }
        result.sort(Comparator
                .comparingInt((DspServiceCentreAnalysisResult value)
                        -> timetableRank.get(value.serviceCentreId()))
                .thenComparing(DspServiceCentreAnalysisResult::serviceCentreId));
        return List.copyOf(result);
    }

    private static DspFullDayTerminationReason terminationReason(DspFullDayRuntimeState state) {
        return switch (state) {
            case ALL_SUPPORTED_WORK_COMPLETE -> DspFullDayTerminationReason.ALL_SUPPORTED_WORK_COMPLETE;
            case HARD_CUTOFF_REACHED -> DspFullDayTerminationReason.HARD_CUTOFF_REACHED;
            case RUNNING -> throw new IllegalArgumentException("running state has no termination reason");
        };
    }

    private static List<String> unfinishedIdentitiesFor(
            String serviceCentreId,
            DspFullDayAnalysisRuntimeSnapshot runtime,
            LoadedDspData data,
            DspServiceCentreCompletionSnapshot completion) {
        List<String> values = new ArrayList<>();
        for (DspSchedulerOrderState orderState : runtime.scheduler().orderStates()) {
            NotionalToteOrder order = orderState.order();
            if (serviceCentreId.equals(order.serviceCentreId())
                    && orderState.status() != DspOrderStatus.COMPLETED) {
                values.add("order=" + order.orderId()
                        + "/sheet=" + order.sheetNumber()
                        + ",status=" + orderState.status());
            }
        }

        Map<PhysicalToteId, String> serviceCentreByTote = new LinkedHashMap<>();
        for (InboundToteManifest manifest : data.inboundToteManifests()) {
            serviceCentreByTote.put(manifest.physicalToteId(), manifest.serviceCentreId());
        }
        for (Map.Entry<PhysicalToteId, PhysicalToteRecord> entry : runtime.lifecycle().totes().entrySet()) {
            if (!entry.getValue().terminal()
                    && serviceCentreId.equals(serviceCentreByTote.get(entry.getKey()))) {
                values.add("physical-tote=" + entry.getKey().value()
                        + ",state=" + entry.getValue().state());
            }
        }
        if (values.isEmpty() && !completion.complete()) {
            values.add("service-centre=" + serviceCentreId
                    + ",remaining-work=" + completion.remainingWork());
        }
        values.sort(String::compareTo);
        return List.copyOf(values);
    }

    private static List<String> allUnfinishedIdentities(
            List<DspServiceCentreAnalysisResult> serviceCentres) {
        List<String> values = new ArrayList<>();
        for (DspServiceCentreAnalysisResult serviceCentre : serviceCentres) {
            for (String identity : serviceCentre.unfinishedIdentities()) {
                values.add(serviceCentre.serviceCentreId() + ":" + identity);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> warnings(
            DspDatasetLoadReport loadReport,
            DspFullDayMetricsSnapshot metrics,
            List<String> unsupportedWork,
            String cutoffDiagnostic) {
        List<String> values = new ArrayList<>();
        values.add("Timing and throughput values are UNCALIBRATED analytical measurements");
        if (loadReport.ignoredManualMessageCount() > 0
                || loadReport.ignoredManualLineCount() > 0) {
            values.add("MANUAL input was excluded from the supported runtime");
        }
        if (loadReport.omittedOrderCount() > 0) {
            values.add("One or more input orders were omitted from the supported runtime");
        }
        for (UnresolvedProductLine issue : loadReport.unresolvedProductLines()) {
            values.add("Unresolved product " + issue.productId()
                    + " for " + issue.orderId() + "/" + issue.lineReference());
        }
        values.addAll(unsupportedWork);
        if (!cutoffDiagnostic.isBlank()) {
            values.add("Cutoff: " + cutoffDiagnostic);
        }
        if (metrics.state() == DspFullDayRuntimeState.HARD_CUTOFF_REACHED) {
            values.add("The terminal boundary is the hard cutoff; unfinished work is not fabricated as complete");
        }
        return stableDistinct(values);
    }

    private static Map<String, Object> configuration(DspUncalibratedFullDayProfile profile) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        DspOperationalClockConfig clock = profile.operationalClockConfig();
        result.put("profileId", profile.profileId());
        result.put("operatingDate", profile.operatingDate().toString());
        result.put("calibrationStatus", profile.calibrationStatus());
        result.put("completionMilestone", profile.completionMilestone());
        result.put("policies", linkedMap(
                "serviceCentreSupply", profile.serviceCentreSupplyPolicyId(),
                "orderEligibility", profile.orderEligibilityPolicyId(),
                "candidateRanking", profile.candidateRankingPolicyId(),
                "p2pLineAllocation", profile.p2pLineAllocationPolicyId(),
                "outboundAllocation", profile.outboundAllocationPolicyId(),
                "inboundArrival", profile.inboundToteArrivalPolicy().policyId()));
        result.put("clock", linkedMap(
                "normalStart", dayTime(clock.normalStart()),
                "normalEnd", dayTime(clock.normalEnd()),
                "hardCutoff", dayTime(clock.hardCutoff()),
                "normalStartDateTime", clock.normalStartDateTime().toString(),
                "normalEndDateTime", clock.normalEndDateTime().toString(),
                "hardCutoffDateTime", clock.hardCutoffDateTime().toString()));
        result.put("osr", linkedMap(
                "capacity", profile.osrInventoryConfig().capacity(),
                "lowWaterMark", profile.serviceCentreSupplyConfig().lowWaterMark(),
                "preloadServiceCentreIds", profile.osrInventoryConfig().preloadServiceCentreIds()));
        result.put("inbound", linkedMap(
                "interval", iso(profile.inboundToteArrivalPolicy().interval()),
                "intervalNanos", nanos(profile.inboundToteArrivalPolicy().interval()),
                "configuredTotesPerSecond", 1d / profile.inboundToteArrivalPolicy().interval().toNanos() * 1_000_000_000d));
        result.put("av02", linkedMap("capacity", profile.av02AllocationConfig().capacity()));
        result.put("outbound", linkedMap(
                "maximumBagCount", profile.outboundToteConfig().maximumBagCount(),
                "maximumPacksPerBag", profile.maximumPacksPerBag()));
        P2pElasticAllocationConfig elastic = profile.p2pElasticAllocationConfig();
        P2pWorkloadCostConfig costs = elastic.workloadCostConfig();
        result.put("p2pElastic", linkedMap(
                "p2pLineCount", elastic.p2pLineCount(),
                "maximumConcurrentServiceCentres", elastic.maximumConcurrentServiceCentres(),
                "minimumReservedLinesForEarlierCentre", elastic.minimumReservedLinesForEarlierCentre(),
                "safetyFactorPermille", elastic.safetyFactorPermille(),
                "parallelEfficiencyPermille", elastic.parallelEfficiencyPermille(),
                "downstreamHandlingDuration", iso(elastic.downstreamHandlingDuration()),
                "downstreamHandlingDurationNanos", nanos(elastic.downstreamHandlingDuration()),
                "workloadCosts", linkedMap(
                        "toteHandling", durationObject(costs.toteHandlingCost()),
                        "packProcessing", durationObject(costs.packProcessingCost()),
                        "bagging", durationObject(costs.baggingCost()))));
        result.put("execution", linkedMap(
                "fixedStep", durationObject(profile.fixedStep()),
                "maximumStepsPerAdvance", profile.maximumStepsPerAdvance(),
                "metricSampleInterval", durationObject(profile.metricSampleInterval()),
                "routeSpeedUnitsPerSecond", profile.routeSpeedUnitsPerSecond()));
        result.put("queues", queueCapacities(profile.queueCapacities()));
        result.put("thirdParty", linkedMap(
                "waitingCapacity", profile.thirdPartyAreaConfig().waitingCapacity(),
                "maxConcurrentVisits", profile.thirdPartyAreaConfig().maxConcurrentVisits(),
                "processingDurationSeconds", profile.thirdPartyAreaConfig().processingDurationSeconds()));
        AdaptingStorageConfig adapting = profile.adaptingStorageConfig();
        result.put("adapting", linkedMap(
                "linesPerBin", adapting.linesPerBin(),
                "binsPerShelf", adapting.binsPerShelf(),
                "shelvesPerRack", adapting.shelvesPerRack(),
                "benches", profile.adaptingBenchDefinitions().stream()
                        .map(DspFullDayReportFactory::bench)
                        .toList()));
        result.put("p2pPlaceholders", placeholders(profile.p2pPlaceholderDurations()));
        result.put("p2pLines", profile.p2pLines().stream().map(definition -> linkedMap(
                "lineId", definition.lineId().value(),
                "stationType", definition.destination().stationType().name(),
                "targetId", definition.destination().targetId())).toList());
        result.put("prlCountPerLine", profile.prlsPerLine());
        result.put("timetable", profile.timetable().serviceCentres().stream().map(schedule -> linkedMap(
                "serviceCentreId", schedule.serviceCentreId(),
                "displayName", schedule.displayName(),
                "priority", schedule.priority(),
                "trunkerDepartureTime", dayTime(schedule.trunkerDepartureTime()))).toList());
        return result;
    }

    private static Map<String, Object> queueCapacities(QueueCapacities value) {
        return linkedMap(
                "warehouseTransportCapacity", value.warehouseTransportCapacity(),
                "warehouseInFlightCapacity", value.warehouseInFlightCapacity(),
                "stationArrivalQueueCapacity", value.stationArrivalQueueCapacity(),
                "tipperInputQueueCapacity", value.tipperInputQueueCapacity(),
                "adaptingQueueCapacityPerBench", value.adaptingQueueCapacityPerBench());
    }

    private static Map<String, Object> bench(AdaptingBenchDefinition value) {
        return linkedMap("id", value.id(), "processingDurationSeconds", value.processingDurationSeconds());
    }

    private static Map<String, Object> placeholders(P2pPlaceholderDurations value) {
        return linkedMap(
                "tippingDurationSeconds", value.tippingDurationSeconds(),
                "tipperEmitIntervalSeconds", value.tipperEmitIntervalSeconds(),
                "tipperResetDurationSeconds", value.tipperResetDurationSeconds(),
                "tipperDischargeDurationSeconds", value.tipperDischargeDurationSeconds(),
                "sorterReleaseIntervalSeconds", value.sorterReleaseIntervalSeconds(),
                "pdcTransferDurationSeconds", value.pdcTransferDurationSeconds(),
                "prlToPcrTransferDurationSeconds", value.prlToPcrTransferDurationSeconds(),
                "bagReceivingDurationSeconds", value.bagReceivingDurationSeconds(),
                "bagDroppingDurationSeconds", value.bagDroppingDurationSeconds(),
                "bagSealingDurationSeconds", value.bagSealingDurationSeconds(),
                "bagDischargingDurationSeconds", value.bagDischargingDurationSeconds());
    }

    private static Map<String, Object> dayTime(OperationalDayTime value) {
        return linkedMap("dayOffset", value.dayOffset(), "localTime", value.localTime().toString());
    }

    private static Map<String, Object> durationObject(Duration value) {
        return linkedMap("nanos", nanos(value), "iso", iso(value));
    }

    private static long nanos(Duration value) {
        return value.toNanos();
    }

    private static String iso(Duration value) {
        return value.toString();
    }

    private static Map<String, Object> linkedMap(Object... values) {
        if ((values.length & 1) != 0) {
            throw new IllegalArgumentException("linkedMap requires key/value pairs");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static List<String> stableDistinct(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && seen.add(value.trim())) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
