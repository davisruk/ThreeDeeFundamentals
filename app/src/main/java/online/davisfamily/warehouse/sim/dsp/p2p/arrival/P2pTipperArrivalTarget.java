package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public final class P2pTipperArrivalTarget implements ToteLoadPlanProvider {
    private final OperationalRouteDestination destination;
    private final TipperInputQueue inputQueue;
    private final P2pTipperArrivalAcceptedListener acceptedListener;
    private final Map<String, ToteLoadPlan> acceptedLoadPlansByToteId = new LinkedHashMap<>();
    private long acceptedCount;

    public P2pTipperArrivalTarget(
            OperationalRouteDestination destination,
            TipperInputQueue inputQueue) {
        this(destination, inputQueue, P2pTipperArrivalAcceptedListener.noOp());
    }

    public P2pTipperArrivalTarget(
            OperationalRouteDestination destination,
            TipperInputQueue inputQueue,
            P2pTipperArrivalAcceptedListener acceptedListener) {
        if (destination == null || destination.stationType() != StationType.P2P) {
            throw new IllegalArgumentException("destination must identify a P2P station");
        }
        if (inputQueue == null) {
            throw new IllegalArgumentException("inputQueue must not be null");
        }
        if (acceptedListener == null) {
            throw new IllegalArgumentException("acceptedListener must not be null");
        }
        this.destination = destination;
        this.inputQueue = inputQueue;
        this.acceptedListener = acceptedListener;
    }

    public OperationalRouteDestination destination() {
        return destination;
    }

    public boolean canAccept() {
        return inputQueue.canAccept();
    }

    public boolean hasAccepted(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return acceptedLoadPlansByToteId.containsKey(physicalToteId.value());
    }

    public void accept(
            RoutedPhysicalTote routedTote,
            TipperTotePayload payload) {
        validateAcceptance(routedTote, payload);

        ToteLoadPlan loadPlan = routedTote.loadPlan();
        String toteId = routedTote.physicalToteId().value();
        inputQueue.enqueue(payload);
        acceptedLoadPlansByToteId.put(toteId, loadPlan);
        acceptedCount++;
        acceptedListener.onAccepted(payload, loadPlan);
    }

    @Override
    public ToteLoadPlan getLoadPlanFor(String toteId) {
        if (toteId == null || toteId.isBlank()) {
            throw new IllegalArgumentException("toteId must not be blank");
        }
        return acceptedLoadPlansByToteId.get(toteId);
    }

    public P2pTipperArrivalTargetSnapshot snapshot() {
        MachineWaitQueueSnapshot queueSnapshot = inputQueue.snapshot();
        return new P2pTipperArrivalTargetSnapshot(
                destination,
                queueSnapshot.capacity(),
                queueSnapshot.toteIds().stream()
                        .map(PhysicalToteId::new)
                        .toList(),
                acceptedCount);
    }

    private void validateAcceptance(
            RoutedPhysicalTote routedTote,
            TipperTotePayload payload) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (!destination.equals(routedTote.destination())) {
            throw new IllegalArgumentException("routedTote destination must match target destination");
        }

        String toteId = routedTote.physicalToteId().value();
        if (payload.getTote() != routedTote.tote()) {
            throw new IllegalArgumentException("payload must contain the exact routed tote");
        }
        if (payload.getToteRenderable() != routedTote.renderable()) {
            throw new IllegalArgumentException("payload must contain the exact routed renderable");
        }
        if (!toteId.equals(payload.getTote().getId())) {
            throw new IllegalArgumentException("payload physical tote ID must match routed tote");
        }

        ToteLoadPlan existingLoadPlan = acceptedLoadPlansByToteId.get(toteId);
        if (existingLoadPlan != null) {
            if (existingLoadPlan != routedTote.loadPlan()) {
                throw new IllegalStateException(
                        "Physical tote was previously accepted with a different load plan: " + toteId);
            }
            throw new IllegalArgumentException(
                    "Physical tote was already accepted by P2P target: " + toteId);
        }
        if (inputQueue.contains(toteId)) {
            throw new IllegalStateException(
                    "Tipper input contains a tote without an accepted load plan: " + toteId);
        }
        if (!inputQueue.canAccept()) {
            throw new IllegalStateException(
                    "P2P tipper arrival target is full: " + destination.targetId());
        }
    }
}
