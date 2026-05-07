package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;

public class DspReleaseScheduler {
    private final ServiceCentreWindowPolicy windowPolicy;
    private final DspDependencyEvaluator dependencyEvaluator;
    private final StationAdmissionResolver stationAdmissionResolver;

    public DspReleaseScheduler(ServiceCentreWindowPolicy windowPolicy, DspDependencyEvaluator dependencyEvaluator) {
        this(windowPolicy, dependencyEvaluator, new SnapshotStationAdmissionResolver());
    }

    public DspReleaseScheduler(
            ServiceCentreWindowPolicy windowPolicy,
            DspDependencyEvaluator dependencyEvaluator,
            StationAdmissionResolver stationAdmissionResolver) {
        if (windowPolicy == null) {
            throw new IllegalArgumentException("windowPolicy must not be null");
        }
        if (dependencyEvaluator == null) {
            throw new IllegalArgumentException("dependencyEvaluator must not be null");
        }
        if (stationAdmissionResolver == null) {
            throw new IllegalArgumentException("stationAdmissionResolver must not be null");
        }
        this.windowPolicy = windowPolicy;
        this.dependencyEvaluator = dependencyEvaluator;
        this.stationAdmissionResolver = stationAdmissionResolver;
    }

    public SchedulerEvaluation evaluate(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        Optional<String> activeWindow = windowPolicy.activeWindowFor(snapshot);
        if (activeWindow.isEmpty()) {
            return SchedulerEvaluation.nothingToRelease();
        }

        String activeServiceCentreId = activeWindow.get();
        List<DspSchedulerOrderState> candidates = snapshot.orderStates().stream()
                .filter(orderState -> activeServiceCentreId.equals(orderState.order().serviceCentreId()))
                .filter(orderState -> orderState.status() == DspOrderStatus.WAITING || orderState.status() == DspOrderStatus.BLOCKED)
                .sorted(orderComparator())
                .toList();

        if (candidates.isEmpty()) {
            return SchedulerEvaluation.nothingToRelease();
        }

        List<String> candidateOrderIds = new ArrayList<>();
        List<String> blockReasons = new ArrayList<>();
        for (DspSchedulerOrderState candidate : candidates) {
            candidateOrderIds.add(candidate.order().orderId());

            List<String> reasons = collectBlockReasons(candidate, snapshot);
            if (reasons.isEmpty()) {
                ReleaseOrderCommand command = new ReleaseOrderCommand(
                        candidate.order().orderId(),
                        candidate.order().serviceCentreId(),
                        candidate.routeRequirements().startLocation());
                ReleaseDecision decision = new ReleaseDecision(
                        candidate.order().orderId(),
                        candidate.order().serviceCentreId(),
                        candidate.routeRequirements().startLocation(),
                        candidate.routeRequirements(),
                        command);
                return SchedulerEvaluation.release(decision);
            }
            for (String reason : reasons) {
                blockReasons.add(candidate.order().orderId() + ": " + reason);
            }
        }

        return SchedulerEvaluation.blocked(new BlockedDecision(activeServiceCentreId, candidateOrderIds, blockReasons));
    }

    private List<String> collectBlockReasons(DspSchedulerOrderState candidate, WarehouseSchedulerSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        dependencyEvaluator.findBlocks(candidate, snapshot).stream()
                .map(DependencyBlock::reason)
                .forEach(reasons::add);
        addCapacityBlockReasons(candidate, snapshot, reasons);
        return List.copyOf(reasons);
    }

    private void addCapacityBlockReasons(
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot,
            List<String> reasons) {
        RouteRequirements routeRequirements = candidate.routeRequirements();
        Set<StationType> requiredStations = new LinkedHashSet<>();
        if (routeRequirements.requiresThirdParty()) {
            requiredStations.add(StationType.THIRD_PARTY);
        }
        if (routeRequirements.requiresSortable()) {
            requiredStations.add(StationType.ADAPTING);
        }
        if (routeRequirements.requiresManual()) {
            requiredStations.add(StationType.MANUAL);
        }
        if (routeRequirements.requiresP2p()) {
            requiredStations.add(StationType.P2P);
        }
        if (routeRequirements.requiresManualMerge()) {
            requiredStations.add(StationType.MANUAL_MERGE);
        }

        for (StationType stationType : requiredStations) {
            StationAdmissionSnapshot admission = stationAdmissionResolver.admissionFor(stationType, candidate, snapshot);
            if (admission == null) {
                reasons.add("Missing station admission snapshot for " + stationType);
                continue;
            }
            if (!admission.canAccept()) {
                String blockedReason = admission.blockedReason().isBlank()
                        ? "Station " + stationType + " has no capacity"
                        : admission.blockedReason();
                reasons.add(blockedReason);
            }
        }
    }

    private Comparator<DspSchedulerOrderState> orderComparator() {
        return Comparator
                .comparingInt((DspSchedulerOrderState orderState) -> priorityFor(orderState.order().orderType()))
                .thenComparingInt(orderState -> orderState.order().sheetNumber())
                .thenComparingLong(orderState -> orderState.order().sequenceNumber())
                .thenComparing(orderState -> orderState.order().orderId());
    }

    private int priorityFor(OrderType orderType) {
        return switch (orderType) {
            case ADAPTED -> 0;
            case ASSOCIATED, EMPTY -> 1;
            case FULL_PACK -> 2;
        };
    }
}
