package online.davisfamily.warehouse.sim.dsp.outbound;

import java.time.Duration;
import java.util.List;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public final class OutboundToteAllocationController implements SimulationController {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000d;

    private final P2pLineId p2pLineId;
    private final StoredBagReceiver completedBagReceiver;
    private final BagPlanningResult bagPlanningResult;
    private final OutboundToteAllocator outboundToteAllocator;

    public OutboundToteAllocationController(
            P2pLineId p2pLineId,
            StoredBagReceiver completedBagReceiver,
            BagPlanningResult bagPlanningResult,
            OutboundToteAllocator outboundToteAllocator) {
        if (p2pLineId == null
                || completedBagReceiver == null
                || bagPlanningResult == null
                || outboundToteAllocator == null) {
            throw new IllegalArgumentException("Outbound allocation controller inputs must not be null");
        }
        this.p2pLineId = p2pLineId;
        this.completedBagReceiver = completedBagReceiver;
        this.bagPlanningResult = bagPlanningResult;
        this.outboundToteAllocator = outboundToteAllocator;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Duration allocationTime = simulationTime(context.getSimulationTimeSeconds());
        List<Bag> receivedBags = List.copyOf(completedBagReceiver.getReceivedBags());
        for (Bag runtimeBag : receivedBags) {
            PlannedBag plannedBag = bagPlanningResult
                    .findBagByCorrelationId(runtimeBag.getCorrelationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No planned bag for runtime correlation: " + runtimeBag.getCorrelationId()));
            List<String> runtimePackIds = runtimeBag.getPackContents().stream()
                    .map(PackPlan::packId)
                    .toList();
            if (!runtimePackIds.equals(plannedBag.physicalPackIds())) {
                throw new IllegalStateException(
                        "Runtime pack IDs do not match planned bag for correlation: "
                                + runtimeBag.getCorrelationId());
            }

            outboundToteAllocator.allocate(p2pLineId, plannedBag, allocationTime);
            if (!completedBagReceiver.removeReceivedBag(runtimeBag)) {
                throw new IllegalStateException(
                        "Allocated runtime bag was no longer present in receiver: "
                                + runtimeBag.getCorrelationId());
            }
        }
    }

    private static Duration simulationTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("simulation time must be finite and nonnegative");
        }
        return Duration.ofNanos(Math.round(seconds * NANOSECONDS_PER_SECOND));
    }
}
