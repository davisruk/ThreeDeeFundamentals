package online.davisfamily.warehouse.sim.dsp.outbound;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public final class OutboundToteAllocationController implements SimulationController {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;
    private static final Function<String, Set<String>> NO_MISSING_PACK_IDS_PROVIDER = ignored -> Set.of();

    private final P2pLineId p2pLineId;
    private final StoredBagReceiver completedBagReceiver;
    private final List<Bag> receivedBagView;
    private final BagPlanningResult bagPlanningResult;
    private final OutboundToteAllocator outboundToteAllocator;
    private final Function<String, Set<String>> missingPackIdsProvider;

    public OutboundToteAllocationController(
            P2pLineId p2pLineId,
            StoredBagReceiver completedBagReceiver,
            BagPlanningResult bagPlanningResult,
            OutboundToteAllocator outboundToteAllocator) {
        this(p2pLineId, completedBagReceiver, bagPlanningResult, outboundToteAllocator,
                NO_MISSING_PACK_IDS_PROVIDER);
    }

    public OutboundToteAllocationController(
            P2pLineId p2pLineId,
            StoredBagReceiver completedBagReceiver,
            BagPlanningResult bagPlanningResult,
            OutboundToteAllocator outboundToteAllocator,
            Function<String, Set<String>> missingPackIdsProvider) {
        if (p2pLineId == null
                || completedBagReceiver == null
                || bagPlanningResult == null
                || outboundToteAllocator == null
                || missingPackIdsProvider == null) {
            throw new IllegalArgumentException("Outbound allocation controller inputs must not be null");
        }
        List<Bag> receivedBagView = completedBagReceiver.getReceivedBags();
        if (receivedBagView == null) {
            throw new IllegalArgumentException("completedBagReceiver must expose a received-bag view");
        }
        this.p2pLineId = p2pLineId;
        this.completedBagReceiver = completedBagReceiver;
        this.receivedBagView = receivedBagView;
        this.bagPlanningResult = bagPlanningResult;
        this.outboundToteAllocator = outboundToteAllocator;
        this.missingPackIdsProvider = missingPackIdsProvider;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (receivedBagView.isEmpty()) {
            return;
        }
        List<Bag> receivedBags = List.copyOf(receivedBagView);
        Duration allocationTime = simulationTime(context.getSimulationTimeSeconds());
        for (Bag runtimeBag : receivedBags) {
            PlannedBag plannedBag = bagPlanningResult
                    .findBagByCorrelationId(runtimeBag.getCorrelationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No planned bag for runtime correlation: " + runtimeBag.getCorrelationId()));
            Set<String> missingPackIds = missingPackIdsProvider.apply(
                    runtimeBag.getCorrelationId());
            if (missingPackIds == null) {
                throw new IllegalStateException(
                        "missingPackIdsProvider returned null for correlation: "
                                + runtimeBag.getCorrelationId());
            }
            validateRuntimePackPartition(runtimeBag, plannedBag, missingPackIds);

            outboundToteAllocator.allocate(p2pLineId, plannedBag, allocationTime, missingPackIds);
            if (!completedBagReceiver.removeReceivedBag(runtimeBag)) {
                throw new IllegalStateException(
                        "Allocated runtime bag was no longer present in receiver: "
                                + runtimeBag.getCorrelationId());
            }
        }
    }

    private static void validateRuntimePackPartition(
            Bag runtimeBag,
            PlannedBag plannedBag,
            Set<String> missingPackIds) {
        // Physical arrival order is not planned line order. Each planned ID must occur
        // exactly once in either the physical bag or the registered missing set.
        Set<String> unmatchedPackIds = new HashSet<>(plannedBag.physicalPackIds());
        List<PackPlan> runtimePacks = runtimeBag.getPackContents();
        if (runtimePacks.isEmpty()) {
            throw packPartitionMismatch(runtimeBag, plannedBag, missingPackIds);
        }
        for (PackPlan runtimePack : runtimePacks) {
            if (!unmatchedPackIds.remove(runtimePack.packId())) {
                throw packPartitionMismatch(runtimeBag, plannedBag, missingPackIds);
            }
        }
        for (String missingPackId : missingPackIds) {
            if (!unmatchedPackIds.remove(missingPackId)) {
                throw packPartitionMismatch(runtimeBag, plannedBag, missingPackIds);
            }
        }
        if (!unmatchedPackIds.isEmpty()) {
            throw packPartitionMismatch(runtimeBag, plannedBag, missingPackIds);
        }
    }

    private static IllegalStateException packPartitionMismatch(
            Bag runtimeBag,
            PlannedBag plannedBag,
            Set<String> missingPackIds) {
        return new IllegalStateException(
                "Runtime pack IDs do not match planned bag for correlation: "
                        + runtimeBag.getCorrelationId()
                        + "; planned=" + plannedBag.physicalPackIds()
                        + "; actual=" + runtimeBag.getPackContents().stream()
                                .map(PackPlan::packId).toList()
                        + "; registeredMissing=" + missingPackIds);
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
