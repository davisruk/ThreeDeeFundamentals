package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public final class P2pArrivalConsumerController implements SimulationController {
    public static final String ADMISSION_DEFERRED = "ADMISSION_DEFERRED";
    public static final String TIPPER_INPUT_FULL = "TIPPER_INPUT_FULL";

    private final StationRoutedToteArrivalQueue sourceQueue;
    private final P2pArrivalAdmissionPolicy admissionPolicy;
    private final P2pArrivalRouteBinding routeBinding;
    private final P2pTipperPayloadFactory payloadFactory;
    private final P2pTipperArrivalTarget target;
    private Optional<PhysicalToteId> lastAcceptedPhysicalToteId = Optional.empty();
    private Optional<PhysicalToteId> blockedPhysicalToteId = Optional.empty();
    private String blockedReason = "";
    private String policyReason = "";
    private long successfulAcceptanceCount;

    public P2pArrivalConsumerController(
            StationRoutedToteArrivalQueue sourceQueue,
            P2pArrivalAdmissionPolicy admissionPolicy,
            P2pArrivalRouteBinding routeBinding,
            P2pTipperPayloadFactory payloadFactory,
            P2pTipperArrivalTarget target) {
        if (sourceQueue == null) {
            throw new IllegalArgumentException("sourceQueue must not be null");
        }
        if (admissionPolicy == null) {
            throw new IllegalArgumentException("admissionPolicy must not be null");
        }
        if (routeBinding == null) {
            throw new IllegalArgumentException("routeBinding must not be null");
        }
        if (payloadFactory == null) {
            throw new IllegalArgumentException("payloadFactory must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (sourceQueue.destination().stationType() != StationType.P2P) {
            throw new IllegalArgumentException("sourceQueue must identify a P2P destination");
        }
        if (!sourceQueue.destination().equals(target.destination())) {
            throw new IllegalArgumentException("source and target destinations must match");
        }
        this.sourceQueue = sourceQueue;
        this.admissionPolicy = admissionPolicy;
        this.routeBinding = routeBinding;
        this.payloadFactory = payloadFactory;
        this.target = target;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Optional<RoutedPhysicalTote> optionalHead = sourceQueue.peek();
        if (optionalHead.isEmpty()) {
            clearBlock();
            return;
        }
        RoutedPhysicalTote head = optionalHead.orElseThrow();
        requireTerminalRoutePosition(head);

        P2pArrivalAdmissionRequest request = P2pArrivalAdmissionRequest.from(head);
        P2pArrivalAdmissionDecision decision = admissionPolicy.evaluate(request);
        if (decision == null) {
            throw new IllegalStateException("P2P arrival admission policy returned null");
        }
        if (!decision.permitted()) {
            recordBlock(head.physicalToteId(), ADMISSION_DEFERRED, decision.reason());
            return;
        }
        if (!target.canAccept()) {
            recordBlock(head.physicalToteId(), TIPPER_INPUT_FULL, "");
            return;
        }

        TipperTotePayload payload = payloadFactory.create(head);
        if (payload == null) {
            throw new IllegalStateException("P2P tipper payload factory returned null");
        }
        target.accept(head, payload);

        RoutedPhysicalTote dequeued = sourceQueue.dequeue().orElseThrow(() ->
                new IllegalStateException(
                        "P2P station arrival head disappeared after target acceptance"));
        if (dequeued != head) {
            throw new IllegalStateException(
                    "P2P station arrival dequeued a different routed tote after acceptance");
        }

        successfulAcceptanceCount++;
        lastAcceptedPhysicalToteId = Optional.of(head.physicalToteId());
        clearBlock();
    }

    public P2pArrivalConsumerControllerSnapshot snapshot() {
        StationRoutedToteArrivalQueueSnapshot sourceSnapshot = sourceQueue.snapshot();
        P2pTipperArrivalTargetSnapshot targetSnapshot = target.snapshot();
        Optional<PhysicalToteId> headPhysicalToteId = sourceSnapshot.entries().isEmpty()
                ? Optional.empty()
                : Optional.of(sourceSnapshot.entries().get(0).physicalToteId());
        return new P2pArrivalConsumerControllerSnapshot(
                sourceSnapshot.destination(),
                sourceSnapshot.capacity(),
                sourceSnapshot.occupancy(),
                targetSnapshot.destination(),
                targetSnapshot.capacity(),
                targetSnapshot.occupancy(),
                routeBinding.terminalSegmentLabel(),
                routeBinding.tipperEntrySegmentLabel(),
                headPhysicalToteId,
                lastAcceptedPhysicalToteId,
                blockedPhysicalToteId,
                blockedReason,
                policyReason,
                successfulAcceptanceCount);
    }

    private void requireTerminalRoutePosition(RoutedPhysicalTote routedTote) {
        RouteFollower follower = routedTote.tote().getRouteFollower();
        if (!routeBinding.matchesTerminalSegment(follower.getCurrentSegment())) {
            throw new IllegalStateException(
                    "P2P arrival tote is not on the configured terminal segment: "
                            + routedTote.physicalToteId().value());
        }
    }

    private void recordBlock(
            PhysicalToteId physicalToteId,
            String reason,
            String admissionPolicyReason) {
        blockedPhysicalToteId = Optional.of(physicalToteId);
        blockedReason = reason;
        policyReason = admissionPolicyReason;
    }

    private void clearBlock() {
        blockedPhysicalToteId = Optional.empty();
        blockedReason = "";
        policyReason = "";
    }
}
