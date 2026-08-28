package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.Optional;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimController;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationArrivalClaimControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingBinding;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueueSnapshot;

public final class P2pArrivalConsumerController implements SimulationController {
    public static final String ADMISSION_DEFERRED = "ADMISSION_DEFERRED";
    public static final String TIPPER_INPUT_FULL = "TIPPER_INPUT_FULL";

    private final StationRoutedToteArrivalQueue sourceQueue;
    private final P2pArrivalAdmissionPolicy admissionPolicy;
    private final P2pArrivalRouteBinding routeBinding;
    private final P2pTipperPayloadFactory payloadFactory;
    private final P2pTipperArrivalTarget target;
    private final P2pStationProcessingTarget processingTarget;
    private final StationArrivalClaimController claimController;

    public P2pArrivalConsumerController(
            StationRoutedToteArrivalQueue sourceQueue,
            P2pArrivalAdmissionPolicy admissionPolicy,
            P2pArrivalRouteBinding routeBinding,
            P2pTipperPayloadFactory payloadFactory,
            P2pTipperArrivalTarget target) {
        this(
                sourceQueue,
                admissionPolicy,
                routeBinding,
                payloadFactory,
                target,
                new StationProcessingCoordinator());
    }

    public P2pArrivalConsumerController(
            StationRoutedToteArrivalQueue sourceQueue,
            P2pArrivalAdmissionPolicy admissionPolicy,
            P2pArrivalRouteBinding routeBinding,
            P2pTipperPayloadFactory payloadFactory,
            P2pTipperArrivalTarget target,
            StationProcessingCoordinator coordinator) {
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
        if (coordinator == null) {
            throw new IllegalArgumentException("coordinator must not be null");
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
        this.processingTarget = new P2pStationProcessingTarget(
                admissionPolicy,
                routeBinding,
                payloadFactory,
                target,
                coordinator);
        this.claimController = new StationArrivalClaimController(
                new StationProcessingBinding(sourceQueue, processingTarget));
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        claimController.update(context, dtSeconds);
    }

    public P2pArrivalConsumerControllerSnapshot snapshot() {
        StationRoutedToteArrivalQueueSnapshot sourceSnapshot = sourceQueue.snapshot();
        P2pTipperArrivalTargetSnapshot targetSnapshot = target.snapshot();
        StationArrivalClaimControllerSnapshot claimSnapshot = claimController.snapshot();
        Optional<PhysicalToteId> headPhysicalToteId = sourceSnapshot.entries().isEmpty()
                ? Optional.empty()
                : Optional.of(sourceSnapshot.entries().get(0).physicalToteId());
        Optional<PhysicalToteId> blockedPhysicalToteId = claimSnapshot.blockedPhysicalToteId();
        String genericBlockedReason = claimSnapshot.blockedReason();
        String blockedReason = "";
        String policyReason = "";
        if (blockedPhysicalToteId.isPresent()) {
            if (TIPPER_INPUT_FULL.equals(genericBlockedReason)) {
                blockedReason = TIPPER_INPUT_FULL;
            } else {
                blockedReason = ADMISSION_DEFERRED;
                policyReason = processingTarget.lastPolicyReason();
            }
        }
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
                claimSnapshot.lastClaimedPhysicalToteId(),
                blockedPhysicalToteId,
                blockedReason,
                policyReason,
                claimSnapshot.successfulClaimCount());
    }
}
