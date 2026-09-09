package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspServiceCentreCompletionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssue;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationIssueType;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pServiceCentreLineDemandSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalBlockedCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlock;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportInFlightSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshot;

/**
 * Simulation-thread-owned accumulator for deterministic full-day analytical metrics.
 *
 * <p>The collector consumes immutable suppliers only. It is deliberately a controller so it can
 * be registered after all domain controllers and observe the completed state of each fixed step.</p>
 */
public final class DspFullDayMetricsCollector implements SimulationController {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger MAX_DURATION_NANOS = BigInteger.valueOf(Long.MAX_VALUE)
            .multiply(BILLION)
            .add(BigInteger.valueOf(999_999_999L));
    private static final BigDecimal NANOSECONDS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);
    private static final Comparator<DspServiceCentreCompletionSnapshot> CENTRE_ORDER =
            Comparator.comparingInt((DspServiceCentreCompletionSnapshot value) ->
                            value.deadline().priority())
                    .reversed()
                    .thenComparing(DspServiceCentreCompletionSnapshot::serviceCentreId);

    private final String profileId;
    private final String serviceCentreSupplyPolicyId;
    private final String orderEligibilityPolicyId;
    private final String candidateRankingPolicyId;
    private final String p2pLineAllocationPolicyId;
    private final String outboundAllocationPolicyId;
    private final String calibrationStatus;
    private final String completionMilestone;
    private final Duration configuredInboundInterval;
    private final Duration metricSampleInterval;
    private final int osrLowWaterMark;
    private final DspDatasetLoadReport loadReport;
    private final SnapshotSuppliers suppliers;

    private final List<DspFullDayOccupancySample> occupancySamples = new ArrayList<>();
    private final Map<String, EnumMap<DspFullDayBlockCategory, MutableBlockSummary>> blocksByCentre =
            new LinkedHashMap<>();
    private final Map<P2pLineId, Duration> busyDurationByLine = new LinkedHashMap<>();
    private final List<DspFullDayMetricsSnapshot.ElasticInfeasibilityEvent>
            elasticInfeasibilityHistory = new ArrayList<>();
    private final Map<String, String> lastElasticIssueSignatures = new LinkedHashMap<>();
    private final Set<String> previouslyCompletedCentres = new HashSet<>();

    private Duration observedSimulationDuration = Duration.ZERO;
    private Duration nextMetricSampleElapsedTime;
    private Duration lastClockElapsedTime;
    private long previousAdmittedInboundToteCount;
    private long previousDepartedInboundToteCount;
    private long previousClosedOutboundToteCount;
    private long previousAllocatedBagCount;
    private Duration capacityBlockedDuration = Duration.ZERO;
    private boolean normalEndSampled;
    private boolean hardCutoffSampled;
    private BigInteger occupancySum = BigInteger.ZERO;
    private long occupancySampleCount;
    private int minimumOsrOccupancy;
    private int maximumOsrOccupancy;
    private double requestedExecutionSpeed = 1d;
    private double achievedExecutionSpeed = 1d;

    public DspFullDayMetricsCollector(
            String profileId,
            String serviceCentreSupplyPolicyId,
            String orderEligibilityPolicyId,
            String candidateRankingPolicyId,
            String p2pLineAllocationPolicyId,
            String outboundAllocationPolicyId,
            String calibrationStatus,
            String completionMilestone,
            Duration configuredInboundInterval,
            Duration metricSampleInterval,
            int osrLowWaterMark,
            DspDatasetLoadReport loadReport,
            SnapshotSuppliers suppliers) {
        this.profileId = requireValue(profileId, "profileId");
        this.serviceCentreSupplyPolicyId = requireValue(
                serviceCentreSupplyPolicyId, "serviceCentreSupplyPolicyId");
        this.orderEligibilityPolicyId = requireValue(
                orderEligibilityPolicyId, "orderEligibilityPolicyId");
        this.candidateRankingPolicyId = requireValue(
                candidateRankingPolicyId, "candidateRankingPolicyId");
        this.p2pLineAllocationPolicyId = requireValue(
                p2pLineAllocationPolicyId, "p2pLineAllocationPolicyId");
        this.outboundAllocationPolicyId = requireValue(
                outboundAllocationPolicyId, "outboundAllocationPolicyId");
        this.calibrationStatus = requireValue(calibrationStatus, "calibrationStatus");
        this.completionMilestone = requireValue(completionMilestone, "completionMilestone");
        this.configuredInboundInterval = requirePositiveDuration(
                configuredInboundInterval, "configuredInboundInterval");
        this.metricSampleInterval = requirePositiveDuration(
                metricSampleInterval, "metricSampleInterval");
        if (osrLowWaterMark < 0) {
            throw new IllegalArgumentException("osrLowWaterMark must be nonnegative");
        }
        this.osrLowWaterMark = osrLowWaterMark;
        this.loadReport = Objects.requireNonNull(loadReport, "loadReport must not be null");
        this.suppliers = Objects.requireNonNull(suppliers, "suppliers must not be null");

        MetricInputs initial = readInputs();
        this.lastClockElapsedTime = initial.clock().elapsedSimulationTime();
        this.nextMetricSampleElapsedTime = metricSampleInterval;
        this.previousAdmittedInboundToteCount = initial.supply().admittedAfterStartupCount();
        this.previousDepartedInboundToteCount = initial.osr().departedTotes().size();
        this.previousClosedOutboundToteCount = initial.outbound().closedTotes().size();
        this.previousAllocatedBagCount = initial.outbound().allocatedBags().size();
        initializeCompletionState(initial);
        recordElasticIssues(initial, true);
        captureOccupancySample(initial);
    }

    /**
     * Records runner-owned execution speed values without changing any domain state. The
     * headless runtime uses the deterministic one-to-one default until the runner supplies its
     * measured real-time value in a later step.
     */
    public void recordExecutionSpeed(double requestedSpeed, double achievedSpeed) {
        requireNonnegativeFinite(requestedSpeed, "requestedSpeed");
        requireNonnegativeFinite(achievedSpeed, "achievedSpeed");
        requestedExecutionSpeed = requestedSpeed;
        achievedExecutionSpeed = achievedSpeed;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        requireNonnegativeFinite(dtSeconds, "dtSeconds");

        MetricInputs current = readInputs();
        Duration elapsed = current.clock().elapsedSimulationTime();
        if (elapsed.compareTo(lastClockElapsedTime) < 0) {
            throw new IllegalStateException("operational clock elapsed time moved backwards");
        }
        validateMonotonicCounts(current);

        Duration stepDuration = durationFromSeconds(dtSeconds);
        observedSimulationDuration = saturatingAdd(observedSimulationDuration, stepDuration);
        classifyAndAccumulate(current, stepDuration);
        accumulateLineBusyTime(current, stepDuration);
        if (hasCapacityBlockedSupply(current)) {
            capacityBlockedDuration = saturatingAdd(capacityBlockedDuration, stepDuration);
        }
        recordElasticIssues(current, false);

        boolean newCompletion = recordNewCompletions(current);
        boolean normalEnd = current.clock().normalEndReached() && !normalEndSampled;
        boolean hardCutoff = (current.clock().hardCutoffReached()
                || current.state() == DspFullDayRuntimeState.HARD_CUTOFF_REACHED)
                && !hardCutoffSampled;
        if (elapsed.compareTo(nextMetricSampleElapsedTime) >= 0) {
            captureOccupancySample(current);
            nextMetricSampleElapsedTime = saturatingAdd(elapsed, metricSampleInterval);
        }
        if (newCompletion || normalEnd || hardCutoff) {
            captureOccupancySample(current);
        }
        if (normalEnd) {
            normalEndSampled = true;
        }
        if (hardCutoff) {
            hardCutoffSampled = true;
        }

        lastClockElapsedTime = elapsed;
    }

    public DspFullDayMetricsSnapshot snapshot() {
        MetricInputs current = readInputs();
        return buildSnapshot(current);
    }

    public List<DspFullDayOccupancySample> occupancySamples() {
        return List.copyOf(occupancySamples);
    }

    public boolean isSimulationThreadOwned() {
        return true;
    }

    private DspFullDayMetricsSnapshot buildSnapshot(MetricInputs current) {
        OutboundCounts outboundCounts = outboundCounts(current.outbound());
        Duration elapsed = current.clock().elapsedSimulationTime();
        double elapsedSeconds = durationToSeconds(elapsed);
        EnumMap<DspFullDayBlockCategory, Duration> blockDurations =
                new EnumMap<>(DspFullDayBlockCategory.class);
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            blockDurations.put(category, totalBlockDuration(category));
        }

        List<DspServiceCentreMetricsSnapshot> serviceCentres = serviceCentreMetrics(current);
        LinkedHashSet<String> unsupportedWork = new LinkedHashSet<>();
        serviceCentres.forEach(metrics -> unsupportedWork.addAll(metrics.unsupportedWork()));

        return new DspFullDayMetricsSnapshot(
                profileId,
                serviceCentreSupplyPolicyId,
                orderEligibilityPolicyId,
                candidateRankingPolicyId,
                p2pLineAllocationPolicyId,
                outboundAllocationPolicyId,
                calibrationStatus,
                completionMilestone,
                current.state(),
                current.clock(),
                requestedExecutionSpeed,
                achievedExecutionSpeed,
                observedSimulationDuration,
                configuredInboundInterval,
                rate(1L, durationToSeconds(configuredInboundInterval)),
                current.supply().admittedAfterStartupCount(),
                rate(current.supply().admittedAfterStartupCount(), elapsedSeconds),
                current.osr().departedTotes().size(),
                signedDifference(
                        current.supply().admittedAfterStartupCount(),
                        current.osr().departedTotes().size()),
                capacityBlockedDuration,
                outboundCounts.closedToteCount(),
                rate(outboundCounts.closedToteCount(), elapsedSeconds),
                outboundCounts.allocatedBagCount(),
                rate(outboundCounts.allocatedBagCount(), elapsedSeconds),
                occupancySamples,
                minimumOsrOccupancy,
                maximumOsrOccupancy,
                occupancySampleCount == 0
                        ? 0d
                        : new BigDecimal(occupancySum)
                                .divide(BigDecimal.valueOf(occupancySampleCount), 12, RoundingMode.HALF_UP)
                                .doubleValue(),
                blockDurations,
                serviceCentres,
                lineMetrics(current),
                elasticInfeasibilityHistory,
                loadReport.ignoredManualMessageCount(),
                loadReport.ignoredManualLineCount(),
                loadReport.omittedOrderCount(),
                loadReport.unresolvedProductLines(),
                List.copyOf(unsupportedWork));
    }

    private List<DspServiceCentreMetricsSnapshot> serviceCentreMetrics(MetricInputs current) {
        Map<String, ServiceCentreSupplySnapshot> supplyById = current.supply().serviceCentres()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ServiceCentreSupplySnapshot::serviceCentreId,
                        value -> value,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        List<DspServiceCentreCompletionSnapshot> completions =
                current.completions().stream().sorted(CENTRE_ORDER).toList();
        List<DspServiceCentreMetricsSnapshot> result = new ArrayList<>(completions.size());
        for (DspServiceCentreCompletionSnapshot completion : completions) {
            String serviceCentreId = completion.serviceCentreId();
            ServiceCentreSupplySnapshot supply = supplyById.get(serviceCentreId);
            if (supply == null) {
                throw new IllegalStateException(
                        "completion has no matching supply snapshot: " + serviceCentreId);
            }
            P2pServiceCentreLineDemandSnapshot demand = current.elastic().allocation()
                    .find(serviceCentreId).orElse(null);
            List<P2pElasticAllocationIssue> serviceIssues = current.elastic().allocation().issues()
                    .stream()
                    .filter(issue -> issue.serviceCentreId().equals(serviceCentreId))
                    .toList();
            List<P2pElasticAllocationIssue> metricIssues = serviceIssues.isEmpty()
                    ? demand == null ? List.of() : demand.issues().stream()
                            .map(issue -> new P2pElasticAllocationIssue(
                                    serviceCentreId,
                                    issue,
                                    issue.name()))
                            .toList()
                    : serviceIssues;
            int unfinishedSheets = current.scheduler().orderStates().stream()
                    .filter(state -> state.order().serviceCentreId().equals(serviceCentreId))
                    .filter(state -> state.status() != DspOrderStatus.COMPLETED)
                    .map(state -> state.order().orderSheetKey())
                    .distinct()
                    .mapToInt(ignored -> 1)
                    .sum();
            Map<DspFullDayBlockCategory, DspServiceCentreMetricsSnapshot.BlockSummary> summaries =
                    blockSummaries(serviceCentreId);
            result.add(new DspServiceCentreMetricsSnapshot(
                    serviceCentreId,
                    supply.priority(),
                    supply.authorizationState(),
                    supply.authorizationElapsedTime(),
                    supply.upstreamWaitingCount(),
                    completion.deadline(),
                    completion.completionElapsedTime(),
                    completion.completionDateTime(),
                    completion.outcome(),
                    completion.complete(),
                    unfinishedSheets,
                    completion.remainingPhysicalToteCount(),
                    completion.remainingPhysicalPackCount(),
                    completion.remainingPlannedBagCount(),
                    lateness(completion, true),
                    lateness(completion, false),
                    demand == null ? 0 : demand.requiredLines(),
                    demand == null ? 0 : demand.desiredLines(),
                    demand == null ? 0 : demand.ownedLineCount(),
                    demand == null ? 0 : demand.unmetRequiredLines(),
                    (demand != null && demand.infeasible()) || !serviceIssues.isEmpty(),
                    metricIssues,
                    completion.unsupportedWork(),
                    summaries));
        }
        return List.copyOf(result);
    }

    private List<DspP2pLineMetricsSnapshot> lineMetrics(MetricInputs current) {
        Map<P2pLineId, DspHeadlessP2pLineRuntimeSnapshot> runtimeLines = current.p2pLines()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        line -> line.lineDefinition().lineId(),
                        line -> line,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<PhysicalToteId, P2pLineId> assignmentLines = new HashMap<>();
        for (P2pLineLeaseSnapshot line : current.elastic().leases().lines()) {
            for (var assignment : line.physicalAssignments()) {
                assignmentLines.put(assignment.physicalToteId(), line.definition().lineId());
            }
        }
        Map<PhysicalToteId, P2pLineId> outboundToteLines = new HashMap<>();
        current.outbound().openTotesByLine().forEach((lineId, tote) ->
                outboundToteLines.put(tote.physicalToteId(), lineId));
        current.outbound().closedTotes().forEach(tote ->
                outboundToteLines.put(tote.physicalToteId(), tote.p2pLineId()));

        List<DspP2pLineMetricsSnapshot> result = new ArrayList<>();
        for (P2pLineLeaseSnapshot lease : current.elastic().leases().lines()) {
            P2pLineId lineId = lease.definition().lineId();
            DspHeadlessP2pLineRuntimeSnapshot runtimeLine = runtimeLines.get(lineId);
            var activity = runtimeLine == null ? lease.activity() : runtimeLine.activity();
            P2pServiceCentreLineDemandSnapshot demand = lease.serviceCentreId()
                    .flatMap(current.elastic().allocation()::find)
                    .orElse(null);
            long consumedTotes = lease.physicalAssignments().stream()
                    .filter(assignment -> current.lifecycle().totes()
                            .get(assignment.physicalToteId()) != null)
                    .filter(assignment -> current.lifecycle().totes()
                            .get(assignment.physicalToteId()).state()
                                    == PhysicalToteLifecycleState.CONSUMED_AT_P2P)
                    .count();
            long allocatedBags = current.outbound().allocatedBags().stream()
                    .filter(bag -> lineId.equals(outboundToteLines.get(bag.outboundPhysicalToteId())))
                    .count();
            long closedTotes = current.outbound().closedTotes().stream()
                    .filter(tote -> lineId.equals(tote.p2pLineId()))
                    .count();
            Duration busy = busyDurationByLine.getOrDefault(lineId, Duration.ZERO);
            double utilization = observedSimulationDuration.isZero()
                    ? 0d
                    : boundedRatio(
                            durationToNanosForRatio(busy),
                            durationToNanosForRatio(observedSimulationDuration));
            result.add(new DspP2pLineMetricsSnapshot(
                    lineId,
                    lease.serviceCentreId(),
                    demand != null && demand.feedingOwnedLineIds().contains(lineId),
                    demand != null && demand.drainingSurplusLineIds().contains(lineId),
                    activity,
                    observedSimulationDuration,
                    busy,
                    utilization,
                    consumedTotes,
                    rate(consumedTotes, durationToSeconds(observedSimulationDuration)),
                    allocatedBags,
                    rate(allocatedBags, durationToSeconds(observedSimulationDuration)),
                    closedTotes,
                    rate(closedTotes, durationToSeconds(observedSimulationDuration))));
        }
        return List.copyOf(result);
    }

    private Optional<Duration> lateness(
            DspServiceCentreCompletionSnapshot completion,
            boolean target) {
        return completion.completionDateTime().map(completedAt -> Duration.between(
                target ? completion.deadline().targetCompletion()
                        : completion.deadline().latestAllowedCompletion(),
                completedAt));
    }

    private Map<DspFullDayBlockCategory, DspServiceCentreMetricsSnapshot.BlockSummary>
            blockSummaries(String serviceCentreId) {
        EnumMap<DspFullDayBlockCategory, DspServiceCentreMetricsSnapshot.BlockSummary> result =
                new EnumMap<>(DspFullDayBlockCategory.class);
        EnumMap<DspFullDayBlockCategory, MutableBlockSummary> values = blocksByCentre.get(
                serviceCentreId);
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            MutableBlockSummary value = values == null ? null : values.get(category);
            result.put(category, value == null ? DspServiceCentreMetricsSnapshot.BlockSummary
                    .zero() : value.snapshot());
        }
        return Collections.unmodifiableMap(result);
    }

    private void classifyAndAccumulate(MetricInputs current, Duration stepDuration) {
        Map<String, OperationalBlockCounts> operationalBlocks = operationalBlocks(current);
        Map<String, String> physicalToteOwners = physicalToteOwners(current);
        for (DspServiceCentreCompletionSnapshot completion : current.completions()) {
            Optional<BlockObservation> blocked = classify(
                    completion,
                    current,
                    operationalBlocks.get(completion.serviceCentreId()),
                    physicalToteOwners);
            blocked.ifPresent(observation -> blocksByCentre
                    .computeIfAbsent(completion.serviceCentreId(), ignored -> emptyBlockMap())
                    .get(observation.category())
                    .add(observation.count(), stepDuration, observation.reason()));
        }
    }

    private Optional<BlockObservation> classify(
            DspServiceCentreCompletionSnapshot completion,
            MetricInputs current,
            OperationalBlockCounts operational,
            Map<String, String> physicalToteOwners) {
        if (!completion.unsupportedWork().isEmpty()) {
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.UNSUPPORTED_WORK,
                    completion.unsupportedWork().size(),
                    completion.unsupportedWork().getFirst()));
        }
        if (operational != null && operational.dependencyCount() > 0) {
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.DEPENDENCY,
                    operational.dependencyCount(),
                    operational.dependencyReason()));
        }
        ServiceCentreSupplySnapshot supply = current.supply().serviceCentres().stream()
                .filter(value -> value.serviceCentreId().equals(completion.serviceCentreId()))
                .findFirst()
                .orElse(null);
        long osrCount = supply == null ? 0 : supply.physicalTotes().stream()
                .filter(tote -> tote.state() == PhysicalToteSupplyState.HELD_UPSTREAM
                        || tote.state() == PhysicalToteSupplyState.AUTHORIZED_WAITING
                        || tote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY)
                .count();
        osrCount = saturatingAdd(osrCount, completion.capacityBlockedManifestCount());
        osrCount = saturatingAdd(osrCount, completion.osrWaitingCount());
        osrCount = saturatingAdd(osrCount, completion.av02WaitingCount());
        if (completion.upstreamWaitingCount() > 0) {
            osrCount = Math.max(osrCount, completion.upstreamWaitingCount());
        }
        if (osrCount > 0) {
            String reason = supply == null
                    ? "OSR/AV02 work is waiting"
                    : supply.physicalTotes().stream()
                            .filter(tote -> tote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY
                                    || tote.state() == PhysicalToteSupplyState.HELD_UPSTREAM
                                    || tote.state() == PhysicalToteSupplyState.AUTHORIZED_WAITING)
                            .map(tote -> tote.state().name())
                            .findFirst()
                            .orElse("OSR/AV02 work is waiting");
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.OSR_STATE, osrCount, reason));
        }
        if (operational != null && operational.stationCount() > 0) {
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.STATION_CAPACITY,
                    operational.stationCount(),
                    operational.stationReason()));
        }
        long physicalStationBlocks = physicalStationBlocks(
                completion.serviceCentreId(), current, physicalToteOwners);
        if (physicalStationBlocks > 0) {
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.STATION_CAPACITY,
                    physicalStationBlocks,
                    "station or transport admission is blocked"));
        }
        if (operational != null && operational.p2pCount() > 0) {
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.P2P_ASSIGNMENT,
                    operational.p2pCount(),
                    operational.p2pReason()));
        }
        P2pServiceCentreLineDemandSnapshot demand = current.elastic().allocation()
                .find(completion.serviceCentreId()).orElse(null);
        if (demand != null && (demand.unmetRequiredLines() > 0 || demand.infeasible())) {
            String reason = demand.issues().isEmpty()
                    ? "elastic P2P line demand is unmet"
                    : demand.issues().getFirst().name();
            long count = Math.max(1L, Math.max(
                    demand.unmetRequiredLines(), demand.issues().size()));
            return Optional.of(new BlockObservation(
                    DspFullDayBlockCategory.P2P_ASSIGNMENT, count, reason));
        }
        return Optional.empty();
    }

    private long physicalStationBlocks(
            String serviceCentreId,
            MetricInputs current,
            Map<String, String> physicalToteOwners) {
        Set<PhysicalToteId> blocked = new LinkedHashSet<>();
        current.transportIngress().blockedPhysicalToteId().ifPresent(blocked::add);
        current.transportArrival().blockedPhysicalToteId().ifPresent(blocked::add);
        current.stationClaims().stream()
                .map(StationArrivalClaimControllerSnapshot::blockedPhysicalToteId)
                .flatMap(Optional::stream)
                .forEach(blocked::add);
        current.stationArrivals().stream()
                .filter(queue -> queue.capacity() > 0 && queue.entries().size() >= queue.capacity())
                .flatMap(queue -> queue.entries().stream())
                .map(StationRoutedToteArrivalQueueSnapshot.Entry::physicalToteId)
                .forEach(blocked::add);
        return blocked.stream()
                .filter(id -> serviceCentreId.equals(physicalToteOwners.get(id.value())))
                .count();
    }

    private Map<String, OperationalBlockCounts> operationalBlocks(MetricInputs current) {
        Map<String, String> serviceBySheet = new HashMap<>();
        for (DspSchedulerOrderState state : current.scheduler().orderStates()) {
            serviceBySheet.put(state.order().orderSheetKey().toString(), state.order().serviceCentreId());
        }
        Map<String, OperationalBlockCounts> result = new LinkedHashMap<>();
        Optional<DspOperationalReleaseEvaluation> evaluation = current.operationalRelease()
                .lastEvaluation();
        if (evaluation.isEmpty()) {
            return result;
        }
        for (OperationalBlockedCandidate candidate : evaluation.orElseThrow().blockedCandidates()) {
            String serviceCentreId = serviceBySheet.get(candidate.orderSheetKey().toString());
            if (serviceCentreId == null) {
                continue;
            }
            OperationalBlockCounts counts = result.computeIfAbsent(
                    serviceCentreId, ignored -> new OperationalBlockCounts());
            Set<OperationalReleaseBlockType> candidateBlockTypes = new HashSet<>();
            for (OperationalReleaseBlock block : candidate.blocks()) {
                if (candidateBlockTypes.add(block.type())) {
                    counts.add(block);
                }
            }
        }
        return result;
    }

    private Map<String, String> physicalToteOwners(MetricInputs current) {
        Map<String, String> result = new HashMap<>();
        for (ServiceCentreSupplySnapshot centre : current.supply().serviceCentres()) {
            for (PhysicalToteSupplySnapshot tote : centre.physicalTotes()) {
                result.put(tote.physicalToteId().value(), centre.serviceCentreId());
            }
        }
        for (InboundToteManifest manifest : current.osr().storedTotes()) {
            result.put(manifest.physicalToteId().value(), manifest.serviceCentreId());
        }
        for (InboundToteManifest manifest : current.osr().departedTotes()) {
            result.put(manifest.physicalToteId().value(), manifest.serviceCentreId());
        }
        for (Av02AllocatedTote tote : current.av02().waitingTotes()) {
            result.put(tote.physicalToteId().value(), tote.serviceCentreId());
        }
        for (Av02AllocatedTote tote : current.av02().departedTotes()) {
            result.put(tote.physicalToteId().value(), tote.serviceCentreId());
        }
        Map<OrderSheetKey, String> serviceBySheet = new HashMap<>();
        for (DspSchedulerOrderState state : current.scheduler().orderStates()) {
            serviceBySheet.put(state.order().orderSheetKey(), state.order().serviceCentreId());
        }
        for (var assignment : current.lifecycle().assignments()) {
            String serviceCentreId = serviceBySheet.get(assignment.orderSheetKey());
            if (serviceCentreId != null) {
                if (assignment.active()) {
                    result.put(assignment.physicalToteId().value(), serviceCentreId);
                } else {
                    result.putIfAbsent(assignment.physicalToteId().value(), serviceCentreId);
                }
            }
        }
        current.outbound().openTotesByLine().values().forEach(tote ->
                tote.serviceCentreId().ifPresent(serviceCentreId ->
                        result.put(tote.physicalToteId().value(), serviceCentreId)));
        current.outbound().closedTotes().forEach(tote ->
                tote.serviceCentreId().ifPresent(serviceCentreId ->
                        result.put(tote.physicalToteId().value(), serviceCentreId)));
        return result;
    }

    private void accumulateLineBusyTime(MetricInputs current, Duration stepDuration) {
        for (P2pLineLeaseSnapshot line : current.elastic().leases().lines()) {
            if (!line.activity().quiescent()) {
                P2pLineId lineId = line.definition().lineId();
                busyDurationByLine.put(
                        lineId,
                        saturatingAdd(
                                busyDurationByLine.getOrDefault(lineId, Duration.ZERO),
                                stepDuration));
            }
        }
    }

    private boolean recordNewCompletions(MetricInputs current) {
        boolean changed = false;
        for (DspServiceCentreCompletionSnapshot completion : current.completions()) {
            if (completion.complete() && previouslyCompletedCentres.add(
                    completion.serviceCentreId())) {
                changed = true;
            }
        }
        return changed;
    }

    private void initializeCompletionState(MetricInputs initial) {
        initial.completions().stream()
                .filter(DspServiceCentreCompletionSnapshot::complete)
                .map(DspServiceCentreCompletionSnapshot::serviceCentreId)
                .forEach(previouslyCompletedCentres::add);
    }

    private void recordElasticIssues(MetricInputs current, boolean initial) {
        Map<String, List<P2pElasticAllocationIssue>> issuesByCentre = current.elastic()
                .allocation()
                .issues()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        P2pElasticAllocationIssue::serviceCentreId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Set<String> serviceCentreIds = new LinkedHashSet<>(issuesByCentre.keySet());
        current.elastic().allocation().serviceCentres()
                .forEach(demand -> serviceCentreIds.add(demand.serviceCentreId()));
        for (String serviceCentreId : serviceCentreIds) {
            List<P2pElasticAllocationIssue> issues = issuesByCentre.getOrDefault(
                    serviceCentreId, List.of());
            String signature = issues.stream()
                    .map(issue -> issue.type() + "=" + issue.detail())
                    .collect(java.util.stream.Collectors.joining("|"));
            String previous = lastElasticIssueSignatures.put(serviceCentreId, signature);
            if ((initial && !issues.isEmpty())
                    || (!initial && !signature.equals(previous) && !issues.isEmpty())) {
                for (P2pElasticAllocationIssue issue : issues) {
                    elasticInfeasibilityHistory.add(
                            new DspFullDayMetricsSnapshot.ElasticInfeasibilityEvent(
                                    current.clock().elapsedSimulationTime(), issue));
                }
            }
        }
    }

    private void captureOccupancySample(MetricInputs current) {
        Duration elapsed = current.clock().elapsedSimulationTime();
        if (!occupancySamples.isEmpty()
                && occupancySamples.getLast().elapsedSimulationTime().equals(elapsed)) {
            return;
        }
        Map<P2pLineId, Optional<String>> owners = new LinkedHashMap<>();
        for (P2pLineLeaseSnapshot line : current.elastic().leases().lines()) {
            owners.put(line.definition().lineId(), line.serviceCentreId());
        }
        long admitted = current.supply().admittedAfterStartupCount();
        long departed = current.osr().departedTotes().size();
        long closed = current.outbound().closedTotes().size();
        long allocated = current.outbound().allocatedBags().size();
        DspFullDayOccupancySample sample = new DspFullDayOccupancySample(
                elapsed,
                current.clock().businessDateTime(),
                current.clock().phase(),
                current.osr().occupancy(),
                current.osr().capacity(),
                osrLowWaterMark,
                current.supply().serviceCentres().stream()
                        .mapToInt(ServiceCentreSupplySnapshot::upstreamWaitingCount)
                        .sum(),
                admitted,
                departed,
                closed,
                allocated,
                signedDifference(admitted, departed),
                owners);
        occupancySamples.add(sample);
        occupancySum = occupancySum.add(BigInteger.valueOf(sample.osrOccupancy()));
        occupancySampleCount = saturatingAdd(occupancySampleCount, 1L);
        if (occupancySampleCount == 1) {
            minimumOsrOccupancy = sample.osrOccupancy();
            maximumOsrOccupancy = sample.osrOccupancy();
        } else {
            minimumOsrOccupancy = Math.min(minimumOsrOccupancy, sample.osrOccupancy());
            maximumOsrOccupancy = Math.max(maximumOsrOccupancy, sample.osrOccupancy());
        }
    }

    private void validateMonotonicCounts(MetricInputs current) {
        if (current.supply().admittedAfterStartupCount() < previousAdmittedInboundToteCount
                || current.osr().departedTotes().size() < previousDepartedInboundToteCount
                || current.outbound().closedTotes().size() < previousClosedOutboundToteCount
                || current.outbound().allocatedBags().size() < previousAllocatedBagCount) {
            throw new IllegalStateException("full-day metric event counts moved backwards");
        }
        previousAdmittedInboundToteCount = current.supply().admittedAfterStartupCount();
        previousDepartedInboundToteCount = current.osr().departedTotes().size();
        previousClosedOutboundToteCount = current.outbound().closedTotes().size();
        previousAllocatedBagCount = current.outbound().allocatedBags().size();
    }

    private boolean hasCapacityBlockedSupply(MetricInputs current) {
        return current.supply().serviceCentres().stream()
                .flatMap(centre -> centre.physicalTotes().stream())
                .anyMatch(tote -> tote.state() == PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY)
                || current.completions().stream()
                        .anyMatch(completion -> completion.capacityBlockedManifestCount() > 0);
    }

    private MetricInputs readInputs() {
        return new MetricInputs(
                value(suppliers.clockSnapshotSupplier().get(), "clockSnapshot"),
                value(suppliers.supplySnapshotSupplier().get(), "supplySnapshot"),
                value(suppliers.osrSnapshotSupplier().get(), "osrSnapshot"),
                value(suppliers.av02SnapshotSupplier().get(), "av02Snapshot"),
                value(suppliers.lifecycleSnapshotSupplier().get(), "lifecycleSnapshot"),
                value(suppliers.elasticSnapshotSupplier().get(), "elasticSnapshot"),
                listValue(suppliers.p2pLineSnapshotsSupplier(), "p2pLineSnapshots"),
                value(suppliers.operationalReleaseSnapshotSupplier().get(), "operationalReleaseSnapshot"),
                value(suppliers.transportInFlightSnapshotSupplier().get(), "transportInFlightSnapshot"),
                value(suppliers.transportIngressSnapshotSupplier().get(), "transportIngressSnapshot"),
                value(suppliers.transportArrivalSnapshotSupplier().get(), "transportArrivalSnapshot"),
                value(suppliers.outboundTransportSnapshotSupplier().get(), "outboundTransportSnapshot"),
                listValue(suppliers.stationArrivalSnapshotsSupplier(), "stationArrivalSnapshots"),
                value(suppliers.stationProcessingSnapshotSupplier().get(), "stationProcessingSnapshot"),
                listValue(suppliers.stationClaimSnapshotsSupplier(), "stationClaimSnapshots"),
                value(suppliers.outboundSnapshotSupplier().get(), "outboundSnapshot"),
                value(suppliers.schedulerSnapshotSupplier().get(), "schedulerSnapshot"),
                listValue(suppliers.completionSnapshotsSupplier(), "completionSnapshots"),
                value(suppliers.runtimeStateSupplier().get(), "runtimeState"));
    }

    private static <T> T value(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " supplier returned null");
        }
        return value;
    }

    private static <T> List<T> listValue(Supplier<List<T>> supplier, String fieldName) {
        List<T> values = value(supplier.get(), fieldName);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException(fieldName + " supplier returned a null element");
        }
        return List.copyOf(values);
    }

    private static OutboundCounts outboundCounts(OutboundAllocationSnapshot outbound) {
        return new OutboundCounts(
                outbound.closedTotes().size(),
                outbound.allocatedBags().size());
    }

    private Duration totalBlockDuration(DspFullDayBlockCategory category) {
        Duration result = Duration.ZERO;
        for (EnumMap<DspFullDayBlockCategory, MutableBlockSummary> summaries : blocksByCentre.values()) {
            MutableBlockSummary summary = summaries.get(category);
            if (summary != null) {
                result = saturatingAdd(result, summary.duration);
            }
        }
        return result;
    }

    private static EnumMap<DspFullDayBlockCategory, MutableBlockSummary> emptyBlockMap() {
        EnumMap<DspFullDayBlockCategory, MutableBlockSummary> result =
                new EnumMap<>(DspFullDayBlockCategory.class);
        for (DspFullDayBlockCategory category : DspFullDayBlockCategory.values()) {
            result.put(category, new MutableBlockSummary());
        }
        return result;
    }

    private static Duration requirePositiveDuration(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Duration durationFromSeconds(double seconds) {
        requireNonnegativeFinite(seconds, "seconds");
        BigDecimal nanos = BigDecimal.valueOf(seconds)
                .multiply(NANOSECONDS_PER_SECOND)
                .setScale(0, RoundingMode.HALF_UP);
        if (nanos.compareTo(new BigDecimal(MAX_DURATION_NANOS)) >= 0) {
            return Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        }
        return Duration.ofNanos(nanos.longValueExact());
    }

    private static Duration saturatingAdd(Duration first, Duration second) {
        BigInteger nanos = toNanos(first).add(toNanos(second));
        return durationFromNanos(nanos);
    }

    private static Duration durationFromNanos(BigInteger nanos) {
        if (nanos.signum() <= 0) {
            return Duration.ZERO;
        }
        if (nanos.compareTo(MAX_DURATION_NANOS) >= 0) {
            return Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        }
        BigInteger[] parts = nanos.divideAndRemainder(BILLION);
        return Duration.ofSeconds(parts[0].longValueExact(), parts[1].intValueExact());
    }

    private static BigInteger toNanos(Duration duration) {
        return BigInteger.valueOf(duration.getSeconds())
                .multiply(BILLION)
                .add(BigInteger.valueOf(duration.getNano()));
    }

    private static long saturatingAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        if (second < 0 && first < Long.MIN_VALUE - second) {
            return Long.MIN_VALUE;
        }
        return first + second;
    }

    private static long signedDifference(long first, long second) {
        if (second > 0 && first < Long.MIN_VALUE + second) {
            return Long.MIN_VALUE;
        }
        if (second < 0 && first > Long.MAX_VALUE + second) {
            return Long.MAX_VALUE;
        }
        return first - second;
    }

    private static double rate(long count, double seconds) {
        if (count <= 0 || !Double.isFinite(seconds) || seconds <= 0d) {
            return 0d;
        }
        double result = count / seconds;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double boundedRatio(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            return 0d;
        }
        return Math.min(1d, numerator / (double) denominator);
    }

    private static double durationToSeconds(Duration duration) {
        return BigDecimal.valueOf(duration.getSeconds())
                .add(BigDecimal.valueOf(duration.getNano(), 9))
                .doubleValue();
    }

    private static long durationToNanosForRatio(Duration duration) {
        BigInteger nanos = toNanos(duration);
        return nanos.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE
                : nanos.longValue();
    }

    private static void requireNonnegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and nonnegative");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record OutboundCounts(long closedToteCount, long allocatedBagCount) { }

    private record BlockObservation(
            DspFullDayBlockCategory category,
            long count,
            String reason) { }

    private record MetricInputs(
            DspOperationalClockSnapshot clock,
            DspSupplySnapshot supply,
            OsrInventorySnapshot osr,
            Av02InventorySnapshot av02,
            PhysicalToteLifecycleSnapshot lifecycle,
            DspP2pElasticAllocationRuntimeSnapshot elastic,
            List<DspHeadlessP2pLineRuntimeSnapshot> p2pLines,
            DspOperationalReleaseControllerSnapshot operationalRelease,
            WarehouseTransportInFlightSnapshot transportInFlight,
            WarehouseTransportIngressControllerSnapshot transportIngress,
            WarehouseTransportArrivalControllerSnapshot transportArrival,
            OsrOutboundTransportQueueSnapshot outboundTransport,
            List<StationRoutedToteArrivalQueueSnapshot> stationArrivals,
            StationProcessingSnapshot stationProcessing,
            List<StationArrivalClaimControllerSnapshot> stationClaims,
            OutboundAllocationSnapshot outbound,
            WarehouseSchedulerSnapshot scheduler,
            List<DspServiceCentreCompletionSnapshot> completions,
            DspFullDayRuntimeState state) { }

    private static final class MutableBlockSummary {
        private long count;
        private Duration duration = Duration.ZERO;
        private String latestReason;

        private void add(long units, Duration stepDuration, String reason) {
            if (units < 1) {
                return;
            }
            count = saturatingAdd(count, units);
            duration = saturatingAdd(duration, multiply(stepDuration, units));
            if (reason != null && !reason.isBlank()) {
                latestReason = reason.trim();
            }
        }

        private DspServiceCentreMetricsSnapshot.BlockSummary snapshot() {
            return new DspServiceCentreMetricsSnapshot.BlockSummary(
                    count,
                    duration,
                    Optional.ofNullable(latestReason));
        }

        private static Duration multiply(Duration value, long multiplier) {
            if (multiplier <= 0 || value.isZero()) {
                return Duration.ZERO;
            }
            return durationFromNanos(toNanos(value).multiply(BigInteger.valueOf(multiplier)));
        }
    }

    private static final class OperationalBlockCounts {
        private long dependencyCount;
        private long stationCount;
        private long p2pCount;
        private String dependencyReason;
        private String stationReason;
        private String p2pReason;

        private void add(OperationalReleaseBlock block) {
            String reason = block.type() + ": " + block.reason();
            switch (block.type()) {
                case ACTIVE_SHEET_ASSIGNMENT, ADAPTED_DEPENDENCY -> {
                    dependencyCount = saturatingAdd(dependencyCount, 1);
                    dependencyReason = reason;
                }
                case ROUTE_ENTRY, STATION_ADMISSION, TARGET_SELECTION -> {
                    stationCount = saturatingAdd(stationCount, 1);
                    stationReason = reason;
                }
                case P2P_LINE_ALLOCATION -> {
                    p2pCount = saturatingAdd(p2pCount, 1);
                    p2pReason = reason;
                }
            }
        }

        private long dependencyCount() {
            return dependencyCount;
        }

        private long stationCount() {
            return stationCount;
        }

        private long p2pCount() {
            return p2pCount;
        }

        private String dependencyReason() {
            return dependencyReason == null ? "operational dependency is not terminal" : dependencyReason;
        }

        private String stationReason() {
            return stationReason == null ? "station or route-entry admission is blocked" : stationReason;
        }

        private String p2pReason() {
            return p2pReason == null ? "P2P assignment is blocked" : p2pReason;
        }
    }

    /** Immutable suppliers for the existing runtime snapshot boundaries. */
    public record SnapshotSuppliers(
            Supplier<DspOperationalClockSnapshot> clockSnapshotSupplier,
            Supplier<DspSupplySnapshot> supplySnapshotSupplier,
            Supplier<OsrInventorySnapshot> osrSnapshotSupplier,
            Supplier<Av02InventorySnapshot> av02SnapshotSupplier,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSnapshotSupplier,
            Supplier<DspP2pElasticAllocationRuntimeSnapshot> elasticSnapshotSupplier,
            Supplier<List<DspHeadlessP2pLineRuntimeSnapshot>> p2pLineSnapshotsSupplier,
            Supplier<DspOperationalReleaseControllerSnapshot> operationalReleaseSnapshotSupplier,
            Supplier<WarehouseTransportInFlightSnapshot> transportInFlightSnapshotSupplier,
            Supplier<WarehouseTransportIngressControllerSnapshot> transportIngressSnapshotSupplier,
            Supplier<WarehouseTransportArrivalControllerSnapshot> transportArrivalSnapshotSupplier,
            Supplier<OsrOutboundTransportQueueSnapshot> outboundTransportSnapshotSupplier,
            Supplier<List<StationRoutedToteArrivalQueueSnapshot>> stationArrivalSnapshotsSupplier,
            Supplier<StationProcessingSnapshot> stationProcessingSnapshotSupplier,
            Supplier<List<StationArrivalClaimControllerSnapshot>> stationClaimSnapshotsSupplier,
            Supplier<OutboundAllocationSnapshot> outboundSnapshotSupplier,
            Supplier<WarehouseSchedulerSnapshot> schedulerSnapshotSupplier,
            Supplier<List<DspServiceCentreCompletionSnapshot>> completionSnapshotsSupplier,
            Supplier<DspFullDayRuntimeState> runtimeStateSupplier) {

        public SnapshotSuppliers {
            Objects.requireNonNull(clockSnapshotSupplier, "clockSnapshotSupplier must not be null");
            Objects.requireNonNull(supplySnapshotSupplier, "supplySnapshotSupplier must not be null");
            Objects.requireNonNull(osrSnapshotSupplier, "osrSnapshotSupplier must not be null");
            Objects.requireNonNull(av02SnapshotSupplier, "av02SnapshotSupplier must not be null");
            Objects.requireNonNull(lifecycleSnapshotSupplier, "lifecycleSnapshotSupplier must not be null");
            Objects.requireNonNull(elasticSnapshotSupplier, "elasticSnapshotSupplier must not be null");
            Objects.requireNonNull(p2pLineSnapshotsSupplier, "p2pLineSnapshotsSupplier must not be null");
            Objects.requireNonNull(operationalReleaseSnapshotSupplier,
                    "operationalReleaseSnapshotSupplier must not be null");
            Objects.requireNonNull(transportInFlightSnapshotSupplier,
                    "transportInFlightSnapshotSupplier must not be null");
            Objects.requireNonNull(transportIngressSnapshotSupplier,
                    "transportIngressSnapshotSupplier must not be null");
            Objects.requireNonNull(transportArrivalSnapshotSupplier,
                    "transportArrivalSnapshotSupplier must not be null");
            Objects.requireNonNull(outboundTransportSnapshotSupplier,
                    "outboundTransportSnapshotSupplier must not be null");
            Objects.requireNonNull(stationArrivalSnapshotsSupplier,
                    "stationArrivalSnapshotsSupplier must not be null");
            Objects.requireNonNull(stationProcessingSnapshotSupplier,
                    "stationProcessingSnapshotSupplier must not be null");
            Objects.requireNonNull(stationClaimSnapshotsSupplier,
                    "stationClaimSnapshotsSupplier must not be null");
            Objects.requireNonNull(outboundSnapshotSupplier, "outboundSnapshotSupplier must not be null");
            Objects.requireNonNull(schedulerSnapshotSupplier, "schedulerSnapshotSupplier must not be null");
            Objects.requireNonNull(completionSnapshotsSupplier,
                    "completionSnapshotsSupplier must not be null");
            Objects.requireNonNull(runtimeStateSupplier, "runtimeStateSupplier must not be null");
        }
    }
}
