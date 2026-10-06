package online.davisfamily.warehouse.sim.dsp.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;

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
        var runtimePackIterator = runtimeBag.getPackContents().iterator();
        int actualPackCount = 0;
        int matchedMissingPackCount = 0;
        for (String plannedPackId : plannedBag.physicalPackIds()) {
            if (missingPackIds.contains(plannedPackId)) {
                matchedMissingPackCount++;
                continue;
            }
            if (!runtimePackIterator.hasNext()
                    || !plannedPackId.equals(runtimePackIterator.next().packId())) {
                throw packPartitionMismatch(runtimeBag);
            }
            actualPackCount++;
        }
        if (runtimePackIterator.hasNext()
                || matchedMissingPackCount != missingPackIds.size()
                || actualPackCount == 0) {
            throw packPartitionMismatch(runtimeBag);
        }
    }

    private static IllegalStateException packPartitionMismatch(Bag runtimeBag) {
        return new IllegalStateException(
                "Runtime pack IDs do not match planned bag for correlation: "
                        + runtimeBag.getCorrelationId());
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
