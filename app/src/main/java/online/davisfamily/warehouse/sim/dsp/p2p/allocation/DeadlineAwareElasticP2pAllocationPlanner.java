package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshotFactory;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;

public final class DeadlineAwareElasticP2pAllocationPlanner {

    private final ServiceCentreDeadlineSnapshotFactory deadlineFactory =
            new ServiceCentreDeadlineSnapshotFactory();

    public P2pElasticAllocationSnapshot create(
            DspOperationalClockSnapshot clock,
            DspSupplySnapshot supply,
            P2pWorkloadSnapshot workload,
            DspServiceCentreTimetable timetable,
            P2pLineLeaseCatalogSnapshot leases,
            P2pElasticAllocationConfig config) {
        if (clock == null || supply == null || workload == null || timetable == null
                || leases == null || config == null) {
            throw new IllegalArgumentException("allocation planner inputs must not be null");
        }
        if (leases.lines().size() != config.p2pLineCount()) {
            throw new IllegalArgumentException(
                    "lease catalog line count must equal configured p2pLineCount");
        }

        validateIdentities(supply, workload, timetable, leases);
        Map<String, ServiceCentreDeadlineSnapshot> deadlines = deadlinesById(
                deadlineFactory.create(timetable, clock, config.downstreamHandlingDuration()));

        List<CentreInput> ordered = supply.serviceCentres().stream()
                .filter(this::isSupplied)
                .map(serviceCentre -> new CentreInput(
                        serviceCentre,
                        workload.require(serviceCentre.serviceCentreId()),
                        deadlines.get(serviceCentre.serviceCentreId())))
                .filter(input -> input.workload().hasEstimatedWork())
                .peek(this::requireAuthorizationTime)
                .sorted(Comparator
                        .comparing((CentreInput input) -> input.supply()
                                .authorizationElapsedTime().orElseThrow())
                        .thenComparing(
                                (CentreInput input) -> input.supply().priority(),
                                Comparator.reverseOrder())
                        .thenComparing(input -> input.supply().serviceCentreId()))
                .toList();

        List<P2pLineId> configuredLineIds = leases.lines().stream()
                .map(line -> line.definition().lineId())
                .toList();
        List<P2pServiceCentreLineDemandSnapshot> demands = new ArrayList<>();
        List<P2pElasticAllocationIssue> issues = new ArrayList<>();
        int remainingLineCapacity = config.p2pLineCount();

        for (int index = 0; index < ordered.size(); index++) {
            CentreInput input = ordered.get(index);
            boolean withinWindow = index < config.maximumConcurrentServiceCentres();
            DemandCalculation calculation = calculateDemand(input, config);
            int requestedLines = index == 0
                    ? Math.max(
                            calculation.requiredLines(),
                            config.minimumReservedLinesForEarlierCentre())
                    : calculation.requiredLines();
            int desiredLines = withinWindow
                    ? Math.min(requestedLines, remainingLineCapacity)
                    : 0;
            if (withinWindow) {
                remainingLineCapacity -= desiredLines;
            }

            List<P2pLineLeaseSnapshot> ownedLines = leases.lines().stream()
                    .filter(line -> line.serviceCentreId()
                            .filter(input.supply().serviceCentreId()::equals)
                            .isPresent())
                    .toList();
            int feedingCount = Math.min(desiredLines, ownedLines.size());
            List<P2pLineLeaseSnapshot> feedingLines = feedingLines(ownedLines, feedingCount);
            Set<P2pLineId> feedingIds = new LinkedHashSet<>();
            feedingLines.forEach(line -> feedingIds.add(line.definition().lineId()));
            List<P2pLineId> drainingIds = ownedLines.stream()
                    .map(line -> line.definition().lineId())
                    .filter(lineId -> !feedingIds.contains(lineId))
                    .toList();

            List<P2pElasticAllocationIssueType> demandIssues = new ArrayList<>();
            if (input.deadline().latestAllowedCompletionPassed()) {
                addIssue(input, P2pElasticAllocationIssueType.LATEST_ALLOWED_COMPLETION_PASSED,
                        "latest allowed completion has passed", demandIssues, issues);
            }
            if (calculation.rawRequiredLines() > config.p2pLineCount()) {
                addIssue(input, P2pElasticAllocationIssueType.DEMAND_EXCEEDS_LINE_CAPACITY,
                        "raw line demand exceeds configured P2P capacity", demandIssues, issues);
            }
            if (!withinWindow) {
                addIssue(input,
                        P2pElasticAllocationIssueType.OUTSIDE_CONCURRENT_SERVICE_CENTRE_WINDOW,
                        "authorized centre is outside the concurrent processing window",
                        demandIssues,
                        issues);
                if (!ownedLines.isEmpty()) {
                    addIssue(input,
                            P2pElasticAllocationIssueType.LEASE_OWNER_OUTSIDE_ACTIVE_WINDOW,
                            "centre owns a line outside the concurrent processing window",
                            demandIssues,
                            issues);
                }
            }
            if (desiredLines < calculation.requiredLines()) {
                addIssue(input,
                        P2pElasticAllocationIssueType.INSUFFICIENT_SHARED_LINE_CAPACITY,
                        "shared line capacity is below required demand",
                        demandIssues,
                        issues);
            }

            demands.add(new P2pServiceCentreLineDemandSnapshot(
                    input.supply().serviceCentreId(),
                    input.supply().priority(),
                    input.supply().authorizationElapsedTime().orElseThrow(),
                    input.deadline(),
                    input.workload(),
                    calculation.adjustedWork(),
                    calculation.rawRequiredLines(),
                    calculation.requiredLines(),
                    desiredLines,
                    feedingLines.stream().map(line -> line.definition().lineId()).toList(),
                    drainingIds,
                    Math.max(0, desiredLines - feedingCount),
                    Math.max(0, calculation.requiredLines() - desiredLines),
                    withinWindow,
                    demandIssues));
        }

        reportInactiveOwners(ordered, leases, issues);
        return new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                clock.businessDateTime(),
                configuredLineIds,
                config.maximumConcurrentServiceCentres(),
                demands,
                issues);
    }

    private void validateIdentities(
            DspSupplySnapshot supply,
            P2pWorkloadSnapshot workload,
            DspServiceCentreTimetable timetable,
            P2pLineLeaseCatalogSnapshot leases) {
        Set<String> supplyIds = new LinkedHashSet<>();
        for (ServiceCentreSupplySnapshot serviceCentre : supply.serviceCentres()) {
            supplyIds.add(serviceCentre.serviceCentreId());
            if (timetable.require(serviceCentre.serviceCentreId()).priority()
                    != serviceCentre.priority()) {
                throw new IllegalArgumentException(
                        "supply and timetable priorities must match for service centre "
                                + serviceCentre.serviceCentreId());
            }
            workload.require(serviceCentre.serviceCentreId());
        }
        for (P2pServiceCentreWorkloadSnapshot serviceCentre : workload.serviceCentres()) {
            if (!supplyIds.contains(serviceCentre.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "workload service centre is absent from supply: "
                                + serviceCentre.serviceCentreId());
            }
        }
        leases.lines().stream()
                .flatMap(line -> line.serviceCentreId().stream())
                .forEach(owner -> {
                    if (!supplyIds.contains(owner)) {
                        throw new IllegalArgumentException(
                                "lease owner is absent from supply: " + owner);
                    }
                });
    }

    private Map<String, ServiceCentreDeadlineSnapshot> deadlinesById(
            List<ServiceCentreDeadlineSnapshot> deadlines) {
        Map<String, ServiceCentreDeadlineSnapshot> byId = new LinkedHashMap<>();
        deadlines.forEach(deadline -> byId.put(deadline.serviceCentreId(), deadline));
        return byId;
    }

    private boolean isSupplied(ServiceCentreSupplySnapshot serviceCentre) {
        return serviceCentre.authorizationState() == ServiceCentreAuthorizationState.PRELOADED
                || serviceCentre.authorizationState()
                        == ServiceCentreAuthorizationState.AUTHORIZED
                || serviceCentre.authorizationState()
                        == ServiceCentreAuthorizationState.SUPPLY_COMPLETE;
    }

    private void requireAuthorizationTime(CentreInput input) {
        if (input.supply().authorizationElapsedTime().isEmpty()) {
            throw new IllegalArgumentException(
                    "active nonterminal service centre must have an authorization time: "
                            + input.supply().serviceCentreId());
        }
    }

    private DemandCalculation calculateDemand(
            CentreInput input,
            P2pElasticAllocationConfig config) {
        Duration adjustedWork = adjustedWork(
                input.workload().estimatedSingleLineWork(),
                config.safetyFactorPermille(),
                config.parallelEfficiencyPermille());
        if (!input.workload().hasEstimatedWork()) {
            return new DemandCalculation(adjustedWork, 0, 0);
        }
        long rawRequiredLines;
        if (input.deadline().availableTime().isZero()) {
            rawRequiredLines = config.p2pLineCount();
        } else {
            long workNanos = adjustedWork.toNanos();
            long availableNanos = input.deadline().availableTime().toNanos();
            rawRequiredLines = Math.max(1, ceilingDivide(workNanos, availableNanos));
        }
        int requiredLines = (int) Math.min(rawRequiredLines, config.p2pLineCount());
        return new DemandCalculation(adjustedWork, rawRequiredLines, requiredLines);
    }

    private Duration adjustedWork(Duration work, int safetyPermille, int efficiencyPermille) {
        BigInteger numerator = BigInteger.valueOf(work.toNanos())
                .multiply(BigInteger.valueOf(safetyPermille));
        BigInteger divisor = BigInteger.valueOf(efficiencyPermille);
        BigInteger adjusted = numerator.add(divisor).subtract(BigInteger.ONE).divide(divisor);
        if (adjusted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("adjusted workload must fit in nanoseconds");
        }
        return Duration.ofNanos(adjusted.longValueExact());
    }

    private long ceilingDivide(long numerator, long denominator) {
        return numerator / denominator + (numerator % denominator == 0 ? 0 : 1);
    }

    private List<P2pLineLeaseSnapshot> feedingLines(
            List<P2pLineLeaseSnapshot> ownedLines,
            int feedingCount) {
        Map<P2pLineId, Integer> configuredOrder = new LinkedHashMap<>();
        for (int index = 0; index < ownedLines.size(); index++) {
            configuredOrder.put(ownedLines.get(index).definition().lineId(), index);
        }
        return ownedLines.stream()
                .sorted(Comparator
                        .comparing((P2pLineLeaseSnapshot line) ->
                                line.activity().openOutboundTote().isEmpty())
                        .thenComparingInt(line -> configuredOrder.get(
                                line.definition().lineId())))
                .limit(feedingCount)
                .toList();
    }

    private void addIssue(
            CentreInput input,
            P2pElasticAllocationIssueType type,
            String detail,
            List<P2pElasticAllocationIssueType> demandIssues,
            List<P2pElasticAllocationIssue> issues) {
        if (!demandIssues.contains(type)) {
            demandIssues.add(type);
            issues.add(new P2pElasticAllocationIssue(
                    input.supply().serviceCentreId(), type, detail));
        }
    }

    private void reportInactiveOwners(
            List<CentreInput> activeCentres,
            P2pLineLeaseCatalogSnapshot leases,
            List<P2pElasticAllocationIssue> issues) {
        Set<String> activeIds = activeCentres.stream()
                .map(input -> input.supply().serviceCentreId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        leases.lines().stream()
                .flatMap(line -> line.serviceCentreId().stream())
                .filter(owner -> !activeIds.contains(owner))
                .distinct()
                .forEach(owner -> issues.add(new P2pElasticAllocationIssue(
                        owner,
                        P2pElasticAllocationIssueType.LEASE_OWNER_OUTSIDE_ACTIVE_WINDOW,
                        "current lease owner has no active estimated workload")));
    }

    private record CentreInput(
            ServiceCentreSupplySnapshot supply,
            P2pServiceCentreWorkloadSnapshot workload,
            ServiceCentreDeadlineSnapshot deadline) {
    }

    private record DemandCalculation(
            Duration adjustedWork,
            long rawRequiredLines,
            int requiredLines) {
    }
}
